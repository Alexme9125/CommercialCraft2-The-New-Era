package com.tycooncraft.tyc2.mixin;

import com.industrialage.ia2.gameplay.PortableGear;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Integrates TyC2's handheld {@link CommTerminalItem} into IC2's {@link PortableGear} charging
 * pipeline so players can recharge terminals inside IC2 BatBoxes, CESUs, MFEs, MFSUs, and Charge
 * Pads when IC2 is installed. Gated by {@link TyC2MixinPlugin}.
 */
@Mixin(value = PortableGear.class, remap = false)
public abstract class Ic2PortableGearMixin {

    @Inject(method = "powerSlotLimit", at = @At("HEAD"), cancellable = true)
    private static void tyc2$powerSlotLimit(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack != null && stack.getItem() instanceof CommTerminalItem) {
            cir.setReturnValue(1);
        }
    }

    @Inject(method = "acceptsCharge", at = @At("HEAD"), cancellable = true)
    private static void tyc2$acceptsCharge(ItemStack stack, int tier, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && stack.getItem() instanceof CommTerminalItem) {
            boolean canAccept = tier >= 1
                    && CommTerminalItem.getEnergyEu(stack) < TriModePowerMath.TERMINAL_MAX_CAPACITY_EU;
            cir.setReturnValue(canAccept);
        }
    }

    @Inject(method = "chargeComplete", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tyc2$chargeComplete(ItemStack stack, int tier, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && stack.getItem() instanceof CommTerminalItem) {
            boolean full = CommTerminalItem.getEnergyEu(stack) >= TriModePowerMath.TERMINAL_MAX_CAPACITY_EU;
            cir.setReturnValue(full);
        }
    }

    @Inject(method = "charge", at = @At("HEAD"), cancellable = true)
    private static void tyc2$charge(
            ItemStack stack,
            double offered,
            int tier,
            CallbackInfoReturnable<Double> cir
    ) {
        if (stack != null && stack.getItem() instanceof CommTerminalItem) {
            if (tier < 1 || offered <= 0.0) {
                cir.setReturnValue(0.0);
                return;
            }
            int capped = (int) Math.min(
                    Math.floor(offered),
                    TriModePowerMath.TERMINAL_CHARGE_RATE_EU_PER_TICK
            );
            int accepted = CommTerminalItem.chargeEu(stack, capped);
            cir.setReturnValue((double) accepted);
        }
    }

    @Inject(method = "stored", at = @At("HEAD"), cancellable = true)
    private static void tyc2$stored(ItemStack stack, CallbackInfoReturnable<Double> cir) {
        if (stack != null && stack.getItem() instanceof CommTerminalItem) {
            cir.setReturnValue((double) CommTerminalItem.getEnergyEu(stack));
        }
    }

    @Inject(method = "capacity", at = @At("HEAD"), cancellable = true)
    private static void tyc2$capacity(ItemStack stack, CallbackInfoReturnable<Double> cir) {
        if (stack != null && stack.getItem() instanceof CommTerminalItem) {
            cir.setReturnValue((double) TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
        }
    }

    @Inject(method = "electricalItem", at = @At("HEAD"), cancellable = true)
    private static void tyc2$electricalItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && stack.getItem() instanceof CommTerminalItem) {
            cir.setReturnValue(true);
        }
    }
}
