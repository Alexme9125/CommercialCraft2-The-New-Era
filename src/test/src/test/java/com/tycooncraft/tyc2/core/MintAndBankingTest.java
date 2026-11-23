package com.tycooncraft.tyc2.core;

import com.tycooncraft.tyc2.api.TyC2RegistryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MintAndBankingTest {

    @AfterEach
    void cleanupApi() {
        TyC2RegistryApi.reset();
    }

    @Test
    void mintRecipesSupportAll9DenominationsStandaloneAndIc2() {
        MintRecipeRegistry registry = MintRecipeRegistry.createDefault();

        // Pure Vanilla mode (no IC2 loaded)
        var vanillaActive = registry.activeRecipes(mod -> false);
        for (CurrencyDenomination denom : CurrencyDenomination.values()) {
            assertTrue(vanillaActive.stream().anyMatch(r -> r.outputDenomination() == denom),
                    "Pure Vanilla must be able to mint denomination " + denom);
        }

        // Single-slot iron -> 5x (1 coin)
        var iron5 = registry.findMatch("minecraft:iron_ingot", 64, null, 0, null, mod -> false);
        assertTrue(iron5.isPresent());
        assertEquals(CurrencyDenomination.X5, iron5.get().recipe().outputDenomination());
        assertEquals(1, iron5.get().consumeSlot0());
        assertEquals(0, iron5.get().consumeSlot1());

        // Dual-slot iron + iron -> 10x (1 coin)
        var iron10 = registry.findMatch("minecraft:iron_ingot", 16, "minecraft:iron_ingot", 16, null, mod -> false);
        assertTrue(iron10.isPresent());
        assertEquals(CurrencyDenomination.X10, iron10.get().recipe().outputDenomination());
        assertEquals(1, iron10.get().consumeSlot0());
        assertEquals(1, iron10.get().consumeSlot1());

        // Order-independent dual-slot: Emerald in slot 0, 2x Gold in slot 1 -> 100x
        var goldEmerald = registry.findMatch("minecraft:emerald", 1, "minecraft:gold_ingot", 2, null, mod -> false);
        assertTrue(goldEmerald.isPresent());
        assertEquals(CurrencyDenomination.X100, goldEmerald.get().recipe().outputDenomination());
        assertEquals(1, goldEmerald.get().consumeSlot0());
        assertEquals(2, goldEmerald.get().consumeSlot1());

        // Single Netherite Ingot -> 2x 500x = 1000 IC; Netherite + Diamond -> 1x 1000x
        var neth500 = registry.findMatch("minecraft:netherite_ingot", 1, null, 0, null, mod -> false);
        assertTrue(neth500.isPresent());
        assertEquals(CurrencyDenomination.X500, neth500.get().recipe().outputDenomination());
        assertEquals(2, neth500.get().recipe().outputCount());
        assertEquals(1000L, neth500.get().recipe().totalFaceValue());

        var neth1000 = registry.findMatch("minecraft:netherite_ingot", 1, "minecraft:diamond", 1, null, mod -> false);
        assertTrue(neth1000.isPresent());
        assertEquals(CurrencyDenomination.X1000, neth1000.get().recipe().outputDenomination());

        // IC2 Iridium + Alloy gated by ic2 mod
        assertTrue(registry.findMatch("ic2:iridium", 1, "ic2:alloy", 1, null, mod -> false).isEmpty());
        var ic2Iridium = registry.findMatch("ic2:iridium", 1, "ic2:alloy", 1, null, "ic2"::equals);
        assertTrue(ic2Iridium.isPresent());
        assertEquals(CurrencyDenomination.X1000, ic2Iridium.get().recipe().outputDenomination());
    }

    @Test
    void mintIngredientMatchesCommonNeoForgeAndForgeTagsAndJsonMerge() {
        MintRecipeRegistry registry = MintRecipeRegistry.createDefault();

        // Simulate third-party mod ingot tagged with NeoForge c:ingots/tin and Forge forge:ingots/tin
        MintIngredient.TagMatcher neoForgeTags = (item, tag) ->
                item.equals("mekanism:ingot_tin") && tag.equals("c:ingots/tin");
        MintIngredient.TagMatcher forge1192Tags = (item, tag) ->
                item.equals("thermal:tin_ingot") && tag.equals("forge:ingots/tin");

        var neoMatch = registry.findMatch("minecraft:copper_ingot", 1, "mekanism:ingot_tin", 1, neoForgeTags, mod -> false);
        assertTrue(neoMatch.isPresent());
        assertEquals(CurrencyDenomination.X5, neoMatch.get().recipe().outputDenomination());
        assertEquals(2, neoMatch.get().recipe().outputCount());

        var forgeMatch = registry.findMatch("thermal:tin_ingot", 1, null, 0, forge1192Tags, mod -> false);
        assertTrue(forgeMatch.isPresent());
        assertEquals(CurrencyDenomination.X1, forgeMatch.get().recipe().outputDenomination());

        // JSON merge: add custom recipe and disable an existing recipe
        String customJson = """
                {
                  "recipes": [
                    {
                      "id": "custom:osmium_mint",
                      "ingredients": [ { "item": "mekanism:ingot_osmium", "count": 2 } ],
                      "outputDenomination": "X20",
                      "outputCount": 1,
                      "energyEu": 750,
                      "durationTicks": 90
                    },
                    {
                      "id": "tyc2:mint_copper_1",
                      "disabled": true
                    }
                  ]
                }
                """;
        assertEquals(2, registry.mergeFromJson(customJson));
        assertTrue(registry.getById("tyc2:mint_copper_1").isEmpty());
        var customMatch = registry.findMatch("mekanism:ingot_osmium", 2, null, 0, null, mod -> false);
        assertTrue(customMatch.isPresent());
        assertEquals("custom:osmium_mint", customMatch.get().recipe().id());
    }

    @Test
    void primitiveVsIndustrialMintBoundariesAndJsonRoundTrip() {
        MintRecipeRegistry registry = MintRecipeRegistry.createDefault();

        // Exactly 6 pure-Vanilla 1x..100x recipes are primitiveAllowed
        var primitiveRecipes = registry.getPrimitiveRecipes();
        assertEquals(6, primitiveRecipes.size());
        assertEquals(
                Set.of(
                        "tyc2:mint_copper_1",
                        "tyc2:mint_iron_5",
                        "tyc2:mint_iron_pair_10",
                        "tyc2:mint_gold_20",
                        "tyc2:mint_gold_iron_50",
                        "tyc2:mint_gold_emerald_100"
                ),
                primitiveRecipes.stream().map(MintRecipe::id).collect(java.util.stream.Collectors.toSet())
        );

        // Primitive Mint ACCEPTS all 6 pure-Vanilla 1x..100x recipes and does NOT flag requiresIndustrialMint
        assertTrue(registry.findMatch("minecraft:copper_ingot", 1, null, 0, null, mod -> true, true).isPresent());
        assertTrue(registry.findMatch("minecraft:iron_ingot", 1, null, 0, null, mod -> true, true).isPresent());
        assertTrue(registry.findMatch("minecraft:iron_ingot", 1, "minecraft:iron_ingot", 1, null, mod -> true, true).isPresent());
        assertTrue(registry.findMatch("minecraft:gold_ingot", 1, null, 0, null, mod -> true, true).isPresent());
        assertTrue(registry.findMatch("minecraft:gold_ingot", 1, "minecraft:iron_ingot", 1, null, mod -> true, true).isPresent());
        assertTrue(registry.findMatch("minecraft:gold_ingot", 2, "minecraft:emerald", 1, null, mod -> true, true).isPresent());
        assertFalse(registry.requiresIndustrialMint("minecraft:gold_ingot", 2, "minecraft:emerald", 1, null, mod -> true));
        assertFalse(registry.requiresIndustrialMint("minecraft:cobblestone", 64, null, 0, null, mod -> true));

        // Primitive Mint REJECTS 200x..1000x (Diamond, Netherite, Iridium) & modded/IC2 metals/alloys,
        // while Industrial Mint ACCEPTS them and requiresIndustrialMint returns true!
        Object[][] industrialOnlyCases = {
                {"minecraft:diamond", 1, "minecraft:gold_ingot", 2},
                {"minecraft:netherite_scrap", 1, "minecraft:gold_ingot", 2},
                {"minecraft:netherite_ingot", 1, null, 0},
                {"minecraft:netherite_ingot", 1, "minecraft:diamond", 1},
                {"ic2:tin_ingot", 1, null, 0},
                {"minecraft:copper_ingot", 1, "ic2:tin_ingot", 1},
                {"ic2:bronze_ingot", 1, null, 0},
                {"ic2:silver_ingot", 1, null, 0},
                {"ic2:steel_ingot", 1, null, 0},
                {"ic2:refined_iron_ingot", 1, null, 0},
                {"ic2:alloy", 1, "minecraft:gold_ingot", 1},
                {"ic2:alloy", 2, "minecraft:diamond", 1},
                {"ic2:iridium", 1, "ic2:alloy", 1}
        };
        for (Object[] c : industrialOnlyCases) {
            String s0 = (String) c[0];
            int n0 = (Integer) c[1];
            String s1 = (String) c[2];
            int n1 = (Integer) c[3];
            assertTrue(registry.findMatch(s0, n0, s1, n1, null, mod -> true, true).isEmpty(),
                    "Primitive Mint must reject industrial-only inputs: " + s0 + " + " + s1);
            assertTrue(registry.findMatch(s0, n0, s1, n1, null, mod -> true, false).isPresent(),
                    "Industrial Mint must accept inputs: " + s0 + " + " + s1);
            assertTrue(registry.requiresIndustrialMint(s0, n0, s1, n1, null, mod -> true),
                    "requiresIndustrialMint must be true for: " + s0 + " + " + s1);
        }

        // Valid primitive input item checks
        assertTrue(registry.isValidPrimitiveInputItem("minecraft:copper_ingot", null));
        assertTrue(registry.isValidPrimitiveInputItem("minecraft:iron_ingot", null));
        assertTrue(registry.isValidPrimitiveInputItem("minecraft:gold_ingot", null));
        assertTrue(registry.isValidPrimitiveInputItem("minecraft:emerald", null));
        assertFalse(registry.isValidPrimitiveInputItem("minecraft:diamond", null));
        assertFalse(registry.isValidPrimitiveInputItem("minecraft:netherite_ingot", null));
        assertFalse(registry.isValidPrimitiveInputItem("ic2:tin_ingot", null));
        assertFalse(registry.isValidPrimitiveInputItem("ic2:alloy", null));

        // JSON serialization round-trip preserves primitive_allowed
        String jsonOut = registry.toJson();
        assertTrue(jsonOut.contains("\"primitive_allowed\": true"));
        assertTrue(jsonOut.contains("\"primitive_allowed\": false"));

        MintRecipeRegistry reloaded = new MintRecipeRegistry();
        reloaded.mergeFromJson(jsonOut);
        assertEquals(registry.allRecipes().size(), reloaded.allRecipes().size());
        assertEquals(6, reloaded.getPrimitiveRecipes().size());
        assertTrue(reloaded.getById("tyc2:mint_copper_1").orElseThrow().primitiveAllowed());
        assertFalse(reloaded.getById("tyc2:mint_diamond_gold_200").orElseThrow().primitiveAllowed());
        assertFalse(reloaded.getById("tyc2:mint_tin_1").orElseThrow().primitiveAllowed());

        // Custom datapack JSON explicitly overriding primitive_allowed
        reloaded.mergeFromJson("""
                {
                  "id": "custom:primitive_tin_override",
                  "ingredients": [ { "item": "ic2:tin_ingot", "count": 1 } ],
                  "outputDenomination": "X1",
                  "outputCount": 1,
                  "energyEu": 250,
                  "durationTicks": 50,
                  "primitive_allowed": true
                }
                """);
        assertTrue(reloaded.getById("custom:primitive_tin_override").orElseThrow().primitiveAllowed());
    }

    @Test
    void personalAndGuildBankingTransfersLogsAndPersistence() {
        BankAccountManager bank = new BankAccountManager();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        bank.getOrCreatePersonal(alice, "Alice");
        bank.getOrCreatePersonal(bob, "Bob");

        // Deposit cash & coin maps
        assertTrue(bank.deposit(alice, 500L, 100L, "Initial deposit").success());
        assertTrue(bank.depositCoins(alice, CurrencyDenomination.decompose(350L), 110L, "Coin deposit").success());
        assertEquals(850L, bank.getActiveBalance(alice));

        // Denominated withdrawal & insufficient funds check
        assertTrue(bank.withdrawDenominated(alice, CurrencyDenomination.X100, 2, 120L, "Cash out").success());
        assertEquals(650L, bank.getActiveBalance(alice));
        assertFalse(bank.withdraw(alice, 1_000L, 130L, "Too much").success());

        // Player-to-player transfer
        assertTrue(bank.transferToPlayer(alice, bob, 150L, 140L, "Payment").success());
        assertEquals(500L, bank.getActiveBalance(alice));
        assertEquals(150L, bank.getActiveBalance(bob));
        assertFalse(bank.transferToPlayer(alice, alice, 10L, 145L, "Self").success());

        // Create Guild/Company, invite Bob via invite code, toggle active settlement
        BankAccountManager.CompanyAccount guild = bank.createCompany(alice, "Alice", "Ironclad Guild");
        assertEquals(Set.of(alice), guild.memberUuids());
        assertFalse(bank.setActiveCompany(bob, guild.companyId()).success());

        BankAccountManager.BankResult joinRes = bank.joinCompanyByInviteCode(bob, "Bob", guild.inviteCode());
        assertTrue(joinRes.success());
        assertTrue(guild.isMember(bob));

        assertTrue(bank.setActiveCompany(alice, guild.companyId()).success());
        assertTrue(bank.setActiveCompany(bob, guild.companyId()).success());
        assertEquals(BankAccountManager.AccountScope.COMPANY, bank.resolveSettlementAccount(alice).scope());

        // Shared guild deposit by Alice, withdrawal by Bob
        assertTrue(bank.deposit(alice, 1_200L, 200L, "Guild capital").success());
        assertEquals(1_200L, bank.getActiveBalance(bob));
        assertTrue(bank.withdraw(bob, 200L, 210L, "Guild purchase").success());
        assertEquals(1_000L, guild.balance());

        // Total combined balance across all personal + company accounts
        assertEquals(500L + 150L + 1_000L, bank.getTotalCombinedBalance());

        // Transaction log bounded at 32 entries
        for (int i = 0; i < 40; i++) {
            bank.deposit(alice, 1L, 300L + i, "Tick " + i);
        }
        assertEquals(BankAccountManager.MAX_TRANSACTION_LOGS, guild.transactionLogs().size());

        // JSON serialization round-trip
        String serialized = bank.toJson();
        BankAccountManager restored = BankAccountManager.fromJson(serialized);
        assertEquals(bank.getTotalCombinedBalance(), restored.getTotalCombinedBalance());
        assertEquals(Optional.of(alice), restored.findPersonalByName("alice").map(BankAccountManager.PersonalAccount::playerUuid));
        assertEquals(BankAccountManager.AccountScope.COMPANY, restored.resolveSettlementAccount(bob).scope());
        assertEquals(guild.balance(), restored.getCompany(guild.companyId()).orElseThrow().balance());

        // Owner leaving transfers ownership; last member leaving refunds balance to personal account
        assertTrue(restored.leaveCompany(alice, guild.companyId(), 500L).success());
        assertEquals(bob, restored.getCompany(guild.companyId()).orElseThrow().ownerUuid());
        long bobPersonalBefore = restored.getPersonal(bob).orElseThrow().balance();
        long companyBalBefore = restored.getCompany(guild.companyId()).orElseThrow().balance();
        assertTrue(restored.leaveCompany(bob, guild.companyId(), 510L).success());
        assertTrue(restored.getCompany(guild.companyId()).isEmpty());
        assertEquals(bobPersonalBefore + companyBalBefore, restored.getPersonal(bob).orElseThrow().balance());
    }
}
