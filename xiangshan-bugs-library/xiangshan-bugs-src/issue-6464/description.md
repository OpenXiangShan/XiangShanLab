## Summary

When `menvcfg.STCE=0`, a non-M-mode (e.g. HS) access to `vstimecmp` (`0x24D`) does **not** raise an illegal-instruction exception. The `menvcfg.STCE` privilege check in `CSRPermitModule` only covers `stimecmp` (`0x14D`).

This is still present on the default branch `kunminghu-v3` at HEAD `1df18918` (2026-09-02).

## Symptom

| Privilege | CSR | `menvcfg.STCE` | Expected | Actual |
|-----------|-----|----------------|----------|--------|
| HS (or any non-M) | `vstimecmp` (0x24D) | 0 | EX_II | no II |
| HS (or any non-M) | `stimecmp` (0x14D) | 0 | EX_II | EX_II (correct) |

`mcounteren.TM` already gates both addresses; only the STCE term is asymmetric.

## Root cause

In `src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala`:

```scala
private val rwStimecmp_EX_II = !privState.isModeM &&
                              (!mcounterenTM && (addr === CSRs.vstimecmp.U || addr === CSRs.stimecmp.U) ||
                               !menvcfgSTCE && (addr === CSRs.stimecmp.U))
```

The `!menvcfgSTCE` conjunct matches only `stimecmp`, not `vstimecmp`.

## How this was introduced

#6129 / `94a4b91a` (`fix(sstc): menvcfg.stce shouldn't control an attempt to access vstimecmp`) dropped `vstimecmp` from the STCE check. That change followed a then-current (incorrectly edited) Sstc wording that had lost "or vstimecmp".

That wording has since been restored:

- https://github.com/riscv/riscv-isa-manual/issues/3329
- https://github.com/riscv/riscv-isa-manual/pull/3344 (merged 2026-08-24)
- `norm:menvcfg_stce_op2` in `src/priv/machine.adoc`

This is leftover from #6129 after the spec correction, not a newly invented CSR rule.

## Spec / other implementations

- **Spec** (`norm:menvcfg_stce_op2`): when `menvcfg.STCE=0`, an attempt to access `stimecmp` **or** `vstimecmp` from a privilege mode less privileged than M raises an illegal-instruction exception.
- **Spike**: `stimecmp` and `vstimecmp` share `stimecmp_csr_t::verify_permissions`, which checks `MENVCFG_STCE` first.
- **NEMU**: [OpenXiangShan/NEMU#1093](https://github.com/OpenXiangShan/NEMU/pull/1093) applied the same asymmetry, so current XS↔NEMU difftest cannot catch this. An older NEMU (`is_sstc_csr` covering both `0x14D` and `0x24D`) would theoretically flag it, but existing CI does not exercise this path.

## Detection

The ISA rule `norm:menvcfg_stce_op2` was compiled (via [csrformal](https://github.com/aphlta/csrformal)) into a `CSRPermit/S3` assertion and checked against the full `CSRPermitModule` circuit. A counterexample is:

- privilege = HS
- `addr` = `0x24D` (`vstimecmp`)
- `menvcfg.STCE` = 0
- `EX_II` = false

## Suggested fix (not a PR)

Make the STCE term cover both addresses, e.g. restore the previous shape:

```scala
(!mcounterenTM || !menvcfgSTCE) && (addr === CSRs.stimecmp.U || addr === CSRs.vstimecmp.U)
```

or equivalently add `vstimecmp` to the existing `!menvcfgSTCE` conjunct.

I will not open a PR unless maintainers want one.
