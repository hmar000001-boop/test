package dev.coala.core;

/** The kinds of actions that a decision cycle may select for execution. */
public enum ActionKind {
    /** An action that affects an external environment through grounding. */
    GROUNDING,
    /** An action that writes information to long-term memory. */
    LEARNING
}
