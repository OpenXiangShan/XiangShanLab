# Cross-Boundary Code Analysis

Use this reference for any analysis involving instruction fetch, data access, virtual memory, cache, bus, AXI/TL/APB, MMIO, prefetch, or address-range checks.

## Required Cases

Analyze concrete source-backed scenarios for every boundary reachable by the module:

- **Virtual-page crossing:** split at the page offset boundary; trace a separate translation, permission, privilege, ASID/VMID/PBMT/PMP/PMA check, exception field, and stale-context/flush behavior for each page.
- **Cache-line crossing:** split at the line boundary; trace line/set/way/beat address formation, independent hit/miss results, MSHR or refill allocation/merge/full behavior, response ordering, byte/instruction assembly, and replay or redirect handling.
- **MMIO/uncache crossing:** identify the exact PMA/PBMT/MMIO classification and do not treat it as an ICacheable transaction. Trace uncache/MMIO entry allocation, request and response handshake, ordering and side-effect constraints, commit gating, resend/retranslation, exception priority, and cancellation on redirect or flush.

If more than one boundary occurs in the same access, analyze the combined case in order: virtual address split, translation and permission, physical memory-type classification, cache/uncache routing, bus transaction, merge/assembly, and architectural visibility.

## Evidence Checklist

For each sub-request, cite exact effective Chisel lines for:

1. Address split and offset/line/page calculation.
2. Request valid/ready/fire and payload holding behavior.
3. Translation, PMA/PMP/PBMT/MMIO classification, and exception priority.
4. Cache hit/miss, line/beat selection, MSHR/uncache allocation, merge, and response arbitration.
5. Assembly or carry state such as half-instruction, byte mask, fragment valid, or response buffer.
6. Redirect, flush, retry, replay, commit, and context-switch cleanup.

State the producer, consumer, state transition, resource occupied, losing requester, and progress condition for every simultaneous event. If the source has no cross-boundary support, state the exact unsupported condition and the resulting fault, stall, or truncation instead of inferring behavior.

## Minimum Output Table

| Boundary | First fragment | Second fragment | Independent checks | Merge/ordering state | Failure and recovery |
| --- | --- | --- | --- | --- | --- |
| Virtual page | address and translation context | next-page address and context | ITLB/page/access/guest/PMP/PMA | fragment/half-instruction or access buffer | fault, flush, retry, or redirect |
| Cache line | line/set/beat request | next line/set/beat request | tag/meta/data and MSHR state | refill/response assembler | miss, merge, replay, or backpressure |
| MMIO/uncache | classified request | next request or response fragment | PMA/PBMT and ordering rules | uncache entry/response arbiter | commit wait, resend, exception, or cancel |

## Quality Gate

- Do not use only a textbook example; connect every claim to effective source signals and lines.
- Do not merge fragments before proving their ordering, validity, and exception semantics.
- Explicitly distinguish cacheable speculation from side-effect MMIO access.
- Include at least one simultaneous boundary-plus-redirect or boundary-plus-fault scenario when the module exposes those controls.
- Include the boundary cases in `验证特别注意` with named checkers and coverage points.
