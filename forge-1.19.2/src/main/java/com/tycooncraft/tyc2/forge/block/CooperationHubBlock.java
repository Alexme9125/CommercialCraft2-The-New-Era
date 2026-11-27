package com.tycooncraft.tyc2.forge.block;

import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.item.TyC2BlockItem;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.BiomeStyle;
import com.tycooncraft.tyc2.village.VillageRecord;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * Blast-proof, mining-protected Village Cooperation Hub block ({@code tyc2:cooperation_hub})
 * backed by a 1,000-slot SavedData dual-zone warehouse and village meeting POI.
 */
public class CooperationHubBlock extends Block implements EntityBlock {

    public CooperationHubBlock() {
        super(BlockBehaviour.Properties.of(Material.METAL)
                .strength(-1.0F, 3_600_000.0F)
                .sound(SoundType.METAL));
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CooperationHubBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != TyC2ForgeRegistries.COOPERATION_HUB_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof CooperationHubBlockEntity hub && lvl instanceof ServerLevel serverLevel) {
                hub.serverTick(serverLevel);
            }
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof CooperationHubBlockEntity hub) {
                UUID vid = hub.resolveVillageId();
                if (vid != null) {
                    TycoonForgeSavedData data = TycoonForgeSavedData.get(serverLevel);
                    Optional<VillageRecord> vOpt = data.getVillage(vid);
                    if (vOpt.isPresent()) {
                        VillageRecord v = vOpt.get();
                        String dim = serverLevel.dimension().location().toString();
                        if (dim.equals(v.getDimensionId()) && pos.equals(TyC2Ids.toMc(v.getHubPos()))) {
                            boolean dirty = false;
                            if (v.isChunkLoaderInstalled()) {
                                v.setChunkLoaderInstalled(false);
                                dirty = true;
                            }
                            if (v.isHubDeployed()) {
                                v.setHubDeployed(false);
                                dirty = true;
                            }
                            if (dirty) {
                                data.setDirty();
                            }
                        }
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof CooperationHubBlockEntity hub)) {
            return;
        }
        TycoonForgeSavedData data = TycoonForgeSavedData.get(serverLevel);
        String dim = serverLevel.dimension().location().toString();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(TyC2BlockItem.TAG_BOUND_VILLAGE_ID)) {
            UUID boundId = tag.getUUID(TyC2BlockItem.TAG_BOUND_VILLAGE_ID);
            Optional<VillageRecord> existingOpt = data.getVillage(boundId);
            if (existingOpt.isEmpty()) {
                serverLevel.removeBlock(pos, false);
                return;
            }
            VillageRecord existing = existingOpt.get();
            if (!existing.isPartnered()) {
                serverLevel.removeBlock(pos, false);
                if (placer instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.translatable("status.tyc2.hub.protected").withStyle(ChatFormatting.RED),
                            true
                    );
                }
                return;
            }
            if (existing.isHubDeployed()) {
                // Self-heal stale hubDeployed=true ONLY when the recorded Hub location is in a loaded chunk
                // and has no live Cooperation Hub block (e.g., cleared via /setblock, /fill, or WorldEdit).
                // Unloaded or live Hub locations remain strictly rejected to prevent duplicate aliasing (M4).
                ServerLevel oldLevel = TycoonForgeWorldManager.resolveLevel(
                        serverLevel.getServer(), existing.getDimensionId()
                );
                BlockPos oldPos = TyC2Ids.toMc(existing.getHubPos());
                boolean sameSpot = oldLevel == serverLevel && oldPos.equals(pos);
                boolean staleFlag = sameSpot
                        || (oldLevel != null
                        && oldLevel.isLoaded(oldPos)
                        && !oldLevel.getBlockState(oldPos).is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get()));
                if (!staleFlag) {
                    serverLevel.removeBlock(pos, false);
                    if (placer instanceof ServerPlayer sp) {
                        sp.displayClientMessage(
                                Component.translatable("status.tyc2.hub.protected").withStyle(ChatFormatting.RED),
                                true
                        );
                    }
                    return;
                }
                existing.setHubDeployed(false);
                data.setDirty();
            }
            if (!dim.equals(existing.getDimensionId())) {
                // Keep bound Hubs strictly inside their home village dimension
                serverLevel.removeBlock(pos, false);
                if (placer instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.translatable("status.tyc2.hub.wrong_dimension").withStyle(ChatFormatting.RED),
                            true
                    );
                    if (!sp.isCreative()) {
                        ItemStack refund = stack.copy();
                        refund.setCount(1);
                        if (!sp.getInventory().add(refund)) {
                            sp.drop(refund, false);
                        }
                    }
                }
                return;
            }
            existing.setHubPos(TyC2Ids.toCore(pos));
            existing.setHubDeployed(true);
            data.setDirty();
            hub.setVillageId(boundId);
            return;
        }

        // Creative placement without bound village: create a partnered village record anchored here
        if (placer instanceof ServerPlayer player) {
            BiomeStyle style = serverLevel.getBiome(pos).unwrapKey()
                    .map(k -> BiomeStyle.fromBiomeId(k.location().toString()))
                    .orElse(BiomeStyle.PLAINS);
            VillageRecord rec = new VillageRecord(
                    UUID.randomUUID(),
                    dim,
                    data.allocateDefaultVillageName(style),
                    TyC2Ids.toCore(pos),
                    style
            );
            TyC2Config cfg = TyC2RuntimeData.config();
            data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
            BankAccountManager.AccountRef activeRef = data.bank().resolveSettlementAccount(player.getUUID());
            String activeCo = activeRef.scope() == BankAccountManager.AccountScope.COMPANY
                    ? activeRef.accountId() : null;
            rec.establishPartnershipWithWorkingCapital(
                    player.getUUID(), activeCo, cfg.negotiationCostIc(), cfg.initialVillageWorkingCapital()
            );
            rec.setHubDeployed(true);
            rec.setPopulation(2);
            long dayTime = serverLevel.getServer().overworld().getDayTime();
            rec.anchorStipendIfUnset(dayTime);
            data.putVillage(rec);
            TycoonForgeWorldManager.refreshVillageOrders(serverLevel, data, rec, dayTime);
            data.syncMacroAccountTotals();
            data.setDirty();
            hub.setVillageId(rec.getVillageId());
        }
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof CooperationHubBlockEntity hub)) {
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && held.getItem() instanceof CommTerminalItem) {
            CommTerminalItem.handleHubBind(serverPlayer, held, hub);
            return InteractionResult.CONSUME;
        }

        NetworkHooks.openScreen(serverPlayer, hub, pos);
        return InteractionResult.CONSUME;
    }
}
