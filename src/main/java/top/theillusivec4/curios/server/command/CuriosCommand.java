/*
 * Copyright (c) 2018-2020 C4
 *
 * This file is part of Curios, a mod made for Minecraft.
 *
 * Curios is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Curios is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Curios.  If not, see <https://www.gnu.org/licenses/>.
 */

package top.theillusivec4.curios.server.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.network.NetworkHandler;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncCurios;

public class CuriosCommand {

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

    LiteralArgumentBuilder<CommandSourceStack> curiosCommand = Commands.literal("curios")
        .requires(player -> player.hasPermission(2));

    curiosCommand.then(Commands.literal("set").then(
        Commands.argument("slot", CurioArgumentType.slot()).then(
            Commands.argument("player", EntityArgument.player()).executes(
                context -> setSlotsForPlayer(context.getSource(),
                    EntityArgument.getPlayer(context, "player"),
                    CurioArgumentType.getSlot(context, "slot"), 1)).then(
                Commands.argument("amount", IntegerArgumentType.integer()).executes(
                    context -> setSlotsForPlayer(context.getSource(),
                        EntityArgument.getPlayer(context, "player"),
                        CurioArgumentType.getSlot(context, "slot"),
                        IntegerArgumentType.getInteger(context, "amount")))))));

    curiosCommand.then(Commands.literal("add").then(
        Commands.argument("slot", CurioArgumentType.slot()).then(
            Commands.argument("player", EntityArgument.player()).executes(
                context -> growSlotForPlayer(context.getSource(),
                    EntityArgument.getPlayer(context, "player"),
                    CurioArgumentType.getSlot(context, "slot"), 1)).then(
                Commands.argument("amount", IntegerArgumentType.integer()).executes(
                    context -> growSlotForPlayer(context.getSource(),
                        EntityArgument.getPlayer(context, "player"),
                        CurioArgumentType.getSlot(context, "slot"),
                        IntegerArgumentType.getInteger(context, "amount")))))));

    curiosCommand.then(Commands.literal("remove").then(
        Commands.argument("slot", CurioArgumentType.slot()).then(
            Commands.argument("player", EntityArgument.player()).executes(
                context -> shrinkSlotForPlayer(context.getSource(),
                    EntityArgument.getPlayer(context, "player"),
                    CurioArgumentType.getSlot(context, "slot"), 1)).then(
                Commands.argument("amount", IntegerArgumentType.integer()).executes(
                    context -> shrinkSlotForPlayer(context.getSource(),
                        EntityArgument.getPlayer(context, "player"),
                        CurioArgumentType.getSlot(context, "slot"),
                        IntegerArgumentType.getInteger(context, "amount")))))));

    curiosCommand.then(Commands.literal("clear").then(
        Commands.argument("player", EntityArgument.player()).executes(
            context -> clearSlotsForPlayer(context.getSource(),
                EntityArgument.getPlayer(context, "player"), "")).then(
            Commands.argument("slot", CurioArgumentType.slot()).executes(
                context -> clearSlotsForPlayer(context.getSource(),
                    EntityArgument.getPlayer(context, "player"),
                    CurioArgumentType.getSlot(context, "slot"))))));

    curiosCommand.then(Commands.literal("reset").then(
        Commands.argument("player", EntityArgument.player()).executes(
            context -> resetSlotsForPlayer(context.getSource(),
                EntityArgument.getPlayer(context, "player")))));

    dispatcher.register(curiosCommand);
  }

  private static int setSlotsForPlayer(CommandSourceStack source, ServerPlayer playerMP,
                                       String slot, int amount) {
    CuriosApi.getSlotHelper().setSlotsForType(slot, playerMP, amount);
    source.sendSuccess(new TranslatableComponent("commands.curios.set.success", slot,
            CuriosApi.getSlotHelper().getSlotsForType(playerMP, slot), playerMP.getDisplayName()),
        true);
    return Command.SINGLE_SUCCESS;
  }

  private static int growSlotForPlayer(CommandSourceStack source, ServerPlayer playerMP,
                                       String slot, int amount) {
    CuriosApi.getSlotHelper().growSlotType(slot, amount, playerMP);
    source.sendSuccess(new TranslatableComponent("commands.curios.add.success", amount, slot,
        playerMP.getDisplayName()), true);
    return Command.SINGLE_SUCCESS;
  }

  private static int shrinkSlotForPlayer(CommandSourceStack source, ServerPlayer playerMP,
                                         String slot, int amount) {
    CuriosApi.getSlotHelper().shrinkSlotType(slot, amount, playerMP);
    source.sendSuccess(new TranslatableComponent("commands.curios.remove.success", amount, slot,
        playerMP.getDisplayName()), true);
    return Command.SINGLE_SUCCESS;
  }

  private static int clearSlotsForPlayer(CommandSourceStack source, ServerPlayer playerMP,
                                         String slot) {

    CuriosApi.getCuriosHelper().getCuriosHandler(playerMP).ifPresent(handler -> {
      Map<String, ICurioStacksHandler> curios = handler.getCurios();

      if (!slot.isEmpty() && curios.get(slot) != null) {
        clear(curios.get(slot));
      } else {

        for (String id : curios.keySet()) {
          clear(curios.get(id));
        }
      }
    });

    if (slot.isEmpty()) {
      source.sendSuccess(new TranslatableComponent("commands.curios.clearAll.success",
          playerMP.getDisplayName()), true);
    } else {
      source.sendSuccess(new TranslatableComponent("commands.curios.clear.success", slot,
          playerMP.getDisplayName()), true);
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int resetSlotsForPlayer(CommandSourceStack source, ServerPlayer playerMP) {
    CuriosApi.getCuriosHelper().getCuriosHandler(playerMP).ifPresent(handler -> {
      handler.reset();
      NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> playerMP),
          new SPacketSyncCurios(playerMP.getId(), handler.getCurios()));
    });
    source.sendSuccess(
        new TranslatableComponent("commands.curios.reset.success", playerMP.getDisplayName()),
        true);
    return Command.SINGLE_SUCCESS;
  }

  private static void clear(ICurioStacksHandler stacksHandler) {

    for (int i = 0; i < stacksHandler.getSlots(); i++) {
      stacksHandler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
      stacksHandler.getCosmeticStacks().setStackInSlot(i, ItemStack.EMPTY);
    }
  }
}
