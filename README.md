# ColorOS AOD Enhance

ColorOS AOD 增强模块，基于 Qjj7679/ColorOS_Aod_Enhance 继续维护。

## v1.8.1 正式版

- 暗光/亮光初始 AOD 亮度支持自定义或系统默认。
- AOD 自动亮度倍率支持自定义或系统默认。
- AOD 最长显示时间：系统默认、30 秒、1/5/10/30/60 分钟、始终显示、自定义 1–1440 分钟。
- 单击防误触，保留双击唤醒。
- 支持低光/特殊规则下保持 AOD。
- 配置使用 YukiHookPrefsBridge / XSharedPreferences。
- 正式版移除 Debug Telemetry、文件日志与公共存储日志权限。
- 仅提供 arm64-v8a。
- 不强制修改 LTPO/ADFR min_fps，继续由 ColorOS/面板驱动自动调节。

## 安装

需要兼容的 Xposed/LSPosed 环境。安装 APK 后启用模块并设置对应作用域。

> v1.8.1 Release 使用新的 Release 证书。如果设备上是签名不同的旧测试版，Android 可能要求先卸载旧版。
