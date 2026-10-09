# ColorOS AOD Enhance

ColorOS AOD Enhance 是面向 ColorOS AOD（息屏显示）的 Xposed/LSPosed 增强模块。

> [!IMPORTANT]
> 本仓库基于 [Qjj7679/ColorOS_Aod_Enhance](https://github.com/Qjj7679/ColorOS_Aod_Enhance) 进行二次开发与持续维护。  
> 本仓库是**独立维护分支（unofficial maintained fork）**，不是原项目官方版本，也不代表原作者发布或维护的版本。

## 上游项目 / Upstream

- 上游仓库：[Qjj7679/ColorOS_Aod_Enhance](https://github.com/Qjj7679/ColorOS_Aod_Enhance)
- 原作者：Qjj7679
- 本维护仓库：[Ayc-Gh/ColorOS-AOD](https://github.com/Ayc-Gh/ColorOS-AOD)
- 关系：基于上游项目继续开发的独立维护分支
- 许可证：MIT License；保留上游项目的原始版权与许可证声明

本仓库最初以已审计的上游源码为基线继续开发，并在此基础上增加配置桥接、AOD 显示时长控制、系统默认亮度选项等功能，同时对调试代码、跨进程配置和正式版构建流程进行了整理。

## 正式版 1.10.0

- 修复 ColorOS 16 全天全景能力发布和一分钟显示时长。
- 非超时的低光等系统隐藏放行时，结束显示会话并取消剩余定时任务。
- 设置页和 Hook 共用配置模型与解析器；远端读取同步更新本地断连回退副本。
- Release 关闭 DEBUG 追踪，保留首次 Hook 命中、关键生命周期及异常日志。
- v1.10.0 / versionCode 33 使用新建的长期正式签名。原签名已不可用，旧版本和 RC5 USB 测试版需要备份设置、卸载后重新安装；以后同签名版本可覆盖升级。
- 验证范围：PHY110 / Android 16 / PHY110_16.0.10.500(CN01)，LSPosed API 102。整夜自然运行、全部时长组合和其他机型尚未验证，正式版标记不扩大兼容性承诺。
- 删除历史迁移/版本发布工作流，统一为 `release.yml`。固定签名密钥配置齐全时，常规标签验证签名后创建草稿发布；没有签名配置时仅生成明确标记的 unsigned 验证产物，正式 APK 由本地签名后上传。工作流不会删除已有发布。
- 自动签名需要配置 `AOD_KEYSTORE_BASE64`、`AOD_STORE_PASSWORD`、`AOD_KEY_ALIAS`、`AOD_KEY_PASSWORD`。不要为每个版本生成新的签名证书；私钥和密码不得提交仓库或上传 Release。
- 正式签名证书 SHA-256：`CA:4B:EF:D4:3C:51:C1:B4:F2:0B:5D:9F:A1:B9:AE:4F:F2:7F:F2:16:93:94:7E:C9:09:17:6E:C7:EC:CD:A3:F2`。

本维护分支的正式签名不等同于上游签名。[正式版验证与升级说明](docs/1.10.0-validation.md)；下方旧版本日志说明保留作为历史记录。

## v1.9.0 · libxposed API 102 迁移版

### AOD 亮度

- 暗光环境初始 AOD 亮度支持自定义或使用系统默认。
- 亮光环境初始 AOD 亮度支持自定义或使用系统默认。
- AOD 运行阶段自动亮度倍率支持自定义或使用系统默认。

### AOD 显示时长

可设置单次 AOD 的最长显示时间：

- 系统默认
- 30 秒
- 1 分钟
- 5 分钟
- 10 分钟
- 30 分钟
- 60 分钟
- 始终显示（模块不主动设置时长上限）
- 自定义 1–1440 分钟

该设置是 AOD 的**最长显示上限**。来电、解锁以及系统主动结束 AOD 等事件仍由 ColorOS 原有状态机处理。

### 其他功能

- 单击防误触，并保留双击唤醒逻辑。
- 可选择在低光/特殊规则下保持 AOD。
- 配置改用 libxposed Remote Preferences：模块 App 通过 XposedService 写入，Hook 进程通过 XposedModule 只读访问。
- 首次升级会把旧版 `aod_config` 本地配置迁移到 Remote Preferences，尽量保留原有设置。

### 正式版收口

- 删除 Debug Telemetry 和周期刷新率/功耗采样。
- 删除文件日志及公共存储日志权限。
- 不再使用 ContentProvider 作为配置读取链路。
- Release 构建启用 R8 和资源压缩。
- 当前 Release 仅提供 arm64-v8a。
- 不强制写入 OPPO ADFR/min_fps；LTPO/ADFR 刷新率策略继续由 ColorOS 与面板驱动自动管理。

## 现代 API 迁移

- 已移除 `de.robv.android.xposed:api:82`、YukiHookAPI、KavaRef 和旧 `assets/xposed_init` 入口。
- 模块入口改为 `io.github.libxposed.api.XposedModule`。
- 使用 `META-INF/xposed/java_init.list`、`module.prop` 与 `scope.list`。
- `targetApiVersion=102`，要求使用支持 libxposed API 102 的框架版本。
- Hook 全部改为 libxposed `hook(...).intercept { chain -> ... }` 模型。

## v1.9.1 详细日志版

该版本在 v1.9.0 API 102 迁移版基础上启用完整运行日志：

- Release 构建保留 DEBUG / INFO / WARN / ERROR。
- 每次 libxposed Hook 命中记录 `HOOK_HIT`。
- 保留亮度原始值/目标值、AOD 时长、配置读取、Hook 注册与异常日志。
- 日志 TAG：`AOD_Enhance`。
- 提供 `tools/capture-aod-log.sh`，可在 root 环境持续采集到 `/storage/emulated/0/Documents/ColorOS-AOD/log/`。

Termux 示例：

```sh
su -c 'sh /path/to/capture-aod-log.sh'
```

也可以直接执行：

```sh
su -c 'mkdir -p /storage/emulated/0/Documents/ColorOS-AOD/log; logcat -v threadtime -s AOD_Enhance:* -f /storage/emulated/0/Documents/ColorOS-AOD/log/aod-detailed.log'
```

详细日志版用于问题定位，日志量明显高于普通版。

## v1.10.0 RC1 · AOD 时长控制重构

基于 PHY110 / ColorOS 16.0.10.500(CN01) 的原生状态机追踪结果，确认全景 AOD 的普通自动超时由以下链路触发：

`PanoramicAodController -> AODDisplayUtil.requestScreenState(OFF, 100, "Panoramic-Aod-Show")`。

v1.10.0 RC1 不再通过额外 `Handler` 或 `DreamService.finish()` 控制时长，而是只接管上述原生普通超时：

- “系统默认”：完全放行 ColorOS 原逻辑。
- “30 秒 / 1 分钟 / 5 分钟 / 10 分钟 / 30 分钟 / 60 分钟 / 自定义”：在原生普通超时到来时暂缓 OFF，到目标时间后回放同一个 ColorOS 原生 OFF 请求。
- “始终显示”：只屏蔽 `Panoramic-Aod-Show` 这一条普通自动超时。
- 指纹、解锁、主动唤醒、来电以及其他 reason 的 OFF 请求完全不拦截。
- 到期调度使用 `ELAPSED_REALTIME_WAKEUP`，避免旧 Handler 方案在休眠中延迟。

关键诊断日志：`AOD_SESSION_START`、`AOD_NATIVE_TIMEOUT_INTERCEPT`、`AOD_DEADLINE_SCHEDULE`、`AOD_DEADLINE_REACHED`、`AOD_NATIVE_OFF_REPLAY`、`AOD_SESSION_END`。

## 安装

1. 安装构建出的 APK。
2. 在支持 libxposed API 102 的 LSPosed/兼容框架中启用模块。
3. 根据设备和系统实际情况设置模块作用域。
4. 修改配置后按模块/系统提示使配置生效。

> [!WARNING]
> 如果设备上安装的是签名不同的旧 Debug/测试版，Android 可能不允许直接覆盖安装，需要先卸载旧版本；相同签名升级时会保留本地配置并在框架服务可用后迁移到 Remote Preferences。

## 下载

正式版本请从本仓库的 [Releases](https://github.com/Ayc-Gh/ColorOS-AOD/releases) 页面获取。Release 同时提供 APK、SHA256 校验信息和签名信息。

## 兼容性

当前正式构建仅提供 **arm64-v8a**。不同 ColorOS/OPlus SystemUI/AOD 版本的内部实现可能存在差异，因此不能保证所有 ColorOS 设备均兼容。

## 许可证与致谢

本项目遵循上游项目的 **MIT License**。

感谢 [Qjj7679/ColorOS_Aod_Enhance](https://github.com/Qjj7679/ColorOS_Aod_Enhance) 及其作者 **Qjj7679** 提供原始项目。本仓库保留上游项目的版权和许可证声明；后续修改、维护和 Release 由本仓库独立进行。

如需了解原项目本身，请以上游仓库内容为准。
