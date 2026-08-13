### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

In VMergeBuffer.scala, when a fault-only-first (FOF) load instruction (vle{8,16,32,64}ff.v) triggers a debug watchpoint with action=1 (enter debug mode) on an element after the first, the trigger information is silently discarded and vl is incorrectly reduced.

The entry.uop.trigger assignment is inside the when(!entry.fof || vstart === 0.U) branch, so the .otherwise branch (FOF non-first element) only reduces vl without preserving the trigger action:

```
when(!entry.fof || vstart === 0.U){
  entry.vstart       := vstart
  entry.exceptionVec := selExceptionVec
  entry.uop.trigger  := selPort.trigger   // trigger only saved here
  entry.vaddr        := vaddr
  ...
}.otherwise{
  entry.vl           := Mux(entry.vl < vstart, entry.vl, vstart)  // trigger lost
}
```

When the trigger is lost, the backend never sees the debug mode action, so the processor does not enter debug mode. The debug watchpoint event is lost.

### Expected behavior

Per RISC-V spec:

> "When the fault-only-first instruction would trigger a debug data-watchpoint trap on an element after the first, implementations should not reduce vl but instead should trigger the debug trap as otherwise the event might be lost."

When a debug watchpoint (action=1) fires on a non-first element of a FOF load, the processor should preserve the trigger action and propagate it to the backend so that debug mode is entered. The vl should not be reduced.

### Environment

- Repo
  - XiangShan commit id: `45eeffe`


### To Reproduce

I'm still debugging the POC and will provide it as soon as possible.

### Additional context

_No response_
