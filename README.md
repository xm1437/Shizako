<div align="center">

<img src="manager/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="Shizako logo" />

# Shizako

免 Root，让普通应用用上系统级特权 API

基于开源项目 [Shizuku](https://github.com/RikkaApps/Shizuku) 二次开发 · 作者 [初然](https://github.com/xm1437)

[![Release](https://img.shields.io/github/v/release/xm1437/Shizako?style=flat-square&label=%E7%89%88%E6%9C%AC)](https://github.com/xm1437/Shizako/releases)
[![Downloads](https://img.shields.io/github/downloads/xm1437/Shizako/total?style=flat-square&label=%E4%B8%8B%E8%BD%BD)](https://github.com/xm1437/Shizako/releases)
[![Platform](https://img.shields.io/badge/Android-6.0%2B-34A853?style=flat-square&logo=android&logoColor=white)](https://github.com/xm1437/Shizako/releases)
[![Based on](https://img.shields.io/badge/%E5%9F%BA%E4%BA%8E-Shizuku%2013.x-3F51B5?style=flat-square)](https://github.com/RikkaApps/Shizuku)
[![License](https://img.shields.io/github/license/xm1437/Shizako?style=flat-square)](LICENSE)

</div>

---

## 它能做什么

Root 不是获取特权的唯一途径。Shizako 把 ADB 或 Root 启动的特权进程安全地共享给你信任的应用：静默安装、应用冻结、权限管理、读取系统设置——这些原本只有系统进程能做的事，普通应用在获得授权后同样可以完成，体验与调用系统 API 几乎一致。

**核心能力：**

- 通过无线调试（Android 11+）、USB 连接或 Root 启动特权服务
- 按应用粒度授权，随时撤销，权限明细一目了然
- 首页新增「一键注入」卡片：服务运行时一键为常用应用授权，免去逐个申请
- 完整保留上游的终端（rish）、开机自启、多用户支持

## 下载

前往 [Releases](https://github.com/xm1437/Shizako/releases) 获取最新 APK。

| 要求 | 说明 |
|------|------|
| 系统版本 | Android 6.0 及以上 |
| 无线调试激活 | 需 Android 11 及以上 |
| USB / Root 激活 | 无额外要求 |

## 与上游 Shizuku 的差异

| 项目 | 说明 |
|------|------|
| 一键注入 | 首页新增授权卡片，服务运行时批量授权常用应用 |
| 双权限 API | 内置 API 库同时请求两个生态的权限，见下文 |
| 品牌与版本 | 独立名称、图标与版本号 `zako2.1` |
| 合规改名 | applicationId 与自定义权限全部更换，见下文 |

## 兼容性说明

### 底层就是 Shizuku API

`api/` 目录内置了 [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) 的完整源码（MIT License）。binder 协议、AIDL 接口、`rikka.shizuku.Shizuku` 调用方式与上游完全同源，本地改动清单见 [api/SHIZAKO-CHANGES.md](api/SHIZAKO-CHANGES.md)。

### 为什么改了权限名

上游 Shizuku 的 [许可声明](https://github.com/RikkaApps/Shizuku#license)（Apache 2.0 附加条款）禁止任何 fork 声明 `moe.shizuku.manager.permission.*` 权限或使用 `moe.shizuku.privileged.api` 作为 application id。因此 Shizako 使用自己的命名空间：

| 项 | Shizako |
|----|---------|
| applicationId | `com.churan.shizako` |
| API 权限 | `com.churan.shizako.permission.API_V23` |

### 双权限适配

内置 API 库为使用它的应用同时请求两个生态的权限。每台特权服务只向请求了自己权限的应用推送 binder、只授予自己定义的权限，互不干扰：

| 应用类型 | 官方 Shizuku | Shizako |
|----------|:---:|:---:|
| 用本仓库 `api/` 编译的应用 | ✅ 可连接 | ✅ 可连接 |
| 官方 `dev.rikka.shizuku:api` 应用 | ✅ 可连接 | ❌ 无法连接 |

两个管理器可并存安装，互不影响。官方 API 应用如需同时连接 Shizako，换用本仓库的 `api/` 重新编译即可。

## 开发者适配

把 `api/` 目录复制进你的项目，替换原有依赖：

```gradle
// settings.gradle
include ':aidl', ':shared', ':api', ':provider'
project(':aidl').projectDir = file('api/aidl')
project(':shared').projectDir = file('api/shared')
project(':api').projectDir = file('api/api')
project(':provider').projectDir = file('api/provider')

// app/build.gradle
implementation project(':api')
implementation project(':provider')
```

代码层面与官方 API 完全一致，`Shizuku.bindUserService(...)`、`Shizuku.requestPermission(...)` 等一行不用改——只是换了依赖来源，你的应用从此同时兼容两个管理器。

## 从源码构建

```bash
git clone https://github.com/xm1437/Shizako.git
cd Shizako
./gradlew :manager:assembleRelease
```

| 环境要求 | 版本 |
|----------|------|
| JDK | 17 及以上 |
| Android SDK | compileSdk 36 |
| NDK | 29.0.13113456 |

国内网络无需额外配置，仓库已内置阿里云 Maven 镜像。产物位于 `manager/build/outputs/apk/release/`，未配置签名时自动回退 debug 签名。

## 项目结构

```
Shizako/
├── manager/    Android 应用本体（Kotlin）
├── server/     特权服务进程（Java，运行于 shell / root uid）
├── starter/    服务启动器
├── shell/      预编译的 shell 工具
├── common/     共享模块
└── api/        Shizuku-API 客户端库源码（MIT），已内置并做双权限适配
```

## 致谢

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) 及其贡献者——Shizako 的全部核心能力来自这个项目
- [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API)——客户端通信库

## 许可

本项目继承上游 [Apache License 2.0](LICENSE)，另见 [NOTICE](NOTICE)。

- 原项目版权归 RikkaApps 所有；Shizako 的修改部分版权归 初然 所有
- 未使用上游 `Shizuku` 名称、`moe.shizuku.privileged.api` application id、`moe.shizuku.manager.permission.*` 权限及上游图标文件
- `api/` 目录遵循其自身的 MIT License
