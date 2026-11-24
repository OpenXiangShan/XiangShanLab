### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

On the current kunminghu-v3 head (`c8d7b3a5c`), a **segmented vector store whose data spans several registers (LMUL > 1)**, followed
shortly by another store-queue operation, trips one of two self-checks in `VirtualStoreQueue.scala` and the core never commits
another instruction:

* `XSError(retireValidReg && toPhysicalQueueRetirePtrNext =/= getEntryEndSqIdx(deqPtrVec.head - 1.U), "retirePtr update error!")`
  (`VirtualStoreQueue.scala:322`) — triggered by `vsseg2e8.v` at LMUL 4 followed by **any AMO**.
* In longer programs of the same shape (an LMUL 2 `vsseg4e32.v`, a second store, then a burst of multiplies) the neighbouring
  check `"Virtual StoreQueue release error!"` (`VirtualStoreQueue.scala:406`) fires instead. I have not yet reduced that one to a
  standalone program, so this report and its attachment cover the `retirePtr` check; I will add the second reproducer when I have it.

This is **new in the window ede751b7c..c8d7b3a5c**: the identical ELF runs to `RVTEST_PASS` on a stock `ede751b7c` emu (and on
`e43c569f8`) and aborts on `c8d7b3a5c`. The retire/dequeue bookkeeping that both checks guard was rewritten in that window by
`9051202e6 timing(lsq): pipeline VSQ recovery and dequeue paths` (the retire decision used to free entries is now taken one cycle
earlier than the copy that advances the dequeue pointer and the physical-queue retire count). I have not bisected to the commit
itself; it is the only change to this logic in the window.

With the assertion made non-fatal, the core commits the store and then nothing else for the rest of the run (thousands of idle
cycles), i.e. the `XSError` reports a real loss of forward progress, not a spurious check.

Difftest output on `c8d7b3a5c` (attached `run_difftest_c8d7b3a5c_vsq_segstore_amo.log`):

```
Assertion failed at build/rtl/VirtualStoreQueue.sv:4999.
Core 0: ABORT at pc = 0x1fefca1179954
Core-0 instrCnt = 173, cycleCnt = 8,801, IPC = 0.019657
Assertion failed at LogUtils.scala:133
[ERROR][time=                   0] SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner.lsq.storeQueue.virtualStoreQueue: retirePtr update error!
```

No architectural register differs before the abort — the reference model and XiangShan agree on every committed instruction;
the failure is XiangShan's own invariant.

### Expected behavior

The segmented store and the AMO commit normally and the program reaches `RVTEST_PASS`, as it does on `ede751b7c`. No `XSError` fires and the core keeps committing.

### Environment

- Hardware
  - CPU: Intel(R) Xeon(R) 6530P
  - Memory (GB): 498
  - Storage (GB): 1742
- Software
  - Operating system: Ubuntu 26.04 LTS
  - gcc version: gcc (conda-forge gcc 13.4.0-19) 13.4.0
  - clang version: not used
  - java version: openjdk version "20.0.2-internal" 2023-07-18
  - mill version: Mill Build Tool version 0.12.17
- Repo
  - XiangShan commit id: `c8d7b3a5c1abf3f42c954e61abba20dd27e02a21`
  - NEMU commit id (if difftest failed with NEMU): `513786c4c597dd628c6126144937372d6f7fc92e`
  - SPIKE commit id (if difftest failed with SPIKE): ``
- Build & Run
  - Build command: `make sim-verilog DISABLE_PERF=1 && make -C ./difftest emu-mk NUM_CORES=1 RTL_SUFFIX=sv EMU_TRACE= && make -C ./difftest emu NUM_CORES=1 RTL_SUFFIX=sv EMU_TRACE= OBJCACHE= -j24`
  - Run command (if applicable): `./build/emu -i vsq_segstore_amo.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 20000`
  - Workload (binary and source code) attached: `xs_v3_vsq_segstore_attachments.tar.gz`

### To Reproduce

A plain riscv-tests **env-p** program (attached), M-mode, standard `RVTEST_CODE_BEGIN` reset sequence.

`vsq_segstore_amo.S` — the trigger is two instructions; the lines before them only enable the vector unit, zero the vector
registers and put a few scalar instructions between that setup and the test (with the `csrs mstatus` immediately before the
store the pipeline flush hides the problem):

```asm
        li      t0, MSTATUS_VS
        csrs    mstatus, t0             # enable the vector unit
        vsetvli t0, x0, e64, m8, ta, ma
        vmv.v.i v0, 0                   # zero v0..v31
        vmv.v.i v8, 0
        vmv.v.i v16, 0
        vmv.v.i v24, 0
        csrwi   vstart, 0
        li      t0, 40
1:      addi    t0, t0, -1              # ~120 scalar instructions of spacing
        bnez    t0, 1b
        vsetvli t0, x0, e8, m4, tu, mu  # LMUL = 4
        la      t1, buf
        vsseg2e8.v v0, (t1)             # 2-field segmented store, 8 registers of data
        la      t2, buf2
        amomin.d t3, t2, (t2)           # any AMO right after -> VirtualStoreQueue assertion
        RVTEST_PASS
```

What matters, from variants I tried on `c8d7b3a5c`: LMUL 1 instead of 4 → passes; `vse8.v` instead of `vsseg2e8.v` → passes;
a scalar `sd` instead of the AMO → passes; `amoadd.w` instead of `amomin.d` → same assertion. Privilege mode (M/S/U) and whether
a trap or branch mispredict precedes the store make no difference.

Build and run:

```
riscv64-unknown-elf-gcc -march=rv64gcv -mabi=lp64 -static -mcmodel=medany -nostdlib -nostartfiles \
    -I env -I env/p -T env/link.ld vsq_segstore_amo.S -o vsq_segstore_amo.elf
./build/emu -i vsq_segstore_amo.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 20000
```

On `c8d7b3a5c` the emu aborts with the assertion above; on `ede751b7c` and `e43c569f8` the same ELF reaches `RVTEST_PASS` with no
difftest divergence (attached `run_difftest_ede751b7c_vsq_segstore_amo.log`, `run_difftest_e43c569f8_vsq_segstore_amo.log`).

[xs_v3_vsq_segstore_attachments.tar.gz](https://github.com/user-attachments/files/32410711/xs_v3_vsq_segstore_attachments.tar.gz)

### Additional context

_No response_
