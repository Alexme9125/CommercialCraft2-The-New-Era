package com.tycooncraft.tyc2.forge.mixin;

import com.industrialage.ia2.forge.equipment.ForgeGear;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enables {@code tyc2:comm_terminal} to charge directly inside IC2 BatBox, CESU, MFE, MFSU,
 * and Charging Pads when {@code ic2} is loaded (gated by {@link TyC2ForgeMixinPlugin}).
 */
@Mixin(value = ForgeGear.class, remap = false)
public class Ic2PortableGearMixin {

    @Inject(method = "acceptsCharge", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tyc2$acceptsCharge(ItemStack stack, int tier, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof CommTerminalItem) {
            cir.setReturnValue(tier >= 1 && CommTerminalItem.getStoredEu(stack) < TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
        }
    }

    @Inject(method = "charge", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tyc2$charge(ItemStack stack, double available, int tier, CallbackInfoReturnable<Double> cir) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof CommTerminalItem) {
            cir.setReturnValue(CommTerminalItem.chargeEu(stack, available, tier));
        }
    }

    @Inject(method = "electricalItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tyc2$electricalItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof CommTerminalItem) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "stored", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tyc2$stored(ItemStack stack, CallbackInfoReturnable<Double> cir) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof CommTerminalItem) {
            cir.setReturnValue((double) CommTerminalItem.getStoredEu(stack));
        }
    }

    @Inject(method = "capacity", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tyc2$capacity(ItemStack stack, CallbackInfoReturnable<Double> cir) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof CommTerminalItem) {
            cir.setReturnValue((double) TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
        }
    }

    @Inject(method = "chargeComplete", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void tyc2$chargeComplete(ItemStack stack, int tier, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof CommTerminalItem) {
            cir.setReturnValue(tier >= 1 && CommTerminalItem.getStoredEu(stack) >= TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
        }
    }

    @Inject(method = "powerSlotLimit", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void tyc2$powerSlotLimit(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof CommTerminalItem) {
            cir.setReturnValue(1);
        }
    }
}
