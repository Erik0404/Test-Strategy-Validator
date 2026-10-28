package org.thesis.refinery;

import tools.refinery.generator.GeneratorResult;
import tools.refinery.generator.ModelGenerator;
import tools.refinery.generator.standalone.StandaloneRefinery;
import tools.refinery.language.model.problem.Problem;
import tools.refinery.language.model.problem.Assertion;
import tools.refinery.language.model.problem.LogicConstant;
import tools.refinery.language.model.problem.NodeAssertionArgument;
import tools.refinery.language.model.problem.WildcardAssertionArgument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RefineryValidator {
    public static final long GENERATION_TIMEOUT_SECONDS = 15;

    public void checkSyntax(String filePath) throws Exception {
        StandaloneRefinery.getProblemLoader().loadFile(filePath);
    }

    public List<Set<String>> validateAndExtractModels(String filePath, int maxSolutions) throws Exception {
        var problemLoader = StandaloneRefinery.getProblemLoader();
        var problem = problemLoader.loadFile(filePath);
        var generatorFactory = StandaloneRefinery.getGeneratorFactory();

        // Idokorlatos generalas. A Refinery idotullepeskor eldobja a reszeredmenyt, es az idotullepett
        // generator nem hasznalhato ujra, ezert friss generatorral feleannyi megoldast kerunk
        // (10 -> 5 -> 2 -> 1). Ha egyet sem talal, dob (UNSATISFIABLE / TIMEOUT), ez lesz a lastError az LLM-nek!
        int requested = maxSolutions;
        while (true) {
            try (var generator = generatorFactory.createGenerator(problem)) {
                generator.setMaxNumberOfSolutions(requested);
                var result = generator.tryGenerateWithTimeout(GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (result == GeneratorResult.TIMEOUT && requested > 1) {
                    requested = Math.max(1, requested / 2);
                    continue;
                }
                result.orThrow();
                return extractSolutions(generator);
            }
        }
    }

    private static List<Set<String>> extractSolutions(ModelGenerator generator) {
        List<Set<String>> extractedModels = new ArrayList<>();
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
                        // Csak a konkret, instancialt tenyeket mentjuk el. A nem igaz ertekuek
                        // (!, ?) prefixet kapnak, igy a lefedettsegi mintakra nem illeszkednek.
                        if (!arguments.contains("*")) {
                            currentModelFacts.add(truthPrefix(assertion) + relationName + "(" + arguments + ")");
                        }
                    }
                });

                extractedModels.add(currentModelFacts);
            }
        }
        return extractedModels;
    }

    private static String truthPrefix(Assertion assertion) {
        if (assertion.getValue() instanceof LogicConstant constant) {
            return switch (constant.getLogicValue()) {
                case TRUE -> "";
                case FALSE -> "!";
                case UNKNOWN -> "?";
                case ERROR -> "#";
            };
        }
        return "";
    }
}