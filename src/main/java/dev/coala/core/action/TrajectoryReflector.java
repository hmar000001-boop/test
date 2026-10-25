package dev.coala.core.action;

import dev.coala.core.AgentContext;

import java.util.Objects;

/** Reasons over current trajectory evidence and writes a reflection only to working memory. */
public final class TrajectoryReflector {
    private final ReasoningAction reasoning;
    public TrajectoryReflector(ReasoningAction reasoning) { this.reasoning = Objects.requireNonNull(reasoning); }
    public String reflect(AgentContext context) {
        String evidence = "goal=" + context.workingMemory().getString("goal", "")
                + "\nlatest_action=" + context.workingMemory().getString("latest_action", "")
                + "\nlatest_observation=" + context.workingMemory().getString("latest_observation", "")
                + "\nprevious_rejection=" + context.workingMemory().getString("plan.last_rejection", "");
        String reflection = reasoning.reason("REFLECT_TRAJECTORY",
                "Analyze only the supplied trajectory evidence. Return concise structured prose with: what worked, what failed, reusable lesson, uncertainty. Do not claim memory has been saved.\n" + evidence,
                context).trim();
        if (reflection.length() > 4_000) reflection = reflection.substring(0, 4_000);
        context.workingMemory().put("reflection.latest", reflection);
        context.workingMemory().put("reasoning.latest", null);
        return reflection;
    }
}
