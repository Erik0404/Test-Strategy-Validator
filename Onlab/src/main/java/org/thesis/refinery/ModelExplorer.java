package org.thesis.refinery;

import tools.refinery.generator.standalone.StandaloneRefinery;
import tools.refinery.language.model.problem.Problem;
import tools.refinery.language.model.problem.Assertion;
import tools.refinery.language.model.problem.NodeAssertionArgument;
import tools.refinery.language.model.problem.WildcardAssertionArgument;

import java.util.ArrayList;
import java.util.List;

public class ModelExplorer {

    public void exploreModels(String filePath, int requestedSolutions) throws Exception {
        System.out.println("Metamodell betöltése: " + filePath);

        var problemLoader = StandaloneRefinery.getProblemLoader();
        var problem = problemLoader.loadFile(filePath);

        var generatorFactory = StandaloneRefinery.getGeneratorFactory();

        try (var generator = generatorFactory.createGenerator(problem)) {

            System.out.println("Kért megoldások száma: " + requestedSolutions);
            generator.setMaxNumberOfSolutions(requestedSolutions);
            generator.generate();

            int found = generator.getSolutionCount();
            System.out.println("Ténylegesen legenerált (talált) modellek száma: " + found);

            for (int i = 0; i < found; i++) {
                // Betöltjük az i-edik megoldást az aktív memóriába
                generator.loadSolution(i);
                System.out.println("\n=============================================");
                System.out.println("--- " + (i + 1) + ". Modell Állapota ---");

                // Szerializáljuk az aktív memóriát egy Problem objektumba
                var solutionProblemOpt = generator.trySerialize();

                if (solutionProblemOpt.isPresent()) {
                    Problem solutionProblem = solutionProblemOpt.get();

                    System.out.println("Modell tényei (Gráf állapota):");

                    solutionProblem.getStatements().forEach(stmt -> {
                        if (stmt instanceof Assertion assertion) {

                            String relationName = assertion.getRelation().getName();

                            // Lista az argumentumok (csomópontok) neveinek
                            List<String> argNames = new ArrayList<>();

                            for (var arg : assertion.getArguments()) {
                                if (arg instanceof NodeAssertionArgument nodeArg) {
                                    // Kinyerjük a csomópont tényleges nevét!
                                    argNames.add(nodeArg.getNode().getName());
                                } else if (arg instanceof WildcardAssertionArgument) {
                                    // Ez a '*' vagy '_' karaktert jelenti
                                    argNames.add("*");
                                } else {
                                    argNames.add(arg.toString());
                                }
                            }

                            String arguments = String.join(", ", argNames);

                            // Kiszűrjük a "mindenkire vonatkozó" default szabályokat
                            if (!arguments.contains("*")) {
                                System.out.println("  " + relationName + "(" + arguments + ")");
                            }
                        }
                    });

                } else {
                    System.out.println("Nem sikerült szerializálni a modellt.");
                }
            }
        }
    }
}