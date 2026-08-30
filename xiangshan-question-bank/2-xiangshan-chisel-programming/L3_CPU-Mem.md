# L5 CPU-Mem 方向 Chisel Programming 题目

## 统一题目格式

每题需提交设计文档、验证计划、Chisel 代码、Verilog 代码、黑盒参考模型、白盒参考模型、验证代码、Coverage、仿真结果、综合结果、Critical Path、Area、时序分析和后仿结果。YAML 需描述存储层级参数、接口协议、异常规则、测试 trace 和评分项。

## 题目 1：VIPT 实现两级 Cache

- 功能需求：实现 VIPT L1 与下级 Cache 交互，处理 index 使用虚拟地址、tag 使用物理地址、TLB 协同、alias/synonym 风险和 refill。
- 输出需求：输出两级 Cache 响应、TLB 协同状态、miss/refill 事务、alias 检测信息。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：架构文档、RTL、缓存/翻译模型、别名与并发测试、Coverage、综合/STA/后仿报告。

## 题目 2：VIVT 实现两级 Cache

- 功能需求：实现 VIVT 两级 Cache，支持上下文切换或 ASID 标记、虚拟地址 tag/index、flush 策略和一致性风险说明。
- 输出需求：输出命中响应、虚拟 tag 状态、flush 事件、性能统计。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：设计文档、Chisel/Verilog、参考模型、ASID/flush 测试、Coverage、综合报告。

## 题目 3：Page Cache 实现

- 功能需求：实现页级缓存结构，缓存页表遍历结果或页属性，支持查找、替换、权限属性、flush 和 refill。
- 输出需求：输出页缓存命中、物理页号、权限属性、替换事件。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：设计文档、RTL、页表模型、权限/替换测试、Coverage、综合与后仿报告。

## 题目 4：两级 Page Cache 实现

- 功能需求：实现 L1/L2 page cache 层级，支持多级查询、refill、替换、flush、页大小区分和性能计数。
- 输出需求：输出各级 hit/miss、翻译结果、refill 请求、统计计数器。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：架构文档、Chisel/Verilog、层级参考模型、随机页表测试、Coverage、综合/STA 报告。

## 题目 5：Trace Cache 实现

- 功能需求：实现 Trace Cache，缓存动态指令 trace，支持 trace tag、分支结束条件、填充、命中输出和 redirect 失效处理。
- 输出需求：输出 trace hit、指令包、目标地址、填充状态和预测统计。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：设计文档、RTL、前端 trace 模型、分支/redirect 测试、Coverage、综合与后仿报告。

## 题目 6：Load Queue + Load Pipeline

- 功能需求：实现 Load Queue 和 Load Pipeline，支持地址生成、TLB/Cache 请求、store-load forwarding、违例检测、重放和提交。
- 输出需求：输出 load 完成结果、异常、重放请求、队列状态和性能计数器。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：微架构文档、RTL、周期模型、依赖/异常/重放测试、Coverage、综合/STA/后仿报告。

## 题目 7：Store Queue + Store Pipeline + Store Buffer

- 功能需求：实现 Store Queue、Store Pipeline 和 Store Buffer，支持地址/数据分离、提交后写出、合并、异常和一致性顺序维护。
- 输出需求：输出 store 请求、buffer 状态、提交/回滚事件、异常信息。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：设计文档、Chisel/Verilog、周期模型、乱序提交测试、Coverage、综合报告。

## 题目 8：Load/Store Queue + Load/Store Pipeline 复用

- 功能需求：实现统一 LSQ 和复用访存流水线，处理 load/store 仲裁、forwarding、内存依赖预测、异常和 flush。
- 输出需求：输出访存流水事件、forwarding 结果、冲突/违例、性能统计。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：架构文档、RTL、白盒周期模型、复杂依赖测试、Coverage、综合/STA/后仿报告。

## 题目 9：Load/Store Misalign Buffer

- 功能需求：实现非对齐访存缓冲，将跨 cache line 或跨边界请求拆分、合并响应，并维护异常和重放顺序。
- 输出需求：输出拆分请求、合并数据、异常状态、buffer 占用。
- 难度级别：L5 高级。
- 预期工期：1-2 周。
- 交付物清单：设计文档、RTL、非对齐参考模型、边界访问测试、Coverage、综合/STA 报告。
