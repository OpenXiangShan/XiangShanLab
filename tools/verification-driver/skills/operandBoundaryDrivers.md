# XiangShan Operand Boundary Drivers

Use this file when generating architecture or microarchitecture verification drivers for any instruction, functional unit, address-generation path, comparator, arithmetic datapath, floating-point datapath, vector datapath, CSR field, immediate decoder, mask generator, or memory request path.

Every driver must enumerate all relevant operand classes and traverse boundary values. The generated tests should combine boundary operands with exception, interrupt, queue, FSM, index, bus, and conflict scenarios when reachable.

## Operand Boundary Driver Shape

```markdown
## Operand Boundary Verification
| Operand class | Boundary set | Instruction/path | Stimulus construction | Expected result | Checkers |
| --- | --- | --- | --- | --- | --- |
```

For every operand class, include:

- Width and interpretation: signed, unsigned, floating, vector element, address, mask, CSR field, immediate, pointer, index, or protocol field.
- Boundary set: min, max, zero, one, negative one, sign bit, carry/borrow boundary, overflow boundary, alignment boundary, FP special operands (`qNaN`, `sNaN`, `+inf`, `-inf`, `+0`, `-0`, subnormal), page/cacheline/burst boundary, mask empty/full/single-bit, and reserved encodings.
- Cross product policy: exhaustive for small domains; pairwise or constrained-random with directed corners for large domains.
- Expected model: ISA model, spec-derived rule, reference function, scoreboard, or code-derived microarchitecture checker.

## Integer Operand Boundaries

| Operand ID | Boundary class | Values | Required checks |
| --- | --- | --- | --- |
| `O_INT_ZERO_ONE` | Identity/zero | `0`, `1`, `-1` | Add/sub/logical/compare/shift/branch identity behavior |
| `O_INT_SIGN` | Sign boundary | `INT_MIN`, `INT_MIN+1`, `-1`, `0`, `1`, `INT_MAX-1`, `INT_MAX` | Signed compare, branch, overflow-like internal conditions |
| `O_UINT_CARRY` | Carry/borrow | `0`, `1`, `UINT_MAX-1`, `UINT_MAX`, pairs causing carry/borrow | Unsigned add/sub/compare/carry chains |
| `O_SHIFT_AMT` | Shift amount | `0`, `1`, `XLEN-1`, `XLEN`, `2*XLEN-1` masked as spec requires | SLL/SRL/SRA and word-form shifts |
| `O_MUL_DIV` | Mul/div edges | `0`, `1`, `-1`, `INT_MIN`, `INT_MAX`, divisor `0`, divisor `-1` | MUL high parts, DIV/REM divide-by-zero and overflow cases |
| `O_IMMEDIATE` | Immediate edges | min/max signed immediate, zero immediate, sign-extension boundary, compressed immediate boundaries | Decode sign extension and ALU/address result |
| `O_BRANCH_CMP` | Compare edges | Equal, just-less, just-greater, signed/unsigned disagreement pairs | Branch decision and redirect target correctness |
| `O_BITMANIP` | Bit pattern | all-zero, all-one, alternating `0x55/0xaa`, one-hot each end, sign-bit only | Bit count, rotate, pack, min/max, extension ops |

## Floating-Point Operand Boundaries

| Operand ID | Boundary class | Values | Required checks |
| --- | --- | --- | --- |
| `O_FP_ZERO` | Signed zero | `+0`, `-0` | Compare, min/max, sign injection, conversion |
| `O_FP_INF` | Infinity | `+inf`, `-inf` | Arithmetic, compare, conversion, exception flags |
| `O_FP_QNAN` | Quiet NaN | canonical quiet NaN, non-canonical quiet NaN, payload variants, positive/negative sign encodings | NaN propagation, canonicalization, payload handling, compare behavior, invalid flag absence/presence per operation |
| `O_FP_SNAN` | Signaling NaN | signaling NaN, payload variants, positive/negative sign encodings, boxed and unboxed encodings when applicable | Quieting behavior, invalid flag, NaN propagation, compare behavior |
| `O_FP_NORMAL_SUBNORMAL` | Normal/subnormal | smallest subnormal, mid subnormal, largest subnormal, smallest normal, largest finite, just-below-normal boundary | Underflow/overflow/inexact and rounding |
| `O_FP_UNDERFLOW` | Underflow boundary | exact tiny result, inexact tiny result, result just below smallest normal, subnormal result rounded to zero, cancellation to subnormal | Underflow flag, inexact flag, tininess detection rule, rounded result per rounding mode |
| `O_FP_OVERFLOW` | Overflow boundary | largest finite operand/result, result just above largest finite, overflow to `+inf`/`-inf`, overflow rounded to max finite | Overflow flag, inexact flag, sign, saturation/infinity selection per rounding mode |
| `O_FP_ROUNDING` | Rounding boundary | halfway cases for RNE/RTZ/RDN/RUP/RMM/dynamic rounding | Result and fflags per rounding mode |
| `O_FP_CONVERT` | Conversion edges | int min/max, uint max, fp values just inside/outside integer range | Saturation/invalid/inexact behavior per spec |
| `O_FP_FMA_CANCEL` | Cancellation | large opposite-sign operands, tiny addend, exact/inexact cancellation | FMA precision and exception flags |
| `O_FP_CLASS` | Classification | every fpclass category | FCLASS result bits |

Floating-point tests must vary:

- Special operand class for every FP source position: `qNaN`, `sNaN`, `+inf`, `-inf`, `+0`, `-0`, positive/negative subnormal, smallest normal, largest finite.
- Exception/flag boundary: underflow, overflow, inexact, invalid, divide-by-zero when the instruction can raise it.
- FP width implemented by config: half/single/double when present.
- Rounding mode: all static modes and dynamic `frm`.
- `mstatus.FS`/`sstatus.FS` legality and dirty-state update behavior.
- NaN boxing for narrower operands in wider registers when applicable.

## Address Operand Boundaries

| Operand ID | Boundary class | Values | Required checks |
| --- | --- | --- | --- |
| `O_ADDR_ALIGN` | Alignment | aligned, misaligned by 1, halfword, word, doubleword, cacheline crossing | Misalign exception or split/replay behavior |
| `O_ADDR_PAGE` | Page boundary | page start, page end, access crossing page, superpage boundary | Translation, fault priority, tval, replay |
| `O_ADDR_CANONICAL` | Canonicality | lowest/highest legal virtual address, non-canonical values if applicable | Address fault/page fault behavior |
| `O_ADDR_PMP_PMA_IOPMP` | Protection | first/last byte inside region, first byte outside region, locked entry boundary | Access fault/deny behavior |
| `O_ADDR_CACHELINE` | Cacheline | line start, line end, crossing line, same set different tag | Cache index, refill, split, bank conflict |
| `O_ADDR_BANK_SET` | Bank/set | one address per bank/set, same bank different set, same set different tag | Bank conflict and hash/index behavior |
| `O_ADDR_MMIO_UNCACHE` | MMIO/uncache | cacheable/uncache boundary, MMIO error address, narrow write masks | Ordering, bus protocol, access fault |
| `O_ADDR_FETCH` | Fetch target | aligned target, compressed boundary, fetch block end, page-crossing target | IFU exception and redirect target |

Address boundary tests must combine with `skills/indexBusHashDrivers.md` for index/bank/hash generation, `skills/virtualizationProtectionDrivers.md` for page, PMP, PMA, IOPMP, guest translation, and MMIO/uncache protection, and `skills/systemVirtualizationPermissionDrivers.md` for read/write/execute permission traversal and guest/host phase interactions.

## Vector and Mask Operand Boundaries

| Operand ID | Boundary class | Values | Required checks |
| --- | --- | --- | --- |
| `O_VEC_VL` | Vector length | `vl=0`, `1`, `VLEN/SEW-1`, `VLEN/SEW`, `vlmax`, greater-than-vlmax input | Active element count and tail behavior |
| `O_VEC_VSTART` | Restart index | `0`, `1`, last active element, faulting element, out-of-range | Precise restart and exception behavior |
| `O_VEC_MASK` | Mask | all-off, all-on, first bit, last bit, alternating bits | Masked-off side effects and exception suppression |
| `O_VEC_SEW_LMUL` | Element grouping | min/max SEW, fractional/integer LMUL boundaries | Register grouping, illegal config, lane routing |
| `O_VEC_STRIDE_INDEX` | Address generation | zero stride, negative stride, max stride, duplicate indices, crossing pages | Memory ordering, faults, bank conflicts |
| `O_VEC_REDUCTION` | Reduction edges | empty active set, one active element, all active, NaN/overflow data | Reduction result and flags |
| `O_VEC_FP_LANE_SPECIAL` | Per-lane FP special operands | For every implemented FP vector lane: `qNaN`, `sNaN`, `+inf`, `-inf`, `+0`, `-0`, positive/negative subnormal, smallest normal, largest finite, underflow-producing operands, overflow-producing operands | Every lane accepts, routes, computes, flags, masks, and writes back the full FP special operand set without lane aliasing or lane-specific loss |

Vector floating-point tests must vary:

- Lane coverage: each implemented vector lane must be the active lane for every FP special operand class, and all lanes must also be tested active together with mixed special operands.
- Mask and tail policy: repeat per-lane special operand tests with the target lane active, masked-off, tail-undisturbed, and tail-agnostic when those policies are implemented.
- Cross-lane placement: rotate `qNaN`, `sNaN`, `+inf`, `-inf`, `+0`, `-0`, positive/negative subnormal, underflow-producing, and overflow-producing operands through every lane and every FP source operand position.
- Required checks: lane-local result, exception flags, mask suppression, writeback index, element ordering, reduction behavior, and no corruption of adjacent lanes.

## CSR, Privilege, and Control Operand Boundaries

| Operand ID | Boundary class | Values | Required checks |
| --- | --- | --- | --- |
| `O_CSR_FIELD` | CSR fields | reset value, all writable bits zero/one, reserved bits one, WARL edge values | Legalization, masks, side effects |
| `O_PMC_COUNTER` | Performance counter/control fields | reset value, `0`, `1`, `max-1`, `max`, sign-bit, carry boundary, all writable bits zero/one, reserved event encodings, illegal event encodings, inhibit on/off, overflow clear/set | Counter wrap/overflow, event select legalization, inhibit timing, privilege visibility, and side effects |
| `O_PRIV_MODE` | Privilege | U/S/M, HS/VS/VU, debug mode when implemented | Access legality and trap routing |
| `O_STATUS_BITS` | Status stack | xIE/xPIE/xPP, FS/VS/XS, MPRV/SUM/MXR, virtualization bits | Trap return, permission, dirty-state behavior |
| `O_INTERRUPT_MASK` | Interrupt enables | all disabled, one enabled, all enabled, delegated/nondelegated | Interrupt priority and pending preservation |
| `O_PMP_PMA_IOPMP_CFG` | Protection config | first/last entry, locked/unlocked, TOR/NA4/NAPOT boundaries | Permission match and deny behavior |

## Bus and Protocol Field Boundaries

| Operand ID | Boundary class | Values | Required checks |
| --- | --- | --- | --- |
| `O_AXI_ID` | ID/source | `0`, `1`, max ID, reused live ID | Outstanding tracking and response routing |
| `O_AXI_LEN_SIZE` | Burst fields | len `0`, `1`, max, size min/max, illegal crossing if relevant | Beat count, `last`, mask, alignment |
| `O_AXI_STRB_MASK` | Write mask | all-zero if allowed, one byte, first/last byte, all bytes | Store data and protocol legality |
| `O_TL_SOURCE_SINK` | TL source/sink | first/last source/sink, reused source, multibeat edge | TL ordering and response matching |

## Cross-Product Strategy

Use this strategy to keep generated tests tractable:

1. Exhaustive single-operand boundary sweep for each operand position.
2. Pairwise cross product between operand positions for two-source and three-source instructions.
3. Directed triple tests for known hazards: carry plus sign overflow, shift plus sign bit, FP `qNaN`/`sNaN` plus rounding, FP underflow/overflow plus rounding mode, address boundary plus privilege, vector mask plus faulting element.
4. Combine at least one boundary operand test with:
   - exception trigger
   - interrupt pending
   - redirect/replay
   - queue full/almost full
   - FSM busy state
   - bus backpressure
   - context switch

## Completion Checklist

Before an operand boundary driver is complete:

- All source operands, destination-dependent operands, immediates, masks, addresses, CSR fields, and protocol fields are classified.
- Integer tests include zero/one/sign/carry/shift/mul-div/bit-pattern boundaries.
- Floating tests include signed zero, infinity, quiet NaN, signaling NaN, subnormal, normal, underflow, overflow, rounding, conversion, FMA, and FP status legality.
- Address tests include alignment, page, superpage, cacheline, bank/set, MMIO/uncache, protection, and fetch boundaries.
- Vector tests include `vl`, `vstart`, mask, SEW/LMUL, stride/index, reduction boundaries, and per-lane FP special operand coverage for every implemented vector lane when vector FP is implemented.
- CSR/control tests include reset, writable masks, reserved bits, WARL edge values, privilege modes, status stack, interrupt masks, protection config, and performance counter/control boundaries.
- Boundary operand tests are combined with exception, interrupt, conflict, FSM, bus, index/hash, and context-switch scenarios when reachable.

