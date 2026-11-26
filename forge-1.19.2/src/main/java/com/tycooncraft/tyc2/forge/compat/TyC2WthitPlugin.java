package com.tycooncraft.tyc2.forge.compat;

import com.tycooncraft.tyc2.forge.block.AtmMachineBlock;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlock;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.forge.block.MintMachineBlock;
import com.tycooncraft.tyc2.forge.block.MintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.PrimitiveMintMachineBlock;
import com.tycooncraft.tyc2.forge.block.PrimitiveMintMachineBlockEntity;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Optional soft integration with WTHIT ({@code wthit}) on Forge 1.19.2, registered via
 * {@code wthit_plugins.json} and loaded exclusively when WTHIT is installed.
 */
public final class TyC2WthitPlugin implements IWailaCommonPlugin, IWailaClientPlugin {

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.blockData(BlockDataProvider.INSTANCE, CooperationHubBlockEntity.class);
        registrar.blockData(BlockDataProvider.INSTANCE, PrimitiveMintMachineBlockEntity.class);
        registrar.blockData(BlockDataProvider.INSTANCE, MintMachineBlockEntity.class);
        registrar.blockData(BlockDataProvider.INSTANCE, AtmMachineBlockEntity.class);
        registrar.entityData(MayorDataProvider.INSTANCE, Villager.class);
    }

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(BlockComponentProvider.INSTANCE, CooperationHubBlock.class);
        registrar.body(BlockComponentProvider.INSTANCE, PrimitiveMintMachineBlock.class);
        registrar.body(BlockComponentProvider.INSTANCE, MintMachineBlock.class);
        registrar.body(BlockComponentProvider.INSTANCE, AtmMachineBlock.class);
        registrar.body(MayorComponentProvider.INSTANCE, Villager.class);
    }

    private enum BlockDataProvider implements IDataProvider<BlockEntity> {
        INSTANCE;

        @Override
        public void appendData(
                IDataWriter data,
                IServerAccessor<BlockEntity> accessor,
                IPluginConfig config
        ) {
            BlockEntity be = accessor.getTarget();
            ServerLevel serverLevel = accessor.getLevel();
            if (be != null && serverLevel != null) {
                TyC2ProbeHelper.writeBlockData(data.raw(), serverLevel, be);
            }
        }
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

    private enum MayorDataProvider implements IDataProvider<Villager> {
        INSTANCE;

        @Override
        public void appendData(
                IDataWriter data,
                IServerAccessor<Villager> accessor,
                IPluginConfig config
        ) {
            Villager villager = accessor.getTarget();
            ServerLevel serverLevel = accessor.getLevel();
            if (villager != null && serverLevel != null) {
                TyC2ProbeHelper.writeEntityData(data.raw(), serverLevel, villager);
            }
        }
    }

    private enum MayorComponentProvider implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            for (Component line : TyC2ProbeHelper.readEntityLines(accessor.getData().raw())) {
                tooltip.addLine(line);
            }
        }
    }
}
