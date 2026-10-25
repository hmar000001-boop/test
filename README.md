# CoALA Java Framework

A Java 21 / Spring Boot reference project that models the conceptual architecture in **Cognitive Architectures for Language Agents (CoALA)** by Sumers, Yao, Narasimhan and Griffiths (TMLR, 2024). The paper is the authority for the cognitive-architecture model. Java interfaces, API behavior, limits and deployment controls in this repository are engineering decisions, not components prescribed by the paper.

## Included

- **CoALA-oriented core:** working memory; episodic/semantic long-term memory interface; reasoning and retrieval operations used during planning; grounding and learning actions; a recurring observation → planning → execution loop.
- **Six runnable use cases:** the original `dialogue-direct`, `coding-iterative`, and `puzzle-deliberative`, plus three full-chain integration examples: `research-deliberative`, `travel-plan-first`, and `support-iterative`.
  - `research-deliberative` — retrieves scoped memories, compares candidate next actions, uses the built-in `WebSearchGroundingAction` through a **synthetic provider**, formats a source-attributed evidence brief, stores an episode, and delivers the result.
  - `travel-plan-first` — produces a multi-step plan before execution, then checks a mock calendar, searches mock flights and hotels, assembles a draft itinerary, records an episode, and presents the draft. It never books.
  - `support-iterative` — repeatedly reasons from the latest observation, classifies a support request, and returns safe guidance or a **fictional** escalation reference. It never accesses an account or creates a real ticket.
  - All three new use cases are deterministic with the `scripted` profile. Their external data/actions are mock fixtures, not live provider calls. Activating `openai` changes LLM generation only; it does not turn the mock search/calendar/travel/support providers into live integrations.
- A REST API and per-session long-term-memory store for the examples.
- A deterministic **scripted offline adapter** for repeatable examples and tests, plus a narrow **Spring AI OpenAI adapter** for provider text generation only.
- A reusable built-in component catalogue: agent assembly/context factories, trace and completion hooks, prompt-context shaping, typed action contracts, memory context assembly, lexical/weighted/hybrid retrieval policies, optional vector ports, episodic/semantic learning, candidate proposal/evaluation/selection, plan-first/refinement/fixed-set/search templates, read-only calculator/workspace/HTTP/web-search/clarification actions, approval policies, action decorators, and a declarative skill catalog.
- Action-specific required/optional argument contracts, allow-listed dispatch, structured action state in working memory, bounded reasoning/search budgets, request limits, API rate limiting, optional bearer-token protection, tests, CI workflow and detailed architecture documentation.

## Requirements and offline run

Requirements: Java 21 or later. Maven 3.10.0 is the default for the included lightweight bootstrap scripts. If Maven is installed, the scripts use it; otherwise they download Maven 3.10.0 and verify its official SHA-512 checksum before extraction (network access is required on first use). If overriding `MAVEN_VERSION`, set `MAVEN_SHA512` to the official checksum for the platform archive (TAR.GZ on Linux/macOS, ZIP on Windows); unverified custom versions are rejected.

```bash
./mvnw -B clean verify
./mvnw spring-boot:run
```

The default LLM provider is `scripted`. It is fixture code, **not an LLM** and not a general reasoner. It provides predictable outputs without credentials or network calls so the decision procedure can be inspected and tested. The server binds to `127.0.0.1` by default.

## Use OpenAI through Spring AI

```bash
export OPENAI_API_KEY='your-key'
export OPENAI_MODEL='gpt-4.1-mini'  # optional
export SPRING_PROFILES_ACTIVE=openai
./mvnw spring-boot:run
```

The OpenAI profile configures a bounded 30-second provider timeout and bounded retries. The per-run reasoning-call budget is also bounded (default 50 calls). The exact supported model names and account availability are controlled by the provider.

Spring AI is isolated behind `dev.coala.llm.LanguageModel`; it performs provider text generation only. It is **not** used for agent orchestration, memory, planning, action dispatch or search. The framework asks the LLM for constrained JSON, parses it into data, validates it against the action registry and never executes model-generated code.

## API

- `GET /api/use-cases`
- `POST /api/use-cases/{id}/run` with JSON `{"goal":"...", "maxCycles":8, "sessionId":"previously-returned-session-id"}`
- `GET /actuator/health`

Example:

```bash
curl -X POST http://127.0.0.1:8080/api/use-cases/dialogue-direct/run \
  -H 'Content-Type: application/json' \
  -d '{"goal":"Explain CoALA in one paragraph"}'
```

The run response has a `sessionId` and a `run` object. Copy the returned server-generated `sessionId` into the `sessionId` field of a later request to resume that session's long-term memory; do not invent an ID. For example, after the first response, send `{"goal":"Use what you learned","sessionId":"<sessionId-from-response>"}`. If no ID is supplied, a new random ID is returned. Caller-chosen IDs cannot create or reserve sessions; an unknown, expired or evicted ID returns 404. The ID is an unguessable demo session capability but is not a substitute for authentication or authorization.

List all six runnable use cases:

```bash
curl http://127.0.0.1:8080/api/use-cases
```

Run the three additional examples (the exact goals below match the scripted fixtures):

```bash
curl -X POST http://127.0.0.1:8080/api/use-cases/research-deliberative/run \
  -H 'Content-Type: application/json' \
  -d '{"goal":"Prepare a research brief on retrieval-augmented generation"}'

curl -X POST http://127.0.0.1:8080/api/use-cases/travel-plan-first/run \
  -H 'Content-Type: application/json' \
  -d '{"goal":"Plan a 4-day trip to Paris next month within CAD 1400, check my calendar, and suggest flights and hotels. Do not book anything."}'

curl -X POST http://127.0.0.1:8080/api/use-cases/support-iterative/run \
  -H 'Content-Type: application/json' \
  -d '{"goal":"I cannot sign in after several attempts; my account is locked."}'
```

Each new use case has a dedicated execution walkthrough under [`docs/use-cases/`](docs/use-cases/README.md), with a separate PDF showing request arrival, component interactions, each decision cycle and the final result.

## API safety defaults

- Server binds to loopback (`127.0.0.1`) by default.
- Request goals are limited to 4,000 characters; JSON request bodies are limited to 32 KiB by default; model outputs are limited to 64,000 characters and action observations to 16,000 characters.
- API requests are limited to 30 per IP address per minute by default. This in-memory limiter is a local/demo guard, not a distributed production rate limiter.
- Set `COALA_API_TOKEN` to require `Authorization: Bearer <token>` for API calls. If `SERVER_ADDRESS` is configured to a non-loopback address, the app refuses to start unless `COALA_API_TOKEN` is set.
- Session long-term memory is isolated by a server-generated session ID, bounded to 1,000 sessions and expires after 30 minutes idle by default. Each session retains at most 32 records by default (oldest inserted records are evicted), and individual memory text is limited to 4,000 characters. The memory record cap must be at least 3 to retain the three seeded demo records. This store is in memory: process restart, TTL expiry or LRU eviction loses that session's learned data. A previously returned ID must exist to resume; clients cannot choose new IDs.
- External environment actions are mock implementations only. Before connecting real systems, add identity-aware session ownership, audited per-action authorization/approval, and deployment-appropriate distributed rate limiting and secret management.

Configuration keys are in `src/main/resources/application.yml` and `application-openai.yml`:

- `coala.run.max-cycles` (default 8; request override capped at 100)
- `coala.run.max-reasoning-calls` (default 50 per run)
- `coala.planning.max-candidates` (default 6, allowed range 2-20)
- `coala.planning.max-proposal-attempts` (default 3, allowed range 1-10)
- `coala.memory.max-sessions`, `coala.memory.max-records-per-session` and `coala.memory.idle-ttl`
- `coala.api.rate-limit.requests-per-minute`, `coala.api.max-request-bytes`, `coala.api.token`

## Fidelity boundary

Section 4 of the CoALA paper describes the conceptual pieces and their semantics; it does not prescribe Java interface names, REST endpoints, retry policies, session IDs or these three example procedures. `CoalaDecisionProcedure`, the shared loop class, the action registry, the scripted adapter and `SessionMemoryStore` are implementation choices shaped around the paper.

The deliberative example is **not** a full Tree of Thoughts, RAP, BFS, DFS, beam search or MCTS implementation. It implements one simple form of proposal, evaluation and selection, including re-proposal when all alternatives are rejected. The demos are a framework foundation, not production integrations for a real repository, travel provider, calendar or other external system.

Read the [detailed architecture document (PDF)](ARCHITECTURE.pdf) or its [editable Markdown source](docs/ARCHITECTURE.md) for the paper-to-code mapping, complete built-in component catalogue, runtime diagrams, decision-procedure comparisons, common agent compositions, customization recipes, safety boundaries and release gates. [`docs/REVIEW_FIXES.md`](docs/REVIEW_FIXES.md) maps each original review finding to the fix or explicitly documented scope boundary.

## Full execution walkthroughs

For the detailed request-to-outcome trace of the three demo agents, see [`docs/use-cases/deep-walkthroughs/`](docs/use-cases/deep-walkthroughs/README.md). Each PDF is 50+ pages and follows actual application-owned method calls across every cycle, explains source excerpts, records exact scripted outputs, and distinguishes mock fixtures from real provider calls.
