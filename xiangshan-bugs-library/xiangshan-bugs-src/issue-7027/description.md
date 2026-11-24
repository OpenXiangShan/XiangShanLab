### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

The first pass of a PBMT=NC (or PBMT=IO) load still reads the DCache speculatively. If L1 holds a cacheable copy of the same physical line that is flagged with a TL corrupt, s3 merges that corrupt into the load's `hardwareError` bit. The load then has no path to complete: it is refused by LoadQueueUncache, it does not write back, and there is no replay cause. The ROB head waits on it forever. A cacheable load with the same late error completes and reports the hardware error.

s3 merges the delayed TL corrupt into the exception vector; only the NC pass that already carries data is exempt:

```scala
// src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1647-1651
s3_out.bits.uop.exceptionVec(hardwareError) := (s3_in.uop.exceptionVec(hardwareError) || s3_hw_err ||
                                               io.dcache.resp.bits.tl_error_delayed.tl_corrupt && !io.dcache.resp.bits.tl_error_delayed.tl_denied) && s3_vecActive && !s3_in.isPrefetch
when (s3_nc_with_data) {
  s3_out.bits.uop.exceptionVec := s3_in.uop.exceptionVec
}
```

Whether this pass writes back was decided in s2; the NC first pass and MMIO do not write back from s3:

```scala
// src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1374
val s2_safe_wakeup = !s2_out.rep_info.need_rep && !s2_mmio && (!s2_in.nc || s2_nc_with_data) && !s2_mis_align && !s2_real_exception
// src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1582
val s3_safe_writeback = RegEnable(s2_safe_writeback, s2_fire) || s3_hw_err
// src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1643
s3_out.valid                := s3_valid && s3_safe_writeback && !toMisalignBufferValid
```

LoadQueueUncache only takes NC/MMIO requests without an exception:

```scala
// src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:364
s2_enqueue(w) := s2_valid(w) && !s2_has_exception(w) && !s2_need_replay(w) && (s2_req(w).mmio || s2_req(w).nc)

### Expected behavior

The load either completes through LoadQueueUncache (this pass does not use DCache data) or reports a precise hardware error. It does not hang.

### Environment

- XiangShan: kunminghu-v2 `9c53db086a`, KunminghuV2Config, 1 core, Verilator emu, difftest disabled

### To Reproduce

[nc-first-pass-err.zip](https://github.com/user-attachments/files/35227466/nc-first-pass-err.zip)

Attachment: `nc-first-pass-err.zip`. It contains `nc-err.S`, `io-err.S`, the images `nc-err.bin` and `io-err.bin`, the controls `nc-control.bin` and `io-control.bin`, `build.sh` (rebuilds all four with LLVM and checks `SHA256SUMS`), and a short README.

The program makes a cacheable line Y dirty in L1, arms a one-shot L1 data pseudo-ECC error through CtrlUnit (0x38022000), evicts Y (ReleaseData carries corrupt) and reloads it (Grant carries corrupt, the L1 line is flagged; this cacheable reload traps with cause 19, which the handler counts and skips). Then, in S mode, it loads Y through its PBMT=NC alias (`io-err`: PBMT=IO alias). The controls skip the CtrlUnit write.

```bash
./build/emu -i nc-err.bin     --no-diff -C 200000
./build/emu -i nc-control.bin --no-diff -C 200000
./build/emu -i io-err.bin     --no-diff -C 200000
./build/emu -i io-control.bin --no-diff -C 200000
```

| Image | Result |
|---|---|
| `nc-err.bin` | prints `r 1`, `n …`, then nothing; `EXCEEDING CYCLE/INSTR LIMIT` at 200000 cycles, `instrCnt = 7821` |
| `nc-control.bin` | prints `r 0`, `v 0`; `HIT GOOD TRAP` at pc `0x800001e0`, 10628 cycles |
| `io-err.bin` | prints `r 1`, `n …`, then nothing; `EXCEEDING CYCLE/INSTR LIMIT` at 200000 cycles, `instrCnt = 7822` |
| `io-control.bin` | prints `r 0`, `v 0`; `HIT GOOD TRAP` at pc `0x80000220`, 10674 cycles |

### Additional context

_No response_
