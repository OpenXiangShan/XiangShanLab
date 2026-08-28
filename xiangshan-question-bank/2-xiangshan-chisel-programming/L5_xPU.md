# L5 xPU 方向 Chisel Programming 题目

## 统一题目格式

每题需提交设计文档、验证计划、Chisel 代码、Verilog 代码、黑盒参考模型、白盒参考模型、验证代码、Coverage、仿真结果、综合结果、Critical Path、Area、时序分析和后仿结果。YAML 需描述张量形状、数据类型、存储接口、测试约束和评分项。

## 题目 1：池化算法实现

- 功能需求：实现 Max Pooling 或 Average Pooling，支持参数化窗口、stride、padding、通道数和定点数据格式。
- 输出需求：输出池化结果、valid/ready、窗口状态、边界填充处理信息。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：设计文档、RTL、Python/Numpy 参考模型、张量测试、Coverage、综合报告。

## 题目 2：CNN 算法实现

- 功能需求：实现卷积神经网络核心算子，支持卷积窗口、输入/权重缓冲、MAC 阵列、量化和激活函数。
- 输出需求：输出 feature map、访存事务、计算进度、性能计数器。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：架构文档、Chisel/Verilog、Numpy/PyTorch 模型、卷积测试、Coverage、综合/STA 报告。

## 题目 3：DNN 算法实现

- 功能需求：实现全连接层或 MLP 加速器，支持矩阵向量乘、bias、激活、批处理和片上缓存。
- 输出需求：输出层结果、访存请求、MAC 利用率、完成状态。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：设计文档、RTL、参考模型、量化误差测试、Coverage、综合报告。

## 题目 4：RNN 算法实现

- 功能需求：实现 RNN/LSTM/GRU 中一种循环神经网络核心，支持时间步迭代、隐藏状态保存、门控计算和定点量化。
- 输出需求：输出每步结果、隐藏状态、访存事务、性能统计。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：架构文档、RTL、模型比对、序列测试、Coverage、综合/STA/后仿报告。

## 题目 5：Transformer 算法实现

- 功能需求：实现 Transformer 中一个核心模块，例如 attention、QKV 矩阵乘、softmax 近似或 FFN，支持分块计算和片上缓存。
- 输出需求：输出 attention/FFN 结果、中间张量、访存统计、近似误差报告。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：微架构文档、Chisel/Verilog、PyTorch/Numpy 模型、端到端张量测试、Coverage、综合/STA 报告。
