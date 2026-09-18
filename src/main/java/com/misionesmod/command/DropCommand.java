package com.misionesmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.misionesmod.drop.DropManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class DropCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("drop")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("loot")
                    .executes(context -> {
                        if (context.getSource().getEntity() instanceof ServerPlayer player) {
                            DropManager.openDropLootEditor(player);
                            return 1;
                        }
                        return 0;
                    })
                )
                .then(Commands.literal("aqui")
                    .executes(context -> triggerHere(context.getSource(), "raro"))
                    .then(Commands.argument("tier", StringArgumentType.word())
                        .executes(context -> triggerHere(context.getSource(), StringArgumentType.getString(context, "tier")))
                    )
                )
                .then(Commands.literal("en")
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(context -> {
                                int x = IntegerArgumentType.getInteger(context, "x");
                                int z = IntegerArgumentType.getInteger(context, "z");
                                return triggerAt(context.getSource(), new BlockPos(x, 64, z), "raro");
                            })
                            .then(Commands.argument("tier", StringArgumentType.word())
                                .executes(context -> {
                                    int x = IntegerArgumentType.getInteger(context, "x");
                                    int z = IntegerArgumentType.getInteger(context, "z");
                                    String tier = StringArgumentType.getString(context, "tier");
                                    return triggerAt(context.getSource(), new BlockPos(x, 64, z), tier);
                                })
                            )
                        )
                    )
                )
                .then(Commands.literal("random")
                    .executes(context -> triggerRandom(context.getSource(), 200, "raro"))
                    .then(Commands.argument("radio", IntegerArgumentType.integer(10, 5000))
                        .executes(context -> triggerRandom(context.getSource(), IntegerArgumentType.getInteger(context, "radio"), "raro"))
                        .then(Commands.argument("tier", StringArgumentType.word())
                            .executes(context -> triggerRandom(
                                    context.getSource(),
                                    IntegerArgumentType.getInteger(context, "radio"),
                                    StringArgumentType.getString(context, "tier")
                            ))
                        )
                    )
                )
        );
    }

    private static int triggerHere(CommandSourceStack source, String tier) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Este comando debe ser ejecutado por un jugador en el juego."));
            return 0;
        }
        DropManager.spawnDrop(player.level(), player.blockPosition(), tier);
        source.sendSuccess(() -> Component.literal("§aDrop lanzado con éxito en tu posición actual."), true);
        return 1;
    }

    private static int triggerAt(CommandSourceStack source, BlockPos pos, String tier) {
        DropManager.spawnDrop(source.getLevel(), pos, tier);
        source.sendSuccess(() -> Component.literal("§aDrop lanzado en coordenadas: " + pos.getX() + ", " + pos.getZ()), true);
        return 1;
    }

    private static int triggerRandom(CommandSourceStack source, int radius, String tier) {
        BlockPos center = source.getEntity() != null ? source.getEntity().blockPosition() : BlockPos.ZERO;
        int dx = (int) ((Math.random() * 2 - 1) * radius);
        int dz = (int) ((Math.random() * 2 - 1) * radius);
        BlockPos targetPos = center.offset(dx, 0, dz);

        DropManager.spawnDrop(source.getLevel(), targetPos, tier);
        source.sendSuccess(() -> Component.literal("§aDrop aleatorio lanzado a " + Math.hypot(dx, dz) + " bloques."), true);
        return 1;
    }
}
