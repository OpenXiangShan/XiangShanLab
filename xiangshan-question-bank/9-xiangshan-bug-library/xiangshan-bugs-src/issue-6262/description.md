### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A unit-stride segment load (`vlseg<nf>e32.v`) whose element address falls in an **unmapped page** causes the core to **deadlock**: it stops committing entirely and never delivers the expected load page fault. The hang is reported by the difftest watchdog as `No instruction of core 0 commits for 15000 cycles, maybe get stuck`.

Confirmed for **`nf=4`** (`vlseg4e32.v`) and **`nf=2`** (`vlseg2e32.v`) — two independently-found workloads, same hang, same faulting-page mechanism — so this is the `VSegmentUnit` segment-load fault-delivery path in general, not a single encoding.

The reference models take a normal load page fault at the same instruction and continue:
- NEMU: `scause=0x0d` (load page fault), `sepc` = the vlseg PC, `stval` = faulting VA.
- Spike (rv64gcv): `trap_load_page_fault`, `epc` = the vlseg PC, `tval` = faulting VA.

Because XS is frozen one trap behind, difftest first surfaces it as a CSR mismatch (`mode`/`mstatus`/`sstatus`/`sepc`/`stval` differ) — but those are a symptom of the hang (XS's `sepc`/`stval` are stale from the *previous*, correctly-handled fault); the underlying failure is the deadlock, not the CSR values.

### Expected behavior

`vlseg4e32.v` accessing an unmapped page should raise a load page fault (`scause=0x0d`) with `sepc` = the instruction PC and `stval` = the faulting element virtual address, trap to the handler, and — after the handler maps the page and returns — re-execute and complete. It must not stop committing / hang. This is the behavior of both NEMU and Spike on the identical binary.

### Environment

- Hardware
    - CPU: Intel(R) Xeon(R) CPU E5-2683 v4 @ 2.10GHz
    - Memory (GB): 503
    - Storage (GB): 2301
  - Software
    - Operating system: Ubuntu 24.04.3 LTS
    - gcc version: gcc (conda-forge) 13.2.0
    - java version: openjdk 20.0.2
    - mill version: 0.12.15
- Repo
  - XiangShan commit id: `2b5769e8b2` (kunminghu-v3 tip, 2026-07-20) — reproduced here.
  - NEMU commit id (if difftest failed with NEMU): `377a854`
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig` (plain build reproduces the hang)
  - Run command: `./build/emu -i min_vlseg4.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 20000 --dump-commit-trace`

### To Reproduce

Attached workloads — bare-metal RV64GCV tests running in U-mode under Sv39 paging; standard boot + page-table + trap handler, with the data page at VA `0x40000` intentionally left **unmapped** so the segment load faults on first touch: 
- **`min_vlseg4.elf`** — minimal (nf=4) reproducer. Its entire fuzz body is the four instructions below.
- **`repro_vlseg2.elf`** — second instance (`vlseg2e32.v`, nf=2, base `0x40030`) — same hang; shows the defect spans the `vlseg<nf>` family, not one encoding.

Full fuzz body of `min_vlseg4.elf`:
```asm
        vsetvli t0, x0, e32, m2      # 0x011072d7  (SEW=32, LMUL=2, vl=VLMAX=8 @ VLEN=128)
        lui     t1, 0x40             # t1 = 0x40000
        addi    t1, t1, 0x20         # t1 = 0x40020  (VA whose page is NOT mapped)
        vlseg4e32.v v24, (t1)        # 0x62036c07  unit-stride segment load, nf=4  -> HANGS here
```

Steps:
1. Build the difftest emu on kunminghu-v3, e.g. `make emu CONFIG=DefaultConfig`. Use the NEMU reference from the **`ready-to-run` submodule of the same commit** (`make emu` does this automatically). NOTE: a mismatched/older NEMU aborts at `difftest_init_v2` ("different states than DUT") due to a difftest arch-state size difference between commits — that is unrelated to this bug; just use the
   matching ready-to-run NEMU.
2. Run with the commit trace so the stuck instruction is visible:
   ```
   ./build/emu -i min_vlseg4.elf \
       --diff ./ready-to-run/riscv64-nemu-interpreter-so \
       -C 20000 --dump-commit-trace
   ```
3. Observed on XS: the core commits boot + `vsetvli` + `lui` (last committed PC = the `addi` that forms the base `0x40020`), then **stops committing entirely** — `vlseg4e32.v` and everything after never retire. The emu reports `No instruction of core 0 commits for 15000 cycles, maybe get stuck`; instrCnt freezes (~3557) and IPC collapses (~0.18) until the cycle cap.
4. Expected — and what NEMU and Spike (rv64gcv) both do on the identical binary: `vlseg4e32.v` raises a **load page fault** (`scause=0x0d`, `sepc` = the instruction PC, `stval` = the faulting element VA `0x40020`); the handler maps the page and execution resumes.


[xs_vlseg_unmapped_page_hang.zip](https://github.com/user-attachments/files/30216496/xs_vlseg_unmapped_page_hang.zip)

### Additional context

Suspected module: **VSegmentUnit**.

Waveform (trace-enabled emu, window around the stall) shows, once the vlseg (the frozen ROB-head uop) enters VSegmentUnit with its element address = the unmapped VA:
- the FSM oscillates between two states every cycle, continuously re-issuing the DTLB request for the same faulting address (`io_dtlb_req_valid` / `io_dtlb_resp_valid` toggle each cycle);
- `canTriggerException` is asserted, **but** the page-fault is never latched or delivered: `exceptionWithPf`, `instMicroOp_uop_exceptionVec_13` (load page fault), `io_exceptionInfo_valid`, and `io_uopwriteback_valid` all stay 0;
- consequently the uop is never written back, stays at the ROB head, and the core never commits again.

In short, VSegmentUnit appears to receive the TLB page-fault for the segment load's element but never converts it into a reported load-page-fault exception/writeback, so the instruction cannot retire.

Second instance (attached `id_60_49_vlseg2.elf`): `vlseg2e32.v v16,(t1)` with `t1=0x40030` unmapped — same hang, same VCD signature (VSegmentUnit `state` oscillating `0011<->0100`, DTLB re-requested every cycle, `exceptionVec_13`/ `io_exceptionInfo_valid`/`io_uopwriteback_valid` never asserted). Confirms the defect spans the `vlseg<nf>` family (nf=2 and nf=4), not one encoding.

Possibly related (same unit / fault path; none appears to cover this exact case — regular vlseg, first/whole-access page fault, silent deadlock):
- #6151 (VSegmentUnit, `vl=0` stall) — same unit, different trigger.
- #5767 (`vlseg2e8ff.v` fault-only-first, later-element fault -> critical error).
- #3830 (closed) — the scalar analog: a faulting scalar load deadlocks; this is the vector-segment counterpart on the current mainline.
- PR #6123 (merged, "vSegmentUnit needs to connect ready to IQ") is already in the tested commit and does not resolve this.
