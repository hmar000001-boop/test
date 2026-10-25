# Report implementation status and review-fix checklist

This checklist tracks the recommendations in `COALA_BUILT_IN_COMPONENTS_REPORT.md` and the preceding source review. The detailed implementation and paper-conformance boundaries are documented in `ARCHITECTURE.md`. The CoALA paper remains the only reference used to define the cognitive architecture; class names, contracts, integration choices and bounds are implementation decisions.

## Built-in catalogue: applied

### P0 - Assembly and state
- `CoalaAgentDefinition`, `CoalaAgent`, `AgentContextFactory`, `AgentRunRequest`, `DecisionTrace`, `DecisionTraceListener`, `AgentRunCompletionListener`, and `PendingInteractionContextFactory` provide a repeatable assembly and per-run context boundary.
- `ActionCatalogFormatter`, `ActionArgumentDefinition`, richer `ActionDescriptor`, `ActionRegistryFactory`, `ActionExecutionDecorator`, `DelegatingAction`, `TracingActionDecorator`, `TimeoutActionDecorator`, and `IdempotentRetryActionDecorator` provide action contracts/formatting/decoration.
- `PromptContextPolicy` and `WorkingMemoryPromptContextBuilder` choose operation-relevant keys, redact common credential patterns, and bound prompt context. Dynamic working-memory values are not blindly dumped into model prompts.

### P0/P1 - Memory and learning
- `MemoryContextAssembler`, `EpisodicMemoryLearningAction`, `SemanticMemoryLearningAction`, `ReflectionToSemanticMemoryLearningAction`, and `DecisionEpisodeRecorder` are implemented.
- `MemoryRetriever`, `LexicalMemoryRetriever`, `RecencyImportanceRelevanceRetriever`, and `HybridMemoryRetriever` separate retrieval/ranking from storage. `DenseMemoryRetriever`, `DenseMemoryRetrieverAdapter`, `EmbeddingProvider`, `VectorMemoryIndex`, and `VectorIndexingLongTermMemory` provide optional dense retrieval integration ports.
- `MemoryNamespace`, `MemoryRetentionPolicy`, `MaxAgeMemoryRetentionPolicy`, `ScopedLongTermMemory`, `PersistentLongTermMemory`, `MemoryRecordRepository`, `FileMemoryRecordRepository`, `MemoryConsolidator`, `MemoryDeletionPolicy`, and `MemoryMaintenanceService` implement scope, retention, a local durable file adapter and optional maintenance.
- Episodic, semantic, and reflection-derived memory writes use shared credential-pattern sanitization, control-character normalization, and explicit content/metadata bounds. This is defense-in-depth, not guaranteed secret detection. Consolidation writes the replacement before deleting source records to avoid a destructive gap.

### P0/P1 - Reasoning/planning
- `StructuredReasoningAction<T>`, `ObservationSummarizer`, `TrajectoryReflector`, `CandidateProposer`, `CandidateEvaluator`, `CandidateSelectionPolicy`, LLM/rule-based implementations, and score/evaluation value objects are implemented.
- Built-in procedures include direct action generation, iterative reason-act, deliberative proposal/evaluation/selection with rejection/re-proposal, fixed action set, plan-first, iterative refinement, and optional search tree.
- Rejected candidate sets are not executed; deliberative re-proposal is bounded. Candidate indexes/action calls/scores are checked for consistency.
- Optional BFS/DFS/MCTS operate on a use-case simulation domain. Search budgets cap materialized tree nodes (root included) and depth; non-finite/overflowing scores fail closed. Domains must still bound the transition list and remain side-effect free.
- `DecisionProcedureRouter` and `ProcedureRoutingPolicy` exist as experimental opt-in facilities, not mandatory architecture layers.

### P1 - Grounding and execution safety
- `CalculatorGroundingAction`, fixed-endpoint `JsonHttpGroundingAction`, `WebSearchGroundingAction`/`WebSearchProvider`, `ReadOnlyWorkspaceGroundingAction`, `HumanClarificationAction`, the pending-interaction store, `ActionApprovalPolicy`, `InMemoryActionApprovalPolicy`, and `ApprovalRequiredAction` are implemented.
- `SkillCatalog` and `SkillRetrievalAction` retrieve declarative skill definitions only; retrieved text is not executed.
- Model-generated action IDs/arguments are validated against an allow-list and per-action contracts. Execution decorators can supply tracing, timeouts and retries only for actions marked idempotent.
- Web-search URLs are constrained to absolute HTTP(S) URLs with no user-info, and malformed/null provider results are filtered before normalization.

## Original source-review findings fixed

- Deliberative planning requires multiple distinct candidates, supports rejection and bounded re-proposal, records candidate/evaluation state, and fails closed on exhaustion.
- Structured selected-action identity and arguments are written into working memory before selected action execution.
- Reusable decision procedures are not tied to an action literally named `respond`.
- Long-term demo memory can be resumed across requests by a server-generated session ID and is isolated/bounded per session.
- Argument counts/value sizes, request bodies, goals, observations, reasoning calls, model outputs, memory records and search trees have explicit bounds.
- Working memory freezes supported nested values; prompt context is selected/redacted/bounded; traces do not echo raw observations, candidate JSON, prompts or action arguments.
- Approval grants bind to principal and exact action call; unauthorized attempts do not consume someone else's pending approval.
- File-backed memory loading filters after scoping the full available record set; memory consolidation stores the replacement before removing duplicates.
- API error classes are sanitized; malformed request JSON has a dedicated 400 mapping; provider/malformed output errors remain distinct from action/runtime failures.
- Maven wrapper scripts default to Maven 3.10.0 (with checksum-pinned 3.9.16 compatibility) and verify the platform-specific archive SHA-512 before extraction. CI runs Maven verification on Linux and Windows and preserves test reports.

## Intentionally scoped / not claimed as complete

1. **Real integrations:** travel, calendar, flight/hotel booking, repository write/command execution and production web search providers are not included. Adapter contracts and safe mock/local helpers exist. Real consequential actions need identity-aware authorization, explicit human approval where appropriate, audit, and deployment-level egress/sandbox controls.
2. **Identity:** the API token is a shared demo token; it is not individual identity. Session IDs are capabilities, not user identity. Production sessions need principal binding and durable authz.
3. **Distributed infrastructure:** in-memory rate limits, pending interactions and demo session store are single-process. `FileMemoryRecordRepository` is not a multi-writer database.
4. **Vector implementation:** vector/embedding contracts exist, but no specific vendor/index is forced or bundled. Supply implementations before using dense retrieval.
5. **Dynamic executable skills:** there is no runtime that executes retrieved model/user text. That remains deliberately excluded.
6. **Full build:** this environment could not download Maven 3.10.0 due DNS failures for Apache hosts. The real Maven dependency graph and JUnit suite must be verified in CI/network-enabled environment; no successful full Maven run is claimed here.

## Local validation summary

Static checks, Java 21 compilation of the framework core and non-Spring test sources against temporary external-API stubs, and focused runtime smoke checks can validate source syntax, internal call consistency, and selected bounded-search/episode behaviors only. They do not replace `./mvnw -B clean verify` using the declared Spring Boot, Spring AI and Jackson dependencies. See Section 15 of `ARCHITECTURE.md` for the release gate and required external verification.
