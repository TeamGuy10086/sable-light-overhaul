"""Offline, strict-content shader patcher. Never writes into the selected pack."""
import base64
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import stat
import tempfile
import zipfile

MAX_TOTAL = 256 * 1024 * 1024
MAX_FILE = 32 * 1024 * 1024
MAX_FILES = 10000
MARKER = "SLO-PATCHER.json"


class PatchError(ValueError):
    pass


def digest(data):
    return hashlib.sha256(data).hexdigest()


def canonical(name, data):
    # ZIP metadata, root folder name and editor line endings are not versions.
    if Path(name).suffix.lower() in {".glsl", ".vsh", ".fsh", ".gsh", ".csh", ".json", ".properties", ".lang"}:
        return data.replace(b"\r\n", b"\n")
    return data


def safe_name(name):
    if "\\" in name or ":" in name or "\x00" in name:
        raise PatchError("Unsafe archive path / ZIP 路径不安全: " + name)
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts or not path.parts:
        raise PatchError("Unsafe archive path / ZIP 路径不安全: " + name)
    return path.as_posix()


def load_pack(source):
    source = Path(source)
    files = {}
    total = 0
    seen = set()

    def add(name, size, reader):
        nonlocal total
        name = safe_name(name)
        key = name.casefold()
        if key in seen:
            raise PatchError("Duplicate archive path / 重复文件路径: " + name)
        seen.add(key)
        total += size
        if size > MAX_FILE or total > MAX_TOTAL or len(seen) > MAX_FILES:
            raise PatchError("Pack exceeds safety limits / 文件数量或大小超限")
        data = reader()
        if len(data) != size:
            raise PatchError("File changed during read / 读取时文件发生变化: " + name)
        files[name] = data

    if source.is_dir():
        for path in sorted(source.rglob("*")):
            if path.is_symlink():
                raise PatchError("Symlinks are not supported / 不支持符号链接")
            if path.is_file():
                add(path.relative_to(source).as_posix(), path.stat().st_size, path.read_bytes)
    else:
        try:
            with zipfile.ZipFile(source) as archive:
                for info in archive.infolist():
                    safe_name(info.filename.rstrip("/"))
                    if stat.S_ISLNK(info.external_attr >> 16):
                        raise PatchError("Symlinks are not supported / 不支持符号链接")
                    if not info.is_dir():
                        add(info.filename, info.file_size, lambda i=info: archive.read(i))
        except (zipfile.BadZipFile, RuntimeError) as exc:
            raise PatchError("Invalid, damaged or encrypted ZIP / ZIP 无效、损坏或已加密") from exc
    roots = set()
    for name in files:
        parts = name.split("/")
        if "shaders" in parts:
            roots.add("/".join(parts[:parts.index("shaders")]))
    if len(roots) != 1:
        raise PatchError("Select one shader pack containing shaders/ / 请选择单个光影包")
    root = roots.pop()
    prefix = root + "/" if root else ""
    return {name[len(prefix):]: data for name, data in files.items() if name.startswith(prefix)}


def shader_hashes(files):
    return {name: digest(canonical(name, data)) for name, data in files.items() if name.startswith("shaders/")}


def select_variant(files, manifest):
    if MARKER in files or any(name.startswith("shaders/lib/slo/") for name in files):
        raise PatchError("Already patched / 已打过 SLO 补丁，请选择原包")
    hashes = shader_hashes(files)
    for variant in manifest["variants"]:
        if hashes == variant["base"]:
            return variant
    supported = "\n".join(v["label"] for v in manifest["variants"])
    raise PatchError("Unsupported version or modified shader files / 版本不支持或源码已改动。\nSupported / 支持版本:\n" + supported)


def apply_delta(original, operations):
    result = []
    size = 0
    for op in operations:
        if op[0] == "copy":
            start, length = op[1:]
            if start < 0 or length < 0 or start + length > len(original):
                raise PatchError("Invalid patch copy range")
            block = original[start:start + length]
        elif op[0] == "add":
            block = base64.b64decode(op[1], validate=True)
        else:
            raise PatchError("Unknown patch operation")
        size += len(block)
        if size > MAX_FILE:
            raise PatchError("Patched file exceeds size limit")
        result.append(block)
    return b"".join(result)


def patch_pack(source, output, manifest, progress=lambda text: None):
    source, output = Path(source).resolve(), Path(output).resolve()
    if output == source or output.exists() or output.suffix.lower() != ".zip":
        raise PatchError("Choose a NEW .zip output; originals are never overwritten / 请选择尚不存在的 ZIP 输出路径")
    if source.is_dir() and source in output.parents:
        raise PatchError("Output must be outside the input folder / 输出不能放在原包目录内")
    progress("Checking original pack / 正在校验原包…")
    files = load_pack(source)
    variant = select_variant(files, manifest)
    progress("Applying " + variant["label"] + " / 正在打补丁…")
    # Preserve untouched files and licenses byte-for-byte.
    for name, delta in variant["changes"].items():
        safe_name(name)
        original = canonical(name, files.get(name, b""))
        if digest(original) != delta["before"]:
            raise PatchError("Source changed / 原文件不匹配: " + name)
        value = apply_delta(original, delta["ops"])
        if digest(value) != delta["after"]:
            raise PatchError("Patch verification failed / 补丁数据校验失败: " + name)
        files[name] = value
    files[MARKER] = json.dumps({"patcher": manifest["release"], "mod": manifest["mod_version"],
                               "base": variant["label"], "status": "verified"}, ensure_ascii=False, indent=2).encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    progress("Verifying output / 正在校验输出…")
    temp = None
    try:
        with tempfile.NamedTemporaryFile(prefix=".slo-patch-", suffix=".zip", dir=output.parent, delete=False) as handle:
            temp = Path(handle.name)
        with zipfile.ZipFile(temp, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for name, data in sorted(files.items()):
                info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, data)
        with zipfile.ZipFile(temp) as archive:
            if set(archive.namelist()) != set(files):
                raise PatchError("Output file list mismatch")
            for name, expected in files.items():
                if digest(archive.read(name)) != digest(expected):
                    raise PatchError("Output verification failed / 输出校验失败: " + name)
        # Windows rename fails if the destination appeared meanwhile; never overwrite it.
        if os.name == "nt":
            os.rename(temp, output)
        else:
            os.link(temp, output)
            temp.unlink()
        temp = None
    finally:
        if temp is not None:
            temp.unlink(missing_ok=True)
    return {"success": True, "variant": variant["label"], "output": str(output),
            "sha256": digest(output.read_bytes()), "files": len(files), "changed": len(variant["changes"])}
