package dev.coala.core;

/** Result/observation produced by executing a selected grounding or learning action. */
public record ActionResult(String observation, boolean terminal, boolean awaitingInput) {
    public static final int MAX_OBSERVATION_CHARACTERS = 16_000;
    public ActionResult {
        observation = observation == null ? "" : observation;
        if (observation.length() > MAX_OBSERVATION_CHARACTERS) throw new IllegalArgumentException("Action observation exceeds " + MAX_OBSERVATION_CHARACTERS + " characters; summarize it in the action adapter.");
        if (terminal && awaitingInput) throw new IllegalArgumentException("An action result cannot be both terminal and awaiting input");
    }
    public ActionResult(String observation, boolean terminal) { this(observation, terminal, false); }
    public static ActionResult continueWith(String observation) { return new ActionResult(observation, false, false); }
    public static ActionResult finish(String observation) { return new ActionResult(observation, true, false); }
    public static ActionResult awaitingInput(String observation) { return new ActionResult(observation, false, true); }
}
