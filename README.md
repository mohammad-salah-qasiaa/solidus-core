<div align="center">

# Solidus

[![Platform](https://img.shields.io/badge/Platform-Fabric-blue.svg)](https://fabricmc.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.x-green.svg)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-SCCL_v1.0-red.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0.0--beta-orange.svg)](https://github.com/mohammad-salah-qasiaa/solidus)
[![Java](https://img.shields.io/badge/Java-25-purple.svg)](https://adoptium.net/)
[![Side](https://img.shields.io/badge/Server-Side-brightgreen.svg)]()

**Server-side economy, shop, and auction system for Minecraft Fabric — designed for vanilla compatibility and stable long-term economies.**

</div>

---

## Why Solidus?

- **Fully server-side** — Zero client installation, zero resource packs, zero custom textures
- **No client installation required** — Works with any Minecraft client, modded or vanilla
- **Dynamic economy** — In-memory cache with async SQLite persistence, memory-backed balance reads
- **Auction House** — Player-driven marketplace with race-condition protection
- **Anti-farm balancing** — Hardcoded deflation table neutralizes Iron Farms, Raid Farms, Piglin Bartering, and Trial Chamber exploits
- **Vanilla compatible** — Native ScreenHandler GUIs (GENERIC_9x6), no third-party GUI libraries

<!-- Add screenshot or GIF here -->
<!--
![Solidus Shop GUI](assets/shop-preview.gif)
![Solidus Auction House](assets/auction-preview.gif)
-->

---

## Features

### Economy

In-memory balance cache backed by asynchronous SQLite with WAL mode — memory-backed balance reads with no database hits. Single-Threaded Executor Queue serializes all mutations, avoiding race conditions through serialized execution without database-level locks. Players start with 500 S$ (configurable). Peer-to-peer transfers via `/pay` with anti-exploit validation. Server-wide leaderboards via `/baltop`.

### Shop

Virtual chest GUI with 11 categories and 120+ items. Left-click buys 1, right-click sells 1, shift-click trades a full stack. All items are display-only — moving, dragging, and shift-clicking are blocked programmatically via Mixin packet interception. Prices are loaded from `shop.json` and support hot-reload without server restart. Text components parsed using Minecraft's official `ComponentSerialization.CODEC`.

### Auction House

Player-driven `/ah` marketplace. List any item from your main hand with `/ah sell <price>`. 72-hour listing duration with automatic expiration. 2% listing fee discourages spam listings. Race-condition protection via Single-Threaded Executor Queue — two players cannot purchase the same listing simultaneously. Armor Trims and progression items are excluded from the shop, forcing real player commerce.

### Economy Protection

Hardcoded anti-farm deflation table prevents configuration tampering and economy exploitation:

| Material | Reduction | Reason |
|----------|-----------|--------|
| Emerald | 70% | Raid Farms / Trading Halls |
| Gold | 50% | Piglin Bartering Farms |
| Shulker Shell/Box | 50% | Shulker Farms |
| Mace | 60% | Trial Chamber Farms |
| Heavy Core | 60% | Trial Chamber Farms |
| Breeze Rod | 50% | Trial Chamber Farms |
| Trial Keys | 70% | Trial Chamber Farms |
| Iron | 30% | Iron Farms |
| + 8 more materials | 20-60% | Various farm exploits |

---

## Download

| Platform | Link |
|----------|------|
| GitHub Releases | [Latest Release](https://github.com/mohammad-salah-qasiaa/solidus/releases) |
| Modrinth | *Coming soon* |

---

## Installation

> **Requirements:** Minecraft 26.1.x · Java 25+ · Fabric Loader >= 0.19.2

1. Install [Fabric Loader](https://fabricmc.net/use/) `>= 0.19.2` on your server
2. Download the latest `.jar` from [Releases](https://github.com/mohammad-salah-qasiaa/solidus/releases)
3. Place the `.jar` in your server's `mods/` directory
4. Start the server — Solidus will auto-generate `config/solidus/shop.json`
5. Customize `shop.json` as needed (supports hot-reload, no restart required)

---

## Commands

| Command | Description |
|---------|-------------|
| `/balance` / `/bal` | Display your current Solidus balance |
| `/pay <player> <amount>` | Transfer currency to another player |
| `/baltop` | View the server's wealthiest players (Top 10) |
| `/shop` | Open the virtual server shop GUI |
| `/ah` | Browse the Auction House listings |
| `/ah sell <price>` | List the item in your main hand for sale |

---

## Configuration

Solidus uses a single `shop.json` file for all shop pricing. The file is auto-generated on first run at `config/solidus/shop.json`.

```json
{
  "sections": [
    {
      "name": {"text": "Ores & Minerals", "color": "aqua", "bold": true},
      "icon": "DIAMOND",
      "items": [
        {
          "material": "DIAMOND",
          "buyPrice": 3000,
          "sellPrice": 1800,
          "displayName": {"text": "Diamond", "color": "aqua", "bold": true}
        },
        {
          "material": "EMERALD",
          "buyPrice": 1000,
          "sellPrice": 300,
          "displayName": {"text": "Emerald", "color": "green"}
        }
      ]
    }
  ]
}
```

Text components use Minecraft's official `ComponentSerialization.CODEC` format — the same format used by the game's Data Components system. This ensures full forward compatibility with future Minecraft updates.

---

## Compatibility

| Requirement | Value |
|-------------|-------|
| **Minecraft** | Java Edition 26.1.x |
| **Mod Loader** | Fabric Loader >= 0.19.2 |
| **Java** | 25 |
| **Side** | Server-Side Only |
| **Client** | Vanilla Minecraft (un-modded) |
| **Database** | SQLite (WAL mode, async) |
| **GUI** | Native ScreenHandler (no third-party libs) |

---

## FAQ

**Does this require client mods?**
No. Solidus is fully server-side. No client installation required — works with any Minecraft client, modded or vanilla.

**Works with Velocity / BungeeCord?**
Yes, Solidus runs on the backend server. For proxy networks (BungeeCord, Velocity, Waterfall, or equivalent) with monetization, a Commercial License is required — see [LICENSE](LICENSE).

**Supports offline mode?**
Solidus uses UUID-based identification. In offline/cracked mode, UUIDs are generated from usernames — this works but is less secure against identity spoofing.

**Can I change prices without restarting?**
Yes. `shop.json` supports hot-reload. Edit the file and use `/shop reload` (or it auto-detects changes).

**What about economy exploits?**
Solidus has multiple layers of protection: hardcoded anti-farm deflation, Single-Threaded Executor Queue for race conditions, packet rate limiting (150ms cooldown), ghost item prevention, and anti-negative value validation on all operations.

**Does it work with economy plugins from other platforms?**
No. Solidus is a standalone economy engine and does not integrate with Vault, Essentials, or other plugin-based economy systems.

---

## Developer Notes

<details>
<summary>Click to expand — technical architecture for developers</summary>

### Concurrency: Single-Threaded Executor Queue

Solidus uses **Single-Threaded Executor Queues** instead of database-level locking. SQLite's `BEGIN IMMEDIATE` does not provide row-level locking — it locks the **entire database file** against writes. If one player buys from the auction while another uses `/pay`, a "database is locked" exception would occur.

Instead, all economy mutations and auction transactions are submitted to dedicated single-threaded executors:

1. Operation submitted to executor queue
2. Executor thread picks up the operation sequentially
3. In-memory cache updated immediately (instant for subsequent reads)
4. SQLite persistence happens asynchronously
5. Result returned via CompletableFuture

This guarantees that two players cannot purchase the same auction listing simultaneously — the second buyer simply finds the listing already marked as sold.

### SQLite Architecture

- **WAL mode** enabled for crash resilience and concurrent reads
- **In-memory cache** for all balance reads — zero database queries on `/balance`
- **Async persistence** — cache writes are flushed to disk asynchronously
- **No `BEGIN IMMEDIATE`** — Executor Queue replaces database-level locking entirely

### Text Parsing: ComponentSerialization.CODEC

Shop configuration text components are parsed using Minecraft's official `ComponentSerialization.CODEC` instead of custom GSON parsers. This provides:

- Full compatibility with Mojang's Data Components system
- No breaking changes when Minecraft updates its text component format
- Identical parsing behavior to vanilla Minecraft's own JSON text parsing

### Ghost Item Prevention

When a Mixin cancels a container click packet (to block item movement in virtual GUIs), the client does not immediately know about the cancellation due to network latency. This causes "ghost items" — items that appear to move on the client but never actually moved on the server.

The fix is to call `player.currentScreenHandler.sendContentUpdates()` after every packet cancellation, forcing an immediate container resync from server to client.

### Build System

| Setting | Value |
|---------|-------|
| Loom Plugin | `net.fabricmc.fabric-loom` |
| Dependency type | `implementation` (not `modImplementation`) |
| Mappings | None (unobfuscated 26.1.x) |
| Java target | `LanguageVersion.of(25)` |
| Jar task | `jar` (not `remapJar`) |

### Intermediary Names

Without a mappings block, code in the IDE uses Intermediary names (e.g., `class_1703`) instead of official Mojang names. The Yarn mappings dependency resolves these at compile time. This is expected behavior for unobfuscated Minecraft 26.1.x environments.

### Critical Rules

1. **NEVER use legacy formatting characters** — causes client disconnects and thread crashes. Use `Component.literal().withStyle()` exclusively.
2. **NEVER use `BEGIN IMMEDIATE` for concurrency** — SQLite does NOT support row-level locking. Use Executor Queues.
3. **NEVER use third-party GUI libraries** — write native `ScreenHandler` extensions only.
4. **ALWAYS call `sendContentUpdates()`** after canceling packets in Mixins.
5. **Use `ComponentSerialization.CODEC`** for text component parsing from JSON.

For full project structure and detailed architecture, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

</details>

---

## Building from Source

Make sure you have **JDK 25** installed, then:

```bash
# Clone the repository
git clone https://github.com/mohammad-salah-qasiaa/solidus.git
cd solidus

# Build the release jar
./gradlew build
```

The compiled `.jar` will be at:
```
build/libs/solidus-1.0.0-beta.jar
```

---

## Roadmap

- [ ] **Transaction Taxes** — Configurable tax rate on shop purchases and auction sales
- [ ] **Multi-Currency** — Support for multiple independent currency types
- [ ] **REST API** — HTTP endpoint for external integrations (web dashboards, Discord bots)
- [ ] **Redis Backend** — Optional Redis backend for multi-server economy sync
- [ ] **Metrics & Economy Analytics** — Inflation tracking, transaction logs, and server health dashboards
- [ ] **Backup & Recovery** — Automated economy snapshots with point-in-time restore

---

## License

This project is licensed under the **Solidus Community & Commercial License (SCCL) v1.0**.

**Copyright (c) 2026 MOHD_Gs**

| Use Case | Allowed? |
|----------|----------|
| Private / non-commercial servers | Free |
| Study & modify source code | Free |
| Fork & redistribute (open-source, same license) | Free |
| Commercial servers (paid ranks, webshops, etc.) | Requires Commercial License |
| Large-scale public servers | Requires Commercial License |
| Proxy networks with monetization | Requires Commercial License |
| Selling or relicensing as a paid product | Prohibited |

See [LICENSE](LICENSE) for full terms.

**Commercial inquiries:** Contact MOHD_Gs

---

<div align="center">
Built with precision by <b>MOHD_Gs</b>
</div>
