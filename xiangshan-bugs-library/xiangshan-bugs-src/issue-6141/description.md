### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

In EX_LAM_LAF_m (load address misalign and load access fault simultaneously occur in M-mode) batch test, **308** load-related cases mismatch between REF (NEMU) and DUT (Xiangshan). All mismatches are confined to pure-load cases under `mmio + acrossPage + acrossPMP`

Observed Behavior
- ref:
  mcause = EX_LAM (4, Load Address Misaligned)
- dut:
  mcause = EX_LAF (5, Load Access Fault)

Representative cases include:
- `ld/lw/lh/lhu/lwu`
- `fld/flw/flh`
- `hlv.* / hlvx.*`
- `c.ld / c.lw / c.lh / c.lhu / c.fld / c.ldsp / c.lwsp / c.fldsp`

Test Construction
- All mismatching cases are pure loads with non-zero offset, so the access itself is misaligned.
- All mismatching cases cross page boundary.
- All mismatching cases also cross two PMP TOR regions in MMIO space.
- **PMP read permission pattern is consistent across all 308 failing pure-load cases: first PMP region: `R = 1`
  second PMP region: `R = 0`**

Speciality:
- The full `mmio + acrossPage + acrossPMP` load-related bucket contains **2060 cases**.
- Among them:
  - **308 failing** cases are all pure-load cases with PMP read-permission pattern `(R1, R2) = (1, 0)`
  - **502 passing** pure-load cases all have `(R1, R2) = (0, 1)` or `(0, 0)`
  - **1250 passing** atomic-related cases (`lr/amo/amocas`) do not follow the same exception-selection behavior
- Therefore, the mismatch is not triggered by "MMIO + cross-page + cross-PMP" alone.
- The real trigger is the specific split-load situation: the first part of the load is still readable in the first PMP region, while the second part crosses into a non-readable PMP region.


This may **NOT necessarily** a hardware-design bug in Xiangshan, but indicates the mismatching behavior between Xiangshan and NEMU in certain configurations of load instructions.


### Expected behavior

Nemu and Xiangshan agrees on the exception type in the same `mmio + acrossPage + acrossPMP` scenario, independent of the privilege setting of two PMP regions.

### Environment

[bug-report2.tar.gz](https://github.com/user-attachments/files/29342133/bug-report2.tar.gz)


### To Reproduce

Please refer to the attachment of bug-report2.tar.gz, which provides one specific case of those 308 failing cases mentioned above. 

### Additional context

_No response_
