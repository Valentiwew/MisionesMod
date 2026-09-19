package com.misionesmod.mixin;

import com.misionesmod.mission.MissionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
public class FurnaceResultSlotMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void onSmeltTake(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && stack != null && !stack.isEmpty()) {
            MissionManager.onItemSmelted(serverPlayer, stack.getItem(), stack.getCount());
        }
    }
}
