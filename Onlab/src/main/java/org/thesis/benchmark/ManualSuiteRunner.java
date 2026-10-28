package org.thesis.benchmark;

import org.thesis.refinery.RefineryValidator;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ManualSuiteRunner {

    private final RefineryValidator validator = new RefineryValidator();
    private final int maxSolutionsPerScenario;

    private final Map<String, List<Set<String>>> scenarioCache = new HashMap<>();

    public ManualSuiteRunner(int maxSolutionsPerScenario) {
        this.maxSolutionsPerScenario = maxSolutionsPerScenario;
    }

    public Map<String, ObjectCoverageCalculator.SuiteCoverage> run(String groundTruthPath,
                                                                   String metaModelPath,
                                                                   String outputCsvPath,
                                                                   String reportMdPath) throws Exception {
        Map<String, RequirementGroundTruth> groundTruth = RequirementGroundTruth.load(groundTruthPath);
        String metaModel = Files.readString(Path.of(metaModelPath));

        Map<String, ObjectCoverageCalculator.SuiteCoverage> results = new LinkedHashMap<>();
        StringBuilder report = new StringBuilder();
        report.append("# Manual (golden) test suite - object coverage\n\n");
        report.append("Every scenario below was solved by Refinery against the metamodel, ")
                .append("therefore the manual reference suite is formally validated as well.\n\n");

        try (PrintWriter csv = new PrintWriter(new FileWriter(outputCsvPath))) {
            csv.println("Req_ID,Suite_Size,Scenarios,Total_Items,Covered_Items,Coverage_Pct,"
                    + "Expected_By_Hand,Contributing_Models,Covered_Item_Ids,Uncovered_Item_Ids");

            for (RequirementGroundTruth gt : groundTruth.values()) {
                System.out.println("\n=== Manual suite: " + gt.reqId + " ===");
                List<Set<String>> suite = new ArrayList<>();

                for (String scenarioPath : gt.manualSuite) {
                    List<Set<String>> models = solveScenario(metaModel, scenarioPath);
                    System.out.printf("  %s -> %d instance model(s)%n", scenarioPath, models.size());
                    suite.addAll(models);
                }

                ObjectCoverageCalculator.SuiteCoverage coverage =
                        ObjectCoverageCalculator.evaluate(gt, suite);
                results.put(gt.reqId, coverage);

                System.out.printf(Locale.US, "  Manual object coverage: %d/%d (%.1f%%)%n",
                        coverage.coveredItems(), coverage.totalItems(), coverage.coveragePct());
                if (coverage.coveredItems() != gt.manualMaxCoverage) {
                    System.out.printf("  [!] Differs from the count derived on paper (%d). "
                            + "Re-check the ground truth or the scenarios.%n", gt.manualMaxCoverage);
                }

                csv.printf(Locale.US, "%s,%d,%d,%d,%d,%.2f,%d,%d,%s,%s%n",
                        gt.reqId,
                        coverage.suiteSize,
                        gt.manualSuite.size(),
                        coverage.totalItems(),
                        coverage.coveredItems(),
                        coverage.coveragePct(),
                        gt.manualMaxCoverage,
                        coverage.contributingModels(),
                        String.join("|", coverage.coveredIds()),
                        String.join("|", coverage.uncoveredIds()));
                csv.flush();

                appendReportSection(report, gt, coverage);
            }
        }

        Files.writeString(Path.of(reportMdPath), report.toString());
        System.out.println("\nManual coverage written to " + outputCsvPath + " and " + reportMdPath);
        return results;
    }

    private List<Set<String>> solveScenario(String metaModel, String scenarioPath) throws Exception {
        List<Set<String>> cached = scenarioCache.get(scenarioPath);
        if (cached != null) {
            return cached;
        }
        String scenario = Files.readString(Path.of(scenarioPath));
        Path combined = Path.of("src/main/resources/manual_run.problem");
        Files.writeString(combined, metaModel + "\n" + scenario);
        List<Set<String>> models =
                validator.validateAndExtractModels(combined.toString(), maxSolutionsPerScenario);
        scenarioCache.put(scenarioPath, models);
        return models;
    }

    private void appendReportSection(StringBuilder report,
                                     RequirementGroundTruth gt,
                                     ObjectCoverageCalculator.SuiteCoverage coverage) {
        report.append("## ").append(gt.reqId).append("\n\n");
        report.append("> ").append(gt.text).append("\n\n");
        report.append(String.format(Locale.US,
                "**Manual object coverage: %d / %d (%.1f%%)** over %d instance model(s) "
                        + "generated from %d hand-written scenario(s).\n\n",
                coverage.coveredItems(), coverage.totalItems(), coverage.coveragePct(),
                coverage.suiteSize, gt.manualSuite.size()));

        report.append("| Item | Pattern | Trace | Covered | Witnessing models |\n");
        report.append("| --- | --- | --- | --- | --- |\n");
        for (int i = 0; i < gt.items.size(); i++) {
            CoverageItem item = gt.items.get(i);
            List<String> witnesses = new ArrayList<>();
            for (int m = 0; m < coverage.perModel.size(); m++) {
                if (coverage.perModel.get(m)[i]) {
                    witnesses.add("M" + (m + 1));
                }
            }
            report.append("| ").append(item.id)
                    .append(" | `").append(item).append("`")
                    .append(" | ").append(item.traceTo == null ? "-" : item.traceTo)
                    .append(" | ").append(coverage.covered[i] ? "yes" : "NO")
                    .append(" | ").append(witnesses.isEmpty() ? "-" : String.join(", ", witnesses))
                    .append(" |\n");
        }
        report.append("\n");
    }
}
