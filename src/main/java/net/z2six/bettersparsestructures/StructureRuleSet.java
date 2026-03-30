package net.z2six.bettersparsestructures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class StructureRuleSet {
    private static final Comparator<MatchRule> RULE_ORDER = Comparator
            .comparingInt(MatchRule::specificity)
            .reversed()
            .thenComparingInt(MatchRule::order)
            .reversed();

    private final int globalSpacingRadiusChunks;
    private final int maxSpacingRadiusChunks;
    private final List<MatchRule> whitelistRules;
    private final List<SpacingOverrideRule> spacingOverrideRules;

    private StructureRuleSet(int globalSpacingRadiusChunks, int maxSpacingRadiusChunks, List<MatchRule> whitelistRules, List<SpacingOverrideRule> spacingOverrideRules) {
        this.globalSpacingRadiusChunks = globalSpacingRadiusChunks;
        this.maxSpacingRadiusChunks = maxSpacingRadiusChunks;
        this.whitelistRules = whitelistRules;
        this.spacingOverrideRules = spacingOverrideRules;
    }

    public static StructureRuleSet empty(int globalSpacingRadiusChunks) {
        return new StructureRuleSet(globalSpacingRadiusChunks, globalSpacingRadiusChunks, List.of(), List.of());
    }

    public static StructureRuleSet create(int globalSpacingRadiusChunks, List<? extends String> whitelistEntries, List<? extends String> overrideEntries) {
        List<MatchRule> whitelistRules = parseWhitelistRules(whitelistEntries);
        List<SpacingOverrideRule> overrideRules = parseOverrideRules(overrideEntries);
        int maxSpacingRadiusChunks = globalSpacingRadiusChunks;
        for (SpacingOverrideRule overrideRule : overrideRules) {
            maxSpacingRadiusChunks = Math.max(maxSpacingRadiusChunks, overrideRule.radiusChunks());
        }
        return new StructureRuleSet(globalSpacingRadiusChunks, maxSpacingRadiusChunks, whitelistRules, overrideRules);
    }

    public int globalSpacingRadiusChunks() {
        return globalSpacingRadiusChunks;
    }

    public int maxSpacingRadiusChunks() {
        return maxSpacingRadiusChunks;
    }

    public int whitelistCount() {
        return whitelistRules.size();
    }

    public int overrideCount() {
        return spacingOverrideRules.size();
    }

    public boolean isWhitelisted(String structureId) {
        for (MatchRule whitelistRule : whitelistRules) {
            if (whitelistRule.matches(structureId)) {
                return true;
            }
        }
        return false;
    }

    public int spacingRadiusChunks(String structureId) {
        for (SpacingOverrideRule overrideRule : spacingOverrideRules) {
            if (overrideRule.matches(structureId)) {
                return overrideRule.radiusChunks();
            }
        }
        return globalSpacingRadiusChunks;
    }

    private static List<MatchRule> parseWhitelistRules(List<? extends String> entries) {
        List<MatchRule> parsedRules = new ArrayList<>();
        int order = 0;

        for (String rawEntry : entries) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            if (!entry.contains(":")) {
                Bettersparsestructures.LOGGER.warn("Ignoring invalid whitelistedStructures entry '{}': expected a namespaced structure pattern like 'minecraft:*'.", rawEntry);
                continue;
            }

            parsedRules.add(new MatchRule(WildcardPattern.compile(entry), order++));
        }

        parsedRules.sort(RULE_ORDER);
        return List.copyOf(parsedRules);
    }

    private static List<SpacingOverrideRule> parseOverrideRules(List<? extends String> entries) {
        List<SpacingOverrideRule> parsedRules = new ArrayList<>();
        int order = 0;

        for (String rawEntry : entries) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            int separatorIndex = entry.indexOf('=');
            if (separatorIndex < 0) {
                Bettersparsestructures.LOGGER.warn("Ignoring invalid spacingRadiusOverrides entry '{}': expected the form 'pattern=radius'.", rawEntry);
                continue;
            }

            String patternText = entry.substring(0, separatorIndex).trim();
            String radiusText = entry.substring(separatorIndex + 1).trim();
            if (!patternText.contains(":")) {
                Bettersparsestructures.LOGGER.warn("Ignoring invalid spacingRadiusOverrides entry '{}': '{}' is not a namespaced structure pattern.", rawEntry, patternText);
                continue;
            }

            int radiusChunks;
            try {
                radiusChunks = Integer.parseInt(radiusText);
            } catch (NumberFormatException exception) {
                Bettersparsestructures.LOGGER.warn("Ignoring invalid spacingRadiusOverrides entry '{}': '{}' is not an integer radius.", rawEntry, radiusText);
                continue;
            }

            if (radiusChunks < 0 || radiusChunks > ServerConfig.MAX_SPACING_RADIUS_CHUNKS) {
                Bettersparsestructures.LOGGER.warn(
                        "Ignoring invalid spacingRadiusOverrides entry '{}': radius must be between 0 and {} chunks.",
                        rawEntry,
                        ServerConfig.MAX_SPACING_RADIUS_CHUNKS
                );
                continue;
            }

            parsedRules.add(new SpacingOverrideRule(WildcardPattern.compile(patternText), order++, radiusChunks));
        }

        parsedRules.sort(RULE_ORDER);
        return List.copyOf(parsedRules);
    }

    private static class MatchRule {
        private final WildcardPattern pattern;
        private final int order;

        private MatchRule(WildcardPattern pattern, int order) {
            this.pattern = pattern;
            this.order = order;
        }

        public boolean matches(String structureId) {
            return pattern.matches(structureId);
        }

        public int specificity() {
            return pattern.specificity();
        }

        public int order() {
            return order;
        }
    }

    private static final class SpacingOverrideRule extends MatchRule {
        private final int radiusChunks;

        private SpacingOverrideRule(WildcardPattern pattern, int order, int radiusChunks) {
            super(pattern, order);
            this.radiusChunks = radiusChunks;
        }

        public int radiusChunks() {
            return radiusChunks;
        }
    }

    private record WildcardPattern(Pattern regex, int specificity) {
        private static final String REGEX_SPECIALS = "\\.[]{}()+-^$|";

        public static WildcardPattern compile(String sourcePattern) {
            StringBuilder regexBuilder = new StringBuilder("^");
            int specificity = 0;

            for (int index = 0; index < sourcePattern.length(); index++) {
                char character = sourcePattern.charAt(index);
                if (character == '*') {
                    regexBuilder.append(".*");
                    continue;
                }

                if (character == '?') {
                    regexBuilder.append('.');
                    continue;
                }

                if (REGEX_SPECIALS.indexOf(character) >= 0) {
                    regexBuilder.append('\\');
                }

                regexBuilder.append(character);
                specificity++;
            }

            regexBuilder.append('$');
            return new WildcardPattern(Pattern.compile(regexBuilder.toString()), specificity);
        }

        public boolean matches(String structureId) {
            return regex.matcher(structureId).matches();
        }
    }
}
