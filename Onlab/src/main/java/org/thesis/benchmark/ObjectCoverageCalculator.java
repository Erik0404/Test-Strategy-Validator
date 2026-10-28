package org.thesis.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ObjectCoverageCalculator {

    private static final Pattern FACT_PATTERN =
            Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)\\s*$");

    private ObjectCoverageCalculator() {
    }

    public static final class SuiteCoverage {
        public String reqId;
        public int suiteSize;
        public List<String> itemIds = new ArrayList<>();
        public boolean[] covered = new boolean[0];
        public List<boolean[]> perModel = new ArrayList<>();

        public int totalItems() {
            return itemIds.size();
        }

        public int coveredItems() {
            int n = 0;
            for (boolean b : covered) {
                if (b) n++;
            }
            return n;
        }

        public double coveragePct() {
            return totalItems() == 0 ? 0.0 : 100.0 * coveredItems() / totalItems();
        }

        public Set<String> coveredIds() {
            Set<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < itemIds.size(); i++) {
                if (covered[i]) ids.add(itemIds.get(i));
            }
            return ids;
        }

        public Set<String> uncoveredIds() {
            Set<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < itemIds.size(); i++) {
                if (!covered[i]) ids.add(itemIds.get(i));
            }
            return ids;
        }

        public int contributingModels() {
            boolean[] acc = new boolean[itemIds.size()];
            int contributing = 0;
            for (boolean[] vector : perModel) {
                boolean adds = false;
                for (int i = 0; i < acc.length; i++) {
                    if (vector[i] && !acc[i]) {
                        acc[i] = true;
                        adds = true;
                    }
                }
                if (adds) contributing++;
            }
            return contributing;
        }
    }

    public static SuiteCoverage evaluate(RequirementGroundTruth groundTruth, List<Set<String>> suite) {
        SuiteCoverage result = new SuiteCoverage();
        result.reqId = groundTruth.reqId;
        List<CoverageItem> items = groundTruth.items == null ? List.of() : groundTruth.items;
        for (CoverageItem item : items) {
            result.itemIds.add(item.id);
        }
        result.covered = new boolean[items.size()];
        if (suite == null || suite.isEmpty()) {
            return result;
        }
        result.suiteSize = suite.size();

        for (Set<String> model : suite) {
            ParsedModel parsed = ParsedModel.of(model);
            boolean[] vector = new boolean[items.size()];
            for (int i = 0; i < items.size(); i++) {
                CoverageItem item = items.get(i);
                boolean applicable = item.guardType == null || !parsed.nodesOfType(item.guardType).isEmpty();
                boolean holds = patternHolds(item, parsed);
                // A negated item is witnessed by a model in which the pattern does NOT hold.
                vector[i] = applicable && (item.negated != holds);
                if (vector[i]) {
                    result.covered[i] = true;
                }
            }
            result.perModel.add(vector);
        }
        return result;
    }

    private static boolean patternHolds(CoverageItem item, ParsedModel model) {
        if (item.kind == CoverageItem.Kind.OBJECT) {
            return !model.nodesOfType(item.type).isEmpty();
        }
        Set<String> sources = item.source != null ? Set.of(item.source) : model.nodesOfType(item.sourceType);
        for (String source : sources) {
            if (linksToMatchingTarget(item, model, model.targetsOf(item.relation, source))) {
                return true;
            }
        }
        return false;
    }

    private static boolean linksToMatchingTarget(CoverageItem item, ParsedModel model, Set<String> targets) {
        if (item.target != null && !item.target.isBlank()) {
            return targets.contains(item.target);
        }
        for (String candidate : targets) {
            if (model.nodesOfType(item.type).contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static final class ParsedModel {
        private final Map<String, Set<String>> typeIndex = new HashMap<>();
        private final Map<String, Set<String>> binaryIndex = new HashMap<>();

        static ParsedModel of(Set<String> facts) {
            ParsedModel parsed = new ParsedModel();
            for (String fact : facts) {
                Matcher matcher = FACT_PATTERN.matcher(fact);
                if (!matcher.matches()) continue;
                String name = matcher.group(1);
                String[] args = matcher.group(2).split("\\s*,\\s*");
                if (args.length == 1 && !args[0].isBlank()) {
                    parsed.typeIndex.computeIfAbsent(name, k -> new HashSet<>()).add(args[0].trim());
                } else if (args.length == 2) {
                    parsed.binaryIndex
                            .computeIfAbsent(name + "|" + args[0].trim(), k -> new HashSet<>())
                            .add(args[1].trim());
                }
            }
            return parsed;
        }

        Set<String> nodesOfType(String type) {
            return typeIndex.getOrDefault(type, Collections.emptySet());
        }

        Set<String> targetsOf(String relation, String source) {
            return binaryIndex.getOrDefault(relation + "|" + source, Collections.emptySet());
        }
    }
}
