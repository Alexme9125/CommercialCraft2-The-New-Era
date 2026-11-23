package com.tycooncraft.tyc2.forge.network;

import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.forge.client.TyC2ClientHandlers;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C sync snapshot for {@link com.tycooncraft.tyc2.forge.gui.AtmMachineMenu} carrying
 * structured client-localizable transaction log records and translatable feedback components.
 */
public final class S2CAtmSyncPacket {

    public record CompanyBrief(String id, String name, String inviteCode, long balance, int memberCount) {
    }

    /** Structured bank log entry translated on the client using {@code gui.tyc2.atm.tx.*}. */
    public record TxLogDto(String txTypeKey, long delta, String counterparty) {
        public String formatLocalized() {
            String sign = delta >= 0L ? "+" : "";
            String label = Component.translatable(txTypeKey).getString();
            String trimmed = counterparty == null ? "" : counterparty.trim();
            if (trimmed.isEmpty()
                    || trimmed.toUpperCase(Locale.ROOT).startsWith("ATM")
                    || "DEPOSIT".equalsIgnoreCase(trimmed)
                    || "WITHDRAW".equalsIgnoreCase(trimmed)) {
                return sign + delta + " " + label;
            }
            return sign + delta + " " + label + " " + trimmed;
        }
    }

    public final long personalBalance;
    public final boolean companyMode;
    public final String activeCompanyId;
    public final String activeCompanyName;
    public final String activeCompanyInvite;
    public final int activeCompanyMembers;
    public final long activeCompanyBalance;
    public final List<CompanyBrief> companies;
    public final List<TxLogDto> txLogs;
    public final List<String> logs;
    public final Component feedbackComponent;
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
            List<TxLogDto> txLogs,
            Component feedbackComponent
    ) {
        this.personalBalance = personalBalance;
        this.companyMode = companyMode;
        this.activeCompanyId = activeCompanyId == null ? "" : activeCompanyId;
        this.activeCompanyName = activeCompanyName == null ? "" : activeCompanyName;
        this.activeCompanyInvite = activeCompanyInvite == null ? "" : activeCompanyInvite;
        this.activeCompanyMembers = activeCompanyMembers;
        this.activeCompanyBalance = activeCompanyBalance;
        this.companies = companies;
        this.txLogs = txLogs;
        List<String> formatted = new ArrayList<>(txLogs.size());
        for (TxLogDto entry : txLogs) {
            formatted.add(entry.formatLocalized());
        }
        this.logs = formatted;
        this.feedbackComponent = feedbackComponent == null ? Component.empty() : feedbackComponent;
        this.feedback = this.feedbackComponent.getString();
    }

    /** Builds a fresh sync snapshot for {@code player} with translatable {@code feedback}. */
    public static S2CAtmSyncPacket buildFor(ServerPlayer player, Component feedback) {
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
        List<TxLogDto> structuredLogs = new ArrayList<>(rawLogs.size());
        for (int i = rawLogs.size() - 1; i >= 0; i--) {
            BankAccountManager.BankTransactionLog log = rawLogs.get(i);
            String cp = log.counterparty() == null ? "" : log.counterparty();
            structuredLogs.add(new TxLogDto(txTypeKey(log.type()), log.delta(), cp));
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
                structuredLogs,
                feedback == null ? Component.empty() : feedback
        );
    }

    /** Convenience overload accepting plain string feedback. */
    public static S2CAtmSyncPacket buildFor(ServerPlayer player, String feedback) {
        return buildFor(player, feedback == null || feedback.isEmpty() ? Component.empty() : Component.literal(feedback));
    }

    private static String txTypeKey(BankAccountManager.TransactionType type) {
        if (type == null) {
            return "gui.tyc2.atm.tx.deposit";
        }
        return switch (type) {
            case DEPOSIT, SYSTEM_ADJUST -> "gui.tyc2.atm.tx.deposit";
            case WITHDRAW -> "gui.tyc2.atm.tx.withdraw";
            case TRANSFER_IN, COMPANY_DISSOLVE -> "gui.tyc2.atm.tx.transfer_in";
            case TRANSFER_OUT -> "gui.tyc2.atm.tx.transfer_out";
            case TRADE_BUY, NEGOTIATION -> "gui.tyc2.atm.tx.trade_buy";
            case TRADE_SELL -> "gui.tyc2.atm.tx.trade_sell";
        };
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

        buf.writeVarInt(pkt.txLogs.size());
        for (TxLogDto entry : pkt.txLogs) {
            buf.writeUtf(entry.txTypeKey(), 64);
            buf.writeVarLong(entry.delta());
            buf.writeUtf(entry.counterparty(), 64);
        }
        buf.writeComponent(pkt.feedbackComponent);
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
        List<TxLogDto> txLogs = new ArrayList<>(lCount);
        for (int i = 0; i < lCount; i++) {
            txLogs.add(new TxLogDto(buf.readUtf(64), buf.readVarLong(), buf.readUtf(64)));
        }
        Component fbComp = buf.readComponent();
        return new S2CAtmSyncPacket(pBal, coMode, coId, coName, coInv, coMem, coBal, companies, txLogs, fbComp);
    }

    public static void handle(S2CAtmSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TyC2ClientHandlers.handleAtmSync(pkt));
        ctx.setPacketHandled(true);
    }
}
