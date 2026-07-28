# 工作流

## 1. 先定目标
- 先明确目标指令的 `PC / 指令 / 寄存器 / 内存 / CSR / 异常` 语义。
- 对应到 bug 时，先判断它属于前端、后端、访存、Cache、MMU、CSR 或 RVV 哪一类。
- 如果有反汇编，先确认宽度、地址、立即数和是否与波形 PC 一致。

## 2. 先找锚点
- 用波形里的 PC 信号找第一处命中，再沿 `robIdx` 或 `ftqIdx` 向后跟踪。
- 默认用 `TOP.clock` 正沿采样。
- 每个边界都要确认 `valid && ready`，不要默认传输已经发生。

## 3. 再分域
- 前端：FTQ、预测器、ICache、IFU、ITLB、IBuffer、decode。
- 后端：rename、dispatch、ROB、issue、execute、writeback、commit、CSR/trap。
- 访存：LSQ、DTLB、PTW、DCache、MSHR、store buffer、replay。

## 4. 最后收口
- 把波形证据、源码逻辑和架构态放在一起看。
- 结论里要明确：谁产生了 redirect、谁触发了 kill/replay、谁写回了架构态。
- 没有波形支撑的推断不要写成结论。

## 5. 报告顺序
1. 目标指令和预期语义
2. 波形锚点与时间线
3. 分流水线阶段分析
4. redirect / flush / replay 路径
5. commit / difftest / CSR / exception
6. 根因与修复建议
