# Shizako

Shizako 是基于开源项目 [Shizuku](https://github.com/RikkaApps/Shizuku) 二次开发的 Android 特权服务管理器。

> 作者：初然 | 版本：zako2.1 | 基于 Shizuku 13.x | 要求 Android 6.0+（无线调试需 Android 11+）

## 这是什么

Shizako 帮助你在**不 Root** 的前提下，把系统通过 ADB / Root 授予的特权安全地共享给其他应用，让普通应用也能使用系统级 API（静默安装、应用冻结、权限管理等）。

核心能力与上游 Shizuku 一致：

- 通过无线调试（Android 11+）、USB 或 Root 启动特权服务
- 按应用粒度授权，随时可撤销
- 提供与系统 API 几乎一致的调用体验

## 相对上游的改动

| 改动 | 说明 |
|------|------|
| 一键注入 | 首页新增"一键注入"卡片，服务运行时可一键为常用应用授权，免去逐个申请 |
| 品牌重塑 | 应用名 Shizako、全新图标与关于页面 |
| 版本体系 | 独立版本号 `zako2.1`，避免与上游版本混淆 |
| 合规改名 | applicationId 与自定义权限按上游许可要求全部更换（见下） |
| API 内置与双权限 | `api/` 由 git 子模块改为内置源码，并让库同时请求两个生态的权限，用这套库编译的应用可同时连接官方 Shizuku 与 Shizako |

## 与 Shizuku 生态的兼容性

### 底层就是 Shizuku API

Shizako 内置了 [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) 的完整源码（`api/`，MIT License，原为 git 子模块，现直接内置）。binder 协议、AIDL、`rikka.shizuku.Shizuku` 调用接口与上游完全同源，本地改动清单见 [api/SHIZAKO-CHANGES.md](api/SHIZAKO-CHANGES.md)。

### 双权限适配

依照上游 [Shizuku 许可声明](https://github.com/RikkaApps/Shizuku#license)（Apache 2.0 附加条款），fork 项目**不得**使用 `moe.shizuku.privileged.api` 作为 application id，也**不得**声明 `moe.shizuku.manager.permission.*` 权限。因此 Shizako 使用自己的名称：

- applicationId：`com.churan.shizako`
- API 权限：`com.churan.shizako.permission.API_V23`

在此基础上，内置 API 库为使用它的应用**同时请求两个生态的权限**（`moe.shizuku.manager.permission.API_V23` + `com.churan.shizako.permission.API_V23`）。两台服务器各自只向请求了自己权限的应用推送 binder、只授予自己定义的权限，因此：

- **用本仓库 `api/` 编译的应用**：官方 Shizuku 与 Shizako 都能连，装哪个用哪个，两者并存时任取其一
- **未改动的官方 `dev.rikka.shizuku:api` 应用**：照常连接官方 Shizuku（可与 Shizako 并存安装，互不干扰）

> 为什么不支持官方 API 应用直连 Shizako？上游许可明确禁止 fork 声明官方权限名；技术上，重复定义同名 runtime 权限也会在与官方 Shizuku 并存时产生"定义归属取决于安装顺序、卸载即失效"的冲突。双权限请求是兼顾合规与可用的路线。

### 开发者适配指南

把 `api/` 下的模块复制进你的项目并引入：

```gradle
// settings.gradle（四个模块即可：aidl ← shared ← api ← provider）
include ':aidl', ':shared', ':api', ':provider'
project(':aidl').projectDir = file('api/aidl')
project(':shared').projectDir = file('api/shared')
project(':api').projectDir = file('api/api')
project(':provider').projectDir = file('api/provider')

// app/build.gradle
implementation project(':api')
implementation project(':provider')
```

代码层面与官方 API 完全一致：`Shizuku.bindUserService(...)`、`Shizuku.requestPermission(...)` 等一行都不用改，只是把依赖坐标从 `dev.rikka.shizuku:api` 换成本地模块。你的应用从此同时兼容两个管理器。

## 下载

见本仓库 [Releases](../../releases)。

## 构建

```bash
git clone <this-repo>
cd Shizako

# 国内网络：仓库已内置阿里云镜像（settings.gradle），无需额外配置
./gradlew :manager:assembleRelease
```

要求：JDK 17+、Android SDK（`ANDROID_HOME` 环境变量）。产物位于 `manager/build/outputs/apk/release/`。

## 目录结构

- `manager/` — Android 应用本体（Kotlin）
- `server/` — 特权服务进程（Java，运行于 shell/root uid）
- `api/` — Shizuku-API 客户端库源码（[MIT License](api/LICENSE)），已内置并做双权限适配，改动见 [api/SHIZAKO-CHANGES.md](api/SHIZAKO-CHANGES.md)

## 致谢

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) 及其贡献者 — Shizako 的全部核心能力来自这个优秀的项目
- [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) — 客户端通信库

## License

本项目继承上游 Apache License 2.0，详见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。

依照 Apache 2.0 及上游附加条款：

- 原项目版权归 RikkaApps 所有；Shizako 的修改部分版权归 初然 所有
- 本项目未使用上游 `Shizuku` 名称、`moe.shizuku.privileged.api` application id、`moe.shizuku.manager.permission.*` 权限及上游图标文件
- `api/` 子模块遵循其自身的 MIT License
