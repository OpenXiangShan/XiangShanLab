### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

## Location
`xiangshan/backend/fu/NewCSR/InterruptFilter.scala:300`

## Root Cause
`onlyC3Enable = Candidate3 & !Candidate123` is always false because Candidate3 is a subset of Candidate123.

## Scenario
1. M-mode writes hvip.VSEIP=1 with VGEIN=0, HVICTL not injecting SEI (VTI=0 or IID!=9 or IPRIO=0), and IPRIOM=1
2. Only Candidate3 is active (VS-mode SEI from hvip software write)
3. IID output: correct (SEI) via `Candidate123Reg & NoCandidate45Reg` condition
4. IPRIO output: wrong — the IPRIO Mux1H has no matching condition for this case
   - `onlyC3EnableReg` is always false (Candidate3 ⊆ Candidate123, so `Candidate3 & !Candidate123 = false`)
   - IPRIO outputs 0 (highest priority) instead of intended 255 (iprioOnlyC3, lowest priority)
5. Catch-all VS-mode SEI always reported as highest priority when IPRIOM=1

## Severity
Catch-all VS-mode SEI always reported as highest priority when IPRIOM=1, potentially causing incorrect interrupt prioritization in virtualized environments.

### Expected behavior

## Expected Behavior
When only Candidate3 is active, IPRIO should output 255 (iprioOnlyC3, lowest priority for catch-all interrupts).

## Actual Behavior
IPRIO outputs 0 (highest priority) because `onlyC3Enable` is always false, causing the IPRIO Mux1H to select no entry and default to 0.

### Environment

Detected by a static analysis tool.

### To Reproduce

Detected by a static analysis tool.

### Additional context

_No response_
