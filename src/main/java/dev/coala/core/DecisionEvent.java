package dev.coala.core;

public record DecisionEvent(int cycle, DecisionStage stage, String summary) {
    public DecisionEvent {
        summary = summary == null ? "" : summary;
    }
}
