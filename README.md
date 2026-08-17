# CLaJ（复制链接并加入）— Minecraft 26.2 / Fabric

<div align="center">

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg)](https://fabricmc.net/)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)

[English](README_EN.md) · [简体中文](README.md)

</div>

> 🛠 **本模组由 `DeepSeek-V4-Flash-0731` 模型制作** —— 移植 CLaJ 协议层、设计 Minecraft 回环桥接传输、编写 UI / Mixin / 构建脚本，并完成多轮调试与优化。

在 Minecraft 26.2 里像开黑一样轻松联机：创建房间、分享 `claj://` 链接，好友无需内网穿透与端口映射即可如局域网般直接加入你的单人世界。

本项目是 [xpdustry/claj](https://github.com/xpdustry/claj) 的 Minecraft Java Edition 适配版，复用其游戏无关的高效协议层（`common` + `api` 模块），协议版本 2.4.x，与 Mindustry 版 CLaJ 及现有中继服务器完全互通。

---

## ✨ 功能特性

- **一键建房**：在任意公共 / 自建 CLaJ 中继上创建房间，自动复制链接到剪贴板。
- **自动局域网**：建房时自动对局域网开放（启动 TCP 监听），房间关闭时自动关闭。
- **通过链接加入**：主界面 → 多人游戏 → 左下角「通过 CLaJ 加入」，粘贴链接即可直接进入。
- **中继服务器列表**：原生滚动列表（无行数限制），实时显示名称、地址、**延迟 (ms)**、在线 / 离线状态与版本兼容性；支持一键添加与删除自定义服务器。
- **公共房间浏览器**：按服务器分组展示公共房间（房间名、在线/最大人数、游戏模式、版本号、加密锁标记），点击即刻加入。
- **房间密码保护**：可选 4 位数字 PIN 码。
- **快捷入口**：游戏内暂停菜单右上角「管理 CLaJ 房间」按钮，并支持默认 `K` 键快捷键。
- **双语本地化**：完整支持简体中文 (`zh_cn`) 与英文 (`en_us`)。
- **在线模式友好**：回环 CLaJ 连接自动走离线登录路径，解决联机认证 IP 不匹配问题（可配置）。

---

## 📦 安装与使用

### 客户端依赖
1. 安装 [Fabric Loader](https://fabricmc.net/use/)（版本 ≥ 0.19.3）与 [Fabric API](https://modrinth.com/mod/fabric-api)（`0.157.0+26.2`）。
2. 将 `claj-mc-<version>.jar` 放入 `.minecraft/mods/` 文件夹。
3. 启动游戏确认模组已成功加载。

> 💡 **提示**：房主与所有加入者均需安装本模组。

### 房主操作（创建房间）
1. 进入任意**单人世界**。
2. 按 `Esc` 打开暂停菜单，点击右上角「管理 CLaJ 房间」（或按快捷键 `K`）。
3. 选择一个中继服务器，点击「创建房间」。
4. 房间链接将自动复制到剪贴板，发送给好友即可。

### 加入者操作（加入房间）
1. 打开游戏主界面 → 「多人游戏」 → 点击左下角「通过 CLaJ 加入」。
2. 粘贴好友发给你的 `claj://主机:端口/房间ID` 链接（如设有密码则输入 4 位 PIN）。
3. 或点击「浏览全部房间」选择公开房间直接加入。

---

## 🏗 技术架构与工作原理

采用**本地回环桥接（Local Loopback Bridge）**设计，Minecraft 协议字节全程透明透传（CLaJ 不侵入解析 Minecraft 内部数据包）：

```
[加入者 MC 客户端] ──TCP──> [本机监听器] ──拆帧──> [RelayClient (ArcNet)]
                                                     │
                                               (CLaJ 协议帧)
                                                     ▼
                                          [CLaJ 中继服务器 (Relay)]
                                                     │
                                               (CLaJ 协议帧)
                                                     ▼
[房主集成服务端]  <──TCP──  [LoopbackBridge] <──组帧── [MinecraftClajProxy]
```

- **房主端**（`MinecraftClajProxy` + `LoopbackBridge`）：为每个远程 CLaJ 客户端在本机建立真实的 TCP 回环连接，集成服务端将其视作标准的局域网连接。
- **加入端**（`MinecraftClajJoiner` + `RelayClient`）：在本地开启临时 TCP 监听，并将客户端流量通过 ArcNet 经中继转发至房主端。
- **传输优化**：加入端至房主端按帧透明解包与重新组帧；房主端至加入端按流分块（无大小限制，保障世界数据同步无阻）。
- **帧大小限制**：加入者 → 房主方向的单帧上限为 24KB（中继排队上限 8KB）。原版客户端帧都很小，但**极端大型模组包**（如超大自定义负载）可能超过该限制导致断连——大型模组整合包客户端可能不兼容。

---

## 🔨 构建与测试

### 环境要求
- **JDK 25**
- 支持网络访问（依赖通过阿里云镜像与 JitPack 解析）

### 构建命令
```bash
# 运行单元测试
./gradlew test

# 一键构建 Fabric 模组与独立中继服务端 (产物输出至 build/release/)
./gradlew release

# 单独构建
./gradlew build             # 模组 -> build/libs/claj-mc-<version>.jar
./gradlew :server:build     # 中继服务端 -> server/build/libs/claj-server.jar
```

### 自建中继服务器
```bash
java -jar claj-server.jar <端口>   # 例：java -jar claj-server.jar 50000
```
> ⚠️ **注意**：中继服务器需**同时开放 TCP 和 UDP 端口**（TCP 用于数据中继，UDP 用于心跳探测与节点发现）。

---

## ⚙️ 配置文件

模组配置文件位于 `.minecraft/config/claj.json`：

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `customServers` | Map | `{}` | 自定义中继服务器列表 (`名称: 主机:端口`) |
| `roomPublic` | boolean | `true` | 是否将房间公开发布到房间浏览器 |
| `roomProtected` | boolean | `false` | 是否开启 4 位 PIN 码房间密码保护 |
| `roomPassword` | int | `0` | 房间密码 (0000 - 9999) |
| `lanPort` | int | `0` | 自动开启局域网时使用的端口 (`0` 为默认 25565) |
| `onlineModeBypass` | boolean | `true` | 是否对本地回环连接旁路在线模式验证 |

---

## ⚠️ 安全与信任说明

使用前请知悉以下设计前提（CLaJ 与原版一致，属信任模型而非缺陷）：

- **中继节点与房主均为可信任实体**：所有流量（含房间数据）为**明文 TCP 传输，无加密**。中继管理员或能篡改流量者理论上可注入任意协议字节——请只使用可信的中继节点（如公共列表中的官方/知名节点）。
- **在线模式旁路默认开启**：回环 CLaJ 连接跳过 Mojang 会话验证，**本机任意进程**（如已植入的木马）可无验证连接 `127.0.0.1:<端口>` 进入你的世界。可在 `config/claj.json` 中将 `onlineModeBypass` 设为 `false` 关闭。
- **房间密码不是加密**：4 位 PIN 仅用于防误入/反滥用，明文传输且空间仅 10000，不可作为安全机制。
- **链接即凭证**：持有链接即可加入，请勿公开分享。

---

## 📄 开源许可

本项目采用 [GNU General Public License v3.0](./LICENSE) 协议开源。

- 移植的底层协议与服务端代码 © [Xpdustry](https://github.com/xpdustry)
- 底层网络驱动 © [Anuken/Arc](https://github.com/Anuken/Arc)
