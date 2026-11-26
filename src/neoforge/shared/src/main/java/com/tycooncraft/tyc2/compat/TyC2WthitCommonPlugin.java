package com.tycooncraft.tyc2.compat;

import com.tycooncraft.tyc2.blockentity.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.blockentity.MintMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.PrimitiveMintMachineBlockEntity;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared NeoForge WTHIT common plugin syncing Cooperation Hub, Primitive Minting Machine,
 * Industrial Minting Machine, Industrial ATM, and Village Mayor telemetry across 1.21.1,
 * 26.1.2, and 26.2. Loaded exclusively by WTHIT via {@code wthit_plugins.json}.
 */
public final class TyC2WthitCommonPlugin implements IWailaCommonPlugin {

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.blockData(BlockDataProvider.INSTANCE, CooperationHubBlockEntity.class);
        registrar.blockData(BlockDataProvider.INSTANCE, PrimitiveMintMachineBlockEntity.class);
        registrar.blockData(BlockDataProvider.INSTANCE, MintMachineBlockEntity.class);
        registrar.blockData(BlockDataProvider.INSTANCE, AtmMachineBlockEntity.class);
        registrar.entityData(EntityDataProvider.INSTANCE, Entity.class);
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
            if (be != null && accessor.getLevel() instanceof ServerLevel serverLevel) {
                TyC2ProbeHelper.writeBlockData(data.raw(), serverLevel, be);
            }
        }
    }

    private enum EntityDataProvider implements IDataProvider<Entity> {
        INSTANCE;

        @Override
        public void appendData(
                IDataWriter data,
                IServerAccessor<Entity> accessor,
                IPluginConfig config
        ) {
            Entity entity = accessor.getTarget();
            if (entity != null && accessor.getLevel() instanceof ServerLevel serverLevel) {
                TyC2ProbeHelper.writeEntityData(data.raw(), serverLevel, entity);
            }
        }
    }
}
