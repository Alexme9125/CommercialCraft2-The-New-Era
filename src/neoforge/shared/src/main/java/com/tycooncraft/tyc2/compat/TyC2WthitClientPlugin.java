package com.tycooncraft.tyc2.compat;

import com.tycooncraft.tyc2.block.AtmMachineBlock;
import com.tycooncraft.tyc2.block.CooperationHubBlock;
import com.tycooncraft.tyc2.block.MintMachineBlock;
import com.tycooncraft.tyc2.block.PrimitiveMintMachineBlock;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Shared NeoForge WTHIT client plugin rendering Cooperation Hub, Primitive Minting Machine,
 * Industrial Minting Machine, Industrial ATM, and Village Mayor telemetry across 1.21.1,
 * 26.1.2, and 26.2. Loaded exclusively by WTHIT via {@code wthit_plugins.json}.
 */
public final class TyC2WthitClientPlugin implements IWailaClientPlugin {

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(BlockComponentProvider.INSTANCE, CooperationHubBlock.class);
        registrar.body(BlockComponentProvider.INSTANCE, PrimitiveMintMachineBlock.class);
        registrar.body(BlockComponentProvider.INSTANCE, MintMachineBlock.class);
        registrar.body(BlockComponentProvider.INSTANCE, AtmMachineBlock.class);
        registrar.body(EntityComponentProvider.INSTANCE, Entity.class);
    }

    private enum BlockComponentProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            for (Component line : TyC2ProbeHelper.readBlockLines(accessor.getData().raw())) {
                tooltip.addLine(line);
            }
        }
    }

    private enum EntityComponentProvider implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            for (Component line : TyC2ProbeHelper.readEntityLines(accessor.getData().raw())) {
                tooltip.addLine(line);
            }
        }
    }
}
