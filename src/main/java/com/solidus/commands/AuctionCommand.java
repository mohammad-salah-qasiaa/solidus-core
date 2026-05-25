package com.solidus.commands;

import com.solidus.auction.AuctionManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * /ah command - Auction House commands.
 *
 * Usage:
 *   /ah           - View global auction listings
 *   /ah sell <price>  - List the item in main hand for the specified price
 *
 * Permission: Available to all players
 *
 * The Auction House excludes structural progression items like Armor Trims
 * from the virtual server shop, forcing them into player-driven commerce
 * to incentivize real survival exploration.
 */
public class AuctionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, AuctionManager auctionManager) {
        // /ah - View listings
        dispatcher.register(Commands.literal("ah")
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                auctionManager.openAuction(player);
                return 1;
            })
            // /ah sell <price> - List held item
            .then(Commands.literal("sell")
                .then(Commands.argument("price", DoubleArgumentType.doubleArg(1.0))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        double price = DoubleArgumentType.getDouble(context, "price");
                        auctionManager.listItem(player, price);
                        return 1;
                    })
                )
            )
        );
    }
}
