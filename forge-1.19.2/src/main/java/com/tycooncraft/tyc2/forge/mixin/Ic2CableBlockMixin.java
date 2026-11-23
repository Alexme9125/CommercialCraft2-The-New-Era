package com.tycooncraft.tyc2.forge.mixin;

import com.industrialage.ia2.forge.content.CableBlock;
import com.tycooncraft.tyc2.forge.block.TyC2PowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows IC2 cables to visually connect arms to TyC2 powered machines ({@link TyC2PowerBlock}).
 * Only applied when {@code ic2} is loaded (gated by {@link TyC2ForgeMixinPlugin}).
 */
@Mixin(value = CableBlock.class, remap = false)
public class Ic2CableBlockMixin {

    @Inject(method = "connects", at = @At("RETURN"), cancellable = true, remap = false)
    private void tyc2$connectsToPowerBlock(
            BlockState state,
            BlockState neighbor,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ() && neighbor.getBlock() instanceof TyC2PowerBlock) {
            cir.setReturnValue(true);
        }
    }
}
