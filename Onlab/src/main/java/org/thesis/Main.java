package org.thesis;

import org.thesis.refinery.ModelExplorer;
import org.thesis.benchmark.BenchmarkRunner;

public class Main {
    public static void main(String[] args) throws Exception {

        //ModelExplorer explorer = new ModelExplorer();

        //explorer.exploreModels("src/main/resources/temp.problem", 2);

        System.out.println("Benchmark környezet indítása...");

        // 3 iteráció engedélyezett javításra, követelményenként 3-szor futtatjuk
        BenchmarkRunner runner = new BenchmarkRunner(3, 3);

        runner.run(
                "src/main/resources/benchmark_requirements.json",
                "src/main/resources/temp.problem",
                "benchmark_results.csv"
        );
    }
}