package com.solidus.auction;

import java.util.UUID;

/**
 * Immutable data model representing a single auction listing.
 *
 * Each auction entry represents an item listed by a player for sale
 * at a fixed price. Other players can browse and purchase these items.
 *
 * Concurrency Safety:
 * - The UUID uniquely identifies each listing globally
 * - The 'sold' flag is protected by database row-level locking
 *   during purchase transactions to prevent duplication glitches
 *
 * Lifecycle:
 * 1. CREATED - Item listed via /ah sell <price>
 * 2. ACTIVE  - Visible in the auction house GUI
 * 3. SOLD    - Purchased by another player (transaction complete)
 * 4. EXPIRED - Listing duration exceeded, item returned to seller
 */
public record AuctionEntry(
    UUID listingId,        // Unique identifier for this listing
    UUID sellerUuid,       // The player who listed the item
    String sellerName,     // Cached seller display name
    String materialName,   // Minecraft Material name for the item
    int quantity,          // Number of items in the stack
    String itemNbt,        // Serialized item data (for enchanted/custom items)
    double price,          // Listed sale price in Solidus currency
    long listedTimestamp,  // Epoch millis when the item was listed
    long expireTimestamp,  // Epoch millis when the listing expires
    boolean sold           // Whether the item has been purchased
) {

    /**
     * Default auction duration in milliseconds (72 hours).
     */
    public static final long DEFAULT_DURATION_MS = 72 * 60 * 60 * 1000L;

    /**
     * Maximum auction duration in milliseconds (168 hours = 7 days).
     */
    public static final long MAX_DURATION_MS = 168 * 60 * 60 * 1000L;

    /**
     * Minimum listing price.
     */
    public static final double MIN_LISTING_PRICE = 1.0;

    /**
     * Maximum listing price.
     */
    public static final double MAX_LISTING_PRICE = 10_000_000.0;

    /**
     * Listing fee percentage (2% of the listed price).
     * This fee is deducted from the seller's balance when listing
     * to discourage spam listings and stabilize the economy.
     */
    public static final double LISTING_FEE_PERCENT = 0.02;

    /**
     * Creates a new AuctionEntry with auto-generated IDs and timestamps.
     */
    public static AuctionEntry create(UUID sellerUuid, String sellerName,
                                       String materialName, int quantity,
                                       String itemNbt, double price) {
        long now = System.currentTimeMillis();
        return new AuctionEntry(
            UUID.randomUUID(),
            sellerUuid,
            sellerName,
            materialName,
            quantity,
            itemNbt,
            price,
            now,
            now + DEFAULT_DURATION_MS,
            false
        );
    }

    /**
     * Checks if this listing has expired.
     */
    public boolean isExpired() {
        return !sold && System.currentTimeMillis() > expireTimestamp;
    }

    /**
     * Checks if this listing is currently active (not sold, not expired).
     */
    public boolean isActive() {
        return !sold && !isExpired();
    }

    /**
     * Calculates the listing fee for a given price.
     */
    public static double calculateListingFee(double price) {
        return Math.max(1.0, price * LISTING_FEE_PERCENT);
    }
}
