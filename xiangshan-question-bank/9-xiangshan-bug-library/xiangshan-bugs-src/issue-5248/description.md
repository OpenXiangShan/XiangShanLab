### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

- The program contains five CSR accesses (`csrrw/csrrc/csrrs/csrrwi/csrrci`), targeting `hvictl` and `time`.
- Spike raises exception `cause=0x2` (illegal instruction) on all five instructions.
- XiangShan executes the same instructions with the same register values without exceptions and writes back normally.
- The two implementations therefore diverge on exception behavior for the same CSR sequence.


### Expected behavior

Raises exception cause=0x2 (illegal instruction) on all five instructions

### To Reproduce

  ```
  li x13, 0x0
  li x16, 0x969b
  csrrw x13, hvictl, x16
  li x16, 0x7fffffff
  li x18, 0xac04
  csrrc x16, hvictl, x18
  li x20, 0x15
  li x19, 0xdb48
  csrrs x20, hvictl, x19
  li x16, 0x0
  csrrwi x16, hvictl, 23
  li x13, 0x0
  csrrci x13, time, 0
  ```

### Environment

- XiangShan branch:master
- XiangShan commit id:d86bc65752a40d6c081651bc1f38625d8337df4d
- XiangShan config:default config


### Additional context

_No response_
