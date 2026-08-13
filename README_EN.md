# CLaJ (Copy Link & Join) — Minecraft 26.2 / Fabric

<div align="center">

[中文](README.md)

</div>

> 🛠 **This mod was created by the `DeepSeek-V4-Flash-0731` model** — ported the CLaJ protocol layer, designed the Minecraft loopback bridge transport, wrote the UI / Mixins / build scripts, and completed multiple rounds of debugging and optimization.

> Play Minecraft 26.2 with friends as easily as a LAN party: create a room, share a `claj://` link, and friends can join your single-player world directly over the internet — no port forwarding needed.

> This project is a Minecraft Java Edition port of [xpdustry/claj](https://github.com/xpdustry/claj), reusing its game-agnostic protocol layer (the `common` + `api` modules). It speaks CLaJ protocol version 2.4.x and is interoperable with the Mindustry version of CLaJ and existing relay servers.

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
