---
name: xiangshan-wave-analysis
description: Analyze XiangShan Kunminghu waveforms with disassembly, logs, and source. Use this skill for frontend, backend, Load/Store, cache, MSHR, redirect/kill/replay, branch prediction, wrong-path behavior, RVV, CSR, exceptions, interrupts, PMP/PMA, MMU, and page-table bugs.
---

# 香山波形分析

## 适用场景
- 目标是用波形定位香山昆明湖 bug 根因，而不是只读日志。
- 输入通常是 `FST/VCD/FSDB + 反汇编 + 仿真日志 + XiangShan 源码`。
- 默认使用 wavekit 读取波形，并以 `TOP.clock` 正沿采样，除非证据表明需要换边沿。
- 优先覆盖前端、后端、访存、Cache、MMU、CSR、异常与 RVV 问题。

## 默认流程
1. 先归一化目标指令：PC、反汇编、寄存器、副作用、异常可能性。
2. 再找波形锚点：先从前端 PC 锚定，再沿流水线跟到 `robIdx`。
3. 逐级检查边界：每个 `valid/ready` 接口都要确认 `fire = valid && ready`。
4. 需要时分域跟踪：前端预测/redirect、后端提交/异常、访存/cache/MSHR、MMU/页表。
5. 最后回到架构态：commit、difftest、CSR、trap、寄存器写回、异常目标。
6. 结论必须由波形和源码共同证明；没有波形证据不要下结论。

## 读波形的硬规则
- rename 之后不要只靠 PC；用 `robIdx`、`lqIdx`、`sqIdx`、物理寄存器继续跟踪。
- 访存指令要补齐 `addr/data/mask/size/signedness/aq/rl/amo`。
- redirect/kill/replay/nack/flush 都要记录源头、目标、时机和影响范围。
- wrong-path 和时序侧信道只在波形确实显示投机路径、恢复和共享资源竞争时再谈。
- 不要把性能 bubble 当成根因；只有在 bug 分析需要时才单独统计。

## 前端重点
- 关注 `FTQ -> BPU -> ICache/IFU -> ITLB -> IBuffer -> Decode`。
- 常见信号：`backendIPF/backendIAF/backendIGPF`、`satpFlush`、`flushPipe`、`isMisPred`、`req/resp/miss/pf/af/gpf`。
- 常见问题：canonical address、错误路径取指、ITLB miss/PTW、页表刷新、重定向后恢复。
- 参考文档：`references/frontend-itlb.md`。

## 后端重点
- 关注 `Decode -> Rename -> Dispatch -> ROB -> Issue -> Execute -> Writeback -> Commit`。
- 常见信号：`robIdx`、`ftqIdx`、`redirect`、`flushAfter`、`needFlush`、`exceptionVec`、`difftest_commit_*`。
- 常见问题：分支误判、执行单元异常、CSR/trap 产生 redirect、年轻指令清空。
- 参考文档：`references/backend-redirect-csr.md`。

## 访存与 Cache
- 关注 `LSQ / LoadUnit / StoreUnit / DTLB / PTW / DCache / MSHR / StoreBuffer`。
- 常见信号：`miss`、`hit`、`replay`、`nack`、`forward`、`pte`、`pmp`、`pma`、`block`、`queue full/empty`。
- 常见问题：翻译失败、权限失败、cache miss、MSHR 资源冲突、store-to-load forward 不一致、replay 风暴。
- 参考文档：`references/memory-cache-lsq.md`。

## ISA / CSR / MMU
- RV64/RVV：要核对指令语义、宽度、符号扩展、mask/tail、`vtype/vl/vstart/vxsat/vxrm`。
- CSR / 异常 / 中断：要核对 `mcause/mepc/mtval`、`scause/sepc/stval`、`mstatus/sstatus`、`satp/vsatp/hgatp`。
- MMU / PMP / PMA：要区分页表 walk、canonical 地址、权限检查、访问属性与访问异常。
- 参考文档：`references/isa-mmu-exception.md`。

## 源码阅读原则
- 先看 bundle/IO 定义，再看赋值和 gating，再看 state/FSM，最后回到波形。
- 只引用能从当前 XiangShan 源码树证明的信号含义。
- 读单域问题时优先打开对应参考文档，不要一次把全部知识塞进上下文。

## 参考流程
- `references/workflow.md`
- `references/frontend-itlb.md`
- `references/backend-redirect-csr.md`
- `references/memory-cache-lsq.md`
- `references/isa-mmu-exception.md`
