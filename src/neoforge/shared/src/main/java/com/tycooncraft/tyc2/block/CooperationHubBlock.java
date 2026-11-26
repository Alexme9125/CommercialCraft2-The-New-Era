package com.tycooncraft.tyc2.block;

import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.blockentity.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.item.CooperationHubBlockItem;
import com.tycooncraft.tyc2.platform.TyC2BlockBase;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.village.BlockPos3i;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Village Cooperation Hub ({@code tyc2:cooperation_hub}):
 * blast-proof, unbreakable-by-mining municipal center backing a 1,000-slot dual-zone
 * warehouse (0..499 Inbound insert-only, 500..999 Outbound extract-only) persisted in
 * {@code TycoonSavedData}.
 */
public final class CooperationHubBlock extends TyC2BlockBase {

    public CooperationHubBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean allowEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CooperationHubBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (TyC2Native.isClientSide(level)) {
            return null;
        }
        return type == TyC2Registries.COOPERATION_HUB_BE.get()
                ? (lvl, pos, st, be) -> ((CooperationHubBlockEntity) be).serverTick((ServerLevel) lvl)
                : null;
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof CooperationHubBlockEntity be)) {
            return;
        }
        Optional<UUID> boundId = CooperationHubBlockItem.getBoundVillageId(stack);
        if (boundId.isPresent()) {
            UUID villageId = boundId.get();
            Optional<VillageRecord> existingOpt = TyC2WorldManager.getVillage(serverLevel, villageId);
            if (existingOpt.isPresent()) {
                VillageRecord v = existingOpt.get();
                BlockPos oldPos = new BlockPos(v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z());
                String curDim = TyC2Native.dimensionId(serverLevel);
                if (curDim.equals(v.getDimensionId())
                        && !oldPos.equals(pos)
                        && serverLevel.hasChunkAt(oldPos)
                        && serverLevel.getBlockState(oldPos).is(TyC2Registries.COOPERATION_HUB_BLOCK.get())) {
                    // Refuse aliasing a second Hub onto a village whose live Hub already exists
                    serverLevel.removeBlock(pos, false);
                    if (placer instanceof Player p) {
                        TyC2Native.sendActionBar(
                                p,
                                Component.translatable("status.tyc2.hub.protected").withStyle(ChatFormatting.RED)
                        );
                    }
                    return;
                }
                be.setVillageId(villageId);
                v.setHubPos(new BlockPos3i(pos.getX(), pos.getY(), pos.getZ()));
                v.setDimensionId(curDim);
                TyC2WorldManager.markDirty(serverLevel);
            } else {
                be.setVillageId(villageId);
            }
        } else if (be.getVillageId() == null) {
            // Creative-placed hub: create/attach a partnered village immediately so it works out of the box
            UUID ownerUuid = placer instanceof Player p ? p.getUUID() : null;
            VillageRecord created = TyC2WorldManager.getOrCreateVillageForHub(serverLevel, pos, ownerUuid);
            be.setVillageId(created.getVillageId());
        }
    }

    @Override
    protected boolean handleUseWithItem(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        // Sneak+Right-Click terminal binding is handled cleanly in CommTerminalItem.onItemUseFirst
        // so it fires even when both hands hold items and never double-toggles.
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (TyC2Native.isClientSide(level)) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof CooperationHubBlockEntity be) {
            be.resolveVillage(serverLevel);
            serverPlayer.openMenu(be, pos);
            be.sendSyncTo(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected int computeAnalogOutputSignal(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof CooperationHubBlockEntity be) {
            return be.computeComparatorSignal(serverLevel);
        }
        return 0;
    }
}
