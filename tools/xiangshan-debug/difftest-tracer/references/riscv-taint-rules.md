# RISC-V Taint Rules

## Contents

1. General rules
2. Instruction classes
3. Control, CSR, and exception state
4. Memory and ordering
5. Difficult cases

## General rules

Represent every tainted value with width and interpretation. Preserve XLEN truncation, W-instruction sign extension, immediate sign extension, shift masking, NaN boxing, vector element width, and byte masks. `x0` always reads zero and discards writes; do not propagate a false definition through it.

Track architectural registers by dynamic producer, not name alone. Renaming means two in-flight writes to `a0` have different physical producers. At commit, map back to the architectural register state compared by difftest.

## Instruction classes

| Class | Destination taint depends on | Extra checks |
| --- | --- | --- |
| Integer ALU | source regs, immediate | XLEN/W semantics, overflow truncation |
| Compare/branch | source regs | signedness, taken bit, target, fallthrough |
| `jal/jalr` | PC for link; source+imm for indirect target | low target bit clearing, alignment |
| Load | address inputs, selected memory bytes | translation, permissions, forwarding, endian, extension |
| Store | address inputs, data input, mask | ordering, visibility, aliasing |
| LR/SC/AMO | address, operands, reservation, old memory | PMA atomic support, aq/rl, read and write effects |
| CSR RMW | old CSR, source/zimm, privilege/config | read/write suppression rules, WARL behavior |
| FP | operands, `frm`, flags | NaN boxing, rounding, accrued `fflags` |
| Vector | operands, `vl/vtype/vstart`, masks | tail/mask policy, element grouping, partial traps |
| Fence/CBO/TLB op | prior/future memory or translation state | ordering and delayed event interfaces |

The static helper intentionally uses conservative mnemonic heuristics. Manually correct its def/use set for pseudo-instructions, custom XiangShan instructions, vector grouping, implicit CSR state, and calls.

## Control, CSR, and exception state

PC taint can come from a branch predicate, branch immediate, `jalr` base, prediction recovery, trap target, `xepc`, return-status fields, or an interrupt sampled between commits. Add control dependence to every instruction whose execution is conditional on a tainted decision.

CSR values may be changed explicitly, implicitly by trap/return, by counters/timers, or by implementation-defined/WARL normalization. For a CSR mismatch, determine which bits differ and slice each field separately. Do not assume the instruction named at the reporter directly writes that CSR.

For exception mismatch, seed taint from:

- operation class and access size;
- VA/PA and alignment;
- translation result and privilege;
- PMP/PMA/PBMT/device classification;
- exception-vector generation and priority;
- delegation, interrupt enable/pending, and trap selection;
- `xepc` choice, `xtval` source, and trap-vector calculation.

## Memory and ordering

Model a load as reading a particular dynamic memory version. Candidate producers include older stores in the same hart, StoreQueue forwarding, StoreBuffer, cache state/refill, MMIO/device response, DMA/other harts, and initialization. Resolve by PA plus byte mask and ordering, not by symbol name alone.

For a mismatching store, distinguish:

1. address/data computation;
2. queue allocation and completion;
3. architectural permission to retire;
4. difftest store event;
5. external memory visibility.

These points can occur at different cycles. AMOs combine a read result and a write effect; trace both. `aq` and `rl` add ordering dependencies even when arithmetic data is correct.

MMIO reads, timers, interrupts, random sources, and performance counters require synchronized inputs or difftest-specific handling. A value mismatch is not sufficient to assign DUT ownership until environmental equivalence is proven.

## Difficult cases

### Self-modifying code

Static ELF/disassembly becomes stale after a store changes executable bytes. Trace stores, coherence/fence behavior, fetched bytes, and committed instruction bits. Use the bytes from the failing run as authority.

### Compressed and variable-length instructions

Use instruction bytes to determine 16-bit versus 32-bit length. Trap handlers that blindly add 4 to `xepc` are unsafe for compressed instructions. Confirm the decoded length and whether the trapped instruction is actually skipped.

### Calls and returns

A call may define caller-saved registers through its callee and may read argument registers or memory. Static intra-procedural slicing must mark the call boundary unresolved unless the callee or dynamic trace is analyzed. Returns depend on the saved link register and stack/memory state.

### Undefined or unspecified state

Uninitialized registers/memory, WPRI fields, WARL normalization, NaN payload choices, vector-agnostic elements, races, and device timing can legally differ. Compare only architecturally constrained state and prove initialization before declaring a hardware bug.
