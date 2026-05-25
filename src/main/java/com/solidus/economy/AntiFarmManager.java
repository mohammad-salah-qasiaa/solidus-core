package com.solidus.economy;

import com.solidus.SolidusMod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-Farm Deflation Manager
 *
 * Protects the server economy from automated farm exploits by applying
 * heavy sell-price deflation on items that are commonly mass-produced
 * through game mechanics like Raid Farms, Piglin Bartering Farms,
 * Shulker Farms, Trading Halls, and Trial Chamber Farms.
 *
 * Architecture:
 * - The deflation table is hardcoded as a static registry keyed by
 *   Material name. This prevents configuration tampering.
 * - Buy prices remain at normal market rates to keep the shop functional
 *   for legitimate purchases.
 * - Sell prices are multiplied by the deflation factor (0.0 to 1.0).
 *
 * IMPORTANT: Items from Trial Chambers (Mace, Heavy Core, Breeze Rod, etc.)
 * are hardcoded here because they are rare but farmable through repeated
 * Trial Chamber challenges. If left only to the external shop.json config,
 * these values could be tampered with to exploit duplication glitches or
 * destabilize the economy. The hardcoded deflation ensures these items
 * always have reduced sell values regardless of config file changes.
 *
 * Deflation Strategy Rationale:
 * - Emeralds: 70% sell reduction (0.30 factor) - Raid Farms and Trading
 *   Halls can produce thousands of Emeralds per hour with zero effort.
 * - Gold Ingots: 50% sell reduction (0.50 factor) - Piglin Bartering
 *   Farms generate massive gold throughput automatically.
 * - Shulker Shells/Boxes: 50% sell reduction (0.50 factor) - End city
 *   Shulker farms undermine storage item scarcity.
 * - Mace: 60% sell reduction (0.40 factor) - Trial Chamber farms allow
 *   repeated acquisition of this extremely powerful weapon.
 * - Heavy Core: 60% sell reduction (0.40 factor) - Trial Chamber farms
 *   make this rare crafting component repeatedly obtainable.
 * - Breeze Rod: 50% sell reduction (0.50 factor) - Farmable from Trial
 *   Chambers, used to craft Mace and other powerful items.
 * - Trial Key / Ominous Trial Key: 70% sell reduction (0.30 factor) -
 *   Trial Chambers can be farmed for unlimited keys.
 * - Ominous Bottle: 50% sell reduction (0.50 factor) - Farmable from
 *   Trial Chamber bad omen triggers.
 */
public final class AntiFarmManager {

    /**
     * Deflation record containing the factor and a human-readable reason.
     *
     * @param factor The sell price multiplier (0.0 to 1.0).
     *               1.0 = no deflation, 0.5 = 50% reduction, 0.3 = 70% reduction.
     * @param reason A short description of why this item is deflated (for display in GUI).
     */
    public record DeflationEntry(double factor, String reason) {}

    /**
     * The master deflation registry.
     * Key: Minecraft Material name (uppercase, as used in Item codes)
     * Value: DeflationEntry with factor and reason
     */
    private static final Map<String, DeflationEntry> DEFLATION_TABLE = new ConcurrentHashMap<>();

    static {
        // ── High-Priority Anti-Farm Deflations ──

        // Emerald: 70% sell price reduction
        // Raid Farms and Villager Trading Halls can produce unlimited Emeralds
        DEFLATION_TABLE.put("EMERALD", new DeflationEntry(0.30, "Anti-Raid Farm"));
        DEFLATION_TABLE.put("EMERALD_BLOCK", new DeflationEntry(0.30, "Anti-Raid Farm"));

        // Gold: 50% sell price reduction
        // Piglin Bartering Farms generate massive gold throughput
        DEFLATION_TABLE.put("GOLD_INGOT", new DeflationEntry(0.50, "Anti-Piglin Farm"));
        DEFLATION_TABLE.put("GOLD_BLOCK", new DeflationEntry(0.50, "Anti-Piglin Farm"));
        DEFLATION_TABLE.put("GOLD_NUGGET", new DeflationEntry(0.50, "Anti-Piglin Farm"));
        DEFLATION_TABLE.put("RAW_GOLD", new DeflationEntry(0.50, "Anti-Piglin Farm"));

        // Shulker: 50% sell price reduction
        // Shulker farms undermine storage item scarcity
        DEFLATION_TABLE.put("SHULKER_SHELL", new DeflationEntry(0.50, "Anti-Shulker Farm"));
        DEFLATION_TABLE.put("SHULKER_BOX", new DeflationEntry(0.50, "Anti-Shulker Farm"));

        // ── Trial Chamber Anti-Farm Deflations (26.1.x New Items) ──
        // These items are rare but farmable through repeated Trial Chamber challenges.
        // They MUST be hardcoded here to prevent economy exploitation via config
        // tampering or duplication glitches. Leaving them only in shop.json would
        // allow admins (or unauthorized config access) to set sell prices high
        // enough to enable infinite money loops with farmable items.

        // Mace: 60% sell reduction — the most powerful weapon in the game,
        // farmable through Trial Chamber challenge completions
        DEFLATION_TABLE.put("MACE", new DeflationEntry(0.40, "Anti-Trial Farm"));

        // Heavy Core: 60% sell reduction — key crafting component for Mace,
        // repeatedly obtainable from Trial Chamber vaults
        DEFLATION_TABLE.put("HEAVY_CORE", new DeflationEntry(0.40, "Anti-Trial Farm"));

        // Breeze Rod: 50% sell reduction — drops from Breezes in Trial Chambers,
        // used to craft Mace and Wind Charges
        DEFLATION_TABLE.put("BREEZE_ROD", new DeflationEntry(0.50, "Anti-Trial Farm"));

        // Trial Key: 70% sell reduction — obtainable in unlimited quantities
        // from Trial Chamber exploration and combat
        DEFLATION_TABLE.put("TRIAL_KEY", new DeflationEntry(0.30, "Anti-Trial Farm"));

        // Ominous Trial Key: 70% sell reduction — farmable from Trial Chambers
        // with the Bad Omen effect active
        DEFLATION_TABLE.put("OMINOUS_TRIAL_KEY", new DeflationEntry(0.30, "Anti-Trial Farm"));

        // Ominous Bottle: 50% sell reduction — drops from Trial Chambers with
        // Bad Omen, granting the Bad Omen effect when consumed
        DEFLATION_TABLE.put("OMINOUS_BOTTLE", new DeflationEntry(0.50, "Anti-Trial Farm"));

        // ── Moderate Anti-Farm Deflations ──

        // Iron: 30% sell reduction - Iron Farms are extremely common
        DEFLATION_TABLE.put("IRON_INGOT", new DeflationEntry(0.70, "Anti-Iron Farm"));
        DEFLATION_TABLE.put("IRON_BLOCK", new DeflationEntry(0.70, "Anti-Iron Farm"));
        DEFLATION_TABLE.put("RAW_IRON", new DeflationEntry(0.70, "Anti-Iron Farm"));

        // Gunpowder: 25% sell reduction - Creeper farms
        DEFLATION_TABLE.put("GUNPOWDER", new DeflationEntry(0.75, "Anti-Creeper Farm"));

        // String: 25% sell reduction - Spider/String farms
        DEFLATION_TABLE.put("STRING", new DeflationEntry(0.75, "Anti-Spider Farm"));

        // Bones: 30% sell reduction - Skeleton farms
        DEFLATION_TABLE.put("BONE", new DeflationEntry(0.70, "Anti-Skeleton Farm"));

        // Rotten Flesh: 40% sell reduction - Zombie farms are trivial
        DEFLATION_TABLE.put("ROTTEN_FLESH", new DeflationEntry(0.60, "Anti-Zombie Farm"));

        // Sugar Cane: 20% sell reduction - Auto-harvest farms
        DEFLATION_TABLE.put("SUGAR_CANE", new DeflationEntry(0.80, "Auto-Farm"));

        // Bamboo: 30% sell reduction - Mass bamboo farms
        DEFLATION_TABLE.put("BAMBOO", new DeflationEntry(0.70, "Auto-Farm"));

        // Kelp: 30% sell reduction - Auto-kelp farms
        DEFLATION_TABLE.put("KELP", new DeflationEntry(0.70, "Auto-Farm"));

        // Scute: Heavy deflation - Turtle farms
        DEFLATION_TABLE.put("SCUTE", new DeflationEntry(0.40, "Anti-Turtle Farm"));

        // Nautilus Shell: Heavy deflation - Drowned farms
        DEFLATION_TABLE.put("NAUTILUS_SHELL", new DeflationEntry(0.50, "Anti-Drowned Farm"));
    }

    private AntiFarmManager() {}

    /**
     * Checks if a material has anti-farm deflation applied.
     *
     * @param materialName The Minecraft Material name (e.g., "EMERALD")
     * @return true if the material is in the deflation table
     */
    public static boolean isDeflated(String materialName) {
        return DEFLATION_TABLE.containsKey(materialName.toUpperCase());
    }

    /**
     * Gets the deflation entry for a material, if one exists.
     *
     * @param materialName The Minecraft Material name
     * @return The DeflationEntry, or null if no deflation applies
     */
    public static DeflationEntry getDeflation(String materialName) {
        return DEFLATION_TABLE.get(materialName.toUpperCase());
    }

    /**
     * Applies anti-farm deflation to a sell price.
     * If the material is in the deflation table, the sell price is multiplied
     * by the deflation factor. Otherwise, the original price is returned unchanged.
     *
     * @param materialName The Minecraft Material name
     * @param sellPrice    The original sell price before deflation
     * @return The deflated sell price
     */
    public static double applyDeflation(String materialName, double sellPrice) {
        DeflationEntry entry = DEFLATION_TABLE.get(materialName.toUpperCase());
        if (entry != null) {
            double deflatedPrice = sellPrice * entry.factor;
            SolidusMod.LOGGER.debug("Anti-Farm deflation applied: {} -> {} (factor: {}, reason: {})",
                materialName, deflatedPrice, entry.factor, entry.reason);
            return deflatedPrice;
        }
        return sellPrice;
    }

    /**
     * Gets the deflation factor for a material.
     * Returns 1.0 (no deflation) if the material is not in the table.
     *
     * @param materialName The Minecraft Material name
     * @return The deflation factor (0.0 to 1.0)
     */
    public static double getDeflationFactor(String materialName) {
        DeflationEntry entry = DEFLATION_TABLE.get(materialName.toUpperCase());
        return entry != null ? entry.factor : 1.0;
    }

    /**
     * Gets the deflation reason for a material.
     * Returns null if the material is not deflated.
     *
     * @param materialName The Minecraft Material name
     * @return The reason string, or null
     */
    public static String getDeflationReason(String materialName) {
        DeflationEntry entry = DEFLATION_TABLE.get(materialName.toUpperCase());
        return entry != null ? entry.reason : null;
    }

    /**
     * Returns a defensive copy of all deflation entries.
     * Useful for administrative commands that list deflation policies.
     */
    public static Map<String, DeflationEntry> getAllDeflations() {
        return Map.copyOf(DEFLATION_TABLE);
    }
}
