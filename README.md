# Solidus

**Advanced Server-Side Economy & Commerce Engine for Minecraft**

<p align="center">
  <strong>Solidus</strong> — محرك اقتصاد وتجارة متقدم بالكامل من جانب الخادم لماينكرافت
</p>

---

## Overview

Solidus is a 100% server-side economy and commerce engine for Minecraft Java Edition, built on the Fabric mod loader. It provides a complete virtual currency system, a GUI-based server shop with anti-farm price deflation, and a player-driven auction house with race-condition protection. Fully vanilla-client compatible — no custom textures, no client mods, and no resource packs required. Players connect and interact using a completely unmodified Vanilla Minecraft Client.

---

## Features

### Virtual Economy Engine
- In-memory balance cache with async SQLite persistence — instant O(1) reads
- Single-Threaded Executor Queue eliminates all race conditions without database locks
- Asynchronous SQLite database with WAL mode for crash resilience
- No data rollbacks or economy sync-loss on server crashes
- Safe peer-to-peer transfers with anti-exploit validation
- Server leaderboards (`/baltop`)
- Configurable starting balance (default: 500 S$)

### Virtual Server Shop (`/shop`)
- Server-driven Virtual Chest GUI (GENERIC_9x6 layout)
- 11 categories with 120+ items and dynamic pagination
- Display-Only items — moving, dragging, and shift-clicking are blocked programmatically
- Left-click = Buy 1, Right-click = Sell 1, Shift+Click = Buy/Sell stack
- Configurable via `shop.json` (no server restart required for price changes)
- Text components parsed using the official Minecraft `ComponentSerialization.CODEC`

### Auction House (`/ah`)
- Player-driven marketplace incentivizing real survival exploration
- Armor Trims and progression items excluded from shop, forcing player commerce
- Race-condition protection via Single-Threaded Executor Queue (no database locking)
- 72-hour listing duration with automatic expiration
- 2% listing fee to discourage spam

### Anti-Farm Economy Protection
- **70% sell-price reduction** on Emeralds (Raid Farms / Trading Halls)
- **50% sell-price reduction** on Gold Ingots (Piglin Bartering Farms)
- **50% sell-price reduction** on Shulker Shells/Boxes (Shulker Farms)
- **60% sell-price reduction** on Mace (Trial Chamber Farms)
- **60% sell-price reduction** on Heavy Core (Trial Chamber Farms)
- **50% sell-price reduction** on Breeze Rod (Trial Chamber Farms)
- Moderate deflations on Iron, Gunpowder, String, Bones, and more
- Hardcoded deflation table prevents configuration tampering

### Security & Anti-Exploit
- Packet rate limiter (150ms cooldown per player) — blocks cheat clients
- In-memory balance cache with Single-Threaded Executor for all mutations
- Ghost Item prevention via forced container resync after packet cancellation
- Anti-negative value exploitation on all financial operations
- Maximum transaction cap and balance ceiling
- Double-click / drag / shift-click fully blocked in virtual GUIs

### Modern Text Architecture
- **Legacy formatting character is completely banned** — using it causes client disconnects and thread crashes
- All text uses Minecraft's native `Component.literal().withStyle(ChatFormatting)` architecture
- Shop configuration (`shop.json`) uses Minecraft's official `ComponentSerialization.CODEC`
- Full forward compatibility with Mojang's evolving Data Components system

---

## Commands

| Command | Description |
|---------|-------------|
| `/balance` or `/bal` | Display your current Solidus balance |
| `/pay <player> <amount>` | Transfer currency to another player |
| `/baltop` | View the server's wealthiest players (Top 10) |
| `/shop` | Open the virtual server shop GUI |
| `/ah` | Browse the Auction House listings |
| `/ah sell <price>` | List the item in your main hand for sale |

---

## Shop Categories

| Category | Items | Icon |
|----------|-------|------|
| Building Blocks | 24 | Cobblestone |
| Ores & Minerals | 19 | Diamond |
| Food | 16 | Cooked Beef |
| Farming | 12 | Wheat |
| Mob Drops & Combat | 30 | Diamond Sword |
| Trial Challenges | 3 | Trial Key |
| Tools & Armor | 28 | Diamond Chestplate |
| Enchanting & Potions | 9 | Enchanting Table |
| Redstone | 13 | Redstone |
| Decoration | 12 | Painting |
| Misc & Ocean | 19 | Trident |

---

## Technical Specifications

| Requirement | Value |
|-------------|-------|
| **Minecraft Version** | Java Edition 1.21.7 (compatible 26.1.x) |
| **Mod Loader** | Fabric Loader 0.19.2+ |
| **Java Runtime** | Java 25 |
| **Side Compatibility** | 100% Server-Side Only |
| **Client Requirement** | Vanilla Minecraft (un-modded) |
| **Database** | SQLite (WAL mode, async, in-memory cache) |
| **GUI Library** | Native ScreenHandler (no third-party libs) |
| **Concurrency Model** | Single-Threaded Executor Queue per subsystem |
| **Text Parsing** | ComponentSerialization.CODEC (official Minecraft) |

---

## Architecture: Race Condition Protection

Solidus uses **Single-Threaded Executor Queues** instead of database-level locking for concurrency protection. This approach is both correct and performant:

### Why Not BEGIN IMMEDIATE?
SQLite's `BEGIN IMMEDIATE` does **not** provide row-level locking — it locks the **entire database file** against writes from other threads. If one player buys from the auction while another uses `/pay`, a "database is locked" exception would occur because both operations compete for the same file lock.

### How the Executor Queue Works
All economy mutations and auction transactions are submitted to dedicated single-threaded executors. Each executor processes operations **sequentially, one at a time**:

1. Operation submitted to executor queue
2. Executor thread picks up the operation
3. In-memory cache updated immediately (instant for subsequent reads)
4. SQLite persistence happens asynchronously
5. Result returned via CompletableFuture

This guarantees that two players cannot purchase the same auction listing simultaneously — the executor processes purchases one by one, and the second buyer simply finds the listing already marked as sold.

### Performance
- Balance reads: O(1) from in-memory cache (no database query)
- Balance writes: Cache updated immediately, persisted async
- No lock contention: Operations are queued, not locked
- No "database is locked" exceptions: No file-level locking needed

---

## Project Structure

```
solidus/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── src/main/java/com/solidus/
│   ├── SolidusMod.java                  # Main entry point
│   ├── economy/
│   │   ├── EconomyEngine.java           # Central coordinator
│   │   ├── SQLiteStorage.java           # Async SQLite + in-memory cache
│   │   ├── AntiFarmManager.java         # Deflation table (hardcoded)
│   │   └── BalanceManager.java          # High-level balance API
│   ├── commands/
│   │   ├── BalanceCommand.java          # /balance, /bal
│   │   ├── PayCommand.java              # /pay
│   │   ├── BaltopCommand.java           # /baltop
│   │   ├── ShopCommand.java             # /shop
│   │   └── AuctionCommand.java          # /ah, /ah sell
│   ├── shop/
│   │   ├── ShopManager.java             # Shop config loader & Codec parser
│   │   ├── ShopGUI.java                 # GUI layout builder
│   │   ├── ShopScreenHandler.java       # Native ScreenHandler
│   │   └── ShopDummyContainer.java      # Display-only container
│   ├── auction/
│   │   ├── AuctionManager.java          # Auction engine (executor queue)
│   │   ├── AuctionGUI.java              # Auction GUI builder
│   │   ├── AuctionScreenHandler.java    # Native ScreenHandler
│   │   ├── AuctionEntry.java            # Listing data model
│   │   └── AuctionDummyContainer.java   # Display-only container
│   ├── networking/
│   │   ├── PacketHandler.java           # Packet interception
│   │   └── RateLimiter.java             # Click cooldown manager
│   ├── mixin/
│   │   ├── ServerPlayerEntityMixin.java # Container click + ghost item fix
│   │   └── ScreenHandlerMixin.java      # Quick-move blocker + resync
│   └── util/
│       ├── TextUtil.java                # Component factory (NO legacy chars)
│       ├── CurrencyUtil.java            # Currency formatting & limits
│       └── ConfigManager.java           # File I/O manager
├── src/main/resources/
│   ├── fabric.mod.json
│   ├── solidus.mixins.json
│   ├── shop.json                        # 120+ items, 11 sections
│   └── pack.mcmeta
```

---

## Build & Installation

### Prerequisites
- JDK 25
- Gradle 8.x+

### Building
```bash
git clone <repository-url>
cd solidus
./gradlew build
```

The compiled JAR will be in `build/libs/solidus-1.0.0-beta.jar`.

### Installation
1. Place the JAR in your server's `mods/` directory
2. Ensure Fabric Loader 0.19.2+ is installed on the server
3. Start the server — Solidus will auto-generate `config/solidus/shop.json`
4. Customize `shop.json` as needed (supports hot-reload)

---

## Critical Rules for Developers

1. **NEVER use legacy formatting characters** — It causes client disconnects and thread crashes. Use `Component.literal().withStyle()` exclusively.
2. **NEVER use `modImplementation` for non-Fabric deps** — Use standard `implementation` in `build.gradle` for non-remapped dependencies.
3. **NEVER use `BEGIN IMMEDIATE` for concurrency** — SQLite does NOT support row-level locking. Use Single-Threaded Executor Queues instead.
4. **NEVER use third-party GUI libraries** — Write native `ScreenHandler` extensions only.
5. **Java 25 strict enforcement** — The project targets `LanguageVersion.of(25)`.
6. **Use `ComponentSerialization.CODEC`** — Not custom GSON parsers, for text component parsing from JSON.
7. **Always call `sendContentUpdates()`** after canceling packets in Mixins to prevent ghost items.
8. **Intermediary names in IDE** — Without mappings, code uses Intermediary names like `class_1703`. The Yarn mappings dependency resolves this at compile time.

---

## Anti-Farm Deflation Table

| Material | Factor | Reduction | Reason |
|----------|--------|-----------|--------|
| EMERALD, EMERALD_BLOCK | 0.30 | 70% | Anti-Raid Farm |
| GOLD_INGOT, GOLD_BLOCK, GOLD_NUGGET, RAW_GOLD | 0.50 | 50% | Anti-Piglin Farm |
| SHULKER_SHELL, SHULKER_BOX | 0.50 | 50% | Anti-Shulker Farm |
| MACE | 0.40 | 60% | Anti-Trial Farm |
| HEAVY_CORE | 0.40 | 60% | Anti-Trial Farm |
| BREEZE_ROD | 0.50 | 50% | Anti-Trial Farm |
| TRIAL_KEY, OMINOUS_TRIAL_KEY | 0.30 | 70% | Anti-Trial Farm |
| OMINOUS_BOTTLE | 0.50 | 50% | Anti-Trial Farm |
| IRON_INGOT, IRON_BLOCK, RAW_IRON | 0.70 | 30% | Anti-Iron Farm |
| GUNPOWDER | 0.75 | 25% | Anti-Creeper Farm |
| STRING | 0.75 | 25% | Anti-Spider Farm |
| BONE | 0.70 | 30% | Anti-Skeleton Farm |
| ROTTEN_FLESH | 0.60 | 40% | Anti-Zombie Farm |
| SUGAR_CANE | 0.80 | 20% | Auto-Farm |
| BAMBOO, KELP | 0.70 | 30% | Auto-Farm |
| SCUTE | 0.40 | 60% | Anti-Turtle Farm |
| NAUTILUS_SHELL | 0.50 | 50% | Anti-Drowned Farm |

---

## License & Credits

**Copyright 2025 MOHD_Gs. All rights reserved.**

Solidus Economy & Commerce Engine is developed and published by **MOHD_Gs**.

This project and its source code are the intellectual property of the author. Unauthorized reproduction, distribution, or modification is prohibited without explicit written permission.

---

<p align="center">
  <strong>Solidus</strong> — Built with precision by <strong>MOHD_Gs</strong>
</p>
