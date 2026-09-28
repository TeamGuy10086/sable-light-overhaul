# CBC Muzzle Light 0.9.3-alpha

本版修复射灯倾斜时的扇形缺口、把射程上限提到 512（Compatible 档裁到 64）、给静态光源与射灯加上颜色（仅 `Advanced` 档）。

安装、调色板、配置表与复测步骤见 **COLORS-0.9.3.zh-CN.md**。

- JAR：`cbc-muzzle-light-0.9.3-alpha.jar`；**协议仍为 6**（没有协议变更），双端一致即可。
- 用 `Advanced` 档**必须重新覆盖**光影包里的 `shaders/include/lighting/diffuse_lighting.glsl`（新增 `cbcMuzzleTintN`，并修扇形缺口）。
- 服务端配置 `config/cbcmuzzlelight-server.toml` 的 `maximumDistance`/`distanceStep` 已由我改为 512/32（备份在同目录）；客户端配置无需改动。
- 90 项自动检查通过，**未实机验证**。

其他文档：`SPOTLIGHT-0.9.2.zh-CN.md`（射灯行为与各档对照；其射程/颜色部分已被本版取代）、`LIGHTING-0.9.zh-CN.md`（0.9.0 新增功能与命令）、`STABILITY-0.9.1.zh-CN.md`（区段刷新与特殊模型稳定性）、`ADVANCED-0.8.zh-CN.md`（炮口参数）、`patch/README.zh-CN.md`（Photon 补丁）、`GPU-VERIFY.zh-CN.md`（屏幕空间叠加测试说明，模式名已变更为 `DynamicOnly`）。
