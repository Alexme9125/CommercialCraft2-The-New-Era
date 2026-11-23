package com.tycooncraft.tyc2.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure-Java configuration model for {@code config/tyc2/general.json}.
 *
 * <p>Covers macro-economy inflation guardrails, IC2 coin recipe filtering,
 * industrial tier gating, demand/expansion intervals, and village negotiation costs.
 */
public final class TyC2Config {

    private boolean disableIc2MetalFormerCoinRecipe = true;
    private boolean enforceIndustrialTierCap = true;
    private boolean enableSeasonalArbitrage = true;

    private double minPriceMultiplier = 0.25;
    private double maxPriceMultiplier = 4.00;
    private double cpiExponent = 0.55;
    private double baselineMoneyPerVillage = 2000.0;
    private double goodsCirculationDecay = 0.20;
    private double itemSupplyFlowDecay = 0.10;
    private double municipalUpkeepRate = 0.02;

    private int demandRefreshTicks = 12_000;
    private int expansionIntervalTicks = 18_000;
    private int autoFulfillIntervalTicks = 40;
    private int minExpansionRadius = 10;
    private int maxExpansionRadius = 72;
    private int constructionBlocksPerStep = 2;
    private int constructionStepIntervalTicks = 3;
    private int terminalMaxBoundVillages = 16;

    private long negotiationCostIc = 100L;
    private int negotiationRequiredLogs = 32;
    private int negotiationRequiredIron = 16;
    private int negotiationRequiredFood = 32;

    /**
     * Creates a configuration instance populated with default values.
     */
    public TyC2Config() {
    }

    /**
     * Creates a fresh copy of the default configuration.
     *
     * @return default {@link TyC2Config}
     */
    public static TyC2Config defaults() {
        return new TyC2Config();
    }

    /**
     * Loads {@code general.json} from {@code path}, or writes a clean default file if absent.
     *
     * @param path file path (e.g. {@code config/tyc2/general.json})
     * @return loaded or default configuration
     * @throws IOException if file I/O fails
     */
    public static TyC2Config loadOrCreate(Path path) throws IOException {
        if (Files.exists(path)) {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return fromJson(content);
        }
        TyC2Config config = defaults();
        config.save(path);
        return config;
    }

    /**
     * Saves this configuration in pretty-printed JSON format, creating parent directories if needed.
     *
     * @param path destination file path
     * @throws IOException if writing fails
     */
    public void save(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, toJson() + "\n", StandardCharsets.UTF_8);
    }

    /**
     * Serializes this configuration into pretty-printed JSON.
     *
     * @return JSON text
     */
    public String toJson() {
        return JsonLite.toPrettyJson(toDataMap());
    }

    /**
     * Deserializes a configuration from JSON text, applying safe bounds clamping.
     *
     * @param json JSON object string
     * @return parsed and sanitized configuration
     */
    public static TyC2Config fromJson(String json) {
        return fromDataMap(JsonLite.parseObject(json));
    }

    /**
     * Converts this configuration into a structured map suitable for JSON or NBT persistence.
     *
     * @return ordered key-value map
     */
    public Map<String, Object> toDataMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("disableIc2MetalFormerCoinRecipe", disableIc2MetalFormerCoinRecipe);
        map.put("enforceIndustrialTierCap", enforceIndustrialTierCap);
        map.put("enableSeasonalArbitrage", enableSeasonalArbitrage);
        map.put("minPriceMultiplier", minPriceMultiplier);
        map.put("maxPriceMultiplier", maxPriceMultiplier);
        map.put("cpiExponent", cpiExponent);
        map.put("baselineMoneyPerVillage", baselineMoneyPerVillage);
        map.put("goodsCirculationDecay", goodsCirculationDecay);
        map.put("itemSupplyFlowDecay", itemSupplyFlowDecay);
        map.put("municipalUpkeepRate", municipalUpkeepRate);
        map.put("demandRefreshTicks", demandRefreshTicks);
        map.put("expansionIntervalTicks", expansionIntervalTicks);
        map.put("autoFulfillIntervalTicks", autoFulfillIntervalTicks);
        map.put("minExpansionRadius", minExpansionRadius);
        map.put("maxExpansionRadius", maxExpansionRadius);
        map.put("constructionBlocksPerStep", constructionBlocksPerStep);
        map.put("constructionStepIntervalTicks", constructionStepIntervalTicks);
        map.put("terminalMaxBoundVillages", terminalMaxBoundVillages);
        map.put("negotiationCostIc", negotiationCostIc);
        map.put("negotiationRequiredLogs", negotiationRequiredLogs);
        map.put("negotiationRequiredIron", negotiationRequiredIron);
        map.put("negotiationRequiredFood", negotiationRequiredFood);
        return map;
    }

    /**
     * Reconstructs a configuration from a map, falling back to defaults for absent keys.
     *
     * @param map key-value map
     * @return parsed and clamped configuration
     */
    public static TyC2Config fromDataMap(Map<String, Object> map) {
        TyC2Config c = new TyC2Config();
        if (map == null) {
            return c;
        }
        c.disableIc2MetalFormerCoinRecipe = JsonLite.getBoolean(map, "disableIc2MetalFormerCoinRecipe", c.disableIc2MetalFormerCoinRecipe);
        c.enforceIndustrialTierCap = JsonLite.getBoolean(map, "enforceIndustrialTierCap", c.enforceIndustrialTierCap);
        c.enableSeasonalArbitrage = JsonLite.getBoolean(map, "enableSeasonalArbitrage", c.enableSeasonalArbitrage);

        c.minPriceMultiplier = clamp(JsonLite.getDouble(map, "minPriceMultiplier", c.minPriceMultiplier), 0.05, 1.0);
        c.maxPriceMultiplier = clamp(JsonLite.getDouble(map, "maxPriceMultiplier", c.maxPriceMultiplier), Math.max(1.0, c.minPriceMultiplier), 20.0);
        c.cpiExponent = clamp(JsonLite.getDouble(map, "cpiExponent", c.cpiExponent), 0.10, 2.0);
        c.baselineMoneyPerVillage = Math.max(100.0, JsonLite.getDouble(map, "baselineMoneyPerVillage", c.baselineMoneyPerVillage));
        c.goodsCirculationDecay = clamp(JsonLite.getDouble(map, "goodsCirculationDecay", c.goodsCirculationDecay), 0.01, 0.90);
        c.itemSupplyFlowDecay = clamp(JsonLite.getDouble(map, "itemSupplyFlowDecay", c.itemSupplyFlowDecay), 0.01, 0.90);
        c.municipalUpkeepRate = clamp(JsonLite.getDouble(map, "municipalUpkeepRate", c.municipalUpkeepRate), 0.0, 0.25);

        c.demandRefreshTicks = Math.max(200, JsonLite.getInt(map, "demandRefreshTicks", c.demandRefreshTicks));
        c.expansionIntervalTicks = Math.max(200, JsonLite.getInt(map, "expansionIntervalTicks", c.expansionIntervalTicks));
        c.autoFulfillIntervalTicks = Math.max(10, JsonLite.getInt(map, "autoFulfillIntervalTicks", c.autoFulfillIntervalTicks));
        c.minExpansionRadius = Math.max(6, JsonLite.getInt(map, "minExpansionRadius", c.minExpansionRadius));
        c.maxExpansionRadius = Math.max(c.minExpansionRadius + 8, JsonLite.getInt(map, "maxExpansionRadius", c.maxExpansionRadius));
        c.constructionBlocksPerStep = Math.max(1, JsonLite.getInt(map, "constructionBlocksPerStep", c.constructionBlocksPerStep));
        c.constructionStepIntervalTicks = Math.max(1, JsonLite.getInt(map, "constructionStepIntervalTicks", c.constructionStepIntervalTicks));
        c.terminalMaxBoundVillages = Math.max(1, Math.min(64, JsonLite.getInt(map, "terminalMaxBoundVillages", c.terminalMaxBoundVillages)));

        c.negotiationCostIc = Math.max(0L, JsonLite.getLong(map, "negotiationCostIc", c.negotiationCostIc));
        c.negotiationRequiredLogs = Math.max(0, JsonLite.getInt(map, "negotiationRequiredLogs", c.negotiationRequiredLogs));
        c.negotiationRequiredIron = Math.max(0, JsonLite.getInt(map, "negotiationRequiredIron", c.negotiationRequiredIron));
        c.negotiationRequiredFood = Math.max(0, JsonLite.getInt(map, "negotiationRequiredFood", c.negotiationRequiredFood));
        return c;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** @return whether IC2 Metal Former cutting coin recipes are filtered out */
    public boolean disableIc2MetalFormerCoinRecipe() {
        return disableIc2MetalFormerCoinRecipe;
    }

    /** @param value whether to disable IC2 Metal Former coin cutting */
    public TyC2Config setDisableIc2MetalFormerCoinRecipe(boolean value) {
        this.disableIc2MetalFormerCoinRecipe = value;
        return this;
    }

    /** @return whether village goods are gated one tier behind player industrial tier */
    public boolean enforceIndustrialTierCap() {
        return enforceIndustrialTierCap;
    }

    /** @param value whether to enforce industrial tier cap */
    public TyC2Config setEnforceIndustrialTierCap(boolean value) {
        this.enforceIndustrialTierCap = value;
        return this;
    }

    /** @return whether seasonal agriculture pricing and off-season arbitrage are active */
    public boolean enableSeasonalArbitrage() {
        return enableSeasonalArbitrage;
    }

    /** @param value whether seasonal arbitrage is enabled */
    public TyC2Config setEnableSeasonalArbitrage(boolean value) {
        this.enableSeasonalArbitrage = value;
        return this;
    }

    /** @return minimum combined price multiplier guardrail (default 0.25) */
    public double minPriceMultiplier() {
        return minPriceMultiplier;
    }

    /** @param value minimum multiplier (clamped to [0.05, 1.0]) */
    public TyC2Config setMinPriceMultiplier(double value) {
        this.minPriceMultiplier = clamp(value, 0.05, 1.0);
        if (this.maxPriceMultiplier < this.minPriceMultiplier) {
            this.maxPriceMultiplier = this.minPriceMultiplier;
        }
        return this;
    }

    /** @return maximum combined price multiplier guardrail (default 4.00) */
    public double maxPriceMultiplier() {
        return maxPriceMultiplier;
    }

    /** @param value maximum multiplier (clamped to [max(1.0, min), 20.0]) */
    public TyC2Config setMaxPriceMultiplier(double value) {
        this.maxPriceMultiplier = clamp(value, Math.max(1.0, this.minPriceMultiplier), 20.0);
        return this;
    }

    /** @return CPI elasticity exponent (default 0.55) */
    public double cpiExponent() {
        return cpiExponent;
    }

    /** @param value CPI exponent */
    public TyC2Config setCpiExponent(double value) {
        this.cpiExponent = clamp(value, 0.10, 2.0);
        return this;
    }

    /** @return baseline reference money supply per partnered village (default 2000.0) */
    public double baselineMoneyPerVillage() {
        return baselineMoneyPerVillage;
    }

    /** @param value baseline money per village */
    public TyC2Config setBaselineMoneyPerVillage(double value) {
        this.baselineMoneyPerVillage = Math.max(100.0, value);
        return this;
    }

    /** @return EMA decay fraction applied to goods circulation each demand cycle (default 0.20) */
    public double goodsCirculationDecay() {
        return goodsCirculationDecay;
    }

    /** @param value decay fraction in [0.01, 0.90] */
    public TyC2Config setGoodsCirculationDecay(double value) {
        this.goodsCirculationDecay = clamp(value, 0.01, 0.90);
        return this;
    }

    /** @return mean-reversion decay fraction applied to per-item net supply flow each cycle (default 0.10) */
    public double itemSupplyFlowDecay() {
        return itemSupplyFlowDecay;
    }

    /** @param value decay fraction in [0.01, 0.90] */
    public TyC2Config setItemSupplyFlowDecay(double value) {
        this.itemSupplyFlowDecay = clamp(value, 0.01, 0.90);
        return this;
    }

    /** @return daily municipal upkeep burn rate on excess village reserve (default 0.02) */
    public double municipalUpkeepRate() {
        return municipalUpkeepRate;
    }

    /** @param value upkeep rate in [0.0, 0.25] */
    public TyC2Config setMunicipalUpkeepRate(double value) {
        this.municipalUpkeepRate = clamp(value, 0.0, 0.25);
        return this;
    }

    /** @return ticks between demand refreshes (default 12,000) */
    public int demandRefreshTicks() {
        return demandRefreshTicks;
    }

    /** @param value refresh period in ticks */
    public TyC2Config setDemandRefreshTicks(int value) {
        this.demandRefreshTicks = Math.max(200, value);
        return this;
    }

    /** @return ticks between village expansion desire checks (default 18,000) */
    public int expansionIntervalTicks() {
        return expansionIntervalTicks;
    }

    /** @param value expansion interval in ticks */
    public TyC2Config setExpansionIntervalTicks(int value) {
        this.expansionIntervalTicks = Math.max(200, value);
        return this;
    }

    /** @return ticks between Cooperation Hub auto-fulfill scans (default 40) */
    public int autoFulfillIntervalTicks() {
        return autoFulfillIntervalTicks;
    }

    /** @param value auto-fulfill interval in ticks */
    public TyC2Config setAutoFulfillIntervalTicks(int value) {
        this.autoFulfillIntervalTicks = Math.max(10, value);
        return this;
    }

    /** @return minimum expansion radius in blocks from Hub (default 10) */
    public int minExpansionRadius() {
        return minExpansionRadius;
    }

    /** @return maximum expansion radius in blocks from Hub (default 72) */
    public int maxExpansionRadius() {
        return maxExpansionRadius;
    }

    /** @return blocks placed per gradual construction step (default 2) */
    public int constructionBlocksPerStep() {
        return constructionBlocksPerStep;
    }

    /** @return ticks between gradual construction steps (default 3) */
    public int constructionStepIntervalTicks() {
        return constructionStepIntervalTicks;
    }

    /** @return max villages bindable to a single Communication Terminal (default 16) */
    public int terminalMaxBoundVillages() {
        return terminalMaxBoundVillages;
    }

    /** @return initial Mayor negotiation fee in IC (default 100) */
    public long negotiationCostIc() {
        return negotiationCostIc;
    }

    /** @param value negotiation IC fee */
    public TyC2Config setNegotiationCostIc(long value) {
        this.negotiationCostIc = Math.max(0L, value);
        return this;
    }

    /** @return initial Mayor negotiation log requirement (default 32) */
    public int negotiationRequiredLogs() {
        return negotiationRequiredLogs;
    }

    /** @return initial Mayor negotiation iron ingot requirement (default 16) */
    public int negotiationRequiredIron() {
        return negotiationRequiredIron;
    }

    /** @return initial Mayor negotiation basic food requirement (default 32) */
    public int negotiationRequiredFood() {
        return negotiationRequiredFood;
    }
}
