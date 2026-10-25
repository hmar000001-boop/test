# Deep CoALA use-case execution walkthroughs

These source-level guides trace the supplied Java/Spring project from HTTP request arrival to the final API response. They enumerate method-call ledgers for the shared request path and every decision cycle, explain the important code rather than only naming classes, include source excerpts with repository-relative paths and line ranges, and show working-memory/long-term-memory effects and exact deterministic fixture outputs.

## Documents

- `01-research-deliberative-deep-walkthrough.pdf` (71 pages) and matching Markdown: four deliberative cycles. Each cycle covers scoped memory retrieval, proposal, strict parsing, candidate evaluation, selection, selected-action commit, execution, and cycle close.
- `02-travel-plan-first-deep-walkthrough.pdf` (73 pages) and matching Markdown: six plan-first cycles. The plan is generated once and stored; each subsequent cycle consumes one pending step, validates prerequisites, executes the action, and records the observation. The scenario never books travel.
- `03-support-iterative-deep-walkthrough.pdf` (66 pages) and matching Markdown: two iterative reason-act-observe cycles. The fixture first classifies the request and then recommends safe escalation.

## Interpretation and limits

The guides document the source snapshot in this repository. The default `ScriptedLanguageModel` is deterministic fixture logic, not a real LLM. The search/calendar/flight/hotel/support integrations are simulated; no external service or account is contacted. The method ledgers cover application-controlled calls and identify framework dispatch separately rather than inventing Spring internals. Source line ranges refer to the source tree bundled with these guides.

The PDFs were rendered and inspected after generation. Their PDF page counts are 71, 73 and 66 respectively; each exceeds the requested 50-page minimum.
