package com.tycooncraft.tyc2.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Unified cross-loader ({@code forge:} &harr; {@code c:}) and cross-version
 * (Forge 1.19.2 &harr; NeoForge 1.21.1 / 26.x) common item tag alias registry.
 *
 * <p>Provides deterministic expansion of bare common tag paths (such as {@code "#ingots/iron"},
 * {@code "#stones"}, {@code "#tools/wrench"}) as well as explicit {@code #forge:*} / {@code #c:*}
 * tags across namespace changes and Minecraft 1.21+ standard tag renames.
 */
public final class CommonTagAliases {

    /** Canonical alias groups: first entry is Forge 1.19.2 convention, second is NeoForge 1.21+ convention. */
    private static final List<List<String>> RENAME_GROUPS = List.of(
            List.of("stone", "stones"),
            List.of("cobblestone", "cobblestones"),
            List.of("obsidian", "obsidians"),
            List.of("glass", "glass_blocks"),
            List.of("sand", "sands"),
            List.of("gravel", "gravels"),
            List.of("slimeballs", "slime_balls"),
            List.of("leather", "leathers"),
            List.of("string", "strings"),
            List.of("gunpowder", "gunpowders"),
            List.of("netherrack", "netherracks"),
            List.of("shears", "tools/shear"),
            List.of("tools/wrench", "tools/wrench", "tools/wrenches", "wrenches"),
            List.of("bread", "foods/bread"),
            List.of("cooked_pork", "foods/cooked_pork"),
            List.of("cooked_beef", "foods/cooked_beef")
    );

    private static final Map<String, List<String>> ALIAS_MAP;
    private static final Map<String, String> FORGE_CANONICAL_PATH;
    private static final Map<String, String> NEOFORGE_CANONICAL_PATH;

    static {
        Map<String, Set<String>> grouped = new LinkedHashMap<>();
        Map<String, String> forgeCanon = new LinkedHashMap<>();
        Map<String, String> neoCanon = new LinkedHashMap<>();

        for (List<String> group : RENAME_GROUPS) {
            if (group.isEmpty()) {
                continue;
            }
            String forgePath = group.get(0);
            String neoPath = group.size() > 1 ? group.get(1) : forgePath;
            Set<String> members = new LinkedHashSet<>(group);
            for (String member : members) {
                grouped.computeIfAbsent(member, k -> new LinkedHashSet<>()).addAll(members);
                forgeCanon.putIfAbsent(member, forgePath);
                neoCanon.putIfAbsent(member, neoPath);
            }
        }

        Map<String, List<String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : grouped.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        ALIAS_MAP = Collections.unmodifiableMap(frozen);
        FORGE_CANONICAL_PATH = Collections.unmodifiableMap(forgeCanon);
        NEOFORGE_CANONICAL_PATH = Collections.unmodifiableMap(neoCanon);
    }

    private CommonTagAliases() {
    }

    /**
     * Normalizes a raw tag specification to lowercase with a leading {@code '#'}, or returns
     * {@code ""} when {@code raw} is {@code null} or blank.
     *
     * @param raw raw tag specification (with or without leading {@code '#'})
     * @return normalized tag spec with leading {@code '#'}, or {@code ""}
     */
    public static String normalizeTagSpec(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    }

    /**
     * Normalizes a raw tag specification, returning {@code null} if {@code raw} is {@code null} or blank.
     *
     * @param raw raw tag specification
     * @return normalized tag spec with leading {@code '#'}, or {@code null}
     */
    public static String normalizeTagSpecOrNull(String raw) {
        String norm = normalizeTagSpec(raw);
        return norm.isEmpty() ? null : norm;
    }

    /**
     * Expands a tag specification into an ordered, deduplicated list of concrete tag IDs
     * (without leading {@code '#'}) covering {@code c:} &harr; {@code forge:} namespaces and
     * Forge 1.19.2 &harr; NeoForge 1.21+ standard tag renames.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "#ingots/copper"} &rarr; {@code ["c:ingots/copper", "forge:ingots/copper"]}</li>
     *   <li>{@code "#forge:cobblestone"} &rarr; {@code ["forge:cobblestone", "c:cobblestone", "c:cobblestones", "forge:cobblestones"]}</li>
     *   <li>{@code "#minecraft:logs"} &rarr; {@code ["minecraft:logs"]}</li>
     * </ul>
     *
     * @param tagSpec tag specification with or without leading {@code '#'}
     * @return unmodifiable ordered list of candidate tag IDs (never {@code null})
     */
    public static List<String> expandTagCandidates(String tagSpec) {
        if (tagSpec == null || tagSpec.isBlank()) {
            return Collections.emptyList();
        }
        String clean = tagSpec.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        if (clean.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> out = new LinkedHashSet<>();
        int colon = clean.indexOf(':');
        if (colon >= 0) {
            String ns = clean.substring(0, colon);
            String path = clean.substring(colon + 1);
            if (ns.isEmpty() || path.isEmpty()) {
                return Collections.emptyList();
            }
            out.add(ns + ":" + path);
            if ("c".equals(ns) || "forge".equals(ns)) {
                String otherNs = "c".equals(ns) ? "forge" : "c";
                out.add(otherNs + ":" + path);
                for (String aliasPath : aliasPathsFor(path)) {
                    if (!aliasPath.equals(path)) {
                        out.add("c:" + aliasPath);
                        out.add("forge:" + aliasPath);
                    }
                }
            }
        } else {
            out.add("c:" + clean);
            out.add("forge:" + clean);
            for (String aliasPath : aliasPathsFor(clean)) {
                if (!aliasPath.equals(clean)) {
                    out.add("c:" + aliasPath);
                    out.add("forge:" + aliasPath);
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    /**
     * Returns all equivalent common tag paths for {@code path} (always containing {@code path} itself).
     *
     * @param path tag path without namespace (e.g. {@code "cobblestone"} or {@code "tools/wrench"})
     * @return unmodifiable list of equivalent paths
     */
    public static List<String> aliasPathsFor(String path) {
        if (path == null || path.isBlank()) {
            return Collections.emptyList();
        }
        String norm = path.trim().toLowerCase(Locale.ROOT);
        List<String> aliases = ALIAS_MAP.get(norm);
        return aliases != null ? aliases : Collections.singletonList(norm);
    }

    /**
     * Checks whether {@code itemId} matches {@code tagSpec} using {@code tagMatcher} across all
     * expanded cross-namespace and cross-version aliases.
     *
     * @param itemId candidate item registry ID
     * @param tagSpec tag specification (with or without leading {@code '#'})
     * @param tagMatcher platform tag lookup callback
     * @return {@code true} if any expanded candidate tag matches {@code itemId}
     */
    public static boolean matchesTagSpec(
            String itemId,
            String tagSpec,
            MintIngredient.TagMatcher tagMatcher) {
        if (itemId == null || itemId.isBlank() || tagSpec == null || tagSpec.isBlank() || tagMatcher == null) {
            return false;
        }
        String normItem = itemId.trim().toLowerCase(Locale.ROOT);
        for (String candidate : expandTagCandidates(tagSpec)) {
            if (tagMatcher.hasTag(normItem, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether {@code itemId} carries any standard wrench item tag
     * ({@code c:tools/wrench}, {@code forge:tools/wrench}, {@code c:tools/wrenches},
     * {@code forge:tools/wrenches}, {@code c:wrenches}, or {@code forge:wrenches}).
     *
     * @param itemId candidate item registry ID
     * @param tagMatcher platform tag lookup callback
     * @return {@code true} if tagged as a wrench
     */
    public static boolean isWrenchTag(String itemId, MintIngredient.TagMatcher tagMatcher) {
        return matchesTagSpec(itemId, "#tools/wrench", tagMatcher);
    }

    /**
     * Maps a common tag path (without namespace) to the canonical path for either Forge 1.19.2
     * or NeoForge 1.21.1+.
     *
     * @param path tag path without namespace
     * @param preferForge {@code true} for Forge 1.19.2 conventions, {@code false} for NeoForge 1.21+
     * @return loader-canonical path
     */
    public static String canonicalPathForLoader(String path, boolean preferForge) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String norm = path.trim().toLowerCase(Locale.ROOT);
        return preferForge
                ? FORGE_CANONICAL_PATH.getOrDefault(norm, norm)
                : NEOFORGE_CANONICAL_PATH.getOrDefault(norm, norm);
    }

    /**
     * Formats a tag specification for player-facing UI tooltips using the native namespace and
     * canonical path for the active loader ({@code #forge:...} on Forge 1.19.2, {@code #c:...} on
     * NeoForge 1.21+), while preserving explicit non-common namespaces like {@code #minecraft:logs}.
     *
     * @param tagSpec raw tag specification
     * @param preferForge {@code true} for Forge 1.19.2, {@code false} for NeoForge 1.21+
     * @return formatted display label with leading {@code '#'}, or {@code ""} if blank
     */
    public static String displayTagLabel(String tagSpec, boolean preferForge) {
        if (tagSpec == null || tagSpec.isBlank()) {
            return "";
        }
        String clean = tagSpec.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        if (clean.isEmpty()) {
            return "";
        }
        int colon = clean.indexOf(':');
        String preferredNs = preferForge ? "forge" : "c";
        if (colon < 0) {
            return "#" + preferredNs + ":" + canonicalPathForLoader(clean, preferForge);
        }
        String ns = clean.substring(0, colon);
        String path = clean.substring(colon + 1);
        if ("c".equals(ns) || "forge".equals(ns)) {
            return "#" + preferredNs + ":" + canonicalPathForLoader(path, preferForge);
        }
        return "#" + ns + ":" + path;
    }
}
