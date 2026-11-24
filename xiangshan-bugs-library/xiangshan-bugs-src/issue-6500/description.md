### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

When a `wfi` is woken by an enabled interrupt and the instruction right after it is a load, store, CSR access, fence, vset or AMO, that instruction is committed first and the interrupt is taken on the next "interrupt-safe" instruction after it. So `mepc != wfi + 4`, and the instruction following the WFI has executed *before* the interrupt handler.

Spec, `machine.adoc` 3.3.3 WFI (`norm:wfi_mepc_val`): "If an enabled interrupt is present or later becomes present while the hart is stalled, the interrupt trap will be taken on the following instruction, i.e., execution resumes in the trap handler and `mepc` = `pc` + 4." (The "resume for any reason / WFI as NOP" allowance is for cores that do not stall; XiangShan does stall — `hasWFI` blocks commit — and resumes because of this interrupt, so this sentence applies.)

Root cause, `Rob.scala`: wake-up and interrupt entry are two independent mechanisms and nothing ties the interrupt to the WFI's successor.

```scala
// 2e148b4 L421 / L576 / L1146-1151; v2 HEAD 5d393413 L420 / L575 / L1162-1167; v3 HEAD L460 / L608 / L1182-1187
when(RegNext(RegNext(io.csr.wfiEvent)) || ...) { hasWFI := false.B }   // wake-up: only unblocks commit, no trap
val intrEnable = intrBitSetReg && !hasWaitForward && deqPtrEntry.interrupt_safe && !deqHasFlushed
val allow_interrupts = !CommitType.isLoadStore(..) && !FuType.isFence(..) && !FuType.isCsr(..) &&
                       !isVset && !FuType.isAMO(..)
robEntries(..).interrupt_safe := allow_interrupts
```

The interrupt then goes through the normal path, which refuses to fire while the ROB head is not interrupt-safe, so the head instruction commits and the trap slides to the next safe one.

Spike takes pending interrupts at the start of every step (`execute.cc:248 take_pending_interrupt()`), so after `wfi` the trap is always on the next instruction with `epc = wfi + 4`, whatever that instruction is.

### Expected behavior

`wfi ; ld a0, flag` with a timer interrupt arriving during the WFI:

- Expected (spec / Spike): trap with `mepc = &ld`; handler runs and sets `flag`; then `ld` reads the new `flag`.
- XiangShan: `ld` commits and reads the *old* `flag`; trap taken on the instruction after it, `mepc = &ld + 2`.

Software impact: `do { wfi } while (!flag)` reads the stale flag and goes back to sleep (for a one-shot event: until some unrelated interrupt arrives); `wfi ; csrc mie/mstatus, ...` masks the source before the handler ever runs.


### Environment

- XiangShan kunminghu-v2, commit `2e148b4` (2026-03-16), DefaultConfig (`--issue E.b`). Cited lines identical on kunminghu-v2 HEAD `5d393413` and kunminghu-v3 HEAD `a2340a516` (2026-09-06).
- NEMU `67da68a2`, Spike `611a62b7` (ready-to-run), difftest `7b5fc3bb2`. difftest cannot see this: the reference is told to take the interrupt at whatever pc the DUT chose (`ArchEventChecker::do_interrupt` → `raise_intr`), so both sides agree on the wrong take-point.


### To Reproduce

Minimal sequence (M-mode, `mtvec` set, `mtimecmp` armed a few ticks ahead):

```
csrs mie, MTIE
wfi
ld   a0, 0(a1)      # a1 -> flag written by the handler
nop                 # XiangShan: trap taken here (mepc = this nop), a0 = old flag
                    # spec / Spike: mepc = &ld, a0 = new flag
```

Attached `csr-misc-poc` (nexus-am, M-mode), section **[C]**: arms `mtimecmp = mtime + delay` (delay swept 4..64), runs `wfi ; <insn> ; 8×nop`; the handler records `mepc`/`mcause` and writes `flag = 1`; the `ld` variant loads that flag.

```
make ARCH=riscv64-xs
./build/emu -i csr-misc-poc-riscv64-xs.bin --no-diff
```

Output on 2e148b4 (`mepc-wfi` = byte offset of `mepc` from the `wfi`; `ld`/`sd` are 2-byte compressed, `csrr`/`fence` 4-byte, so +6/+8 = the instruction *after* `<insn>`):

```
[C] wfi;nop    delay= 4..64  cause=0x8000000000000007  mepc-wfi=+4   OK                          (control, interrupt-safe)
[C] wfi;ld     delay= 4..64  cause=0x8000000000000007  mepc-wfi=+6   ld read flag=0 (stale)      VIOLATION
[C] wfi;sd     delay= 4..64  cause=0x8000000000000007  mepc-wfi=+6                               VIOLATION
[C] wfi;csrr   delay= 4..64  cause=0x8000000000000007  mepc-wfi=+8                               VIOLATION
[C] wfi;fence  delay= 4..64  cause=0x8000000000000007  mepc-wfi=+8                               VIOLATION
[C] wfi;ld x3  delay= 4..64  cause=0x8000000000000007  mepc-wfi=+10  ld read flag=0 (stale)      VIOLATION
```

Identical for every delay, so interrupt timing is not the factor — only the class of the instruction after the WFI is.

[wfi-mepc-poc-results.zip](https://github.com/user-attachments/files/31882182/wfi-mepc-poc-results.zip)

### Additional context

_No response_
