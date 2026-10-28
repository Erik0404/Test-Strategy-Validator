package org.thesis.benchmark;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CoverageComparator {

    public static final class Agreement {
        public int truePositive;
        public int falsePositive;
        public int falseNegative;
        public int trueNegative;

        public int union() {
            return truePositive + falsePositive + falseNegative;
        }

        public double jaccard() {
            return union() == 0 ? 1.0 : (double) truePositive / union();
        }

        public double recall() {
            int denominator = truePositive + falseNegative;
            return denominator == 0 ? Double.NaN : (double) truePositive / denominator;
        }

        public double precision() {
            int denominator = truePositive + falsePositive;
            return denominator == 0 ? Double.NaN : (double) truePositive / denominator;
        }

        public double f1() {
            double p = precision();
            double r = recall();
            if (Double.isNaN(p) || Double.isNaN(r) || p + r == 0) return Double.NaN;
            return 2 * p * r / (p + r);
        }

        public double cohenKappa() {
            double n = (double) truePositive + falsePositive + falseNegative + trueNegative;
            if (n == 0 || truePositive + falseNegative == 0 || falsePositive + trueNegative == 0) return Double.NaN;
            double observed = (truePositive + trueNegative) / n;
            double manualYes = (truePositive + falseNegative) / n;
            double genYes = (truePositive + falsePositive) / n;
            double expected = manualYes * genYes + (1 - manualYes) * (1 - genYes);
            if (Math.abs(1 - expected) < 1e-12) return Double.NaN;
            return (observed - expected) / (1 - expected);
        }

        public double phi() {
            double tp = truePositive, fp = falsePositive, fn = falseNegative, tn = trueNegative;
            double denominator = Math.sqrt((tp + fp) * (tp + fn) * (tn + fp) * (tn + fn));
            if (denominator < 1e-12) return Double.NaN;
            return (tp * tn - fp * fn) / denominator;
        }

        public Agreement plus(Agreement other) {
            Agreement sum = new Agreement();
            sum.truePositive = truePositive + other.truePositive;
            sum.falsePositive = falsePositive + other.falsePositive;
            sum.falseNegative = falseNegative + other.falseNegative;
            sum.trueNegative = trueNegative + other.trueNegative;
            return sum;
        }
    }

    public static Agreement compare(List<String> universe, Set<String> manual, Set<String> generated) {
        Agreement agreement = new Agreement();
        for (String item : universe) {
            boolean m = manual.contains(item);
            boolean g = generated.contains(item);
            if (m && g) agreement.truePositive++;
            else if (!m && g) agreement.falsePositive++;
            else if (m) agreement.falseNegative++;
            else agreement.trueNegative++;
        }
        return agreement;
    }

    public void report(String groundTruthPath,
                       String manualCsvPath,
                       String benchmarkCsvPath,
                       String outputCsvPath) throws Exception {
        Map<String, RequirementGroundTruth> groundTruth = RequirementGroundTruth.load(groundTruthPath);
        Map<String, Set<String>> manualCovered = readCoveredSets(manualCsvPath, "Req_ID", "Covered_Item_Ids");
        List<String[]> generatedRows = readRows(benchmarkCsvPath);
        Map<String, Integer> header = headerIndex(benchmarkCsvPath);
        // Ablacio: a megszoritasok onmagukban (tervezett szcenariok nelkul) - ha a CSV tartalmazza
        boolean hasBase = header.containsKey("Base_Covered_Item_Ids");

        Agreement pooled = new Agreement();
        Agreement pooledBase = new Agreement();
        List<Double> manualScores = new ArrayList<>();
        List<Double> generatedScores = new ArrayList<>();
        List<Double> baseManualScores = new ArrayList<>();
        List<Double> baseScores = new ArrayList<>();

        try (PrintWriter csv = new PrintWriter(new FileWriter(outputCsvPath))) {
            csv.println("Req_ID,Run_Index,Total_Items,Manual_Covered,Generated_Covered,Manual_Pct,"
                    + "Generated_Pct,Intersection,Union,Jaccard,Recall,Precision,F1,Cohen_Kappa,Phi,"
                    + "Base_Covered,Base_Recall,Missed_By_LLM");

            for (String[] row : generatedRows) {
                String reqId = row[header.get("Req_ID")];
                RequirementGroundTruth gt = groundTruth.get(reqId);
                if (gt == null) continue;

                List<String> universe = gt.items.stream().map(i -> i.id).toList();
                Set<String> manual = manualCovered.getOrDefault(reqId, Set.of());
                Set<String> generated = splitIds(row[header.get("Covered_Item_Ids")]);
                String baseCell = hasBase ? row[header.get("Base_Covered_Item_Ids")].trim() : "n/a";
                // n/a: a szcenario nelkuli (ablacios) generalas abban a futasban nem keszult el
                Set<String> base = baseCell.equals("n/a") ? null : splitIds(baseCell);

                Agreement agreement = compare(universe, manual, generated);
                pooled = pooled.plus(agreement);
                Agreement baseAgreement = null;
                if (base != null) {
                    baseAgreement = compare(universe, manual, base);
                    pooledBase = pooledBase.plus(baseAgreement);
                    baseManualScores.add((double) manual.size());
                    baseScores.add((double) base.size());
                }

                double manualPct = 100.0 * manual.size() / universe.size();
                double generatedPct = 100.0 * generated.size() / universe.size();
                manualScores.add((double) manual.size());
                generatedScores.add((double) generated.size());

                Set<String> missed = new LinkedHashSet<>(manual);
                missed.removeAll(generated);

                csv.printf(Locale.US,
                        "%s,%s,%d,%d,%d,%.2f,%.2f,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s,%s,%s%n",
                        reqId,
                        row[header.get("Run_Index")],
                        universe.size(),
                        manual.size(),
                        generated.size(),
                        manualPct,
                        generatedPct,
                        agreement.truePositive,
                        agreement.union(),
                        agreement.jaccard(),
                        agreement.recall(),
                        agreement.precision(),
                        agreement.f1(),
                        agreement.cohenKappa(),
                        agreement.phi(),
                        base == null ? "n/a" : String.valueOf(base.size()),
                        baseAgreement == null ? "n/a" : String.format(Locale.US, "%.4f", baseAgreement.recall()),
                        missed.isEmpty() ? "-" : String.join("|", missed));
            }
        }

        double[] x = manualScores.stream().mapToDouble(Double::doubleValue).toArray();
        double[] y = generatedScores.stream().mapToDouble(Double::doubleValue).toArray();
        double[] xBase = baseManualScores.stream().mapToDouble(Double::doubleValue).toArray();
        double[] yBase = baseScores.stream().mapToDouble(Double::doubleValue).toArray();
        hasBase = hasBase && yBase.length > 0;

        System.out.println("\n================ Manual vs. generated coverage ================");
        System.out.printf(Locale.US, "Compared runs: %d%s%n%n", x.length,
                hasBase ? " (solver-only ablation available for " + yBase.length + ")" : "");
        System.out.printf("%-32s %16s %16s%n", "", "generated suite", hasBase ? "solver only" : "");
        printRow("Pooled TP / FN", pooled.truePositive + " / " + pooled.falseNegative,
                hasBase ? pooledBase.truePositive + " / " + pooledBase.falseNegative : "");
        printRow("Pooled recall (vs. manual)", fmt(pooled.recall()), hasBase ? fmt(pooledBase.recall()) : "");
        printRow("Pooled Jaccard", fmt(pooled.jaccard()), hasBase ? fmt(pooledBase.jaccard()) : "");
        printRow("Pooled precision", fmt(pooled.precision()), hasBase ? fmt(pooledBase.precision()) : "");
        printRow("Pooled Cohen's kappa", fmt(pooled.cohenKappa()), hasBase ? fmt(pooledBase.cohenKappa()) : "");
        printRow("Pooled phi (MCC)", fmt(pooled.phi()), hasBase ? fmt(pooledBase.phi()) : "");
        printRow("Pearson r (covered counts)", fmt(pearson(x, y)), hasBase ? fmt(pearson(xBase, yBase)) : "");
        printRow("Spearman rho (covered counts)", fmt(spearman(x, y)), hasBase ? fmt(spearman(xBase, yBase)) : "");
        if (pooled.falsePositive + pooled.trueNegative == 0) {
            System.out.println("\nn/a: the manual suite covers every item, so its coverage vector is constant and "
                    + "phi / kappa are undefined.\n     Use recall and the Pearson / Spearman correlation across "
                    + "requirements instead.");
        }
        System.out.println("Detailed per-run table written to " + outputCsvPath);
    }

    private static void printRow(String label, String llm, String base) {
        System.out.printf("%-32s %16s %16s%n", label, llm, base);
    }

    private static String fmt(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.US, "%.4f", value);
    }

    public static double pearson(double[] x, double[] y) {
        int n = Math.min(x.length, y.length);
        if (n < 2) return Double.NaN;
        double meanX = Arrays.stream(x, 0, n).average().orElse(0);
        double meanY = Arrays.stream(y, 0, n).average().orElse(0);
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            sxy += dx * dy;
            sxx += dx * dx;
            syy += dy * dy;
        }
        if (sxx < 1e-12 || syy < 1e-12) return Double.NaN; // one of the series is constant
        return sxy / Math.sqrt(sxx * syy);
    }

    public static double spearman(double[] x, double[] y) {
        return pearson(rank(x), rank(y));
    }

    private static double[] rank(double[] values) {
        int n = values.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(values[a], values[b]));
        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && values[order[j + 1]] == values[order[i]]) j++;
            double averageRank = (i + j) / 2.0 + 1;
            for (int k = i; k <= j; k++) ranks[order[k]] = averageRank;
            i = j + 1;
        }
        return ranks;
    }

    private static Set<String> splitIds(String cell) {
        Set<String> ids = new LinkedHashSet<>();
        if (cell == null) return ids;
        for (String part : cell.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !trimmed.equals("-")) ids.add(trimmed);
        }
        return ids;
    }

    private static Map<String, Integer> headerIndex(String csvPath) throws Exception {
        String first = Files.readAllLines(Path.of(csvPath)).get(0);
        String[] columns = first.split(",", -1);
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < columns.length; i++) index.put(columns[i].trim(), i);
        return index;
    }

    private static List<String[]> readRows(String csvPath) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(csvPath));
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            rows.add(lines.get(i).split(",", -1));
        }
        return rows;
    }

    private static Map<String, Set<String>> readCoveredSets(String csvPath,
                                                            String keyColumn,
                                                            String valueColumn) throws Exception {
        Map<String, Integer> header = headerIndex(csvPath);
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String[] row : readRows(csvPath)) {
            result.put(row[header.get(keyColumn)], splitIds(row[header.get(valueColumn)]));
        }
        return result;
    }
}
