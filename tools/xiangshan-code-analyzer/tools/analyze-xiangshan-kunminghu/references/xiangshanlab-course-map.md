# XiangShanLab Course Map

Use this file to locate foundation-level superscalar and out-of-order microarchitecture material in `OpenXiangShan/XiangShanLab.git`.

## Repository

- GitHub: `https://github.com/OpenXiangShan/XiangShanLab.git`
- Course root: `xiangshan-course/docs`

## Superscalar and Out-of-Order Foundations

Primary theory directory:

`课程体系4：实现篇-香山高性能处理器微架构优化/入门-超标量乱序处理器基础知识/`

Important files:
- `1.单周期vs.多周期vs.流水线.md`: pipeline basics and stage decomposition.
- `2.为什么需要超标量.md`: why superscalar/multi-issue exists.
- `3.结构冲突vs.数据冲突vs.控制冲突.md`: structural, data, and control hazards.
- `4.指令间的相关性.md`: instruction dependencies and hazard types.
- `5.单发射vs.多发射.md`: single-issue versus multi-issue execution.
- `7.Tomosulovs.ScoreBoard.md`: Tomasulo/scoreboard concepts, dynamic scheduling, dependency tracking.

## XiangShan Implementation Concepts

Primary implementation-concept directory:

`课程体系4：实现篇-香山高性能处理器微架构优化/初级-（香山里的实现、概念）高性能香山乱序流水线/`

Important files:
- `1.高性能乱序流水线经典划分.md`: pipeline partitioning.
- `2.一条指令在流水线执行过程中的状态.md`: instruction state across pipeline.
- `4.寄存器重命名.md`: register renaming.
- `5.Move指令消除.md`: move elimination.
- `6.DispatchQueue.md`: dispatch queue behavior.
- `7.IssueQueue.md`: issue queue behavior.
- `8.BypassNetwork.md`: bypass/forwarding network.
- `9.ExecuteUnit.md`: execute unit role.
- `10.PhysicalRegister.md`: physical register file.
- `11.PhysicalRegisterCache.md`: physical register cache/reg-cache concept.
- `12.CSR.md`: CSR role.
- `13.ROB.md`: reorder buffer and commit.

## Code-Oriented Analysis Lessons

Primary code-analysis directory:

`课程体系4：实现篇-香山高性能处理器微架构优化/中级-基于代码进行分析/`

Important files:
- `1.译码.md`
- `2.寄存器重命名.md`
- `3.Move指令消除.md`
- `4.分发阶段.md`

## Dynamic Instruction Execution Case Studies

Primary dynamic-analysis directory:

`课程体系5：解析篇-指令在香山处理器的动态执行解析/`

Important files/directories:
- `一条ADD指令的简单分析过程.md`
- `一条LOAD指令的简单分析过程/index.md`
- `一条STORE指令的简单分析过程.md`
- `一条AMO指令的简单分析过程/index.md`
- `一条Jal_Jalr指令的执行过程.md`

## Search Strategy

Use `rg` over the course root with both Chinese and English terms:

- Superscalar: `超标量|多发射|single issue|multi issue|superscalar`
- Out-of-order: `乱序|out-of-order|动态调度|Tomasulo|ScoreBoard`
- Hazards: `结构冲突|数据冲突|控制冲突|相关性|RAW|WAR|WAW`
- Pipeline: `流水线|pipeline|发射|执行|写回|提交`
- Structures: `重命名|物理寄存器|IssueQueue|BypassNetwork|ROB|DispatchQueue`
- Memory: `LOAD|STORE|AMO|访存|LSQ|StoreQueue|LoadQueue`

Use course docs to explain the concept, then verify the exact implementation in XiangShan source.
