### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [ ] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

On a cache-block operation (`cbo.zero`/`cbo.clean`/`cbo.flush`/`cbo.inval`), the store trigger
compares only the *cache-block index* and **drops the configured `mcontrol6.match`**. `match=EQ`
happens to stay correct, but `match=GE` and `match=LT` no longer behave as GE/LT. Ordinary
scalar stores are unaffected.

Debug Spec 1.0, Sdtrig **Cache Operations** requires CBOs to match as stores, and:

> Only triggers with `mcontrol6.size`=0 and `mcontrol6.select`=0 will match. Since cache
> operations affect multiple addresses, there are multiple possible values to compare against.
> **Implementations must implement one of the following options.** From most desirable to least
> desirable, they are:
>
> 1. Every address from the effective address rounded down to the nearest cache block boundary
>    (inclusive) to the effective address rounded up to the nearest cache block boundary
>    (exclusive) is a compare value.
> 2. The effective address rounded down to the nearest cache block boundary is a compare value.
> 3. The effective address of the instruction is a compare value.

`match` is defined *over these compare values* (GE = "any compare value ≥ `tdata2`", LT = "any
compare value < `tdata2`"). Whichever option is chosen, the match type still applies. XiangShan
instead substitutes block-index equality, ignoring `matchType`:

```scala
// src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala  (MemTrigger)
val tiggerVaddrHit = Mux(isCacheLine, cacheLineEq,
                     Mux(isVectorStride, hitVecVectorStride, triggerHitVec))

def DcacheLineBitsEq(): (Bool, Vec[Bool]) = (
  io.isCbo.getOrElse(false.B),
  VecInit(tdataVec.zip(tEnableVec).map { case (tdata, en) =>
    !tdata.select && !debugMode && en &&
      tdata.store && io.isCbo.getOrElse(false.B) &&
      (vaddr >> DCacheLineOffset) === (tdata.tdata2 >> DCacheLineOffset)  // no matchType
  })
)
```

For a CBO the `Mux` selects `cacheLineEq` and discards `triggerHitVec` — the only branch that
passes `matchType` to `TriggerCmp` (`getTriggerHitVec()`). Scalar stores keep using
`triggerHitVec`, so they honour GE/LT correctly; only the CBO path is wrong.

**`match=EQ` is conformant and should be kept.** Under option 1, "some byte of the block equals
`tdata2`" is exactly "`tdata2` lies in the same block as the effective address", which is what
block-index EQ computes. But for GE/LT, block-index EQ (a bounded interval test) equals **none**
of the three permitted options (each a monotone half-line) — this is not merely the choice of a
weaker option:

| `match` | XiangShan | option 1 | option 2 | option 3 |
|---|---|---|---|---|
| EQ | block-index EQ | identical | broader | broader |
| GE | block-index EQ | **≠** | **≠** | **≠** |
| LT | block-index EQ | **≠** | **≠** | **≠** |

GE/LT are genuinely reachable: they are the legal `match` values
(`TrigMatch.legalValues = Seq(EQ, GE, LT)`), writable and readable by a debugger. The precondition
holds structurally — `Mcontrol6.SIZE` and `Mcontrol6.SELECT` are read-only 0 — so `size`/`select`
never suppress the match.

For comparison, Spike honours `match` on CBOs: it applies the full EQ/GE/LT compare
(`simple_match`) to every byte of the block-sized store (`riscv/triggers.cc`, driven with
`len=blocksz` from `mmu.h` `cbo_zero`/`clean_inval`), i.e. option 1.

The vector unit-stride path (`highBitsEq()`) has the same "EQ only" limitation, but Sdtrig
**Combined Accesses** phrases that as a *should*; **CBO is a must**

### Expected behavior

`DCacheLineOffset = 6`, so a 64-byte block. A CBO at `0x40` covers `0x40..0x7F`:

| trigger | spec (option 1) | XiangShan | failure |
|---|---|---|---|
| `match=GE`, `tdata2=0` | hit (some byte ≥ 0) | **miss** | missed watchpoint |
| `match=LT`, `tdata2=0x40` | miss (no byte in the block is `< 0x40`) | **hit** | spurious entry to Debug Mode |
| `match=LT`, `tdata2=0x400` | hit (`0x40 < 0x400`) | **miss** | missed watchpoint |

GE is a pure false negative; LT fails in both directions.

This breaks the standard range watchpoint (trigger0 = `GE lo, chain=1`; trigger1 = `LT hi`):
`TriggerCheckCanFire` chains over `tiggerVaddrHit`, which on a CBO is `cacheLineEq`. So a `cbo.zero`
clobbering 64 bytes in the middle of the watched range never fires unless both bounds happen to
fall in the same 64-byte block as the effective address — while the same address written by an
ordinary store does fire.

### Environment

- java version: openjdk version "23.0.2" 2025-01-21
- mill version: 0.12.3
- XiangShan branch: kunminghu-v2
- XiangShan commit id: `e12436c7cba86b195deec24981976d78bc263661`

### To Reproduce

1. Store trigger: `tdata1.type=6` (mcontrol6), `dmode=1`, `store=1`, `action=1` (enter Debug
   Mode; requires `dmode=1`, otherwise it is legalized to 0), `match=GE` (2), `tdata2=0`.
2. Execute `cbo.zero`/`cbo.flush`/`cbo.inval` on a later block, e.g. `0x40`.
3. Expected: enter Debug Mode. Actual: no fire.
4. Control: the same trigger on an ordinary store to `0x40` does fire — confirming the trigger is
   programmed correctly and only the CBO path drops `match`.

Range-watchpoint variant: trigger0 = `GE 0x1000, chain=1`, trigger1 = `LT 0x2000`, then `cbo.zero`
at `0x1800`. Expected: fire. Actual: no fire; an ordinary store to `0x1800` fires.

### Additional context

_No response_
