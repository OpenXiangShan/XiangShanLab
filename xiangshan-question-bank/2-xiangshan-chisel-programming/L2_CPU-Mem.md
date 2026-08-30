# L4 CPU-Mem 方向 Chisel Programming 题目

## 统一题目格式

每题需提交设计文档、验证计划、Chisel 代码、Verilog 代码、黑盒参考模型、白盒参考模型、验证代码、Coverage、仿真结果、综合结果、Critical Path、Area、时序分析和后仿结果。YAML 需描述缓存/TLB/一致性参数、接口协议、测试场景和评分项。

## 题目 1：MSHR 算法实现

- 功能需求：实现支持 primary/secondary miss 合并的 MSHR，跟踪 block address、请求元信息、refill 状态和唤醒顺序。
- 输出需求：输出分配结果、merge 结果、refill 完成响应、满/阻塞原因。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：设计文档、RTL、参考模型、miss/merge/flush 测试、Coverage、综合与时序报告。

## 题目 2：Block Cache 实现

- 功能需求：实现阻塞式 Cache，在 miss 期间阻塞后续请求，支持 tag/data/valid/dirty、refill、writeback 和替换。
- 输出需求：输出 CPU 响应、内存事务、cache hit/miss、状态机状态。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：设计文档、Chisel/Verilog、缓存参考模型、读写回归、Coverage、综合/STA/后仿报告。

## 题目 3：Non-block Cache 实现

- 功能需求：实现支持多个 outstanding miss 的 Non-blocking Cache，包含 MSHR、miss 合并、refill 写入、命中绕过和请求重放。
- 输出需求：输出多事务内存请求、CPU 响应、MSHR 状态、阻塞与重放事件。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：架构文档、RTL、事务级与周期级模型、并发 miss 测试、Coverage、综合/STA/后仿报告。

## 题目 4：SSID 算法实现

- 功能需求：实现 Store Set ID 预测结构，用于 load/store 依赖预测，包含 SSIT、LFST、冲突学习、清除和重新训练。
- 输出需求：输出 load 是否等待、依赖 store 标识、SSID 分配/合并事件。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：设计文档、Chisel/Verilog、依赖预测模型、冲突 trace 测试、Coverage、综合与后仿报告。

## 题目 5：TLB 实现

- 功能需求：实现参数化 TLB，支持 VPN 匹配、ASID、权限检查、page size、替换策略、refill 和 flush。
- 输出需求：输出 PPN、权限异常、hit/miss、替换 way、调试状态。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：接口文档、RTL、地址翻译模型、权限/大页/flush 测试、Coverage、综合/STA 报告。

## 题目 6：Page Table Walk 实现

- 功能需求：实现多级页表遍历状态机，支持 TLB miss 后发起内存读、解析 PTE、识别叶子项、生成物理地址和异常。
- 输出需求：输出翻译结果、异常类型、内存请求、PTW 状态和 TLB refill 信息。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：设计文档、Chisel/Verilog、页表参考模型、异常/大页/多级遍历测试、Coverage、综合与后仿报告。

## 题目 7：MSI/MESI/MESIF/MOESI 协议实现

- 功能需求：任选一种或多种缓存一致性协议，实现状态机、总线监听、读写升级、失效、回写和响应流程。
- 输出需求：输出 cache line 状态、总线事务、命中响应、一致性事件日志。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：协议文档、RTL、协议参考模型、多核访问测试、Coverage、综合/STA/后仿报告。

## 题目 8：Directory-Based 一致性协议实现

- 功能需求：实现目录式一致性控制器，维护 sharer vector、owner、line state，处理 GetS/GetM/Put/Inv/Ack 等事务。
- 输出需求：输出目录状态、下行探测请求、响应合并状态和异常事务检测。
- 难度级别：L4 中级。
- 预期工期：1 周。
- 交付物清单：架构文档、Chisel/Verilog、目录协议模型、多节点随机测试、Coverage、综合与后仿报告。
