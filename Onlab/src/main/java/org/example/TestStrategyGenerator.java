package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import com.fasterxml.jackson.databind.DeserializationFeature;

public class TestStrategyGenerator {

    // For Local test
    public static void main(String[] args) throws Exception {
        String jsonInput = Files.readString(Paths.get("src/main/resources/shot.json"));
        //generateFromRawJson(jsonInput);
    }

    // Ezt hívja a Main.java az AI-tól kapott JSON-nel
    public static void generateFromRawJson(String jsonInput, String refineryCode) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        StrategyData data = mapper.readValue(jsonInput, StrategyData.class);
        buildAndSaveMarkdown(data);
        //buildAndSaveHtml(data, refineryCode);
    }

    private static void buildAndSaveHtml(StrategyData data, String refineryCode) throws Exception {
        StringBuilder html = new StringBuilder();

        // Tailwind CSS behúzása (ez csinálja a szép dizájnt)
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        html.append("<script src=\"https://cdn.tailwindcss.com\"></script>");
        html.append("<title>Test Strategy Report</title></head>");

        html.append("<body class=\"bg-gray-100 text-gray-800 font-sans p-8\">");
        html.append("<div class=\"max-w-6xl mx-auto bg-white p-8 rounded-lg shadow-xl\">");

        // Fejléc & Követelmény
        html.append("<h1 class=\"text-3xl font-bold text-blue-700 mb-6\">🚇 Önvezető Metró - Teszt Stratégia</h1>");
        html.append("<div class=\"bg-blue-50 border-l-4 border-blue-500 p-4 mb-8 rounded\">");
        html.append("<h2 class=\"font-bold text-lg mb-2\">Eredeti Követelmény:</h2>");
        html.append("<p class=\"italic\">").append(data.requirement_text).append("</p></div>");

        // Két oszlopos elrendezés: Bal oldalon a Refinery, Jobb oldalon a Szabályok
        html.append("<div class=\"grid grid-cols-2 gap-8 mb-8\">");

        // Bal oszlop: Refinery Kód
        html.append("<div><h2 class=\"text-xl font-bold text-gray-700 mb-4\">⚙️ Generált Refinery Modell</h2>");
        html.append("<pre class=\"bg-gray-900 text-green-400 p-4 rounded-lg overflow-x-auto text-sm\"><code>");
        html.append(refineryCode.replace("<", "&lt;").replace(">", "&gt;")); // HTML escape
        html.append("</code></pre></div>");

        // Jobb oszlop: Feltételek és Szabályok
        html.append("<div><h2 class=\"text-xl font-bold text-gray-700 mb-4\">📝 Logikai Szabályok</h2>");
        html.append("<ul class=\"list-disc pl-5 space-y-2 mb-6\">");
        for (Rule r : data.rules) {
            html.append("<li><span class=\"font-mono bg-gray-200 px-1 rounded\">").append(r.logic)
                    .append("</span> ➡️ <span class=\"font-bold text-blue-600\">")
                    .append(String.join(", ", r.result_ids)).append("</span></li>");
        }
        html.append("</ul></div></div>"); // Két oszlop vége

        // Mátrix Táblázat
        html.append("<h2 class=\"text-xl font-bold text-gray-700 mb-4\">📊 Lefedettségi Mátrix</h2>");
        html.append("<div class=\"overflow-x-auto\"><table class=\"min-w-full bg-white border border-gray-300\">");

        // Táblázat Fejléc
        html.append("<thead class=\"bg-gray-200 text-gray-600 uppercase text-sm leading-normal\"><tr>");
        List<String> cIds = data.conditions.stream().map(c -> c.id).toList();
        for (String cId : cIds) html.append("<th class=\"py-3 px-6 text-left\">").append(cId).append("</th>");
        html.append("<th class=\"py-3 px-6 text-center\">Expected</th>");
        html.append("<th class=\"py-3 px-6 text-center\">Covered</th></tr></thead>");

        // Táblázat Sorok (A mátrix logika újrahasznosítása)
        html.append("<tbody class=\"text-gray-600 text-sm font-light\">");
        int n = data.conditions.size();
        for (int i = 0; i < (1 << n); i++) {
            Map<String, Boolean> state = new HashMap<>();
            html.append("<tr class=\"border-b border-gray-200 hover:bg-gray-100\">");
            for (int j = 0; j < n; j++) {
                boolean val = (i & (1 << (n - 1 - j))) != 0;
                state.put(cIds.get(j), val);
                String badgeColor = val ? "bg-green-200 text-green-700" : "bg-red-200 text-red-700";
                html.append("<td class=\"py-3 px-6 text-left\"><span class=\"").append(badgeColor).append(" py-1 px-3 rounded-full text-xs\">")
                        .append(val ? "True" : "False").append("</span></td>");
            }

            Set<String> expected = new TreeSet<>();
            for (Rule r : data.rules) {
                if (evaluateSimpleLogic(r.logic, state)) expected.addAll(r.result_ids);
            }
            String expStr = expected.isEmpty() ? "-" : String.join(" && ", expected);
            String stepStr = expected.isEmpty() ? "-" : "Test01: Step" + String.format("%02d", i + 1);

            html.append("<td class=\"py-3 px-6 text-center font-bold\">").append(expStr).append("</td>");
            html.append("<td class=\"py-3 px-6 text-center\">").append(stepStr).append("</td></tr>");
        }
        html.append("</tbody></table></div>");

        html.append("</div></body></html>");

        try (FileWriter writer = new FileWriter("src/main/resources/TestStrategyReport.html")) {
            writer.write(html.toString());
        }
        System.out.println("🌐 Frontend HTML sikeresen generálva: src/main/resources/TestStrategyReport.html");
    }
    // Test Strategy md builder
    private static void buildAndSaveMarkdown(StrategyData data) throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("# Test Strategy Sample\n\n");
        md.append("**Requirement:** ").append(data.requirement_text).append("\n\n");

        appendSection(md, "Conditions", data.conditions);
        appendSection(md, "Expectations", data.expectations);

        md.append("### Rules\n");
        for (Rule r : data.rules) {
            md.append("- ").append(r.logic).append(" => ").append(String.join(", ", r.result_ids)).append("\n");
        }
        md.append("\n");

        md.append("### Coverage\n");
        generateMatrix(md, data);

        try (FileWriter writer = new FileWriter("TestStrategy.md")) {
            writer.write(md.toString());
        }
        System.out.println("Markdown sikeresen generálva: TestStrategy.md");
    }

    private static void generateMatrix(StringBuilder md, StrategyData data) {
        int n = data.conditions.size();
        List<String> cIds = data.conditions.stream().map(c -> c.id).toList();

        md.append("| ").append(String.join(" | ", cIds)).append(" | Expected | Covered |\n");
        md.append("|").append(" --- |".repeat(n + 2)).append("\n");

        for (int i = 0; i < (1 << n); i++) {
            Map<String, Boolean> state = new HashMap<>();
            List<String> rowValues = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                boolean val = (i & (1 << (n - 1 - j))) != 0;
                state.put(cIds.get(j), val);
                rowValues.add(val ? "True" : "False");
            }

            Set<String> expected = new TreeSet<>();
            for (Rule r : data.rules) {
                if (evaluateSimpleLogic(r.logic, state)) {
                    expected.addAll(r.result_ids);
                }
            }

            String expStr = expected.isEmpty() ? "-" : String.join(" && ", expected);
            String stepStr = expected.isEmpty() ? "-" : "Test01: Step" + String.format("%02d", i + 1);

            md.append("| ").append(String.join(" | ", rowValues))
                    .append(" | ").append(expStr)
                    .append(" | ").append(stepStr).append(" |\n");
        }
    }

    private static boolean evaluateSimpleLogic(String logic, Map<String, Boolean> state) {
        String replaced = logic;
        for (Map.Entry<String, Boolean> entry : state.entrySet()) {
            replaced = replaced.replace(entry.getKey(), entry.getValue().toString());
        }
        return replaced.contains("true") && !replaced.contains("false") || replaced.equals("true");
    }

    private static void appendSection(StringBuilder md, String title, List<Element> elements) {
        if (elements == null || elements.isEmpty()){
            return;
        }
        md.append("### ").append(title).append("\n");
        for (Element e : elements) {
            md.append("- **").append(e.id).append("**: ").append(e.description)
                    .append(" *(Source: \"").append(e.source_text).append("\")*\n");
        }
        md.append("\n");
    }

    static class StrategyData { public String requirement_text; public List<Element> conditions; public List<Element> expectations; public List<Rule> rules; }
    static class Element { public String id, description, source_text; }
    static class Rule { public String logic; public List<String> result_ids; }
}