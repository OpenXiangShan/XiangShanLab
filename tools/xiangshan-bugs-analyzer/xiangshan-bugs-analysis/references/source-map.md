# 源码地图

## 前端
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/NewFtq.scala`
- `src/main/scala/xiangshan/frontend/IFU.scala`
- `src/main/scala/xiangshan/frontend/IBuffer.scala`
- `src/main/scala/xiangshan/frontend/icache/ICache.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala`

## 后端
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/ctrlblock/RedirectGenerator.scala`
- `src/main/scala/xiangshan/backend/ctrlblock/ROB.scala`
- `src/main/scala/xiangshan/backend/fu/CSR.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala`

## 访存 / Cache
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`
- `src/main/scala/xiangshan/cache/mmu/L2TLB.scala`
- `src/main/scala/xiangshan/cache/dcache/CtrlUnit.scala`

## 关键看点
- `FTQ` 和 `ITLB` 决定前端是否合法继续取指。
- `ROB` 和 `CtrlBlock` 决定 redirect、kill 和 commit 行为。
- `LSQ / DCache / MSHR` 决定访存是否回放、重试或被阻塞。
- `PMP / PMA / page table` 决定地址能否被翻译和访问。
