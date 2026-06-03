package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tools.refinery.generator.standalone.StandaloneRefinery;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final String TS_SERVER_URL = "http://localhost:3000/generate-validated-scenario";

    public static void main(String[] args) throws Exception {
        String requirement = Files.readString(Paths.get("/Users/nagyerik/Documents/BME/Onlab/src/main/resources/req1"));

        String metaModel = Files.readString(Path.of("/Users/nagyerik/Documents/BME/Onlab/src/main/resources/temp.problem"));

        String lastError = null;
        boolean isValid = false;

        ObjectMapper mapper = new ObjectMapper();

        // 3 try
        for (int i = 0; i < 3 && !isValid; i++) {
            System.out.println("\n--- AI hívása... " + (i + 1) + ". próbálkozás ---");

            // 1. Kérés küldése a TS szervernek
            String aiResponseRaw = callAiBridge(requirement, metaModel, lastError, mapper);

            System.out.println("Nyers AI válasz a szervertől:\n" + aiResponseRaw);

            // 2. JSON szétszedése
            JsonNode rootNode = mapper.readTree(aiResponseRaw);

            String refineryCode = rootNode.get("missingRefineryCode").asText();
            String strategyJson = rootNode.get("strategyData").toString();
            System.out.println("Generált Refinery kód:\n" + refineryCode);

            //String strategyJson = aiResponseRaw;

            // 3. Refinery kód mentése egy ideiglenes fájlba
            String finalPath = "src/main/resources/final.problem";
            Path targetPath = Paths.get(finalPath);
            Files.writeString(Paths.get("src/main/resources/try.problem"), refineryCode);
            Files.writeString(targetPath, Files.readString(Paths.get("/Users/nagyerik/Documents/BME/Onlab/src/main/resources/temp.problem")));

            Files.writeString(targetPath, "\n" + refineryCode, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            // 4. Validálás a Refineryvel
            try {
                validateWithRefinery(finalPath);
                System.out.println("Refinery validálás SIKERES!");

                // 5. Ha sikeres, generáljuk le a TestStrategy.md fájlt!
                TestStrategyGenerator.generateFromRawJson(strategyJson, refineryCode);
                isValid = true;

            } catch (Exception e) {
                System.out.println("Refinery validálás SIKERTELEN. Hiba: " + e.getMessage());
                e.printStackTrace();
                lastError = e.getMessage(); // Ezt a hibát küldjük vissza az AI-nak a következő körben!
            }
        }

        if (!isValid) {
            System.out.println("Nem sikerült érvényes modellt generálni 3 próbálkozásból.");
        }
    }

    private static String callAiBridge(String requirement, String metaModel, String lastError, ObjectMapper mapper) throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("requirement", requirement);
        payload.put("metaModel", metaModel);
        if (lastError != null) {
            payload.put("lastError", lastError);
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TS_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private static void validateWithRefinery(String filePath) throws Exception {
        var problem = StandaloneRefinery.getProblemLoader().loadFile(filePath);
        try (var generator = StandaloneRefinery.getGeneratorFactory().createGenerator(problem)) {
            generator.generate();
        }
    }
}

//Top Down Magyarázat
//Architectura
//Inputok Outputok
//Kibontás -> ábrák