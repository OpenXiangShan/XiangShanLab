# 工作流

## 1. 先定目标
- 归一化目标 PC、指令、寄存器、副作用和异常预期。
- 识别测试属于前端、后端、访存、Cache、MMU、CSR 或 RVV 哪类。
- 如果有反汇编，先确认宽度、立即数、地址和 PC 是否一致。

## 2. 再找锚点
- 用日志、commit、trap 信息或 disassembly 先找到目标指令的波形锚点。
- 之后沿 `robIdx`、`ftqIdx`、`lqIdx`、`sqIdx` 或物理寄存器继续跟踪。
- 每个边界都检查 `valid / ready / fire`，不要默认传输发生。

## 3. 分阶段追踪
- 前端：预测、redirect、ITLB、IBuffer、decode。
- 后端：rename、dispatch、ROB、issue、execute、writeback、commit。
- 访存：LSQ、DTLB、PTW、DCache、MSHR、StoreBuffer。
- MMU ：page table、PMP/PMA、trap、interrupt、privilege。
- CSR : status, priviledge, control states

## 4. 收口
- 用波形证明因果，不要只看源码推断。
- 用 commit/difftest 验证架构态是否与预期一致。
- 根因、影响范围和修复建议要分开写。

## 5. 报告顺序
1. 目标与预期
2. 波形时间线
3. 分域分析
4. redirect / replay / flush
5. commit / difftest / CSR
6. 根因与修复建议
