package org.thesis.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.thesis.ai.AiClient;
import org.thesis.refinery.RefineryValidator;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BenchmarkRunner {
    private final AiClient aiClient = new AiClient();
    private final RefineryValidator validator = new RefineryValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    private final int maxRetries;
    private final int runsPerReq;
    private final int maxModelsToExtract = 10;

    private Map<String, RequirementGroundTruth> groundTruth = Map.of();

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
            csvWriter.println("Req_ID,Difficulty,Run_Index,Success,Iterations,Generated_Models,Total_Tokens,"
                    + "Total_Items,Covered_Items,Object_Coverage_Pct,Contributing_Models,"
                    + "Covered_Item_Ids,Error_Log");

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
            int generatedModelCount = 0;
            StringBuilder errorLog = new StringBuilder();
            int totalTokens = 0;

            ObjectCoverageCalculator.SuiteCoverage coverage =
                    ObjectCoverageCalculator.evaluate(gt, List.of());

            for (iter = 1; iter <= maxRetries && !isValid; iter++) {
                try {
                    // LLM hivas
                    String aiResponse = aiClient.getTestStrategy(requirementText, metaModel, lastError);
                    JsonNode rootNode = mapper.readTree(aiResponse);
                    if (rootNode.has("error")) {
                        throw new IllegalStateException("AI bridge error: " + rootNode.get("error").asText());
                    }
                    String refineryCode = rootNode.get("missingRefineryCode").asText();

                    if (rootNode.has("usage") && rootNode.get("usage").has("total_tokens")) {
                        totalTokens = rootNode.get("usage").get("total_tokens").asInt();
                    }

                    // Fajl osszeallitasa: metamodell + a generalt kenyszerek
                    String targetPath = "src/main/resources/final.problem";
                    Files.writeString(Paths.get(targetPath), metaModel + "\n" + refineryCode);

                    List<Set<String>> extractedModels =
                            validator.validateAndExtractModels(targetPath, maxModelsToExtract);
                    isValid = true;
                    generatedModelCount = extractedModels.size();

                    // A teszt-keszlet a legeneralt peldanymodellek halmaza; a lefedettseg a kezzel
                    // levezetett kotelezettsegek UNIOJA, amit legalabb egy modell tanusit.
                    coverage = ObjectCoverageCalculator.evaluate(gt, extractedModels);

                    System.out.printf(Locale.US,
                            "    [Iter %d] SIKERES validacio. Grafok: %d | Object coverage: %d/%d (%.1f%%)%n",
                            iter, generatedModelCount, coverage.coveredItems(), coverage.totalItems(),
                            coverage.coveragePct());
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

            csvWriter.printf(Locale.US, "%s,%s,%d,%b,%d,%d,%d,%d,%d,%.2f,%d,%s,%s%n",
                    reqId, difficulty, runIndex, isValid, (iter - 1), generatedModelCount, totalTokens,
                    coverage.totalItems(), coverage.coveredItems(), coverage.coveragePct(),
                    coverage.contributingModels(),
                    coverage.coveredIds().isEmpty() ? "-" : String.join("|", coverage.coveredIds()),
                    finalErrors);
            csvWriter.flush();
        }
    }
}
