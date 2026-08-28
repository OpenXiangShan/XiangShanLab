# L4 CPU-BPU 方向 Chisel Programming 题目

## 统一题目格式

每题需提交设计文档、验证计划、Chisel 代码、Verilog 代码、黑盒参考模型、白盒参考模型、验证代码、Coverage、仿真结果、综合结果、Critical Path、Area、时序分析和后仿结果。YAML 需描述参数、接口、约束、测试场景和评分权重。

## 题目 1：G-share 分支预测器实现

- 功能需求：实现 GHR 与 PC 异或索引的 G-share 预测器，包含 PHT 查询、投机历史更新、提交/回滚训练和 flush 恢复。
- 输出需求：输出 taken、索引、历史快照、训练状态和预测统计计数器。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：设计文档、Chisel/Verilog、黑盒与白盒模型、随机分支 trace 测试、覆盖率、综合与后仿报告。

## 题目 2：Bi-mode 分支预测器实现

- 功能需求：实现 choice predictor、taken predictor、not-taken predictor 三类表项，按 PC 和历史选择预测方向并训练选择器。
- 输出需求：输出最终预测、各子预测器结果、choice 决策和更新写口信息。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：设计文档、RTL、参考模型、训练/误预测测试、Coverage、综合与时序报告。

## 题目 3：Branch Target Buffer 实现

- 功能需求：实现参数化 BTB，支持 tag 匹配、目标地址预测、分支类型记录、替换策略和执行端更新。
- 输出需求：输出 hit、target、branch type、way/index、更新冲突处理信息。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：接口文档、Chisel/Verilog、BTB 软件模型、定向与随机测试、Coverage、综合/STA/后仿结果。
