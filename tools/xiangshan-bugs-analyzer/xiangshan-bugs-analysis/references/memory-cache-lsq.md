# 访存、Cache 与 MSHR

## 关注链路
- `mem/MemBlock.scala`
- `cache/dcache/*`
- `cache/mmu/TLB.scala`
- `cache/mmu/L2TLB.scala`

## 常看信号
- `valid / ready / fire`
- `addr / data / mask / size`
- `load / store / lqIdx / sqIdx / robIdx`
- `miss / hit / replay / nack`
- `forward / pte / pmp / pma`
- `mshr / queue full / queue empty`

## 读法
- 把地址生成、翻译、权限、Cache、回放分开看。
- 如果出现停顿，先找资源占用和背压，再找功能错误。
- 只有在波形给出明确证据时，才把现象归因到 MSHR、StoreBuffer 或某个队列。

## 常见问题
- miss 合并后等待过久
- MSHR 满导致回压
- refill / writeback 时序错位
- store-to-load forward 不一致
- ECC 或 invalidation 后恢复失败
