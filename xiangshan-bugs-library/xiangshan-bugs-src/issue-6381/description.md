### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

Writing `SDT = 1` is supposed to clear `SIE`. When `menvcfg.DTE = 0`, however, `MstatusModule` decides whether a write to `sstatus` clears `SIE` by looking at the SDT bit **already stored** in `mstatus`, instead of the SDT value that write actually carries.

As a result, once `mstatus.SDT` holds `1` while `menvcfg.DTE = 0`, **any** explicit CSR write to `sstatus` re-clears `mstatus.SIE` to `0` — including a write whose purpose is to set `SIE = 1`, and including writes that touch only unrelated fields. Supervisor software cannot re-enable supervisor interrupts in this state, and the write fails silently.

Per specification, when `menvcfg.DTE = 0` the extension behaves as if not implemented and `sstatus.SDT` reads as zero, so `SIE` must be settable to `1`:

> "When `menvcfg.DTE` is zero, the implementation behaves as though Ssdbltrp is not implemented. When Ssdbltrp is not implemented `sstatus.SDT`, `vsstatus.SDT`, and `henvcfg.DTE` bits are read-only zero."

>"The SIE bit can only be set to 1 by an explicit CSR write if the SDT bit is being set to 0 by the same write **or is already 0**."

XiangShan itself masks the read (`sstatus.SDT := regOut.SDT && menvcfg.DTE`), so from the S-mode view `SDT` is already `0` and the `SIE = 1` write must take effect.


#### Buggy Code

`src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
(`kunminghu-v3` @ `ff4720da4`: L643–L659 · `kunminghu-v2` @ `e12436c7c`: L577–L593)

```scala
// when DTE is zero, SDT field is read-only zero(write any, read zero, side effect of write 1 is block)
writeMstatusSDT := w.wdataFields.SDT.asBool
writeSstatusSDT := Mux(this.menvcfg.DTE.asBool, wAliasSstatus.wdataFields.SDT.asBool, reg.SDT.asBool)
//                                              BUG: DTE=0 branch uses the stored SDT, should be false.B
writeSDT        := Mux(w.wen, writeMstatusSDT, wAliasSstatus.wen && writeSstatusSDT)

when (writeSDT) {
  reg.SIE := false.B          // overrides the SIE value written by the same sstatus write
}
```

With `menvcfg.DTE = 0` and `reg.SDT = 1`:

- `writeSstatusSDT = reg.SDT = 1`, regardless of the data being written;
- any sstatus write (`wAliasSstatus.wen = 1`) therefore makes `writeSDT = 1`;
- `reg.SIE := false.B` is emitted after `reconnectReg()`, so it overrides the `SIE = 1` the same write stored.

For comparison, the VS-level path uses `0.U` on the DTE-off branch, so `vsstatus` is not affected:

```scala
// VirtualSupervisorLevel.scala
writeSDT := Mux(this.menvcfg.DTE && this.henvcfg.DTE, w.wdataFields.SDT.asBool, 0.U)
```

#### Reference Models

The reference models seems appear to have the same issue.

**NEMU** — `src/isa/riscv64/system/priv.c`, `CSR_SSTATUS` write:

```c
write_sdt = new_val.sdt;
if (menvcfg->dte == 0 ) {
  sstatus_wmask &= ~SSTATUS_SDT;
  write_sdt = mstatus->sdt;
}
mstatus->val = mask_bitset(mstatus->val, sstatus_wmask, new_val.val);
if (write_sdt) { mstatus->sie = 0; }
```

**Spike** (`OpenXiangShan/riscv-isa-sim`) — `riscv/csrs.h` does not mask `mstatus.SDT` by DTE:

```cpp
reg_t read() const noexcept override {   // mstatus_csr_t
  return val;
}
```

so in `riscv/csrs.cc` the residual SDT survives the write mask and clears SIE:

```cpp
const reg_t mDTE = (state->menvcfg->read() & MENVCFG_DTE);
const reg_t adj_write_mask = sstatus_write_mask & ~(mDTE ? 0 : SSTATUS_SDT);
reg_t new_mstatus = (mstatus->read() & ~adj_write_mask) | (val & adj_write_mask);
new_mstatus = (new_mstatus & SSTATUS_SDT) ? (new_mstatus & ~SSTATUS_SIE) : new_mstatus;
```

With `DTE = 0`, `adj_write_mask` drops SDT, so `new_mstatus` inherits `SDT = 1` from the unmasked `mstatus->read()` and `SIE` is cleared.


### Expected behavior

While `menvcfg.DTE = 0`, a write to `sstatus` should not trigger any SDT side effect. A write setting `SIE = 1` should take effect, so `mstatus.SIE` and `sstatus.SIE` read `1` afterwards.

### Environment

- Repo
  - XiangShan commit id: `e12436c7c`


### To Reproduce

`menvcfg.DTE` resets to `0`, and the direct `mstatus` write path (`writeMstatusSDT := w.wdataFields.SDT`) is not gated by DTE, so M-mode can set `mstatus.SDT = 1` while DTE is off. 

```asm
li    t0, (1 << 24)
csrrs zero, mstatus, t0

li    t0, (1 << 1)
csrrs zero, sstatus, t0

csrr  t1, sstatus
csrr  t2, mstatus
```

### Additional context

_No response_
