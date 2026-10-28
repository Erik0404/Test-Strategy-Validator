package org.thesis;

import org.thesis.benchmark.BenchmarkRunner;
import org.thesis.benchmark.CoverageComparator;
import org.thesis.benchmark.ManualSuiteRunner;
import org.thesis.refinery.RefineryValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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
            case "explore" -> runExplore(args);
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
                          explore    - explore <szcenario-fajl> [modellek szama]: a metamodell + szcenario
                                       megoldasainak kiirasa, pontosan azokkal a tenyekkel, amiket a meres lat
                          all        - manual + benchmark + compare egymas utan""");
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

    private static void runExplore(String[] args) throws Exception {
        Path target = Path.of(META_MODEL);
        if (args.length > 1) {
            target = Path.of("src/main/resources/explore_run.problem");
            Files.writeString(target, Files.readString(Path.of(META_MODEL)) + "\n" + Files.readString(Path.of(args[1])));
        }
        int count = args.length > 2 ? Integer.parseInt(args[2]) : 2;

        List<Set<String>> models = new RefineryValidator().validateAndExtractModels(target.toString(), count);
        for (int i = 0; i < models.size(); i++) {
            System.out.println("\n--- Model " + (i + 1) + " ---");
            models.get(i).stream().sorted().forEach(fact -> System.out.println("  " + fact));
        }
    }
}
