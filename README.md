# Solidus

[![Platform](https://img.shields.io/badge/Platform-Fabric-blue.svg)](https://fabricmc.net/)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.1.x-green.svg)](https://www.minecraft.net/)



### Server-side economy, shop, and auction system for Minecraft Fabric

Stable economies · Vanilla compatibility · No client installation

---

## Why Solidus?

Solidus is a complete **server-side economy and commerce engine** for Minecraft Fabric.

Create long-term survival economies without requiring client mods, resource packs, custom assets, or plugin stacks.

### Highlights

* Fully server-side architecture
* Works with vanilla and modded clients
* Built-in virtual economy
* GUI-based server shop
* Player-driven auction house
* Anti-inflation economy balancing
* Crash-resilient persistence
* Inter-mod API for third-party integration
* Minimal operational overhead

---

## Features

### Economy

A lightweight virtual economy designed for multiplayer survival servers.

Features include:

* Memory-backed balance reads
* Async SQLite persistence
* Configurable starting balance
* Secure player transfers (`/pay`) — online and offline
* Global wealth leaderboard (`/baltop`)
* Full transaction history (`/transactions`)
* Offline notifications on login
* Consistency-focused execution model

---

### Server Shop (`/shop`)

Virtual shop interface powered entirely by the server.

Includes:

* 11 categories
* 120+ configured items
* Stack trading support
* Item search (`/shop search <query>`)
* Hot-reload configuration
* Display-only GUI protection

---

### Auction House (`/ah`)

Marketplace for player-to-player trading.

Capabilities:

* Item listing directly from inventory (`/ah sell <price>`)
* Listing expiration with item return
* Reclaim expired items (`/ah collect`)
* Cancel own listings (`/ah cancel <uuid>`)
* Sort listings by price, newest, or material (`/ah sort`)
* Listing fee support
* Purchase protection
* Offline seller notifications
* Progression-focused balancing

---

### Economy Protection

Solidus includes balancing mechanisms to reduce the economic impact of automated farms.

Examples:

| Resource             | Reduction             |
| -------------------- | --------------------- |
| Emerald              | 70%                   |
| Gold                 | 50%                   |
| Iron                 | 30%                   |
| Trial rewards        | 50–70%                |
| Additional materials | Configured internally |

---

### Inter-Mod API

Solidus exposes a stable public API (`com.solidus.api.SolidusAPI`) for other Fabric mods to integrate with the economy system. No compile-time dependency required — mods can access all balance operations via pure reflection.

Available operations:

| Method | Description |
| ------ | ----------- |
| `getBalance` | Get player balance (online or offline) |
| `addBalance` | Add currency to a player |
| `subtractBalance` | Subtract currency from a player |
| `transfer` | Atomic player-to-player transfer |
| `transferOffline` | Atomic transfer by UUID (no online requirement) |
| `hasSufficientBalance` | Check if a player can afford an amount |
| `getTopBalances` | Wealth leaderboard data |
| `getTransactionLog` | Log custom transaction events |

Integration example (zero compile dependency):

```java
// Check if Solidus is loaded
if (FabricLoader.getInstance().isModLoaded("solidus")) {
    Class<?> apiClass = Class.forName("com.solidus.api.SolidusAPI");
    Object api = apiClass.getMethod("getInstance").invoke(null);
    // Call any method via reflection
}
```

See `docs/ARCHITECTURE.md` for the full API reference and integration guide.

---

## Screenshots

> Screenshots and GIF previews coming soon.

Suggested media:

* Shop GUI
* Auction interface
* Leaderboards
* Configuration examples

---

## Download

| Platform        | Link        |
| --------------- | ----------- |
| GitHub Releases | Releases    |
| Modrinth        | Coming soon |

---

## Installation

> Requirements: Minecraft 26.1.x · Java 25 · Fabric Loader · Fabric API

1. Install [Fabric Loader](https://fabricmc.net/use/)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) on the server
3. Download the latest Solidus release
4. Place both `.jar` files into `mods/`
5. Start the server
6. Configure `config/solidus/shop.json`

No client installation required.

---

## Commands

| Command              | Description                  |
| -------------------- | ---------------------------- |
| `/balance`           | Show balance                 |
| `/pay <player> <amount>` | Transfer to online player |
| `/pay offline <name> <amount>` | Transfer to offline player |
| `/baltop`            | Wealth leaderboard           |
| `/shop`              | Open shop                    |
| `/shop search <query>` | Search shop items          |
| `/ah`                | Open auction                 |
| `/ah sell <price>`   | Create listing               |
| `/ah collect`        | Reclaim expired items        |
| `/ah cancel <uuid>`  | Cancel own listing           |
| `/ah sort <order>`   | Sort listings                |
| `/transactions [page]` | Transaction history        |

---

## Configuration

Solidus generates configuration automatically.

Location:

```text
config/solidus/shop.json
```

Example:

```json
{
  "startingBalance":500,
  "currency":"S$",
  "listingFee":2
}
```

Supports:

* Categories
* Prices
* Text formatting
* Reload without restart

---

## Compatibility

| Component | Requirement    |
| --------- | -------------- |
| Minecraft | 26.1.x         |
| Loader    | Fabric         |
| Fabric API | Required      |
| Java      | 25             |
| Client    | Any            |
| Database  | SQLite         |
| Side      | Server         |

---

## FAQ

### Does this require client mods?

No.

Players join using standard Minecraft clients.

---

### Works with proxy networks?

Yes.

Solidus runs on backend servers.

---

### Supports offline mode?

Yes, but online-mode servers are recommended.

---

### Can prices be changed live?

Yes.

Configuration supports hot reload.

---

### Does Solidus integrate with other mods?

Yes.

Solidus provides a stable public API (`SolidusAPI`) that other Fabric mods can use to read balances, transfer currency, and log transactions. Integration works via reflection with zero compile-time dependency — the other mod does not need Solidus in its build path.

If Solidus is not installed, the integrating mod simply skips the API calls and continues working normally.

---

## Developer Notes

### Architecture

Solidus separates:

* Economy
* Shop
* Auction
* Persistence
* Networking
* Public API

### Storage

* SQLite
* WAL mode
* Async persistence
* Memory cache

### Concurrency

Operations are serialized internally to reduce contention and preserve consistency.

### Text System

Uses Minecraft component serialization.

More details:

```text
docs/ARCHITECTURE.md
```

---

## Building

```bash
git clone <repository>

cd solidus

./gradlew build
```

Output:

```text
build/libs/
```

---

## Roadmap

* [ ] Transaction taxes
* [ ] Multi-currency
* [ ] REST API
* [ ] Redis backend
* [ ] Metrics & analytics
* [ ] Backup & restore

---

## Contributing

Contributions are welcome.

* Report issues
* Suggest features
* Submit pull requests

---

## License

Licensed under:

**Solidus Community & Commercial License (SCCL) v1.0**

| Usage               | Status           |
| ------------------- | ---------------- |
| Private servers     | Allowed          |
| Study & modify      | Allowed          |
| Open redistribution | Allowed          |
| Commercial use      | License required |

See `LICENSE`.

---

Built with ☕ by MOHD_Gs
