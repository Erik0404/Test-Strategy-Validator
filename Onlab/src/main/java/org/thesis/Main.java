package org.thesis;

import org.thesis.benchmark.BenchmarkRunner;
import org.thesis.benchmark.CoverageComparator;
import org.thesis.benchmark.ManualSuiteRunner;
import org.thesis.refinery.RefineryValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {

    private static final String META_MODEL = "src/main/resources/temp.problem";
    private static final String GROUND_TRUTH = "src/main/resources/coverage/ground_truth.json";
    private static final String DATASET = "src/main/resources/benchmark_requirements.json";
    private static final Path RESULTS = Path.of("results");
    private static final String PLOT_SCRIPT = "analysis/run_plots.py";
    private static final String STAMP = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    private static final Pattern BENCHMARK_NAME = Pattern.compile("benchmark_results_(.+)\\.csv");

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0].toLowerCase() : "manual";
        Files.createDirectories(RESULTS);

        switch (mode) {
            case "manual" -> runManual();
            case "benchmark" -> runBenchmark();
            case "compare" -> runCompare(benchmarkArgument(args));
            case "plots" -> runPlots(benchmarkArgument(args));
            case "explore" -> runExplore(args);
            case "all" -> {
                runManual();
                Path benchmark = runBenchmark();
                runCompare(benchmark);
                runPlots(benchmark);
            }
            default -> {
                System.out.println("""
                        Hasznalat: Main <mode>
                          manual     - a kezzel irt (golden) teszt-keszletek futtatasa es lefedettsegmerese (nincs LLM hivas)
                          benchmark  - az LLM altal generalt teszt-keszletek merese (LLM hivasokkal)
                          compare    - compare [benchmark-csv]: a kezi es a generalt eredmeny osszevetese
                          plots      - plots [benchmark-csv]: szakdolgozatba illo abrak es tablazatok (Python)
                          explore    - explore <szcenario-fajl> [modellek szama]: a metamodell + szcenario
                                       megoldasainak kiirasa, pontosan azokkal a tenyekkel, amiket a meres lat
                          all        - manual + benchmark + compare + plots egymas utan
                        Minden kimenet idobelyeggel a results/ mappaba kerul; compare es plots alapbol a
                        legfrissebb benchmark_results_*.csv fajlt hasznalja.""");
            }
        }
    }

    private static Path runManual() throws Exception {
        System.out.println("### 1. lepes: kezi (golden) teszt-keszlet lefedettsege ###");
        Path csv = RESULTS.resolve("manual_coverage_" + STAMP + ".csv");
        Path report = RESULTS.resolve("ManualCoverageReport_" + STAMP + ".md");
        new ManualSuiteRunner(10).run(GROUND_TRUTH, META_MODEL, csv.toString(), report.toString());
        return csv;
    }

    private static Path runBenchmark() throws Exception {
        System.out.println("### 2. lepes: LLM altal generalt teszt-keszletek ###");
        Path csv = RESULTS.resolve("benchmark_results_" + STAMP + ".csv");
        // 3 javitasi iteracio engedelyezett, kovetelmenyenkent 5 fuggetlen futas
        new BenchmarkRunner(3, 5).run(DATASET, META_MODEL, GROUND_TRUTH, csv.toString());
        return csv;
    }

    private static void runCompare(Path benchmark) throws Exception {
        System.out.println("### 3. lepes: kezi vs. generalt osszevetes ###");
        Path output = RESULTS.resolve("coverage_comparison_" + tagOf(benchmark) + ".csv");
        new CoverageComparator().report(GROUND_TRUTH, manualCsv().toString(), benchmark.toString(), output.toString());
    }

    private static void runPlots(Path benchmark) throws Exception {
        System.out.println("### 4. lepes: abrak es tablazatok ###");
        List<String> arguments = List.of(PLOT_SCRIPT,
                "--benchmark", benchmark.toString(),
                "--manual", manualCsv().toString(),
                "--ground-truth", GROUND_TRUTH,
                "--out", RESULTS.resolve("figures_" + tagOf(benchmark)).toString());

        for (String python : List.of("py", "python", "python3")) {
            List<String> command = new ArrayList<>();
            command.add(python);
            command.addAll(arguments);
            Process process;
            try {
                process = new ProcessBuilder(command).inheritIO().start();
            } catch (IOException notInstalled) {
                continue;
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Az abrakeszito szkript hibaval allt le (kilepesi kod: " + exitCode + ").");
            }
            return;
        }
        throw new IllegalStateException("Nem talalhato Python (py / python / python3). Telepits Python 3.10+ verziot.");
    }

    private static Path benchmarkArgument(String[] args) throws IOException {
        if (args.length > 1) {
            return Path.of(args[1]);
        }
        Path latest = latest("benchmark_results_");
        if (latest == null) {
            throw new IllegalStateException("Nincs benchmark_results_*.csv a " + RESULTS + " mappaban - futtasd elobb a 'benchmark' modot.");
        }
        return latest;
    }

    private static Path manualCsv() throws Exception {
        Path latest = latest("manual_coverage_");
        return latest != null ? latest : runManual();
    }

    private static Path latest(String prefix) throws IOException {
        try (Stream<Path> files = Files.list(RESULTS)) {
            return files
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.getFileName().toString().endsWith(".csv"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElse(null);
        }
    }

    private static String tagOf(Path benchmark) {
        String name = benchmark.getFileName().toString();
        Matcher matcher = BENCHMARK_NAME.matcher(name);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return name.endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
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
