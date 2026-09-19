package com.misionesmod.mixin;

import com.misionesmod.mission.MissionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {

    @Shadow @Final private Player player;
    @Shadow private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void onCheckTakeAchievements(ItemStack stack, CallbackInfo ci) {
        if (this.player instanceof ServerPlayer serverPlayer && stack != null && !stack.isEmpty()) {
            int count = this.removeCount > 0 ? this.removeCount : stack.getCount();
            if (count > 0) {
                MissionManager.onItemSmelted(serverPlayer, stack.getItem(), count);
            }
        }
    }
}
