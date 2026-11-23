# SLO offline shader patchers — 0.3.4

Three Windows x64 GUI executables apply the Sable Light Overhaul 0.1.717 adapters to a user's own shader packs. Python, PowerShell and a network connection are not required to run the EXEs. The download buttons open the original authors' pages in the user's browser; the tools do not download packs automatically.

## Supported inputs

| Patcher | Exact supported source |
| --- | --- |
| Photon | voxy-support snapshot `ba11ae2ad9d506c3a2fd9ca10c317be259b94d69` (2025-10-26) |
| Complementary | Unbound r5.8.1; also Unbound r5.8.1 + Euphoria Patches 1.9.3 |
| Mellow | v3.5 |

Original author download links:

- [Photon project](https://modrinth.com/shader/photon-shader), [supported official GitHub snapshot](https://github.com/sixthsurge/photon/archive/ba11ae2ad9d506c3a2fd9ca10c317be259b94d69.zip).
- [Complementary](https://www.complementary.dev/shaders/), [Unbound versions](https://modrinth.com/shader/complementary-unbound/versions).
- [Euphoria Patches](https://www.euphoriapatches.com/). First generate the matching Euphoria pack using its own installer, then select that pack's folder in the Complementary SLO patcher.
- [Mellow 3.5](https://modrinth.com/shader/mellow/version/3.5).

Newer versions are intentionally rejected until their adapters are validated. A matching filename is not sufficient. Every shader source/resource is fingerprinted; root folder names, ZIP compression metadata, and CRLF versus LF in text files do not affect compatibility. Editing options directly in shader source before patching will fail validation. Iris's external options files are not copied or modified.

## Use

1. Download the supported original from its author.
2. Open the corresponding EXE. Select the original ZIP or extracted pack folder.
3. Choose a new ZIP output path outside the input folder and click **Apply and verify**.
4. When successful, place the new ZIP in `shaderpacks`, select it in Iris, and use SLO's **Advanced** lighting backend.
5. Keep the original pack. Do not install multiple SLO JAR versions simultaneously.

The tool never overwrites its input or an existing output. Unsupported/modified sources, already patched sources, malformed ZIPs, duplicate paths, symlinks and oversized inputs are rejected. Every output file is reread and SHA-256 checked before the output is published. Failure leaves no finished output ZIP.

“Success” means the patch and file verification passed. It does **not** mean that GLSL was compiled by Minecraft, Iris and your GPU driver; select the generated pack in-game to check that. These are local compatibility tools, not official releases or endorsements from the shader authors. EXEs are currently unsigned.

## 命令行 / CLI

The same EXE can run without a GUI for tests and scripts:

```powershell
& '.\Mellow-SLO-Patcher-0.3.4.exe' --input 'C:\Downloads\Mellow-v3.5.zip' --output 'C:\Downloads\Mellow + SLO Patch.zip' --report 'C:\Downloads\patch-result.json'
```

Exit status 0 = success, 1 = patch failure. A windowed EXE does not have a console, so use `--report` for machine-readable results. Report paths are explicitly supplied by the caller and should be new JSON files separate from inputs and outputs.

## License / 许可证

The original Sable Light Overhaul mod and patcher code are licensed under the MIT License (see `LICENSE`). Third-party shader code, derived patch content, and bundled runtime components retain their respective licenses and notices.

Sable Light Overhaul 模组和 patcher 的自有代码采用 MIT 协议，见 `LICENSE`。第三方光影代码、由其衍生的补丁内容及运行时组件仍适用各自的许可证，不因本项目采用 MIT 而改变。

## Rebuild from source

The source archive includes `payloads/*.json.gz`, the patcher Python source, this README, license notices and pinned build dependency versions. It contains no original shader archives or full patched packs.

```powershell
python -m pip install -r requirements-build.txt
python -m unittest discover -s . -p 'test_*.py' -v
python build_exes.py payloads dist --release 0.3.4
```

For an unfrozen development GUI, copy the desired payload to `payload.json.gz` beside `app.py`, then run `python app.py`. Build with Python 3.12 x64 on Windows. All three executables share the same `app.py` and `engine.py` and differ only in embedded payload and branding. The builder's staging folder is an author-side intermediate, not an installation destination.

To generate a new adapter payload, use `build_payloads.py CONFIG.json OUTPUT_DIR`. Each family has `family`, `links` and a `variants` array; each variant has `label`, `source` (original ZIP/folder), `target` (fully patched folder) and optional `overlay: true` for a folder containing only replacement/additional files. Top-level fields are `release`, `mod_version`, and `families`. Input packs are required locally but must not be added to the patcher source archive.

The delta format consists of copy ranges into the user's original files plus added/replacement lines. Unchanged original text is not embedded in the delta. Existing upstream license and attribution files are retained. Shipping only a patcher does not waive upstream license requirements; the original shader authors retain their rights. Do not upload the generated full shader packs as part of this release.

## 中文简要说明

这是三个本地离线补丁工具，分别对应 Photon、Complementary、Mellow。请先去作者官网下载上述支持的原版，自行选择 ZIP 或解压目录。程序只带差分和 SLO 新增代码，不包含完整光影包，不需要安装 Python。

输出是新 ZIP，原包和已有输出都不会被覆盖。版本不支持、源码已改动、重复打补丁会明确报错；成功后会重新读取输出并逐文件校验。文件校验不等于游戏里的显卡编译通过，仍需在 Iris 中选择新包确认。界面只做基本布局，足够选择原包、输出、查看状态即可。

Complementary 原包及 Euphoria 扩展各自遵守其上游协议。补丁工具采用独立名称描述兼容目标，不代表获上游官方认证；本发行目录不提供完整修改版包。请在 GitHub 发布说明保留以上作者链接与署名。
