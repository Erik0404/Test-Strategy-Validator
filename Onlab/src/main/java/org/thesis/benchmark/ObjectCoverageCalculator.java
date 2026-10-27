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

/**
 * Object coverage of a test suite (a set of instance models produced by Refinery)
 * with respect to the manually derived coverage obligations of a requirement.
 *
 * Key difference from the earlier, text-based metric:
 *   the denominator is fixed (the ground-truth item set), it does not grow with the number
 *       of models;
 *    coverage is the union over the suite: an item is covered if at least one
 *       model witnesses it;
 *   matching happens on Datalog facts, not on natural-language fragments.
 */
public final class ObjectCoverageCalculator {

    private static final Pattern FACT_PATTERN =
            Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)\\s*$");

    private ObjectCoverageCalculator() {
    }

    public static final class SuiteCoverage {
        public String reqId;
        public int suiteSize;
        public List<String> itemIds = new ArrayList<>();
        /** Union over the suite, aligned with {@link #itemIds}. */
        public boolean[] covered = new boolean[0];
        /** Per model coverage vectors, aligned with {@link #itemIds}. */
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

        /**
         * Number of models that cover at least one item no earlier model had covered.
         * A low value next to a large suite means the generator produced redundant tests.
         */
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
                boolean holds = patternHolds(item, parsed);
                // A negated item is witnessed by a model in which the pattern does NOT hold.
                vector[i] = item.negated != holds;
                if (vector[i]) {
                    result.covered[i] = true;
                }
            }
            result.perModel.add(vector);
        }
        return result;
    }

    /** Evaluates the positive form of the item against a single instance model. */
    private static boolean patternHolds(CoverageItem item, ParsedModel model) {
        if (item.kind == CoverageItem.Kind.OBJECT) {
            return !model.nodesOfType(item.type).isEmpty();
        }
        Set<String> targets = model.targetsOf(item.relation, item.source);
        if (targets.isEmpty()) {
            return false;
        }
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

    /** Indexed view over the raw {@code relation(arg, ...)} strings of one solution. */
    private static final class ParsedModel {
        /** class name -> nodes asserted to belong to it. */
        private final Map<String, Set<String>> typeIndex = new HashMap<>();
        /** "relation|source" -> target nodes of binary facts. */
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
