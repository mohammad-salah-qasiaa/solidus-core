# Solidus Architecture

Technical architecture, project structure, and design decisions for Solidus — the server-side Economy & Commerce Engine for Minecraft Fabric.

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
│   │   ├── EconomyEngine.java           # Central coordinator (executor queue)
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
└── docs/
    └── ARCHITECTURE.md                  # This file
```

---

## System Architecture

### Layer Overview

```
┌─────────────────────────────────────────────┐
│                 Commands                     │
│  BalanceCommand · PayCommand · BaltopCommand │
│  ShopCommand · AuctionCommand                │
├─────────────────────────────────────────────┤
│              Business Logic                  │
│  EconomyEngine · BalanceManager              │
│  ShopManager · AuctionManager                │
│  AntiFarmManager                             │
├─────────────────────────────────────────────┤
│              Presentation                    │
│  ShopGUI · ShopScreenHandler                │
│  AuctionGUI · AuctionScreenHandler           │
│  ShopDummyContainer · AuctionDummyContainer  │
├─────────────────────────────────────────────┤
│              Infrastructure                  │
│  SQLiteStorage · PacketHandler               │
│  RateLimiter · ConfigManager                 │
│  TextUtil · CurrencyUtil                     │
├─────────────────────────────────────────────┤
│              Mixins                          │
│  ServerPlayerEntityMixin · ScreenHandlerMixin│
└─────────────────────────────────────────────┘
```

---

## Core Subsystems

### Economy Engine

The `EconomyEngine` is the central coordinator. It owns:

- **BalanceManager** — High-level API for balance reads, writes, and transfers
- **SQLiteStorage** — Async persistence layer with in-memory cache
- **AntiFarmManager** — Hardcoded deflation multipliers

All balance mutations flow through a **Single-Threaded Executor Queue**:

```
Player Action → Command → BalanceManager → ExecutorService.submit()
                                                ↓
                                         Sequential Processing
                                                ↓
                                    1. Validate operation
                                    2. Update in-memory cache
                                    3. Async SQLite write
                                    4. Return CompletableFuture
```

### SQLite Storage

- **WAL mode** — Write-Ahead Logging allows concurrent reads while writing
- **In-memory cache** — `ConcurrentHashMap<UUID, Double>` for balance reads
- **Async writes** — Cache is updated immediately, disk persistence happens asynchronously
- **No `BEGIN IMMEDIATE`** — Executor Queue eliminates the need for database-level locking

### Shop System

- **ShopManager** — Loads `shop.json` using `ComponentSerialization.CODEC`, supports hot-reload
- **ShopGUI** — Builds the 6-row chest inventory layout with category navigation
- **ShopScreenHandler** — Extends `ScreenHandler` (GENERIC_9x6), manages buy/sell logic
- **ShopDummyContainer** — `SimpleContainer` that rejects all item modifications (display-only)

### Auction System

- **AuctionManager** — Manages listings with its own Executor Queue for serialized transactions
- **AuctionEntry** — Data model: seller UUID, item, price, timestamp, status
- **AuctionGUI** — Paginated auction browser
- **AuctionScreenHandler** — Handles purchase flow with race-condition protection

### Packet Interception

- **PacketHandler** — Intercepts `ServerboundContainerClickPacket` to block item movement in virtual GUIs
- **RateLimiter** — 150ms cooldown per player, prevents cheat-client spam
- **Ghost Item Fix** — After canceling a packet, calls `sendContentUpdates()` to force client resync

### Mixin Layer

- **ServerPlayerEntityMixin** — Intercepts container clicks at the player level
- **ScreenHandlerMixin** — Blocks quick-move (shift-click) operations in virtual containers

Both mixins call `sendContentUpdates()` after every cancellation to prevent ghost items.

---

## Anti-Farm Deflation Table

The `AntiFarmManager` uses a hardcoded `Map<Item, Double>` that cannot be modified via configuration. This prevents server operators from accidentally or intentionally removing deflation protection.

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

**How it works:** When a player sells an item, `AntiFarmManager.getDeflationFactor(item)` is called. If the item exists in the deflation table, the sell price is multiplied by the factor before being returned. For example, selling an Emerald with a base price of 1000 S$ would yield only 300 S$ (factor 0.30).

---

## Build System

| Setting | Value | Notes |
|---------|-------|-------|
| Loom Plugin | `net.fabricmc.fabric-loom` | New plugin ID for Fabric Loom 1.16+ |
| Dependency type | `implementation` | Not `modImplementation` (unobfuscated) |
| Mappings | None | Unobfuscated Minecraft 26.1.x |
| Java target | `LanguageVersion.of(25)` | Java 25 strict enforcement |
| Jar task | `jar` | Not `remapJar` (no remapping needed) |

### Intermediary Names

Without a mappings block, code in the IDE uses Intermediary names (e.g., `class_1703`) instead of official Mojang names. The Yarn mappings dependency resolves these at compile time. This is expected behavior for unobfuscated Minecraft 26.1.x environments.

---

## Critical Rules for Contributors

1. **NEVER use legacy formatting characters** — causes client disconnects and thread crashes. Use `Component.literal().withStyle()` exclusively.
2. **NEVER use `BEGIN IMMEDIATE` for concurrency** — SQLite does NOT support row-level locking. Use Executor Queues.
3. **NEVER use third-party GUI libraries** — write native `ScreenHandler` extensions only.
4. **ALWAYS call `sendContentUpdates()`** after canceling packets in Mixins to prevent ghost items.
5. **Use `ComponentSerialization.CODEC`** for text component parsing from JSON — not custom GSON parsers.
6. **NEVER use `modImplementation`** for non-Fabric dependencies — use standard `implementation`.
7. **Java 25 strict enforcement** — the project targets `LanguageVersion.of(25)`.
