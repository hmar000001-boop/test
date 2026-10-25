package dev.coala.core;

/** External action that interacts with an environment through grounding. */
public interface GroundingAction extends CoalaAction {
    @Override default ActionKind kind() { return ActionKind.GROUNDING; }
}
