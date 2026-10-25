package dev.coala.security;

import dev.coala.core.ActionCall;
import dev.coala.core.AgentContext;
import dev.coala.core.CoalaAction;

/** Optional safeguard for consequential grounding actions; independent from cognitive-architecture semantics. */
@FunctionalInterface
public interface ActionApprovalPolicy {
    ApprovalDecision authorize(CoalaAction action, ActionCall call, AgentContext context);

    record ApprovalDecision(boolean approved, String requestId, String message) {
        public static ApprovalDecision grant() { return new ApprovalDecision(true, "", "Approved"); }
        public static ApprovalDecision pending(String id) { return new ApprovalDecision(false, id, "User approval is required"); }
    }
}
