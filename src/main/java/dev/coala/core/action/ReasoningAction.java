package dev.coala.core.action;

import dev.coala.core.AgentContext;

/** Internal action: reads working memory and writes new information into it using the LLM. */
public interface ReasoningAction {
    String reason(String operation, String instruction, AgentContext context);
}
