### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

When executing the instruction `ori zero, zero, -1247` (`b2106013`), XiangShan incorrectly raises a Load Access Fault (Cause 5), while NEMU correctly treats it as a NOP.

According to the RISC-V Unprivileged ISA Specification, all instructions encoded as `ori x0, rs1, imm` are designated as HINTs. The spec explicitly states the core philosophy of these encodings:

> "These HINT encodings have been chosen so that simple implementations can ignore HINTs altogether, and instead execute a HINT as a regular instruction that happens not to mutate the architectural state."

The immediate `-1247` has `imm[4:0] = 10001`, which falls into the Reserved for future standard use space, rather than standard Zicbop prefetches:
- `imm[4:0] = 0b00000` → `prefetch.i`
- `imm[4:0] = 0b00001` → `prefetch.r`
- `imm[4:0] = 0b00011` → `prefetch.w`

Therefore, it should be treated purely as a NOP. No exceptions should be raised even if the calculated target address (`0xfffffffffffffb20`) is invalid.


The bug report is as follows：
[seeds_108_log.zip](https://github.com/user-attachments/files/28152894/seeds_108_log.zip)

### Expected behavior

XiangShan should treat the reserved hint as a NOP and continue running normally without mutating the architectural state, matching NEMU's behavior.

### Environment

- Repo
  - XiangShan commit id: `dcc1d26893afe367b73ec69e5e30faef2be7d505`
  - NEMU commit id:  bundled `riscv64-nemu-interpreter-so`


### To Reproduce

[seeds_108_.zip](https://github.com/user-attachments/files/28152841/seeds_108_.zip)

### Additional context

_No response_
