package com.tycooncraft.tyc2.forge.network;

import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.forge.client.TyC2ClientHandlers;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C sync snapshot for {@link com.tycooncraft.tyc2.forge.gui.AtmMachineMenu}.
 */
public final class S2CAtmSyncPacket {

    public record CompanyBrief(String id, String name, String inviteCode, long balance, int memberCount) {
    }

    public final long personalBalance;
    public final boolean companyMode;
    public final String activeCompanyId;
    public final String activeCompanyName;
    public final String activeCompanyInvite;
    public final int activeCompanyMembers;
    public final long activeCompanyBalance;
    public final List<CompanyBrief> companies;
    public final List<String> logs;
    public final String feedback;

    public S2CAtmSyncPacket(
            long personalBalance,
            boolean companyMode,
            String activeCompanyId,
            String activeCompanyName,
            String activeCompanyInvite,
            int activeCompanyMembers,
            long activeCompanyBalance,
            List<CompanyBrief> companies,
            List<String> logs,
            String feedback
    ) {
        this.personalBalance = personalBalance;
        this.companyMode = companyMode;
        this.activeCompanyId = activeCompanyId == null ? "" : activeCompanyId;
        this.activeCompanyName = activeCompanyName == null ? "" : activeCompanyName;
        this.activeCompanyInvite = activeCompanyInvite == null ? "" : activeCompanyInvite;
        this.activeCompanyMembers = activeCompanyMembers;
        this.activeCompanyBalance = activeCompanyBalance;
        this.companies = companies;
        this.logs = logs;
        this.feedback = feedback == null ? "" : feedback;
    }

    /** Builds a fresh sync snapshot for {@code player}. */
    public static S2CAtmSyncPacket buildFor(ServerPlayer player, String feedback) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        BankAccountManager bank = data.bank();
        BankAccountManager.PersonalAccount personal = bank.getOrCreatePersonal(
                player.getUUID(), player.getGameProfile().getName()
        );
        BankAccountManager.AccountRef activeRef = bank.resolveSettlementAccount(player.getUUID());
        boolean isCompany = activeRef.scope() == BankAccountManager.AccountScope.COMPANY;
        String coId = "";
        String coName = "";
        String coInvite = "";
        int coMembers = 0;
        long coBal = 0L;
        if (isCompany) {
            Optional<BankAccountManager.CompanyAccount> coOpt = bank.getCompany(activeRef.accountId());
            if (coOpt.isPresent()) {
                BankAccountManager.CompanyAccount co = coOpt.get();
                coId = co.companyId();
                coName = co.name();
                coInvite = co.inviteCode();
                coMembers = co.memberUuids().size();
                coBal = co.balance();
            } else {
                isCompany = false;
            }
        }

        List<CompanyBrief> companyList = new ArrayList<>();
        for (BankAccountManager.CompanyAccount co : bank.getCompaniesForPlayer(player.getUUID())) {
            companyList.add(new CompanyBrief(
                    co.companyId(), co.name(), co.inviteCode(), co.balance(), co.memberUuids().size()
            ));
        }

        List<BankAccountManager.BankTransactionLog> rawLogs = bank.getTransactionLogs(activeRef);
        List<String> formattedLogs = new ArrayList<>(rawLogs.size());
        for (int i = rawLogs.size() - 1; i >= 0; i--) {
            BankAccountManager.BankTransactionLog log = rawLogs.get(i);
            String sign = log.delta() >= 0 ? "+" : "";
            String cp = log.counterparty().isEmpty() ? log.type().name() : log.counterparty();
            formattedLogs.add(sign + log.delta() + " " + cp);
        }

        return new S2CAtmSyncPacket(
                personal.balance(),
                isCompany,
                coId,
                coName,
                coInvite,
                coMembers,
                coBal,
                companyList,
                formattedLogs,
                feedback
        );
    }

    public long activeBalance() {
        return companyMode ? activeCompanyBalance : personalBalance;
    }

    public static void encode(S2CAtmSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarLong(pkt.personalBalance);
        buf.writeBoolean(pkt.companyMode);
        buf.writeUtf(pkt.activeCompanyId, 64);
        buf.writeUtf(pkt.activeCompanyName, 64);
        buf.writeUtf(pkt.activeCompanyInvite, 32);
        buf.writeVarInt(pkt.activeCompanyMembers);
        buf.writeVarLong(pkt.activeCompanyBalance);

        buf.writeVarInt(pkt.companies.size());
        for (CompanyBrief c : pkt.companies) {
            buf.writeUtf(c.id(), 64);
            buf.writeUtf(c.name(), 64);
            buf.writeUtf(c.inviteCode(), 32);
            buf.writeVarLong(c.balance());
            buf.writeVarInt(c.memberCount());
        }

        buf.writeVarInt(pkt.logs.size());
        for (String line : pkt.logs) {
            buf.writeUtf(line, 128);
        }
        buf.writeUtf(pkt.feedback, 192);
    }

    public static S2CAtmSyncPacket decode(FriendlyByteBuf buf) {
        long pBal = buf.readVarLong();
        boolean coMode = buf.readBoolean();
        String coId = buf.readUtf(64);
        String coName = buf.readUtf(64);
        String coInv = buf.readUtf(32);
        int coMem = buf.readVarInt();
        long coBal = buf.readVarLong();

        int cCount = Math.min(32, buf.readVarInt());
        List<CompanyBrief> companies = new ArrayList<>(cCount);
        for (int i = 0; i < cCount; i++) {
            companies.add(new CompanyBrief(
                    buf.readUtf(64),
                    buf.readUtf(64),
                    buf.readUtf(32),
                    buf.readVarLong(),
                    buf.readVarInt()
            ));
        }

        int lCount = Math.min(64, buf.readVarInt());
        List<String> logs = new ArrayList<>(lCount);
        for (int i = 0; i < lCount; i++) {
            logs.add(buf.readUtf(128));
        }
        String fb = buf.readUtf(192);
        return new S2CAtmSyncPacket(pBal, coMode, coId, coName, coInv, coMem, coBal, companies, logs, fb);
    }

    public static void handle(S2CAtmSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TyC2ClientHandlers.handleAtmSync(pkt));
        ctx.setPacketHandled(true);
    }
}
