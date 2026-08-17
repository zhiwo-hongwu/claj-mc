# CLaJ (Copy Link & Join) — Minecraft 26.2 / Fabric

<div align="center">

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg)](https://fabricmc.net/)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)

[English](README_EN.md) · [简体中文](README.md)

</div>

> 🛠 **This mod was created by the `DeepSeek-V4-Flash-0731` model** — ported the CLaJ protocol layer, designed the Minecraft loopback bridge transport, wrote the UI / Mixins / build scripts, and completed multiple rounds of debugging and optimization.

Play Minecraft 26.2 with friends as easily as a LAN party: create a room, share a `claj://` link, and friends can join your single-player world directly over the internet — no port forwarding or complex tunneling required.

This project is a Minecraft Java Edition port of [xpdustry/claj](https://github.com/xpdustry/claj), reusing its high-performance, game-agnostic protocol layer (`common` + `api` modules). It speaks CLaJ protocol version 2.4.x and is fully interoperable with the Mindustry version of CLaJ and existing relay servers.

---

## ✨ Features

- **One-click Room Creation**: Create a room on any public or self-hosted CLaJ relay server; link is automatically copied to clipboard.
- **Automatic LAN Exposure**: Automatically exposes the single-player world to LAN (starts TCP listener) when a room is created, and closes it when the room shuts down.
- **Join via Link**: Main menu → Multiplayer → "Join via CLaJ" (bottom left), paste the link to connect instantly.
- **Relay Server List**: Native scrollable list (no row limit) displaying server name, address, **ping latency (ms)**, online status, and version compatibility; easily add or delete custom servers.
- **Public Room Browser**: Browse public rooms grouped by relay server (room name, player count, game mode, version, lock badge); click to join directly.
- **Password Protection**: Optional 4-digit PIN for private rooms.
- **Quick Access**: "Manage CLaJ Room" button in pause screen and configurable `K` key shortcut.
- **Full Localization**: English (`en_us`) and Simplified Chinese (`zh_cn`).
- **Online Mode Friendly**: Loopback bridge connections automatically bypass Mojang session verification to avoid IP mismatch errors (configurable).

---

## 📦 Installation & Usage

### Prerequisites
1. Install [Fabric Loader](https://fabricmc.net/use/) (≥ 0.19.3) and [Fabric API](https://modrinth.com/mod/fabric-api) (`0.157.0+26.2`).
2. Place `claj-mc-<version>.jar` into `.minecraft/mods/`.
3. Launch Minecraft and verify the mod is loaded.

> 💡 **Note**: Both the host and joining players must have this mod installed.

### Host (Create Room)
1. Load into any **single-player world**.
2. Press `Esc` and click "Manage CLaJ Room" in the top-right corner (or press `K`).
3. Select a relay server and click "Create Room".
4. The shareable `claj://` link will be copied to your clipboard automatically.

### Joiner (Join Room)
1. From the main menu, go to **Multiplayer** → click **Join via CLaJ** (bottom left).
2. Paste the `claj://host:port/roomId` link (and 4-digit PIN if required).
3. Alternatively, open **Browse All Rooms** to select and join any public room.

---

## 🏗 Architecture & Design

Uses a **Local Loopback Bridge** architecture that keeps Minecraft protocol bytes transparent end-to-end:

```
[Joiner MC Client] ──TCP──> [Local Listener] ──Deframing──> [RelayClient (ArcNet)]
                                                                    │
                                                              (CLaJ Frames)
                                                                    ▼
                                                         [CLaJ Relay Server]
                                                                    │
                                                              (CLaJ Frames)
                                                                    ▼
[Host Integrated Server] <──TCP── [LoopbackBridge] <──Framing── [MinecraftClajProxy]
```

- **Host Side** (`MinecraftClajProxy` + `LoopbackBridge`): Creates an actual TCP loopback connection to the host's integrated server for every remote CLaJ player.
- **Joiner Side** (`MinecraftClajJoiner` + `RelayClient`): Redirects the client to a local listening port and bridges bytes across ArcNet through the relay.
- **Stream Optimization**: Joiner-to-host transfers are framed and re-framed cleanly; host-to-joiner transfers stream in raw chunks without arbitrary payload size caps.
- **Frame size limit**: The joiner → host direction caps each frame at 24KB (relay queue cap 8KB). Vanilla client frames are tiny, but **extreme modpacks** (e.g. very large custom payloads) may exceed this limit and drop the connection — heavily modded clients may be incompatible.

---

## 🔨 Building & Testing

### Requirements
- **JDK 25**

### Commands
```bash
# Run unit tests
./gradlew test

# Build both mod jar and standalone server jar (outputs to build/release/)
./gradlew release

# Build separately
./gradlew build             # Mod -> build/libs/claj-mc-<version>.jar
./gradlew :server:build     # Relay -> server/build/libs/claj-server.jar
```

### Running Standalone Relay Server
```bash
java -jar claj-server.jar <port>   # e.g., java -jar claj-server.jar 50000
```
> ⚠️ **Important**: Relay servers must expose **both TCP and UDP** on the chosen port.

---

## ⚙️ Configuration

Configuration is located at `.minecraft/config/claj.json`:

| Key | Type | Default | Description |
|---|---|---|---|
| `customServers` | Map | `{}` | Custom relay server mappings (`Name: host:port`) |
| `roomPublic` | boolean | `true` | Whether the room is visible in the public room browser |
| `roomProtected` | boolean | `false` | Whether password protection is enabled |
| `roomPassword` | int | `0` | Room password PIN (0000 - 9999) |
| `lanPort` | int | `0` | TCP port for LAN exposure (`0` = 25565 default) |
| `onlineModeBypass` | boolean | `true` | Whether to bypass Mojang session check for loopback connections |

---

## ⚠️ Security & Trust Notes

Please understand the following design assumptions before use (identical to vanilla CLaJ — a trust model, not a flaw):

- **Relay nodes and hosts are trusted entities**: all traffic (including room data) is **plaintext TCP, no encryption**. Relay admins — or anyone able to tamper with the traffic — could in theory inject arbitrary protocol bytes. Only use trusted relays (e.g. the official / well-known nodes on the public list).
- **Online-mode bypass is enabled by default**: loopback CLaJ connections skip Mojang session verification, so **any local process** (e.g. an installed trojan) can connect to `127.0.0.1:<port>` without verification and enter your world. Disable it by setting `onlineModeBypass` to `false` in `config/claj.json`.
- **Room passwords are not encryption**: the 4-digit PIN only keeps strangers / abusers out; it is transmitted in plaintext and has only 10000 combinations — treat it as a deterrent, not a security mechanism.
- **The link is the credential**: anyone holding the link can join; do not share it publicly.

---

## 📄 License

This project is licensed under the [GNU General Public License v3.0](./LICENSE).

- Ported protocol and server core © [Xpdustry](https://github.com/xpdustry)
- Underlying networking library © [Anuken/Arc](https://github.com/Anuken/Arc)
