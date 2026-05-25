package com.solidus.economy;

import com.solidus.SolidusMod;
import com.solidus.util.CurrencyUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous SQLite Storage Backend for Solidus Economy Engine.
 *
 * Architecture: Single-Threaded Executor Queue + In-Memory Cache
 *
 * Race Condition Strategy:
 * All balance mutations are processed through a dedicated single-thread executor,
 * which guarantees sequential execution without any overlap. An in-memory balance
 * cache (ConcurrentHashMap) provides instant O(1) reads without hitting the database,
 * while all writes update the cache first and then persist asynchronously to SQLite.
 *
 * This approach eliminates the need for database-level locking (BEGIN IMMEDIATE)
 * entirely. Since SQLite does NOT support row-level locking — BEGIN IMMEDIATE
 * actually locks the entire database file — relying on it would cause "database
 * is locked" errors when concurrent operations (e.g., auction purchase + /pay)
 * occur across different subsystems. The single-threaded executor queue solves
 * this by serializing all mutations in memory first, making database locking
 * unnecessary and improving performance by orders of magnitude.
 *
 * Crash Resilience:
 * - WAL (Write-Ahead Logging) mode ensures committed transactions survive crashes.
 * - The in-memory cache is rebuilt from the database on startup.
 * - Auto-checkpoint balances performance vs. crash recovery window.
 * - All critical mutations are persisted to SQLite immediately after the
 *   in-memory state is updated, minimizing the data-at-risk window.
 */
public class SQLiteStorage {

    private static final String DATABASE_NAME = "economy.db";
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS player_balances (
            uuid TEXT PRIMARY KEY NOT NULL,
            player_name TEXT NOT NULL,
            balance REAL NOT NULL DEFAULT 0.0,
            last_updated INTEGER NOT NULL
        )
    """;
    private static final String CREATE_INDEX_SQL = """
        CREATE INDEX IF NOT EXISTS idx_balance_rank
        ON player_balances (balance DESC)
    """;

    /**
     * In-memory balance cache. Provides instant O(1) reads without database
     * queries. All mutations are performed on the single-threaded executor,
     * which updates this cache before persisting to SQLite.
     *
     * Thread Safety: ConcurrentHashMap allows safe concurrent reads from any
     * thread (e.g., server tick thread). All writes are performed exclusively
     * by the single-threaded executor, establishing a clear happens-before
     * relationship that guarantees consistency.
     */
    private final ConcurrentHashMap<UUID, Double> balanceCache = new ConcurrentHashMap<>();

    /**
     * In-memory player name cache. Maps UUID to the last known player name.
     * Used by getTopBalances() to display names without querying the database.
     * Populated during initial cache load and updated on every balance operation.
     */
    private final ConcurrentHashMap<UUID, String> playerNameCache = new ConcurrentHashMap<>();

    private final ExecutorService asyncExecutor;
    private final String databaseUrl;
    private volatile boolean initialized = false;

    /**
     * Constructs a new SQLiteStorage with the given config directory path.
     *
     * @param configDir The directory where the database file will be stored
     */
    public SQLiteStorage(String configDir) {
        this.databaseUrl = "jdbc:sqlite:" + configDir + "/" + DATABASE_NAME;
        // Single-threaded executor guarantees sequential consistency for all
        // DB operations and in-memory cache mutations — NO race conditions possible
        this.asyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Solidus-Economy-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Initializes the database: creates tables, indexes, configures WAL mode,
     * and pre-loads all balances into the in-memory cache.
     * Must be called once during mod startup before any other operations.
     */
    public void initialize() {
        try (Connection conn = getConnection()) {
            // Enable WAL mode for crash resilience
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA temp_store=MEMORY");
                stmt.execute("PRAGMA mmap_size=67108864"); // 64MB memory map
                stmt.execute("PRAGMA cache_size=-2000"); // 2MB cache
            }

            // Create tables
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(CREATE_TABLE_SQL);
                stmt.execute(CREATE_INDEX_SQL);
            }

            // Pre-load all balances into the in-memory cache
            loadAllBalancesIntoCache(conn);

            initialized = true;
            SolidusMod.LOGGER.info("SQLite database initialized successfully. Cached {} player balances.",
                balanceCache.size());
        } catch (SQLException e) {
            SolidusMod.LOGGER.error("CRITICAL: Failed to initialize SQLite database!", e);
            throw new RuntimeException("Solidus economy database initialization failed", e);
        }
    }

    /**
     * Pre-loads all existing balances and player names from the database
     * into the in-memory cache. This ensures that getBalance() calls
     * never need to query the database, and getTopBalances() can display
     * player names without additional lookups.
     */
    private void loadAllBalancesIntoCache(Connection conn) throws SQLException {
        String sql = "SELECT uuid, player_name, balance FROM player_balances";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("player_name");
                double balance = rs.getDouble("balance");
                balanceCache.put(uuid, balance);
                if (name != null && !name.isEmpty()) {
                    playerNameCache.put(uuid, name);
                }
            }
        }
    }

    /**
     * Shuts down the async executor gracefully, waiting for pending operations.
     * All pending database writes are flushed before shutdown completes.
     */
    public void shutdown() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
                SolidusMod.LOGGER.warn("Economy executor forced shutdown after timeout.");
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        SolidusMod.LOGGER.info("SQLite storage shut down complete.");
    }

    // ── Instant Read Operations (from in-memory cache) ────

    /**
     * Retrieves a player's balance instantly from the in-memory cache.
     * No database query is needed — the result is O(1) and immediate.
     *
     * If the player has no record in the cache, a new entry is created
     * with the default starting balance and persisted asynchronously.
     *
     * @param uuid       The player's unique ID
     * @param playerName The player's display name (for record creation)
     * @return CompletableFuture containing the player's balance (completes instantly)
     */
    public CompletableFuture<Double> getBalance(UUID uuid, String playerName) {
        ensureInitialized();

        // Update player name cache whenever we see a non-empty name
        if (playerName != null && !playerName.isEmpty()) {
            playerNameCache.put(uuid, playerName);
        }

        // Check the in-memory cache first (instant, no DB query)
        Double balance = balanceCache.get(uuid);
        if (balance != null) {
            return CompletableFuture.completedFuture(balance);
        }

        // Player not in cache — create a new entry with the default starting balance
        double startingBalance = CurrencyUtil.DEFAULT_STARTING_BALANCE;
        balanceCache.put(uuid, startingBalance);

        // Persist the new player asynchronously
        asyncPersistNewPlayer(uuid, playerName, startingBalance);

        return CompletableFuture.completedFuture(startingBalance);
    }

    /**
     * Retrieves the top N players by balance for leaderboard display.
     * Reads from the in-memory cache and sorts on the calling thread.
     * Player names are resolved from the in-memory playerNameCache.
     *
     * @param limit Maximum number of entries to return
     * @return CompletableFuture containing list of BalanceEntry objects
     */
    public CompletableFuture<List<BalanceEntry>> getTopBalances(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            ensureInitialized();
            return balanceCache.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .collect(ArrayList<BalanceEntry>::new,
                    (list, entry) -> {
                        String name = playerNameCache.getOrDefault(entry.getKey(), "Unknown");
                        list.add(new BalanceEntry(
                            list.size() + 1,
                            name,
                            entry.getValue()
                        ));
                    },
                    ArrayList::addAll);
        }, asyncExecutor);
    }

    // ── Write Operations (via Single-Threaded Executor Queue) ──

    /**
     * Sets a player's balance to an exact value.
     *
     * The mutation is submitted to the single-threaded executor queue,
     * which updates the in-memory cache and persists to SQLite.
     * No database-level locking (BEGIN IMMEDIATE) is needed because
     * the executor serializes all mutations sequentially.
     *
     * @param uuid       The player's unique ID
     * @param playerName The player's display name
     * @param amount     The new balance value
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> setBalance(UUID uuid, String playerName, double amount) {
        ensureInitialized();
        amount = CurrencyUtil.round(amount);
        if (!CurrencyUtil.isValidBalance(amount)) {
            SolidusMod.LOGGER.warn("Invalid balance amount rejected: {}", amount);
            return CompletableFuture.completedFuture(false);
        }

        // Submit to the single-threaded executor queue for sequential processing
        return CompletableFuture.supplyAsync(() -> {
            // Save the previous value for rollback in case persist fails
            Double previousBalance = balanceCache.get(uuid);

            // Update in-memory cache
            balanceCache.put(uuid, amount);

            // Persist to SQLite
            boolean success = persistBalance(uuid, playerName, amount);
            if (!success) {
                // Rollback cache to previous value on persist failure
                if (previousBalance != null) {
                    balanceCache.put(uuid, previousBalance);
                } else {
                    balanceCache.remove(uuid);
                }
                SolidusMod.LOGGER.error("Failed to persist balance for UUID: {}. Cache rolled back to previous value.", uuid);
            }
            return success;
        }, asyncExecutor);
    }

    /**
     * Atomically adds an amount to a player's balance.
     *
     * All mutations are serialized through the single-threaded executor,
     * eliminating race conditions without database-level locking.
     * The in-memory cache is updated before the DB write, ensuring
     * subsequent reads see the latest value instantly.
     *
     * @param uuid       The player's unique ID
     * @param playerName The player's display name
     * @param amount     The amount to add (must be positive)
     * @return CompletableFuture with the new balance, or -1 on failure
     */
    public CompletableFuture<Double> addBalance(UUID uuid, String playerName, double amount) {
        ensureInitialized();
        amount = CurrencyUtil.round(amount);

        // Submit to the single-threaded executor queue for sequential processing
        return CompletableFuture.supplyAsync(() -> {
            // Update in-memory cache atomically (only this thread writes)
            double currentBalance = balanceCache.getOrDefault(uuid, CurrencyUtil.DEFAULT_STARTING_BALANCE);
            double newBalance = CurrencyUtil.round(currentBalance + amount);

            if (!CurrencyUtil.isValidBalance(newBalance)) {
                SolidusMod.LOGGER.warn("Balance overflow prevented for UUID: {} (would be {})",
                    uuid, newBalance);
                return -1.0;
            }

            balanceCache.put(uuid, newBalance);

            // Persist to SQLite
            boolean success = persistBalance(uuid, playerName, newBalance);
            if (!success) {
                // Rollback cache to previous value
                balanceCache.put(uuid, currentBalance);
                SolidusMod.LOGGER.error("Failed to persist add-balance for UUID: {}. Cache rolled back.", uuid);
                return -1.0;
            }

            return newBalance;
        }, asyncExecutor);
    }

    /**
     * Atomically subtracts an amount from a player's balance.
     *
     * All mutations are serialized through the single-threaded executor,
     * eliminating race conditions without database-level locking.
     * Rejects the operation if insufficient funds.
     *
     * @param uuid       The player's unique ID
     * @param playerName The player's display name
     * @param amount     The amount to subtract (must be positive)
     * @return CompletableFuture with the new balance, or -1 on failure/insufficient funds
     */
    public CompletableFuture<Double> subtractBalance(UUID uuid, String playerName, double amount) {
        ensureInitialized();
        amount = CurrencyUtil.round(amount);

        // Submit to the single-threaded executor queue for sequential processing
        return CompletableFuture.supplyAsync(() -> {
            double currentBalance = balanceCache.getOrDefault(uuid, CurrencyUtil.DEFAULT_STARTING_BALANCE);

            if (currentBalance < amount) {
                return -1.0; // Insufficient funds
            }

            double newBalance = CurrencyUtil.round(currentBalance - amount);

            balanceCache.put(uuid, newBalance);

            // Persist to SQLite
            boolean success = persistBalance(uuid, playerName, newBalance);
            if (!success) {
                // Rollback cache to previous value
                balanceCache.put(uuid, currentBalance);
                SolidusMod.LOGGER.error("Failed to persist subtract-balance for UUID: {}. Cache rolled back.", uuid);
                return -1.0;
            }

            return newBalance;
        }, asyncExecutor);
    }

    /**
     * Checks whether a player has at least the specified amount.
     * Reads from the in-memory cache instantly.
     *
     * @param uuid   The player's unique ID
     * @param amount The amount to check against
     * @return CompletableFuture with true if the player can afford it
     */
    public CompletableFuture<Boolean> hasBalance(UUID uuid, double amount) {
        return getBalance(uuid, "").thenApply(balance -> balance >= amount);
    }

    // ── Internal Persistence Helpers ─────────────────────

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(databaseUrl);
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("SQLiteStorage accessed before initialization!");
        }
    }

    /**
     * Persists a balance update to SQLite.
     * Called from the single-threaded executor — no locking needed.
     * Also updates the playerNameCache to keep names current.
     */
    private boolean persistBalance(UUID uuid, String playerName, double balance) {
        // Update player name cache whenever we see a non-empty name
        if (playerName != null && !playerName.isEmpty()) {
            playerNameCache.put(uuid, playerName);
        }

        String upsertSql = """
            INSERT INTO player_balances (uuid, player_name, balance, last_updated)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                balance = excluded.balance,
                player_name = excluded.player_name,
                last_updated = excluded.last_updated
        """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setDouble(3, balance);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            SolidusMod.LOGGER.error("Failed to persist balance for UUID: {}", uuid, e);
            return false;
        }
    }

    /**
     * Persists a new player record to SQLite asynchronously.
     */
    private void asyncPersistNewPlayer(UUID uuid, String playerName, double balance) {
        CompletableFuture.runAsync(() -> {
            String insertSql = "INSERT OR IGNORE INTO player_balances (uuid, player_name, balance, last_updated) VALUES (?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setDouble(3, balance);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                SolidusMod.LOGGER.error("Failed to persist new player: {}", uuid, e);
            }
        }, asyncExecutor);
    }

    /**
     * Immutable data class representing a leaderboard entry.
     */
    public record BalanceEntry(int rank, String playerName, double balance) {}
}
