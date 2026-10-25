# End-to-end use-case walkthroughs

Each PDF explains one additional runnable scenario from request arrival to terminal result. The walkthroughs include the component communication path, code excerpts, expected scripted output, decision-cycle table, working-memory changes, and an explicit mock/integration boundary.

| Use case | Decision procedure | PDF | Editable source |
|---|---|---|---|
| Research brief | Deliberative proposal / evaluation / selection | [01 - Research brief PDF](01-research-deliberative.pdf) | [Markdown](01-research-deliberative.md) |
| Travel planning | Plan-first, one step per cycle | [02 - Travel planning PDF](02-travel-plan-first.pdf) | [Markdown](02-travel-plan-first.md) |
| Support triage | Iterative reason-act-observe | [03 - Support triage PDF](03-support-iterative.pdf) | [Markdown](03-support-iterative.md) |

## Run the use cases

Start the app with `./mvnw spring-boot:run`, then POST to `/api/use-cases/{id}/run`. Full curl examples and exact requests are in the corresponding documents and the main README. The default `scripted` language model is a deterministic fixture rather than an LLM. The external search/calendar/flight/hotel/support adapters in these scenarios are mocks.

## Conceptual fidelity

The CoALA paper is the reference for conceptual distinctions such as memory, action types, and the recurring decision-making procedure. The names of Java classes, endpoint and fixture contracts are implementation choices. None of these scenarios claims that CoALA mandates its exact software structure.
