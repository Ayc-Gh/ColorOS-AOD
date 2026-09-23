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

## v1.8.1 正式版

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
- 配置使用 YukiHookPrefsBridge / XSharedPreferences 跨进程读取。

### 正式版收口

- 删除 Debug Telemetry 和周期刷新率/功耗采样。
- 删除文件日志及公共存储日志权限。
- 不再使用 ContentProvider 作为配置读取链路。
- Release 构建启用 R8 和资源压缩。
- 当前 Release 仅提供 arm64-v8a。
- 不强制写入 OPPO ADFR/min_fps；LTPO/ADFR 刷新率策略继续由 ColorOS 与面板驱动自动管理。

## 安装

1. 安装 Release 页面提供的 APK。
2. 在兼容的 Xposed/LSPosed 环境中启用模块。
3. 根据设备和系统实际情况设置模块作用域。
4. 修改配置后按模块/系统提示使配置生效。

> [!WARNING]
> v1.8.1 Release 使用独立 Release 证书。如果设备上安装的是签名不同的旧 Debug/测试版，Android 可能不允许直接覆盖安装，需要先卸载旧版本。

## 下载

正式版本请从本仓库的 [Releases](https://github.com/Ayc-Gh/ColorOS-AOD/releases) 页面获取。Release 同时提供 APK、SHA256 校验信息和签名信息。

## 兼容性

当前正式构建仅提供 **arm64-v8a**。不同 ColorOS/OPlus SystemUI/AOD 版本的内部实现可能存在差异，因此不能保证所有 ColorOS 设备均兼容。

## 许可证与致谢

本项目遵循上游项目的 **MIT License**。

感谢 [Qjj7679/ColorOS_Aod_Enhance](https://github.com/Qjj7679/ColorOS_Aod_Enhance) 及其作者 **Qjj7679** 提供原始项目。本仓库保留上游项目的版权和许可证声明；后续修改、维护和 Release 由本仓库独立进行。

如需了解原项目本身，请以上游仓库内容为准。
