# 前端与 ITLB

## 关注链路
- `Frontend.scala`：前端总线和 ITLB/ICache/IFU 连接。
- `NewFtq.scala`：FTQ、backend redirect、`backendIPF/backendIAF/backendIGPF`。
- `ICache.scala` / `ICacheMainPipe.scala`：ICache 主流水、前端异常汇总。
- `IPrefetch.scala`：ITLB 重发、prefetch 状态机。
- `IFU.scala`：取指、mmio/ITLB 交互、前端异常注入。
- `cache/mmu/TLB.scala`：ITLB/DTLB 核心逻辑。

## 常看信号
- `io_backend_toFtq_redirect_*`
- `io_backend_cfVec_*`
- `io_ptw_req_*` / `io_ptw_resp_*`
- `io_requestor_*_req/resp_*`
- `s1_need_itlb`、`s1_wait_itlb`、`itlb_finish`
- `state`、`next_state`
- `pf_instr`、`af_instr`、`gpf_instr`

## 常见前端 bug
- 顺序取指跨 canonical boundary，但没有产生 `pf_instr`
- redirect target 走了 backend 保护，fall-through 没走
- ITLB miss/PTW 成功，但 PA 指向错误 alias 页
- `satpFlush` / `flushPipe` / `kill` 之后仍然使用旧结果
- wrong-path 取指进入共享资源，导致时序侧信道或资源污染

## 读法
- 先区分“取指请求本身”与“redirect target”。
- 如果是 `exec` 类请求，确认 `vaddr`、`vpn`、`paddr`、`miss`、`pf`、`af` 的变化顺序。
- 如果 `pf_instr=0` 且页面明明非规范，优先怀疑 canonical check 缺失，而不是 PTE 问题。

## 相关源码提示
- backend redirect 的 page-fault 逻辑在 `backend/ctrlblock/RedirectGenerator.scala`。
- canonical 判定通常来自 `Bundle.scala` 里的 `AddrTransType.checkPageFault()`。
