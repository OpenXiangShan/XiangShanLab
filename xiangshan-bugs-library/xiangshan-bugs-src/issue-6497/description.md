### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

In `NewCSR/MachineLevel.scala`, bit 13 of `mip` gives a software write unconditional priority over the
hardware count-overflow request, without merging the two:

```scala
// MachineLevel.scala (mip module, "bit 13 LCOFIP")
when (fromSip.LCOFIP.valid || fromVSip.LCOFIP.valid || wen) {
  reg.LCOFIP := Mux1H(Seq(
    fromSip.LCOFIP.valid  -> fromSip.LCOFIP.bits,
    fromVSip.LCOFIP.valid -> fromVSip.LCOFIP.bits,
    wen -> wdata.LCOFIP,
  ))
}.elsewhen(lcofiReq) {
  reg.LCOFIP := lcofiReq
}
```

`lcofiReq` is a single-cycle pulse: `lcofiReqVec(i) := ofFromPerfCntVec(i) && !mhpmevent.OF` (`NewCSR.scala`),
where `toMhpmeventOF := !countingInhibit & counterAdd.head(1)` is only high in the cycle the counter wraps.
`mhpmevent.OF` is set in that same cycle by an independent module, and once OF=1 the counter never issues
another request (Sscofpmf semantics).

Consequently, if a hpmcounter overflows in the cycle in which a CSR write to `mip` (or to `sip` with
`mideleg[13]=1`, or `vsip` with `hideleg[13]&mideleg[13]`) takes effect and the written bit 13 is 0, the
request is discarded and never re-issued: the machine ends in **`mhpmeventN.OF = 1`, `scountovf[N] = 1`,
`mip.LCOFIP = 0`**, and stays there forever. No count-overflow interrupt is ever delivered for that counter.

The vulnerable window is actually two cycles for the read-modify-write forms (`csrrs/csrrc/csrrsi/csrrci`):
`wrapper/CSR.scala` samples `regOut` at `io.in.fire` (T0) and latches `wdata = regOut op src` into
`wdataReg`; the write is applied one cycle later via `wenLegalReg = RegNext(wenLegal)` (T1).
* request in T1: `wen` wins in the Mux1H above, request dropped;
* request in T0: `reg.LCOFIP` is set at the end of T0, but `wdataReg` already holds the stale value
  (bit13 = 0) read in T0, and T1 writes it back -> the freshly set bit is cleared.

Note that the most common real-world writer of these registers is exactly this RMW form:
`csrc sip, SSIP` (IPI acknowledge in S-mode software) or `csrs/csrc mip, ...` in M-mode firmware.
A pending LCOFIP is preserved by such an RMW in every cycle *except* these two, so the loss is purely a
hardware race.

**Measured on RTL** (Verilator emu, `--no-diff`, PoC attached; k = number of events after which the
armed counter wraps, swept over the whole burst so the overflow cycle walks through every cycle of 4
back-to-back CSR instructions):

```
csrrc x0,mip,SSIP      E=258 swept k=1..262  overflow=262  LOST=48  first_lost_k=43
csrrc mip (1/cycle ev) E=43  swept k=1..47   overflow=44   LOST=8
csrrc x0,sip,SSIP      E=258 swept k=1..262  overflow=262  LOST=48
ctrl csrrc mscratch    E=258 swept k=1..262  overflow=262  LOST=0        <- same timing, unrelated CSR
ctrl nop               E=42  swept k=1..46   overflow=46   LOST=0
replay k=43: mhpmevent11.OF=1 mip.LCOFIP=0
after delay: mip.LCOFIP=0 scountovf[11]=1 mhpmevent11.OF=1 -> PERMANENTLY LOST
```

`LOST` = trials that ended with OF=1 and LCOFIP=0. With a strictly 1-per-cycle event
(`backend_stall_cycle`) 4 RMW instructions lose exactly 8 requests (= 4 x 2 cycles); with
`decoder_utilization` (up to 6 per cycle, DecodeWidth=6) they lose 48 (= 4 x 2 x 6). Writing an
unrelated CSR with identical pipeline timing loses nothing.

**Spec references** (riscv-isa-manual, `src/priv/sscofpmf.adoc`, `src/priv/machine.adoc`):
* "If an hpmcounter overflows while the associated OF bit is zero, then a count overflow interrupt
  request is generated. If the OF bit is one, then no interrupt request is generated."
* "When an OF bit is set, it eventually, but not necessarily immediately, sets the LCOFIP bit in the
  mip/sip registers."
* mip: "LCOFIP ... reflects the occurrence of a local counter-overflow interrupt request resulting from
  any of the mhpmevent_n.OF bits being set."
* NOTE: "Once a counter has overflowed, it and the OF bit must be reinitialized before another overflow interrupt can be generated."

### Expected behavior

A hardware count-overflow request must never be lost because of a simultaneous software write to
`mip`/`sip`/`vsip`. Whenever `mhpmeventN.OF` becomes 1 by hardware, `mip.LCOFIP` must (eventually) be 1
unless software explicitly writes 0 to it *after* it was set. Concretely:
* the write cycle (T1) should merge the request: `reg.LCOFIP := writtenValue | lcofiReq`;
* for csrrs/csrrc the RMW must be atomic with respect to the hardware set, i.e. a request that arrives
  between the read (T0) and the write (T1) must survive (e.g. keep a sticky "request seen since fire"
  bit and OR it in the write cycle, or compute the RMW value in the write cycle from the current
  `regOut`).

With that, the PoC's `csrrc mip/sip` sweeps should report `LOST=0`, exactly like the `mscratch` and
`nop` control sweeps.

### Environment

- XiangShan branch: kunminghu-v2 (commit is also the base of the current master line; the relevant
  code on master is byte-identical: `MachineLevel.scala` L307-318 (mip bit 13), L376 (`toMhpmeventOF`),
  `NewCSR.scala` L1319/L1328 (`lcofiReq`), checked 2026-09-06)
- XiangShan commit id: 2e148b41d52307bb90d0e7812462dacd90bc8b33 (2026-03-16, v3.2.2-alpha-1084-g2e148b41d)
- XiangShan config: DefaultConfig (`--issue E.b`)
- NEMU commit id: 67da68a2590e3402d81729c7f9103fb1041fff80 (ready-to-run e79a631, riscv64-xs-ref_defconfig)
- SPIKE commit id: 611a62b7cee89447ae722b1fb5e55bf9b9ff4297 (ready-to-run riscv64-spike-so)
- difftest 7b5fc3bb2, utility fcb82239

### To Reproduce


Minimal instruction sequence (M-mode; `mhpmevent11` selects backend event 4 = `decoder_utilization`,
which counts during the CSR burst; k=43 is the first colliding overflow position on this build):

```asm
csrw  mip, x0              # LCOFIP := 0
li    t0, 4
csrw  mhpmevent11, t0      # OF := 0, EVENT0 = 4, all xINH = 0
li    t1, -43
csrw  mhpmcounter11, t1    # wraps after 43 events
li    a2, 2                # SSIP
csrc  mip, a2              # RMW, written bit13 = value read (0)
csrc  mip, a2
csrc  mip, a2
csrc  mip, a2
csrr  t2, mhpmevent11      # bit 63 (OF)   == 1
csrr  t3, mip              # bit 13 (LCOFIP) == 0   <- bug; stays 0 forever
```

[lcofip-lost-poc-results.zip](https://github.com/user-attachments/files/31870487/lcofip-lost-poc-results.zip)

Full PoC (nexus-am bare-metal app, sweeps k automatically, includes controls and persistence check):
`lcofip-lost-poc/src/main.c` in the attachment; prebuilt `lcofip-lost-poc/build/lcofip-lost-poc-riscv64-xs.bin`.

```bash
# build (nexus-am, ARCH=riscv64-xs)
make -C nexus-am/apps/lcofip-lost-poc ARCH=riscv64-xs
# run, ~212k cycles
./build/emu -i nexus-am/apps/lcofip-lost-poc/build/lcofip-lost-poc-riscv64-xs.bin --no-diff
# -> "=== BUG CONFIRMED: 104 overflow requests lost to same-cycle mip/sip writes ===", HIT BAD TRAP
# optional: with NEMU difftest it aborts with "mip different ... right = 0x2000, wrong = 0x0"
./build/emu -i ... --diff ready-to-run/riscv64-nemu-interpreter-so
```

Logs: `emu_nodiff_selfcheck.log` (full), `emu_nodiff_selfcheck_trimmed.log` (PERF lines removed),
`emu_diff_nemu.log`.

### Additional context

Root cause: hardware-set / software-cleared pending bit implemented as "software write has priority,
hardware request only in `elsewhen`", combined with the CSR RMW reading `regOut` one cycle before the
write. Affected modules: `backend/fu/NewCSR/MachineLevel.scala` (mip module, mhpmevent OF),
`backend/fu/wrapper/CSR.scala` (RMW timing).

Practical impact: silent, permanent loss of a perf-sampling interrupt. With `perf record` on a single
event the sample stream just stops (OF=1 disables further requests, no interrupt ever re-arms the
counter); with several events the lost counter is only serviced when another counter's interrupt
happens to fire. Triggers whenever a `csrc sip, SSIP` (IPI ack) / `csrs mip, SSIP` (firmware IPI
injection) / LCOFI handler `csrc sip, LCOFIP` lands within 2 cycles of an overflow.
