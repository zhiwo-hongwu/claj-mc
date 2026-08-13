# CLaJ（复制链接并加入）— Minecraft 26.2 / Fabric

<div align="center">

<a href="#zh-cn"><kbd>🇨🇳 中文</kbd></a>
&nbsp;&nbsp;·&nbsp;&nbsp;
<a href="#en-us"><kbd>🇺🇸 English</kbd></a>

<sub>点击按钮切换到对应语言 / Click a button to switch language</sub>

</div>

> 🛠 **本模组由 `DeepSeek-V4-Flash-0731` 模型制作** —— 移植 CLaJ 协议层、设计 Minecraft 回环桥接传输、编写 UI / Mixin / 构建脚本，并完成多轮调试与优化。
> 🛠 **This mod was created by the `DeepSeek-V4-Flash-0731` model** — ported the CLaJ protocol layer, designed the Minecraft loopback bridge transport, wrote the UI / Mixins / build scripts, and completed multiple rounds of debugging and optimization.

> 在 Minecraft 26.2 里像开黑一样轻松联机：创建房间、分享 `claj://` 链接，好友无需端口映射即可如局域网般直接加入你的单人世界。
> Play Minecraft 26.2 with friends as easily as a LAN party: create a room, share a `claj://` link, and friends can join your single-player world directly over the internet — no port forwarding needed.

> 本项目是 [xpdustry/claj](https://github.com/xpdustry/claj) 的 Minecraft Java Edition 移植版，复用其游戏无关的协议层（`common` + `api` 模块），协议版本 2.4.x，与 Mindustry 版 CLaJ 及现有中继服务器互通。
> This project is a Minecraft Java Edition port of [xpdustry/claj](https://github.com/xpdustry/claj), reusing its game-agnostic protocol layer (the `common` + `api` modules). It speaks CLaJ protocol version 2.4.x and is interoperable with the Mindustry version of CLaJ and existing relay servers.

---

<a id="zh-cn"></a>
## 🇨🇳 中文

## ✨ 功能特性

- **一键建房**：在任意公共 / 自建 CLaJ 中继上创建房间，自动复制链接
- **自动局域网**：建房时自动对局域网开放（启动 TCP 监听），关房自动关闭
- **通过链接加入**：主界面 → 多人游戏 → 「通过 CLaJ 加入」，粘贴链接即可（无需按键、无需先开世界）
- **中继服务器列表**：滚动列表（无行数上限），显示名称、地址、**延迟(ms)**、在线 / 离线、版本兼容，自定义服务器可点击删除
- **公共房间浏览器**：按服务器分组显示自己服务器的公共房间（房间名、人数、模式、版本、加密标记），点击即加入
- **房间密码**：4 位数字 PIN（可选）
- **暂停菜单**：「管理 CLaJ 房间」按钮 + 默认 `K` 键快捷入口
- **完整中文 / 英文**：内置 `zh_cn` / `en_us` 语言文件
- **在线模式友好**：回环 CLaJ 连接自动走离线登录（可配置，见下文）

## 📦 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/)（版本 ≥ 0.19.3）与 [Fabric API](https://modrinth.com/mod/fabric-api)（`0.157.0+26.2`）
2. 将 `claj-mc-<version>.jar` 放入 `.minecraft/mods/`
3. 启动游戏，进入 Mods 列表确认 **CLaJ（复制链接并加入）** 已加载

> 房主与所有加入者都需安装本模组。

## 🎮 使用说明

### 房主（创建房间）

1. 进入一个**单人世界**
2. 暂停菜单右上角 → 「管理 CLaJ 房间」
3. 选择中继服务器 → 「创建房间」（世界会自动对局域网开放）
4. 复制链接发给朋友

### 加入者（加入房间）

1. 主界面 → 多人游戏 → 左下角「通过 CLaJ 加入」
2. 粘贴 `claj://主机:端口/房间ID` 链接（加密房间输入 4 位 PIN）
3. 或从「浏览全部房间」中选择一个公共房间

> ⚠️ 持有链接即可加入（在线模式校验已旁路）——请勿公开分享链接，与原版 CLaJ 一致。

## 🔨 构建

环境要求：**JDK 25**、可访问网络（Maven Central 走阿里云镜像，Arc 走 JitPack）。

```bash
# 一键构建模组 + 中继服务器（仿 xpdustry/claj 的 release 任务），产物输出到 build/release/
./gradlew release

# 或分别构建
./gradlew build                  # 模组 → build/libs/claj-mc-<version>.jar
./gradlew :server:build          # 中继服务器 → server/build/libs/claj-server.jar
```

自建中继服务器：

```bash
java -jar claj-server.jar <端口>   # 例：java -jar claj-server.jar 50000
```

> ⚠️ 中继服务器需要**同时开放 TCP 与 UDP** 端口（TCP 用于数据转发，UDP 用于客户端心跳与服务器发现）。若 UDP 被 NAT/防火墙丢弃，客户端将无法连接/探测到该节点。
> CLaJ 中继是带宽大户（平均约 1MB/s / 节点），公共节点列表见 [public-servers.hjson](https://github.com/xpdustry/claj/blob/main/public-servers.hjson)。

## 🏗 工作原理

采用**本地回环桥接**设计，Minecraft 协议字节全程透明（CLaJ 不解析任何协议内容）：

```
加入者MC客户端 ─TCP→ 本机监听 ─拆帧→ ArcNet客户端 ─CLaJ帧→ 中继服务器(纯字节搬运)
房主集成服务器 ←TCP─ 回环桥接 ─组帧─ ArcNet代理 ←CLaJ帧──┘
```

- **房主端**（`MinecraftClajProxy` + `LoopbackBridge`）：每个远程玩家对应一条连向本机集成服务器的真实回环 TCP，服务器只看到普通网络客户端
- **加入端**（`MinecraftClajJoiner` + `RelayClient`）：将 Minecraft 客户端重定向到本机监听端口，经 ArcNet 连接中继并发送 CLaJ 加入包登记入房，随后纯字节双向泵
- **方向语义**：加入者 → 房主按帧传输（剥 / 补长度前缀）；房主 → 加入者按字节流分块（无大小限制，支持大世界同步）
- **帧大小限制**：加入者 → 房主方向的单帧上限为 24KB（中继排队上限 8KB）。原版客户端帧都很小，但**极端大型模组包**（如超大自定义负载）可能超过该限制导致断连——大型模组整合包客户端可能不兼容

### 模块结构

```
src/main/java/
├── com/xpdustry/claj/   # 移植的 CLaJ 协议层（common + api，仅依赖 Arc）
└── zhiwo/claj/          # Minecraft 实现
    ├── join/            # 加入者桥接（RelayClient / 序列化器）
    ├── proxy/           # 房主代理（MinecraftClajProxy / LoopbackBridge）
    ├── screen/          # UI（加入 / 管理 / 浏览器 / 设置 / 滚动服务器列表）
    ├── mixin/           # 在线模式旁路、多人游戏与暂停菜单按钮
    ├── state/           # 房间状态编解码（浏览器展示）
    └── transport/       # Minecraft 帧工具
server/                  # CLaJ 中继服务器（Gradle 子项目，./gradlew release 一并构建）
```

## ⚙️ 配置

配置文件位于 `config/claj.json`（首次运行时生成）：

| 字段 | 说明 |
|---|---|
| `customServers` | 自定义中继服务器（名称 → `主机:端口`） |
| `roomPublic` | 房间是否公开（显示在房间浏览器） |
| `roomProtected` | 房间是否需要密码 |
| `roomPassword` | 4 位数字 PIN |
| `lanPort` | 自动开局域网使用的端口（`0` = 默认 25565） |
| `onlineModeBypass` | 是否旁路在线模式校验（回环连接走离线登录，默认 `true`） |

## 🧩 协议兼容

- CLaJ 协议版本 `2.4.2`（major `4`，与 2.4.x 中继一致）
- 实现类型 `"Minecraft"`（房间按类型隔离，与 Mindustry 房间互不干扰）
- 与旧版 scheme-size CLaJ 不兼容

## ⚠️ 安全与信任说明

使用前请知悉以下设计前提（CLaJ 与原版一致，属信任模型而非缺陷）：

- **中继节点与房主均为可信任实体**：所有流量（含房间数据）为**明文 TCP 传输，无加密**。中继管理员或能篡改流量者理论上可注入任意协议字节——请只使用可信的中继节点（如公共列表中的官方/知名节点）。
- **在线模式旁路默认开启**：回环 CLaJ 连接跳过 Mojang 会话验证，**本机任意进程**（如已植入的木马）可无验证连接 `127.0.0.1:<端口>` 进入你的世界。可在 `config/claj.json` 中将 `onlineModeBypass` 设为 `false` 关闭。
- **房间密码不是加密**：4 位 PIN 仅用于防误入/反滥用，明文传输且空间仅 10000，不可作为安全机制。
- **链接即凭证**：持有链接即可加入，请勿公开分享。

## 🤝 贡献

欢迎提交 Issue 与 Pull Request。开发工作流：

```bash
# 修改代码 → 构建 → 回归测试
./gradlew release
# 清理构建缓存，保持仓库干净
rm -rf .gradle build server/build plugins
```

## 📄 许可

[GPL-3.0](./LICENSE)

移植的协议与服务器代码 © [Xpdustry](https://github.com/xpdustry)。

## 🙏 致谢

- [xpdustry/claj](https://github.com/xpdustry/claj) — 原始 CLaJ 项目（协议 / 服务器 / API）
- [Anuken/Arc](https://github.com/Anuken/Arc) — 底层网络框架
- [FabricMC](https://fabricmc.net/) — 模组加载器与工具链

---

<a id="en-us"></a>
## 🇺🇸 English

## ✨ Features

- **One-click room creation**: create a room on any public / self-hosted CLaJ relay, link copied to clipboard automatically
- **Automatic LAN exposure**: opening a room automatically opens the world to LAN (starts a TCP listener); closing the room stops it
- **Join via link**: Main menu → Multiplayer → "Join via CLaJ", paste the link (no key press needed, no need to open a world first)
- **Relay server list**: scrollable list (no row limit) showing name, address, **latency (ms)**, online / offline status and version compatibility; custom servers can be removed with a click
- **Public room browser**: public rooms of your servers grouped by relay (room name, player count, mode, version, encryption flag); click to join
- **Room password**: optional 4-digit PIN
- **Pause menu**: "Manage CLaJ Rooms" button plus a default `K` key shortcut
- **Full Chinese / English localization**: built-in `zh_cn` / `en_us` language files
- **Online-mode friendly**: loopback CLaJ connections automatically use offline login (configurable, see below)

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) (≥ 0.19.3) and [Fabric API](https://modrinth.com/mod/fabric-api) (`0.157.0+26.2`)
2. Put `claj-mc-<version>.jar` into `.minecraft/mods/`
3. Launch the game and confirm **CLaJ (Copy Link & Join)** is listed in the Mods screen

> Both the host and every joiner must install this mod.

## 🎮 Usage

### Host (create a room)

1. Enter a **single-player world**
2. Pause menu, top right → "Manage CLaJ Rooms"
3. Pick a relay server → "Create Room" (the world is automatically exposed on LAN)
4. Copy the link and send it to your friends

### Joiner (join a room)

1. Main menu → Multiplayer → "Join via CLaJ" (bottom left)
2. Paste a `claj://host:port/roomId` link (enter the 4-digit PIN for protected rooms)
3. Or pick a public room from "Browse All Rooms"

> ⚠️ Anyone holding the link can join (online-mode verification is bypassed) — do not share links publicly, same as vanilla CLaJ.

## 🔨 Building

Requirements: **JDK 25** and network access (Maven Central via Aliyun mirror, Arc via JitPack).

```bash
# One-command build of the mod and the relay server (mirrors xpdustry/claj's release task); output goes to build/release/
./gradlew release

# Or build them separately
./gradlew build                  # mod → build/libs/claj-mc-<version>.jar
./gradlew :server:build          # relay server → server/build/libs/claj-server.jar
```

Self-host a relay server:

```bash
java -jar claj-server.jar <port>   # e.g. java -jar claj-server.jar 50000
```

> ⚠️ A relay server must expose **both TCP and UDP** ports (TCP carries data forwarding, UDP carries client heartbeats and server discovery). If UDP is dropped by NAT / firewalls, clients will not be able to connect to / discover the node.
> CLaJ relays are bandwidth-heavy (about 1 MB/s per node on average); the public node list lives at [public-servers.hjson](https://github.com/xpdustry/claj/blob/main/public-servers.hjson).

## 🏗 How it works

It uses a **local loopback bridge** design: Minecraft protocol bytes stay transparent end to end (CLaJ never parses any protocol content):

```
Joiner MC client ─TCP→ local listener ─deframing→ ArcNet client ─CLaJ frames→ relay server (pure byte forwarding)
Host integrated server ←TCP─ loopback bridge ─framing─ ArcNet proxy ←CLaJ frames──┘
```

- **Host side** (`MinecraftClajProxy` + `LoopbackBridge`): every remote player is a real loopback TCP connection to the local integrated server, which only sees ordinary network clients
- **Joiner side** (`MinecraftClajJoiner` + `RelayClient`): redirects the Minecraft client to the local listening port, connects to the relay over ArcNet, sends a CLaJ join packet to register into the room, then pumps raw bytes in both directions
- **Direction semantics**: joiner → host transfers frames (length prefixes stripped / re-added); host → joiner transfers a byte stream in chunks (no size limit, supports big world sync)
- **Frame size limit**: the joiner → host direction caps each frame at 24KB (relay queue cap 8KB). Vanilla client frames are tiny, but **extreme modpacks** (e.g. very large custom payloads) may exceed this limit and drop the connection — heavily modded clients may be incompatible

### Module layout

```
src/main/java/
├── com/xpdustry/claj/   # ported CLaJ protocol layer (common + api, Arc-only)
└── zhiwo/claj/          # Minecraft implementation
    ├── join/            # joiner bridge (RelayClient / serializers)
    ├── proxy/           # host proxy (MinecraftClajProxy / LoopbackBridge)
    ├── screen/          # UI (join / manage / browser / settings / scrolling server list)
    ├── mixin/           # online-mode bypass, multiplayer & pause menu buttons
    ├── state/           # room state codec (browser display)
    └── transport/       # Minecraft frame utilities
server/                  # CLaJ relay server (Gradle subproject, built by ./gradlew release)
```

## ⚙️ Configuration

The configuration file lives at `config/claj.json` (generated on first run):

| Field | Description |
|---|---|
| `customServers` | custom relay servers (name → `host:port`) |
| `roomPublic` | whether the room is public (shown in the room browser) |
| `roomProtected` | whether the room requires a password |
| `roomPassword` | 4-digit PIN |
| `lanPort` | port used for automatic LAN exposure (`0` = default 25565) |
| `onlineModeBypass` | whether to bypass online-mode verification (loopback connections use offline login, default `true`) |

## 🧩 Protocol compatibility

- CLaJ protocol version `2.4.2` (major `4`, matching 2.4.x relays)
- Implementation type `"Minecraft"` (rooms are isolated by type and never interfere with Mindustry rooms)
- Not compatible with the old scheme-size CLaJ

## ⚠️ Security & trust notes

Please understand the following design assumptions before use (identical to vanilla CLaJ — a trust model, not a flaw):

- **Relay nodes and hosts are trusted entities**: all traffic (including room data) is **plaintext TCP, no encryption**. Relay admins — or anyone able to tamper with the traffic — could in theory inject arbitrary protocol bytes. Only use trusted relays (e.g. the official / well-known nodes on the public list).
- **Online-mode bypass is enabled by default**: loopback CLaJ connections skip Mojang session verification, so **any local process** (e.g. an installed trojan) can connect to `127.0.0.1:<port>` without verification and enter your world. Disable it by setting `onlineModeBypass` to `false` in `config/claj.json`.
- **Room passwords are not encryption**: the 4-digit PIN only keeps strangers / abusers out; it is transmitted in plaintext and has only 10000 combinations — treat it as a deterrent, not a security mechanism.
- **The link is the credential**: anyone holding the link can join; do not share it publicly.

## 🤝 Contributing

Issues and pull requests are welcome. Development workflow:

```bash
# edit code → build → regression tests
./gradlew release
# clean build caches, keep the repo tidy
rm -rf .gradle build server/build plugins
```

## 📄 License

[GPL-3.0](./LICENSE)

Ported protocol and server code © [Xpdustry](https://github.com/xpdustry).

## 🙏 Acknowledgements

- [xpdustry/claj](https://github.com/xpdustry/claj) — the original CLaJ project (protocol / server / API)
- [Anuken/Arc](https://github.com/Anuken/Arc) — the underlying networking framework
- [FabricMC](https://fabricmc.net/) — the mod loader & toolchain
