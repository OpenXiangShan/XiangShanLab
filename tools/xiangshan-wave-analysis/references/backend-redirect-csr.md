# 后端、Redirect 与 CSR

## 关注链路
- `backend/ctrlblock/RedirectGenerator.scala`
- `backend/CtrlBlock.scala`
- `backend/fu/wrapper/BranchUnit.scala`
- `backend/fu/wrapper/JumpUnit.scala`
- `backend/fu/CSR.scala`
- `backend/ctrlblock/ROB.scala`

## 常看信号
- `redirect.valid`
- `redirect.bits.cfiUpdate.*`
- `isMisPred`
- `backendIPF/backendIAF/backendIGPF`
- `flushAfter`、`flushPipe`、`needFlush`
- `exceptionVec`、`hasException`
- `difftest_commit_*`

## 读法
- 先区分 redirect 的来源：分支误判、跳转目标、执行单元 fault、CSR/trap、load replay。
- 对 redirect 一律检查 `robIdx`、`ftqIdx`、`ftqOffset`、`target`、`taken/predTaken`。
- 若 `backendIPF` 只在 redirect target 上出现，不要默认它也覆盖顺序 fall-through。

## CSR / trap
- 始终对照 `mcause`、`mepc`、`mtval`、`mstatus`、`satp`。
- 有异常时要区分“机器态 trap”与“前端恢复所需的 redirect 标记”。
- 记录是否只改了 architectural state，还是同时触发了 younger work flush。

## 常见误区
- 只看 commit，不看中间 redirect。
- 只看异常号，不看触发它的 `fullTarget` 或 `pc`。
- 把 trap handler 的行为当成前端 bug。
