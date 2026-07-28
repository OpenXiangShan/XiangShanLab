# 后端、Redirect 与 CSR

## 关注链路
- `backend/ctrlblock/RedirectGenerator.scala`
- `backend/CtrlBlock.scala`
- `backend/ctrlblock/ROB.scala`
- `backend/fu/wrapper/BranchUnit.scala`
- `backend/fu/wrapper/JumpUnit.scala`
- `backend/fu/CSR.scala`

## 常看信号
- `redirect.valid`
- `redirect.bits.cfiUpdate.*`
- `isMisPred`
- `backendIPF/backendIAF/backendIGPF`
- `flushAfter`、`flushPipe`、`needFlush`
- `exceptionVec`
- `difftest_commit_*`

## 读法
- 先分清 redirect 来源：分支误判、跳转目标、执行单元 fault、CSR/trap、load replay。
- 始终记录 `robIdx`、`ftqIdx`、`ftqOffset`、`target`、`taken/predTaken`。
- 不要把只作用于 redirect target 的 fault 标记，误当成顺序 fall-through 也会触发。

## CSR / trap
- 对照 `mcause`、`mepc`、`mtval`、`mstatus`、`satp`。
- 分清机器态 trap 和前端恢复标记。
- 记录是否清空 younger work，还是只改了 architectural state。
