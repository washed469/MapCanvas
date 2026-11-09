package com.washed.mapcanvas.mixin;

import com.washed.mapcanvas.PosterHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrame.class)
public class ItemFrameMixin {

    @Inject(at = @At("HEAD"), method = "interact", cancellable = true)
    private void onInteract(Player player, InteractionHand hand, Vec3 hitPos, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand != InteractionHand.MAIN_HAND) return;
        ItemStack stack = player.getItemInHand(hand);
        if ((stack.getItem() == Items.FILLED_MAP)) {
            net.minecraft.world.item.component.CustomData nbt = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (nbt != null && nbt.copyTag().contains("isPoster")) {
                ItemFrame frame = (ItemFrame)(Object)this;
                if (!frame.getItem().isEmpty()) {
                    cir.setReturnValue(InteractionResult.FAIL);
                    cir.cancel();
                    return;
                }
                PosterHandler.tryPlacePoster(player, frame, stack);
                cir.setReturnValue(InteractionResult.SUCCESS);
                cir.cancel();
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "hurtServer", cancellable = true)
    private void onDamage(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.getEntity() instanceof Player player) {
            ItemFrame frame = (ItemFrame) (Object) this;
            if (frame.hasFramedMap()) {
                ItemStack held = frame.getItem();
                net.minecraft.world.item.component.CustomData nbt = held.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (nbt != null && nbt.copyTag().contains("isPosterSection")) {
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

    @Inject(at = @At("HEAD"), method = "hurtClient", cancellable = true)
    private void onClientDamage(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (source.getEntity() instanceof Player) {
            ItemFrame frame = (ItemFrame) (Object) this;
            if (frame.hasFramedMap()) {
                ItemStack held = frame.getItem();
                net.minecraft.world.item.component.CustomData nbt = held.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (nbt != null && nbt.copyTag().contains("isPosterSection")) {
                    cir.setReturnValue(false);
                    cir.cancel();
                }
            }
        }
    }
}
