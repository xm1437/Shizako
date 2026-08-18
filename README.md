<div align="center">

<img src="docs/banner.jpg" alt="Shizako酱抱着终端的横幅插画" width="820" />

# Shizako

**不用 Root，让猫耳看板娘替你使唤系统级 API**

*Your catgirl assistant for privileged Android APIs — no root required.*

基于 [Shizuku](https://github.com/RikkaApps/Shizuku) 二次开发 · 作者 [初然](https://github.com/xm1437)

[![Release](https://img.shields.io/github/v/release/xm1437/Shizako?style=flat-square&label=%E7%89%88%E6%9C%AC)](https://github.com/xm1437/Shizako/releases)
[![Downloads](https://img.shields.io/github/downloads/xm1437/Shizako/total?style=flat-square&label=%E4%B8%8B%E8%BD%BD)](https://github.com/xm1437/Shizako/releases)
[![Platform](https://img.shields.io/badge/Android-7.0%2B-34A853?style=flat-square&logo=android&logoColor=white)](https://github.com/xm1437/Shizako/releases)
[![Based on](https://img.shields.io/badge/%E5%9F%BA%E4%BA%8E-Shizuku%2013.x-7C6FD0?style=flat-square)](https://github.com/RikkaApps/Shizuku)
[![License](https://img.shields.io/github/license/xm1437/Shizako?style=flat-square)](LICENSE)

</div>

---

> 「你好呀，我是 **Shizako酱** `(ฅ'ω'ฅ)`
>
> 你的手机里有一大堆"系统限定"的好东西——静默安装、冻结应用、调权限、读系统设置——平时只有系统自己才碰得到。Root 当然能拿到钥匙，但代价不小。
>
> 我走了另一条路：先替你跑一个特权小进程（通过无线调试、USB 或 Root 唤醒我），然后把这个特权**借给你信任的应用**。不是开门揖盗哦——每个应用都要你点头才发通行证，随时可以收回。」

## 🐾 见面礼：她能做到的事

| 特性 | 说明 |
|------|------|
| 免 Root 特权 | 通过 ADB / 无线调试获得近似 Root 的能力，不拆机不刷机 |
| 通行证制度 | 每个应用第一次连接都要你亲自授权，随时反悔收回 |
| 官方生态直连 | 用官方 Shizuku-API 写的应用**一行代码不用改**就能连上她 |
| 一键注入 | 首页卡片一键把权限分给常用应用，不用一个个点 |
| 双管理器共存 | 和官方 Shizuku 同时安装互不干扰 |

## 🚀 带她回家

| 要求 | 说明 |
|------|------|
| 系统版本 | Android 7.0 及以上 |
| 无线调试激活 | 需 Android 11 及以上 |
| USB / Root 激活 | 没有额外限制 |

下载走 [Releases](https://github.com/xm1437/Shizako/releases) 拿最新版就行。激活教程可以参考上游 Shizuku 的[配置指南](https://shizuku.rikka.app/zh-hans/guide/setup/)，流程完全一致。

## 🔗 生态兼容：能连什么

### 底层就是 Shizuku 的 API

`api/` 目录里装着 [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) 的完整源码（MIT License）。通信协议、接口、调用方式，和上游用的是同一套东西。具体改了什么看 [api/SHIZAKO-CHANGES.md](api/SHIZAKO-CHANGES.md)。

### 为什么权限名不一样了

上游 Shizuku 的 [许可条款](https://github.com/RikkaApps/Shizuku#license) 说得很清楚：fork 项目不能占用 `moe.shizuku.manager.permission.*` 这套权限名，也不能用官方的包名。所以 Shizako 是：

| 项目 | Shizako 这边 |
|------|---------|
| 包名 | `com.churan.shizako` |
| 权限名 | `com.churan.shizako.permission.API_V23` |

### 官方生态应用也能直连

改了名字不代表改了脾气。用官方 `dev.rikka.shizuku:api` 写的应用（MT 管理器、冰箱、SystemUI Tuner 等），**不用改代码、不用重编译**，装好就能连上：

| 什么应用 | 连官方 Shizuku | 连 Shizako |
|----------|:---:|:---:|
| 官方 `dev.rikka.shizuku:api` 写的 | ✅ | ✅ |
| 用本仓库 `api/` 写的 | ✅ | ✅ |

秘密在于服务端——它认的是"你请求了权限"，而不是"你请求了哪个权限名"。每个应用第一次连的时候，Shizako 会弹出和上游一样的授权对话框，你点了同意才算数。整个过程没有声明任何上游权限名，老老实实遵守上游的许可条款。

## 🎨 关于看板娘

<div align="center">
<img src="manager/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="Shizako logo" />
</div>

Shizako酱是本项目的看板娘：白发猫耳、月牙发饰，名字来自 Shizu**ko**（Shizuku + 子）。项目图标、应用内文案和宣传物料都围绕她展开，欢迎拿去当表情包用 (ˊᗜˋ*)

## 🛠️ 给开发者

官方 `dev.rikka.shizuku:api` 已经能直连两个管理器，什么都不用改。

**API 怎么用？完整指南见 [docs/API.md](docs/API.md)** —— 依赖配置、权限申请、binder 生命周期、AIDL 用户服务全流程都有代码示例。

如果你更喜欢源码级依赖，把 `api/` 目录搬进项目就行：

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

代码怎么写？和官方 API 一模一样——`Shizuku.bindUserService(...)`、`Shizuku.requestPermission(...)` 该怎么写还怎么写。换了个依赖来源，你的应用就同时拥抱了两个生态。

## 📦 自己动手编译

```bash
git clone https://github.com/xm1437/Shizako.git
cd Shizako
./gradlew :manager:assembleRelease
```

| 你需要什么 | 版本 |
|----------|------|
| JDK | 17 及以上 |
| Android SDK | compileSdk 36 |
| NDK | 29.0.13113456 |

国内网络不用操心，仓库里已经配好了阿里云 Maven 镜像。编译产物在 `manager/build/outputs/apk/release/`，没配签名的话会自动用 debug 签名顶上。

## 🗂️ 代码长这样

```
Shizako/
├── manager/    Android 应用本体（Kotlin）
├── server/     特权服务进程（Java，跑在 shell / root 里）
├── starter/    服务启动器
├── shell/      预编译的 shell 工具
├── common/     共享模块
├── docs/       看板娘物料
└── api/        Shizuku-API 客户端库源码（MIT），已内置
```

## 💗 感谢

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) 和它的贡献者们——Shizako 的全部核心能力来自这里
- [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API)——客户端通信库
- 每一个给 Shizako酱点 Star 的你

## 📬 勾搭看板娘

遇到 Bug 想报、有了想法想聊、或者单纯想夸她可爱：

| 方式 | 入口 |
|------|------|
| QQ 反馈 | [891276089](https://qm.qq.com/cgi-bin/qm/qr?k=891276089) |
| QQ 吹水群 | [1104445003](https://qm.qq.com/cgi-bin/qm/qr?k=1104445003) |
| GitHub Issues | [Issues](https://github.com/xm1437/Shizako/issues) |

## 📄 许可

本项目继承上游 [Apache License 2.0](LICENSE)，另见 [NOTICE](NOTICE)。

- 原项目版权归 RikkaApps 所有，Shizako 的修改部分版权归 初然 所有
- 未使用上游 `Shizuku` 名称、`moe.shizuku.privileged.api` 包名、`moe.shizuku.manager.permission.*` 权限及上游图标
- `api/` 目录遵循其自身的 MIT License

---

<div align="center">

<sub>English summary: Shizako is an Apache-2.0 licensed fork of Shizuku with an original catgirl mascot. It runs a privileged service via ADB / wireless debugging / root and lends elevated APIs to apps you trust — including apps built with the official `dev.rikka.shizuku:api`, which connect without any code change.</sub>

</div>
