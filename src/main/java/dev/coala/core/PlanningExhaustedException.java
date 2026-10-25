package dev.coala.core;

import java.util.List;

/** Planning safety bound was reached without selecting an action; no action was executed. */
public final class PlanningExhaustedException extends DecisionProcedureException {
    private static final long serialVersionUID = 1L;

    private final int attempts;
    private final String lastRejection;
    private final transient List<DecisionEvent> events;

    public PlanningExhaustedException(int attempts, String lastRejection, List<DecisionEvent> events) {
        super("Planning did not select an action after " + attempts
                + " proposal attempt(s). No action was executed. Last rejection: "
                + (lastRejection == null ? "unspecified planning rejection" : lastRejection));
        if (attempts < 1) throw new IllegalArgumentException("attempts must be positive");
        this.attempts = attempts;
        this.lastRejection = lastRejection == null ? "" : lastRejection;
        this.events = events == null ? List.of() : List.copyOf(events);
    }

    public int attempts() { return attempts; }
    public String lastRejection() { return lastRejection; }
    public List<DecisionEvent> events() { return events; }
}
