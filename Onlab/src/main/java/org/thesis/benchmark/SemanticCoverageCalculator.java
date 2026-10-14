package org.thesis.benchmark;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.thesis.TestStrategyGenerator.TestStrategyGenerator.Element;
import org.thesis.TestStrategyGenerator.TestStrategyGenerator.Rule;
import org.thesis.TestStrategyGenerator.TestStrategyGenerator.StrategyData;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SemanticCoverageCalculator {

    private static final JexlEngine jexl = new JexlBuilder().cache(512).strict(true).silent(true).create();
    public static boolean DEBUG_MODE = true;

    private static final Set<String> STATIC_RELATIONS = Set.of(
            "train", "trainmanagementcomponent", "passengerinformationcomponent",
            "operationscreen", "port", "tmcport", "picport", "screenport",
            "nearobc", "farobc", "node", "component",
            "trainevent", "trainspeed", "traindoors"
    );

    public static class CoverageResult {
        public int totalModels;
        public int coveredModels;
        public int totalRelevantObjects;
        public int coveredRelevantObjects;

        public double getModelCoveragePct() {
            return totalModels == 0 ? 0 : ((double) coveredModels / totalModels) * 100;
        }

        public double getObjectCoveragePct() {
            return totalRelevantObjects == 0 ? 0 : ((double) coveredRelevantObjects / totalRelevantObjects) * 100;
        }
    }

    public static CoverageResult calculateCoverage(StrategyData strategy, List<Set<String>> extractedModels) {
        CoverageResult result = new CoverageResult();
        if (extractedModels == null || extractedModels.isEmpty() || strategy == null) return result;
        result.totalModels = extractedModels.size();

        for (Set<String> modelFacts : extractedModels) {
            MapContext context = new MapContext();
            for (Element cond : strategy.conditions) {
                context.set(cond.id, isMatchInModel(cond, modelFacts));
            }

            Set<String> triggeredExpectationIds = new HashSet<>();
            for (Rule r : strategy.rules) {
                if (evaluateLogic(r.logic, context)) triggeredExpectationIds.addAll(r.result_ids);
            }

            boolean modelFullyCovered = true;
            boolean ruleTriggered = !triggeredExpectationIds.isEmpty();

            for (Element exp : strategy.expectations) {
                boolean existsInModel = isMatchInModel(exp, modelFacts);
                boolean triggeredByRule = triggeredExpectationIds.contains(exp.id);

                if (existsInModel) {
                    result.totalRelevantObjects++;
                    if (triggeredByRule) result.coveredRelevantObjects++;
                    else modelFullyCovered = false;
                } else {
                    if (triggeredByRule) modelFullyCovered = false;
                }
            }
            if (modelFullyCovered && ruleTriggered) result.coveredModels++;
        }
        return result;
    }

    private static boolean isMatchInModel(Element element, Set<String> modelFacts) {
        if (element == null || element.source_text == null || element.source_text.trim().isEmpty()) {
            return false;
        }

        // LLM szöveg szóközök nélkül, kisbetűsítve (pl. "Zero Speed" -> "zerospeed")
        String sourceNoSpace = element.source_text.replace(" ", "").toLowerCase();

        for (String fact : modelFacts) {
            int bracketIndex = fact.indexOf('(');
            if (bracketIndex == -1) continue;

            // Datalog reláció neve kisbetűsítve (pl. "ZeroSpeed(t1)" -> "zerospeed", "trainEvent(t1)" -> "trainevent")
            String relationName = fact.substring(0, bracketIndex).toLowerCase();

            // 1. Feketelista: Hagyjuk figyelmen kívül a statikus alapobjektumokat!
            if (STATIC_RELATIONS.contains(relationName)) {
                continue;
            }

            // 2. Tiszta, szigorú egyezés: A teljes relációnévnek benne kell lennie a mondatban
            // Így a "trainevent" soha többé nem fog illeszkedni a "trainspeed"-re!
            if (sourceNoSpace.contains(relationName)) {
                if (DEBUG_MODE) System.out.println("      [Match] " + element.id + " (Source: '" + element.source_text + "') -> " + fact);
                return true;
            }
        }
        return false;
    }

    private static boolean evaluateLogic(String logicText, MapContext context) {
        try {
            String formattedLogic = logicText
                    .replaceAll("(?i)\\bAND\\b", "&&")
                    .replaceAll("(?i)\\bOR\\b", "||")
                    .replaceAll("(?i)\\bNOT\\b", "!");
            JexlExpression e = jexl.createExpression(formattedLogic);
            return Boolean.TRUE.equals(e.evaluate(context));
        } catch (Exception ex) {
            return false;
        }
    }
}