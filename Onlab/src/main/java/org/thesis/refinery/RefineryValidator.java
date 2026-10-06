package org.thesis.refinery;

import tools.refinery.generator.standalone.StandaloneRefinery;
import tools.refinery.language.model.problem.Problem;
import tools.refinery.language.model.problem.Assertion;
import tools.refinery.language.model.problem.NodeAssertionArgument;
import tools.refinery.language.model.problem.WildcardAssertionArgument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RefineryValidator {
    public void validate(String filePath) throws Exception {
        var problem = StandaloneRefinery.getProblemLoader().loadFile(filePath);
        try (var generator = StandaloneRefinery.getGeneratorFactory().createGenerator(problem)) {
            generator.generate();
        }
    }

    /*public void generateMultipleModels(String filePath, int limit) throws Exception {
        var problem = StandaloneRefinery.getProblemLoader().loadFile(filePath);

        try (var generator = StandaloneRefinery.getGeneratorFactory().createGenerator(problem)) {
            int modelCount = 0;

            // Addig generálunk, amíg a limitet el nem érjük, vagy nincs több megoldás
            while (modelCount < limit && generator.generate()) {
                modelCount++;
                System.out.println("=== " + modelCount + ". Érvényes Modell ===");

                // Itt kinyerjük a gráf állapotát a memóriából
                var model = generator.getModel();

                // Kinyomtatjuk táblázatosan vagy stringként,
                // hogy lássuk, melyik vonat/port milyen állapotban van
                printModelState(model);
            }
            System.out.println("Összesen " + modelCount + " érvényes állapotot találtunk.");
        }
    }*/
    // Visszatérési érték: A legenerált modellek listája.
    // Minden modell egy halmaz (Set), amiben String-ként benne vannak a tények
    public List<Set<String>> validateAndExtractModels(String filePath, int maxSolutions) throws Exception {
        List<Set<String>> extractedModels = new ArrayList<>();

        var problemLoader = StandaloneRefinery.getProblemLoader();
        var problem = problemLoader.loadFile(filePath);
        var generatorFactory = StandaloneRefinery.getGeneratorFactory();

        try (var generator = generatorFactory.createGenerator(problem)) {
            generator.setMaxNumberOfSolutions(maxSolutions);

            // Ha a generálás elszáll, itt dobja az Exceptiont, ami a lastError lesz az LLM-nek!
            generator.generate();

            int found = generator.getSolutionCount();

            // Végigmegyünk az összes talált modellen
            for (int i = 0; i < found; i++) {
                generator.loadSolution(i);
                var solutionProblemOpt = generator.trySerialize();

                if (solutionProblemOpt.isPresent()) {
                    Set<String> currentModelFacts = new HashSet<>();
                    Problem solutionProblem = solutionProblemOpt.get();

                    solutionProblem.getStatements().forEach(stmt -> {
                        if (stmt instanceof Assertion assertion) {
                            String relationName = assertion.getRelation().getName();
                            List<String> argNames = new ArrayList<>();

                            for (var arg : assertion.getArguments()) {
                                if (arg instanceof NodeAssertionArgument nodeArg) {
                                    argNames.add(nodeArg.getNode().getName());
                                } else if (arg instanceof WildcardAssertionArgument) {
                                    argNames.add("*");
                                } else {
                                    argNames.add(arg.toString());
                                }
                            }

                            String arguments = String.join(", ", argNames);
                            // Csak a konkrét, instanciált tényeket mentjük el
                            if (!arguments.contains("*")) {
                                currentModelFacts.add(relationName + "(" + arguments + ")");
                            }
                        }
                    });

                    extractedModels.add(currentModelFacts);
                }
            }
        }
        return extractedModels;
    }
}