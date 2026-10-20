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

/**
 * Compares the coverage reached by the LLM-generated test suites with the coverage reached by the
 * hand-written reference suites.
 *
 * <p>Two families of numbers are produced:</p>
 * <ul>
 *   <li><b>Item-level agreement</b> (Jaccard, recall, precision, Cohen's kappa and the phi /
 *       Matthews correlation coefficient). Both suites are turned into a binary vector over the
 *       same ground-truth item universe, so phi is literally the correlation between "the engineer
 *       covered this obligation" and "the generator covered this obligation".</li>
 *   <li><b>Score-level correlation</b> (Pearson and Spearman) between the manual and the generated
 *       covered-item counts across all requirement runs.</li>
 * </ul>
 */
public class CoverageComparator {

    public static final class Agreement {
        public int truePositive;   // covered by both
        public int falsePositive;  // covered only by the generated suite
        public int falseNegative;  // covered only by the manual suite
        public int trueNegative;   // covered by neither

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

        /** Cohen's kappa: agreement corrected for the agreement expected by chance. */
        public double cohenKappa() {
            double n = truePositive + falsePositive + falseNegative + trueNegative;
            if (n == 0) return Double.NaN;
            double observed = (truePositive + trueNegative) / n;
            double manualYes = (truePositive + falseNegative) / n;
            double genYes = (truePositive + falsePositive) / n;
            double expected = manualYes * genYes + (1 - manualYes) * (1 - genYes);
            if (Math.abs(1 - expected) < 1e-12) return Double.NaN;
            return (observed - expected) / (1 - expected);
        }

        /** Phi / Matthews correlation coefficient over the two binary coverage vectors. */
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

        Agreement pooled = new Agreement();
        List<Double> manualScores = new ArrayList<>();
        List<Double> generatedScores = new ArrayList<>();

        try (PrintWriter csv = new PrintWriter(new FileWriter(outputCsvPath))) {
            csv.println("Req_ID,Run_Index,Total_Items,Manual_Covered,Generated_Covered,Manual_Pct,"
                    + "Generated_Pct,Intersection,Union,Jaccard,Recall,Precision,F1,Cohen_Kappa,Phi,Missed_By_LLM");

            Map<String, Integer> header = headerIndex(benchmarkCsvPath);
            for (String[] row : generatedRows) {
                String reqId = row[header.get("Req_ID")];
                RequirementGroundTruth gt = groundTruth.get(reqId);
                if (gt == null) continue;

                List<String> universe = gt.items.stream().map(i -> i.id).toList();
                Set<String> manual = manualCovered.getOrDefault(reqId, Set.of());
                Set<String> generated = splitIds(row[header.get("Covered_Item_Ids")]);

                Agreement agreement = compare(universe, manual, generated);
                pooled = pooled.plus(agreement);

                double manualPct = 100.0 * manual.size() / universe.size();
                double generatedPct = 100.0 * generated.size() / universe.size();
                manualScores.add((double) manual.size());
                generatedScores.add((double) generated.size());

                Set<String> missed = new LinkedHashSet<>(manual);
                missed.removeAll(generated);

                csv.printf(Locale.US,
                        "%s,%s,%d,%d,%d,%.2f,%.2f,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s%n",
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
                        missed.isEmpty() ? "-" : String.join("|", missed));
            }
        }

        double[] x = manualScores.stream().mapToDouble(Double::doubleValue).toArray();
        double[] y = generatedScores.stream().mapToDouble(Double::doubleValue).toArray();

        System.out.println("\n================ Manual vs. generated coverage ================");
        System.out.printf(Locale.US, "Compared runs                 : %d%n", x.length);
        System.out.printf(Locale.US, "Pooled item-level agreement   : TP=%d FP=%d FN=%d TN=%d%n",
                pooled.truePositive, pooled.falsePositive, pooled.falseNegative, pooled.trueNegative);
        System.out.printf(Locale.US, "Pooled Jaccard                : %.4f%n", pooled.jaccard());
        System.out.printf(Locale.US, "Pooled recall (vs. manual)    : %.4f%n", pooled.recall());
        System.out.printf(Locale.US, "Pooled precision              : %.4f%n", pooled.precision());
        System.out.printf(Locale.US, "Pooled Cohen's kappa          : %.4f%n", pooled.cohenKappa());
        System.out.printf(Locale.US, "Pooled phi (MCC) correlation  : %.4f%n", pooled.phi());
        System.out.printf(Locale.US, "Pearson r (covered counts)    : %.4f%n", pearson(x, y));
        System.out.printf(Locale.US, "Spearman rho (covered counts) : %.4f%n", spearman(x, y));
        System.out.println("Detailed per-run table written to " + outputCsvPath);
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
