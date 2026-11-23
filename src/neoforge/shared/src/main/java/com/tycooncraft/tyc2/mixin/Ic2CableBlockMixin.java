package com.tycooncraft.tyc2.mixin;

import com.industrialage.ia2.gameplay.CableBlock;
import com.tycooncraft.tyc2.block.TyC2PowerBlock;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows IC2 cables to visually connect to TyC2 tri-mode power blocks ({@link TyC2PowerBlock}:
 * Minting Machine and Industrial ATM). Loaded conditionally via {@link TyC2MixinPlugin} only when
 * IC2 is installed.
 */
@Mixin(value = CableBlock.class, remap = false)
public abstract class Ic2CableBlockMixin {

    @Shadow
    private static Map<Direction, BooleanProperty> CONNECTIONS;

    @Inject(method = "updateConnection", at = @At("HEAD"), cancellable = true)
    private void tyc2$connectToTyC2PowerBlock(
            BlockState state,
            Direction side,
            BlockState neighbor,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if (neighbor != null && neighbor.getBlock() instanceof TyC2PowerBlock && CONNECTIONS != null) {
            BooleanProperty prop = CONNECTIONS.get(side);
            if (prop != null && state.hasProperty(prop)) {
                cir.setReturnValue(state.setValue(prop, true));
            }
        }
    }
}
