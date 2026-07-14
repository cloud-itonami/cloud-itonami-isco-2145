# cloud-itonami-isco-2145

Open Business Blueprint for **ISCO-08 2145**: Chemical Engineers — an ISCO
**Wave 1 (design & governance)** occupation per ADR-2607121000. Engineering
design work on chemical processes: the actor drafts and prepares engineering
analysis material (process design calculations, safety-review drafts) for the
licensed chemical engineer's own professional review and sign-off. The actor
itself NEVER issues a final certified engineering design or process-safety
certification.

**Maturity: `:implemented`** — ChemicalEngineersAdvisor ⊣
ChemicalEngineersGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-2141's industrial production actor.
15 tests / 31 assertions green.

The chemical engineering HARD invariants — safety discipline, not judgement:

1. **Project registration** — the project/facility record must be verified and
   registered before any design action can proceed.
2. **Effect gating** — a proposal's `:effect` must be `:propose` only. The
   actor NEVER issues a final certified design or safety certification.
3. **Scope exclusion** — the actor's operations are strictly limited to draft
   analysis preparation. It cannot itself sign-off on engineering designs or
   process-safety certifications (that role is reserved for the licensed
   chemical engineer).

Also HARD: non-`:propose` effect, certification/sign-off attempt. ESCALATIONS
(always human sign-off): `:flag-process-safety-risk` (hazardous material
handling, runaway reactions, overpressure scenarios — always escalates),
process design touching hazardous materials, low confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
