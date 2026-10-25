package dev.coala.core;

/** Internal action that writes information to long-term memory. */
public interface LearningAction extends CoalaAction {
    @Override default ActionKind kind() { return ActionKind.LEARNING; }
}
