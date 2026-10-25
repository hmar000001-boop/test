package dev.coala.core.agent;

/** Observer extension point; implementations must avoid logging secrets/prompts/argument values. */
@FunctionalInterface
public interface DecisionTraceListener {
    void onTrace(DecisionTrace trace);
}
