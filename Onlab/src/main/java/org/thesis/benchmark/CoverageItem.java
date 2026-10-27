package org.thesis.benchmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One atomic, model-grounded coverage obligation derived from a requirement.
 *
 * A coverage item is intentionally NOT matched against the free text produced by the LLM.
 * It is a pattern over the concrete instance model (the Datalog facts serialized by Refinery),
 * so the metric is grounded in the formal model instead of in natural language.
 *
 *   {@code OBJECT}: holds in a model iff a node of class {@link #type} exists.
 *   {@code LINK}: holds in a model iff there is a fact {@code relation(source, y)}
 *       where {@code y} is a node of class {@link #type} (or, if {@link #target} is given,
 *       {@code y} is exactly that node).
 *
 * If {@link #negated} is {@code true}, the item is covered by a test suite as soon as at least
 * one model of the suite violates the pattern. This is what makes the "else" branch of a
 * requirement an explicit, measurable coverage obligation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoverageItem {

    public enum Kind { OBJECT, LINK }

    public String id;
    public Kind kind;
    /** OBJECT: the class of the node. LINK: the class of the target node. */
    public String type;
    /** LINK only: the name of the Datalog relation, e.g. {@code allowedBubbles}. */
    public String relation;
    /** LINK only: the name of the concrete source node, e.g. {@code TMCPort}. */
    public String source;
    /** LINK only, optional: the name of a concrete target node instead of a class. */
    public String target;
    public boolean negated;
    /** Traceability back to the element of the generated test strategy (C1, E2, ...). */
    public String traceTo;
    public String description;

    @Override
    public String toString() {
        String core = kind == Kind.OBJECT
                ? type + "(x)"
                : relation + "(" + source + ", y:" + (target != null ? target : type) + ")";
        return (negated ? "NOT " : "") + core;
    }
}
