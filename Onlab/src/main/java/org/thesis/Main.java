package org.thesis;

import org.thesis.benchmark.BenchmarkRunner;
import org.thesis.benchmark.CoverageComparator;
import org.thesis.benchmark.ManualSuiteRunner;

public class Main {

    private static final String META_MODEL = "src/main/resources/temp.problem";
    private static final String GROUND_TRUTH = "src/main/resources/coverage/ground_truth.json";
    private static final String DATASET = "src/main/resources/benchmark_requirements.json";
    private static final String MANUAL_CSV = "manual_coverage.csv";
    private static final String MANUAL_REPORT = "ManualCoverageReport.md";
    private static final String BENCHMARK_CSV = "benchmark_results.csv";
    private static final String COMPARISON_CSV = "coverage_comparison.csv";

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0].toLowerCase() : "manual";

        switch (mode) {
            case "manual" -> runManual();
            case "benchmark" -> runBenchmark();
            case "compare" -> runCompare();
            case "all" -> {
                runManual();
                runBenchmark();
                runCompare();
            }
            default -> {
                System.out.println("""
                        Hasznalat: Main <mode>
                          manual     - a kezzel irt (golden) teszt-keszletek futtatasa es lefedettsegmerese (nincs LLM hivas)
                          benchmark  - az LLM altal generalt teszt-keszletek merese (LLM hivasokkal)
                          compare    - a ket eredmeny osszevetese, korrelacio szamitasa
                          all        - mind a harom egymas utan""");
            }
        }
    }

    private static void runManual() throws Exception {
        System.out.println("### 1. lepes: kezi (golden) teszt-keszlet lefedettsege ###");
        new ManualSuiteRunner(10).run(GROUND_TRUTH, META_MODEL, MANUAL_CSV, MANUAL_REPORT);
    }

    private static void runBenchmark() throws Exception {
        System.out.println("### 2. lepes: LLM altal generalt teszt-keszletek ###");
        // 3 javitasi iteracio engedelyezett, kovetelmenyenkent 3 fuggetlen futas
        new BenchmarkRunner(3, 3).run(DATASET, META_MODEL, GROUND_TRUTH, BENCHMARK_CSV);
    }

    private static void runCompare() throws Exception {
        System.out.println("### 3. lepes: kezi vs. generalt osszevetes ###");
        new CoverageComparator().report(GROUND_TRUTH, MANUAL_CSV, BENCHMARK_CSV, COMPARISON_CSV);
    }
}
