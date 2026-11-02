# QuadRing completion report

The planned functional implementation is complete: a four-channel ring carries a
CHI subset between two cached request nodes, one home node, and one memory node.
This report records delivered behavior, validation results, and model limits.
Architecture and transaction details are in [Design](design.md).

## Delivered behavior

| Area | Implemented behavior |
|---|---|
| Network | Four independent REQ/RSP/SNP/DAT rings; destination routing, FIFO buffering, forwarding priority, credit and bubble flow control |
| Integration | Diplomacy parameter negotiation and node-ID mapping; RN0, HN, RN1, SN connected to the actual ring transport |
| Non-coherent access | Complete-line reads and writes, initialized SN memory, transaction-ID preservation, and SN write completion |
| Coherent access | Configurable direct-mapped RN caches in I/SC/UC/UD, an HN directory covering SN memory, shared reads and unique ownership acquisition |
| Replacement and snoops | Clean Evict, dirty WriteBackFull, SnpShared/SnpUnique, dirty ownership transfer, and cancellation of writebacks whose data was taken by a snoop |
| Retry | RN-to-HN and HN-to-SN RetryAck/PCrdGrant, credit type zero, resource reservation, and credited reissue |
| Build and validation | Mill build, directed and deterministic random simulation, four separate RTL outputs, Verilator lint, and a shared local/CI script |

`ChiSystem` defaults to non-coherent operation. Enable caches and the directory
with `ChiSystemParams(coherent = true)`; the generator accepts `--coherent`.
Local commands read or replace aligned 64-byte lines. Each DAT flit carries one
complete 512-bit line.

Each RN has one local command in flight; HN serializes transactions globally.
An RN continues servicing snoops while its command or local result is waiting.
For coherent fills, RN sends CompAck before offering the local result, and HN
waits for that acknowledgement. For downstream writes, HN retains its context
until SN reports the actual write completion.

## Recorded validation

The V4 regression used the script committed in `1d5948f`, including its final
no-daemon Mill invocation. The following commit, `d831167`, changed only README
and design documentation. This report adds no hardware, test, or build changes;
the results below are from the completed V4 run, not a new simulation run.

| Check | Recorded result |
|---|---|
| Full `./test.sh --long` | 39 suites, 482 tests; zero failures, errors, or skipped tests |
| Single-ring random traffic | Five seeds, 100,000 cycles each, followed by drain checks |
| Non-coherent system random traffic | Five seeds, at least 100,000 cycles each, followed by readback and drain checks |
| Coherent system random traffic | Five seeds, at least 100,000 cycles each, followed by readback and drain checks |
| RTL generation | All four outputs generated successfully |
| Verilator lint | All four generated module sets passed independently |

The three random groups account for at least 1,500,000 simulated cycles, excluding
additional readback, drain, and directed-test cycles. Each group uses seeds
`1`, `0x5eed`, `0xc0ffee`, `7`, and `42`, with differing parameter configurations.

Directed tests cover routing, backpressure, message identities, malformed
responses, reset, cache transitions, snoop races, and retry admission. Random
tests compare data with independent reference models. Coherent tests enumerate
legal orders for overlapping local commands rather than infer execution order
from when a blocked result is consumed.

Test-only observers check actual cache and dirty-victim ownership, retry execution
and credit conservation, and every ring link's credit/FIFO balance. Drain checks
require empty FIFOs, restored link credits, and no pending retry reservations or
credits. Dedicated endpoint tests exercise SN retry contention, which the
product's globally serial HN does not naturally create.

Validation entry points and source links are listed in [Design](design.md#validation-and-model-limits).
The archived XML report and full local log were rechecked when preparing this
report. CI is configured to run the same long script; no remote CI result is
claimed here. Simulation and lint validate the exercised configurations and
traffic; they are not a formal proof of the protocol.

## Reproduction and outputs

Use JDK 21, Verilator, a C++ compiler, and Make. The repository pins Mill 1.1.2,
Scala 2.13.17, and Chisel 7.13.0, and compiles pinned Diplomacy/CDE submodules
from source.

```bash
git submodule update --init --recursive
./test.sh          # 5,000 random cycles per seed
./test.sh --long   # 100,000 random cycles per seed
```

Both commands compile, run all tests, generate RTL, and lint each output:

| Directory | Top module | Contents |
|---|---|---|
| `generated/ring/` | `Ring` | Standalone four-stop ring with 32-bit payloads |
| `generated/chi/` | `ChiNoCTop` | Four-channel CHI payload transport |
| `generated/system/` | `ChiSystem` | Non-coherent endpoint system |
| `generated/system-coherent/` | `ChiSystem` | Coherent endpoint system |

Each directory has its own `filelist.f`. Simulation artifacts are under
`build/chiselsim/`; generated and build outputs remain outside version control.
See [README](../README.md) for the short setup description.

## Scope boundaries

This is a functional CHI subset with Decoupled interfaces and fixed RN-to-HN-to-SN
routing. The generic ring is configurable; the assembled system has four endpoints.
It does not implement the complete CHI wire format or link layer, partial writes,
SD, CleanUnique/MakeUnique, direct memory transfer, forwarded snoops, or error
recovery. Reset clears in-flight work and caches and restores SN's configured
initial contents; it does not preserve dirty data.

Progress arguments assume finite offered traffic and eventual downstream
acceptance. Forwarding priority does not guarantee injection fairness under
unlimited competing traffic. Bounded waits in tests are checks under their
backpressure schedules, not a protocol latency guarantee.

Multiple outstanding commands per RN, concurrent HN transactions, performance
optimization, and performance statistics remain optional future work. They are
outside the completed functional scope.
