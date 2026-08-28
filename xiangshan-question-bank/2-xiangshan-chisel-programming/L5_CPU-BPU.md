# L5 CPU-BPU 方向 Chisel Programming 题目

## 统一题目格式

每题需提交设计文档、验证计划、Chisel 代码、Verilog 代码、黑盒参考模型、白盒参考模型、验证代码、Coverage、仿真结果、综合结果、Critical Path、Area、时序分析和后仿结果。YAML 需描述预测器参数、trace 格式、训练约束和评分项。

## 题目 1：TAGE 分支预测器实现

- 功能需求：实现简化 TAGE 预测器，包含 base predictor、多张 tagged table、折叠历史、useful bit、provider/alternate 选择、分配和老化策略。
- 输出需求：输出 taken、provider 表号、alternate 预测、分配事件、误预测恢复信息和准确率统计。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：架构文档、Chisel/Verilog、trace 驱动模型、长 trace 回归、Coverage、综合/STA/后仿报告。
