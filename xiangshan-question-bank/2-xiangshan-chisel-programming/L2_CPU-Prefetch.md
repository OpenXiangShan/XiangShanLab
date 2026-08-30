# L4 CPU-Prefetch 方向 Chisel Programming 题目

## 统一题目格式

每题需提交设计文档、验证计划、Chisel 代码、Verilog 代码、系统级黑盒参考模型、周期级白盒参考模型、验证代码、Coverage、仿真结果、综合结果、Critical Path、Area、时序分析和后仿结果。YAML 需描述预取器参数、流量约束、测试 trace 和评分项。

## 题目 1：Stride 预取器实现

- 功能需求：实现基于 PC 的 stride 预取器，记录 last address、stride、confidence，并在置信度达到阈值时生成后续预取请求。
- 输出需求：输出 prefetch valid/address、命中表项、置信度变化、过滤/限流状态。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：设计文档、Chisel/Verilog、trace 参考模型、定向/随机访问测试、Coverage、综合与后仿报告。
