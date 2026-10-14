package org.thesis.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.thesis.ai.AiClient;
import org.thesis.refinery.RefineryValidator;
import org.thesis.TestStrategyGenerator.TestStrategyGenerator;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BenchmarkRunner {
    private final AiClient aiClient = new AiClient();
    private final RefineryValidator validator = new RefineryValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    private final int maxRetries;
    private final int runsPerReq;
    private final int maxModelsToExtract = 10;

    public BenchmarkRunner(int maxRetries, int runsPerReq) {
        this.maxRetries = maxRetries;
        this.runsPerReq = runsPerReq;
    }

    public void run(String datasetPath, String metaModelPath, String outputCsvPath) throws Exception {
        String jsonDataset = Files.readString(Paths.get(datasetPath));
        List<Map<String, String>> requirements = mapper.readValue(jsonDataset, new TypeReference<>() {});
        String metaModel = Files.readString(Path.of(metaModelPath));

        try (PrintWriter csvWriter = new PrintWriter(new FileWriter(outputCsvPath))) {
            csvWriter.println("Req_ID,Difficulty,Run_Index,Success,Iterations,Generated_Models,Total_Tokens,Coverage_Pct,Error_Log");

            for (Map<String, String> reqData : requirements) {
                runSingleRequirementBenchmark(reqData, metaModel, csvWriter);
            }
        }
        System.out.println("Benchmark kész! Eredmények kimentve: " + outputCsvPath);
    }

    private void runSingleRequirementBenchmark(Map<String, String> reqData, String metaModel, PrintWriter csvWriter) {
        String reqId = reqData.get("id");
        String difficulty = reqData.get("difficulty");
        String requirementText = reqData.get("text");
        System.out.println("\n=== Tesztelés: " + reqId + " [" + difficulty + "] ===");

        for (int runIndex = 1; runIndex <= runsPerReq; runIndex++) {
            System.out.println(" -> Futás #" + runIndex);

            String lastError = null;
            boolean isValid = false;
            int iter;
            int generatedModelCount = 0;
            StringBuilder errorLog = new StringBuilder();
            List<Set<String>> extractedModels = null;
            int totalTokens = 0;

            double finalCoveragePct = 0.0;

            for (iter = 1; iter <= maxRetries && !isValid; iter++) {
                try {
                    // LLM Hívás
                    String aiResponse = aiClient.getTestStrategy(requirementText, metaModel, lastError);
                    JsonNode rootNode = mapper.readTree(aiResponse);
                    String refineryCode = rootNode.get("missingRefineryCode").asText();
                    String strategyJson = rootNode.get("strategyData").toString();

                    if (rootNode.has("usage") && rootNode.get("usage").has("total_tokens")) {
                        totalTokens = rootNode.get("usage").get("total_tokens").asInt();
                    }

                    // Fájl összeállítása
                    String targetPath = "src/main/resources/final.problem";
                    Files.writeString(Paths.get(targetPath), metaModel);
                    Files.writeString(Paths.get(targetPath), "\n" + refineryCode, StandardOpenOption.APPEND);

                    // Validáció és Modellek kinyerése!
                    TestStrategyGenerator.StrategyData strategyData = mapper.readValue(strategyJson, TestStrategyGenerator.StrategyData.class);

                    extractedModels = validator.validateAndExtractModels(targetPath, maxModelsToExtract);
                    isValid = true;
                    generatedModelCount = extractedModels.size();

                    SemanticCoverageCalculator.CoverageResult coverage = SemanticCoverageCalculator.calculateCoverage(strategyData, extractedModels);

                    finalCoveragePct = coverage.getObjectCoveragePct();

                    System.out.printf("    [Iter %d] SIKERES validáció. Gráfok: %d | Object Coverage: %.1f%%%n",
                            iter, generatedModelCount, finalCoveragePct);

                } catch (Exception e) {
                    String cleanError = e.getMessage().replace("\n", " ").replace(",", ";");
                    System.out.println("    [Iter " + iter + "] HIBA: " + cleanError);
                    errorLog.append("[").append(cleanError).append("] ");
                    lastError = cleanError;
                }
            }

            String finalErrors = errorLog.toString().isEmpty() ? "None" : errorLog.toString();

            csvWriter.printf(java.util.Locale.US, "%s,%s,%d,%b,%d,%d,%d,%.2f,%s\n",
                    reqId, difficulty, runIndex, isValid, (iter - 1), generatedModelCount, totalTokens, finalCoveragePct, finalErrors);
            csvWriter.flush();
        }
    }
}