# 前端与 ITLB

## 关注链路
- `Frontend.scala` 连接 ITLB、ICache、IFU 和 FTQ。
- `NewFtq.scala` 负责预测、backend redirect 和前端恢复。
- `ICacheMainPipe.scala` 负责前端异常汇总和 fetch 管线。
- `IPrefetch.scala` 负责 ITLB 重发和 prefetch 状态机。
- `IFU.scala` 负责取指、ITLB 交互和异常注入。
- `cache/mmu/TLB.scala` 负责 ITLB/DTLB 核心翻译。

## 常看信号
- `io_backend_toFtq_redirect_*`
- `io_requestor_*_req/resp_*`
- `io_ptw_req_*` / `io_ptw_resp_*`
- `s1_need_itlb`、`s1_wait_itlb`、`itlb_finish`
- `state`、`next_state`
- `backendIPF`、`backendIAF`、`backendIGPF`
- `pf_instr`、`af_instr`、`gpf_instr`

## 常见 bug
- 顺序取指跨 canonical boundary，但没有 `pf_instr`。
- redirect target 走了保护路径，fall-through 没走。
- ITLB miss/PTW 成功，却翻译到了错误 alias 页。
- `satpFlush` / `flushPipe` 后仍继续用旧结果。
- wrong-path 取指污染共享前端资源。

## 读法
- 先区分“顺序取指”与“redirect target”。
- 再看 `vaddr -> vpn -> paddr -> exception` 的顺序变化。
- 如果 `pf_instr=0` 而地址显然非法，优先怀疑 canonical check 缺失。
