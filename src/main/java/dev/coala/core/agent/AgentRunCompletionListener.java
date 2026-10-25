package dev.coala.core.agent;

import dev.coala.core.AgentContext;
import dev.coala.core.AgentRunResult;

/** Optional end-of-run hook. Learning/storage happens only when a listener is explicitly configured. */
@FunctionalInterface
public interface AgentRunCompletionListener {
    void onCompleted(AgentRunResult result, AgentContext context);
}
