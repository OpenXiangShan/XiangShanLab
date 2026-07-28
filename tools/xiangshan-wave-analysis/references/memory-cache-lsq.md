# 访存、Cache 与 MSHR

## 关注链路
- `mem/MemBlock.scala`
- `cache/dcache/*`
- `cache/mmu/TLB.scala`
- `cache/mmu/L2TLB.scala`
- `frontend/icache/*` 在取指缓存场景也要看

## 常看信号
- `valid/ready/fire`
- `addr`、`data`、`mask`、`size`
- `load/store`、`lqIdx`、`sqIdx`、`robIdx`
- `miss`、`hit`、`replay`、`nack`
- `mshr`、`store buffer`、`queue full/empty`
- `pmp`、`pma`、`pte`

## 读法
- Load/Store 指令要把地址生成、翻译、权限、Cache、回放分开看。
- 如果 `miss` 或 `replay` 反复出现，先找资源占用与队列背压，再找功能错误。
- 如果地址翻译成功但访问失败，再区分 `PMP/PMA` 与页表权限。

## MSHR / Cache 常见问题
- miss 合并后等待太久
- MSHR 满导致新请求回压
- refill / writeback 时序错位
- store-to-load forward 不一致
- meta/data ECC 或 invalidation 之后未正确恢复

## 波形判断
- 只有在 `valid && !ready`、`ready && !valid`、`fire` 缺失、或明确的 `replay/nack` 出现时，才把它写成性能或功能原因。
- 仅凭停顿不要直接断言 cache 或 MSHR 有 bug。
