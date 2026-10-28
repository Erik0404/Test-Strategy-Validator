package org.thesis.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.thesis.ai.AiClient;
import org.thesis.refinery.RefineryValidator;
import tools.refinery.generator.GeneratorTimeoutException;
import tools.refinery.generator.UnsatisfiableProblemException;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BenchmarkRunner {
    private static final String CONSTRAINTS_PATH = "src/main/resources/final.problem";
    private static final String SCENARIO_PATH = "src/main/resources/final_scenario.problem";

    private final AiClient aiClient = new AiClient();
    private final RefineryValidator validator = new RefineryValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    private final int maxRetries;
    private final int runsPerReq;
    private final int maxModelsToExtract = 10;
    private final int baseModelsToExtract = 5;

    private Map<String, RequirementGroundTruth> groundTruth = Map.of();

    private record ValidatedAnswer(List<Set<String>> baseModels, List<Set<String>> suite, int scenarioCount) {
    }

    public BenchmarkRunner(int maxRetries, int runsPerReq) {
        this.maxRetries = maxRetries;
        this.runsPerReq = runsPerReq;
    }

    public void run(String datasetPath, String metaModelPath, String groundTruthPath, String outputCsvPath)
            throws Exception {
        String jsonDataset = Files.readString(Paths.get(datasetPath));
        List<Map<String, String>> requirements = mapper.readValue(jsonDataset, new TypeReference<>() {});
        String metaModel = Files.readString(Path.of(metaModelPath));
        groundTruth = RequirementGroundTruth.load(groundTruthPath);

        try (PrintWriter csvWriter = new PrintWriter(new FileWriter(outputCsvPath))) {
            csvWriter.println("Req_ID,Difficulty,Run_Index,Success,Iterations,Scenarios,Generated_Models,Total_Tokens,"
                    + "Total_Items,Base_Covered_Items,Base_Coverage_Pct,Covered_Items,Object_Coverage_Pct,"
                    + "Contributing_Models,Base_Covered_Item_Ids,Covered_Item_Ids,Error_Log");

            for (Map<String, String> reqData : requirements) {
                runSingleRequirementBenchmark(reqData, metaModel, csvWriter);
            }
        }
        System.out.println("Benchmark kesz! Eredmenyek kimentve: " + outputCsvPath);
    }

    private void runSingleRequirementBenchmark(Map<String, String> reqData, String metaModel, PrintWriter csvWriter) {
        String reqId = reqData.get("id");
        String difficulty = reqData.get("difficulty");
        String requirementText = reqData.get("text");
        RequirementGroundTruth gt = groundTruth.get(reqId);
        if (gt == null) {
            System.out.println("[!] Nincs ground truth bejegyzes ehhez: " + reqId + " - kihagyva.");
            return;
        }
        System.out.println("\n=== Teszteles: " + reqId + " [" + difficulty + "] ===");

        for (int runIndex = 1; runIndex <= runsPerReq; runIndex++) {
            System.out.println(" -> Futas #" + runIndex);

            String lastError = null;
            boolean isValid = false;
            int iter;
            int scenarioCount = 0;
            int generatedModelCount = 0;
            StringBuilder errorLog = new StringBuilder();
            int totalTokens = 0;

            ObjectCoverageCalculator.SuiteCoverage coverage =
                    ObjectCoverageCalculator.evaluate(gt, List.of());
            ObjectCoverageCalculator.SuiteCoverage baseCoverage = null;

            for (iter = 1; iter <= maxRetries && !isValid; iter++) {
                try {
                    // LLM hivas
                    String aiResponse = aiClient.getTestStrategy(requirementText, metaModel, lastError);
                    JsonNode rootNode = mapper.readTree(aiResponse);
                    if (rootNode.has("error")) {
                        throw new IllegalStateException("AI bridge error: " + rootNode.get("error").asText());
                    }
                    // Az osszes iteracio tokenje szamit, nem csak az utolsoe
                    totalTokens += rootNode.path("usage").path("total_tokens").asInt(0);

                    ValidatedAnswer answer = validateAnswer(rootNode, metaModel);
                    isValid = true;
                    scenarioCount = answer.scenarioCount();
                    generatedModelCount = answer.suite().size();

                    // A teszt-keszlet az LLM altal tervezett szcenariok peldanymodelljeinek unioja.
                    // Ablacio: a megszoritasok onmagukban, tervezett szcenariok nelkul (a solver szabad modelljei).
                    coverage = ObjectCoverageCalculator.evaluate(gt, answer.suite());
                    baseCoverage = answer.baseModels() == null
                            ? null : ObjectCoverageCalculator.evaluate(gt, answer.baseModels());

                    System.out.printf(Locale.US,
                            "    [Iter %d] SIKERES validacio. Szcenariok: %d | Grafok: %d | Object coverage: %d/%d (%.1f%%)"
                                    + " | csak solver: %s%n",
                            iter, scenarioCount, generatedModelCount, coverage.coveredItems(), coverage.totalItems(),
                            coverage.coveragePct(),
                            baseCoverage == null ? "n/a" : baseCoverage.coveredItems() + "/" + baseCoverage.totalItems());
                    if (!coverage.uncoveredIds().isEmpty()) {
                        System.out.println("      Lefedetlen: " + String.join(", ", coverage.uncoveredIds()));
                    }

                } catch (Exception e) {
                    String cleanError = String.valueOf(e.getMessage()).replace("\n", " ").replace(",", ";");
                    System.out.println("    [Iter " + iter + "] HIBA: " + cleanError);
                    errorLog.append("[").append(cleanError).append("] ");
                    lastError = cleanError;
                }
            }

            String finalErrors = errorLog.toString().isEmpty() ? "None" : errorLog.toString();
            String baseCovered = baseCoverage == null ? "n/a" : String.valueOf(baseCoverage.coveredItems());
            String basePct = baseCoverage == null ? "n/a" : String.format(Locale.US, "%.2f", baseCoverage.coveragePct());
            String baseIds = baseCoverage == null ? "n/a" : joinIds(baseCoverage.coveredIds());

            csvWriter.printf(Locale.US, "%s,%s,%d,%b,%d,%d,%d,%d,%d,%s,%s,%d,%.2f,%d,%s,%s,%s%n",
                    reqId, difficulty, runIndex, isValid, (iter - 1), scenarioCount, generatedModelCount, totalTokens,
                    coverage.totalItems(), baseCovered, basePct,
                    coverage.coveredItems(), coverage.coveragePct(), coverage.contributingModels(),
                    baseIds, joinIds(coverage.coveredIds()), finalErrors);
            csvWriter.flush();
        }
    }

    private ValidatedAnswer validateAnswer(JsonNode root, String metaModel) throws Exception {
        String refineryCode = root.path("missingRefineryCode").asText("");
        if (refineryCode.isBlank()) {
            throw new IllegalStateException("missingRefineryCode is empty.");
        }

        // 1) Gyors szintaktikai / nevfeloldasi ellenorzes a generalt megszoritasokra (generalas nelkul).
        Files.writeString(Paths.get(CONSTRAINTS_PATH), metaModel + "\n" + refineryCode);
        validator.checkSyntax(CONSTRAINTS_PATH);

        // 2) Minden tesztszcenariot kulon oldunk meg a metamodell + a megszoritasok tetejen.
        //    Az osszes elutasitott szcenariot egyszerre jelezzuk vissza, hogy egy javitasi kor eleg legyen.
        JsonNode scenarios = root.path("testScenarios");
        if (!scenarios.isArray() || scenarios.isEmpty()) {
            throw new IllegalStateException("The answer contained no testScenarios.");
        }
        List<Set<String>> suite = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        int index = 0;
        for (JsonNode scenario : scenarios) {
            index++;
            String id = scenario.path("id").asText("TS" + index);
            String facts = scenario.path("refineryFacts").asText("");
            if (facts.isBlank()) {
                rejected.add("Test scenario " + id + " has no refineryFacts.");
                continue;
            }
            Files.writeString(Paths.get(SCENARIO_PATH), metaModel + "\n" + refineryCode + "\n" + facts);
            try {
                List<Set<String>> models = validator.validateAndExtractModels(SCENARIO_PATH, maxModelsToExtract);
                System.out.printf("      %s %s -> %d modell%n", id, scenario.path("covers"), models.size());
                suite.addAll(models);
            } catch (UnsatisfiableProblemException e) {
                rejected.add("Test scenario " + id + " is UNSATISFIABLE.");
            } catch (GeneratorTimeoutException e) {
                rejected.add("Test scenario " + id + " TIMED OUT without a solution.");
            } catch (Exception e) {
                rejected.add("Test scenario " + id + " rejected: " + e.getMessage());
            }
        }
        if (!rejected.isEmpty()) {
            throw new IllegalStateException(String.join(" ", rejected));
        }
        return new ValidatedAnswer(solverOnlyModels(), suite, scenarios.size());
    }

    private List<Set<String>> solverOnlyModels() {
        try {
            // Ablacio (best effort): a megszoritasok szcenario nelkul, ahogy az elso benchmark-futasban.
            // A szabad keresest a solver neha nem fejezi be idoben; ez a futas sikeret nem befolyasolja.
            return validator.validateAndExtractModels(CONSTRAINTS_PATH, baseModelsToExtract);
        } catch (Exception e) {
            System.out.println("      (ablacio: szcenario nelkuli generalas sikertelen - " + e.getMessage() + ")");
            return null;
        }
    }

    private static String joinIds(Set<String> ids) {
        return ids.isEmpty() ? "-" : String.join("|", ids);
    }
}
