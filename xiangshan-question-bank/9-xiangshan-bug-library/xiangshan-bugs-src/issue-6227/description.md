### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

In Bare translation mode (satp/vsatp/hgatp all MODE=Bare), when a trap is taken at an address whose bits above the implemented VA width are set, genTrapVA selects the isBare path and ZERO-extends only the low PAddrWidth bits, dropping the high bits of the faulting address from the trap-address CSRs (mepc/mtval or sepc/stval). The NEMU difftest reference records the full 64-bit faulting address, so XS_value == reference & ((1<<PAddrWidth)-1).

RTL (src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala, genTrapVA; unchanged at 8a42e727):
```
      val bareAddr = ZeroExt(addr(PAddrWidth - 1, 0), XLEN)   // Bare : zero-extend
      val sv39Addr = SignExt(addr.take(39), XLEN)             // Sv39 : sign-extend
      val sv48Addr = SignExt(addr.take(48), XLEN)             // Sv48 : sign-extend
      trapAddr = Mux1H(Seq(isBare -> bareAddr, isSv39 -> sv39Addr, isSv48 -> sv48Addr, ...))
```
This is the same genTrapVA function as #5860 / #6156, but a DISTINCT sub-path. #5860/#6156 are a mode-PROVENANCE bug: XS selects the wrong translation mode for a first-fetch fault immediately after a satp/vsatp write (their fix, satpFlushFirstFetchFault, corrects the mode). This report is different: satp MODE is genuinely Bare at the fault, genTrapVA CORRECTLY selects the isBare branch, and the bug is the ZeroExt *within* that branch. In the attached difftest capture the fault is reached by an sret to a high address in established Bare mode (register-restore, then sret at pc 0x8000047c), NOT a first-fetch-after-satp-write, so #5860's window does not apply.

Raw difftest divergence capture attached (xs_difftest_capture_id_6_374.log):
  at the trap, satp MODE=Bare, sepc/stval read back 0x0000ffffffe00480 on XS vs 0xffffffffffe00480 on the NEMU reference.

[xs_difftest_capture_id_6_374.log](https://github.com/user-attachments/files/29938255/xs_difftest_capture_id_6_374.log)

### Expected behavior

On a trap taken in Bare mode, the trap-address CSRs (mepc/mtval, sepc/stval) should record the full faulting address, matching the reference — OR, if Bare-mode zero-extension to PAddrWidth is intended WARL behavior, that should be documented. Since these CSRs are WARL and the implemented width is implementation-defined, this is partly a question about the intended semantics of the isBare branch. Concretely: after an sret/mret to a high address in Bare mode, sepc/stval (or mepc/mtval) should read back the full address, not the low-PAddrWidth-bits-only value.

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
    - XiangShan commit id: `8a42e727cc58`  (kunminghu-v3; genTrapVA bit-identical since d97789d — attached capture was produced on
  d97789de12)
    - NEMU commit id (if difftest failed with NEMU): `045689730874`
  - Build & Run
    - Build command: `make emu NUM_CORES=1`
    - Run command: `./build/emu -i <workload.elf> --diff <riscv64-nemu-interpreter-so>`


### To Reproduce

Attached workload: xs6208_workload_trap_highaddr.elf  (rv64, loads at 0x80000000; symbols stripped, self-contained).
```
  Run under NEMU-difftest:
      ./build/emu -i xs6208_workload_trap_highaddr.elf --diff <riscv64-nemu-interpreter-so>
```
The workload runs in Bare mode (satp MODE=Bare) and reaches an `sret` (pc 0x8000047c) whose sepc holds a high address (bits above the implemented VA width set). The sret fetches at that high address -> instruction access fault -> genTrapVA(isBare) zero-extends the trap-address CSRs.

Reproduced divergence (verified; see attached capture), abort at pc 0x800027cc:
```
      scause : 0x0000000000000001   (instruction access fault)
      satp   : MODE = Bare
      sepc   : 0x0000ffffffe00480   (XiangShan  -- zero-extended, high bits dropped)
      sepc   : 0xffffffffffe00480   (NEMU reference -- full 64-bit)
      stval  : same divergence as sepc
```
So XS_value == reference_value & ((1 << PAddrWidth) - 1).

Minimal-sequence note: the essential trigger is just "instruction fetch to a high address (bits above VA width set) while satp MODE=Bare -> access fault". The attached workload is a captured self-contained reproducer; a hand-written bare-metal minimal did not run in the difftest emu (it requires the full riscv-tests reset scaffold for DUT/REF lockstep), so the captured ELF is provided instead.

[xs_workload_trap_highaddr.elf.zip](https://github.com/user-attachments/files/29938413/xs_workload_trap_highaddr.elf.zip)

### Additional context

Behaviorally inert in our observation: sret/mret from the truncated sepc/mepc resumes at the correct full-width address (XS re-derives the high bits on use), so this is a trap-CSR read-back / representation difference, not a control-flow error.

Distinct from #5860 / #6156: those are a mode-PROVENANCE bug -- XS selects the wrong translation mode for a first-fetch fault immediately after a satp/vsatp write (fixed by satpFlushFirstFetchFault). This report is the isBare ZeroExt path itself: at the fault satp MODE is genuinely Bare (see capture), genTrapVA CORRECTLY selects the isBare branch, and the bug is the ZeroExt within it. The workload does contain boot-time satp writes, but they only CONFIGURE Bare mode; the faulting sret occurs later in established Bare mode, not as a first-fetch after a satp write -- so #5860's window does not apply. Related: #5860 (merged on master), #6156 (open kunminghu-v3 port).
