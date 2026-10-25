package dev.coala.demo.action;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.ActionRisk;
import dev.coala.core.AgentContext;
import dev.coala.core.GroundingAction;

import java.util.Set;

/** Mock customer-support actions. They never call a real ticketing or identity system. */
public final class SupportTriageActions {
    private SupportTriageActions() { }

    public static GroundingAction classifyCase() {
        return new GroundingAction() {
            @Override public String id() { return "classify_support_case"; }
            @Override public String description() { return "Classify the user's support request using a deterministic teaching fixture."; }
            @Override public ActionRisk risk() { return ActionRisk.LOW; }
            @Override public boolean idempotent() { return true; }
            @Override public ActionResult execute(ActionCall call, AgentContext context) {
                String goal = context.workingMemory().getString("goal", "").toLowerCase(java.util.Locale.ROOT);
                boolean elevated = containsAny(goal, "locked", "compromised", "fraud", "unauthorized", "payment", "security");
                String category = containsAny(goal, "sign in", "login", "password", "locked") ? "account-access" : "general-support";
                context.workingMemory().put("support.triage", java.util.Map.of("category", category,
                        "severity", elevated ? "elevated" : "normal", "source", "deterministic-demo-rule"));
                return ActionResult.continueWith("Mock triage recorded category=" + category + ", severity=" + (elevated ? "elevated" : "normal") + ". No user account was accessed.");
            }
        };
    }

    public static GroundingAction suggestSafeSteps() {
        return new GroundingAction() {
            @Override public String id() { return "suggest_safe_steps"; }
            @Override public String description() { return "Give safe, non-invasive support guidance without changing an account."; }
            @Override public ActionRisk risk() { return ActionRisk.LOW; }
            @Override public boolean idempotent() { return true; }
            @Override public ActionResult execute(ActionCall call, AgentContext context) {
                return ActionResult.finish("Suggested next steps (demo): use the service's official recovery path, do not share passwords or one-time codes, and avoid repeatedly retrying if the service says the account is locked. If access remains blocked, contact the provider's verified support channel. No account was inspected or changed.");
            }
        };
    }

    public static GroundingAction escalateCase() {
        return new GroundingAction() {
            @Override public String id() { return "escalate_case"; }
            @Override public String description() { return "Return a simulated recommendation to escalate an elevated-risk support case; no real ticket is created."; }
            @Override public ActionRisk risk() { return ActionRisk.HIGH; }
            @Override public boolean idempotent() { return true; }
            @Override public ActionResult execute(ActionCall call, AgentContext context) {
                return ActionResult.finish("Escalation recommendation: use the provider's verified support channel for account-access review. Demo reference SUP-DEMO-1042 is fictional; no real support ticket, identity check or account action occurred.");
            }
        };
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }
}
