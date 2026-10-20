package org.thesis.benchmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The manually derived reference ("ground truth") for a single requirement:
 * the complete set of coverage obligations plus the hand-written test suite
 * that is expected to maximise them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequirementGroundTruth {

    public String reqId;
    public String text;
    public List<CoverageItem> items;
    /** Paths of the hand-written Refinery scenario snippets forming the manual test suite. */
    public List<String> manualSuite;
    /** Coverage count computed by hand on paper; used as a sanity check against the tool. */
    public int manualMaxCoverage;

    public int totalItems() {
        return items == null ? 0 : items.size();
    }

    public static Map<String, RequirementGroundTruth> load(String path) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<RequirementGroundTruth> list =
                mapper.readValue(Files.readString(Path.of(path)), new TypeReference<>() {});
        Map<String, RequirementGroundTruth> byId = new LinkedHashMap<>();
        for (RequirementGroundTruth gt : list) {
            byId.put(gt.reqId, gt);
        }
        return byId;
    }
}
