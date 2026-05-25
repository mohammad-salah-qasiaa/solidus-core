package com.solidus.commands;

import com.solidus.economy.BalanceManager;
import com.solidus.util.TextUtil;
import com.solidus.util.CurrencyUtil;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /pay command - Safe peer-to-peer balance transfer with validation checks.
 *
 * Usage: /pay <player> <amount>
 * Permission: Available to all players
 *
 * Anti-Exploit Protections:
 * - Negative amount rejection (prevents reverse-transfer exploitation)
 * - Zero amount rejection (prevents spam)
 * - Self-transfer rejection (prevents confusion)
 * - Maximum transaction cap enforcement
 * - Recipient existence validation
 * - Atomic deduct-then-add operation with rollback on failure
 *
 * All text uses Component.literal().withStyle() - NO legacy formatting codes.
 */
public class PayCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, BalanceManager balanceManager) {
        dispatcher.register(Commands.literal("pay")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(CurrencyUtil.MIN_TRANSACTION))
                    .executes(context -> {
                        ServerPlayer sender = context.getSource().getPlayerOrException();
                        ServerPlayer receiver = EntityArgument.getPlayer(context, "player");
                        double amount = DoubleArgumentType.getDouble(context, "amount");
                        executePay(sender, receiver, amount, balanceManager);
                        return 1;
                    })
                )
            )
        );
    }

    private static void executePay(ServerPlayer sender, ServerPlayer receiver, double amount, BalanceManager balanceManager) {
        // Pre-validation: reject negative or zero amounts
        if (amount <= 0) {
            sender.sendSystemMessage(TextUtil.error("Amount must be positive!"));
            return;
        }

        // Pre-validation: reject amounts exceeding the maximum transaction cap
        if (amount > CurrencyUtil.MAX_TRANSACTION) {
            sender.sendSystemMessage(TextUtil.error(
                "Amount exceeds maximum transfer limit of " + CurrencyUtil.format(CurrencyUtil.MAX_TRANSACTION)));
            return;
        }

        // Pre-validation: prevent self-transfer
        if (sender.getUUID().equals(receiver.getUUID())) {
            sender.sendSystemMessage(TextUtil.error("You cannot pay yourself!"));
            return;
        }

        // Perform atomic transfer
        balanceManager.transfer(sender, receiver, amount).thenAccept(result -> {
            // Schedule notification on the server thread
            sender.server.execute(() -> {
                if (result.success()) {
                    // Notify sender
                    sender.sendSystemMessage(
                        TextUtil.success("You paid " + receiver.getName().getString() + " ")
                            .append(TextUtil.currency(CurrencyUtil.format(amount)))
                            .append(TextUtil.plain(". "))
                            .append(TextUtil.styled("New balance: ", net.minecraft.ChatFormatting.GRAY))
                            .append(TextUtil.currency(CurrencyUtil.format(result.senderNewBalance())))
                    );

                    // Notify receiver
                    receiver.sendSystemMessage(
                        TextUtil.success("You received " + CurrencyUtil.format(amount) + " from ")
                            .append(TextUtil.styled(sender.getName().getString(), net.minecraft.ChatFormatting.YELLOW))
                            .append(TextUtil.plain(". "))
                            .append(TextUtil.styled("New balance: ", net.minecraft.ChatFormatting.GRAY))
                            .append(TextUtil.currency(CurrencyUtil.format(result.receiverNewBalance())))
                    );
                } else {
                    // Transfer failed
                    sender.sendSystemMessage(
                        TextUtil.error(result.message())
                    );
                }
            });
        });
    }
}
