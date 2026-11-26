package com.tycooncraft.tyc2.forge.network;

import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.client.TyC2ClientHandlers;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.VillageRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C snapshot for {@link com.tycooncraft.tyc2.forge.gui.CommTerminalMenu} carrying battery state,
 * up to 16 bound village summaries, selected village full snapshot, remote stock summaries, and
 * translatable feedback component.
 */
public final class S2CTerminalSyncPacket {

    public record BoundVillageDto(
            UUID villageId,
            String name,
            String biomeStyleId,
            int level,
            long balance,
            boolean partnered,
            boolean depopulated
    ) {
    }

    public record StockEntry(String itemId, int count) {
    }

    public final int batteryEu;
    public final int maxBatteryEu;
    public final int activeIndex;
    public final List<BoundVillageDto> boundVillages;
    @Nullable
    public final S2CHubSyncPacket selectedVillage;
    public final List<StockEntry> inboundSummary;
    public final List<StockEntry> outboundSummary;
    public final Component feedbackComponent;
    public final String feedback;

    public S2CTerminalSyncPacket(
            int batteryEu,
            int maxBatteryEu,
            int activeIndex,
            List<BoundVillageDto> boundVillages,
            @Nullable S2CHubSyncPacket selectedVillage,
            List<StockEntry> inboundSummary,
            List<StockEntry> outboundSummary,
            Component feedbackComponent
    ) {
        this.batteryEu = batteryEu;
        this.maxBatteryEu = maxBatteryEu;
        this.activeIndex = activeIndex;
        this.boundVillages = boundVillages;
        this.selectedVillage = selectedVillage;
        this.inboundSummary = inboundSummary;
        this.outboundSummary = outboundSummary;
        this.feedbackComponent = feedbackComponent == null ? Component.empty() : feedbackComponent;
        this.feedback = this.feedbackComponent.getString();
    }

    /** Builds a live remote terminal snapshot with translatable feedback. */
    public static S2CTerminalSyncPacket buildFor(ServerPlayer player, ItemStack terminalStack, Component feedback) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        int eu = CommTerminalItem.getStoredEu(terminalStack);
        List<UUID> ids = CommTerminalItem.getBoundVillages(terminalStack);
        int activeIdx = CommTerminalItem.getActiveIndex(terminalStack);
        if (activeIdx >= ids.size()) {
            activeIdx = Math.max(0, ids.size() - 1);
            CommTerminalItem.setActiveIndex(terminalStack, activeIdx);
        }

        List<BoundVillageDto> briefs = new ArrayList<>();
        for (UUID vid : ids) {
            Optional<VillageRecord> vOpt = data.getVillage(vid);
            if (vOpt.isPresent()) {
                VillageRecord v = vOpt.get();
                briefs.add(new BoundVillageDto(
                        vid, v.getVillageName(), v.getBiomeStyle().id(), v.getLevel(), v.getVillageBalance(),
                        v.isPartnered(), v.getMayorState().isPausedDueToDepopulation()
                ));
            } else {
                briefs.add(new BoundVillageDto(vid, vid.toString().substring(0, 8), "plains", 1, 0L, false, false));
            }
        }

        Component safeFeedback = feedback == null ? Component.empty() : feedback;
        S2CHubSyncPacket selected = null;
        List<StockEntry> inSum = new ArrayList<>();
        List<StockEntry> outSum = new ArrayList<>();
        if (!ids.isEmpty()) {
            UUID selId = ids.get(activeIdx);
            Optional<VillageRecord> vOpt = data.getVillage(selId);
            if (vOpt.isPresent()) {
                VillageRecord v = vOpt.get();
                selected = S2CHubSyncPacket.buildFor(
                        player, v, null, CooperationHubStorage.Zone.INBOUND, 0, "", safeFeedback
                );
                for (Map.Entry<String, Integer> e : v.getStorage().getItemSummary(CooperationHubStorage.Zone.INBOUND).entrySet()) {
                    if (inSum.size() >= 32) {
                        break;
                    }
                    inSum.add(new StockEntry(e.getKey(), e.getValue()));
                }
                for (Map.Entry<String, Integer> e : v.getStorage().getItemSummary(CooperationHubStorage.Zone.OUTBOUND).entrySet()) {
                    if (outSum.size() >= 32) {
                        break;
                    }
                    outSum.add(new StockEntry(e.getKey(), e.getValue()));
                }
            }
        }

        return new S2CTerminalSyncPacket(
                eu,
                TriModePowerMath.TERMINAL_MAX_CAPACITY_EU,
                activeIdx,
                briefs,
                selected,
                inSum,
                outSum,
                safeFeedback
        );
    }

    public static void encode(S2CTerminalSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.batteryEu);
        buf.writeVarInt(pkt.maxBatteryEu);
        buf.writeVarInt(pkt.activeIndex);
        buf.writeVarInt(pkt.boundVillages.size());
        for (BoundVillageDto v : pkt.boundVillages) {
            buf.writeUUID(v.villageId());
            buf.writeUtf(v.name(), 64);
            buf.writeUtf(v.biomeStyleId() == null ? "plains" : v.biomeStyleId(), 32);
            buf.writeVarInt(v.level());
            buf.writeVarLong(v.balance());
            buf.writeBoolean(v.partnered());
            buf.writeBoolean(v.depopulated());
        }
        buf.writeBoolean(pkt.selectedVillage != null);
        if (pkt.selectedVillage != null) {
            S2CHubSyncPacket.encode(pkt.selectedVillage, buf);
        }
        buf.writeVarInt(pkt.inboundSummary.size());
        for (StockEntry e : pkt.inboundSummary) {
            buf.writeUtf(e.itemId(), 96);
            buf.writeVarInt(e.count());
        }
        buf.writeVarInt(pkt.outboundSummary.size());
        for (StockEntry e : pkt.outboundSummary) {
            buf.writeUtf(e.itemId(), 96);
            buf.writeVarInt(e.count());
        }
        buf.writeComponent(pkt.feedbackComponent);
    }

    public static S2CTerminalSyncPacket decode(FriendlyByteBuf buf) {
        int eu = buf.readVarInt();
        int maxEu = buf.readVarInt();
        int activeIdx = buf.readVarInt();
        int vCount = Math.min(32, buf.readVarInt());
        List<BoundVillageDto> villages = new ArrayList<>(vCount);
        for (int i = 0; i < vCount; i++) {
            villages.add(new BoundVillageDto(
                    buf.readUUID(),
                    buf.readUtf(64),
                    buf.readUtf(32),
                    buf.readVarInt(),
                    buf.readVarLong(),
                    buf.readBoolean(),
                    buf.readBoolean()
            ));
        }
        S2CHubSyncPacket sel = buf.readBoolean() ? S2CHubSyncPacket.decode(buf) : null;
        int inCount = Math.min(64, buf.readVarInt());
        List<StockEntry> inSum = new ArrayList<>(inCount);
        for (int i = 0; i < inCount; i++) {
            inSum.add(new StockEntry(buf.readUtf(96), buf.readVarInt()));
        }
        int outCount = Math.min(64, buf.readVarInt());
        List<StockEntry> outSum = new ArrayList<>(outCount);
        for (int i = 0; i < outCount; i++) {
            outSum.add(new StockEntry(buf.readUtf(96), buf.readVarInt()));
        }
        Component fb = buf.readComponent();
        return new S2CTerminalSyncPacket(eu, maxEu, activeIdx, villages, sel, inSum, outSum, fb);
    }

    public static void handle(S2CTerminalSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TyC2ClientHandlers.handleTerminalSync(pkt));
        ctx.setPacketHandled(true);
    }
}
