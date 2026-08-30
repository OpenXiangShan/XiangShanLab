# XiangShan Difftest Root-Cause Analysis

## 1. Conclusion

**Status:** Proven root cause / Strong candidate / Unresolved

**Analysis mode:** Static preliminary / Dynamic proof

**First bad architectural event:** `<hart, order/cycle, PC, bytes, event and differing bits>`

**Root-cause instruction or event:** `<PC/identity; say explicitly if different from reporter PC>`

**Ownership and repair boundary:** `<XiangShan / reference / difftest / workload / environment; module and behavior to change>`

## 2. Reproduction and Artifact Identity

| Artifact/configuration | Absolute path or value | SHA-256/revision | Notes |
| --- | --- | --- | --- |

State what was reproduced in this analysis and what came only from supplied artifacts.

Evidence labels: `E1 OBSERVED`, `E2 DERIVED`, `E3 CORRELATED`, `E4 PROVEN`, and `HYPOTHESIS`.

## 3. Failure Boundary

| Order/cycle | Hart | Identity | PC/bytes | Event | DUT | Reference | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |

Explain harness comparison semantics and distinguish reporter PC, first divergence, and root cause.

## 4. Taint Slice

| Edge | Producer | Consumer | Value/state | Evidence | Confidence |
| --- | --- | --- | --- | --- | --- |

List unresolved path, memory-alias, privilege, and environment dependencies.

## 5. Instruction Flow

| Cycle/time | Stage/port | valid | ready | fire | PC/bytes | ROB/uop identity | Payload/result |
| --- | --- | ---: | ---: | ---: | --- | --- | --- |

## 6. Control Flow

Describe the executed predecessor/successor, branch or redirect, and trap/interrupt/return state. Include predicted and resolved targets when relevant.

## 7. Data Flow

Describe register, CSR, address, memory-version, mask, forwarding, functional-unit, writeback, and commit edges. Explain forward propagation to the reported mismatch.

## 8. Earliest Wrong Boundary and Source Mapping

Show the last correct inputs, first wrong output, active source assignment/state transition, and exact source lines. Separate code facts, dynamic facts, and inference.

## 9. Competing Hypotheses

| Hypothesis | Supporting evidence | Refuting evidence | Status |
| --- | --- | --- | --- |

## 10. Repair and Regression Plan

Give the narrow repair boundary. Cover the failing case, nearby instruction/width/privilege variants, stalls/replay/flush, exceptions, and negative cases. State the required A/B observation.

## 11. Evidence Gaps

List every claim that still needs a commit trace, waveform signal, source revision, independent reference, or rerun. Specify the smallest next capture that would resolve it.
