package com.tycooncraft.tyc2.forge.network;

import com.tycooncraft.tyc2.forge.client.TyC2ClientHandlers;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.VillageRecord;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C snapshot for {@link com.tycooncraft.tyc2.forge.gui.MayorNegotiationMenu}.
 */
public final class S2CMayorSyncPacket {

    public final UUID villageId;
    public final String villageName;
    public final String biomeStyleId;
    public final int population;
    public final long cashIc;
    public final long bankIc;
    public final long reqIc;
    public final int haveLogs;
    public final int reqLogs;
    public final int haveIron;
    public final int reqIron;
    public final int haveFood;
    public final int reqFood;
    public final boolean partnered;
    public final String feedback;

    public S2CMayorSyncPacket(
            UUID villageId,
            String villageName,
            String biomeStyleId,
            int population,
            long cashIc,
            long bankIc,
            long reqIc,
            int haveLogs,
            int reqLogs,
            int haveIron,
            int reqIron,
            int haveFood,
            int reqFood,
            boolean partnered,
            String feedback
    ) {
        this.villageId = villageId;
        this.villageName = villageName == null ? "" : villageName;
        this.biomeStyleId = biomeStyleId == null ? "plains" : biomeStyleId;
        this.population = population;
        this.cashIc = cashIc;
        this.bankIc = bankIc;
        this.reqIc = reqIc;
        this.haveLogs = haveLogs;
        this.reqLogs = reqLogs;
        this.haveIron = haveIron;
        this.reqIron = reqIron;
        this.haveFood = haveFood;
        this.reqFood = reqFood;
        this.partnered = partnered;
        this.feedback = feedback == null ? "" : feedback;
    }

    public boolean currencyMet() {
        return cashIc + bankIc >= reqIc;
    }

    public boolean logsMet() {
        return haveLogs >= reqLogs;
    }

    public boolean ironMet() {
        return haveIron >= reqIron;
    }

    public boolean foodMet() {
        return haveFood >= reqFood;
    }

    public boolean allMet() {
        return currencyMet() && logsMet() && ironMet() && foodMet();
    }

    public static S2CMayorSyncPacket buildFor(ServerPlayer player, UUID villageId, String feedback) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        TycoonForgeWorldManager.NegotiationStatus st = TycoonForgeWorldManager.evaluateNegotiation(player, data);
        Optional<VillageRecord> vOpt = data.getVillage(villageId);
        String name = vOpt.map(VillageRecord::getVillageName).orElse("Village");
        String biome = vOpt.map(v -> v.getBiomeStyle().id()).orElse("plains");
        int pop = vOpt.map(VillageRecord::getPopulation).orElse(2);
        boolean partnered = vOpt.map(VillageRecord::isPartnered).orElse(false);
        return new S2CMayorSyncPacket(
                villageId, name, biome, pop,
                st.cashIc(), st.bankIc(), st.requiredIc(),
                st.haveLogs(), st.requiredLogs(),
                st.haveIron(), st.requiredIron(),
                st.haveFood(), st.requiredFood(),
                partnered, feedback
        );
    }

    public static void encode(S2CMayorSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.villageId);
        buf.writeUtf(pkt.villageName, 64);
        buf.writeUtf(pkt.biomeStyleId, 32);
        buf.writeVarInt(pkt.population);
        buf.writeVarLong(pkt.cashIc);
        buf.writeVarLong(pkt.bankIc);
        buf.writeVarLong(pkt.reqIc);
        buf.writeVarInt(pkt.haveLogs);
        buf.writeVarInt(pkt.reqLogs);
        buf.writeVarInt(pkt.haveIron);
        buf.writeVarInt(pkt.reqIron);
        buf.writeVarInt(pkt.haveFood);
        buf.writeVarInt(pkt.reqFood);
        buf.writeBoolean(pkt.partnered);
        buf.writeUtf(pkt.feedback, 192);
    }

    public static S2CMayorSyncPacket decode(FriendlyByteBuf buf) {
        return new S2CMayorSyncPacket(
                buf.readUUID(),
                buf.readUtf(64),
                buf.readUtf(32),
                buf.readVarInt(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readUtf(192)
        );
    }

    public static void handle(S2CMayorSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TyC2ClientHandlers.handleMayorSync(pkt));
        ctx.setPacketHandled(true);
    }
}
