package dev.coala.core.action;

import dev.coala.core.AgentContext;
import dev.coala.core.InvalidModelOutputException;
import dev.coala.core.PromptContextPolicy;
import dev.coala.core.WorkingMemoryPromptContextBuilder;
import dev.coala.core.DecisionProcedureException;
import dev.coala.llm.LanguageModel;
import dev.coala.llm.LanguageModelException;

import java.util.Objects;

/** Internal reasoning action: invokes the configured LLM and stores generated information in working memory. */
public final class LlmReasoningAction implements ReasoningAction {
    public static final int MAX_MODEL_OUTPUT_CHARACTERS = 64_000;
    private final LanguageModel languageModel;
    private final int maxCallsPerRun;
    private final WorkingMemoryPromptContextBuilder promptContextBuilder;
    private final PromptContextPolicy promptContextPolicy;

    public LlmReasoningAction(LanguageModel languageModel) {
        this(languageModel, 50);
    }

    public LlmReasoningAction(LanguageModel languageModel, int maxCallsPerRun) {
        this(languageModel, maxCallsPerRun, new WorkingMemoryPromptContextBuilder(), PromptContextPolicy.defaultPolicy());
    }

    public LlmReasoningAction(LanguageModel languageModel, int maxCallsPerRun,
                              WorkingMemoryPromptContextBuilder promptContextBuilder,
                              PromptContextPolicy promptContextPolicy) {
        this.languageModel = Objects.requireNonNull(languageModel, "languageModel");
        if (maxCallsPerRun < 1 || maxCallsPerRun > 1_000) {
            throw new IllegalArgumentException("maxCallsPerRun must be between 1 and 1000");
        }
        this.maxCallsPerRun = maxCallsPerRun;
        this.promptContextBuilder = Objects.requireNonNull(promptContextBuilder, "promptContextBuilder");
        this.promptContextPolicy = Objects.requireNonNull(promptContextPolicy, "promptContextPolicy");
    }

    @Override
    public String reason(String operation, String instruction, AgentContext context) {
        int callCount = Integer.parseInt(context.workingMemory().getString("reasoning.call_count", "0"));
        if (callCount >= maxCallsPerRun) {
            throw new DecisionProcedureException("Maximum reasoning-call budget exhausted for this run (" + maxCallsPerRun + ").");
        }
        context.workingMemory().put("reasoning.call_count", callCount + 1);
        WorkingMemoryPromptContextBuilder.Request request = promptContextPolicy.requestFor(operation, context);
        if (request == null) throw new IllegalStateException("PromptContextPolicy returned null for " + operation);
        WorkingMemoryPromptContextBuilder.PromptContext memoryView = promptContextBuilder.build(context.workingMemory(), request);
        String prompt = "You are performing an internal CoALA reasoning action.\n"
                + "OPERATION=" + operation + "\n"
                + "CYCLE=" + context.cycle() + "\n"
                + "GOAL=" + promptContextBuilder.sanitizeText(context.workingMemory().getString("goal", ""), request) + "\n"
                + "WORKING_MEMORY_BEGIN\n" + memoryView.rendered()
                + "WORKING_MEMORY_END\n"
                + instruction + "\n"
                + "Return only the requested result. Do not perform an external action yourself.";
        String generated;
        try {
            generated = languageModel.generate(promptContextBuilder.sanitizeText(prompt, request));
        } catch (LanguageModelException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new LanguageModelException("Language-model provider call failed for operation " + operation + ".", ex);
        }
        if (generated == null || generated.isBlank()) {
            throw new LanguageModelException("Language-model provider returned an empty response for operation " + operation + ".");
        }
        String normalized = generated.trim();
        if (normalized.length() > MAX_MODEL_OUTPUT_CHARACTERS) {
            throw new InvalidModelOutputException("Language-model output exceeds the maximum allowed size of "
                    + MAX_MODEL_OUTPUT_CHARACTERS + " characters.");
        }
        context.workingMemory().put("reasoning.last_operation", operation);
        context.workingMemory().put("reasoning.latest", normalized);
        return normalized;
    }
}
