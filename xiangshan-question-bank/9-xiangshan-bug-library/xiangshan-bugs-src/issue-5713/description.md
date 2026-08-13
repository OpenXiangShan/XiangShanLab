### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
 [] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

According to the RISC-V External Debug Support Version 0.13.2,mcontrol6.chain field definition:

> Because chain affects the next trigger, hardware must zero it in writes to mcontrol6 that set dmode to 0 if the next trigger has dmode of 1.

<img width="605" height="231" alt="Image" src="https://github.com/user-attachments/assets/40282945-2341-4bfc-887e-abce6a6e23c5" />

The spec also defines another rule:

> In addition hardware should ignore writes to mcontrol6 that set dmode to 1 if the previous trigger has both dmode of 0 and chain of 1.

PR #4256 correctly fixed the second rule (backward check via canWriteDmode), but the first rule (forward check) was not implemented.

When writing tdata1 for trigger[N] with dmode=0 and chain=1, hardware should check if trigger[N+1] has dmode=1. If so, chain must be forced to 0. Currently, it seems that the chainable signal used to gate the chain write only checks chain length legality (TriggerCheckChainLegal), and does not check the next trigger's dmode.

The chainable signal is computed in Debug.scala:80-83:

```
val tselect1H = UIntToOH(tselect.asUInt, TriggerNum).asBools
val chainVec = mcontrol6WireVec.map(_.CHAIN.asBool)
val newTriggerChainVec = tselect1H.zip(chainVec).map{case(a, b) => a | b}
val newTriggerChainIsLegal = TriggerUtil.TriggerCheckChainLegal(newTriggerChainVec, TriggerChainMaxLength)
```

TriggerCheckChainLegal (in Trigger.scala:227-229) only checks that consecutive chain=1 bits do not exceed TriggerChainMaxLength:

```
def TriggerCheckChainLegal(chainVec: Seq[Bool], chainLen: Int): Bool = {
  !ConsecutiveOnes(chainVec, chainLen)
}
```

### Expected behavior

When writing tdata1 for trigger[N] with dmode=0 and chain=1, hardware should check if trigger[N+1] has dmode=1. If so, chain must be forced to 0.

### Environment

XiangShan config:DefaultConfig
XiangShan branch:kunminghu-v3

### To Reproduce

N/A — sorry, the issue was initially identified through code inspection. And I encountered some problems while trying to validate this bug, specifically when attempting to put the CPU into debug mode, so I’m sorry that I currently do not have an accurate reproducer to provide.

### Additional context

_No response_
