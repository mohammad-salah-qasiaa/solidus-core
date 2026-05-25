package com.solidus.commands;

import com.solidus.shop.ShopManager;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * /shop command - Opens the virtual server shop GUI.
 *
 * Usage: /shop
 * Permission: Available to all players
 *
 * This command triggers the server to open a virtual GENERIC_9x6 container
 * screen on the player's client. The GUI is entirely server-driven via
 * packet manipulation - no client mod required.
 */
public class ShopCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ShopManager shopManager) {
        dispatcher.register(Commands.literal("shop")
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                shopManager.openShop(player);
                return 1;
            })
        );
    }
}
