package com.tycooncraft.tyc2.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java configuration model for {@code config/tyc2/general.json}.
 *
 * <p>Covers macro-economy inflation guardrails, IC2 coin recipe filtering,
 * industrial tier gating, demand/expansion intervals, and village negotiation costs.
 */
public final class TyC2Config {

    /** Current configuration schema version. */
    public static final int CURRENT_CONFIG_VERSION = 2;

    /** Legacy v1.0.0/v1.0.1 default refresh interval (12,000 ticks = 10 real min). */
    public static final int LEGACY_DEMAND_REFRESH_TICKS = 12_000;

    /** Canonical weekly demand refresh interval (168,000 dayTime ticks = 7 Minecraft days). */
    public static final int DEFAULT_DEMAND_REFRESH_TICKS = 168_000;

    private int configVersion = CURRENT_CONFIG_VERSION;

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

    private int demandRefreshTicks = DEFAULT_DEMAND_REFRESH_TICKS;
    private double weeklyQuantityMultiplier = 3.5;
    private double weeklyRareQuantityMultiplier = 1.75;
    private long initialVillageWorkingCapital = 500L;
    private double weeklyMunicipalStipendRatio = 0.45;
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

    private int aidGatherTicksSmall = 3_600;
    private int aidGatherTicksLarge = 6_000;
    private double aidLaborSpeedPerUnemployed = 0.125;
    private double aidMaxLaborSpeedBonus = 1.0;
    private int maxQueuedAidProjects = 3;
    private double lowUnemploymentThreshold = 0.25;
    private double lowUnempRestockMinMult = 1.25;
    private double lowUnempRestockMaxMult = 1.50;
    private double lowUnempQtyMinMult = 1.25;
    private double lowUnempQtyMaxMult = 1.50;
    private boolean enableModdedProfessions = true;
    private List<String> moddedProfessionBlacklist = List.of();

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
     * <p>Automatically migrates pre-v2 configs ({@code configVersion < 2}) whose legacy
     * {@code demandRefreshTicks} remained at {@code 12,000} ticks up to the canonical weekly
     * {@code 168,000} ticks (7 MC days) while preserving every other user setting, then persists
     * the migrated config back to disk.
     *
     * @param path file path (e.g. {@code config/tyc2/general.json})
     * @return loaded or default configuration
     * @throws IOException if file I/O fails
     */
    public static TyC2Config loadOrCreate(Path path) throws IOException {
        if (Files.exists(path)) {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            TyC2Config loaded = fromJson(content);
            if (loaded.configVersion < CURRENT_CONFIG_VERSION) {
                if (loaded.demandRefreshTicks == LEGACY_DEMAND_REFRESH_TICKS) {
                    loaded.demandRefreshTicks = DEFAULT_DEMAND_REFRESH_TICKS;
                }
                loaded.configVersion = CURRENT_CONFIG_VERSION;
                loaded.save(path);
            }
            return loaded;
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
        map.put("configVersion", configVersion);
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
        map.put("weeklyQuantityMultiplier", weeklyQuantityMultiplier);
        map.put("weeklyRareQuantityMultiplier", weeklyRareQuantityMultiplier);
        map.put("initialVillageWorkingCapital", initialVillageWorkingCapital);
        map.put("weeklyMunicipalStipendRatio", weeklyMunicipalStipendRatio);
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
        map.put("aidGatherTicksSmall", aidGatherTicksSmall);
        map.put("aidGatherTicksLarge", aidGatherTicksLarge);
        map.put("aidLaborSpeedPerUnemployed", aidLaborSpeedPerUnemployed);
        map.put("aidMaxLaborSpeedBonus", aidMaxLaborSpeedBonus);
        map.put("maxQueuedAidProjects", maxQueuedAidProjects);
        map.put("lowUnemploymentThreshold", lowUnemploymentThreshold);
        map.put("lowUnempRestockMinMult", lowUnempRestockMinMult);
        map.put("lowUnempRestockMaxMult", lowUnempRestockMaxMult);
        map.put("lowUnempQtyMinMult", lowUnempQtyMinMult);
        map.put("lowUnempQtyMaxMult", lowUnempQtyMaxMult);
        map.put("enableModdedProfessions", enableModdedProfessions);
        map.put("moddedProfessionBlacklist", new ArrayList<>(moddedProfessionBlacklist));
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
        c.configVersion = Math.max(0, JsonLite.getInt(map, "configVersion", 0));
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
        c.weeklyQuantityMultiplier = clamp(
                JsonLite.getDouble(map, "weeklyQuantityMultiplier", c.weeklyQuantityMultiplier), 0.5, 20.0);
        c.weeklyRareQuantityMultiplier = clamp(
                JsonLite.getDouble(map, "weeklyRareQuantityMultiplier", c.weeklyRareQuantityMultiplier), 0.5, 10.0);
        c.initialVillageWorkingCapital = clampLong(
                JsonLite.getLong(map, "initialVillageWorkingCapital", c.initialVillageWorkingCapital), 0L, 100_000L);
        c.weeklyMunicipalStipendRatio = clamp(
                JsonLite.getDouble(map, "weeklyMunicipalStipendRatio", c.weeklyMunicipalStipendRatio), 0.0, 1.0);
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

        c.aidGatherTicksSmall = Math.max(20, JsonLite.getInt(map, "aidGatherTicksSmall", c.aidGatherTicksSmall));
        c.aidGatherTicksLarge = Math.max(20, JsonLite.getInt(map, "aidGatherTicksLarge", c.aidGatherTicksLarge));
        c.aidLaborSpeedPerUnemployed = clamp(
                JsonLite.getDouble(map, "aidLaborSpeedPerUnemployed", c.aidLaborSpeedPerUnemployed), 0.0, 1.0);
        c.aidMaxLaborSpeedBonus = clamp(
                JsonLite.getDouble(map, "aidMaxLaborSpeedBonus", c.aidMaxLaborSpeedBonus), 0.0, 5.0);
        c.maxQueuedAidProjects = Math.max(1, Math.min(16,
                JsonLite.getInt(map, "maxQueuedAidProjects", c.maxQueuedAidProjects)));
        c.lowUnemploymentThreshold = clamp(
                JsonLite.getDouble(map, "lowUnemploymentThreshold", c.lowUnemploymentThreshold), 0.0, 1.0);
        c.lowUnempRestockMinMult = clamp(
                JsonLite.getDouble(map, "lowUnempRestockMinMult", c.lowUnempRestockMinMult), 1.0, 5.0);
        c.lowUnempRestockMaxMult = clamp(
                JsonLite.getDouble(map, "lowUnempRestockMaxMult", c.lowUnempRestockMaxMult),
                c.lowUnempRestockMinMult, 10.0);
        c.lowUnempQtyMinMult = clamp(
                JsonLite.getDouble(map, "lowUnempQtyMinMult", c.lowUnempQtyMinMult), 1.0, 5.0);
        c.lowUnempQtyMaxMult = clamp(
                JsonLite.getDouble(map, "lowUnempQtyMaxMult", c.lowUnempQtyMaxMult),
                c.lowUnempQtyMinMult, 10.0);
        c.enableModdedProfessions = JsonLite.getBoolean(map, "enableModdedProfessions", c.enableModdedProfessions);
        c.setModdedProfessionBlacklist(JsonLite.getStringList(map, "moddedProfessionBlacklist"));
        return c;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    /** @return persisted configuration schema version (current {@value #CURRENT_CONFIG_VERSION}) */
    public int configVersion() {
        return configVersion;
    }

    /** @param value configuration schema version ({@code >= 0}) */
    public TyC2Config setConfigVersion(int value) {
        this.configVersion = Math.max(0, value);
        return this;
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

    /** @return dayTime ticks between weekly demand refreshes (default 168,000 = 7 game days) */
    public int demandRefreshTicks() {
        return demandRefreshTicks;
    }

    /** @param value refresh period in ticks (min 200) */
    public TyC2Config setDemandRefreshTicks(int value) {
        this.demandRefreshTicks = Math.max(200, value);
        return this;
    }

    /** @return weekly batch quantity multiplier for standard goods (default 3.5) */
    public double weeklyQuantityMultiplier() {
        return weeklyQuantityMultiplier;
    }

    /** @param value weekly quantity multiplier clamped to [0.5, 20.0] */
    public TyC2Config setWeeklyQuantityMultiplier(double value) {
        this.weeklyQuantityMultiplier = clamp(value, 0.5, 20.0);
        return this;
    }

    /** @return weekly batch quantity multiplier for rare ENCHANTED_BOOK/ENDGAME goods (default 1.75) */
    public double weeklyRareQuantityMultiplier() {
        return weeklyRareQuantityMultiplier;
    }

    /** @param value weekly rare quantity multiplier clamped to [0.5, 10.0] */
    public TyC2Config setWeeklyRareQuantityMultiplier(double value) {
        this.weeklyRareQuantityMultiplier = clamp(value, 0.5, 10.0);
        return this;
    }

    /** @return minimum municipal working capital floor upon establishing partnership (default 500 IC) */
    public long initialVillageWorkingCapital() {
        return initialVillageWorkingCapital;
    }

    /** @param value initial village working capital floor clamped to [0, 100,000] */
    public TyC2Config setInitialVillageWorkingCapital(long value) {
        this.initialVillageWorkingCapital = clampLong(value, 0L, 100_000L);
        return this;
    }

    /** @return weekly municipal stipend threshold ratio relative to target reserve (default 0.45) */
    public double weeklyMunicipalStipendRatio() {
        return weeklyMunicipalStipendRatio;
    }

    /** @param value stipend threshold ratio clamped to [0.0, 1.0] (0.0 disables stipend) */
    public TyC2Config setWeeklyMunicipalStipendRatio(double value) {
        this.weeklyMunicipalStipendRatio = clamp(value, 0.0, 1.0);
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

    /** @return base gathering duration in ticks for small aided workplaces (default 3,600) */
    public int aidGatherTicksSmall() {
        return aidGatherTicksSmall;
    }

    /** @param value base gathering ticks for small aided workplaces (>= 20) */
    public TyC2Config setAidGatherTicksSmall(int value) {
        this.aidGatherTicksSmall = Math.max(20, value);
        return this;
    }

    /** @return base gathering duration in ticks for large aided workshops (default 6,000) */
    public int aidGatherTicksLarge() {
        return aidGatherTicksLarge;
    }

    /** @param value base gathering ticks for large aided workshops (>= 20) */
    public TyC2Config setAidGatherTicksLarge(int value) {
        this.aidGatherTicksLarge = Math.max(20, value);
        return this;
    }

    /** @return labor gathering speed bonus per unemployed adult villager (default 0.125) */
    public double aidLaborSpeedPerUnemployed() {
        return aidLaborSpeedPerUnemployed;
    }

    /** @param value labor speed bonus per unemployed villager in [0.0, 1.0] */
    public TyC2Config setAidLaborSpeedPerUnemployed(double value) {
        this.aidLaborSpeedPerUnemployed = clamp(value, 0.0, 1.0);
        return this;
    }

    /** @return maximum additive labor gathering speed bonus (default 1.0, i.e. up to 2.0x total) */
    public double aidMaxLaborSpeedBonus() {
        return aidMaxLaborSpeedBonus;
    }

    /** @param value maximum additive labor speed bonus in [0.0, 5.0] */
    public TyC2Config setAidMaxLaborSpeedBonus(double value) {
        this.aidMaxLaborSpeedBonus = clamp(value, 0.0, 5.0);
        return this;
    }

    /** @return maximum simultaneously queued aid construction projects per village (default 3) */
    public int maxQueuedAidProjects() {
        return maxQueuedAidProjects;
    }

    /** @param value max queued aid projects in [1, 16] */
    public TyC2Config setMaxQueuedAidProjects(int value) {
        this.maxQueuedAidProjects = Math.max(1, Math.min(16, value));
        return this;
    }

    /** @return unemployment rate threshold below which low-unemployment bonus activates (default 0.25) */
    public double lowUnemploymentThreshold() {
        return lowUnemploymentThreshold;
    }

    /** @param value threshold in [0.0, 1.0] (0.0 disables bonus) */
    public TyC2Config setLowUnemploymentThreshold(double value) {
        this.lowUnemploymentThreshold = clamp(value, 0.0, 1.0);
        return this;
    }

    /** @return minimum restock speed multiplier when low-unemployment bonus activates (default 1.25) */
    public double lowUnempRestockMinMult() {
        return lowUnempRestockMinMult;
    }

    /** @param value minimum restock speed multiplier in [1.0, 5.0] */
    public TyC2Config setLowUnempRestockMinMult(double value) {
        this.lowUnempRestockMinMult = clamp(value, 1.0, 5.0);
        if (this.lowUnempRestockMaxMult < this.lowUnempRestockMinMult) {
            this.lowUnempRestockMaxMult = this.lowUnempRestockMinMult;
        }
        return this;
    }

    /** @return maximum restock speed multiplier at 0% unemployment (default 1.50) */
    public double lowUnempRestockMaxMult() {
        return lowUnempRestockMaxMult;
    }

    /** @param value maximum restock speed multiplier in [minMult, 10.0] */
    public TyC2Config setLowUnempRestockMaxMult(double value) {
        this.lowUnempRestockMaxMult = clamp(value, this.lowUnempRestockMinMult, 10.0);
        return this;
    }

    /** @return minimum trade quantity multiplier when low-unemployment bonus activates (default 1.25) */
    public double lowUnempQtyMinMult() {
        return lowUnempQtyMinMult;
    }

    /** @param value minimum trade quantity multiplier in [1.0, 5.0] */
    public TyC2Config setLowUnempQtyMinMult(double value) {
        this.lowUnempQtyMinMult = clamp(value, 1.0, 5.0);
        if (this.lowUnempQtyMaxMult < this.lowUnempQtyMinMult) {
            this.lowUnempQtyMaxMult = this.lowUnempQtyMinMult;
        }
        return this;
    }

    /** @return maximum trade quantity multiplier at 0% unemployment (default 1.50) */
    public double lowUnempQtyMaxMult() {
        return lowUnempQtyMaxMult;
    }

    /** @param value maximum trade quantity multiplier in [minMult, 10.0] */
    public TyC2Config setLowUnempQtyMaxMult(double value) {
        this.lowUnempQtyMaxMult = clamp(value, this.lowUnempQtyMinMult, 10.0);
        return this;
    }

    /** @return whether third-party mod villager professions are dynamically discovered (default true) */
    public boolean enableModdedProfessions() {
        return enableModdedProfessions;
    }

    /** @param value whether to enable dynamic modded profession discovery */
    public TyC2Config setEnableModdedProfessions(boolean value) {
        this.enableModdedProfessions = value;
        return this;
    }

    /** @return unmodifiable list of blacklisted modded profession IDs (supports {@code "modid:*"}) */
    public List<String> moddedProfessionBlacklist() {
        return moddedProfessionBlacklist;
    }

    /** @param blacklist list of blacklisted profession IDs or namespace wildcards */
    public TyC2Config setModdedProfessionBlacklist(List<String> blacklist) {
        if (blacklist == null || blacklist.isEmpty()) {
            this.moddedProfessionBlacklist = List.of();
            return this;
        }
        List<String> normalized = new ArrayList<>(blacklist.size());
        for (String entry : blacklist) {
            if (entry != null && !entry.isBlank()) {
                normalized.add(entry.trim().toLowerCase(Locale.ROOT));
            }
        }
        this.moddedProfessionBlacklist = Collections.unmodifiableList(normalized);
        return this;
    }

    /**
     * Checks whether {@code professionId} is excluded by {@link #moddedProfessionBlacklist()}.
     * Supports exact profession IDs (e.g. {@code "modx:guard"}) and namespace wildcards
     * (e.g. {@code "modx:*"}).
     *
     * @param professionId namespaced profession ID
     * @return {@code true} if blacklisted
     */
    public boolean isModdedProfessionBlacklisted(String professionId) {
        if (professionId == null || professionId.isBlank() || moddedProfessionBlacklist.isEmpty()) {
            return false;
        }
        String norm = professionId.trim().toLowerCase(Locale.ROOT);
        int colon = norm.indexOf(':');
        String nsWildcard = colon > 0 ? norm.substring(0, colon) + ":*" : null;
        for (String blocked : moddedProfessionBlacklist) {
            if (blocked.equals(norm) || (nsWildcard != null && blocked.equals(nsWildcard))) {
                return true;
            }
        }
        return false;
    }
}
