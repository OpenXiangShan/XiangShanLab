# L4 SoC 方向 Chisel Programming 题目

## 统一题目格式

每题需提交设计文档、验证计划、Chisel 代码、Verilog 代码、黑盒参考模型、白盒参考模型、验证代码、Coverage、仿真结果、综合结果、Critical Path、Area、时序分析和后仿结果。YAML 需描述总线拓扑、地址映射、权限规则、测试场景和评分项。

## 题目 1：基于 Diplomacy 的 N-to-1 AXI Crossbar

- 功能需求：基于 Rocket Chip Diplomacy 风格或等价参数化机制，实现 N 个 AXI master 到 1 个 AXI slave 的 crossbar/仲裁器，支持读写通道独立仲裁、burst 锁定和响应路由。
- 输出需求：输出 AXI 五通道信号、仲裁状态、ID 路由表、阻塞与公平性统计。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：设计文档、Chisel/Verilog、事务参考模型、并发读写测试、Coverage、综合/STA/后仿报告。

## 题目 2：IOPMP 实现

- 功能需求：实现 I/O Physical Memory Protection 模块，支持多区域权限配置、地址匹配、读写执行权限检查、优先级规则和异常上报。
- 输出需求：输出访问许可、拒绝原因、命中区域编号、配置寄存器状态。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：寄存器文档、RTL、权限参考模型、边界地址测试、Coverage、综合与后仿报告。
