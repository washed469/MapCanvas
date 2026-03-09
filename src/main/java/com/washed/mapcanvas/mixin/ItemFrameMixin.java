package com.washed.mapcanvas.mixin;

import com.washed.mapcanvas.PosterHandler;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrameEntity.class)
public class ItemFrameMixin {

    @Inject(at = @At("HEAD"), method = "interact", cancellable = true)
    private void onInteract(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (hand != Hand.MAIN_HAND) return;
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isOf(Items.FILLED_MAP)) {
            net.minecraft.component.type.NbtComponent nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
            if (nbt != null && nbt.copyNbt().contains("isPoster")) {
                ItemFrameEntity frame = (ItemFrameEntity)(Object)this;
                if (!frame.getHeldItemStack().isEmpty()) {
                    cir.setReturnValue(ActionResult.FAIL);
                    cir.cancel();
                    return;
                }
                PosterHandler.tryPlacePoster(player, frame, stack);
                cir.setReturnValue(ActionResult.SUCCESS);
                cir.cancel();
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "damage", cancellable = true)
    private void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.getAttacker() instanceof PlayerEntity player) {
            ItemFrameEntity frame = (ItemFrameEntity) (Object) this;
            if (frame.containsMap()) {
                ItemStack held = frame.getHeldItemStack();
                net.minecraft.component.type.NbtComponent nbt = held.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                if (nbt != null && nbt.copyNbt().contains("isPosterSection")) {
                    PosterHandler.tryCollectPoster(player, frame);
                    cir.setReturnValue(false);
                    cir.cancel();
                } else if (PosterHandler.collectingFrames.contains(frame.getId())) {
                    cir.setReturnValue(false);
                    cir.cancel();
                }
            } else if (PosterHandler.collectingFrames.contains(frame.getId())) {
                cir.setReturnValue(false);
                cir.cancel();
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "clientDamage", cancellable = true)
    private void onClientDamage(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (source.getAttacker() instanceof PlayerEntity) {
            ItemFrameEntity frame = (ItemFrameEntity) (Object) this;
            if (frame.containsMap()) {
                ItemStack held = frame.getHeldItemStack();
                net.minecraft.component.type.NbtComponent nbt = held.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                if (nbt != null && nbt.copyNbt().contains("isPosterSection")) {
                    cir.setReturnValue(false);
                    cir.cancel();
                }
            }
        }
    }
}