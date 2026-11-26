package com.tycooncraft.tyc2.forge.network;

import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * C2S banking action packet sent from {@link com.tycooncraft.tyc2.forge.client.AtmMachineScreen}.
 */
public final class C2SAtmActionPacket {

    public enum Action {
        REQUEST_SYNC,
        DEPOSIT_SLOT,
        DEPOSIT_ALL,
        WITHDRAW_AMOUNT,
        WITHDRAW_DENOM,
        CREATE_COMPANY,
        JOIN_COMPANY,
        LEAVE_COMPANY,
        SWITCH_TO_PERSONAL,
        SWITCH_TO_COMPANY,
        TRANSFER
    }

    public final BlockPos atmPos;
    public final Action action;
    public final long amount;
    public final int denomIndex;
    public final String textA;
    public final String textB;

    public C2SAtmActionPacket(
            BlockPos atmPos, Action action, long amount, int denomIndex, String textA, String textB
    ) {
        this.atmPos = atmPos;
        this.action = action;
        this.amount = amount;
        this.denomIndex = denomIndex;
        this.textA = textA == null ? "" : textA;
        this.textB = textB == null ? "" : textB;
    }

    public static C2SAtmActionPacket simple(BlockPos pos, Action action) {
        return new C2SAtmActionPacket(pos, action, 0L, 0, "", "");
    }

    public static void encode(C2SAtmActionPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.atmPos);
        buf.writeEnum(pkt.action);
        buf.writeVarLong(pkt.amount);
        buf.writeVarInt(pkt.denomIndex);
        buf.writeUtf(pkt.textA, 64);
        buf.writeUtf(pkt.textB, 64);
    }

    public static C2SAtmActionPacket decode(FriendlyByteBuf buf) {
        return new C2SAtmActionPacket(
                buf.readBlockPos(),
                buf.readEnum(Action.class),
                buf.readVarLong(),
                buf.readVarInt(),
                buf.readUtf(64),
                buf.readUtf(64)
        );
    }

    public static void handle(C2SAtmActionPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            ctx.setPacketHandled(true);
            return;
        }
        if (!player.level.isLoaded(pkt.atmPos)
                || !(player.level.getBlockEntity(pkt.atmPos) instanceof AtmMachineBlockEntity atm)
                || !atm.stillValid(player)) {
            ctx.setPacketHandled(true);
            return;
        }

        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        BankAccountManager bank = data.bank();
        bank.getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
        long tick = player.getServer().overworld().getGameTime();
        Component feedback = Component.empty();

        switch (pkt.action) {
            case REQUEST_SYNC -> feedback = Component.empty();
            case DEPOSIT_SLOT -> feedback = atm.handleDepositSlot(player);
            case DEPOSIT_ALL -> feedback = atm.handleDepositAll(player);
            case WITHDRAW_AMOUNT -> feedback = atm.handleWithdrawAmount(player, pkt.amount);
            case WITHDRAW_DENOM -> {
                CurrencyDenomination[] values = CurrencyDenomination.values();
                int idx = Math.max(0, Math.min(values.length - 1, pkt.denomIndex));
                int count = (int) Math.max(1L, Math.min(64L, pkt.amount <= 0 ? 1 : pkt.amount));
                feedback = atm.handleWithdrawDenom(player, values[idx], count);
            }
            case CREATE_COMPANY -> {
                String name = pkt.textA.trim();
                if (name.length() > BankAccountManager.MAX_COMPANY_NAME_LENGTH) {
                    name = name.substring(0, BankAccountManager.MAX_COMPANY_NAME_LENGTH).trim();
                }
                if (name.isEmpty()) {
                    feedback = Component.translatable("status.tyc2.atm.invalid_amount");
                } else if (!bank.canJoinAnotherCompany(player.getUUID())) {
                    feedback = Component.translatable("status.tyc2.atm.company_limit");
                } else {
                    try {
                        BankAccountManager.CompanyAccount co = bank.createCompany(
                                player.getUUID(), player.getGameProfile().getName(), name
                        );
                        bank.setActiveCompany(player.getUUID(), co.companyId());
                        data.setDirty();
                        feedback = Component.translatable(
                                "status.tyc2.atm.company_created", co.name(), co.inviteCode()
                        );
                    } catch (IllegalStateException ex) {
                        feedback = Component.translatable("status.tyc2.atm.company_limit");
                    }
                }
            }
            case JOIN_COMPANY -> {
                BankAccountManager.BankResult res = bank.joinCompanyByInviteCode(
                        player.getUUID(), player.getGameProfile().getName(), pkt.textA
                );
                if (res.success() && res.accountRef() != null) {
                    bank.setActiveCompany(player.getUUID(), res.accountRef().accountId());
                    data.setDirty();
                    String coName = bank.getCompany(res.accountRef().accountId())
                            .map(BankAccountManager.CompanyAccount::name).orElse("");
                    feedback = Component.translatable("status.tyc2.atm.company_joined", coName);
                } else {
                    feedback = Component.translatable(res.errorKey());
                }
            }
            case LEAVE_COMPANY -> {
                String coId = pkt.textA.isBlank()
                        ? bank.resolveSettlementAccount(player.getUUID()).accountId()
                        : pkt.textA;
                String coName = bank.getCompany(coId).map(BankAccountManager.CompanyAccount::name).orElse(coId);
                BankAccountManager.BankResult res = bank.leaveCompany(player.getUUID(), coId, tick);
                if (res.success()) {
                    data.syncMacroAccountTotals();
                    data.setDirty();
                    feedback = Component.translatable("status.tyc2.atm.company_left", coName);
                } else {
                    feedback = Component.translatable(res.errorKey());
                }
            }
            case SWITCH_TO_PERSONAL -> {
                bank.setActiveCompany(player.getUUID(), null);
                data.setDirty();
            }
            case SWITCH_TO_COMPANY -> {
                String targetCo = pkt.textA;
                if (targetCo.isBlank()) {
                    var list = bank.getCompaniesForPlayer(player.getUUID());
                    if (!list.isEmpty()) {
                        targetCo = list.get(0).companyId();
                    }
                }
                if (!targetCo.isBlank()) {
                    BankAccountManager.BankResult res = bank.setActiveCompany(player.getUUID(), targetCo);
                    if (res.success()) {
                        data.setDirty();
                    } else {
                        feedback = Component.translatable(res.errorKey());
                    }
                }
            }
            case TRANSFER -> {
                String recipient = pkt.textA.trim();
                long amt = pkt.amount;
                if (recipient.isEmpty() || amt <= 0L) {
                    feedback = Component.translatable("status.tyc2.atm.invalid_amount");
                } else {
                    BankAccountManager.BankResult res = null;
                    // 1. Try company ID or exact company name
                    Optional<BankAccountManager.CompanyAccount> targetCo = bank.getCompany(recipient);
                    if (targetCo.isEmpty()) {
                        for (BankAccountManager.CompanyAccount c : bank.allCompanyAccounts().values()) {
                            if (c.name().equalsIgnoreCase(recipient)) {
                                targetCo = Optional.of(c);
                                break;
                            }
                        }
                    }
                    if (targetCo.isPresent()) {
                        res = bank.transferToCompany(player.getUUID(), targetCo.get().companyId(), amt, tick, "");
                    } else {
                        // 2. Try online or offline player name
                        ServerPlayer online = player.getServer().getPlayerList().getPlayerByName(recipient);
                        if (online != null) {
                            bank.getOrCreatePersonal(online.getUUID(), online.getGameProfile().getName());
                            res = bank.transferToPlayer(player.getUUID(), online.getUUID(), amt, tick, "");
                        } else {
                            Optional<BankAccountManager.PersonalAccount> offline = bank.findPersonalByName(recipient);
                            if (offline.isPresent()) {
                                res = bank.transferToPlayer(player.getUUID(), offline.get().playerUuid(), amt, tick, "");
                            }
                        }
                    }
                    if (res == null) {
                        feedback = Component.translatable("status.tyc2.atm.invalid_recipient");
                    } else if (res.success()) {
                        data.setDirty();
                        feedback = Component.translatable("status.tyc2.atm.transferred", amt, recipient);
                    } else {
                        feedback = Component.translatable(res.errorKey());
                    }
                }
            }
        }

        TyC2ForgeNetwork.sendToPlayer(player, S2CAtmSyncPacket.buildFor(player, feedback));
        ctx.setPacketHandled(true);
    }
}
