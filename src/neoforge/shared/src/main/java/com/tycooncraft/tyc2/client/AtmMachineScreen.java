package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.menu.AtmMachineMenu;
import com.tycooncraft.tyc2.network.TyC2ActionPayload;
import com.tycooncraft.tyc2.platform.TyC2Native;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pixel-exact GUI screen for the Industrial ATM ({@code gui/atm_machine.png}, 220x214).
 * Supports Personal & Guild/Company accounts, deposit/withdraw/exact denomination exchange,
 * company creation/joining/leaving/switching, transfers, 32-entry ledger view, and terminal charging.
 */
public final class AtmMachineScreen extends TyC2ScreenBase<AtmMachineMenu> {

    private static final String GUI_TEX = "atm_machine";
    private static final CurrencyDenomination[] DENOMS = CurrencyDenomination.values();

    private int rightTab = 0; // 0=Log, 1=Company, 2=Transfer
    private int selectedDenomIdx = 5; // default X100
    private int logScroll = 0;

    private EditBox amountInput;
    private EditBox secondaryInput; // company name / invite code / transfer recipient
    private Button denomWithdrawBtn;

    public AtmMachineScreen(AtmMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 220, 214);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 29;
        this.inventoryLabelY = 121;
    }

    @Override
    protected void init() {
        super.init();
        rebuildControls();
        sendAction("REQUEST_SYNC", Map.of());
    }

    private void rebuildControls() {
        clearWidgets();
        clearEditBoxes();

        // Top LCD quick switch Personal/Company button (162, 22, 46x12)
        addRenderableWidget(Button.builder(
                        Component.literal("Acct"),
                        b -> cycleActiveAccount()
                ).bounds(leftPos + 162, topPos + 21, 46, 12).build());

        // Left Controls box (35, 56, 72x68):
        // Row 1: Amount input (37, 58, 68x12)
        amountInput = addTrackedEditBox(new EditBox(font, leftPos + 37, topPos + 58, 68, 12, Component.literal("IC")));
        amountInput.setMaxLength(10);
        amountInput.setValue("100");
        addRenderableWidget(amountInput);

        // Row 2: Deposit (37, 72, 44x12) & All (83, 72, 22x12) — fits full labels without clipping
        addRenderableWidget(Button.builder(
                Component.translatable("gui.tyc2.atm.deposit"),
                b -> sendAction("DEPOSIT_SLOT", Map.of())
        ).bounds(leftPos + 37, topPos + 72, 44, 12).build());

        addRenderableWidget(Button.builder(
                Component.literal("All"),
                b -> sendAction("DEPOSIT_ALL", Map.of())
        ).bounds(leftPos + 83, topPos + 72, 22, 12).build());

        // Row 3: Withdraw Amount (37, 86, 68x12)
        addRenderableWidget(Button.builder(
                Component.translatable("gui.tyc2.atm.withdraw"),
                b -> {
                    long amt = parsePositiveLong(amountInput.getValue(), 0L);
                    if (amt > 0L) {
                        sendAction("WITHDRAW_AMOUNT", Map.of("amount", amt));
                    }
                }
        ).bounds(leftPos + 37, topPos + 86, 68, 12).build());

        // Row 4: Cycle Denom (<) + Withdraw Denom (e.g. "100x")
        addRenderableWidget(Button.builder(
                Component.literal("<"),
                b -> {
                    selectedDenomIdx = (selectedDenomIdx - 1 + DENOMS.length) % DENOMS.length;
                    updateDenomButtonLabel();
                }
        ).bounds(leftPos + 37, topPos + 100, 12, 12).build());

        denomWithdrawBtn = addRenderableWidget(Button.builder(
                Component.literal(DENOMS[selectedDenomIdx].value() + "x"),
                b -> {
                    int cnt = (int) Math.max(1L, Math.min(64L, parsePositiveLong(amountInput.getValue(), 1L)));
                    sendAction("WITHDRAW_DENOM", Map.of(
                            "denom", DENOMS[selectedDenomIdx].name(),
                            "count", cnt
                    ));
                }
        ).bounds(leftPos + 50, topPos + 100, 42, 12).build());

        addRenderableWidget(Button.builder(
                Component.literal(">"),
                b -> {
                    selectedDenomIdx = (selectedDenomIdx + 1) % DENOMS.length;
                    updateDenomButtonLabel();
                }
        ).bounds(leftPos + 93, topPos + 100, 12, 12).build());

        // Right LCD tabs (111, 56, 75x68) -> top strip 3 mini tabs (112..185) without clipping
        addRenderableWidget(Button.builder(Component.literal("Log"), b -> {
            rightTab = 0;
            rebuildControls();
        }).bounds(leftPos + 112, topPos + 57, 22, 11).build());

        addRenderableWidget(Button.builder(Component.literal("Co"), b -> {
            rightTab = 1;
            rebuildControls();
        }).bounds(leftPos + 135, topPos + 57, 20, 11).build());

        addRenderableWidget(Button.builder(Component.literal("Send"), b -> {
            rightTab = 2;
            rebuildControls();
        }).bounds(leftPos + 156, topPos + 57, 29, 11).build());

        if (rightTab == 0) {
            // Log scroll buttons at bottom of Right LCD
            addRenderableWidget(Button.builder(Component.literal("^"), b -> logScroll = Math.max(0, logScroll - 4))
                    .bounds(leftPos + 114, topPos + 111, 16, 11).build());
            addRenderableWidget(Button.builder(Component.literal("v"), b -> logScroll += 4)
                    .bounds(leftPos + 132, topPos + 111, 16, 11).build());
        } else if (rightTab == 1) {
            secondaryInput = addTrackedEditBox(new EditBox(font, leftPos + 113, topPos + 70, 71, 12, Component.literal("Co")));
            secondaryInput.setMaxLength(24);
            addRenderableWidget(secondaryInput);

            addRenderableWidget(Button.builder(Component.literal("New"), b -> {
                String txt = secondaryInput.getValue().trim();
                if (!txt.isEmpty()) {
                    sendAction("CREATE_COMPANY", Map.of("name", txt));
                }
            }).bounds(leftPos + 113, topPos + 84, 34, 12).build());

            addRenderableWidget(Button.builder(Component.literal("Join"), b -> {
                String txt = secondaryInput.getValue().trim();
                if (!txt.isEmpty()) {
                    sendAction("JOIN_COMPANY", Map.of("code", txt));
                }
            }).bounds(leftPos + 150, topPos + 84, 34, 12).build());

            addRenderableWidget(Button.builder(Component.literal("Leave"), b -> {
                sendAction("LEAVE_COMPANY", Map.of());
            }).bounds(leftPos + 113, topPos + 98, 71, 12).build());
        } else if (rightTab == 2) {
            secondaryInput = addTrackedEditBox(new EditBox(font, leftPos + 113, topPos + 72, 71, 12, Component.literal("To")));
            secondaryInput.setMaxLength(32);
            addRenderableWidget(secondaryInput);

            addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.atm.transfer"), b -> {
                String rec = secondaryInput.getValue().trim();
                long amt = parsePositiveLong(amountInput.getValue(), 0L);
                if (!rec.isEmpty() && amt > 0L) {
                    sendAction("TRANSFER", Map.of("recipient", rec, "amount", amt));
                }
            }).bounds(leftPos + 113, topPos + 88, 71, 14).build());
        }
    }

    private void updateDenomButtonLabel() {
        if (denomWithdrawBtn != null) {
            denomWithdrawBtn.setMessage(Component.literal(DENOMS[selectedDenomIdx].value() + "x"));
        }
    }

    private void cycleActiveAccount() {
        Map<String, Object> snap = menu.snapshot();
        String activeCo = JsonLite.getString(snap, "activeCompanyId", "");
        List<Object> cos = JsonLite.asArray(snap.get("companies"));
        if (cos == null || cos.isEmpty()) {
            sendAction("SWITCH_ACCOUNT", Map.of("companyId", ""));
            return;
        }
        if (activeCo.isEmpty()) {
            Map<String, Object> first = JsonLite.asObject(cos.get(0));
            if (first != null) {
                sendAction("SWITCH_ACCOUNT", Map.of("companyId", JsonLite.getString(first, "id", "")));
            }
            return;
        }
        for (int i = 0; i < cos.size(); i++) {
            Map<String, Object> cm = JsonLite.asObject(cos.get(i));
            if (cm != null && activeCo.equals(JsonLite.getString(cm, "id", ""))) {
                if (i + 1 < cos.size()) {
                    Map<String, Object> next = JsonLite.asObject(cos.get(i + 1));
                    sendAction("SWITCH_ACCOUNT", Map.of("companyId", JsonLite.getString(next, "id", "")));
                } else {
                    sendAction("SWITCH_ACCOUNT", Map.of("companyId", ""));
                }
                return;
            }
        }
        sendAction("SWITCH_ACCOUNT", Map.of("companyId", ""));
    }

    @Override
    protected void paintPanel(ScreenCanvas canvas, int mouseX, int mouseY) {
        canvas.blit(GUI_TEX, 0, 0, 0, 0, imageWidth, imageHeight);

        Map<String, Object> snap = menu.snapshot();
        String scope = JsonLite.getString(snap, "activeScope", "PERSONAL");
        String label = JsonLite.getString(snap, "activeLabel", "Personal");
        long balance = JsonLite.getLong(snap, "activeBalance", 0L);

        // Top LCD badge: Personal (224,32) or Company (224,52)
        boolean isCompany = "COMPANY".equals(scope);
        canvas.blit(GUI_TEX, 12, 26, 224, isCompany ? 52 : 32, 16, 16);

        canvas.drawClippedString(label, 32, 22, 126, 0xFF7BE8FF);
        canvas.drawClippedString(
                Component.translatable("gui.tyc2.atm.balance", balance).getString(),
                32, 34, 126, 0xFFFFE066
        );

        // Show company invite code in top LCD right corner if active
        if (isCompany) {
            String activeCo = JsonLite.getString(snap, "activeCompanyId", "");
            List<Object> cos = JsonLite.asArray(snap.get("companies"));
            if (cos != null) {
                for (Object obj : cos) {
                    Map<String, Object> cm = JsonLite.asObject(obj);
                    if (cm != null && activeCo.equals(JsonLite.getString(cm, "id", ""))) {
                        String code = JsonLite.getString(cm, "inviteCode", "");
                        canvas.drawClippedString(code, 160, 36, 50, 0xFF9AF5B0);
                        break;
                    }
                }
            }
        }

        // Mini EU bar (196, 56, 14x30) -> inner (197, 57, 12x28) from UV (224, 0, 12x28)
        int eu = menu.storedEu();
        int maxEu = menu.maxEu();
        if (eu > 0 && maxEu > 0) {
            int filledH = Math.max(1, Math.min(28, (int) Math.round(28.0 * eu / (double) maxEu)));
            int off = 28 - filledH;
            canvas.blit(GUI_TEX, 197, 57 + off, 224, off, 12, filledH);
        }

        // Right LCD content
        if (rightTab == 0) {
            List<Object> logs = JsonLite.asArray(snap.get("logs"));
            if (logs == null || logs.isEmpty()) {
                canvas.drawClippedString(
                        Component.translatable("gui.tyc2.atm.no_history").getString(),
                        114, 72, 69, 0xFF7B8E9F
                );
            } else {
                int clamped = Math.min(logScroll, Math.max(0, logs.size() - 4));
                logScroll = clamped;
                int y = 70;
                for (int i = clamped; i < Math.min(logs.size(), clamped + 4); i++) {
                    Map<String, Object> lm = JsonLite.asObject(logs.get(i));
                    if (lm == null) {
                        continue;
                    }
                    long delta = JsonLite.getLong(lm, "delta", 0L);
                    String cp = JsonLite.getString(lm, "counterparty", "");
                    String line = (delta >= 0 ? "+" : "") + delta + " " + cp;
                    canvas.drawClippedString(line, 114, y, 70, delta >= 0 ? 0xFF5CE88A : 0xFFFF7A70);
                    y += 10;
                }
            }
        } else if (rightTab == 1) {
            List<Object> cos = JsonLite.asArray(snap.get("companies"));
            int count = cos == null ? 0 : cos.size();
            canvas.drawClippedString("Cos: " + count, 114, 112, 70, 0xFFB0C4DE);
        } else if (rightTab == 2) {
            canvas.drawClippedString("Uses Amt box", 114, 106, 70, 0xFF8AA4BF);
        }
    }

    @Override
    protected void paintOverlayTooltips(ScreenCanvas canvas, int mouseX, int mouseY) {
        int px = mouseX - leftPos;
        int py = mouseY - topPos;
        if (px >= 196 && px < 210 && py >= 56 && py < 86) {
            int eu = menu.storedEu();
            int maxEu = menu.maxEu();
            canvas.drawTooltip(
                    List.of(Component.translatable("gui.tyc2.energy.eu_fe", eu, maxEu, TriModePowerMath.euToFe(eu))),
                    mouseX,
                    mouseY
            );
        }
    }

    private void sendAction(String action, Map<String, Object> args) {
        String json = args.isEmpty() ? "{}" : JsonLite.toJson(new LinkedHashMap<>(args));
        TyC2Native.sendToServer(new TyC2ActionPayload("ATM", menu.blockPos(), action, json));
    }

    private static long parsePositiveLong(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            long val = Long.parseLong(raw.trim());
            return val > 0L ? val : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
