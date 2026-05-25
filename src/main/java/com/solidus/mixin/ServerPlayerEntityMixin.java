package com.solidus.mixin;

import com.solidus.SolidusMod;
import com.solidus.networking.PacketHandler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ClickType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ServerPlayerEntity Mixin - Hooks into the player's network connection
 * to intercept container click packets for virtual GUI processing.
 *
 * This mixin intercepts the handleContainerClick method on the server-side
 * packet listener. When a player clicks in any container, we check if it's
 * a Solidus virtual GUI (Shop or Auction) and route the click through our
 * custom handling pipeline with rate limiting.
 *
 * Defense-in-Depth Strategy:
 * - Primary defense: ShopScreenHandler and AuctionScreenHandler override
 *   clicked() and quickMoveStack() to block all item movement
 * - Secondary defense: This mixin intercepts packets before they reach
 *   the vanilla handler, adding rate limiting
 * - The abstract quickMoveStack is NOT targeted here (it cannot be injected
 *   into since it has no method body). Instead, the concrete overrides in
 *   our ScreenHandlers provide the protection.
 *
 * Ghost Item Prevention:
 * When the mixin cancels a container click packet on the server side, the
 * client does not immediately know about the cancellation due to network
 * latency (ping). This causes "ghost items" to appear in the player's
 * screen — items that exist on the client but not on the server.
 * After canceling, we force a container resync via sendContentUpdates(),
 * which ensures the client's inventory state matches the server immediately.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerEntityMixin {

    @Shadow
    public ServerPlayer player;

    /**
     * Intercepts container click packets before vanilla processing.
     *
     * If the player has a Solidus virtual GUI open, the click is processed
     * by the PacketHandler (which applies rate limiting and routes to the
     * appropriate ScreenHandler), and the vanilla handling is cancelled.
     *
     * If the player is using a normal vanilla container, the click is
     * passed through unchanged.
     *
     * CRITICAL: After canceling a packet, we MUST call sendContentUpdates()
     * to force the client to resync with the server's container state.
     * Without this, ghost items appear due to the client-server state
     * mismatch caused by network latency.
     */
    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void onContainerClick(
        net.minecraft.network.protocol.game.ServerboundContainerClickPacket packet,
        CallbackInfo ci) {

        PacketHandler packetHandler = SolidusMod.getPacketHandler();
        if (packetHandler == null) return;

        // Extract click data from the packet
        // In Minecraft 1.21.2+, ServerboundContainerClickPacket uses record-style accessors
        int slotIndex = packet.getSlotNum();
        int button = packet.getButtonNum();
        ClickType clickType = packet.getClickType();

        // Note: If using Yarn mappings for 1.21.7, the accessor method names may differ.
        // Common variants: getSlotNum() / slotNum(), getButtonNum() / buttonNum(),
        // getClickType() / clickType(). The record-style (no get prefix) is the modern standard.

        // Check if this is a Solidus GUI click
        boolean handled = packetHandler.handleContainerClick(
            player, slotIndex, button, clickType);

        if (handled) {
            // Cancel vanilla processing - the click has been handled by Solidus
            ci.cancel();

            // FORCE RESYNC: After canceling the packet on the server, the client
            // still has the pre-click state due to network latency. This causes
            // "ghost items" — items that appear to be in the container on the client
            // side but were never actually moved on the server side. By calling
            // sendContentUpdates(), we force the client to receive a fresh snapshot
            // of the container's actual state, making any phantom items disappear
            // in the same moment.
            player.containerMenu.sendContentUpdates();
        }
    }
}
