### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

On a **unit-stride vector load whose data page is unmapped**, XS writes the wrong value to the `vstart` CSR when the load page-faults. The trap is taken on the **first element** (index 0) — the entire access lies in a single unmapped 4 KiB page — so `vstart` must be `0`. XS instead reports `vstart = 0x20` (32), a value **greater than the trapping element index**.

Both models agree on everything else about the trap — `scause` (0x0d, load page fault), `sepc`, and `stval` are identical between XS and the NEMU reference. The **only** architectural difference is `vstart`: 
- NEMU (reference): `vstart = 0x0`   ← trapping element index 0 (spec-correct)
- XS (DUT):         `vstart = 0x20`  ← 32 (= a `vl`/LMUL granule boundary)

Per the RISC-V "V" extension spec v1.0 §3.7, when a trap is taken during a vector instruction, `vstart` is set to the element index of the trapping element and **must be ≤ that index** so the instruction is correctly restartable. `0x20 > 0` violates this. If the trap handler maps the page and re-executes, resuming from `vstart = 32` would **skip elements 0..31**, which were never loaded — silent data corruption on trap-resume.

This is in the same vector-load fault-handling area as the VSegmentUnit segment-load deadlock reported separately, but it is a distinct, non-hanging defect: here the fault IS delivered correctly (same cause/epc/tval as the reference); only the `vstart` element index is wrong.

### Expected behavior

`vle8.v` (vl=64) whose base address `0x60020` is in an unmapped page should raise a load page fault (`scause=0x0d`, `sepc` = the instruction PC, `stval` = the faulting VA) **with `vstart = 0`** (the index of the trapping element — the first). This is NEMU's behavior on the identical binary. `vstart` must never exceed the trapping element index.

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
  - XiangShan commit id: `2b5769e8b2` - reproduced in the recent tip
  - NEMU commit id (if difftest failed with NEMU): `377a854`
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig`
  - Run command: `./build/emu -i repro_vstart_vle8.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 30000 --dump-ref-trace --dump-commit-trace`


### To Reproduce

Attached workload `repro_vstart_vle8.elf` — a bare-metal RV64GCV test running under Sv39 paging with the vector data page left **unmapped**. The relevant faulting sequence is:
```asm
        vsetvli t0, x0, e8, m4       # SEW=8, LMUL=4  -> vl = VLMAX = 64
        auipc   t1, 0x5e
        addi    t1, t1, -1600        # t1 = 0x60020  (VA whose page is NOT mapped)
        vle8.v  v16, (t1)            # unit-stride load, 64 bytes 0x60020..0x6005f
                                     #   -> load page fault on element 0
```

Steps:
1. Build the difftest emu on kunminghu-v3 (`make emu CONFIG=DefaultConfig`), using the NEMU from the same commit's `ready-to-run` submodule.
2. Run with ref-trace + commit-trace:
   ```
   ./build/emu -i repro_vstart_vle8.elf \
       --diff ./ready-to-run/riscv64-nemu-interpreter-so \
       -C 30000 --dump-ref-trace --dump-commit-trace
   ```
3. Observed: difftest aborts reporting **only `vstart` differs**:
   `vstart different ... right = 0x0, wrong = 0x20` (`right` = NEMU = 0, `wrong` = XS = 32). The NEMU ref-trace shows the load's base VA `0x60020` walks to a leaf PTE of `0x0` (unmapped) — i.e. the trap is on element 0, so the correct `vstart` is 0.
4. Expected: `vstart = 0` (as NEMU reports).

[xs_vstart_wrong_on_vector_fault.zip](https://github.com/user-attachments/files/30440330/xs_vstart_wrong_on_vector_fault.zip)

### Additional context

Suspected area: the vector-load exception path that computes the `vstart` write-back on a faulting element (the `io.csr.vstart` set in the ROB / the LSU vector-load exception buffer). XS appears to report `vstart` at a `vl`/LMUL granule boundary (here 32, half of vl=64 at EEW=8/LMUL=4) rather than the exact trapping element index.

Possibly related (same vector fault path; none covers this exact case — a plain unit-stride load, first-element page fault, `vstart` set past the trap element): 
- Ongoing vector misalign/exception rework (commit b240e1c "refactoring misalign and support vector misalign").
- #5767 (`vlseg…ff` fault-only-first, later-element fault) — related fault path.
- The VSegmentUnit unmapped-page **deadlock** reported separately (segment loads) — same broad area, different symptom.
- RISC-V V-spec discussion of vstart precision on traps: riscv-v-spec #924, #766.

Filing for maintainer debugging assistance during the vector-unit rework — this is reproducible, deterministic, single-CSR divergence with a clear spec reference(§3.7) and a 4-instruction faulting sequence.
