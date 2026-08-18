# 香山源代码解析

本目录用于沉淀对 [OpenXiangShan/XiangShan](https://github.com/OpenXiangShan/XiangShan.git) 源代码的模块化解析，重点围绕前端、后端、Cache 与访存系统展开。文档目标不是复述源码，而是把关键模块的设计意图、有效代码路径、接口边界、流水级行为和验证关注点串起来，方便按源码目录逐层阅读。

英文版入口见 [README_EN.md](README_EN.md)。

## 源码获取

```bash
git clone https://github.com/OpenXiangShan/XiangShan.git
cd XiangShan
```

推荐结合源码仓库和在线阅读工具交叉查看：

- 源码仓库：[https://github.com/OpenXiangShan/XiangShan.git](https://github.com/OpenXiangShan/XiangShan.git)
- Zread 源代码参考：[https://zread.ai/OpenXiangShan/XiangShan](https://zread.ai/OpenXiangShan/XiangShan)

## 阅读主线

香山主要 Scala 源码位于 `src/main/scala/xiangshan/`。本目录按源码结构组织为以下几条主线：

| 源码目录 | 本目录文档 | 阅读重点 |
| --- | --- | --- |
| `frontend/` | [frontend/](frontend/) | 取指、预译码、分支预测、FTQ、ICache 请求侧、指令缓冲 |
| `backend/` | [backend/](backend/) | 译码、派发、重命名、寄存器缓存、指令融合、Move 消除 |
| `cache/` | 当前分散在 Frontend ICache 与 Memory 相关文档中 | Cache 层次、取指 Cache、访存系统和一致性路径的连接点 |
| `mem/` | [memory/](memory/) | Load/Store、访存依赖预测、replay、内存顺序约束 |

建议先读系统级概览，再进入具体子模块：

1. [Frontend Overview and End-to-End Signal Analysis](frontend/Frontend-Overview-and-End-to-End-Signal-Analysis.md)
2. [Decode](backend/Decode.md)
3. [Rename](backend/Rename.md)
4. [Dispatch](backend/Dispatch.md)
5. [MDP / Memory Dependence Predictor](memory/mdp-ref.md)

## Frontend

源码参考路径：`src/main/scala/xiangshan/frontend/`

Frontend 负责从预测 PC 开始组织取指请求，经过分支预测、ICache、预译码、FTQ/IBuffer 等结构，把指令流稳定交付给后端。阅读时应优先关注 redirect、override、history update、fetch packet 生成和前后端握手。

| 文档 | 内容 |
| --- | --- |
| [Frontend-Overview-and-End-to-End-Signal-Analysis.md](frontend/Frontend-Overview-and-End-to-End-Signal-Analysis.md) | 前端全链路信号流和端到端行为 |
| [Frontend-BPU.md](frontend/Frontend-BPU.md) | BPU 顶层、预测器组合、历史和 redirect/override |
| [Frontend-BPU-Doc.md](frontend/Frontend-BPU-Doc.md) | BPU 设计文档式总览 |
| [Frontend-FTB.md](frontend/Frontend-FTB.md) | Fetch Target Buffer |
| [Frontend-FauFTB.md](frontend/Frontend-FauFTB.md) | 快速路径 FTB / fall-through 预测 |
| [Frontend-Tage.md](frontend/Frontend-Tage.md) | TAGE 条件分支预测器 |
| [Frontend-SC.md](frontend/Frontend-SC.md) | Statistical Corrector |
| [Frontend-ITTAGE.md](frontend/Frontend-ITTAGE.md) | 间接跳转预测 |
| [Frontend-RAS.md](frontend/Frontend-RAS.md) | Return Address Stack |
| [Frontend-Bim.md](frontend/Frontend-Bim.md) | BIM 基础预测器 |
| [Frontend-FTQ.md](frontend/Frontend-FTQ.md) | Fetch Target Queue |
| [Frontend-ICache.md](frontend/Frontend-ICache.md) | ICache 请求、miss、prefetch 与回压 |
| [Frontend-IBuffer.md](frontend/Frontend-IBuffer.md) | 指令缓冲与前后端交付 |
| [Frontend-IFU-and-Predecode-Deep-Dive.md](frontend/Frontend-IFU-and-Predecode-Deep-Dive.md) | IFU 和预译码深入分析 |
| [Frontend-Question-Code-Evidence.md](frontend/Frontend-Question-Code-Evidence.md) | 前端问题与代码证据索引 |

## Backend

源码参考路径：`src/main/scala/xiangshan/backend/`

Backend 负责把前端交付的指令转成微操作，完成译码、重命名、派发、调度、执行、写回与提交。当前文档重点覆盖译码到派发附近的控制路径，以及与性能优化相关的重命名、RegCache、Move 消除和 Decode Fusion。

| 文档 | 内容 |
| --- | --- |
| [Decode.md](backend/Decode.md) | 译码信息、译码表、预译码关系、宏指令拆分 |
| [Decode-Fusion.md](backend/Decode-Fusion.md) | Decode 端 Fusion 场景与边界条件 |
| [Rename.md](backend/Rename.md) | 物理寄存器重命名、FreeList、快照恢复 |
| [Move-elimination.md](backend/Move-elimination.md) | Move 指令消除与引用计数 FreeList |
| [Dispatch.md](backend/Dispatch.md) | 派发路径、队列连接和控制条件 |
| [RegCache.md](backend/RegCache.md) | RegCache 结构、替换、旁路和取消路径 |

## Cache

源码参考路径：`src/main/scala/xiangshan/cache/`

Cache 文档后续可按 L1/L2/L3、MSHR、prefetch、uncache、TLB/一致性接口等方向继续补齐。当前已有内容中，与 Cache 关系最直接的是：

| 文档 | 内容 |
| --- | --- |
| [Frontend-ICache.md](frontend/Frontend-ICache.md) | 前端 ICache、miss、MSHR、预取和取指回压 |
| [mdp-ref.md](memory/mdp-ref.md) | 访存依赖预测与 Load/Store 队列交互，可作为理解数据侧访存行为的入口 |

建议阅读源码时同时查看 `cache/` 与 `mem/`，因为 Cache 行为通常需要结合 Load/Store queue、MSHR、replay、TLB 和一致性请求一起理解。

## Memory

源码参考路径：`src/main/scala/xiangshan/mem/`

Memory 目录关注 Load/Store 执行、访存顺序、replay、依赖预测、异常和提交约束。本目录使用 `memory/` 作为文档目录名，对应源码中的 `mem/`。

| 文档 | 内容 |
| --- | --- |
| [mdp-ref.md](memory/mdp-ref.md) | Memory Dependence Predictor、SSIT、LFST、Load/Store queue 等待与训练路径 |

## 文档使用方式

- 对照源码阅读：每篇文档中的源码路径和信号名应回到 XiangShan 仓库确认。
- 先看边界，再看算法：优先确认模块输入输出、流水级、flush/redirect/commit 关系，再进入表项组织和更新策略。
- 关注有效路径：香山源码中有配置开关、历史实现和演进痕迹，阅读时应区分设计意图、当前有效代码和验证关注点。
- 记录疑问：遇到与设计文档不一致的地方，优先用当前源码、配置参数和实例化路径判断真实行为。
