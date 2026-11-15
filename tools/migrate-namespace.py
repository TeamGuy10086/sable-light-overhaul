"""Copy an offline world/config directory and migrate one registry namespace.

Only NBT string values and compound keys are changed; numeric/array payloads stay intact.
The source is never written. No third-party Python dependencies are required.

Binary stores are NOT rewritten and keep the old namespace: Voxy's RocksDB + ZSTD LOD
database (`voxy/`) force-crashes the client at world join once the old block ids are gone,
the server-side LOD cache (`vss-lod/`) is derived from the same ids, and Sable's sub-level
sections (`sublevels/`) load the old blocks as default and drop their block entities.
Those stores are reported in `unhandled_binary_stores`; delete the LOD caches before
loading the migrated world.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import zlib


class NbtRewriter:
    def __init__(self, data, old, new):
        self.data, self.old, self.new = data, old.encode(), new.encode()
        self.at = 0
        self.changes = 0

    def take(self, n):
        if n < 0 or self.at + n > len(self.data):
            raise ValueError("Truncated or invalid NBT")
        result = self.data[self.at:self.at + n]
        self.at += n
        return result

    def string(self):
        size = struct.unpack('>H', self.take(2))[0]
        value = self.take(size)
        # Preserve modified-UTF8 bytes outside the ASCII namespace exactly.
        updated = re.sub(rb'(?<![A-Za-z0-9_])' + re.escape(self.old)
                         + rb'(?=$|[:_.])', self.new, value)
        if updated != value:
            self.changes += 1
        if len(updated) > 65535:
            raise ValueError("Migrated NBT string exceeds its length limit")
        return struct.pack('>H', len(updated)) + updated

    def payload(self, kind, depth=0):
        if depth > 512:
            raise ValueError("NBT nesting exceeds 512")
        if kind in {1, 2, 3, 4, 5, 6}:
            return self.take({1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}[kind])
        if kind in {7, 11, 12}:
            length = self.take(4)
            count = struct.unpack('>i', length)[0]
            if count < 0:
                raise ValueError("Negative NBT array length")
            return length + self.take(count * {7: 1, 11: 4, 12: 8}[kind])
        if kind == 8:
            return self.string()
        if kind == 9:
            header = self.take(5)
            child, count = header[0], struct.unpack('>i', header[1:])[0]
            if count < 0 or (child == 0 and count != 0):
                raise ValueError("Invalid NBT list")
            return header + b''.join(self.payload(child, depth + 1) for _ in range(count))
        if kind == 10:
            result = bytearray()
            keys = set()
            while True:
                tag = self.take(1)
                result += tag
                if tag == b'\0':
                    return bytes(result)
                key = self.string()
                if key in keys:
                    raise ValueError("Compound key collision after namespace migration")
                keys.add(key)
                result += key
                result += self.payload(tag[0], depth + 1)
        raise ValueError(f"Unknown NBT tag {kind}")

    def rewrite(self):
        tag = self.take(1)
        if tag[0] == 0:
            raise ValueError("Empty NBT root")
        result = tag + self.string() + self.payload(tag[0])
        if self.at != len(self.data):
            raise ValueError("Trailing bytes after NBT root")
        return result, self.changes


def rewrite_nbt(data, old, new):
    return NbtRewriter(data, old, new).rewrite()


def decode(data, codec):
    if codec == 1:
        return gzip.decompress(data)
    if codec == 2:
        return zlib.decompress(data)
    if codec == 3:
        return data
    raise ValueError(f"Unsupported region compression {codec}; source remains untouched")


def encode(data, codec):
    return {1: lambda: gzip.compress(data, mtime=0),
            2: lambda: zlib.compress(data), 3: lambda: data}[codec]()


def rewrite_region(source, target, old, new):
    data = source.read_bytes()
    if not data:
        # Minecraft pre-allocates region files it never writes; they carry no NBT
        # and must be copied through untouched instead of rejected as corrupt.
        return 0
    if len(data) < 8192 or len(data) % 4096:
        raise ValueError(f"Invalid region length: {source}")
    match = re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca', source.name)
    if not match:
        raise ValueError(f"Unknown region filename: {source}")
    rx, rz = map(int, match.groups())
    result = bytearray(data[:8192])
    result[:4096] = bytes(4096)
    changes = 0
    external_updates = []
    used = {0, 1}
    for slot in range(1024):
        location = int.from_bytes(data[slot * 4:slot * 4 + 4], 'big')
        if not location:
            continue
        offset, sectors = location >> 8, location & 255
        if offset < 2 or sectors == 0 or (offset + sectors) * 4096 > len(data):
            raise ValueError(f"Invalid chunk sector range: {source}, slot {slot}")
        span = set(range(offset, offset + sectors))
        if used & span:
            raise ValueError(f"Overlapping chunk sectors: {source}, slot {slot}")
        used |= span
        pos = offset * 4096
        length = int.from_bytes(data[pos:pos + 4], 'big')
        if length < 1 or length + 4 > sectors * 4096:
            raise ValueError(f"Invalid chunk length: {source}, slot {slot}")
        compression = data[pos + 4]
        codec = compression & 127
        external = bool(compression & 128)
        ext_name = f'c.{rx * 32 + slot % 32}.{rz * 32 + slot // 32}.mcc'
        packed = ((source.parent / ext_name).read_bytes() if external
                  else data[pos + 5:pos + 4 + length])
        rewritten, count = rewrite_nbt(decode(packed, codec), old, new)
        changes += count
        payload = encode(rewritten, codec) if count else packed
        record = len(payload) + 1
        needed = (record + 4 + 4095) // 4096
        if external or needed > 255:
            external_updates.append((target.parent / ext_name, payload))
            chunk = struct.pack('>I', 1) + bytes([codec | 128])
            needed = 1
        else:
            chunk = struct.pack('>I', record) + bytes([codec]) + payload
        new_offset = len(result) // 4096
        if new_offset > 0xFFFFFF:
            raise ValueError("Region offset exceeds format limit")
        result[slot * 4:slot * 4 + 4] = ((new_offset << 8) | needed).to_bytes(4, 'big')
        result += chunk + bytes(needed * 4096 - len(chunk))
    if changes:
        target.write_bytes(result)
        for path, payload in external_updates:
            path.write_bytes(payload)
    return changes


def migrate(source, output, old, new):
    source, output = Path(source).resolve(), Path(output).resolve()
    if not source.is_dir() or output.exists() or output.is_relative_to(source) or source.is_relative_to(output):
        raise ValueError("Source must exist; output must be new and outside source")
    if old == new or not all(re.fullmatch(r'[a-z][a-z0-9_]*', v) for v in [old, new]):
        raise ValueError("Provide distinct valid namespaces")
    if any(source.rglob('session.lock')):
        raise ValueError("World session.lock found: close Minecraft, copy the world, then remove ONLY the copy's stale session.lock before migration")
    # A partial/error output is never labelled complete, and the original remains untouched.
    shutil.copytree(source, output)
    report = {'status': 'IN_PROGRESS', 'source': str(source), 'output': str(output),
              'from': old, 'to': new, 'changes': []}
    report_path = output / 'namespace-migration-report.json'
    try:
        for path in sorted(source.rglob('*')):
            if not path.is_file():
                continue
            target = output / path.relative_to(source)
            count = 0
            if path.suffix == '.mca':
                count = rewrite_region(path, target, old, new)
            elif path.suffix in {'.dat', '.dat_old', '.nbt'}:
                data = path.read_bytes()
                codec = 1 if data.startswith(b'\x1f\x8b') else (2 if data[:1] == b'x' else 3)
                plain = decode(data, codec)
                if plain[:1] != b'\x0a':
                    # Third-party files bearing .dat need not be NBT; record them explicitly.
                    report.setdefault('unrecognised_dat', []).append(str(path.relative_to(source)))
                    continue
                rewritten, count = rewrite_nbt(plain, old, new)
                if count:
                    target.write_bytes(encode(rewritten, codec))
            elif path.suffix in {'.toml', '.json'}:
                data = path.read_bytes()
                updated, count = re.subn(rb'(?<![A-Za-z0-9_])' + re.escape(old.encode())
                                         + rb'(?=$|[:_.-])', new.encode(), data)
                if count:
                    target.write_bytes(updated)
            if count:
                report['changes'].append({'file': str(path.relative_to(source)), 'strings': count,
                                           'source_sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
        # SavedData and config filenames are part of the namespace contract too.
        for path in sorted(output.rglob('*'), key=lambda p: len(p.parts), reverse=True):
            if path.name == old or path.name.startswith(old + '_') or path.name.startswith(old + '-'):
                dest = path.with_name(new + path.name[len(old):])
                if dest.exists():
                    raise ValueError(f"Migration filename collision: {dest}")
                path.rename(dest)
                report.setdefault('renamed', []).append(str(dest.relative_to(output)))
        # Binary stores copy through verbatim: they still carry the old namespace and
        # therefore decide whether the migrated world can be loaded at all.
        for name in ('voxy', 'vss-lod', 'sublevels'):
            for store in sorted(output.rglob(name)):
                if not store.is_dir():
                    continue
                report.setdefault('unhandled_binary_stores', []).append({
                    'path': str(store.relative_to(output)),
                    'files': sum(1 for p in store.rglob('*') if p.is_file()),
                })
        if report.get('unhandled_binary_stores'):
            report.setdefault('warnings', []).append(
                'Binary stores were copied verbatim and still contain the old namespace ('
                + ', '.join(s['path'] for s in report['unhandled_binary_stores'])
                + '). Delete the LOD caches before loading: Voxy force-crashes the client on the '
                  'unknown block state, while Sable sub-level blocks load as default and their '
                  'block entities are dropped.'
            )
        report['status'] = 'COMPLETE'
    except Exception as exc:
        report['status'], report['error'] = 'FAILED_DO_NOT_LOAD', str(exc)
        raise
    finally:
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--from-id', required=True)
    parser.add_argument('--to-id', default='sablelightoverhaul')
    options = parser.parse_args()
    print(json.dumps(migrate(options.source, options.output, options.from_id, options.to_id),
                     ensure_ascii=False, indent=2))
