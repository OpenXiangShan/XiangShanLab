---
name: xiangshan-bug-analysis
description: Analyze XiangShan bug waveforms from FST/VCD/FSDB, logs, disassembly, and source. Use when root-causing frontend, backend, Load/Store, cache, MSHR, redirect/kill/replay, branch prediction, wrong-path, RVV, CSR, exception, interrupt, PMP/PMA, MMU, or page-table bugs.
---

# 香山 Bug 分析

## 适用场景
- 用户给出复现程序、反汇编、仿真日志、FST/VCD/FSDB 和 XiangShan 源码。
- 目标是定位 bug 根因，而不是生成新场景或重新跑仿真。
- 默认重点是前端、后端、访存、Cache、MMU、CSR 和异常路径。
- 需要精确 waveform 查询时，配合 `analyze-xiangshan-wavekit` 技能使用。

## 工作流
1. 先归一化目标：PC、指令、寄存器、副作用、异常预期。
2. 再找波形锚点：先用日志/反汇编/commit 找到目标 PC，再沿流水线往前追。
3. 逐级看边界：每个 `valid/ready` 都要确认 `fire = valid && ready`。
4. 继续带着身份走：rename 后用 `robIdx`、`lqIdx`、`sqIdx`、物理寄存器继续追。
5. 分域追因果：前端预测/redirect、后端执行/提交、访存/cache/MSHR、MMU/PMP/PMA。
6. 最后回到架构态：commit、difftest、CSR、trap、异常目标、寄存器写回。

## 证据规则
- 只写波形和源码共同证明的结论。
- 每个非显然判断都要有源码位置和波形周期。
- 只在波形证明时谈 redirect、kill、replay、wrong-path 或侧信道。
- 不要把 bubble 当根因；只有它解释功能现象时才记录。

## 分域重点

### 前端
- 关注 `FTQ -> BPU -> ICache/IFU -> ITLB -> IBuffer -> Decode`。
- 常看 `backendIPF/backendIAF/backendIGPF`、`satpFlush`、`flushPipe`、`miss`、`pf`、`af`、`gpf`。
- 重点问题：canonical address、错误路径取指、ITLB miss/PTW、redirect 后恢复、投机取指污染。
- 参考：`references/frontend-itlb.md`

### 后端
- 关注 `Decode -> Rename -> Dispatch -> ROB -> Issue -> Execute -> Writeback -> Commit`。
- 常看 `robIdx`、`ftqIdx`、`redirect`、`flushAfter`、`needFlush`、`exceptionVec`、`difftest_commit_*`。
- 重点问题：分支误判、执行单元异常、CSR/trap redirect、年轻指令清空、commit 不一致。
- 参考：`references/backend-redirect-csr.md`

### 访存 / Cache
- 关注 `LSQ / LoadUnit / StoreUnit / DTLB / PTW / DCache / MSHR / StoreBuffer`。
- 常看 `miss`、`hit`、`replay`、`nack`、`forward`、`pmp`、`pma`、`pte`、`queue full/empty`。
- 重点问题：地址翻译、权限、cache miss、MSHR 冲突、store-to-load forward、replay 风暴。
- 参考：`references/memory-cache-lsq.md`

### ISA / CSR / MMU
- RV64/RVV 要核对指令语义、宽度、符号扩展、mask/tail、`vtype/vl/vstart/vxsat/vxrm`。
- CSR / 异常 / 中断要核对 `mcause/mepc/mtval`、`scause/sepc/stval`、`mstatus/sstatus`、`satp/vsatp/hgatp`。
- MMU / PMP / PMA 要区分页表 walk、canonical 地址、权限检查、访问属性和访问异常。
- 参考：`references/isa-mmu-exception.md`

## 源码阅读顺序
- 先看 bundle / IO 定义。
- 再看赋值、gating 和握手条件。
- 然后看 state / FSM / replay / flush 逻辑。
- 最后回到波形验证语义是否一致。

## 输出要求
- 说明用的波形格式、采样边沿和 clock。
- 给出绝对 cycle 和 time。
- 给出对应源码文件和行号链接。
- 给出分阶段时间线、redirect 路径、commit/difftest 状态和根因结论。

## 参考文档
- `references/workflow.md`
- `references/source-map.md`
- `references/frontend-itlb.md`
- `references/backend-redirect-csr.md`
- `references/memory-cache-lsq.md`
- `references/isa-mmu-exception.md`
