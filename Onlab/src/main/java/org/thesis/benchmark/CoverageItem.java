package org.thesis.benchmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoverageItem {

    public enum Kind { OBJECT, LINK }

    public String id;
    public Kind kind;
    public String type;
    public String relation;
    public String source;
    public String sourceType;
    public String target;
    public boolean negated;
    public String guardType;
    public String traceTo;
    public String description;

    @Override
    public String toString() {
        String core;
        if (kind == Kind.OBJECT) {
            core = type + "(x)";
        } else {
            String from = source != null ? source : "x:" + sourceType;
            String to = target != null ? target : "y:" + type;
            core = relation + "(" + from + ", " + to + ")";
        }
        String guard = guardType != null ? " [when " + guardType + "]" : "";
        return (negated ? "NOT " : "") + core + guard;
    }
}
