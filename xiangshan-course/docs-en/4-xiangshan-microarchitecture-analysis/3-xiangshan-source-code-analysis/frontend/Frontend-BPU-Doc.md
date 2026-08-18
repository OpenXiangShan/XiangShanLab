

<!-- [TAGE分支预测器工作原理深度解析（蓝色系）.pptx](https://bosc.yuque.com/attachments/yuque/0/2026/pptx/28590141/1778491561840-9cdd90d8-52d4-46fe-b491-def51e80cd12.pptx) -->
[In-depth analysis of the TAGE branch predictor (blue theme)](https://bosc.yuque.com/attachments/yuque/0/2026/pptx/28590141/1778491561840-9cdd90d8-52d4-46fe-b491-def51e80cd12.pptx)


<!-- > 代码证据基线：以下链接按同目录已校准的 OpenXiangShan/XiangShan `kunminghu-v2` 源码基线给出，分析 commit 为 `52262f303fc06daf84cdab7011d59b7df65ce7e8`。本文的结构性结论应优先回到这些源码入口核对。 -->
> Code-evidence baseline: the links below follow the calibrated OpenXiangShan/XiangShan `kunminghu-v2` source baseline used by the documents in this directory. The analysis commit is `52262f303fc06daf84cdab7011d59b7df65ce7e8`. Structural conclusions in this document should be checked against these source entry points first.

<!-- ## 0. **代码证据索引** -->
## 0. **Code Evidence Index**

<!-- | 文档章节 | 源码对象 | 可证明的结论 | 代码证据 | -->
| Document section | Source object | Demonstrable conclusion | Code evidence |
| :--- | :--- | :--- | :--- |
<!-- | BPU 顶层模块 | `Predictor` / BPU | 预测器链、S0-S3 valid/ready、S2/S3 redirect、后端 redirect 恢复由 BPU 统一调度。 | [Parameters.scala#L124-L143](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/Parameters.scala#L124-L143)、[frontend/BPU.scala#L381-L455](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L381-L455)、[frontend/BPU.scala#L606-L635](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L606-L635)、[frontend/BPU.scala#L827-L854](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L827-L854)、[frontend/BPU.scala#L915-L1050](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L915-L1050) | -->
| BPU top-level module | `Predictor` / BPU | The BPU centrally schedules the predictor chain, S0-S3 valid/ready, S2/S3 redirects, and backend-redirect recovery. | [Parameters.scala#L124-L143](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/Parameters.scala#L124-L143)、[frontend/BPU.scala#L381-L455](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L381-L455)、[frontend/BPU.scala#L606-L635](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L606-L635)、[frontend/BPU.scala#L827-L854](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L827-L854)、[frontend/BPU.scala#L915-L1050](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L915-L1050) |
<!-- | 预测器链路 | `Composer` | 有效链路按 `FauFTB -> Tage_SC -> FTB -> ITTage -> RAS` 串联，元数据按链路拼接并在 update 时拆回各组件。 | [frontend/Composer.scala#L22-L77](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Composer.scala#L22-L77) | -->
| Predictor chain | `Composer` | The effective chain is `FauFTB -> Tage_SC -> FTB -> ITTage -> RAS`; metadata is concatenated along the chain and split back to each component during update. | [frontend/Composer.scala#L22-L77](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Composer.scala#L22-L77) |
<!-- | FallThrough / uBTB 对应的早级预测 | `FauFTB` | 早级低延迟预测先提供 target/fall-through/entry 信息，再交给后级预测器修正。 | [frontend/FauFTB.scala#L76-L128](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/FauFTB.scala#L76-L128)、[frontend/Composer.scala#L25-L31](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Composer.scala#L25-L31) | -->
| Early prediction corresponding to FallThrough / uBTB | `FauFTB` | The low-latency early predictor first supplies target, fall-through, and entry information, which later predictors then refine. | [frontend/FauFTB.scala#L76-L128](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/FauFTB.scala#L76-L128)、[frontend/Composer.scala#L25-L31](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Composer.scala#L25-L31) |
<!-- | uTAGE / TAGE | `Tage` / `Tage_SC` | 条件分支方向由 TAGE provider/alternate 与基础表产生，`Tage_SC` 继续混入 SC 修正。 | [frontend/Tage.scala#L143-L270](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Tage.scala#L143-L270)、[frontend/Tage.scala#L778-L846](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Tage.scala#L778-L846)、[frontend/Tage.scala#L1096](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Tage.scala#L1096) | -->
| uTAGE / TAGE | `Tage` / `Tage_SC` | Conditional-branch direction comes from the TAGE provider/alternate and base table; `Tage_SC` further incorporates SC correction. | [frontend/Tage.scala#L143-L270](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Tage.scala#L143-L270)、[frontend/Tage.scala#L778-L846](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Tage.scala#L778-L846)、[frontend/Tage.scala#L1096](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Tage.scala#L1096) |
<!-- | Ahead BTB / MainBTB 对应的块目标预测 | `FTB` | FTB 以 fetch block 为单位保存 CFI 槽、target、fall-through 与 multi-hit 信息。 | [frontend/FTB.scala#L459-L463](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/FTB.scala#L459-L463)、[frontend/FTB.scala#L533-L558](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/FTB.scala#L533-L558)、[frontend/FTB.scala#L683-L811](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/FTB.scala#L683-L811) | -->
| Block-target prediction corresponding to Ahead BTB / MainBTB | `FTB` | FTB stores CFI slots, targets, fall-through addresses, and multi-hit information per fetch block. | [frontend/FTB.scala#L459-L463](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/FTB.scala#L459-L463)、[frontend/FTB.scala#L533-L558](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/FTB.scala#L533-L558)、[frontend/FTB.scala#L683-L811](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/FTB.scala#L683-L811) |
<!-- | SC | `SC` / `HasSC` | SC 读取多类统计特征与 TAGE 结果，生成条件分支方向修正；最终差异由 BPU redirect 处理。 | [frontend/SC.scala#L259-L372](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/SC.scala#L259-L372)、[frontend/BPU.scala#L827-L854](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L827-L854) | -->
| SC | `SC` / `HasSC` | SC reads several classes of statistical features and TAGE results to generate conditional-branch direction corrections; final differences are handled by the BPU redirect. | [frontend/SC.scala#L259-L372](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/SC.scala#L259-L372)、[frontend/BPU.scala#L827-L854](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L827-L854) |
<!-- | ITTAGE | `ITTAGE` | ITTAGE 专门修正 JALR/间接跳转 target，provider target 与上游 target 不同时进入 S3 redirect 比较。 | [frontend/ITTAGE.scala#L418-L470](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/ITTAGE.scala#L418-L470)、[frontend/ITTAGE.scala#L552-L600](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/ITTAGE.scala#L552-L600)、[frontend/ITTAGE.scala#L709-L748](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/ITTAGE.scala#L709-L748) | -->
| ITTAGE | `ITTAGE` | ITTAGE specifically corrects JALR/indirect-jump targets; when the provider target differs from the upstream target, S3 performs the redirect comparison. | [frontend/ITTAGE.scala#L418-L470](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/ITTAGE.scala#L418-L470)、[frontend/ITTAGE.scala#L552-L600](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/ITTAGE.scala#L552-L600)、[frontend/ITTAGE.scala#L709-L748](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/ITTAGE.scala#L709-L748) |
<!-- | RAS 与 uRAS | `newRAS` | RAS 位于链尾，负责 return target 覆盖、投机栈修复与提交态维护。 | [frontend/newRAS.scala#L18-L26](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/newRAS.scala#L18-L26)、[frontend/newRAS.scala#L696-L706](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/newRAS.scala#L696-L706)、[frontend/Composer.scala#L37-L56](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Composer.scala#L37-L56) | -->
| RAS and uRAS | `newRAS` | RAS is at the end of the chain and handles return-target override, speculative-stack repair, and committed-state maintenance. | [frontend/newRAS.scala#L18-L26](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/newRAS.scala#L18-L26)、[frontend/newRAS.scala#L696-L706](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/newRAS.scala#L696-L706)、[frontend/Composer.scala#L37-L56](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Composer.scala#L37-L56) |
<!-- | PHR / CommonHR | BPU history state | 路径历史、全局历史、折叠历史随预测流水推进，并在 redirect/update 时恢复。 | [frontend/BPU.scala#L915-L1050](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L915-L1050)、[frontend/Composer.scala#L58-L77](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Composer.scala#L58-L77) | -->
| PHR / CommonHR | BPU history state | Path, global, and folded histories advance with the prediction pipeline and are restored on redirect/update. | [frontend/BPU.scala#L915-L1050](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/BPU.scala#L915-L1050)、[frontend/Composer.scala#L58-L77](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Composer.scala#L58-L77) |
<!-- | Bim / UBTB 命名差异 | `Bim.scala` / `TageBTable` | 在该源码基线中，`Bim.scala` 不是有效预测器链的一环，基础方向表由 `TageBTable` 承担。 | [frontend/Bim.scala](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Bim.scala)、[frontend/Tage.scala#L143-L270](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Tage.scala#L143-L270) | -->
| Bim / UBTB naming difference | `Bim.scala` / `TageBTable` | In this source baseline, `Bim.scala` is not an active link in the predictor chain; the base direction table is implemented by `TageBTable`. | [frontend/Bim.scala](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Bim.scala)、[frontend/Tage.scala#L143-L270](https://github.com/OpenXiangShan/XiangShan/blob/52262f303fc06daf84cdab7011d59b7df65ce7e8/src/main/scala/xiangshan/frontend/Tage.scala#L143-L270) |






<!-- ## 1. **BPU 顶层模块** -->
## 1. **BPU Top-Level Module**
<!-- ### **1.1 总体架构** -->
### **1.1 Overall Architecture**
<!-- BPU（Branch Prediction Unit，分支预测单元）顶层模块位于香山 Kunminghu-v3 处理器前端，是连接取指流水线与各类子预测器的核心枢纽。该模块负责整合 10 种子预测器（包括 BTB、TAGE、SC、ITTAGE、RAS 及分支历史寄存器等）的预测结果与训练恢复信息，并通过 FTQ（Fetch Target Queue）实现预测块入队、元数据存储、训练回传及误预测纠正，形成完整的预测反馈闭环。 -->
The BPU (Branch Prediction Unit) top-level module resides in the frontend of the XiangShan Kunminghu-v3 processor. It is the central hub between the fetch pipeline and the predictor submodules. The module integrates prediction results and training/recovery information from ten predictor structures, including BTB, TAGE, SC, ITTAGE, RAS, and branch-history registers. Through the FTQ (Fetch Target Queue), it enqueues prediction blocks, stores metadata, returns training information, and corrects mispredictions, forming a complete prediction-feedback loop.

<!-- 这是一张图片，ocr 内容为： -->
<!-- This is an image; OCR content follows. -->
![](https://cdn.nlark.com/yuque/0/2026/png/28590141/1778482457644-c82ebbfd-4825-4a9c-acb5-45e3e4a2dba4.png)

<!-- 图1. BPU 结构框图 -->
Figure 1. BPU block diagram

<!-- BPU 顶层模块的核心职责归纳为以下四个方面： -->
The core responsibilities of the BPU top-level module can be summarized in four areas:

<!-- **1. 预测结果的生成与聚合**：将 startPc 广播给所有子预测器，在 S1 阶段产生"粗糙"预测、在 S3 阶段产生"精确"预测，并按指令类型和子预测器优先级完成聚合。当 S3 与 S1 预测结果不同时，覆盖 S1 预测结果，确保取指方向的最终正确性。 -->
**1. Generation and aggregation of prediction results:** The BPU broadcasts `startPc` to all subpredictors, produces a “coarse” prediction in S1 and a “precise” prediction in S3, and aggregates them according to instruction type and subpredictor priority. When the S3 and S1 predictions differ, S3 overrides S1 to ensure that the final fetch direction is correct.

<!-- **2. 流水控制信号维护**：维护预测流水线（S0–S3）的 valid/ready/flush/override 握手信号，保证与 FTQ 的正确交互。流水线采用 Valid-Ready 握手协议，各级通过 fire 信号完成数据传递。 -->
**2. Pipeline-control maintenance:** It maintains the valid/ready/flush/override handshake signals of the S0-S3 prediction pipeline to ensure correct interaction with the FTQ. The pipeline uses the valid-ready protocol, and data moves between stages on `fire`.

<!-- **3. 训练与重定向管理**：接收来自 FTQ 的 redirect 信号并驱动历史恢复与预测清刷；接收 train（resolve 训练）与 fastTrain（S3 结果回训 S1 预测器）的训练链路，完成预测器表项的更新。 -->
**3. Training and redirect management:** It receives FTQ `redirect` signals to restore history and flush predictions. It also accepts `train` (resolve-time training) and `fastTrain` (S3 results fed back to S1 predictors) paths to update predictor entries.

<!-- **4. 元数据生产与输出**：向 FTQ 输出 redirect/resolve/commit/perf 等元数据（Meta），支撑后续恢复、训练与可观测性。元数据在 S3 阶段打包发出，包含各子预测器在预测时刻的内部状态快照。 -->
**4. Metadata production and output:** The BPU sends redirect, resolve, commit, performance, and other metadata to the FTQ to support later recovery, training, and observability. Metadata is packaged and emitted in S3 and contains snapshots of each subpredictor's internal state at prediction time.

<!-- 在子预测器组织层面，BPU 顶层采用"快速路径 + 精确路径"的双轨架构，如下表所示。 -->
At the subpredictor-organization level, the BPU top level uses a dual-path architecture consisting of a “fast path” and a “precise path,” as shown below.

<!-- | 流水线阶段 | 子预测器组成 | 功能定位 | -->
| Pipeline stage | Subpredictors | Role |
| :--- | :--- | :--- |
<!-- | S1 快速路径 | uBTB、aBTB、uTAGE、uRAS、FallThrough | 提供低延迟的早期预测，支持快速取指发射 | -->
| S1 fast path | uBTB, aBTB, uTAGE, uRAS, FallThrough | Provides low-latency early predictions to support rapid fetch issuance. |
<!-- | S3 精确路径 | mBTB、TAGE、SC、ITTAGE、RAS | 提供高精度的最终预测，负责 S1 纠错与覆盖 | -->
| S3 precise path | mBTB, TAGE, SC, ITTAGE, RAS | Provides the high-accuracy final prediction and corrects or overrides S1. |




<!-- S1 快速路径与 S3 精确路径通过流水线寄存器级联。S1 预测结果在满足条件时可直接发往 FTQ 以保证取指吞吐率；S3 预测结果作为"终审"判决，当与 S1 不一致时通过 override 机制覆盖前者，在准确率与延迟之间取得平衡。 -->
The S1 fast path and S3 precise path are cascaded through pipeline registers. When conditions permit, the S1 result can be sent directly to the FTQ to preserve fetch throughput. The S3 result is the final decision and overrides S1 when they disagree, balancing accuracy against latency.

<!-- ### **1.2 BPU 顶层模块工作机制** -->
### **1.2 BPU Top-Level Operating Mechanism**
<!-- BPU 顶层的工作机制围绕四级预测流水线（S0–S3）与两级训练流水线（T0–T1）展开，涵盖预测发射、结果覆盖、误预测恢复、训练更新及提交固化等核心环节。 -->
The BPU top level operates around a four-stage prediction pipeline (S0-S3) and a two-stage training pipeline (T0-T1), covering prediction launch, result override, misprediction recovery, training updates, and commit-time state finalization.

<!-- #### **1.2.1 四级预测流水线** -->
#### **1.2.1 Four-Stage Prediction Pipeline**
<!-- BPU 主预测流水线划分为 S0、S1、S2、S3 四个阶段，各级职责明确，通过 Valid-Ready 握手协议推进。 -->
The main BPU prediction pipeline is divided into S0, S1, S2, and S3. Each stage has a defined role, and they advance through the valid-ready handshake protocol.

<!-- **1）S0 阶段（PC 选择与广播）：**S0 为流水线起始阶段，无独立 valid 寄存器。该阶段负责选择下一拍的起始 PC（startPc），并向所有子预测器广播。PC 选择遵循严格优先级：后端 redirect 有效时取 redirect.bits.target；否则若 S3 发生 override，取 s3_prediction.target；否则若 S1 有效，取 s1_prediction.target；默认保持 s0_startPcReg 不变。S0 阶段同时完成所有规格折叠历史的实时计算（s0_foldedPhr），供下游预测器直接读取。 -->
**1) S0 stage (PC selection and broadcast):** S0 is the pipeline entry stage and has no independent valid register. It selects the next-cycle starting PC (`startPc`) and broadcasts it to every subpredictor. The PC priority is strict: use `redirect.bits.target` when a backend redirect is valid; otherwise use `s3_prediction.target` on an S3 override; otherwise use `s1_prediction.target` when S1 is valid; otherwise retain `s0_startPcReg`. S0 also computes all specified folded histories in real time (`s0_foldedPhr`) for direct use by downstream predictors.

<!-- **2）S1 阶段（早期粗糙预测）：**S1 阶段由 uBTB、aBTB、uTAGE、uRAS 及 FallThrough 等轻量级预测器并行工作，产生"粗糙"预测结果。条件分支优先级为 uTAGE > aBTB > uBTB > FallThrough；间接分支优先级为 aBTB > uBTB > FallThrough；Return 指令以 uRAS 预测结果为准。S1 预测结果在满足 s1_valid && s2_ready && io.toFtq.prediction.ready 时直接发往 FTQ，支撑低延迟取指。 -->
**2) S1 stage (early coarse prediction):** Lightweight predictors such as uBTB, aBTB, uTAGE, uRAS, and FallThrough operate in parallel in S1 to produce a coarse prediction. Conditional branches are prioritized as uTAGE > aBTB > uBTB > FallThrough; indirect branches as aBTB > uBTB > FallThrough; and returns follow uRAS. When `s1_valid && s2_ready && io.toFtq.prediction.ready`, the S1 result goes directly to the FTQ to support low-latency fetch.

<!-- **3）S2 阶段（中间缓冲与对齐）：**S2 阶段承担流水线缓冲与元数据对齐职责。该阶段将 S1 的预测结果及各类元数据锁存到流水线寄存器，为 S3 阶段的大规模预测器（mBTB、TAGE、SC、ITTAGE、RAS）提供稳定的输入窗口。同时完成 uTAGE 与 aBTB 元数据的清理和过滤，剔除位于真实跳转分支之后的"幽灵"预测条目。 -->
**3) S2 stage (intermediate buffering and alignment):** S2 buffers the pipeline and aligns metadata. It latches the S1 prediction and all relevant metadata in pipeline registers, providing a stable input window for the large S3 predictors (mBTB, TAGE, SC, ITTAGE, and RAS). It also cleans and filters uTAGE and aBTB metadata, removing “ghost” predictions located after the actual taken branch.

<!-- **4）S3 阶段（后期精确预测与覆盖仲裁）：**S3 阶段由 mBTB、TAGE、SC、ITTAGE 及主 RAS 共同产出"精确"预测结果。条件分支的最终方向优先级为 SC > TAGE > mBTB；间接分支的目标优先级为 RAS(Return) > ITTAGE > mBTB target > fall-through；Return 指令以 RAS 预测结果为最高优先级。S3 阶段将精确预测与 S1 粗糙预测进行比对，当二者不一致且 S3 预测有效时拉高 s3_override 信号，向 FTQ 发送覆盖包以纠正早先的错误预测。 -->
**4) S3 stage (late precise prediction and override arbitration):** mBTB, TAGE, SC, ITTAGE, and the main RAS jointly produce the precise prediction in S3. The final conditional-branch direction priority is SC > TAGE > mBTB; the indirect-branch target priority is RAS (return) > ITTAGE > mBTB target > fall-through; and RAS has the highest priority for return instructions. S3 compares its precise prediction with the S1 coarse prediction. If they disagree while S3 is valid, it raises `s3_override` and sends an override packet to the FTQ to correct the earlier wrong prediction.

<!-- | 流水级 | Fire 条件 | 核心操作 | -->
| Pipeline stage | Fire condition | Core operation |
| :--- | :--- | :--- |
<!-- | S0 | s1_ready && resetDone | 选择 startPc，广播至子预测器 | -->
| S0 | `s1_ready && resetDone` | Selects `startPc` and broadcasts it to subpredictors. |
<!-- | S1 | s1_valid && s2_ready && FTQ.ready | 快速预测，结果直发 FTQ | -->
| S1 | `s1_valid && s2_ready && FTQ.ready` | Performs fast prediction and sends its result directly to the FTQ. |
<!-- | S2 | s2_valid && s3_ready | 缓冲对齐，元数据锁存 | -->
| S2 | `s2_valid && s3_ready` | Buffers and aligns data and latches metadata. |
<!-- | S3 | s3_valid | 精确预测，生成 override 与元数据 | -->
| S3 | `s3_valid` | Performs precise prediction and produces override and metadata. |




<!-- #### **1.2.2 Flush 级联与 Override 机制** -->
#### **1.2.2 Flush Cascade and Override Mechanism**
<!-- BPU 顶层维护一套从后向前的 Flush 级联机制，用于在发生重定向或预测覆盖时快速清刷流水线中的投机状态。 -->
The BPU top level maintains a backward-propagating flush cascade to quickly clear speculative pipeline state when a redirect or prediction override occurs.

<!-- **Flush 信号级联**。s3_flush 由 redirect.valid 直接驱动，表示后端或 IFU 发现真实执行错误，需冲刷 S3 级；s2_flush 由 s3_flush 或 s3_override 驱动，表示 S3 级本身被冲刷或 S3 发现 S1 预测错误需覆盖；s1_flush 由 s2_flush 级联驱动。Flush 信号将对应 sx_valid 寄存器置为 false，丢弃当前流水级中的投机数据。 -->
**Flush-signal cascade.** `s3_flush` is driven directly by `redirect.valid`, indicating that the backend or IFU found an execution error and S3 must be flushed. `s2_flush` is driven by `s3_flush` or `s3_override`, meaning that S3 itself is being flushed or that S3 found an S1 error that must be overridden. `s1_flush` is cascaded from `s2_flush`. Each flush drives the corresponding `sx_valid` register false and discards speculative data in that stage.

<!-- **Override 机制**。s3_override 在 s3_valid 有效且 S3 精确预测与 S1 粗糙预测不一致时拉高。该信号触发两项关键动作：向 FTQ 发送覆盖包，以 s3_startPc 和 s3_prediction 替换原先基于 S1 的错误取指方向；驱动 s2_flush 级联，清刷 S1/S2 中已被证明错误的预测路径。 -->
**Override mechanism.** `s3_override` is asserted when `s3_valid` is true and the S3 precise prediction differs from the S1 coarse prediction. It performs two key actions: sends an override packet to the FTQ, replacing the erroneous S1 fetch direction with `s3_startPc` and `s3_prediction`; and drives the `s2_flush` cascade to clear the S1/S2 prediction path that has been proven wrong.

<!-- S3 Override 与后端 Redirect 存在本质区别。前者是 BPU 内部预测器之间的自纠错，发生在预测前端尚未进入执行阶段；后者是后端执行单元发现真实分支方向错误后发起的全局重定向，需要恢复架构状态并清刷整个前端流水线。 -->
S3 override and a backend redirect are fundamentally different. The former is self-correction among BPU-internal predictors before the predicted frontend path reaches execution. The latter is a global redirect raised when a backend execution unit detects that the actual branch direction was wrong; it must restore architectural state and flush the entire frontend pipeline.

<!-- #### **1.2.3 Train 与 Update 机制** -->
#### **1.2.3 Train and Update Mechanisms**
<!-- BPU 顶层支持两条训练通路：经由 FTQ 反馈的 resolve 训练（train）与前端 S3 结果直接回训的快速训练（fastTrain）。 -->
The BPU top level supports two training paths: resolve-time training (`train`) fed back through the FTQ, and fast training (`fastTrain`) that feeds S3 results directly back to the frontend predictors.

<!-- **Resolve 训练（Train）**。当分支指令在后端被 Resolve 后，FTQ 将实际执行结果通过 io.fromFtq.train 接口回传 BPU。BPU 顶层首先构造 CompareMatrix，比较预测块内各分支的 CFI Position，生成 to_firstMispredictMask，该掩码标记第一个有效且误预测的分支及其之前的所有分支，确保仅将这些实际执行过的分支送去训练。训练数据随后被广播至各子预测器，驱动 TAGE、SC、uBTB 等表项的计数器更新与状态调整。 -->
**Resolve training (`train`).** After a branch is resolved in the backend, the FTQ returns the actual result through `io.fromFtq.train`. The BPU first constructs `CompareMatrix`, compares the CFI positions of branches in the prediction block, and generates `to_firstMispredictMask`. This mask marks the first valid mispredicted branch and every branch before it, ensuring that only branches that actually executed are trained. The training data is then broadcast to all subpredictors to update counters and state in TAGE, SC, uBTB, and related tables.

<!-- **快速训练（FastTrain）**。FastTrain 通路打包 S3 阶段产生的精确预测结果、aBTB 元数据、uTAGE 元数据及 override 标志，在 s3_valid 时直接发往小型预测器。该机制使 aBTB、uTAGE 等轻量表能够在预测发出后立即更新状态，实现无缝连续的快速适应，弥补 resolve 训练因后端延迟而带来的更新滞后。 -->
**Fast training (`fastTrain`).** The fastTrain path packages the precise S3 prediction, aBTB metadata, uTAGE metadata, and the override flag, and sends them directly to the small predictors when `s3_valid` is asserted. This lets lightweight tables such as aBTB and uTAGE update immediately after a prediction is issued, enabling continuous rapid adaptation and compensating for the delay of backend resolve training.

<!-- 分支历史模块的训练遵循"预测时刻快照"原则：训练使用的折叠历史并非当前最新历史，而是通过 commit 阶段传回的 bpTrain.meta.phr 等元数据重新恢复并计算得出，以保证训练数据与预测上下文的一致性。 -->
Branch-history training follows a “prediction-time snapshot” rule. The folded history used for training is not the current latest history; it is restored and recomputed from metadata such as `bpTrain.meta.phr` returned at commit time, keeping the training data consistent with the original prediction context.

<!-- #### **1.2.4 Commit 机制** -->
#### **1.2.4 Commit Mechanism**
<!-- Commit 机制用于在指令不可撤销地退休后，更新那些不能随意回滚的预测器结构，最典型的代表是 RAS（返回地址栈）。 -->
The commit mechanism updates predictor structures that cannot be freely rolled back after an instruction retires irreversibly; the canonical example is the RAS (return-address stack).

<!-- BPU 顶层通过 io.fromFtq.commit 接口接收 FTQ 的提交信号。commitMeta 目前仅包含 RAS: RASCommitMeta，在提交阶段将栈顶指针等关键状态传递给主 RAS，完成非投机栈（Nonspeculative Stack）的更新。其核心目的在于：RAS 的推测指针在预测阶段可随 S1/S3 的 Call/Return 指令任意推进或回滚，但真正的架构状态只有在指令提交后才允许永久修改。Commit 机制充当了投机预测与架构状态之间的安全边界，确保异常发生后能够恢复到正确的返回地址上下文。 -->
The BPU receives FTQ commit signals through `io.fromFtq.commit`. `commitMeta` currently contains only `RAS: RASCommitMeta`; at commit, it passes the stack-top pointer and other key state to the main RAS to update the nonspeculative stack. The speculative RAS pointer may advance or roll back with S1/S3 Call and Return instructions, but architectural state may be permanently changed only after commit. Commit therefore forms the safety boundary between speculative prediction and architectural state, ensuring that an exception can restore the correct return-address context.

<!-- 对于 PHR 模块，commit 信号同样具有"底图固化"作用：后端成功退休的指令通过 commit 更新 PHR 的架构状态路径历史，为灾难性错误恢复提供可信赖 -->
For the PHR module, commit also “solidifies the baseline”: instructions that retire successfully in the backend update the architectural path history through commit, providing a trustworthy basis for catastrophic recovery.

<!-- 的基准点。 -->
<!-- (The preceding sentence supplies the baseline point.) -->

<!-- ### **1.3 BPU 顶层模块关键结构** -->
### **1.3 Key BPU Top-Level Structures**
<!-- BPU 顶层模块内部维护三类关键数据结构：预测流水线寄存器、分支历史维护结构以及元数据快照结构，共同支撑推测执行、错误恢复与训练更新的一致性。 -->
The BPU top level maintains three classes of key data structures: prediction-pipeline registers, branch-history structures, and metadata snapshots. Together they keep speculative execution, error recovery, and training updates consistent.

<!-- #### **1.3.1 预测流水线寄存器** -->
#### **1.3.1 Prediction-Pipeline Registers**
<!-- 预测流水线寄存器负责在 S1–S3 各级之间锁存预测结果、元数据及控制状态。各级 valid 寄存器采用带优先级的时序更新逻辑，确保在流入、冲刷与流出三种场景下的状态正确性。 -->
Prediction-pipeline registers latch prediction results, metadata, and control state between S1-S3. Each stage's valid register uses priority-ordered sequential logic to remain correct on ingress, flush, and egress.

<!-- **1. S1 Valid 更新逻辑**：优先级：流入 > 冲刷 > 流出。当 s0_fire 有效时置 true；若 s0_fire 无效且 s1_flush 有效则置 false；若前两者均无效且 s1_fire 有效则置 false。 -->
**1. S1 valid update:** Priority is ingress > flush > egress. Set it true when `s0_fire` is asserted; if `s0_fire` is false and `s1_flush` is asserted, set it false; if neither applies and `s1_fire` is asserted, set it false.

<!-- **2. S2 Valid 更新逻辑**：优先级：冲刷 > 流入 > 流出。当 s2_flush 有效时直接置 false；若未冲刷且 s1_fire 有效，且 s1 未被冲刷时才允许流入（true）；若未发生流入/冲刷且 s2_fire 有效则置 false。 -->
**2. S2 valid update:** Priority is flush > ingress > egress. Set it false immediately on `s2_flush`; if there is no flush, allow ingress (set true) only when `s1_fire` is asserted and S1 itself is not flushed; if neither ingress nor flush occurs and `s2_fire` is asserted, set it false.

<!-- **3. S3 Valid 更新逻辑**：与 S2 对称。当 s3_flush 有效时置 false；若未冲刷且 s2_fire 有效，且 s2 未被冲刷时流入（true）；若未发生流入/冲刷且 s3_fire 有效则置 false。 -->
**3. S3 valid update:** This mirrors S2. Set it false on `s3_flush`; if there is no flush, set it true on `s2_fire` only when S2 is not flushed; if neither ingress nor flush occurs and `s3_fire` is asserted, set it false.

<!-- 流水线中还锁存了各级预测结果（s1_prediction、s2_s1Prediction、s3_s1Prediction）、FTQ 指针（s2_ftqPtr、s3_ftqPtr）以及各类子预测器元数据（s1_utageMeta、s2_utageMeta、s3_abtbMeta 等），在对应的 fire 信号有效时由上一级数据更新，在 flush 时保持或清零。 -->
The pipeline also latches each stage's prediction (`s1_prediction`, `s2_s1Prediction`, `s3_s1Prediction`), FTQ pointers (`s2_ftqPtr`, `s3_ftqPtr`), and subpredictor metadata (`s1_utageMeta`, `s2_utageMeta`, `s3_abtbMeta`, and so on). These are updated from the preceding stage when the corresponding `fire` is asserted and are held or cleared on flush.

<!-- #### **1.3.2 分支历史维护结构** -->
#### **1.3.2 Branch-History Structures**
<!-- 分支历史由 PHR（Path History Register，路径历史寄存器）与 CommonHR（Common History Register，公共历史寄存器）两大核心模块维护，是 TAGE、 -->
Branch history is maintained by two core modules, PHR (Path History Register) and CommonHR (Common History Register), and supplies key features to TAGE,

<!-- SC、ITTAGE 等高级预测器的关键特征来源。 -->
SC, ITTAGE, and other advanced predictors.

<!-- | 历史类型 | 核心字段 | 使用者 | -->
| History type | Core fields | Consumers |
| :--- | :--- | :--- |
<!-- | PHR | phrBits、phrPtr、foldedPhr | uTAGE、TAGE、SC、ITTAGE | -->
| PHR | `phrBits`, `phrPtr`, `foldedPhr` | uTAGE, TAGE, SC, ITTAGE |
<!-- | CommonHR | ghr、bw、imli、histQueue | SC（ghr/bw/imli） | -->
| CommonHR | `ghr`, `bw`, `imli`, `histQueue` | SC (`ghr`/`bw`/`imli`) |


<!-- #### **1.3.3 元数据快照结构** -->
#### **1.3.3 Metadata Snapshot Structures**
<!-- BPU 顶层在 S3 阶段向 FTQ 输出三类元数据快照，用于后续的重定向恢复、resolve 训练及 commit 提交。元数据在预测时刻精确捕获各子预测器的内部状态，确保训练与恢复使用的是预测当时的上下文。 -->
In S3, the BPU top level sends three metadata snapshots to the FTQ for later redirect recovery, resolve training, and commit processing. Each snapshot captures subpredictor state at prediction time, ensuring that recovery and training use the original context.

<!-- **RedirectMeta（重定向元数据）**：包含 phr（路径历史恢复信息）、commonHRMeta（公共历史恢复信息）和 RAS（RAS 恢复信息）。当后端发现分支预测错误并发起 redirect 时，FTQ 从 metaQueueRedirect 中检索对应的 redirectMeta 回传 BPU。BPU 据此恢复 PHR 的循环指针与高位历史、CommonHR 的 ghr/bw 状态以及 RAS 的栈顶指针，将预测状态精确回滚到错误发生点。 -->
**`RedirectMeta` (redirect metadata):** Contains `phr` (path-history recovery), `commonHRMeta` (common-history recovery), and RAS recovery information. When the backend detects a branch misprediction and raises a redirect, the FTQ retrieves the corresponding `redirectMeta` from `metaQueueRedirect` and returns it to the BPU. The BPU restores the PHR circular pointer and high-order history, CommonHR `ghr`/`bw` state, and the RAS stack-top pointer, rolling prediction state back precisely to the error point.

<!-- **ResolveMeta（解析训练元数据）**：包含 mbtb、tage、sc、ittage、phr、commonHR 和 utage。当分支进入 resolve 阶段时，FTQ 将对应 FTQ entry 的 metaQueueResolve 取出并传递给 BPU 的 train 接口。BPU 将训练信息广播至所有子预测器，各预测器利用 meta 中保存的索引与状态信息直接定位待更新表项，无需重新计算索引，从而保证训练的精准性与一致性。 -->
**`ResolveMeta` (resolve-training metadata):** Contains `mbtb`, `tage`, `sc`, `ittage`, `phr`, `commonHR`, and `utage`. When a branch reaches resolve, the FTQ extracts the corresponding entry from `metaQueueResolve` and passes it to the BPU `train` interface. The BPU broadcasts the training information; each predictor uses the saved indices and state to locate entries directly, without recomputing indices, preserving training precision and consistency.

<!-- **CommitMeta（提交元数据）**：目前仅包含RAS: RASCommitMeta，在 commit 阶段将栈顶指针等提交态信息传递给主 RAS，用于更新非投机栈。该机制确保RAS 的架构状态仅在指令不可撤销地退休后才被修改，与推测阶段的投机性压栈/弹栈严格分离。 -->
**`CommitMeta` (commit metadata):** It currently contains only `RAS: RASCommitMeta`. At commit, stack-top and other committed-state information is passed to the main RAS to update the nonspeculative stack. RAS architectural state is therefore changed only after an instruction retires irreversibly, separate from speculative pushes and pops.

<!-- 三类元数据在 FTQ 中分别存储于独立的 metaQueue（metaQueueRedirect、metaQueueResolve、metaQueueCommit），通过 ftqIdx 索引访问。BPU 顶层在 S3 阶段完成元数据打包，并在 toFtq.meta 接口以 Decoupled 握手方式输出，valid 信号由 s3_valid 驱动。 -->
The three metadata classes are stored in independent FTQ queues (`metaQueueRedirect`, `metaQueueResolve`, and `metaQueueCommit`) and indexed by `ftqIdx`. The BPU packages metadata in S3 and emits it through `toFtq.meta` using a Decoupled handshake, with validity driven by `s3_valid`.

<!-- | 元数据类别 | 包含字段 | 使用场景 | -->
| Metadata class | Fields | Use |
| :--- | :--- | :--- |
<!-- | RedirectMeta | phr、commonHRMeta、RAS | 后端重定向，恢复预测状态 | -->
| `RedirectMeta` | `phr`, `commonHRMeta`, `RAS` | Backend redirect and prediction-state recovery |
<!-- | ResolveMeta | mbtb、tage、sc、ittage、phr、commonHR、utage | Resolve 阶段，训练更新预测器 | -->
| `ResolveMeta` | `mbtb`, `tage`, `sc`, `ittage`, `phr`, `commonHR`, `utage` | Resolve-stage predictor training and updates |
<!-- | CommitMeta | RAS | 指令提交，更新非投机RAS | -->
| `CommitMeta` | `RAS` | Instruction commit and nonspeculative RAS update |




<!-- 综上所述，BPU 顶层模块通过四级预测流水线实现低延迟与高准确率的平衡，通过 Flush 级联与 Override 机制保证投机错误的快速纠正，通过 Train/FastTrain 双通路实现预测器状态的及时更新，并通过 Commit 机制维护架构状态的安全性。PHR 与 CommonHR 两套分支历史系统分别为路径相关与方向相关的预测器提供特征输入，而三类元数据快照则为错误恢复与训练更新提供了预测时刻的精确上下文，共同构成了 BPU 顶层完整且严谨的工作体系。 -->
In summary, the BPU top level balances latency and accuracy with a four-stage prediction pipeline, rapidly corrects speculative errors through the flush cascade and override mechanism, updates predictor state promptly through the train/fastTrain paths, and protects architectural state with commit. PHR and CommonHR provide path- and direction-related features, while the three metadata snapshots preserve the exact prediction-time context needed for recovery and training. Together they form the complete BPU top-level operating model.

<!-- ## 2. **FallThroughPredictor ** -->
## 2. **FallThroughPredictor**
<!-- ### **2.1 总体架构** -->
### **2.1 Overall Architecture**
<!-- FallThroughPredictor 是香山昆明湖-v3 处理器分支预测单元（BPU）中最基础的预测器。其总体架构设计目标并非追求极致的预测精度，而是充当 BPU 流水线中的“兜底”或“默认”角色。在处理器前端取指阶段，当 uBTB、ABTB 等复杂预测器因未命中、未训练或流水线延迟而无法提供有效预测时，FallThroughPredictor 负责提供一条确定性的、低延迟的“顺序执行”路径。 -->
FallThroughPredictor is the most basic predictor in the XiangShan Kunminghu-v3 branch-prediction unit (BPU). Its architecture is not intended to maximize prediction accuracy; it serves as the BPU pipeline's fallback or default path. During frontend fetch, when complex predictors such as uBTB or ABTB cannot provide a valid result because of a miss, lack of training, or pipeline latency, FallThroughPredictor supplies a deterministic, low-latency sequential-execution path.

<!-- FallThroughPredictor 的功能特性如下： -->
FallThroughPredictor has the following characteristics:

<!-- 1. 确定性预测：该预测器永远预测分支指令 taken = false，即当前取指块内不存在跳转行为。 -->
1. Deterministic prediction: it always predicts `taken = false`, meaning that no control-flow transfer occurs within the current fetch block.

<!-- 2. 纯组合与时序逻辑：内部不含任何 SRAM、寄存器堆或历史表（如 TAGE、BTB）。仅包含简单的加法器、比较器和多路选择器，旨在在一个时钟周期内（S0 输入，S1 输出）完成地址计算。 -->
2. Pure combinational and sequential logic: it contains no SRAM, register file, or history table such as TAGE or BTB. It uses only simple adders, comparators, and multiplexers, and computes the address in one clock cycle (S0 input, S1 output).

<!-- 3. 硬件开销极低：其物理实现面积微乎其微，且不涉及训练（Training）或提交（Commit）阶段的复杂状态机更新。 -->
3. Minimal hardware cost: its physical area is negligible, and it has no complex state-machine updates during training or commit.

<!-- ### **2.2 工作机制与流水线时序** -->
### **2.2 Operation and Pipeline Timing**
<!-- FallThroughPredictor 采用单周期流水线设计，分为 S0 和 S1 两个阶段。由于该预测器仅需提供静态的下一个线性地址，因此不涉及 Override、Train/Update 或 Commit 机制。 -->
FallThroughPredictor uses a single-cycle pipeline with S0 and S1 stages. Because it only supplies a static next linear address, it has no override, train/update, or commit mechanism.

<!-- #### **2.2.1流水线时序** -->
#### **2.2.1 Pipeline Timing**
<!-- 流水线处理流程及时序如下表所示： -->
The pipeline flow and timing are shown below:

<!-- | 流水线阶段 | 操作描述 | -->
| Pipeline stage | Operation |
| :--- | :--- |
<!-- | S0 (计算准备) | 接收来自 FTQ 的起始程序计数器 s0_startPc。等待 stageCtrl.s0_fire 有效信号触发。 | -->
| S0 (calculation setup) | Receives the starting PC `s0_startPc` from the FTQ and waits for `stageCtrl.s0_fire`. |
<!-- | S1 (地址计算与输出) | 1. 计算下一对齐块地址 nextBlock。   2. 判定是否跨页 crossPage。   3. 计算控制流指令位置 cfiPosition。   4. 输出 prediction 结构体至 BPU 顶层仲裁逻辑。 | -->
| S1 (address calculation and output) | 1. Computes the next aligned-block address `nextBlock`. 2. Determines `crossPage`. 3. Computes `cfiPosition`. 4. Outputs the `prediction` bundle to the BPU top-level arbitration logic. |




<!-- #### **2.2.2 核心预测逻辑：跨页边界处理** -->
#### **2.2.2 Core Prediction Logic: Page-Boundary Handling**
<!-- 尽管预测结果始终为“不跳转”，但目标地址的计算并非简单的 PC + FetchBlockSize。由于 RISC-V 架构支持虚拟内存，取指块可能跨越页边界（Page Boundary）。FallThroughPredictor 实现了对跨页情形的精确处理，具体逻辑如下： -->
Although the prediction is always “not taken,” the target is not simply `PC + FetchBlockSize`. Because RISC-V supports virtual memory, a fetch block can cross a page boundary. FallThroughPredictor handles this case precisely as follows:

<!-- 1）跨页判断：对比当前预测块的起始 PC 和下一个预测块的起始 PC 是否跨页（比较地址高位是否相等） -->
1) Page-crossing check: compare the starting PC of the current and next prediction blocks to determine whether their high address bits differ.

<!-- 2）地址对齐：清除下一个预测块起始 PC 的块内偏移。 -->
2) Address alignment: clear the intra-block offset from the next block's starting PC.

<!-- 3）计算 NextPC： -->
3) Compute `NextPC`:

<!-- Ø 若 s1_crossPage = true，则发生跨页，NextPC = s1_nextPageAlignedPc； -->
* If `s1_crossPage = true`, a page crossing occurred and `NextPC = s1_nextPageAlignedPc`.

<!-- Ø 若s1_crossPage=false，则未发生跨页，NextPC = s1_nextBlockAlignedPc； -->
* If `s1_crossPage = false`, no page crossing occurred and `NextPC = s1_nextBlockAlignedPc`.

<!-- #### **2.2.3 控制流指令位置计算** -->
#### **2.2.3 Control-Flow Instruction Position**
<!-- 在 XiangShan 的取指架构中，cfiPosition 用于指示取指块内最后一条可能改 -->
In XiangShan's fetch architecture, `cfiPosition` identifies the last instruction in a fetch block that may change control flow.

<!-- 变控制流的指令位置。对于 FallThrough 预测，该值的计算逻辑在跨页与非跨页时存在差异： -->
For FallThrough prediction, its calculation differs between page-crossing and non-page-crossing cases:

<!-- Ø 跨页时：需要重新计算，因为实际取到的指令数可能少于完整块 -->
* On a page crossing, recompute it because fewer than a full block of instructions may be fetched.

<!-- Ø 不跨页：固定为最后一条指令的位置（如 31，假设 32 条指令/块） -->
* Without a page crossing, use the last instruction position (for example, 31 for a 32-instruction block).

<!-- ### **2.3 关键数据结构** -->
### **2.3 Key Data Structures**
<!-- FallThroughPredictor 是 BPU 中唯一不包含任何“存储器”结构的子模块。其内部关键结构本质为瞬时组合逻辑树，不包含任何 FTB Entry、TAGE 表项、SC 表项或 RAS 栈。 -->
FallThroughPredictor is the only BPU submodule without a memory structure. Its internal structure is an instantaneous combinational-logic tree; it contains no FTB entries, TAGE entries, SC entries, or RAS stack.

<!-- Ø 地址生成单元：由 getAlignedPc 和 getPageAlignedAddr 辅助函数构成，负责屏蔽低位地址以进行对齐操作。 -->
* Address-generation unit: helper functions `getAlignedPc` and `getPageAlignedAddr` mask low address bits for alignment.

<!-- Ø 页面交叉检测器：由 isCrossPage 函数实现，比较两个地址的高位是否相等。 -->
* Page-crossing detector: `isCrossPage` compares the high bits of two addresses.

<!-- ### **2.4  I/O 端口** -->
### **2.4 I/O Ports**
<!-- FallThroughPredictor 的 IO 端口定义严格遵循 BasePredictorIO 规范。其输入信号来源于 FTQ 的控制流，输出信号为标准的 Prediction 结构体。具体端口列表如下： -->
FallThroughPredictor's I/O follows the `BasePredictorIO` specification. Its inputs come from FTQ control flow, and its outputs use the standard `Prediction` bundle. The ports are listed below.

<!-- #### **2.4.1 FallThroughPredictor 输入/输出端口列表** -->
#### **2.4.1 FallThroughPredictor Input/Output Ports**
<!-- | 端口名称 | 方向 | 功能描述 | -->
| Port | Direction | Description |
| :--- | :--- | :--- |
<!-- | clock | Input | 模块工作时钟（隐式继承）。 | -->
| `clock` | Input | Module clock (implicitly inherited). |
<!-- | reset | Input | 模块复位信号（隐式继承）。 | -->
| `reset` | Input | Module reset (implicitly inherited). |
<!-- | io.stageCtrl | Input | 流水线握手控制。   - s0_fire: 指示 S0 阶段有效，需锁存 startPc。   - s1_fire: 指示 S1 计算完成且下游准备接收。 | -->
| `io.stageCtrl` | Input | Pipeline handshake control. `s0_fire` indicates that S0 is valid and must latch `startPc`; `s1_fire` indicates that S1 has completed and the downstream is ready. |
<!-- | io.startPc | Input | 起始程序计数器。   由 FTQ 提供的未对齐的实际起始 PC。 | -->
| `io.startPc` | Input | Starting program counter, supplied by the FTQ as the actual unaligned start PC. |
<!-- | io.train | Input | 训练接口（FallThrough Predictor 忽略此信号）。   始终置为 DontCare。 | -->
| `io.train` | Input | Training interface (ignored by FallThroughPredictor); always `DontCare`. |
<!-- | io.prediction | Output | 预测结果输出。   包含最终目标地址、CFI 位置及分支属性。 | -->
| `io.prediction` | Output | Prediction result containing the final target address, CFI position, and branch attributes. |
<!-- | io.resetDone | Output | 复位完成标志。   硬连线为 true.B（表示无需初始化 SRAM）。 | -->
| `io.resetDone` | Output | Reset-complete flag, hard-wired to `true.B` because no SRAM needs initialization. |
<!-- | io.trainReady | Output | 训练就绪标志。   硬连线为 true.B（表示无需繁忙状态）。 | -->
| `io.trainReady` | Output | Training-ready flag, hard-wired to `true.B` because there is no busy state. |




<!-- #### **2.4.2 Prediction 输出结构体字段详细说明** -->
#### **2.4.2 Prediction Output Fields**
<!-- | 字段名 | 功能描述 | -->
| Field | Description |
| :--- | :--- |
<!-- | taken | 分支方向预测。固定为假，表示始终顺序执行。 | -->
| `taken` | Direction prediction, fixed false to indicate sequential execution. |
<!-- | target | 预测目标地址。已处理跨页逻辑的下一块起始地址。 | -->
| `target` | Predicted target, the next-block start address after page-crossing handling. |
<!-- | cfiPosition | CFI 偏移位置。以指令为单位的偏移索引，用于指示块内潜在的控制流结束点。 | -->
| `cfiPosition` | CFI offset in instruction units, identifying the potential control-flow end within the block. |
<!-- | attribute | 分支属性标记。标记为非分支/非调用/非返回指令。 | -->
| `attribute` | Branch-attribute marker indicating a non-branch, non-call, non-return instruction. |


<!-- ## 3. **uTAGE ** -->
## 3. **uTAGE**
<!-- ### 3.1 **总体架构** -->
### 3.1 **Overall Architecture**
<!-- uTAGE（Micro TAGE）是香山 Kunminghu-v3 处理器 BPU S1 阶段的方向预测器，作为 Ahead BTB（ABTB）的校正层，对 ABTB 的基础方向预测进行精细化修正。模块基于 TAGE 算法，通过多张不同历史长度的预测表与标签匹配机制捕获分支相关性，在单周期内提供高精度方向预测。 -->
uTAGE (Micro TAGE) is the direction predictor in the S1 stage of the XiangShan Kunminghu-v3 BPU. It acts as a correction layer for the Ahead BTB (ABTB), refining ABTB's base direction prediction. Based on the TAGE algorithm, it captures branch correlation with multiple prediction tables of different history lengths and tag matching, providing a high-accuracy direction prediction in one cycle.

<!-- 作为 S1 阶段组件，uTAGE 须在单周期内完成从索引计算到结果输出的完整预测流程，以配合后续 S2/S3 阶段操作。模块支持每周期 8 条分支的并行预测，与 ABTB 带宽匹配，确保前端取指不受瓶颈限制。 -->
As an S1 component, uTAGE must complete indexing through output in one cycle to meet the later S2/S3 timing. It predicts eight branches in parallel per cycle, matching ABTB bandwidth so that frontend fetch is not bottlenecked.

<!-- #### **3.1.1 核心功能特性** -->
#### **3.1.1 Key Features**
<!-- | 特性 | 实现 | -->
| Feature | Implementation |
| :--- | :--- |
<!-- | 预测算法 | TAGE（Tagged GEometric history length） | -->
| Prediction algorithm | TAGE (Tagged Geometric history length) |
<!-- | 表结构 | 4张表 × 512组 × 1路，历史长度递增（5 / 9 / 16 / 24） | -->
| Table structure | 4 tables x 512 sets x 1 way, with increasing history lengths (5 / 9 / 16 / 24) |
<!-- | 训练机制 | 双周期流水线（T0请求 / T1更新），支持动态分配 | -->
| Training | Two-cycle pipeline (T0 request / T1 update) with dynamic allocation |
<!-- | 存储优化 | 4-Bank SRAM + 16项旁路影子缓冲（Bypass Shadow Buffer） | -->
| Storage optimization | Four-bank SRAM plus a 16-entry Bypass Shadow Buffer |
<!-- | 替换策略 | 基于非对称有用（useful）计数器的分级替换与衰减策略 | -->
| Replacement | Hierarchical replacement and ageing based on asymmetric useful counters |
<!-- | 并行能力 | 支持每周期8条分支指令的并行预测，延迟仅1周期（S1阶段） | -->
| Parallelism | Predicts eight branches in parallel per cycle with one-cycle latency (S1) |


<!-- #### **3.1.2 顶层架构概览** -->
#### **3.1.2 Top-Level Architecture Overview**
<!-- uTAGE 模块的顶层架构采用多表并行预测结构，内部实例化 NumTables 个独立的 MicroTageTable 子模块（当前配置为 4 个）。每个表具有独立的历史长度、集合数与标签宽度，以适应不同长度的分支历史模式。表 0 至表 3 的历史长度依次为 5、9、16、24，形成几何级数分布，确保对短周期循环分支和长周期相关分支均能有效覆盖。 -->
The uTAGE top level predicts with multiple tables in parallel and instantiates `NumTables` independent `MicroTageTable` submodules (four in the current configuration). Each table has its own history length, set count, and tag width to capture patterns of different lengths. Tables 0 through 3 use histories of 5, 9, 16, and 24 bits, forming a geometric progression that covers both short-period loops and long-range correlations.

<!-- 模块内部还包含一个全局的 A2 选择逻辑，负责在所有表的命中结果中按优先级仲裁出最终的预测方向。此外，每个表配备独立的 BypassShadowBuffer 写缓冲，用于暂存最近更新的表项，解决训练流水线与预测流水线之间的 RAW（Read-After-Write）冲突。Useful 计数器以 Bank 为单位组织，独立于 SRAM 存储，通过寄存器阵列实现单周期更新。 -->
The module also has global A2 selection logic that arbitrates the final direction from all table hits by priority. Each table has a dedicated BypassShadowBuffer write buffer for recently updated entries, resolving RAW (Read-After-Write) hazards between training and prediction. Useful counters are organized per bank, stored separately from SRAM, and updated in one cycle through register arrays.

<!-- ### **3.2 工作机制** -->
### **3.2 Operating Mechanism**
<!-- uTAGE 模块内部实现了一套三级微流水线（A0/A1/A2）用于预测路径，以及一套双周期训练流水线（T0/T1）用于模型更新。此外，模块还支持与 BPU 全局重定向机制对接的 override 处理逻辑。 -->
uTAGE implements a three-stage micro-pipeline (A0/A1/A2) for prediction and a two-cycle training pipeline (T0/T1) for model updates. It also supports override handling connected to the BPU's global redirect mechanism.

<!-- #### **3.2.1 预测流水线（A0 → A1 → A2）** -->
#### **3.2.1 Prediction Pipeline (A0 -> A1 -> A2)**
<!-- 预测路径从接收 S0 阶段传递的 PC 与折叠路径历史开始，经过三个微阶段 -->
The prediction path receives the PC and folded path history from S0 and passes through three micro-stages

<!-- 输出最终预测结果。整个预测流水在单周期内完成，满足 S1 阶段的时序约束。 -->
to produce the final prediction. The complete prediction pipeline finishes in one cycle, meeting S1 timing.

<!-- **1）A0 阶段：索引计算与读请求。**接收 startPc 与 foldedPathHist，通过 computeHashIdx 对每表独立计算读索引——将 PC 低位与折叠历史异或后取低 log₂(NumSets) 位。4 表索引同时计算、同时发出，确保 SRAM 读取延迟不串行累加。 -->
**1) A0: index calculation and read request.** It receives `startPc` and `foldedPathHist`, and independently computes each table's read index with `computeHashIdx` by XORing low PC bits with folded history and taking the low `log2(NumSets)` bits. All four indices are computed and issued together so SRAM latency does not accumulate serially.

<!-- **2）A1 阶段：读数据返回与标签比较。**接收 SRAM 及 BypassShadowBuffer 返回的条目数据。利用 computeHashTag 计算当前 PC 标签，与读出条目 tag 比较产生 tagHit 信号；从 takenCtr 提取方向预测。结果经寄存器锁存后传递至 A2。 -->
**2) A1: read response and tag comparison.** It receives entries returned by SRAM and the BypassShadowBuffer. `computeHashTag` generates the current PC tag, which is compared with each entry tag to produce `tagHit`; the direction is taken from `takenCtr`. Registers latch the result before passing it to A2.

<!-- **3）A2 阶段：最终选择与预测输出。**接收 A1 数据、ABTB 位置及预测结果。对每个 ABTB 条目，遍历各表检查命中（valid 为真、tagHit 为真、cfiPosition 匹配）。多表命中时按表索引由大到小（历史由长到短）优先选择。输出 takenVec、hitVec 及 meta 元数据。结果锁存至 A3 寄存器，用于 overrideValid 时的流水线旁路。 -->
**3) A2: final selection and prediction output.** It receives A1 data, ABTB positions, and prediction results. For every ABTB entry it checks each table for a hit (`valid`, `tagHit`, and matching `cfiPosition`). On multiple hits, it chooses the highest table index first (longest history). It outputs `takenVec`, `hitVec`, and metadata, and latches the result in A3 for bypass when `overrideValid` is asserted.

<!-- #### **3.2.2 Override 与重定向处理机制** -->
#### **3.2.2 Override and Redirect Handling**


<!-- uTAGE 支持两级重定向：overrideValid 用于 S3 阶段覆盖，A3 数据直接旁路至下一周期 A2 输入，提供基于最新全局历史的快速预测；redirectValid 用于全局重定向，触发流水线刷新并基于新 PC 重新发起预测。两信号时序不重叠，由 BPU 顶层调度器保证互斥。 -->
uTAGE supports two redirect levels. `overrideValid` handles S3 override by directly bypassing A3 data into the next cycle's A2 input for a fast prediction using the latest global history. `redirectValid` handles a global redirect, flushing the pipeline and restarting prediction from the new PC. Their timing does not overlap; the BPU top-level scheduler enforces mutual exclusion.

<!-- #### **3.2.3 训练流水线（T0 → T1）** -->
#### **3.2.3 Training Pipeline (T0 -> T1)**
<!-- uTAGE 的训练流水线采用双周期设计，与预测流水线解耦，允许在后端执行单元确认分支结果后异步更新预测模型。训练数据来源于后端提交的 BpuTrain 数据包，包含分支实际方向、PC、全局历史及 ABTB 预测结果等信息。 -->
The uTAGE training pipeline uses two cycles and is decoupled from prediction, allowing the model to be updated asynchronously after the backend confirms a branch result. Training data comes from the backend `BpuTrain` bundle and includes the actual direction, PC, global history, and ABTB prediction.

<!-- **1）T0 阶段：训练触发与误预测判定** -->
**1) T0: training trigger and misprediction detection**

<!-- T0 阶段接收 io.train 数据包，结合 ABTB 的预测结果计算每个分支是否发生误预测。误预测分为两类：hasHitMisPred（表命中但预测方向错误）与 missHitMisPred（表未命中且基础预测错误）。模块选择第一个发生误预测的分支作为本次训练的目标，决定对该分支执行表项更新或新条目分配，并生成各表的 T0 读索引（t0_trainIndex）。同时，T0 阶段读取各表对应位置的 useful 计数器与 cfiPosition 信息，为 T1 阶段的更新决策提供依据。 -->
T0 receives the `io.train` bundle and combines it with ABTB results to determine whether each branch was mispredicted. Two cases are distinguished: `hasHitMisPred` (a table hit with the wrong direction) and `missHitMisPred` (a table miss with an incorrect base prediction). The first mispredicted branch is selected as the training target; the module decides between entry update and new-entry allocation and generates each table's T0 read index (`t0_trainIndex`). T0 also reads the useful counter and `cfiPosition` at each table location to guide T1.

<!-- **2）T1 阶段：更新、分配与写回** -->
**2) T1: update, allocation, and write-back**

<!-- T1 阶段根据 T0 的判定结果执行具体操作。对于命中的表项，更新其 takenCtr 饱和计数器（实际 taken 则递增，否则递减）与 useful 计数器（表预测正确且基础预测错误时递增，表预测错误时递减）。对于未命中但需要新分配的情况，T1 阶段在所有表中按优先级（从低 ID 到高 ID）查找 useful == 0 的 way，选择第一个满足条件的表进行新条目分配。若所有候选表的 useful 均大于 0，则本次分配失败，触发全局 useful 衰减机制。 -->
T1 executes the operation selected by T0. For a hit, it updates the `takenCtr` saturating counter (increment on taken, decrement otherwise) and the useful counter (increment when the table is correct and the base prediction is wrong, decrement when the table is wrong). For a miss requiring allocation, T1 scans tables in increasing ID order for a way with `useful == 0` and allocates in the first eligible table. If every candidate has useful > 0, allocation fails and global useful ageing is triggered.

<!-- 所有更新与分配操作首先写入 BypassShadowBuffer，而非直接写 SRAM。写缓冲采用循环队列结构，新数据进入队列时更新优先级掩码，确保读取时返回最新副本。每个周期尝试将最旧的脏数据写回 SRAM，当队列接近满时强制写回，以避免训练数据丢失。 -->
All updates and allocations first enter the BypassShadowBuffer rather than SRAM. The buffer is a circular queue; inserting new data updates a priority mask so reads return the newest copy. Each cycle it attempts to write the oldest dirty entry back to SRAM, and forces write-back when nearly full to avoid losing training data.

<!-- #### **3.2.4 Commit 与全局状态维护** -->
#### **3.2.4 Commit and Global-State Maintenance**
<!-- uTAGE 模块本身不直接维护重排序缓冲区（ROB）级别的分支状态，而是依赖 BPU 顶层在分支 commit 时提供的训练数据包完成模型更新。然而，模块内部维护了两项需要周期性全局维护的状态：useful 计数器的全局衰减机制，以及 SRAM 复位状态。 -->
uTAGE does not directly maintain ROB-level branch state; it relies on the BPU top level to supply a training bundle at branch commit. Internally it maintains two states requiring periodic global maintenance: useful-counter ageing and SRAM reset state.

<!-- 全局衰减（Ageing）机制在分配失败时激活。模块维护两个计数器：lowTickCounter（7 位）记录低历史表（ID 0,1）连续分配失败次数，highTickCounter（8 位）记录高历史表（ID 2,3）连续分配失败次数。当计数器最高位变为 1 时，分别触发低表 useful 递减（不低于 0）或高表 useful 右移一位（折半衰减）。该非对称策略的设计依据在于：高历史表项的获取成本更高（需要更长的历史匹配），因此应给予更强的保护，避免被频繁淘汰。 -->
Global ageing is activated on allocation failure. Two counters are maintained: `lowTickCounter` (7 bits) records consecutive failures for low-history tables (IDs 0 and 1), and `highTickCounter` (8 bits) records failures for high-history tables (IDs 2 and 3). When a counter's MSB becomes 1, low-table useful values are decremented (not below 0), while high-table useful values are right-shifted by one (halved). This asymmetry protects high-history entries because they cost more to acquire and match.

<!-- resetDone 信号指示各表 SRAM 的复位完成状态。在处理器复位期间，所有 SRAM 需要被清零或初始化至默认状态，useful 计数器寄存器阵列同样需要复位。resetDone 在所有子模块复位完成后置位，向 BPU 顶层报告 uTAGE 已就绪。 -->
`resetDone` indicates that each table's SRAM reset is complete. During processor reset, every SRAM and the useful-counter register arrays must be cleared or initialized. After all submodules finish reset, `resetDone` is asserted to report that uTAGE is ready.

<!-- ### **3.3 关键数据结构** -->
### **3.3 Key Data Structures**
<!-- uTAGE 模块的内部存储结构围绕 TAGE 算法核心需求展开，主要包括 TAGE 表项（MicroTageEntry）、表配置信息（MicroTageInfo）、Useful 计数器、以及 BypassShadowBuffer 条目等。这些数据结构在存储密度、访问并行性与更新灵活性之间进行了针对性权衡。 -->
The uTAGE storage structures support the core TAGE algorithm and include TAGE entries (`MicroTageEntry`), table configuration (`MicroTageInfo`), useful counters, and BypassShadowBuffer entries. They balance storage density, access parallelism, and update flexibility.

<!-- #### **3.3.1 TAGE 表项（MicroTageEntry）** -->
#### **3.3.1 TAGE Entry (`MicroTageEntry`)**
<!-- MicroTageEntry 是 uTAGE 模块最核心的数据结构，每个表项存储一条分支历史模式的预测状态。当前实现中，每个表项的位宽为 24 位，各字段定义如下： -->
`MicroTageEntry` is the central uTAGE structure. Each entry holds prediction state for one branch-history pattern. In the current implementation, an entry is 24 bits wide and has the following fields:

<!-- | 字段名 | 位宽 | 类型 | 功能说明 | -->
| Field | Width | Type | Description |
| :--- | :--- | :--- | :--- |
<!-- | valid | 1 bit | Bool | 条目有效位，初始为 0，分配后置 1 | -->
| `valid` | 1 bit | Bool | Entry-valid bit; initially 0 and set to 1 on allocation. |
<!-- | tag | 16 bits | UInt | 标签，用于与当前 PC 和历史的哈希标签匹配 | -->
| `tag` | 16 bits | UInt | Tag used to match the hash of the current PC and history. |
<!-- | cfiPosition | 4 bits | UInt | 控制流指令（CFI）在取指块中的位置索引 | -->
| `cfiPosition` | 4 bits | UInt | Index of the control-flow instruction (CFI) in the fetch block. |
<!-- | takenCtr | 3 bits | SaturateCounter | 方向饱和计数器，正值预测 taken，负值预测 not-taken | -->
| `takenCtr` | 3 bits | SaturateCounter | Direction saturating counter: positive predicts taken, negative predicts not taken. |




<!-- #### **3.3.2 表配置信息（MicroTageInfo）** -->
#### **3.3.2 Table Configuration (`MicroTageInfo`)**
<!-- 每个 TAGE 表在实例化时通过 MicroTageInfo 参数类进行配置。当前 uTAGE 包含 4 张表，其参数配置如下： -->
Each TAGE table is configured at instantiation through the `MicroTageInfo` parameter class. The current uTAGE has four tables with these parameters:

<!-- | 表 ID | NumSets（组数） | HistoryLength（历史位宽） | HistBitsInTag（标签历史位） | TagWidth（标签总宽） | -->
| Table ID | `NumSets` (sets) | `HistoryLength` (history bits) | `HistBitsInTag` (tag-history bits) | `TagWidth` (total tag width) |
| :--- | :--- | :--- | :--- | :--- |
<!-- | 表 0 | 512 | 5 | 5 | 15 | -->
| Table 0 | 512 | 5 | 5 | 15 |
<!-- | 表 1 | 512 | 9 | 9 | 15 | -->
| Table 1 | 512 | 9 | 9 | 15 |
<!-- | 表 2 | 512 | 16 | 10 | 16 | -->
| Table 2 | 512 | 16 | 10 | 16 |
<!-- | 表 3 | 512 | 24 | 12 | 16 | -->
| Table 3 | 512 | 24 | 12 | 16 |




<!-- 历史长度按几何级数递增，低表捕获短周期模式，高表捕获长程依赖。HistBitsInTag 参与标签哈希，TagWidth 在 512 组配置下控制冲突概率。 -->
History lengths increase geometrically: lower tables capture short-period patterns, while higher tables capture long-range dependencies. `HistBitsInTag` contributes to tag hashing, and `TagWidth` controls collision probability with 512 sets.

<!-- #### **3.3.3 Useful 计数器与替换策略** -->
#### **3.3.3 Useful Counters and Replacement Policy**
<!-- Useful 计数器是 TAGE 算法替换策略的核心元数据，独立于表项存储，以 Bank 为单位组织在寄存器阵列中。每个表项对应一个 2 位 useful 计数器，取值范围为 0 至 3，用于衡量该表项在过去预测中是否提供了有效的修正价值。 -->
Useful counters are the key metadata for TAGE replacement. They are stored separately from entries, organized per bank in register arrays. Each entry has a 2-bit useful counter ranging from 0 to 3, measuring whether it has provided useful corrective value in prior predictions.

<!-- Useful 的更新遵循以下规则：若某表预测正确且 ABTB 基础预测错误（即该表提供了关键修正），则 useful 递增（饱和至 3）；若表预测错误，则 useful 递减（饱和至 0）；若表预测正确但基础预测也正确，或表未命中，则 useful 保持不变。该设计确保只有真正"不可替代"的表项才能获得高 useful 值，从而在替换时受到保护。 -->
Useful values are updated as follows: increment (saturating at 3) when a table is correct while the ABTB base prediction is wrong, meaning the table provided a crucial correction; decrement (saturating at 0) when the table is wrong; otherwise leave it unchanged when both predictions are correct or the table misses. This protects only genuinely irreplaceable entries during replacement.

<!-- 新分配表项的 useful 初始值采用非对称策略：低历史表（ID 0,1）初始为 WeakNegative（通常为 1），高历史表（ID 2,3）初始为 WeakPositive（通常为 2）。该策略的直觉在于：低历史表项的获取成本低、数量多，应更快进入可替换状态；高历史表项稀缺且训练周期长，应给予更长的保护期。 -->
Newly allocated entries use asymmetric initial useful values: low-history tables (IDs 0 and 1) start at `WeakNegative` (normally 1), and high-history tables (IDs 2 and 3) start at `WeakPositive` (normally 2). Low-history entries are plentiful and cheap to acquire, so they should become replaceable sooner; scarce high-history entries need a longer protection period because their training cycles are longer.

<!-- #### **3.3.4 BypassShadowBuffer 写缓冲** -->
#### **3.3.4 BypassShadowBuffer Write Buffer**
<!-- BypassShadowBuffer 是每个 TAGE 表配套的写缓冲结构，深度为 16 项，用于解决训练流水线与预测流水线之间的 RAW 冲突。其核心设计包含三个功能：第一，暂存 T1 阶段写入的新分配或更新表项，避免直接写 SRAM 带来的长延迟与写冲突；第二，在 A1 阶段读取时优先查询缓冲，若命中则直接返回最新数据，保证预测流水线看到的是已训练的最新模型；第三，按周期将最旧的脏数据写回 SRAM，维持缓冲的可用容量。 -->
The BypassShadowBuffer is a 16-entry write buffer paired with each TAGE table to resolve RAW hazards between training and prediction. It temporarily stores newly allocated or updated T1 entries rather than writing SRAM directly; A1 checks it first and returns the newest matching data; and it periodically writes the oldest dirty data back to SRAM to retain available capacity.

<!-- 缓冲内部采用循环队列结构，新写入的数据进入队列尾部，并更新优先级掩码确保读取时返回匹配索引的最新副本。每个周期执行一次写回尝试，选择队列中最旧的条目写回 SRAM；当队列占用超过阈值时触发强制写回，防止训练数据积压。Useful 计数器不经过写缓冲，直接更新寄存器阵列，因其更新频率高且无需持久化至 SRAM。 -->
Internally, the buffer is a circular queue. New writes enter at the tail and update a priority mask so reads return the newest copy for an index. It attempts one write-back per cycle using the oldest entry and forces write-back above an occupancy threshold to prevent training-data buildup. Useful counters bypass this buffer and update their register arrays directly because they change frequently and need not persist in SRAM.

<!-- ### **3.4  I/O 端口** -->
### **3.4 I/O Ports**
<!-- #### **3.4.1 顶层I/O** -->
#### **3.4.1 Top-Level I/O**
<!-- | **信号名** | **方向** | **功能描述** | -->
| **Signal** | **Direction** | **Description** |
| :--- | :--- | :--- |
<!-- | **控制信号** |  |  | -->
| **Control signals** |  |  |
<!-- | enable | Input | 模块使能信号 | -->
| `enable` | Input | Module enable. |
<!-- | stageCtrl.s0_fire | Input | S0阶段有效，指示预测请求有效 | -->
| `stageCtrl.s0_fire` | Input | S0 valid; indicates a valid prediction request. |
<!-- | stageCtrl.s1_fire | Input | S1阶段有效，指示预测输出有效 | -->
| `stageCtrl.s1_fire` | Input | S1 valid; indicates a valid prediction output. |
<!-- | stageCtrl.t0_fire | Input | T0阶段有效，指示训练请求有效 | -->
| `stageCtrl.t0_fire` | Input | T0 valid; indicates a valid training request. |
<!-- | trainReady | Output | 训练就绪标志，恒为true.B | -->
| `trainReady` | Output | Training-ready flag, always `true.B`. |
<!-- | resetDone | Output | 复位完成标志 | -->
| `resetDone` | Output | Reset-complete flag. |
<!-- | **预测输入** |  |  | -->
| **Prediction inputs** |  |  |
<!-- | startPc | Input | 预测起始程序计数器（PC） | -->
| `startPc` | Input | Starting prediction PC. |
<!-- | foldedPathHist | Input | 动态折叠的全局路径历史 | -->
| `foldedPathHist` | Input | Dynamically folded global path history. |
<!-- | **ABTB接口** |  |  | -->
| **ABTB interface** |  |  |
<!-- | abtbPosVec | Input | ABTB提供的各CFI指令位置向量 | -->
| `abtbPosVec` | Input | CFI-position vector supplied by ABTB. |
<!-- | abtbPrediction | Input | ABTB的基础预测结果 | -->
| `abtbPrediction` | Input | ABTB base prediction. |
<!-- | **控制覆盖与重定向** |  |  | -->
| **Override and redirect controls** |  |  |
<!-- | overrideValid | Input | S3阶段重定向覆盖有效，用于流水线冲刷 | -->
| `overrideValid` | Input | S3 redirect override valid; used to flush the pipeline. |
<!-- | redirectValid | Input | 重定向有效信号 | -->
| `redirectValid` | Input | Redirect-valid signal. |
<!-- | **预测输出** |  |  | -->
| **Prediction outputs** |  |  |
<!-- | prediction.takenVec | Output | 各预测槽位的最终跳转方向 | -->
| `prediction.takenVec` | Output | Final direction for each prediction slot. |
<!-- | prediction.hitVec | Output | 各预测槽位是否命中uTAGE表 | -->
| `prediction.hitVec` | Output | Whether each slot hits a uTAGE table. |
<!-- | meta | Output | 包含各表索引、命中信息的元数据，用于训练 | -->
| `meta` | Output | Training metadata containing table indices and hit information. |
<!-- | **训练输入** |  |  | -->
| **Training inputs** |  |  |
<!-- | train | Input | 来自后端的动态训练数据包 | -->
| `train` | Input | Dynamic training bundle from the backend. |
<!-- | foldedPathHistForTrain | Input | 训练时刻的动态折叠路径历史 | -->
| `foldedPathHistForTrain` | Input | Dynamically folded path history at training time. |




<!-- #### **3.4.2 MicroTageTableIO（单表接口）** -->
#### **3.4.2 MicroTageTableIO (Single-Table Interface)**


<!-- | **信号** | **方向** | **说明** | -->
| **Signal** | **Direction** | **Description** |
| :--- | :--- | :--- |
<!-- | req.valid | Input | 读请求有效 | -->
| `req.valid` | Input | Read request valid. |
<!-- | req.bits.readIndex | Input | 读索引 | -->
| `req.bits.readIndex` | Input | Read index. |
<!-- | resps.readEntries | Output | 读出的表项 | -->
| `resps.readEntries` | Output | Read entries. |
<!-- | train.t0_trainIndex | Input | T0训练索引 | -->
| `train.t0_trainIndex` | Input | T0 training index. |
<!-- | train.t0_read | Output | T0读出的训练信息 | -->
| `train.t0_read` | Output | Training information read in T0. |
<!-- | train.t1_tag | Input | T1标签 | -->
| `train.t1_tag` | Input | T1 tag. |
<!-- | train.t1_update | Input | T1更新信息 | -->
| `train.t1_update` | Input | T1 update information. |
<!-- | train.t1_alloc | Input | T1分配信息 | -->
| `train.t1_alloc` | Input | T1 allocation information. |
<!-- | usefulReset | Input | useful计数器复位 | -->
| `usefulReset` | Input | Useful-counter reset. |
<!-- | resetDone | Output | SRAM复位完成 | -->
| `resetDone` | Output | SRAM reset complete. |




<!-- #### **3.4.3 BypassShadowBufferIO（写缓冲接口）** -->
#### **3.4.3 BypassShadowBufferIO (Write-Buffer Interface)**
<!-- | **信号** | **方向** | **说明** | -->
| **Signal** | **Direction** | **Description** |
| :--- | :--- | :--- |
<!-- | req.readIndex | Input | 查询索引 | -->
| `req.readIndex` | Input | Lookup index. |
<!-- | resp.hit | Output | 命中向量 | -->
| `resp.hit` | Output | Hit vector. |
<!-- | resp.readEntries | Output | 读出的表项 | -->
| `resp.readEntries` | Output | Read entries. |
<!-- | train | Bidir | 训练接口（同MicroTageTable） | -->
| `train` | Bidir | Training interface (same as `MicroTageTable`). |
<!-- | tryWrite | Output | 尝试写回SRAM请求 | -->
| `tryWrite` | Output | SRAM write-back request. |
<!-- | writeSuccess | Input | 写回成功确认 | -->
| `writeSuccess` | Input | Write-back success acknowledgement. |
<!-- | usefulReset | Input | useful复位 | -->
| `usefulReset` | Input | Useful reset. |


<!-- ## 4. **Ahead BTB ** -->
## 4. **Ahead BTB**
<!-- ### **4.1 总体架构** -->
### **4.1 Overall Architecture**
<!-- Ahead BTB（Ahead Branch Target Buffer）位于香山处理器前端分支预测单元的底层，作为面向条件分支与直接跳转分支的目标地址和方向预测器。与传统的FTB（Fetch Target Buffer）不同，Ahead BTB 不依赖复杂的块内偏移编码，而是以单条分支指令为粒度，为每个取指周期内的多个候选分支位置并行输出预测结果。该模块的主要功能包括：根据当前取指起始 PC 预测取指块内各分支指令是否存在、提供跳转目标地址、输出分支类型以及基于饱和计数器的方向判断。Ahead BTB 同时支持多条目并行输出，以适应每周期处理多条指令的前端带宽需求。 -->
Ahead BTB (Ahead Branch Target Buffer) is a lower-level predictor in the XiangShan frontend. It predicts targets and directions for conditional and direct-jump instructions. Unlike a conventional FTB (Fetch Target Buffer), it does not rely on complex intra-block offset encoding; instead, it operates at single-branch granularity and emits predictions for multiple candidate positions in parallel each fetch cycle. It determines whether branches exist in the block from the starting fetch PC, supplies targets, reports branch types, and makes direction decisions with saturating counters. Multiple entries can be output in parallel to match frontend multi-instruction bandwidth.

<!-- Ahead BTB 在物理组织上采用多 bank、多 set、多 way 的存储结构，默认配置为 4 个 bank、每个 bank 包含 32 个 set、每个 set 包含 8 个 way，共计 1024 条表项。每个 bank 内部使用单端口 SRAM 存储表项，并通过写缓冲机制解决读写冲突。替换策略采用 PLRU（Pseudo-Least Recently Used）算法，每个 set 独立维护替换状态。预测流水线深度为两拍（模块内 S1 至 S2），外部从发起请求到获得输出共需约两个时钟周期。训练更新流水线同样为两拍，通过 fastTrain 接口接收来自 FTQ 的分支执行结果，完成计数器更新和表项分配。 -->
Physically, Ahead BTB uses multiple banks, sets, and ways: the default configuration has four banks, 32 sets per bank, and eight ways per set, for 1024 entries. Each bank stores entries in single-port SRAM and uses a write buffer to resolve read/write conflicts. Replacement uses PLRU (Pseudo-Least Recently Used), with independent state per set. The prediction pipeline is two cycles internally (S1 to S2), or about two clocks from request to output externally. Training is also two cycles and receives branch outcomes from the FTQ through `fastTrain` to update counters and allocate entries.

<!-- ### **4.2 工作机制** -->
### **4.2 Operating Mechanism**
<!-- **4.2.1 预测流水线** -->
**4.2.1 Prediction Pipeline**

<!-- Ahead BTB 的预测流水线在模块内部组织为两个同步时钟周期（S1 和 S2），此外在 BPU 顶层还存在一个外部 S0 周期用于地址生成和 bank 选择。为便于理解，以下按外部视角描述完整预测流程。 -->
Internally, Ahead BTB's prediction pipeline occupies two synchronous cycles (S1 and S2); the BPU top level also has an external S0 cycle for address generation and bank selection. The complete flow is described below from the external viewpoint.

<!-- 在外部S0 阶段，BPU 顶层向 Ahead BTB 提供当前取指块的起始 PC（startPc），并通过 stageCtrl.s0_fire 信号指示预测请求有效。Ahead BTB 内部立即根据该 PC 计算 set 索引和 bank 索引：set 索引取自 PC 的中间字段，bank索引取自PC的低位字段。随后，模块将读请求发送至对应 bank 的 SRAM 控制器，每个 bank 根据收到的 set 索引并行读出该 set 内所有 way 的表项数据。 -->
In external S0, the BPU supplies the current fetch-block start PC (`startPc`) and asserts `stageCtrl.s0_fire` for a valid request. Ahead BTB immediately derives the set index and bank index from the PC: the set index uses middle PC bits and the bank index uses low bits. It then sends read requests to the corresponding bank SRAM controllers, which read all ways in the selected set in parallel.

<!-- 在S1 阶段，各 bank 的 SRAM 返回读响应数据（readResp.entries），Ahead BTB 根据之前记录的 bank 掩码（bankMask）选择对应 bank 的 entries 进入下一级流水线。同时，模块寄存了当前预测请求的 startPc 和 set 索引等信息，并开始计算用于 tag 比较的 PC 高位字段。值得注意的是，代码中维护了一组s3_* 寄存器的旁路缓存（s3_entries、s3_setIdx、s3_bankMask、s3_startPc），当 overrideValid 信号有效时，S1 阶段会用这组寄存值替代从 SRAM 新鲜读出的数据，从而允许后级预测器用更晚产生的结果覆盖早期读出的数据。这种设计主要用于处理 SC（Statistical Corrector）等后级校正器对 Ahead BTB 预测结果的修正需求。 -->
In S1, each bank's SRAM returns read data (`readResp.entries`). Ahead BTB uses the previously recorded bank mask (`bankMask`) to select entries for the next stage. It also registers the request's `startPc` and set index and begins deriving high PC bits for tag comparison. A set of `s3_*` bypass registers (`s3_entries`, `s3_setIdx`, `s3_bankMask`, `s3_startPc`) is maintained. When `overrideValid` is asserted, S1 uses these saved values instead of fresh SRAM data, allowing a later predictor such as SC (Statistical Corrector) to override an earlier result.

<!-- 在S2 阶段，Ahead BTB 执行 tag 比较以及方向判断。模块首先根据当前startPc 计算 tag（PC 的高位字段），并与每个 way 表项中存储的 tag 进行比对，生成 hitMask 向量。对于命中的表项，模块再从 takenCounter 三维数组（按 bank、set、way 组织）中读出对应 way 的饱和计数器值，判断其是否大于 1（isPositive）作为方向预测结果。若计数器为 2 或 3 则预测 taken，为 0 或 1则预测 not taken。与此同时，模块还根据计数器的饱和状态（值为 0 或 3 时即为强偏置）输出 isStrongBias 标志，供 SC 等后级模块使用。最终，预测结果（包括 taken 方向、cfiPosition、分支属性、目标地址）通过 io.prediction 和io.abtbResult 端口并行输出，支持 NumAheadBtbPredictionEntries 个条目（默认等于取指块内最大分支数）。 -->
In S2, Ahead BTB compares tags and decides direction. It computes a tag from the current `startPc`, compares it with every way's stored tag, and forms `hitMask`. For hits, it reads the corresponding saturating counter from the bank/set/way `takenCounter` array and uses `isPositive` (value > 1) as the direction. Values 2 or 3 predict taken; 0 or 1 predict not taken. A counter at 0 or 3 is strongly biased, so `isStrongBias` is also emitted for SC and other later stages. The final prediction (taken direction, `cfiPosition`, attributes, and target) is output in parallel through `io.prediction` and `io.abtbResult` for `NumAheadBtbPredictionEntries` entries.

<!-- 当出现多命中（multi-hit）情况时，即同一 set 内有多个 way 的表项具有相同的 tag 且存储在相同的分支位置（position 相等），模块会检测并标记该状态，随后在写路径中向其中一个冲突 way 写入无效表项，以此消除多命中导致的不确定性。 -->
On a multi-hit, where multiple ways in one set have the same tag and branch position, the module detects and marks the condition. The write path then invalidates one conflicting way to remove the ambiguity.

<!-- **4.2.2 重定向与覆盖处理** -->
**4.2.2 Redirect and Override Handling**

<!-- Ahead BTB 支持两种外部干预机制：重定向（redirect）和覆盖（override）。重定向由后端执行单元通过 io.redirectValid 信号触发，表示检测到分支误预测，需要清空当前正在进行的预测流水线。模块内部收到重定向信号后，在 S2阶段将 s2_flush 置高，进而清空 s1_valid 和 s2_valid 寄存器，丢弃所有尚未完成的预测请求。需要说明的是，重定向不会回滚 SRAM 中已存储的表项，仅影响流水线状态。 -->
Ahead BTB supports two external interventions: redirect and override. A backend execution unit asserts `io.redirectValid` when it detects a misprediction, requiring the in-flight prediction pipeline to be cleared. The module raises `s2_flush` in S2, clears `s1_valid` and `s2_valid`, and discards incomplete requests. It does not roll back entries already stored in SRAM; only pipeline state is affected.

<!-- 覆盖机制由后级预测器（如SC）通过 io.overrideValid 信号触发，表示后级模块对 Ahead BTB 的预测结果有修正意见，需要用之前寄存的 s3_* 旁路缓存数据覆盖当前S1阶段从SRAM读出的数据。具体而言，当 overrideValid 有效时，S1 阶段实际使用的 entries、setIdx、bankMask和startPc 均切换为 s3_* 寄存值而非新鲜读出的值。这些 s3_* 寄存器在每次 S2 阶段预测完成（s2_fire）时更新，因此始终保存着最近一次成功预测的完整上下文。这种设计使得后级预测器可以在不重新发起 SRAM 读请求的情况下，快速复用上一周期的预测数据并施加修正，从而优化时序路径。 -->
The override mechanism is triggered by a later predictor such as SC through `io.overrideValid`. It replaces the fresh S1 SRAM data with the saved `s3_*` bypass context. When valid, S1 uses the registered `entries`, `setIdx`, `bankMask`, and `startPc` instead of new reads. These registers update on every successful S2 prediction (`s2_fire`), so they hold the latest complete context. Later predictors can therefore reuse the prior cycle's data and apply a correction without starting another SRAM read, improving timing.

<!-- **4.2.3 训练与更新机制** -->
**4.2.3 Training and Update**

<!-- Ahead BTB的训练更新通过io.fastTrain接口接收来自FTQ（Fetch Target Queue）的分支执行结果，主训练入口条件为：io.enable有效、fastTrain有效、且训练条目中的finalPrediction.taken为真、且abtbMeta.valid有效。满足条件时t0_fire置高，训练请求进入流水线。 -->
Ahead BTB receives branch outcomes from the FTQ (Fetch Target Queue) through `io.fastTrain`. The main training entry requires `io.enable`, `fastTrain`, `finalPrediction.taken`, and `abtbMeta.valid` all to be valid. Under these conditions, `t0_fire` is asserted and the request enters the training pipeline.

<!-- 在T1阶段（由t0_fire寄存一拍得到），模块根据训练信息执行实际的更新操作。更新分为三类：计数器更新、新表项分配、目标地址修正。 -->
In T1 (one cycle after `t0_fire`), the module executes updates from the training information. There are three kinds: counter updates, new-entry allocation, and target-address correction.

<!-- 对于计数器更新，模块遍历每个bank、每个set、每个way的takenCounter，根据t1_meta中保存的命中信息、分支属性以及实际跳转结果进行增减。具体规则如下：对于条件分支，若实际跳转发生且该way的分支位置等于当前训练分支的位置（posEqual），则计数器自增（selfIncrease）；若实际跳转未发生且该way是条件分支且其位置在当前训练分支之前（posBefore），或者实际跳转发生但该way的位置在当前训练分支之前，则计数器自减（selfDecrease）。非条件分支不更新计数器。此外，当bank返回写响应且needResetCtr为真时（通常发生在多命中修复场景），模块将对应计数器重置为弱正向状态（resetWeakPositive）。 -->
For counter updates, the module iterates over every bank, set, and way in `takenCounter`, using the hit information, branch attributes, and actual outcome saved in `t1_meta`. For a conditional branch that is taken and has the same position as the training branch (`posEqual`), the counter increments (`selfIncrease`). If the branch is not taken and the way is a conditional branch before the training position (`posBefore`), or if it is taken while the way is before the training position, the counter decrements (`selfDecrease`). Non-conditional branches are not updated. When a bank returns a write response with `needResetCtr` (usually for multi-hit repair), the corresponding counter is reset to weakly positive (`resetWeakPositive`).

<!-- 对于新表项分配，当训练分支在预测时未命中任何表项（t1_hit为假），且该分支为实际跳转的分支时，模块执行分配操作。分配时，replacer根据当前set索引提供victimWay（被替换的way编号），模块将新分支的tag、position、属性以及目标地址低位写入该way。若目标地址修正功能开启（EnableTargetFix为真），还会同时计算目标进位（targetCarry）一并存储。 -->
For allocation, if the training branch missed every entry (`t1_hit` is false) and is actually taken, the module allocates an entry. The replacer supplies a `victimWay` for the set, and the module writes the new branch tag, position, attributes, and low target bits there. With `EnableTargetFix`, it also computes and stores the target carry.

<!-- 对于目标地址修正，当预测时命中的表项属于间接分支（attribute.isIndirect为真），且存储的目标低位与实际执行的目标低位不匹配时，模块执行修正写入。此时不改变way索引（仍使用命中时的way），仅更新目标低位和进位信息。 -->
For target correction, if a hit entry is indirect (`attribute.isIndirect` is true) and its stored low target bits differ from the executed target, the module writes a correction. The way index remains unchanged; only low target bits and carry information are updated.



<!-- 所有写入请求均通过各bank的writeReq端口发送，并经过bank内部的WriteBuffer缓存后再写入SRAM。每个bank在写操作真正提交到SRAM后返回writeResp信号，通知上层计数器执行reset操作（如needResetCtr为真）以及通知replacer更新PLRU状态。 -->
All write requests are sent through each bank's `writeReq` port, buffered in the bank's `WriteBuffer`, and then written to SRAM. After a write is committed to SRAM, the bank returns `writeResp`, notifying the upper level to reset counters when `needResetCtr` is true and to update PLRU state in the replacer.

<!-- ### **4.3 关键数据结构** -->
### **4.3 Key Data Structures**
<!-- **4.3.1 AheadBtbEntry表项结构** -->
**4.3.1 `AheadBtbEntry` Structure**

<!-- Ahead BTB的SRAM中存储的表项定义为AheadBtbEntry，包含以下字段： -->
Entries stored in Ahead BTB SRAM are defined as `AheadBtbEntry` and contain the following fields:

<!-- | 字段 | 位宽 | 含义 | -->
| Field | Width | Meaning |
| :--- | :--- | :--- |
<!-- | valid | 1 | 表项是否有效 | -->
| `valid` | 1 | Whether the entry is valid. |
<!-- | tag | TagWidth（默认 24） | 用于 PC tag 比较（高位） | -->
| `tag` | `TagWidth` (24 by default) | High PC tag used for comparison. |
<!-- | position | CfiPositionWidth | 分支在 fetch block 中的位置 | -->
| `position` | `CfiPositionWidth` | Branch position within the fetch block. |
<!-- | attribute | BranchAttribute（branchType + RASAction） | 分支属性（条件/直接/间接 / RAS 操作） | -->
| `attribute` | `BranchAttribute` (`branchType + RASAction`) | Branch attributes: conditional/direct/indirect and RAS action. |
<!-- | targetLowerBits | TargetLowerBitsWidth（默认 22） | 目标地址的低位部分 | -->
| `targetLowerBits` | `TargetLowerBitsWidth` (22 by default) | Low target-address bits. |
<!-- | targetCarry | Option[TargetCarry]（若 EnableTargetFix） | 目标高位的 carry 修正（用于跨边界修复） | -->
| `targetCarry` | `Option[TargetCarry]` (when `EnableTargetFix`) | Carry correction for high target bits, used for cross-boundary repair. |




<!-- 与传统的FTB表项相比，Ahead BtbEntry将方向预测信息分离到独立的TakenCounter中，表项本身不存储方向或强弱标志。这种设计使得分支目标地址和分支方向可以独立更新，例如在多命中修复场景下只重置计数器而不修改目标地址，或者在间接分支目标修正时只更新低位而保持计数器不变。 -->
Unlike a conventional FTB entry, `AheadBtbEntry` separates direction prediction into an independent `TakenCounter`; the entry itself stores neither direction nor strength. Target and direction can therefore be updated independently, such as resetting only the counter during multi-hit repair or changing only low target bits for an indirect-target correction.

<!-- #### **4.3.2 TakenCounter方向计数器** -->
#### **4.3.2 `TakenCounter` Direction Counter**
<!-- TakenCounter是一个三维数组，维度分别为NumBanks、NumSets、NumWays，每个表项为一个2比特饱和计数器（宽度可配置）。该计数器独立于SRAM存储，使用寄存器阵列实现，从而支持单周期内完成读写更新操作。计数器的语义为：数值2和3表示倾向于taken，数值0和1表示倾向于not taken；isPositive方法返回计数器值大于1的结果，作为方向预测输出；isSaturate方法返回计数器值为0或3的结果，用于判断当前是否处于强偏置状态。 -->
`TakenCounter` is a three-dimensional array indexed by `NumBanks`, `NumSets`, and `NumWays`. Every element is a configurable two-bit saturating counter. It is stored separately from SRAM in register arrays so reads and writes can update in one cycle. Values 2 and 3 favor taken; 0 and 1 favor not taken. `isPositive` returns counter > 1 for the direction output, while `isSaturate` returns true for 0 or 3 to indicate strong bias.

<!-- 计数器更新策略与分支类型和相对位置强相关。对于条件分支，只有在实际跳转且该way的分支位置等于当前训练分支时才增加，或者在实际未跳转且该way位置在训练分支之前时才减少。这种设计源于一个取指块内可能存在多个分支，当后续分支实际跳转时，位于其之前的所有条件分支都应被视为“未实际执行”或“被跳过”，因此需要递减其计数器。非条件分支（如直接跳转和间接跳转）不更新计数器，始终视为taken。 -->
Counter update depends strongly on branch type and relative position. For a conditional branch, increment only if it actually takes and the way's position equals the training branch; decrement if it does not take and the way lies before the training branch. A fetch block can contain multiple branches; if a later branch actually takes, earlier conditional branches are treated as not executed or skipped, so their counters must be decremented. Non-conditional branches such as direct and indirect jumps do not update counters and are always considered taken.

<!-- #### **4.3.3 替换策略与PLRU状态机** -->
#### **4.3.3 Replacement Policy and PLRU State Machine**
<!-- 每个bank独立维护一套替换策略状态，由AheadBtbReplacer模块实现。该模块内部实例化了一个ReplacerState（存储每个set的PLRU状态，宽度为NumWays-1比特）和两个PlruStateGen计算单元（分别用于预测路径和训练路径的状态更新）。PLRU算法通过二叉树结构记录每个way的最近使用情况：当某个way被命中或写入时，沿着从根节点到该way叶子节点的路径翻转各节点的指向位，使得该way成为“最近使用”状态；需要替换时，从根节点开始沿着指向位下降，最终到达的way即为victim。 -->
Each bank maintains its own replacement state through `AheadBtbReplacer`. It instantiates a `ReplacerState` storing the PLRU state for every set (`NumWays - 1` bits wide) and two `PlruStateGen` units, one each for prediction and training updates. The PLRU algorithm uses a binary tree to track recency: a hit or write flips selector bits along the root-to-way path, marking that way recently used; replacement follows selector bits from the root to choose the victim.

<!-- 在预测路径中，每次读访问（io.readValid有效且存在命中way）时，所有命中的way都会被标记为touched，PlruStateGen根据这些touched way计算下一状态并写回。在训练路径中，每次成功写入（io.writeValid有效）时，被写入的way同样被标记为touched，更新PLRU状态。当读写同时发生且访问同一set时（读写冲突），写操作优先级更高，先更新状态再处理读操作。 -->
On the prediction path, each read with `io.readValid` and a hit marks every hit way as touched; `PlruStateGen` computes and writes the next state. On the training path, a successful `io.writeValid` marks the written way as touched and updates PLRU. When read and write access the same set in one cycle, the write has priority and updates state first.

<!-- #### **4.3.4 写缓冲与读写冲突处理** -->
#### **4.3.4 Write Buffer and Read/Write Conflicts**
<!-- 每个AheadBtbBank内部实例化了一个WriteBuffer（深度由WriteBufferSize参数控制，默认为4），用于缓存来自上层模块的写请求。由于SRAM配置为单端口且读优先级高于写，当读请求和写请求同时到达时，读请求直通SRAM，写请求被推入写缓冲。写缓冲的读端口连接到SRAM的写接口，只有当SRAM处于空闲状态（即当前周期无读请求）且写缓冲非空时，才会将缓冲中的写请求提交到SRAM。 -->
Each `AheadBtbBank` instantiates a `WriteBuffer` (depth controlled by `WriteBufferSize`, default 4) to cache writes from the upper module. The SRAM is single-port and gives reads priority. If read and write arrive together, the read goes directly to SRAM while the write enters the buffer. The buffer's read port connects to the SRAM write interface, and buffered writes are committed only when SRAM is idle (no read in the current cycle) and the buffer is non-empty.

<!-- 写缓冲是一个ValidIO接口的队列，写请求的valid信号为io.writeReq.valid，ready信号表示缓冲是否还有空位。当缓冲满时，新的写请求会被丢弃，并由性能计数器记录丢写事件。每个写请求提交到SRAM后，bank会产生writeResp信号，携带needResetCtr标志和set、way信息，通知上层模块该写操作已完成。特别地，当写操作是因多命中修复而触发的无效表项写入时，needResetCtr标志为真，触发对应计数器的重置操作。 -->
The write buffer is a `ValidIO` queue. `io.writeReq.valid` marks a write request, and `ready` indicates free capacity. When full, new requests are dropped and a performance counter records the loss. After a write reaches SRAM, the bank emits `writeResp` with `needResetCtr`, set, and way information. An invalidation caused by multi-hit repair sets `needResetCtr`, resetting the corresponding counter.

<!-- #### **4.3.5 元数据输出（AheadBtbMeta）** -->
#### **4.3.5 Metadata Output (`AheadBtbMeta`)**
<!-- Ahead BTB在每次预测时输出一个AheadBtbMeta结构，用于向训练流水线传递本次预测的完整上下文，避免在训练时重新访问SRAM。该结构包含valid标志、当前访问的set索引、bank掩码以及一个长度为NumWays的AheadBtbMetaEntry向量。每个AheadBtbMetaEntry记录对应way的命中标志（hit）、分支属性（attribute）、分支位置（position）以及目标地址低位（targetLowerBits）。训练时，FTQ会将此meta随分支执行结果一同回传给Ahead BTB，后者据此判断哪些way在预测时命中、命中的表项属性如何，从而执行计数器增减、目标地址修正或新表项分配等更新操作。 -->
For each prediction, Ahead BTB emits an `AheadBtbMeta` structure carrying the complete context into training, avoiding a second SRAM access. It contains a valid flag, the accessed set index, a bank mask, and a `NumWays`-element `AheadBtbMetaEntry` vector. Each entry records the way hit flag, branch attributes, branch position, and low target bits. The FTQ returns this metadata with the branch outcome, allowing Ahead BTB to determine which ways hit, inspect their attributes, and perform counter updates, target corrections, or allocation.

<!-- ### **4.4  I/O 端口** -->
### **4.4 I/O Ports**
<!-- Ahead BTB的顶层接口定义在AheadBtbIO中，该接口继承自BasePredictorIO和HasFastTrainIO。下表列出各端口的方向和功能描述。 -->
The Ahead BTB top-level interface is `AheadBtbIO`, which extends `BasePredictorIO` and `HasFastTrainIO`. The ports and their functions are listed below.

<!-- | 端口名 | 方向 | 位宽（参考/含 bundle） | 功能描述 | -->
| Port | Direction | Width (reference / bundle) | Description |
| :--- | :--- | :--- | :--- |
<!-- | enable | 输入 | 1 | 使能（全局）。与 stageCtrl 一起控制预测时序（来自 BPU top）。 | -->
| `enable` | Input | 1 | Global enable; controls prediction timing with `stageCtrl` from the BPU top level. |
<!-- | stageCtrl | 输入 | StageCtrl bundle | 阶段控制（s0_fire, s1_fire, s2_fire, s3_fire, t0_fire）。用于同步子预测器。 | -->
| `stageCtrl` | Input | `StageCtrl` bundle | Stage control (`s0_fire`, `s1_fire`, `s2_fire`, `s3_fire`, `t0_fire`) for synchronizing subpredictors. |
<!-- | startPc | 输入 | PrunedAddr (VAddrBits) | 预测请求的起始 PC（压缩对齐地址），用于索引/比较/目标重构。 | -->
| `startPc` | Input | `PrunedAddr (VAddrBits)` | Compressed-aligned start PC for indexing, comparison, and target reconstruction. |
<!-- | train | 输入 | BpuTrain bundle | 来自 FTQ / Commit 的训练/提交信息（本模块主要使用 fastTrain，此信号由顶层按需分发）。 | -->
| `train` | Input | `BpuTrain` bundle | Training/commit information from FTQ/commit; this module primarily uses `fastTrain`, distributed by the top level as needed. |
<!-- | fastTrain | 输入（Optional Valid） | Valid[BpuFastTrain] | 快速训练通道（HasFastTrainIO）。用于 s1 predictors 的低延迟训练。 | -->
| `fastTrain` | Input (optional valid) | `Valid[BpuFastTrain]` | Fast-training channel (`HasFastTrainIO`) for low-latency S1 predictor training. |
<!-- | trainReady | 输出 | 1 | 模块是否准备好接受 train（在代码中常量为 true）。 | -->
| `trainReady` | Output | 1 | Whether the module accepts `train` (constant true in the code). |
<!-- | sramResetDone | 输出 | 1 | 各 bank SRAM reset 完成的汇总（AND）。 | -->
| `sramResetDone` | Output | 1 | AND reduction of all bank SRAM reset-done signals. |
<!-- | redirectValid | 输入 | 1 | 后端/顶层发出的 redirect（错误恢复）信号，模块据此 flush pipeline。 | -->
| `redirectValid` | Input | 1 | Backend/top-level redirect for error recovery; flushes the pipeline. |
<!-- | overrideValid | 输入 | 1 | 来自后级 predictor 的覆盖信号；用于用 s3 缓存覆盖 s1 读取结果。 | -->
| `overrideValid` | Input | 1 | Override from a later predictor; replaces the S1 read with the S3 cache. |
<!-- | prediction | 输出 | Vec(NumAheadBtbPredictionEntries, Valid[Prediction]) | 给 Ftq/下游的 prediction 列表。每项含 cfiPosition, target, attribute, taken。 | -->
| `prediction` | Output | `Vec(NumAheadBtbPredictionEntries, Valid[Prediction])` | Prediction list for FTQ/downstream; each item contains `cfiPosition`, `target`, `attribute`, and `taken`. |
<!-- | abtbResult | 输出 | Vec(NumAheadBtbPredictionEntries, Valid[AheadBtbResult]) | 简化的 ABTB 结果（用于 perf / debug / 与 MicroTage 协作），含 isStrongBias。 | -->
| `abtbResult` | Output | `Vec(NumAheadBtbPredictionEntries, Valid[AheadBtbResult])` | Simplified ABTB result for performance/debug/MicroTage cooperation, including `isStrongBias`. |
<!-- | abtbResultPos | 输出 | Vec(..., UInt(CfiPositionWidth.W)) | s1 阶段的 position（供后级并行比较）。 | -->
| `abtbResultPos` | Output | `Vec(..., UInt(CfiPositionWidth.W))` | S1 position for parallel comparison by later stages. |
<!-- | abtbPos | 输出 | Vec(..., UInt(CfiPositionWidth.W)) | s1 阶段直接路由的 position（供 MicroTage 使用，timing 优化）。 | -->
| `abtbPos` | Output | `Vec(..., UInt(CfiPositionWidth.W))` | Directly routed S1 position for MicroTage and timing optimization. |
<!-- | meta | 输出 | AheadBtbMeta | 本次预测的元信息（valid, setIdx, bankMask, entries[]）。 | -->
| `meta` | Output | `AheadBtbMeta` | Metadata for this prediction (`valid`, `setIdx`, `bankMask`, `entries[]`). |
<!-- | debug_startPc | 输出 | PrunedAddr | s2 的 startPc（便于调试/对比）。 | -->
| `debug_startPc` | Output | `PrunedAddr` | S2 `startPc` for debugging and comparison. |




<!-- ## 5. **UBTB ** -->
## 5. **UBTB**
<!-- ### 5.1 **总体结构** -->
### 5.1 **Overall Structure**
<!-- UBTB（Micro Branch Target Buffer）是 BPU 前端预测链路中用于提供低延迟分支目标预测的 BTB 结构。它位于较早的预测阶段，面向能够在小容量 BTB 中快速完成匹配的控制流指令，在取指 PC 到达后给出命中信息、跳转方向和目标地址，在前端预测路径上提供一条延迟更短的 BTB 查询通路。 -->
UBTB (Micro Branch Target Buffer) is a BTB structure in the BPU frontend prediction chain that provides low-latency branch-target prediction. It operates in an early prediction stage for control-flow instructions that can be matched quickly in a small BTB. Once the fetch PC arrives, it supplies hit information, direction, and target address through a shorter-latency BTB lookup path.

<!-- 从硬件组成看，UBTB 主要包含 PC 地址分段逻辑、UBTB Entry 阵列、全相联比较逻辑、命中选择逻辑、预测结果输出逻辑、训练更新逻辑、替换状态以及 reset/context_flush 控制逻辑。PC 地址分段逻辑负责从取指地址中提取 tag 和取指块内偏移，其中Entry 阵列保存 valid、tag、target、type 和 counter 等预测所需状态，比较逻辑根据 valid 与 tag 生成命中结果，输出逻辑再结合 counter 和 target 形成可供前端采用的预测结果。 -->
UBTB consists of PC address-field logic, a UBTB-entry array, fully associative comparison, hit selection, prediction output, training updates, replacement state, and reset/context-flush control. The address logic extracts a tag and intra-fetch-block offset from the fetch address. Entries hold the valid bit, tag, target, type, and counter; comparison generates hits from valid and tag, and output combines the counter and target into a frontend-usable prediction.

<!-- UBTB 的功能主要是预测查询和状态更新展开，预测查询时，模块完成条目匹配，并在命中后返回目标地址和方向信息；训练或更新阶段，模块根据真实分支结果修正 target、type 和 counter，并在未命中且需要分配时通过替换策略写入新条目。reset 或 context_flush 到来时，模块还需要清除会影响后续命中、方向或替换选择的状态，使旧上下文训练出的 BTB 状态不能继续参与新的预测。 -->
UBTB's main functions are prediction lookup and state update. On lookup, it matches entries and returns target and direction on a hit. During training or update, it corrects `target`, `type`, and `counter` from the actual branch result, and uses replacement to write a new entry when a miss requires allocation. On reset or `context_flush`, it clears state that could affect later hits, direction, or replacement choice so BTB state trained in an old context cannot influence new predictions.

<!-- 从流水线组织看，UBTB 的预测路径通常被压缩在单个早期阶段中完成，按 S0 单周期预测路径，PC 输入、tag 比较、命中选择和结果输出在同一预测阶段内完成。 -->
In pipeline terms, the UBTB prediction path is normally compressed into one early stage: under the S0 single-cycle path, PC input, tag comparison, hit selection, and output all complete in the same prediction stage.

<!-- ### 5.2 **工作机制** -->
### 5.2 **Operating Mechanism**
<!-- #### **5.2.1 预测查询过程** -->
#### **5.2.1 Prediction Lookup**
<!-- 预测查询开始时，取指 PC 首先进入地址分段逻辑，硬件从 PC 中提取用于比较的 tag，并保留取指块内偏移等辅助信息。随后，UBTB 阵列中的条目并行读出，比较逻辑对每个条目的 valid 和 tag 进行判断；只有 valid 为真且 tag 与当前查询地址匹配的条目，才可以成为本次预测的候选命中项。 -->
Prediction lookup begins when the fetch PC enters address-field logic, which extracts the comparison tag and retains auxiliary information such as the intra-block offset. UBTB entries are read in parallel; comparison checks every entry's valid bit and tag. Only entries that are valid and whose tag matches the query address can become prediction candidates.

<!-- 如果比较结果中存在命中项，命中选择逻辑会选出一个有效条目，并将其中保存的 target、type 和 counter 送往预测输出逻辑。counter 用于表示分支方向倾向，target 用于给出候选跳转目标，type 用于描述控制流指令类型。若本次查询没有命中，UBTB 不应构造新的预测结果，而应将该取指请求继续交给 MainBTB、ITTAGE、RAS 或其他后级预测结构处理。 -->
If a hit exists, hit selection chooses one valid entry and sends its `target`, `type`, and `counter` to prediction output. The counter expresses direction bias, target supplies the candidate redirect address, and type describes the control-flow instruction. On a miss, UBTB must not construct a new prediction; the fetch request proceeds to MainBTB, ITTAGE, RAS, or another later predictor.

<!-- 预测查询路径不应依赖跨周期的临时状态来决定结果有效性。若同一周期出现 reset、context_flush 或上层要求压制预测输出的控制事件，UBTB 的命中结果和方向结果应被置为无效，避免已经失效的条目经由组合路径继续影响前端取指。 -->
The lookup path must not rely on transient multi-cycle state to decide result validity. If reset, `context_flush`, or upstream suppression occurs in the same cycle, UBTB must invalidate its hit and direction results so invalid entries cannot keep affecting frontend fetch through a combinational path.

<!-- #### **5.2.2 训练与更新过程** -->
#### **5.2.2 Training and Update**
<!-- UBTB 的训练更新由真实分支执行结果或 BPU 顶层整理后的训练信息驱动。训练输入通常包含分支 PC、真实方向、真实目标地址以及分支类型等信息。模块收到有效训练请求后，会使用训练 PC 在现有条目中查找是否存在匹配项，并根据命中结果决定执行更新还是分配。 -->
UBTB training and updates are driven by actual branch outcomes or training information prepared by the BPU top level. Training normally includes branch PC, actual direction, actual target, and branch type. On a valid training request, the module looks for a matching entry with the training PC and chooses update or allocation based on the result.

<!-- 当训练 PC 命中已有条目时，UBTB 会使用真实分支结果修正该条目的 target、type 和 counter。target 的修正用于消除目标地址变化带来的错误预测，type 的修正用于保证 call、return、条件分支等控制流类型与真实执行结果一致，counter 的更新则用于反映分支方向的近期行为。counter 一般采用饱和计数形式，taken 分支会增强跳转倾向，not-taken 分支会削弱跳转倾向。 -->
When the training PC hits an existing entry, UBTB corrects the entry's `target`, `type`, and `counter` from the actual result. Correcting target eliminates errors caused by target changes; correcting type keeps call, return, and conditional-branch classification aligned with execution; counter updates reflect recent direction behavior. Counters normally saturate: taken strengthens the taken bias and not-taken weakens it.

<!-- 当训练 PC 未命中且该分支需要进入 UBTB 时，替换逻辑会选择一个写入位置。若阵列中存在无效条目，分配应优先使用无效项；若所有条目均有效，则由 LRU、FIFO 或实现中采用的替换策略给出 victim 条目。新条目写入时需要设置 valid、tag、target、type 和 counter，并同步更新替换状态，使后续查询能够按照新的条目内容参与预测。 -->
If the training PC misses and the branch should enter UBTB, replacement chooses a write location. Allocation should prefer an invalid entry; if all entries are valid, LRU, FIFO, or the implementation's replacement policy supplies a victim. New entries set `valid`, `tag`, `target`, `type`, and `counter`, and update replacement state so future lookups use them.

<!-- #### **5.2.3 Commit 与状态确认** -->
#### **5.2.3 Commit and State Confirmation**
<!-- Commit 阶段的语义是确认已经能够按照架构顺序退休的分支结果，对于 UBTB 而言， commit 用于强化已经存在的方向状态，使 counter 反映最终确认的分支行为，如果实现中没有单独的 commit 端口，而是由训练信息或 BPU 顶层的最终反馈完成状态确认，则只使用可信的最终分支结果更新方向状态。 -->
Commit confirms branch outcomes that can retire in architectural order. For UBTB, it reinforces established direction state so the counter reflects the final confirmed behavior. If the implementation has no separate commit port and confirmation comes through training or final BPU feedback, direction state must still be updated only from trustworthy final branch outcomes.

<!-- #### **5.2.4 替换与一致性维护** -->
#### **5.2.4 Replacement and Consistency**
<!-- UBTB 容量较小，因此替换策略的重点是低延迟和确定性。常用策略如LRU 策略根据近期访问情况选择较久未使用的条目，FIFO 策略根据写入顺序循环选择条目，二者都属于常见的 BTB 替换实现选择，会影响后续分配行为的微架构状态 -->
Because UBTB is small, replacement prioritizes low latency and determinism. LRU selects entries unused for the longest time based on recent access, while FIFO cycles through entries in write order. Both are common BTB choices and form microarchitectural state that affects later allocation.

<!-- #### **5.2.5 context_flush 行为** -->
#### **5.2.5 `context_flush` Behavior**
<!-- context_flush 用于在上下文切换、地址空间切换、特权级边界或其他安全敏感边界处清理 UBTB 的私有预测状态。对 UBTB 来说，保证 valid 被清零，tag 不再参与旧地址匹配，counter 回到初始弱态，替换状态回到确定初始状态。target 和 type 即使没有物理清零，也必须在 valid 失效后无法被选择为有效预测结果。 -->
`context_flush` clears private UBTB prediction state at a context switch, address-space switch, privilege boundary, or other security-sensitive boundary. It must clear `valid`, prevent old tags from matching, return counters to their initial weak state, and restore deterministic replacement state. Target and type need not be physically cleared, but an invalid entry must never be selected as a valid prediction.

<!-- context_flush 的优先级应高于普通预测查询、训练写入和替换状态更新。若 context_flush 与训练请求同周期出现，旧上下文训练数据不得写入新的预测状态；若 context_flush 与预测查询同周期出现，输出结果应被压制，确保前一上下文中的 BTB 训练结果不能通过命中、方向计数或替换轨迹继续影响后一上下文。 -->
`context_flush` must take priority over normal lookup, training writes, and replacement updates. If it coincides with training, old-context data must not write new prediction state. If it coincides with lookup, output must be suppressed so old-context hits, direction counters, and replacement traces cannot affect the new context.

<!-- ### 5.3 **关键数据结构** -->
### 5.3 **Key Data Structures**
<!-- #### **5.3.1 UBTB Entry** -->
#### **5.3.1 UBTB Entry**
<!-- UBTB Entry 保存一次快速 BTB 预测所需的最小状态集合。其中valid 字段表示该条目是否可以参与查询匹配；tag 字段保存从分支 PC 中提取的地址标签；target 字段保存预测目标地址或目标地址的主要部分；type 字段描述控制流指令类别；counter 字段保存分支方向的饱和计数状态。valid 与 tag 决定旧条目能否继续命中，counter 决定旧上下文训练出的方向倾向是否继续生效，替换状态会影响新上下文的选择。 -->
An UBTB entry stores the minimum state needed for one fast BTB prediction. `valid` controls whether it participates in lookup; `tag` stores the address tag extracted from the branch PC; `target` stores the target address or its main portion; `type` describes the control-flow class; and `counter` stores saturating direction state. `valid` and `tag` determine whether an old entry can hit, `counter` determines whether its old-context direction bias remains active, and replacement state affects new-context selection.

<!-- #### **5.3.2 存储阵列组织** -->
#### **5.3.2 Storage-Array Organization**
<!-- UBTB 阵列通常采用小容量、全相联或近似全相联的组织方式，以换取较短的查询延迟。查询时，多个条目并行读出并同时进行 tag 比较；写入时，训练更新逻辑根据命中结果或替换策略选择一个条目进行更新。 -->
UBTB arrays are usually small and fully or nearly fully associative to reduce lookup latency. Multiple entries are read and tag-compared in parallel; on a write, training logic updates the entry selected by the hit result or replacement policy.

<!-- #### **5.3.3 替换状态** -->
#### **5.3.3 Replacement State**
<!-- 替换状态用于在未命中分配时选择 victim 条目。 LRU替换状态记录条目之间的近期访问关系。在 context_flush 之后，替换状态恢复到确定的初始值，新上下文的分配顺序不受前一上下文访问轨迹影响。验证中覆盖 flush 前多次命中、flush 后首次分配、flush 后连续分配等场景，victim 选择不依赖旧状态。 -->
Replacement state selects the victim on a miss allocation. LRU state records recent relationships among entries. After `context_flush`, it returns to a deterministic initial value so allocation order in a new context is independent of the previous access trace. Verification should cover repeated hits before flush, the first allocation after flush, and consecutive post-flush allocations, confirming that victim selection does not depend on old state.

<!-- #### **5.3.4 PC 地址分段** -->
#### **5.3.4 PC Address Partitioning**
<!-- PC 地址分段规则由具体实现的地址位宽和取指块组织决定， tag 代表参与匹配的地址高位，offset 描述取指块内位置或对齐关系。 -->
PC partitioning follows the implementation's address width and fetch-block organization. `tag` denotes the high address bits used for matching, while `offset` describes the intra-block position or alignment.

<!-- ### **5.4  I/O 端口** -->
### **5.4 I/O Ports**
<!-- #### **5.4.1 输入端口** -->
#### **5.4.1 Inputs**
<!-- UBTB 的预测输入以取指 PC 为核心，PC 与当前前端取指请求保持同周期对应关系。训练输入包括训练有效信号、训练 PC、真实跳转方向、真实目标地址和分支类型。 -->
UBTB prediction input centers on the fetch PC, aligned in the same cycle with the current frontend fetch request. Training inputs include the valid flag, training PC, actual direction, actual target, and branch type.

<!-- 控制输入包括 reset、override 和 context_flush 等信号。reset 用于初始化全部状态，override 用于压制当前错误预测路径上的输出，context_flush 用于清理跨上下文可能残留的预测状态。reset 和 context_flush 优先于普通预测查询和训练写入。 -->
Control inputs include `reset`, `override`, and `context_flush`. Reset initializes all state, override suppresses output on the current wrong path, and context_flush clears state left by a prior context. Reset and context_flush take priority over normal lookup and training writes.

<!-- #### **5.4.2 输出端口** -->
#### **5.4.2 Outputs**
<!-- UBTB 的输出包括 pred_hit、pred_taken、pred_target 以及 ready 信号。pred_hit 表示当前 PC 是否命中有效条目；pred_taken 表示方向计数器给出的跳转倾向；pred_target 表示命中且判定跳转时提供的目标地址；ready 表示模块状态已经完成初始化，可以接受正常预测和训练请求。 -->
UBTB outputs `pred_hit`, `pred_taken`, `pred_target`, and `ready`. `pred_hit` says whether the current PC hit a valid entry; `pred_taken` is the direction bias from the counter; `pred_target` is the target supplied when a hit predicts a jump; and `ready` indicates initialization is complete and normal prediction/training requests are accepted.

<!-- pred_hit 是下游是否采用 UBTB 结果的前提条件，当 pred_hit 为假时，pred_taken 和 pred_target 不应被前端当作有效重定向依据；当 pred_hit 为真但 override 或 context_flush 同周期有效时，输出结果仍应被压制。 -->
`pred_hit` is the prerequisite for downstream adoption. When false, the frontend must not treat `pred_taken` or `pred_target` as a valid redirect basis. Even when true, output must be suppressed if override or context_flush is asserted in the same cycle.

<!-- #### **5.4.3 Bundle 定义** -->
#### **5.4.3 Bundle Definitions**
class UBTBEntry extends Bundle {

val valid   = Bool()

val tag     = UInt(38.W)

val target  = UInt(60.W)

val type    = UInt(3.W)

val counter = UInt(2.W)

}



class UBTBTrainIO extends Bundle {

val valid  = Bool()

val pc     = UInt(64.W)

val taken  = Bool()

val target = UInt(64.W)

val type   = UInt(3.W)

}

## 6. **MainBTB**
<!-- ### 6.1 **总体架构** -->
### 6.1 **Overall Architecture**
<!-- MainBTB 是 BPU 前端预测链路中的主 BTB 结构，负责为较大范围的控制流指令提供目标地址、分支位置、分支属性和方向相关信息。与 UBTB 相比，MainBTB 的容量更大，通常通过组相联、Bank 化 SRAM 和多级流水线组织来平衡容量、功耗和访问延迟。 -->
MainBTB is the primary BTB in the BPU frontend chain. It supplies targets, branch positions, branch attributes, and direction-related information for a larger control-flow working set. Compared with UBTB, it is larger and generally uses set-associative banked SRAM and a multi-stage pipeline to balance capacity, power, and latency.

<!-- MainBTB 的关键部件包括 PC 地址解析逻辑、AlignBank、InternalBank、Entry SRAM、Counter SRAM、命中比较与结果选择逻辑、WriteBuffer、替换器以及 reset/context_flush 控制逻辑。地址解析逻辑根据 startPc 生成 bank 索引、set 索引和 tag；Entry SRAM 保存分支目标、属性和位置等信息；Counter SRAM 保存方向计数状态；WriteBuffer 用于缓解训练写入与预测读取之间的冲突；替换器用于在未命中分配时选择 victim 路。 -->
Its key components include PC parsing, AlignBank, InternalBank, Entry SRAM, Counter SRAM, hit comparison and result selection, WriteBuffer, replacement, and reset/context-flush controls. Address parsing derives bank index, set index, and tag from `startPc`; Entry SRAM stores target, attributes, and position; Counter SRAM stores direction state; WriteBuffer absorbs training/read conflicts; and the replacer selects a victim way on a miss allocation.

<!-- MainBTB 的主要功能是在预测阶段根据 startPc 发起 SRAM 读取，在返回数据后完成 tag 比较、有效性过滤、位置过滤和结果选择，并向 BPU 顶层输出候选预测结果及训练所需的 meta 信息。在训练阶段，MainBTB 根据真实分支结果更新 Entry、Counter 和替换状态，在检测到重复命中或无效状态时还需要执行一致性清理。 -->
During prediction, MainBTB reads SRAM from `startPc`, then performs tag comparison, valid filtering, position filtering, and result selection, returning candidate predictions and training metadata to the BPU top level. During training, it updates entries, counters, and replacement state from actual outcomes and performs consistency cleanup when duplicate hits or invalid states are found.

<!-- 从流水线组织看，MainBTB 通常采用 S0 至 S3 的多级预测流水。S0 阶段解析 startPc 并发起 SRAM 读请求，S1 阶段接收 SRAM 返回数据并锁存中间信息，S2 阶段完成 tag 比较、命中过滤和结果输出，S3 阶段根据最终采用情况维护替换器状态。训练写入路径可以独立划分为 T0/T1 等阶段，以便在不拉长预测关键路径的前提下完成更新。 -->
MainBTB generally uses an S0-S3 prediction pipeline. S0 parses `startPc` and issues SRAM reads; S1 receives and latches SRAM data; S2 performs tag comparison, hit filtering, and result output; and S3 updates replacement state based on final adoption. Training writes may use separate T0/T1 stages so updates do not lengthen the prediction critical path.

<!-- ### 6.2 **工作机制** -->
### 6.2 **Operating Mechanism**
<!-- #### **6.2.1 预测流水线** -->
#### **6.2.1 Prediction Pipeline**
<!-- MainBTB 的预测从 S0 阶段开始。该阶段接收 BPU 顶层给出的 startPc，并由 Helpers 或等价地址解析逻辑生成 AlignBank 索引、InternalBank 索引、setIdx 和 tag。只有在流水线控制信号允许预测推进时，MainBTB 才会向对应的 Entry SRAM 和 Counter SRAM 发起读请求。 -->
Prediction begins in S0, which receives `startPc` from the BPU top level. Helpers or equivalent address logic generates AlignBank and InternalBank indices, `setIdx`, and tag. MainBTB issues Entry SRAM and Counter SRAM reads only when pipeline controls allow advancement.

<!-- S1 阶段主要接收并锁存 SRAM 返回的数据。由于 MainBTB 容量较大，预测信息通常无法像 UBTB 那样在一个组合周期内完成全部访问和比较，因此 S1 需要为后续 S2 命中判断提供稳定的 Entry、Counter、position 和 attribute 等中间数据。若实现需要提前向 TAGE 等预测器提供位置提示，也通常会在这一阶段或相邻阶段完成。 -->
S1 receives and latches SRAM responses. Because MainBTB is larger, access and comparison usually cannot finish in one combinational cycle as in UBTB; S1 therefore provides stable Entry, Counter, `position`, and `attribute` data for S2 hit decisions. Early position hints for TAGE or similar predictors are also normally generated here or in an adjacent stage.

<!-- S2 阶段负责完成预测结果形成。硬件会比较返回条目的 valid 与 tag，过滤不满足取指块位置约束的候选项，并根据 counter、attribute、target 等字段生成 result 和 meta。result 用于驱动前端取指方向选择，meta 则用于后续训练、错误恢复和统计信息维护。 -->
S2 forms the prediction result. Hardware compares valid and tag, filters candidates that violate fetch-block position constraints, and derives `result` and `meta` from `counter`, `attribute`, and `target`. `result` drives frontend next-PC selection, while `meta` supports training, recovery, and statistics.

<!-- S3 阶段更多承担最终反馈和替换器维护职责。当 BPU 顶层形成最终 takenMask 或等价反馈后，MainBTB 只应 touch 最终被采用的命中路，避免早期预测噪声污染替换状态。这样可以使替换器记录更接近真实控制流的访问关系。 -->
S3 mainly handles final feedback and replacement maintenance. Once the BPU produces the final `takenMask` or equivalent feedback, MainBTB should touch only the adopted hit way, preventing early prediction noise from polluting replacement state and keeping the recorded access relation close to real control flow.

<!-- #### **6.2.2 Override 与预测错误处理** -->
#### **6.2.2 Override and Misprediction Handling**
<!-- override 表示较晚阶段已经确认当前预测路径需要被修正。MainBTB 在收到 override 后，应无效化正在流水线中传播的错误路径预测信息，停止继续输出基于错误 PC 的 result 和 meta。override 的作用是截断错误预测路径，它本身不等价于训练写入，也不应直接创建或删除 Entry。 -->
`override` means a later stage has confirmed that the current path must be corrected. MainBTB must invalidate wrong-path information in flight and stop outputting `result` and `meta` based on the wrong PC. Override truncates the bad path; it is not a training write and must not directly create or delete an entry.

<!-- 若 override 与 context_flush 同周期出现，context_flush 的语义应优先，因为它不仅处理一次错误预测，还要求清理跨上下文可能残留的预测状态。若 override 与训练请求同周期出现，训练请求仍需要以真实分支结果和有效上下文为准，不能把被截断路径上的中间预测信息当作可信训练数据写入 SRAM。 -->
When override and `context_flush` coincide, context_flush takes priority because it also clears cross-context state. When override coincides with training, training must still use the real branch result and valid context; intermediate data from the truncated path must not be written to SRAM as trusted training data.

<!-- #### **6.2.3 训练与更新过程** -->
#### **6.2.3 Training and Update**
<!-- MainBTB 的训练更新通常由后端解析完成的真实分支信息驱动。训练信息进入模块后，硬件需要根据训练 PC 查找对应 set 和 way，判断是否命中已有 Entry，并根据命中情况选择更新旧项或分配新项。训练写入会涉及 Entry SRAM、Counter SRAM、WriteBuffer 和替换器，因此必须与预测读取路径做好仲裁。 -->
MainBTB training is normally driven by real branch information resolved in the backend. Hardware uses the training PC to locate a set and way, checks for an existing hit, and chooses update or allocation. Writes touch Entry SRAM, Counter SRAM, WriteBuffer, and replacement state, so they must arbitrate with prediction reads.

<!-- 命中更新时，MainBTB 会根据真实目标、分支属性和方向结果修正已有条目。目标地址变化时，需要更新 targetCarry 和 targetLowerBits 等目标相关字段；分支属性变化时，需要更新 BranchAttribute；方向结果则通过 Counter SRAM 中的饱和计数器反映。未命中分配时，替换器给出 victim 路，新条目写入 valid、tag、position、attribute 和 target 等字段，并初始化 counter。 -->
On a hit, MainBTB corrects the entry from the actual target, branch attributes, and direction. Target changes update `targetCarry`, `targetLowerBits`, and related fields; attribute changes update `BranchAttribute`; and direction is reflected in the Counter SRAM saturating counter. On a miss allocation, the replacer supplies a victim way, a new entry receives `valid`, `tag`, `position`, `attribute`, and `target`, and its counter is initialized.

<!-- WriteBuffer 的作用是处理训练写入与预测读取之间的时序和端口冲突。它可以临时保存待写信息，但也因此成为必须参与 flush 的状态。如果 context_flush 到来时 WriteBuffer 中仍保存旧上下文的写请求，该写请求必须被丢弃或无效化，不能在新上下文开始后落入 SRAM。 -->
WriteBuffer handles timing and port conflicts between training writes and prediction reads. Because it temporarily holds pending writes, it must also participate in flush. If `context_flush` arrives with old-context requests still buffered, those writes must be dropped or invalidated and must not reach SRAM after the new context begins.

<!-- #### **6.2.4 多命中与一致性清理** -->
#### **6.2.4 Multi-Hit and Consistency Cleanup**
<!-- 多命中指同一查询条件下出现多个 Entry 同时有效命中。该情况会使目标地址、分支属性或位置选择产生歧义，因此应作为结构一致性问题处理。MainBTB 可以通过 detectMultiHit 或等价逻辑识别重复项，并在后续写入或清理流程中使多余条目失效。 -->
Multi-hit means that multiple entries are valid hits for one query. It makes target, attribute, and position selection ambiguous and is therefore a structural consistency problem. MainBTB can detect duplicates with `detectMultiHit` or equivalent logic and invalidate redundant entries during later writes or cleanup.

<!-- 多命中清理与 context_flush 的语义不同。多命中清理解决的是同一上下文内的结构一致性问题，而 context_flush 解决的是跨上下文状态残留问题。两者同周期出现时，应优先执行 context_flush，确保所有旧条目均不再参与新上下文预测。 -->
Multi-hit cleanup differs from context_flush: it fixes same-context structural inconsistency, whereas context_flush removes state left across contexts. If both occur together, context_flush takes priority so no old entry participates in the new context.

<!-- #### **6.2.5 context_flush 行为** -->
#### **6.2.5 `context_flush` Behavior**
<!-- MainBTB 的 context_flush 范围比 UBTB 更大，因为它包含 Entry SRAM、Counter SRAM、替换器、WriteBuffer 和多级流水寄存器。只清除 Entry.valid 而保留 Counter 或替换状态，仍可能让旧上下文的方向倾向或 victim 选择轨迹影响新上下文，因此不是完整的安全刷新。 -->
MainBTB's context_flush is broader than UBTB's because it includes Entry SRAM, Counter SRAM, replacement, WriteBuffer, and multi-stage pipeline registers. Clearing only Entry.valid while retaining counters or replacement state could let old direction bias or victim traces affect the new context, so it is not a complete secure refresh.

<!-- 完整的 context_flush 至少应保证 Entry.valid 失效，tag 不再形成旧地址匹配，Counter SRAM 回到确定初始态，替换器回到确定初始状态，WriteBuffer 被清空，S1/S2/S3 中间 result 和 meta 被压制。若目标字段和属性字段没有物理清零，也必须通过 valid 与流水线有效性保证它们无法被新上下文读取为有效预测结果。 -->
A complete context_flush must at least invalidate Entry.valid, prevent old tag matches, reset Counter SRAM and replacement to deterministic initial states, empty WriteBuffer, and suppress intermediate S1/S2/S3 `result` and `meta`. Even if target and attribute fields are not physically cleared, valid bits and pipeline validity must prevent the new context from reading them as valid predictions.

<!-- ### **6.3 关键数据结构** -->
### **6.3 Key Data Structures**
<!-- #### **6.3.1 MainBtbEntry** -->
#### **6.3.1 MainBtbEntry**
<!-- MainBtbEntry 是 MainBTB 保存分支目标和属性信息的核心结构。valid 字段表示条目是否有效，tag 字段用于 set 内匹配，attribute 字段描述控制流指令类型，position 字段记录控制流指令在取指块中的位置，targetCarry 和 targetLowerBits 共同描述预测目标地址。 -->
`MainBtbEntry` is MainBTB's central structure for branch targets and attributes. `valid` indicates entry validity, `tag` matches within the set, `attribute` describes the control-flow type, `position` records the CFI location in the fetch block, and `targetCarry` plus `targetLowerBits` describe the predicted target.

<!-- 这些字段中，valid 和 tag 直接决定旧条目能否继续命中，因此在 context_flush 后必须失效或被屏蔽。target、attribute 和 position 虽然不都属于最小物理清零集合，但只要 valid 已经失效，它们就不能继续进入 result 选择。 -->
`valid` and `tag` directly control whether an old entry can hit, so they must be invalidated or masked after context_flush. Target, attribute, and position need not all be physically cleared, but once valid is false they cannot enter `result` selection.

<!-- #### **6.3.2 MainBtbMetaEntry** -->
#### **6.3.2 MainBtbMetaEntry**
<!-- MainBtbMetaEntry 用于在预测与训练之间传递信息，rawHit 表示未经过最终过滤的原始命中状态，position 用于定位取指块内的控制流指令，attribute 用于恢复分支类型，counter 用于训练阶段判断方向状态如何更新。 -->
`MainBtbMetaEntry` carries information between prediction and training. `rawHit` is the pre-filter hit state, `position` locates the control-flow instruction in the block, `attribute` restores branch type, and `counter` tells training how to update direction state.

<!-- meta 信息必须与 result 的有效性保持一致。如果某一级流水线因为 override、flush 或 context_flush 被无效化，对应的 meta 也应同步无效，不能被后续训练路径误认为真实预测上下文。 -->
Metadata validity must track `result` validity. If a pipeline stage is invalidated by override, flush, or context_flush, its metadata must be invalidated too and must not be mistaken for real prediction context by later training.

<!-- #### **6.3.3 Bank 与 SRAM 组织** -->
#### **6.3.3 Bank and SRAM Organization**
<!-- MainBTB 采用 Bank 化组织是为了在较大容量下控制访问功耗和时序压力。AlignBank 用于处理取指块对齐关系，InternalBank 用于进一步划分物理 SRAM 访问范围，setIdx 和 way 则共同决定一次查询需要比较的候选条目集合。 -->
MainBTB is banked to control access power and timing at larger capacity. AlignBank handles fetch-block alignment, InternalBank further partitions physical SRAM access, and `setIdx` plus way identify the candidate entries to compare for a lookup.

<!-- Entry SRAM 和 Counter SRAM 分离可以降低更新开销，目标地址、标签、属性和位置并不会在每次方向训练中都发生变化，而 counter 的更新频率更高，将二者拆开可以减少不必要的宽写入，要求 context_flush 同时覆盖 Entry SRAM、Counter SRAM 和写缓冲路径，不能只观察 Entry.valid。 -->
Separating Entry SRAM from Counter SRAM reduces update cost: targets, tags, attributes, and positions do not change on every direction training operation, while counters change more often. The split avoids unnecessary wide writes, but requires context_flush to cover Entry SRAM, Counter SRAM, and the write-buffer path rather than observing Entry.valid alone.

<!-- #### **6.3.4 替换器状态** -->
#### **6.3.4 Replacer State**
<!-- MainBTB 的替换器负责在未命中分配时选择 victim 路，并在命中或最终 被确认后更新访问状态。常见实现可以采用 LRU 或 PLRU，具体选择取决于面积、时序和命中率要求。 -->
The MainBTB replacer selects a victim way on a miss allocation and updates access state after a hit or final confirmation. Common implementations use LRU or PLRU depending on area, timing, and hit-rate requirements.

<!-- 替换器状态属于微架构状态，会影响后续 victim 选择，因此在 context_flush 后必须恢复到确定初始状态。验证时需要覆盖 flush 前后连续未命中分配、flush 前命中 touch、flush 后 victim 序列等场景，以确认新上下文不继承旧访问轨迹。 -->
Replacer state is microarchitectural state that affects later victim choice, so it must return to a deterministic initial state after context_flush. Verification should cover consecutive misses before and after flush, pre-flush hit touches, and post-flush victim sequences to prove that a new context does not inherit the old access trace.

<!-- #### **6.3.5 PC 地址分段** -->
#### **6.3.5 PC Address Partitioning**
<!-- MainBTB 的 PC 地址分段由 Helpers 或等价逻辑完成。实现通常从 startPc 中提取取指块偏移、AlignBank 索引、InternalBank 索引、setIdx 和 tag。Bank 与 set 索引用于定位 SRAM 访问范围，tag 用于候选条目比较，offset 或 position 信息用于过滤取指块内不应被采用的控制流指令。 -->
MainBTB PC partitioning is performed by Helpers or equivalent logic. It normally derives fetch-block offset, AlignBank index, InternalBank index, `setIdx`, and tag from `startPc`. Bank and set locate SRAM access, tag compares candidates, and offset/position filters CFIs that must not be selected in the fetch block.

<!-- ### **6.4 I/O 端口** -->
### **6.4 I/O Ports**
<!-- #### **6.4.1 输入端口** -->
#### **6.4.1 Inputs**
<!-- MainBTB 的预测输入以 startPc 和 stageCtrl 为核心。startPc 提供本次取指块的起始地址，stageCtrl 提供 S0 至 S3 各阶段的 valid、fire、stall、flush 等控制信息。只有当对应阶段允许推进时，MainBTB 才应发起 SRAM 读请求或传递中间结果。 -->
MainBTB prediction inputs center on `startPc` and `stageCtrl`. `startPc` supplies the fetch-block starting address, while `stageCtrl` provides S0-S3 valid, fire, stall, and flush control. MainBTB may issue SRAM reads or propagate intermediate results only when the relevant stage is allowed to advance.

<!-- 训练输入通常由 train Bundle 承载，包含真实方向、目标地址、分支属性和误预测相关信息。s3_takenMask 或等价最终反馈用于指示哪些预测路最终被采用，从而使替换器只 touch 真实有效的访问路径。override、context_flush 和 reset 属于控制类输入，它们的优先级应高于普通预测和训练写入。 -->
Training usually arrives in the `train` bundle with actual direction, target, branch attributes, and misprediction information. `s3_takenMask` or equivalent final feedback indicates which predicted way was ultimately adopted so the replacer touches only real accesses. Override, context_flush, and reset are control inputs and must take priority over normal prediction and training writes.

<!-- #### **6.4.2 输出端口** -->
#### **6.4.2 Outputs**
<!-- MainBTB 的 result 输出包含预测方向、目标地址、分支位置和分支类型等信息，供 IFU 或 BPU 顶层选择下一取指地址。meta 输出包含 rawHit、position、attribute 和 counter 等训练所需信息，应与 result 的有效性同步。 -->
MainBTB `result` outputs direction, target, branch position, and branch type for the IFU or BPU top level to select the next fetch address. `meta` carries training fields such as `rawHit`, `position`, `attribute`, and `counter`, and must remain valid in lockstep with `result`.

<!-- s1_positions 用于提前向后级预测器提供控制流指令位置信息，resetDone 表示 SRAM 和内部状态初始化完成，trainReady 表示模块可以接受训练请求。若 reset、context_flush 或写缓冲清理尚未完成，这些状态信号应准确反映模块是否可以恢复正常预测和训练。 -->
`s1_positions` provides early CFI positions to later predictors, `resetDone` means SRAM and internal state initialization are complete, and `trainReady` means the module can accept training. If reset, context_flush, or write-buffer cleanup is still in progress, these status signals must accurately show whether normal prediction and training can resume.

<!-- #### **6.4.3 Bundle 定义** -->
#### **6.4.3 Bundle Definitions**
class MainBtbEntry extends MainBtbBundle {

val valid           = Bool()

val tag             = UInt(TagWidth.W)

val attribute       = new BranchAttribute()

val position        = UInt(CfiAlignedPositionWidth.W)

val targetCarry     = new TargetCarry()

val targetLowerBits = UInt(TargetWidth.W)

}



class MainBtbMetaEntry extends MainBtbBundle {

val rawHit    = Bool()

val position  = UInt(CfiPositionWidth.W)

val attribute = new BranchAttribute()

val counter   = TakenCounter()

}

## 7. **TAGE**
<!-- ### **7.1 总体架构** -->
### **7.1 Overall Architecture**
<!-- TAGE（Tagged Geometric-history-length predictor）前端 BPU 中面向条件分支方向预测的核心子预测器。它对每个条件分支给出 taken / not taken 方向预测，并将预测时的上下文快照打包为 TageMeta，写入 FTQ 以供后续训练。 -->
TAGE (Tagged Geometric-history-length predictor) is the core conditional-branch direction subpredictor in the frontend BPU. It produces a taken/not-taken prediction for each conditional branch and packages the prediction-time context as `TageMeta` for the FTQ to use during later training.

<!-- 从顶层连线看，TAGE 位于 MBTB 与 SC 之间：上游依赖 MBTB 给出的 Prediction、依赖 PHR 提供折叠路径历史；下游向 SC 输出 Provider 的 taken 计数器快照。 -->
At top level, TAGE sits between MBTB and SC: upstream it consumes MBTB `Prediction` results and folded path history from PHR; downstream it sends SC a snapshot of the provider taken counter.

<!-- TAGE 在 Kunminghu-v3 中有以下几个特性： -->
TAGE has the following Kunminghu-v3 characteristics:

<!-- • 使用8 张具有不同历史长度的标记表，历史长度按 4、9、17、29、56、109、211、397 的几何序列递增，覆盖从短期到长程的相关性。 -->
* It uses eight tagged tables with geometric history lengths of 4, 9, 17, 29, 56, 109, 211, and 397, covering short- through long-range correlation.

<!-- • 每张表均采用2 路组相联、4 Bank、4096 表项的组织形式，通过 setIdx + tag 实现带历史特征的匹配。 -->
* Each table is two-way set associative with four banks and 4096 entries, using `setIdx + tag` to match history-dependent state.

<!-- • 对同一分支同时寻找最长历史命中表（Provider）和次长历史命中表（Alternate），并结合 useAltOnNaVec 与 Provider 强弱态决定最终是否采用 Provider。 -->
* For a branch, it finds the longest-history hit (provider) and next-longest hit (alternate), then uses `useAltOnNaVec` and provider strength to choose whether to retain the provider.

<!-- • 预测流水线为S0-S1-S2，训练流水线为 T0-T1-T2，两条流水线共享各表的单端口 SRAM，通过 Bank 冲突检测与 trainReady 完成仲裁。 -->
* Prediction uses S0-S1-S2 and training uses T0-T1-T2. They share single-port SRAM in each table and arbitrate via bank-conflict detection and `trainReady`.

<!-- • 每张表内部带有WriteBuffer，训练写回不会直接与前台读请求硬碰撞，从而降低单端口 SRAM 对前端时序的压力。 -->
* Each table has a WriteBuffer so training write-back does not directly collide with frontend reads, reducing single-port SRAM pressure on frontend timing.

<!-- ### **7.2 工作机制** -->
### **7.2 Operating Mechanism**
<!-- #### **7.2.1 预测流水线** -->
#### **7.2.1 Prediction Pipeline**
<!-- 预测流水线（Predict Pipeline）被设计为三个同步时钟周期（S0、S1、S2） -->
The prediction pipeline uses three synchronous cycles (S0, S1, and S2).

<!-- ，流水线的第一阶段（S0）主要执行 SRAM 读请求的提前发射与粗粒度索引计算。S0 阶段并未等待确切的分支指令程序计数器（PC），而是直接采用当前取指块的起始地址（s0_startPc）进行映射计算。在此同时，硬件利用循环移位寄存器（CSR）维护的全局路径历史（PHR）向 8 个独立的 TAGE 历史表进行分发。每个表依据自身的配置参数，提取出与目标 SRAM 组（Set）深度相匹配的折叠历史片段（forIdx）。该纯组合逻辑生成的短片段随后与 s0_startPc 中的 SetIdxWidth 位段执行按位异或运算（XOR）以生成散列索引。该 12 位左右的 Index 与根据 PC 生成的 Bank 掩码拼接后，作为地址总线数据向后端 SRAM 控制器并行发起异步读请求。 -->
S0 issues SRAM reads early and computes coarse indices. Instead of waiting for an exact branch PC, it maps the fetch-block start address (`s0_startPc`). The PHR-maintained global path history is distributed to eight independent TAGE tables. Each table extracts a folded history slice (`forIdx`) sized for its SRAM set depth, XORs it with the `SetIdxWidth` field in `s0_startPc`, and combines the resulting roughly 12-bit index with a PC-derived bank mask to issue parallel SRAM reads.

<!-- 第二阶段S1，流水线并行执行精确哈希签（Tag）的计算逻辑。此阶段中，主分支目标缓冲计算并传递了该取指块内各有效分支指令的槽位偏移量（s1_positions）。S1 阶段依据此偏移量与上级流水段透传的 s1_startPc 计算出精确的分支指令地址（cfiPc）。由于 S0 阶段采用了基于取指块粒度的非精确索引映射，TAGE 发生冲突的风险显著提升。为解决此问题，此阶段会进一步利用 cfiPc 以及第二组独立的折叠历史特征片段（forTag）组合计算，为每个分支在所有 8 个 TAGE 表生成 13 位的独立 Tag 校验值（s1_tag）。当该阶段末尾，SRAM 操作完成并返回包含预测置信计数器（TakenCtr）、替换评估位（UsefulCtr）以及寄存 Tag 的实体表项数据（s1_readResp）时，所有校验所需的数据源即准备就绪，随后数据一并流入下级流水段进行决裁。 -->
In S1, the pipeline computes precise hash tags in parallel. MainBTB provides positions of valid branch instructions in the fetch block (`s1_positions`); together with forwarded `s1_startPc`, these calculate the exact branch PC (`cfiPc`). Because S0 indexed at fetch-block granularity, this extra check reduces aliasing. `cfiPc` and a separate folded-history slice (`forTag`) generate a distinct 13-bit `s1_tag` for every branch in all eight tables. At the end of S1, SRAM returns `s1_readResp` entries containing `TakenCtr`, `UsefulCtr`, and stored tags, providing all data needed for S2 arbitration.

<!-- 最终阶段S2负责执行基于Tag匹配的优先级裁决及降级选择逻辑。首先，S1 阶段计算得出的理论 Tag 会与并行从 8 个关联表读出的实际数据（s2_readResp）中含有的硬件 Tag 字段执行按位比对（entry.tag === tag）。对于各个完成匹配（Hit）的表项，，针对 TAGE 算法依赖历史长度定权的特点，在所有命中表中筛选出配置有最长全局历史深度的子表，将其对应表项确立为“主提供者（Provider）”，并提取其预测方向（providerPred）作为最优候选输出。 -->
S2 performs tag-match priority arbitration and fallback selection. It compares the tags calculated in S1 with stored tags in the eight-table `s2_readResp` entries (`entry.tag === tag`). Among hits, it selects the table with the longest configured global-history depth as the provider and uses `providerPred` as the preferred direction.

<!-- 在确立Provider 后，S2 阶段进一步引入了预测降级补偿机制（UseAltOnNa 机制）。硬件会在剩余命中表中继续通过优先选择逻辑锁定历史长度次之的有效表项，定义为“后备提供者（Alt）”。当 Provider 表项内的方向饱和计数器（TakenCtr）处于低置信度的弱倾向状态（如值域中间态 3 或 4），且全局硬件监控器（由 useAltOnNa 计数器驱动）指示当前长期历史表普遍因刚分配不久而缺乏置信度积累时，仲裁器将强制否决 Provider（置低 useProvider 位信号），主动降级采用该较短特征但状态更稳定的 Alt 的预测结果（altPred），或者转用基础预测器的输出值。最终，确立无误的分支方向及辅助元数据被整体打包，向后续预测组件（如统计纠正器 SC）推送，并将比对决择信息通过旁路录入重排序缓冲（ROB）中，留待流水线段后进行 TAGE 置信度状态机的精确训练更新。 -->
After choosing a provider, S2 applies the UseAltOnNa fallback mechanism. It selects the next-longest hit as alternate (Alt). When the provider `TakenCtr` is weak (such as 3 or 4) and the `useAltOnNa` monitor indicates that newly allocated long-history tables lack accumulated confidence, arbitration deasserts `useProvider` and uses the more stable `altPred` or the base predictor. The chosen direction and metadata are packed for later predictors such as SC and retained for accurate TAGE confidence-state training.

<!-- #### **7.2.2 训练流水线** -->
#### **7.2.2 Training Pipeline**
<!-- 训练与更新流水线（Train Pipeline）同样被划分为三个同步时钟周期（T0、T1、T2），主要负责在分支指令提交后对其历史状态进行闭环修正，并在误预测时执行动态的条目分配（Allocation）与替换（Replacement）策略。在 T0 与 T1 阶段，结合实际跳转结果（Actual Taken），通过两条并行的状态恢复路径获取更新基准：当主预测器（Provider）预测正确时，通过旁路直接利用分支携带的元数据（Meta）执行轻量更新；而当发生误预测或弱置信度降级时，则利用回滚的训练折叠历史（foldedPathHistForTrain）向 SRAM 发起重新读请求（trainReadReq），获取全量状态。 -->
The training/update pipeline also has three synchronous cycles (T0, T1, T2). After branch commit, it closes the feedback loop on history state and dynamically allocates or replaces entries on a misprediction. In T0/T1, actual direction uses two recovery paths: a correct provider can update lightly from bypassed branch metadata, while a misprediction or weak-confidence fallback uses restored `foldedPathHistForTrain` to issue `trainReadReq` and obtain full SRAM state.

<!-- 进入T2 阶段后，数据被正式修改：提供过预测的主表和备用表会根据真实的跳转方向，直接增加或减少其方向计数器（TakenCtr）；而专门用来评估该表项是否有保留价值的“计数器（u-bit）”，其增加条件极其严格——只有当该表项做出了正确的预测，并且准确度高于其他底层的备用预测器时，它才能加 1。当系统确认预测错误是因为现有的短历史表无法分辨当前分支时，就会触发分配（Allocation）机制：硬件会在历史长度更长的 TAGE 表中寻找位置存放新特征。在寻找替换目标时，它会优先覆盖那些完全为空（Valid=0）或者长期毫无贡献（有用度极低）的陈旧表项。此外，为了防止早期的“僵尸分支”永远霸占有限的 SRAM 资源，系统还引入了定期“老化（Aging）”机制：每隔一定周期，或者当新分支实在找不到空位分配时，硬件会强制把整个预测器里所有表项的有用度统一扣减一次，以此加速淘汰之前的数据。所有这些计算好的更新数据，最终会被打包送入写缓冲队列（Write Buffer），排队写入真实的 SRAM 中完成物理覆盖。 -->
In T2, state is actually modified. Provider and alternate tables increment or decrement their `TakenCtr` from actual direction. The useful counter increments only when an entry predicts correctly and outperforms the lower-level fallback. If a short-history table cannot distinguish the branch, allocation searches longer-history tables, preferring invalid or very-low-useful entries. Periodic ageing, or allocation failure, decrements useful state globally to prevent stale entries from monopolizing SRAM. Computed writes enter the WriteBuffer and are queued for physical SRAM update.

<!-- ### **7.3 关键数据结构** -->
### **7.3 Key Data Structures**
<!-- #### **7.3.1 Tage 表配置** -->
#### **7.3.1 Tage Table Configuration**
<!-- TAGE 的 SRAM 表项由TageEntry定义： -->
TAGE SRAM entries are defined by `TageEntry`:

<!-- | **表索引** | **条目数** | | **路数** | **Bank数** | **历史长度** | **Set数（每Bank）** | **总Set数（所有Bank）** | -->
| **Table index** | **Entries** | | **Ways** | **Banks** | **History length** | **Sets per bank** | **Total sets across banks** |
| :---: | :---: | --- | :---: | :---: | :---: | :---: | :---: |
| Table 0 | 4096 | 2 | | 4 | 4 | 512 | 2048 |
| Table 1 | 4096 | 2 | | 4 | 9 | 512 | 2048 |
| Table 2 | 4096 | 2 | | 4 | 17 | 512 | 2048 |
| Table 3 | 4096 | 2 | | 4 | 29 | 512 | 2048 |
| Table 4 | 4096 | 2 | | 4 | 56 | 512 | 2048 |
| Table 5 | 4096 | 2 | | 4 | 109 | 512 | 2048 |
| Table 6 | 4096 | 2 | | 4 | 211 | 512 | 2048 |
| Table 7 | 4096 | 2 | | 4 | 397 | 512 | 2048 |




<!-- #### **7.3.2 TageEntry** -->
#### **7.3.2 TageEntry**
<!-- TAGE 的 SRAM 表项由TageEntry定义： -->
TAGE SRAM entries are defined by `TageEntry`:

<!-- | **字段** | **位宽** | **作用** | -->
| **Field** | **Width** | **Purpose** |
| --- | --- | --- |
<!-- | valid | 1 bit | 表项是否有效 | -->
| `valid` | 1 bit | Entry validity. |
<!-- | tag | 13 bits | 与 PC 和历史共同生成的标记 | -->
| `tag` | 13 bits | Tag generated from PC and history. |
<!-- | takenCtr | 3 bits | 方向饱和计数器 | -->
| `takenCtr` | 3 bits | Direction saturating counter. |




<!-- 单个TageEntry总位宽为 1 + 13 + 3 = 17 bits。3-bit takenCtr 的判定方式如下： -->
One `TageEntry` is `1 + 13 + 3 = 17` bits. The 3-bit `takenCtr` is interpreted as follows:

<!-- | **计数器值** | **方向** | **置信度类别** | -->
| **Counter value** | **Direction** | **Confidence class** |
| --- | --- | --- |
<!-- | 0、7 | 分别代表强 Not Taken / 强 Taken | Saturate | -->
| 0, 7 | Strong Not Taken, Strong Taken, respectively | Saturate |
<!-- | 1、2、5、6 | 中等置信度 | Mid | -->
| 1, 2, 5, 6 | Medium confidence | Mid |
<!-- | 3、4 | 最弱状态 | Weak | -->
| 3, 4 | Weakest state | Weak |




<!-- 其中value>=4表示Taken，value < 4表示Not Taken。TAGE中与useAltOnNaVec联动的“弱态”正是 3/4 这两个边界值。 -->
`value >= 4` means Taken and `value < 4` means Not Taken. The weak states used with `useAltOnNaVec` are exactly the boundary values 3 and 4.

<!-- #### **7.3.3 TageMeta** -->
#### **7.3.3 TageMeta**
<!-- TAGE 在预测阶段输出 TageMeta，训练时再从 FTQ 取回。每个分支对应一个 TageMetaEntry，关键字段如下： -->
TAGE outputs `TageMeta` during prediction and retrieves it from the FTQ during training. Every branch has a `TageMetaEntry` with these key fields:

<!-- | **字段** | **位宽** | **作用** | -->
| **Field** | **Width** | **Purpose** |
| --- | --- | --- |
<!-- | useProvider | 1 | 预测阶段是否采用了 Provider | -->
| `useProvider` | 1 | Whether prediction used the provider. |
<!-- | providerTableIdx | 3 | Provider 所在表号 | -->
| `providerTableIdx` | 3 | Provider table index. |
<!-- | providerWayIdx | 2 | Provider 命中的 Way 索引，声明宽度按 MaxNumWays.W 展开 | -->
| `providerWayIdx` | 2 | Hit-way index of the provider, declared at `MaxNumWays.W`. |
<!-- | providerTakenCtr | 3 | Provider 的方向计数器快照 | -->
| `providerTakenCtr` | 3 | Snapshot of provider direction counter. |
<!-- | providerUsefulCtr | 2 | Provider 的 Useful 快照 | -->
| `providerUsefulCtr` | 2 | Snapshot of provider useful counter. |
<!-- | altOrBasePred | 1 | 当时 Alternate 或 base 路径给出的方向 | -->
| `altOrBasePred` | 1 | Direction supplied by the alternate or base path. |




<!-- 单个 TageMetaEntry 的可见有效信息总计约 12 bit，8 个分支槽位一起组成完整 TageMeta。 -->
Each `TageMetaEntry` has roughly 12 bits of visible useful information; eight branch slots form a complete `TageMeta`.

<!-- ### **7.4  I/O 端口** -->
### **7.4 I/O Ports**
<!-- | **接口名称** | **方向** | **类型** | **位宽/说明** | **功能描述** | -->
| **Interface** | **Direction** | **Type** | **Width / notes** | **Description** |
| --- | --- | --- | --- | --- |
<!-- | enable | **Input** | Bool | 1 bit | TAGE模块使能信号（受BPU顶层CSR控制） | -->
| `enable` | **Input** | Bool | 1 bit | TAGE enable, controlled by the BPU top-level CSR. |
<!-- | stageCtrl | **Input** | StageCtrl | 5个Bool | 流水线各阶段fire信号（s0/s1/s2/s3/t0_fire） | -->
| `stageCtrl` | **Input** | StageCtrl | 5 Bool | Stage fire signals (`s0/s1/s2/s3/t0_fire`). |
<!-- | startPc | **Input** | PrunedAddr | VAddrBits (50 bits) | 预测请求的取指块起始PC地址 | -->
| `startPc` | **Input** | PrunedAddr | VAddrBits (50 bits) | Fetch-block start PC for the prediction request. |
<!-- | train | **Input** | BpuTrain | - | 来自FTQ的训练数据包 | -->
| `train` | **Input** | BpuTrain | - | Training bundle from the FTQ. |
<!-- | fromPhr.foldedPathHist | **Input** | PhrAllFoldedHistories | - | S0阶段用的折叠路径历史 | -->
| `fromPhr.foldedPathHist` | **Input** | PhrAllFoldedHistories | - | Folded path history for S0. |
<!-- | fromPhr.foldedPathHistForTrain | **Input** | PhrAllFoldedHistories | - | 训练用的折叠路径历史 | -->
| `fromPhr.foldedPathHistForTrain` | **Input** | PhrAllFoldedHistories | - | Folded path history for training. |
<!-- | fromMainBtb.result | **Input** | Vec[Valid[Prediction]] | 8个条目 | MBTB的分支预测结果 | -->
| `fromMainBtb.result` | **Input** | Vec[Valid[Prediction]] | 8 entries | MBTB branch predictions. |
<!-- | fromMainBtb.s1_positions | **Input** | Vec[UInt] | 8 × 5 bits | S1阶段各分支的cfiPosition | -->
| `fromMainBtb.s1_positions` | **Input** | Vec[UInt] | 8 x 5 bits | CFI positions for S1 branches. |
<!-- | debug_trainValid | **Input** | Bool | 1 bit | 用于性能计数器的io.train.valid镜像 | -->
| `debug_trainValid` | **Input** | Bool | 1 bit | Mirror of `io.train.valid` for performance counters. |
<!-- | trainReady | **Output** | Bool | 1 bit | Bank冲突时置低，反压FTQ | -->
| `trainReady` | **Output** | Bool | 1 bit | Low on bank conflict to backpressure the FTQ. |
<!-- | resetDone | **Output** | Bool | 1 bit | 所有SRAM复位完成 | -->
| `resetDone` | **Output** | Bool | 1 bit | All SRAM resets complete. |
<!-- | toSc.providerTakenCtrVec | **Output** | Vec[Valid[SaturateCounter]] | 8 × (1+3) bits | 向SC传递的Provider计数器快照 | -->
| `toSc.providerTakenCtrVec` | **Output** | Vec[Valid[SaturateCounter]] | 8 x (1+3) bits | Provider-counter snapshots for SC. |
<!-- | prediction | **Output** | Vec[TagePrediction] | 8 × 4 bits | 条件分支的TAGE预测结果 | -->
| `prediction` | **Output** | Vec[TagePrediction] | 8 x 4 bits | TAGE prediction for conditional branches. |
<!-- | meta | **Output** | TageMeta | 8 × 12 = 96 bits | 元数据，随预测结果进入FTQ供训练使用 | -->
| `meta` | **Output** | TageMeta | 8 x 12 = 96 bits | Metadata sent with predictions to the FTQ for training. |




## 8. **SC**
<!-- ### **8.1 总体架构** -->
### **8.1 Overall Architecture**
<!-- SC（Statistical Corrector）位于 Kunminghu-v3 BPU 的 TAGE 之后，是面向条件分支方向的统计校正器。它不负责独立发现目标地址，也不试图替代 MBTB 和 TAGE 完成全部方向预测，而是利用路径历史、全局历史、反向历史、循环迭代信息和偏置项等多种特征，对 TAGE 的方向结果做二次判定，并在条件满足时覆盖 TAGE 的判断。 -->
SC (Statistical Corrector) follows TAGE in the Kunminghu-v3 BPU and statistically corrects conditional-branch direction. It does not discover targets independently or replace MBTB/TAGE; instead it uses path history, global history, backward history, loop-iteration information, and bias features to re-evaluate TAGE and override it when conditions permit.

<!-- 从顶层行为上看，SC 先基于多张子表得到一个统计意义上的加权和 totalPercsum，再结合 TAGE Provider 的强弱态，将这个加权和与自适应阈值比较，决定是否启用 SC 覆盖。因此 SC 不是简单的“再来一张表”，而是一个具有感知机风格的后级校正网络。它的设计重点不在 Tag 匹配，而在特征拆分、权重求和和阈值训练。 -->
At top level, SC obtains a statistical weighted sum `totalPercsum` from multiple subtables, combines it with TAGE provider strength, and compares the sum with an adaptive threshold to decide whether to override TAGE. SC is therefore not merely another table but a perceptron-like late correction network focused on feature decomposition, weighted summation, and threshold training rather than tag matching.

<!-- Kunminghu-v3 中 SC 的主要特征如下： -->
SC has the following Kunminghu-v3 features:

<!-- · 子表来源多样，包括 Path、Global、BW、Imli、Bias 五类特征。 -->
* Subtables draw on five feature classes: Path, Global, BW, Imli, and Bias.

<!-- · 各子表表项均为带符号饱和计数器，计数器符号直接表示 Taken / Not Taken 倾向，幅值反映强弱。 -->
* Every subtable entry is a signed saturating counter; its sign gives Taken/Not Taken bias and its magnitude gives strength.

<!-- · 预测阶段先将所有非 Bias 表的计数器映射为 percsum = 2 * ctr + 1，按 Way 累加；随后在 S2 再叠加 Bias 项，形成每个分支的最终 totalPercsum。 -->
* Prediction maps every non-Bias counter to `percsum = 2 * ctr + 1` and accumulates by way; S2 adds the Bias term to form each branch's final `totalPercsum`.

<!-- · SC 是否生效不是固定的，而是依赖 scThreshold 与 TAGE Provider 的置信度共同决定。Provider 越强，SC 越难介入；Provider 越弱，SC 越容易触发。 -->
* SC activation depends on `scThreshold` and TAGE provider confidence: the stronger the provider, the harder it is for SC to intervene.

<!-- ### **8.2 工作机制** -->
### **8.2 Operating Mechanism**
<!-- #### **8.2.1 预测流水线** -->
#### **8.2.1 Prediction Pipeline**
<!-- SC 的预测流水线被组织为三个同步时钟周期（S0、S1、S2）。S0 阶段负责在取指块粒度上并行计算所有启用子表的访问索引与 Bank 选择：Path 表使用 PHR 提供的折叠路径历史，Global 表与 BW 表分别使用 CommonHR 中的 ghr 与 bw 片段，Imli 表使用循环迭代计数，Bias 表则仅依赖当前取指块起始地址的高位切片。所有子表统一使用 pc[4] 对应的 Bank 进行访问，从而保持前台读路径的时序规整性。 -->
SC prediction uses three synchronous cycles (S0, S1, S2). S0 computes indices and bank selection for enabled subtables at fetch-block granularity: Path uses folded PHR history, Global and BW use CommonHR `ghr` and `bw`, Imli uses the loop-iteration counter, and Bias uses high bits of the block start address. All subtables use the bank selected by `pc[4]`, keeping the frontend read path regular.

<!-- 进入S1 阶段后，各个 ScTable 返回的旧表项被解释为带符号计数器响应。硬件不会直接把这些响应视为最终预测，而是将每个 ctr 映射为奇数权重 percsum = 2 × ctr + 1。随后，Path、Global、BW 与 Imli 四类非 Bias 特征会按 Way 并行求和，形成 sumPercsum 向量；Bias 表的 32 路响应则被完整保留，等待下一阶段依据具体分支的扩展 Way 索引挑选。这样的组织方式使同一 fetch block 内的多个候选分支能够复用一次统计求和结果，而不必逐条分支重复遍历全部特征表。 -->
In S1, ScTable responses are interpreted as signed counters. Each `ctr` maps to the odd weight `percsum = 2 * ctr + 1`. Path, Global, BW, and Imli (the non-Bias features) are summed by way into `sumPercsum`; all 32 Bias responses are retained for S2 to select with an extended way index. Multiple candidate branches in one fetch block can reuse one sum instead of traversing every feature table repeatedly.

<!-- S2 阶段是 SC 决策真正落地的地方。首先，MBTB 给出的 cfiPosition 会被折叠成普通 wayIdx，用于从 sumPercsum 中取出该分支所在逻辑槽位的基础求和结果；随后，SC 再结合 TAGE Provider 的 valid、弱态信息以及 Provider 的方向位生成 biasWayIdx，从 Bias 表的 32 路响应中挑出对应偏置项，与基础求和叠加形成 totalPercsum。若 totalPercsum 大于等于 0，则 SC 本身倾向于给出 Taken；否则倾向于给出 Not Taken。 -->
S2 commits the SC decision. MBTB `cfiPosition` is folded into `wayIdx` to select the branch's base sum from `sumPercsum`. SC combines TAGE provider validity, weak-state information, and provider direction to form `biasWayIdx`, selects the corresponding Bias entry, and adds it to produce `totalPercsum`. A nonnegative sum favors Taken; a negative sum favors Not Taken.

<!-- 但SC 并不会只要得出方向就立刻覆盖 TAGE。硬件还会依据 Provider 的置信度，对每个 Way 的阈值寄存器 scThreshold 做不同强度的缩放：当 Provider 处于饱和强态时，只有 totalPercsum 绝对值明显高于阈值时才允许 SC 介入；当 Provider 处于中间态或弱态时，触发门槛会相应降低。最终，只有当前槽位为条件分支、Provider 有效且 totalPercsum 的绝对值超过门槛时，scUsed 才会被置位；真正的 override 行为则由 BPU 顶层在 S3 阶段统一完成。 -->
SC does not override TAGE merely because it has a direction. It scales each way's `scThreshold` according to provider confidence. A saturated strong provider allows SC only when `totalPercsum` is well beyond the threshold; an intermediate or weak provider lowers the trigger. `scUsed` is set only for a conditional branch with a valid provider and a sum whose absolute value exceeds the threshold. The BPU top level performs the actual override in S3.

<!-- #### **8.2.2 训练流水线** -->
#### **8.2.2 Training Pipeline**
<!-- SC 的训练与更新流水线同样被划分为三个同步时钟周期（T0、T1、T2），主要任务是在分支执行结果返回后，对各类统计特征的饱和计数器和阈值状态执行闭环修正。与 TAGE 不同，SC 的 t0_fire 直接由 stageCtrl.t0_fire 驱动，不再与 enable 做与门，且 io.trainReady 恒为 true，因此后台训练不会因为读冲突而反压 FTQ。 -->
SC training/update also has T0, T1, and T2 cycles. It closes the loop on statistical counters and threshold state after branch outcomes return. Unlike TAGE, `t0_fire` is driven directly by `stageCtrl.t0_fire` rather than ANDed with enable, and `io.trainReady` is always true, so backend training does not backpressure the FTQ on read conflicts.

<!-- 在T0 与 T1 阶段，SC 会从 train.meta.sc 中恢复预测时的上下文快照，并结合 trainFoldedPathHist 与 train.meta.commonHR 重新计算各子表的训练索引。由于一个取指块中可能包含多个条件分支，硬件还需要根据 MBTB 的命中关系把每条训练分支重新映射回预测阶段使用过的逻辑 Way。只有在该槽位预测时存在有效的 TAGE Provider，且满足“SC 方向判断错误”或者“虽然判断正确但 totalPercsum 当时尚未越过门槛”这两类条件之一时，相关分支才会真正触发更新。 -->
In T0/T1, SC restores the prediction snapshot from `train.meta.sc`, then recomputes each subtable's training indices from `trainFoldedPathHist` and `train.meta.commonHR`. Because a fetch block may contain several conditional branches, MBTB hit relations remap each training branch to the logical way used during prediction. An update occurs only when that slot had a valid TAGE provider and either SC chose the wrong direction or was correct but its prior sum had not crossed the threshold.

<!-- 真正的更新计算在T1 末到 T2 之间完成。对于 Path、Global、BW 与 Imli 等普通子表，硬件会统计同一 Way 在本次 fetch block 中应当累计增加还是减少多少次，再以净增减的方式一次性计算出新的 ScEntry，避免同一周期内对同一 Way 做多次离散修改。Bias 表则使用扩展后的 biasWayIdx，因此同一分支在“Provider 为弱态且预测 Taken”与“Provider 非弱态且预测 Not Taken”等不同场景下，会落到完全独立的偏置计数器上。与此同时，各 Way 的阈值寄存器 scThreshold 也会被同步训练：若 SC 预测错误，则阈值增大，使以后更谨慎地介入；若 SC 判断正确但当时还未超过门槛，则阈值减小，使以后类似场景更容易启用校正。 -->
The update is computed between the end of T1 and T2. For ordinary Path, Global, BW, and Imli tables, hardware counts the net increments/decrements required for one way in the fetch block and computes one new `ScEntry`, avoiding multiple same-cycle writes. Bias uses the extended `biasWayIdx`, so scenarios such as weak provider + Taken and non-weak provider + Not Taken map to separate counters. `scThreshold` is trained in parallel: an SC misprediction increases the threshold, while a correct decision that did not yet cross the threshold decreases it.

<!-- 进入T2 阶段后，前述已经计算好的 entryVec、wayMask 与 bankMask 会被打包发送到各个 ScTable 的 update 端口，再由表内部的 WriteBuffer 排队写入真实 SRAM。 -->
In T2, the computed `entryVec`, `wayMask`, and `bankMask` are sent to each ScTable's update port, where the internal WriteBuffer queues them for SRAM.

<!-- ### **8.3 关键数据结构** -->
### **8.3 Key Data Structures**
<!-- #### **8.3.1 SC 表配置** -->
#### **8.3.1 SC Table Configuration**
<!-- SC 的所有子表都复用了统一的 ScTable 结构，只是在 numSets、numWays 以及索引所依赖的历史来源上有所不同。普通子表的 Way 数与 NumBtbResultEntries 保持一致，为 8；Bias 表则为了编码额外的 TAGE 状态，将 Way 数扩展到 32。当前版本各表的主要配置如下： -->
All SC subtables reuse the `ScTable` structure, differing in `numSets`, `numWays`, and history source used for indexing. Ordinary subtables have eight ways, matching `NumBtbResultEntries`; Bias expands to 32 ways to encode extra TAGE state. The current configuration is:

<!-- | **子表类型** | **实例数** | **主要特征来源** | **默认使能** | **作用概括** | -->
| **Subtable** | **Instances** | **Primary feature source** | **Default enable** | **Role** |
| --- | --- | --- | --- | --- |
<!-- | PathTable | 2 | PHR 折叠路径历史 | true | 捕捉不同路径上下文下的方向倾向 | -->
| PathTable | 2 | Folded PHR path history | true | Captures direction bias across path contexts |
<!-- | GlobalTable | 2 | commonHR.ghr | false | 理论上利用全局方向历史，当前版本默认关闭 | -->
| GlobalTable | 2 | `commonHR.ghr` | false | Uses global direction history in principle; disabled by default |
<!-- | BWTable | 2 | commonHR.bw | false | 理论上利用反向分支历史，当前版本默认关闭 | -->
| BWTable | 2 | `commonHR.bw` | false | Uses backward history in principle; disabled by default |
<!-- | ImliTable | 1 | commonHR.imli / io.imli | true | 利用循环迭代计数特征 | -->
| ImliTable | 1 | `commonHR.imli` / `io.imli` | true | Uses loop-iteration count |
<!-- | BiasTable | 1 | PC 和 TAGE 状态 | true | 维护静态偏置及与 TAGE 强弱态相关的偏置 | -->
| BiasTable | 1 | PC and TAGE state | true | Maintains static and TAGE-strength-related bias |




<!-- 其中 Global 与 BW 两类特征在当前默认配置下虽然完成了硬件实例化，但 S0 阶段不会主动发起读请求，S1 阶段的响应也会被强制置零，因此运行时真正参与投票的主要是 Path、Imli 和 Bias 三组统计信息。 -->
Although Global and BW are instantiated in the default configuration, S0 does not issue reads and S1 responses are forced to zero. At runtime, voting mainly uses Path, Imli, and Bias statistics.

<!-- #### **8.3.2 ScEntry ** -->
#### **8.3.2 ScEntry**
<!-- TAGE 的 SRAM 表项由TageEntry定义： -->
The SC SRAM entry is defined by `ScEntry`:

<!-- | **字段** | **位宽** | **作用** | -->
| **Field** | **Width** | **Purpose** |
| --- | --- | --- |
<!-- | ctr | 6 bits | 带符号饱和计数器，正值倾向 Taken，负值倾向 Not Taken | -->
| `ctr` | 6 bits | Signed saturating counter; positive favors Taken, negative favors Not Taken |




<!-- 计数器取值范围为 [-32, 31]。语义如下： -->
The counter ranges from [-32, 31]:

<!-- | **取值区间** | **方向含义** | -->
| **Range** | **Direction** |
| --- | --- |
<!-- | >= 0 | 支持 Taken | -->
| >= 0 | Favors Taken |
<!-- | < 0 | 支持 Not Taken | -->
| < 0 | Favors Not Taken |
<!-- | 0 | 最弱正态 | -->
| 0 | Weakest positive |
<!-- | -1 | 最弱负态 | -->
| -1 | Weakest negative |
<!-- | 31 / -32 | 强饱和状态 | -->
| 31 / -32 | Strong saturation |




<!-- 与 TAGE 不同，SC 的 ctr 不用来直接表达“表项命中后最终是否 taken”，而是表达“这个特征维度对 Taken / Not Taken 投票的力度”。真正的方向结论来自多表投票后的总和。 -->
Unlike TAGE, SC `ctr` does not directly encode whether a hit is finally taken; it encodes the strength of that feature's vote for Taken or Not Taken. The direction comes from the sum across subtables.

<!-- #### **8.3.3 ScMeta** -->
#### **8.3.3 ScMeta**
<!-- 与 TAGE 的 TageMeta 类似，SC 在预测阶段也会把关键上下文封装到 ScMeta 中，等待 FTQ 在训练时回传。由于 SC 的决策依赖多张子表的联合投票与阈值状态，因此 ScMeta 的内容明显比普通方向预测器更厚，既保存了各子表的响应快照，也保存了最终是否使用 SC 的信息。 -->
Like `TageMeta`, SC packages prediction context into `ScMeta` for the FTQ to return during training. Because SC depends on joint subtable voting and threshold state, `ScMeta` is larger than ordinary direction metadata: it stores subtable response snapshots and whether SC was ultimately used.

<!-- | **组成部分** | **内容** | -->
| **Component** | **Contents** |
| --- | --- |
<!-- | 各子表响应快照 | scPathResp、scGlobalResp、scBWResp、scImliResp、scBiasResp | -->
| Subtable response snapshots | `scPathResp`, `scGlobalResp`, `scBWResp`, `scImliResp`, `scBiasResp` |
<!-- | Bias 辅助字段 | scBiasLowerBits，记录预测时 Bias 的低位扩展信息 | -->
| Bias auxiliary field | `scBiasLowerBits`, the low-bit extension used by Bias at prediction time |
<!-- | 决策字段 | scPred、tagePred、tagePredValid、useScPred、sumAboveThres | -->
| Decision fields | `scPred`, `tagePred`, `tagePredValid`, `useScPred`, `sumAboveThres` |
<!-- | 调试字段 | 各表单独 taken 向量、预测时各表索引等 | -->
| Debug fields | Per-table taken vectors and prediction-time table indices |


<!-- ### **8.4  I/O 端口** -->
### **8.4 I/O Ports**
<!-- | **接口名称** | **方向** | **类型** | **位宽/说明** | **功能描述** | -->
| **Interface** | **Direction** | **Type** | **Width / notes** | **Description** |
| --- | --- | --- | --- | --- |
<!-- | enable | Input | Bool | 1 | SC模块使能（来自ctrl.scEnable，影响预测流水线s0/s1/s2） | -->
| `enable` | Input | Bool | 1 | SC enable from `ctrl.scEnable`, affecting S0/S1/S2. |
<!-- | stageCtrl | Input | StageCtrl | 5 Bool | 流水线各阶段fire信号（s0/s1/s2/s3/t0_fire） | -->
| `stageCtrl` | Input | StageCtrl | 5 Bool | Stage fire signals (`s0/s1/s2/s3/t0_fire`). |
<!-- | startPc | Input | PrunedAddr | 50 bits | 当前预测取指块起始PC | -->
| `startPc` | Input | PrunedAddr | 50 bits | Current fetch-block start PC. |
<!-- | train | Input | BpuTrain | - | 来自FTQ的训练数据包 | -->
| `train` | Input | BpuTrain | - | Training bundle from FTQ. |
<!-- | mbtbResult | Input | Vec[Valid[Prediction]] | 8个条目 | MBTB的分支结果（cfiPosition、target、attribute、taken） | -->
| `mbtbResult` | Input | Vec[Valid[Prediction]] | 8 entries | MBTB branch result (`cfiPosition`, `target`, `attribute`, `taken`). |
<!-- | providerTakenCtrs | Input | Vec[Valid[SaturateCounter]] | 8 × (1+3) bits | 来自TAGE S2阶段的Provider计数器 | -->
| `providerTakenCtrs` | Input | Vec[Valid[SaturateCounter]] | 8 x (1+3) bits | Provider counters from TAGE S2. |
<!-- | foldedPathHist | Input | PhrAllFoldedHistories | - | 预测用折叠路径历史 | -->
| `foldedPathHist` | Input | PhrAllFoldedHistories | - | Folded path history for prediction. |
<!-- | trainFoldedPathHist | Input | PhrAllFoldedHistories | - | 训练用折叠路径历史 | -->
| `trainFoldedPathHist` | Input | PhrAllFoldedHistories | - | Folded path history for training. |
<!-- | imli | Input | UInt | ImliWidth=8 bits | IMLI循环计数器（来自CommonHR的s0_imli） | -->
| `imli` | Input | UInt | ImliWidth = 8 bits | IMLI loop counter (`s0_imli` from CommonHR). |
<!-- | commonHR | Input | CommonHREntry | valid + ghr + bw + imli | 全局历史+反向历史（含valid位） | -->
| `commonHR` | Input | CommonHREntry | valid + ghr + bw + imli | Global and backward history, including valid. |
<!-- | trainReady | Output | Bool | 1 | 恒为true（io.trainReady := true.B，不反压FTQ） | -->
| `trainReady` | Output | Bool | 1 | Always true (`io.trainReady := true.B`); no FTQ backpressure. |
<!-- | resetDone | Output | Bool | 1 | 所有内部SRAM复位完成 | -->
| `resetDone` | Output | Bool | 1 | All internal SRAM resets complete. |
<!-- | scTakenMask | Output | Vec[Bool] | 8 | SC的预测方向（totalPercsum >= 0） | -->
| `scTakenMask` | Output | Vec[Bool] | 8 | SC direction (`totalPercsum >= 0`). |
<!-- | scUsed | Output | Vec[Bool] | 8 | 是否使用SC预测（仅条件分支+Provider有效+超过阈值） | -->
| `scUsed` | Output | Vec[Bool] | 8 | Whether SC is used (conditional branch, valid provider, sum above threshold). |
<!-- | meta | Output | ScMeta | ≈ 1400+ bits（含debug字段） | SC元数据，写入FTQ待训练时回传 | -->
| `meta` | Output | ScMeta | ~1400+ bits (including debug) | SC metadata stored in FTQ and returned for training. |




<!-- 从这些接口可以看出，SC 的输入既包含来自历史模块的特征流，也包含来自 TAGE 的置信度信息；输出则严格分成“我怎么判断”（scTakenMask）与“顶层是否该用我”（scUsed）两部分。这种接口组织方式保证了 SC 既能保持自身算法的独立性，又能平滑挂接在现有 BPU 决策链路之上。 -->
The interfaces show that SC consumes both history features and TAGE confidence, while separating its direction (`scTakenMask`) from the top-level enable decision (`scUsed`). This keeps the statistical algorithm independent while integrating cleanly with the BPU decision chain.



<!--
## 9. **ITTAGE**
### 9.1 **总体架构**
ITTAGE是一个面向间接分支的TAGE-like预测器，主要负责间接跳转/目标预测，由多张不同历史长度的预测表（IttageTable）并行构成。每个表采用banked SRAM存储条目，包含标签、置信度计数器、useful 计数器以及目标偏移。预测时，根据当前 PC 和折叠全局历史并发查询所有表，并通过 ParallelSelectTwo 机制选出提供者（provider）和替代提供者（alt provider）：若 provider 置信度不低则使用其目标，否则回退到 alt provider。训练阶段依据预测是否正确、是否使用 alt provider等条件更新对应表的计数器和useful计数器，并可在误预测且提供者不满足条件时，向历史长度更长的空闲表分配新条目。此外，目标地址的高位（region）通过 RegionWays模块单独管理，以减少存储开销。

Kunminghu-v3中的ITTAGE的主要特征如下：

Ø 多表结构：Parameters.scala中定义TableInfos，每个表有不同历史长度与行数，提高空间利用率与泛化能力。

Ø 历史折叠：使用FoldedHistoryInfo将长历史折叠成索引、tag所需宽度，支持不同的历史覆盖长度以提高预测精度。

Ø 写缓冲机制：每个bank有独立WriteBuffer，当SRAM读端空闲时由缓冲区下发写操作，减少更新对预测流水线的阻塞。

Ø 目标地址重构：RegionWays用region编码目标地址高位，适合大目标空间下的压缩表示。

### **9.2 工作机制**
#### **9.2.1 预测流水线**
预测流水线分为S0～S3 四个周期。S0 寄存起始 PC 和折叠全局历史，本身不进行复杂计算，主要作用是建立流水线对齐，保证 S1 能在一个周期内稳定地获得所有查询所需的数据；S1 向所有 ITTAGE 表发出读请求；S2 收集各表响应，通过 ParallelSelectTwo 选出 provider 和 alt provider，结合 RegionWays 计算预测目标并确定分配候选；S3 寄存结果并输出最终预测与元数据。整个流程在每拍完成一次间接分支预测。

S0 周期接收来自上游的起始 PC（io.startPc）和折叠全局历史（io.s1_foldedPhr）。两个关键控制信号是 s0_fire = io.stageCtrl.s0_fire && io.enable，当它为真时，S0 将输入寄存到下一周期。具体地，s1_startPc = RegEnable(s0_startPc, s0_fire) 将 PC 传递到 S1；折叠历史则直接连接到 io.s1_foldedPhr，供表模块在 S1 使用。

S1 周期是表查询发起的阶段，当 s1_fire 有效且全局使能 io.enable 为真时，s1_isIndirect 固定为 true.B，因此所有 ITTAGE 表 tables 都会接收读请求。每个表获得三个输入s1_fire、PC、折叠全局历史。在表内部，将 PC 右移指令偏移位，然后结合折叠历史计算 bank 索引、set 索引和标签。由于表采用 banked SRAM，每个 Bank 独立选通。S1 结束时，所有表的读请求均已发出，等待下一个周期返回 SRAM 数据。同时，S1 将所请求的 PC 寄存到 s2_startPc，以便后续周期使用。

S2 周期是预测的关键组合逻辑周期。首先收集各表的读响应 s2_resps，每个响应包含有效位、置信度计数器 cnt、有用计数器 usefulCnt 和目标偏移 targetOffset。随后调用 ParallelSelectTwo从所有命中条目中选出两个最佳候选：providerInfo（置信度最高）和 altProviderInfo（次高）。同时根据置信度计数器判断provider 是否低置信度。为了生成完整目标地址，需调用 RegionWays 模块得到providerCatTarget 和 altProviderCatTarget。预测目标 s2_ittageTarget 根据 provided 和 provided 置信度、altProvided 三者的组合选择 provider 或 alt provider 的目标。此外，S2 还会扫描 s2_allocatableSlots（那些未命中且 useful 计数器为负的表），结合 LFSR 随机数选出最佳分配表索引 s2_allocEntry，存入元数据供训练使用。最后将 provider/alt provider 的各项信息（计数器、目标等）寄存到 S3。

S3 周期仅做寄存和输出。所有 S2 计算出的关键信号都通过 RegEnable 或普通寄存器打一拍，得到 s3_ittageTarget、s3_provided、s3_providerUsefulCnt 等。最终预测结果通过 s3_fire && s3_provided 和 s3_ittageTarget 输出到上级模块。同时，完整的元数据 io.meta 被赋值：包括 provider/alt provider 的 valid、索引、计数器、目标地址以及 altDiffers（两者目标是否不同）、allocate（分配候选）等。S3 是预测流水线的最后一拍，完成后即可使用该预测结果进行跳转验证。整个预测过程从 PC 输入到预测输出共耗费 4 个周期。



#### **9.2.2 训练流水线**
与 TAGE 的 TageMeta 类似，SC 在预测阶段也会把关键上下文封装到 ScMeta 中，等待 FTQ 在训练时回传。由于 SC 的决策依赖多张子表的联合投票与阈值状态，因此 ScMeta 的内容明显比普通方向预测器更厚，既保存了各子表的响应快照，也保存了最终是否使用 SC 的信息。训练流水线在预测完成后若干周期触发，分为T0、T1 和T2三个阶段。T0 本身不执行任何表访问或计数更新，仅仅是数据的跨流水级传递。；T1 解析分支信息，确定是否需要更新、是否误预测，并生成各表的更新掩码、新计数器和目标偏移；T2阶段将更新指令下发给各表 SRAM 和 RegionWays 模块，完成条目修正或新条目分配。

T0 周期的触发条件为 t0_fire = io.enable && io.stageCtrl.t0_fire。该周期接收外部训练包 io.train及其对应的折叠历史 io.trainFoldedPhr，将二者分别寄存到 t1_train 和 t1_trainFoldedPhr。同时，训练包内嵌的元数据（即预测阶段保存的 IttageMeta）也会被寄存到 t1_meta。这样确保在真正需要更新时，S3 周期产生的元数据能准确到达训练流水线。

T1 周期是训练决策的核心。从t1_train.branches中选出需要ITTAGE更新且实际跳转的唯一分支（trainBranchIdx），判断是否有效更新（updateValid）以及是否误预测（updateMisPred）。根据provider和altProvider的命中情况、目标比较结果，设置各表的更新掩码（updateMask、updateUsefulCntMask）、更新正确标志（updateCorrect）、有用计数器更新值（updateUsefulCnt）等。若满足分配条件（误预测且并非提供者正确但低置信度），则向allocate指向的表槽发起分配请求，并更新tickCnt。

T2周期每个Table模块接收对应的io.update信号，根据掩码更新条目中的置信度计数器、有用计数器、目标偏移等字段。同时RegionWays模块依据updateRealUsePCRegion决定是否写入新的region映射，完成训练的全部写回操作。

| 场景 | 误预测？ | 动作 |
| :---: | :---: | :---: |
| 预测正确，provider自信 | 否 | Provider cnt↑，usefulCnt↑ |
| 预测错位，provider自信 | 是 | Provider cnt↓，usefulCnt↓，尝试分配新表 |
| provider不自信，alt provider 预测正确 | 否 | alt provider cnt↑，usefulCnt↑（provider不变） |
| provider不自信，alt provider 预测错误 | 是 | alt provider cnt↓，provider cnt↓，分配新表 |




### **9.3 关键数据结构**
#### **9.3.1 TAGE 表结构以及 TAGE 表条目**
ITTAGE 采用五张不同历史长度与表项深度的 TAGE 表，以覆盖循环、复杂间接分支的多种模式。每张表条目存储标签、信心计数器、有用计数器及目标偏移，并采用双 bank SRAM 实现读（预测）与写（训练）的并行。下表总结了各表的配置参数及每个字段的用途。

| 表索引 | 行数 | 历史长度 | 标签位宽 | 条目字段 | Bank数量 | 每bank行数 |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 0 | 256 | 4 | 9 | valid(1), tag(9), confidenceCnt(2), usefulCnt(1), targetOffset(25), paddingBit(1) | 2 | 128 |
| 1 | | 8 | | | | |
| 2 | 512<br/> | 13 | | | | 256 |
| 3 | | 16 | | | | |
| 4 | | 32 | | | | |




| 字段 | 类型 | 用途 |
| :---: | :---: | :---: |
| valid | Bool | 条目是否有效 |
| tag | UInt(tagLen.W) | 部分地址与折叠历史的异或哈希，用于命中匹配。 |
| confidenceCnt | SaturateCounter (宽度=ConfidenceCntWidth, 2位) | 信心计数器：记录该条目预测正确的置信度。饱和正表示强信心，饱和负表示不可信（会触发使用替代提供者） |
| usefulCnt | SaturateCounter (宽度=UsefulCntWidth, 1位) | 有用计数器：用于替换策略。当为饱和负时，该条目可被分配覆盖。全局老化（tickCnt）会周期性将其复位为饱和负 |
| targetOffset | IttageOffset | 存储目标地址的低TargetOffsetWidth位，以及指向区域缓存（RegionWays）的指针和是否使用PC区域的标志 |
| paddingBit | UInt(1.W) | 对齐填充位，无逻辑功能 |


####
#### **9.3.2 预测元数据：IttageMeta**
预测流水线S3 阶段输出的元数据记录了本次预测所依赖的 provider 表、alt provider 表、两者的计数器值、目标地址以及可分配的新表候选。这些信息是训练流水线进行表项更新、信心调整和分配决策的直接依据，确保了训练与预测之间的一致性。

| 字段 | 类型 | 用途 |
| :---: | :---: | :---: |
| valid | Bool | 该元数据整体是否有效 |
| provider | Valid[UInt(log2Ceil(NumTables).W)] | 最终提供预测的表编号 |
| altProvider | Valid[UInt(log2Ceil(NumTables).W)] | 次优命中表的编号 |
| altDiffers | Bool | 提供者与替代提供者的目标地址是否不同 |
| providerUsefulCnt | UsefulCounter | 提供者条目的有用计数器值，用于更新时调整 |
| providerCnt | ConfidenceCounter | 提供者条目的信心计数器值 |
| altProviderCnt | ConfidenceCounter | 替代提供者条目的信心计数器值 |
| allocate | Valid[UInt(log2Ceil(NumTables).W)] | 可分配的表编号 |
| providerTarget | PrunedAddr(VAddrBits) | 提供者最终计算出的完整目标地址 |
| altProviderTarget | PrunedAddr(VAddrBits) | 替代提供者最终计算出的完整目标地址 |


### **9.4  I/O 端口**
#### **9.4.1 ITTAGE 顶层模块（Ittage）**
顶层模块封装了完整的预测与训练接口。预测方向接收起始PC、折叠历史及流水线控制信号，输出预测目标与元数据；训练方向接收分支结果及对应的折叠历史，返回训练就绪信号。所有 TAGE 表及 RegionWays 的复位完成状态通过 sramResetDone 汇总输出。

| 信号名称 | 方向 | 数据类型 | 功能描述 |
| :---: | :---: | :---: | :---: |
| startPc | I | PrunedAddr(VAddrBits) | 当前预测块的起始PC |
| stageCtrl | I | StageCtrl | 流水线控制信号，指示各级流水有效信号 |
| enable | I | Bool | 全局使能信号，关闭时流水不推进 |
| train | I | BpuTrain | 训练/更新数据包，包含分支信息、预测元数据等 |
| s1_foldedPhr | I | PhrAllFoldedHistories | S1阶段的全局折叠历史（所有历史长度的折叠值），用于TAGE表索引和标签计算 |
| trainFoldedPhr | I | PhrAllFoldedHistories | 与训练分支对应的折叠历史，用于更新时的索引/标签重算 |
| trainReady | O | Bool | 指示预测器可以接受新的训练请求 |
| sramResetDone | O | Bool | 所有TAGE表内部SRAM复位完成信号 |
| prediction | O | IttagePrediction | 预测结果：含hit（是否命中预测）和target（预测目标地址） |
| meta | O | IttageMeta | 预测元数据：记录提供者、替代表、计数器值、分配目标等，供更新阶段使用 |




#### **9.4.2 TAGE 表子模块（IttageTable）**
每个TAGE 表独立提供预测读请求接口（req/resp）和训练更新接口（update）。请求携带起始 PC 和折叠历史，响应返回条目信心、有用计数及目标偏移；更新接口可分别控制信心计数器、有用计数器及目标偏移的修改，支持正常更新、分配新表项及全局 useful 复位。

| 信号名称 | 方向 | 数据类型 | 功能描述 |
| :---: | :---: | :---: | :---: |
| req | I | DecoupledIO[Req] | 预测请求 |
| resq | O | Valid[Resp] | 预测响应 |
| update | I | Update | 表项更新数据 |
| sramResetDone | O | Bool | 该表所有bank SRAM的复位完成信号 |




内部子结构Req、Resp和Update

Req：

startPc: PrunedAddr(VAddrBits)—起始PC

foldedHist: PhrAllFoldedHistories—所有历史长度的折叠值

Resp：

cnt: ConfidenceCounter—2位饱和计数器（信心）

usefulCnt: UsefulCounter—1位有用计数器

targetOffset: IttageOffset—目标偏移（低TargetOffsetWidth位+区域指针）

Update:

startPc: PrunedAddr—更新PC

foldedHist: PhrAllFoldedHistories—折叠历史

valid: Bool—更新有效

correct: Bool—预测是否正确

alloc: Bool—是否为新分配

oldCnt: ConfidenceCounter—旧信心值

usefulCntValid: Bool—有用计数器更新有效

usefulCnt: UsefulCounter—新有用值

resetUsefulCnt: Bool—全局复位有用位触发

targetOffset: IttageOffset—新目标偏移

oldTargetOffset: IttageOffset—旧目标偏移

### **9.4.3 区域缓存子模块（RegionWays）**
为解决目标地址高位（region）存储开销过大的问题，RegionWays 以独立缓存的方式维护 region → 指针的映射。各 TAGE 表仅存储指针和低位偏移，预测时通过指针读取对应 region 并拼接成完整目标地址。训练阶段支持 region 的写入、查询与指针分配，采用可配置的替换策略（PLRU / 随机 / LRU）。

| 信号名称 | 方向 | 数据类型 | 功能描述 |
| :---: | :---: | :---: | :---: |
| reqPointer | I | Vec(NumTables, UInt(log2Ceil(RegionNums).W)) | 并行读取请求：每个TAGE表提供的区域指针，供按指针读出区域高位 |
| respHit | O | Vec(NumTables, Bool) | 每个读指针是否命中（对应条目有效） |
| respRegion | O | Vec(NumTables, UInt(RegionBits.W)) | 命中的区域高位值 |
| updateRegion | I | Vec(RegionPorts, UInt(RegionBits.W)) | 更新/查询区域值（通常2路） |
| updateHit | O | Vec(RegionPorts, Bool) | 更新查询是否命中 |
| updatePointer | O | Vec(RegionPorts, UInt(log2Ceil(RegionNums).W)) | 命中的指针（供写回TAGE表项） |
| writeValid | I | Bool | 写区域有效：指示需要将一个新区域写入缓存 |
| writeRegion | I | UInt(RegionBits.W) | 待写入的区域高位值 |
| writePointer | O | UInt(log2Ceil(RegionNums).W) | 为写入区域分配的指针（若命中则为已存在项的指针，否则为新分配项指针） |
-->
## 9. **ITTAGE**
ITTAGE is a TAGE-like predictor for indirect branches and targets. Multiple `IttageTable` instances with different history lengths are queried in parallel using the PC and folded global history. `ParallelSelectTwo` chooses a provider and alternate provider; a confident provider supplies the target, otherwise the alternate is used. Entries contain tags, confidence and useful counters, and target offsets. Training updates counters and useful state, allocates a longer-history entry on eligible mispredictions, and maintains target high bits separately through `RegionWays`. Banked SRAM and per-bank WriteBuffers decouple training writes from reads.

### 9.1 **Overall Architecture**
The Kunminghu-v3 organization uses table-specific history lengths and row counts from `Parameters.scala`, folded histories for index/tag widths, WriteBuffers for nonblocking updates, and region-pointer target reconstruction for large target spaces.

### **9.2 Operating Mechanism**
#### **9.2.1 Prediction Pipeline**
ITTAGE runs S0-S3. S0 registers `startPc` and folded history; `s0_fire = io.stageCtrl.s0_fire && io.enable` controls acceptance. S1 issues reads to every table, deriving bank, set, and tag from the shifted PC and folded history. S2 collects responses and uses `ParallelSelectTwo` to choose provider and alternate, checks confidence, reconstructs full targets through `RegionWays`, and chooses an allocatable longer-history slot. S3 registers the target, provider state, and metadata and emits a valid prediction when `s3_fire && s3_provided`. The complete path takes four cycles from PC input to output.

#### **9.2.2 Training Pipeline**
Training uses T0, T1, and T2. T0 latches `io.train`, folded training history, and `IttageMeta`. T1 selects the unique taken branch requiring update, computes `updateValid`, `updateMisPred`, table masks, counter updates, and allocation. T2 applies the masks to table counters, useful counters, and target offsets; `RegionWays` updates the region mapping when `updateRealUsePCRegion` requires it. Correct confident provider increments its counters; provider mispredictions decrement and may allocate; an unconfident provider falls back to alternate and updates the alternate according to its correctness.

### **9.3 Key Data Structures**
ITTAGE uses five banked TAGE tables. Each entry stores `valid`, a folded PC/history `tag`, a confidence counter, a useful counter, a low target offset, and a padding bit. `IttageMeta` records provider and alternate validity and indices, target differences, confidence/useful snapshots, allocation candidate, and reconstructed provider/alternate targets so training updates the exact prediction context.

### **9.4 I/O Ports**
The top-level `Ittage` accepts `startPc`, `stageCtrl`, `enable`, `train`, S1 folded history, and training folded history. It outputs `trainReady`, `sramResetDone`, `prediction` (`hit` and `target`), and `IttageMeta`. Each `IttageTable` exposes decoupled `req`, valid `resq`, `update`, and `sramResetDone`; request bundles carry PC/history and response bundles carry confidence, useful, and target-offset fields. `RegionWays` accepts parallel region pointers and update-region values, reports pointer hits and regions, and allocates a pointer for a new region through `writeValid`, `writeRegion`, and `writePointer`.






<!--
## 10. **RAS 与 uRAS**
### **10.1 总体架构**
XiangShan 处理器中的 RAS (Return Address Stack) 是一个用于分支预测的模块，主要用于预测函数调用 (call) 和返回 (return) 的目标地址。RAS的整体架构分为两个主要部分：主RAS（RAS + RASStack） 和URAS（MicroRAS）。主RAS维护一个完整的返回地址栈，由已提交部分（commit stack）和推测部分（speculative queue）构成，通过指针（ssp、sctr、tosr、tosw、bos）管理栈顶与嵌套深度。它接收来自分支预测器的call/ret操作、提交信息以及重定向（redirect）信号，支持栈的push/pop、错误恢复和提交更新，并输出当前的栈顶返回地址及各种元数据，用于后续预测。URAS是一个轻量级的推测模块，跟踪前端流水线S1‑S3阶段的pendng call/ret操作，在遇到RET指令时快速预测返回目标，通过与主RAS的状态协同，提高预测准确性并减少因流水线操作导致的预测错误。两者共同工作，为分支预测器提供高效、可靠的返回地址预测。

Kunminghu-v3中的RAS的主要特征如下：

Ø 双层推测架构：主RAS（RAS + RASStack）维护完整的返回地址栈，分为提交栈（commit stack，已提交状态）和推测队列（speculative queue，未提交的预测操作）。URAS（MicroRAS）轻量追踪前端流水线S1~S3阶段的待处理call/ret，在遇到RET指令时快速输出预测目标，减少流水线冲突。

Ø 精确的栈管理机制：使用多个指针（tosw、tosr、bos、ssp、sctr）分别管理推测写入/读取位置、栈底、提交栈指针及嵌套深度计数器。支持连续相同地址的call合并（ctr计数器），避免栈内冗余条目。

Ø 重定向恢复（Redirect Recovery）：当分支预测错误（redirect信号）时，可根据保存的元数据（RASInternalMeta）将RAS恢复到正确状态，并重新执行相应的call/ret操作。

Ø 溢出保护：监测推测队列与提交栈之间的距离，当接近容量上限时触发specNearOverflow，禁止新的push操作，防止溢出。

Ø 写旁路（Write Bypass）：在RASStack中实现写旁路逻辑，当前周期写入的条目可直接被后续读取，避免流水线停顿。

### **10.2 工作机制**
#### **10.2.1 预测流水线**
S1周期（取指/解码阶段），分支预测器将当前指令的CFI信息（包括起始PC、CFI位置及分支属性）输入RAS的specIn端口。RAS判断该指令是否为call或ret：若为call，则根据PC和CFI位置计算出返回地址（specPushAddr = 起始PC + 偏移*2 + 2），并将该地址连同嵌套计数器准备写入推测队列；若为ret，则标记需要从栈顶弹出。同时，URAS（MicroRAS）在S1周期捕获该操作，更新内部流水线寄存器（s2_hasPush/pop将在下一周期生效）。

S2周期（预测/访问阶段），S1的操作传递至S2，call的返回地址存入s2_retAddr，push/pop标志寄存。同时，URAS开始评估当前S2和S3阶段的pending操作对栈顶的影响：例如，若S1为ret且S2存在push，则两者可能相互抵消。此外，RAS主模块的RASStack根据指针（tosw, tosr, ssp等）读取推测队列或提交栈中的目标地址，为返回预测做好准备。

S3周期（结果输出/重定向处理），S2的操作进入S3（s3_hasPush/pop和s3_retAddr），URAS在此周期综合所有信息（S1当前操作、S2/S3 pending操作、全局重定向信号hasRedirect以及主RAS的fullRetAddr），计算出最终的预测返回地址（topRetAddr）和有效性标志（isCanUse），并通过specOut端口输出给前端。若S3周期发生重定向（overrideData有效），URAS将 S1 和 S2 流水线阶段中记录的未提交的 CALL/RET 操作全部清除，仅根据S3的操作（push/pop）调整预测输出。

后续周期（提交/恢复阶段），指令经过执行并最终提交时，RAS接收commit信息（包含提交的call/ret及对应的元数据）。主RAS的RASStack据此更新提交栈（commit stack）：若为call且与栈顶地址相同且计数器未满，则增加计数器；否则压入新条目并调整nsp指针。同时，更新底部指针bos以确保溢出检测的准确性。若发生分支重定向（redirect有效），则RAS会根据保存的元数据（RASInternalMeta）恢复指针（tosw, tosr, ssp, sctr），并重新执行call/ret操作，使推测状态与真实程序流对齐。

#### **10.2.2 Override 处理机制**
RAS中的Override处理机制是 MicroRAS 提供的一种轻量级、低延迟的修正路径，它能够在不清空整个流水线的前提下，用S3阶段的正确指令信息覆盖前两级错误推测，并快速给出可靠的返回地址预测（pop场景除外）。

当 io.overrideData.valid 为真（hasOverride）时，表示S1和S2阶段对该指令的推测已过时或错误，需要被覆盖。冲刷阶段，hasOverride 信号会立即清除 S1 和 S2 阶段记录的所有 pending 操作（s2_hasPush、s2_hasPop 清零），同时阻止 S1 向 S2 传递新的操作。这意味着流水线前两级中所有对 RAS 的猜测性 push/pop 均被丢弃，不再影响后续预测。更新S3阶段，使用io.overrideData.bits 中包含正确的指令属性（是否为 call/ret）及其返回地址。URAS 根据这些信息重新设定 S3 阶段的操作标志（s3_hasPush / s3_hasPop 虽会被清零，但 s3_realPush / s3_realPop 直接用于最终预测计算）和返回地址（s3_realPushAddr）。最终预测输出阶段，在 hasOverride 生效的周期，isCanUse和topRetAddr完全由 override 数据决定，若 override 操作是 pop（ret），则预测无效（isCanUse = false），因为pop操作会改变栈顶，但主RAS的 fullRetAddr 是在未考虑该pop的情况下的值，而override的pop又无法立即提供新的栈顶（需要从主RAS读取，但主RAS尚未更新），为了避免错误预测。若 override 操作是 push（call），则预测有效，且返回地址为该 push 产生的地址（s3_realPushAddr）。若无 RAS 操作，则直接使用主RAS 提供的当前栈顶（io.fullRetAddr）。

#### **10.2.3 全局重定向机制**
全局重定向机制同时作用于主RAS和URAS两个模块，确保整个返回地址预测状态能够快速、精确地回滚到正确的程序点。该机制由外部分支预测器通过发送io.redirect信号（Valid[BpuRedirect]）触发，信号中携带了完整的RAS元数据（RASInternalMeta，包含ssp、sctr、tosw、tosr、nos等指针）以及重定向所对应指令的属性（是否为call/ret）和调用地址。在RAS模块中，该信号被寄存一拍后传递给底层的RASStack，同时会判断重定向的推测写指针是否在当前推测写指针之前或队列未接近溢出，以决定是否执行恢复。

在主RAS和RASStack一侧，全局重定向激活时执行的是全状态覆盖与操作重放。一旦redirect.valid有效，RASStack立即强制覆盖所有内部指针：tosr、tosw、ssp、sctr均被设为redirect.meta中的值。接着根据重定向携带的指令类型同步执行一次正确的push或pop：若isCall为真，则调用specPush将正确的返回地址压入推测队列；若isRet为真，则调用specPop弹出顶层条目；若非call/ret，则仅恢复状态而不改动栈内容。此外，全局重定向还会影响写旁路逻辑——当重定向是call时，会设置写旁路有效并将当前写入的条目存入旁路寄存器，供后续读取使用。

在URAS一侧，全局重定向信号io.hasRedirect也会被同步处理，但其行为与主RAS不同。MicroRAS收到hasRedirect后，会立即清空其内部流水线寄存器中记录的S2和S3阶段的pending操作（s2_hasPush、s2_hasPop、s3_hasPush、s3_hasPop全部清零），从而丢弃所有尚未提交的推测call/ret信息。同时，MicroRAS的输出强制设为无效：isCanUse := false.B，topRetAddr置零。这意味着在全局重定向发生的周期及其后一个周期（通过redirectDelay1延长抑制），URAS无法提供任何可信的返回地址预测，前端必须等待主RAS通过io.fullRetAddr输出恢复后的栈顶，或者直接使用其他预测机制。值得注意的是，全局重定向的优先级高于局部overrideData，两者同时发生时，全局重定向会覆盖overrideData的效果。

#### **10.2.4 Commit 机制**
RAS 的 Commit 机制用于将已经确认执行的 call/ret 指令从推测队列同步到非推测的提交栈（commit stack）中，从而维护一个始终与程序真实执行流一致的返回地址栈。该机制由主 RAS 模块接收外部的 io.commit 信号触发，信号类型为 Valid[BpuCommit]，其中包含指令的分支属性（是否为 call/ret）以及 RAS 提交元数据RASCommitMeta（即当前的提交栈指针 ssp 和推测写指针 tosw）。在 RAS 模块中，该信号被寄存一拍后传递给底层的RASStack 模块，同时将 commitInfo 中的 meta.tosw 作为索引，从推测队列中读出预存的返回地址，用于后续提交栈的更新。

在RASStack 内部，提交栈由寄存器阵列 commitStack 和栈顶指针 nsp 管理。当收到有效的提交且 pushValid 为真（即提交的是一条 call 指令）时，模块首先检查当前提交栈顶（commitStack(nsp)）的返回地址和计数器：如果栈顶地址与待提交的返回地址相同，且计数器尚未达到最大值（StackCounterMax），则仅将计数器加一；否则，将栈顶指针 nsp 递增，并在新位置写入返回地址，计数器清零。当 popValid 为真（提交的是一条 ret 指令）时，则反向操作：若栈顶计数器大于零则减一，否则递减 nsp 并将新栈顶的计数器（来自提交栈或推测队列）恢复出来。此外，提交更新还会维护一个栈底指针 bos：当 pushValid 时，bos 被直接设为提交元数据中的 tosw；当仅有提交有效而无 push 且 tosw 与 bos 的距离超过 2 时，bos 会递减一次，用于推测队列的溢出检测。

除了更新提交栈本身，Commit 机制还会通过io.commit.metaSsp与当前 nsp 的比较来强制校正指针偏差，即使在推测状态出错的情况下，提交阶段也能将非推测部分恢复到正确位置。

### **10.3 关键数据结构**
#### **RASEntry**
RASEntry 是RAS中存储单个栈条目的核心数据结构，用于记录一个函数调用的返回地址以及该地址被重复压栈的次数（通过计数器实现）。它同时存在于推测队列和提交栈中，是RAS预测返回目标的基础单元。

| 字段 | 类型 | 说明 |
| :---: | :---: | :---: |
| retAddr | PrunedAddr | 返回地址，宽度为 VAddrBits  |
| ctr | UInt | 计数器，宽度由 StackCounterWidth 决定 |


####
#### **RASPtr**
RASPtr 用于管理推测队列（环形缓冲区）的读写位置，通过一个标志位 flag 区分指针是否发生过环绕，从而支持环形队列的满/空判断和距离计算。它是实现推测队列指针算术的关键工具。

| 字段 | 类型 | 说明 |
| :---: | :---: | :---: |
| flag | Bool | 绕回标志，宽度1位 |
| value | UInt | 队列索引值，宽度为log2Up(SpecQueueSize) |


####
#### **RASInternalMeta**
RASInternalMeta封装了RAS在某一时刻的全部状态快照，包括提交栈指针、嵌套计数器、推测写指针、推测读指针以及下一次推测读指针。该结构主要用于重定向恢复和提交更新，使RAS能够在预测错误时精确回滚到正确的状态。

| 字段 | 类型 | 说明 |
| :---: | :---: | :---: |
| ssp | UInt | 提交栈指针，宽度为 log2Up(CommitStackSize) |
| sctr | UInt | 提交栈计数器，宽度由 StackCounterWidth 决定 |
| tosw | RASPtr | 推测写指针，宽度同RASPtr（flag 1位 + value 5位） |
| tosr | RASPtr | 推测读指针 |
| nos | RASPtr | 下一次推测读指针 |


####
#### **RASRedirectMeta**
RASRedirectMeta 继承自 RASInternalMeta，并额外包含一个顶层的返回地址。该结构在分支重定向时传递，用于将RAS恢复到正确的状态，并告知新栈顶的返回地址，以便前端快速恢复预测。

| 字段 | 类型 | 说明 |
| :---: | :---: | :---: |
| 继承字段 | 见3.3 | ssp, sctr, tosw, tosr, nos 字段同上 |
| topRetAddr | PrunedAddr | 顶层返回地址，宽度VAddrBits |


####
#### **RASCommitMeta**
RASCommitMeta 在指令提交时传递，包含提交栈指针和推测写指针。主RAS利用该信息将推测队列中已确认的call/ret操作同步到提交栈，从而推动非推测部分的更新。

| 字段 | 类型 | 说明 |
| :---: | :---: | :---: |
| ssp | UInt | 提交栈指针，宽度为 log2Up(CommitStackSize) |
| tosw | RASPtr | 推测写指针，宽度同RASPtr（flag 1位 + value 5位） |


####
#### **RASSpecInfo**
RASSpecInfo 是主RAS模块接收的输入信息，描述了分支预测器发送的待推测控制流指令（CFI）的详细属性，包括起始PC、CFI在提取块中的位置以及具体的分支类型（是否为call/ret等）。该结构驱动RAS的推测push/pop操作。

| 字段 | 类型 | 说明 |
| :---: | :---: | :---: |
| attribute | BranchAttribute | 分支属性（含isCall/isReturn等标志） |
| cfiPosition | UInt | CFI在提取块中的位置，宽度由CfiPositionWidth决定 |
| startPc | UInt | 指令起始PC，宽度VAddrBits |


####
#### **MicroRASSpecIn**
MicroRASSpecIn是URAS模块从分支预测器接收的输入，用于描述S1阶段即将进入流水线的控制流指令。其内容与主RAS的RASSpecInfo类似，但专门服务于URAS的轻量推测逻辑，用于判断当前指令是否为call/ret并计算返回地址。

| 字段 | 类型 | 说明 |
| :---: | :---: | :---: |
| attribute | BranchAttribute | 分支属性（含isCall/isReturn等标志） |
| cfiPosition | UInt | CFI在提取块中的位置，宽度由CfiPositionWidth决定 |
| startPc | UInt | 指令起始PC，宽度VAddrBits |


####
#### **MicroRASSpecOut**
MicroRASSpecOut是微RAS模块向分支预测器输出的最终预测结果。它包含一个预测的返回地址以及一个有效性标志，前端可根据isCanUse决定是否采纳该预测。若无效，则需等待主RAS或采用其他恢复策略。

| 字段 | 类型 | 说明 |
| :---: | :---: | :---: |
| retTarget | PrunedAddr | 预测的返回地址，宽度VAddrBits |
| isCanUse | Bool | 预测是否有效，宽度1位 |


### **10.4  I/O 端口**
#### **MicroRAS模块的I/O**
| 端口名 | 方向 | 类型 | 说明 |
| :---: | :---: | :---: | :---: |
| stageCtrl | I | StageCtrl | 流水线阶段控制信号（包含s1_fire, s2_fire, s3_fire 等） |
| sramResetDone | O | Bool | SRAM 复位完成标志（固定为 true.B） |
| trainReady | O | Bool | 训练就绪标志（固定为true.B） |
| specIn | I | MicroRASSpecIn | S1 阶段的推测 CFI 信息 |
| specOut | O | MicroRASSpecOut | 预测输出 |
| hasRedirect | I | Bool | 全局重定向信号 |
| overrideData | I | Valid[MicroRASSpecIn] | S3 级别的覆盖数据 |
| fullRetAddr | I | PrunedAddr | 主RAS 当前的栈顶返回地址 |


#### **RAS模块的I/O**
| 端口名 | 方向 | 类型 | 说明 |
| :---: | :---: | :---: | :---: |
| stageCtrl | I | StageCtrl | 流水线阶段控制信号（包含s1_fire, s2_fire, s3_fire 等） |
| sramResetDone | O | Bool | SRAM 复位完成标志（固定为 true.B） |
| trainReady | O | Bool | 训练就绪标志（固定为true.B） |
| specIn | I | Valid[RASSpecInfo] | 推测阶段的 CFI 信息（有效时表示 S3 阶段的指令） |
| commit | I | Valid[BpuCommit] | 提交阶段的指令信息及RAS 元数据 |
| redirect | I | Valid[BpuRedirect] | 全局重定向信息（含正确的RAS 元数据和指令属性） |
| topRetAddr | O | PrunedAddr | 当前预测的栈顶返回地址 |
| redirectMeta | O | RASRedirectMeta | 重定向时需要的RAS 元数据（供外部保存） |
| commitMeta | O | RASCommitMeta | 提交时需要的RAS 元数据（供外部传递） |


#### **RASStack 模块的 I/O**
| 端口名 | 方向 | 类型 | 说明 |
| :---: | :---: | :---: | :---: |
| spec.fire | I | Bool | 推测阶段有效标志 |
| spec.pushValid | I | Bool | 推测阶段 push 有效 |
| spec.popValid | I | Bool | 推测阶段 pop 有效 |
| spec.pushAddr | I | PrunedAddr | 推测 push 的返回地址 |
| spec.popAddr | O | PrunedAddr | 推测 pop 输出的返回地址 |
| commit.valid | I | Bool | 提交阶段有效 |
| commit.pushValid | I | Bool | 提交阶段 push 有效 |
| commit.popValid | I | Bool | 提交阶段 pop 有效 |
| commit.pushAddr | I | PrunedAddr | 提交 push 的返回地址 |
| commit.metaTosw | I | RASPtr | 提交时传递的推测写指针 |
| commit.metaSsp | I | UInt | 提交时传递的提交栈指针 |
| redirect.valid | I | Bool | 全局重定向有效 |
| redirect.isCall | I | Bool | 重定向指令是否为 call |
| redirect.callAddr | I | PrunedAddr | 重定向的 call 地址 |
| redirect.isRet | I | Bool | 重定向指令是否为ret |
| redirect.meta | I | RASInternalMeta | 重定向时携带的完整RAS 元数据 |
| meta | O | RASInternalMeta | 当前RAS 内部元数据 |
| specNearOverflow | O | Bool | 推测队列接近溢出标志 |
| debug | O | RASDebug | 调试接口（含推测队列、提交栈、栈底指针等） |


-->
## 10. **RAS and uRAS**
RAS (Return Address Stack) predicts call and return targets. The main RAS consists of `RAS` and `RASStack`: a committed stack plus a speculative queue managed by `ssp`, `sctr`, `tosw`, `tosr`, and `bos`. It accepts speculative CFI operations, commit records, and redirects, and exposes the current top return address and recovery metadata. uRAS (MicroRAS) tracks pending S1-S3 calls and returns and provides a low-latency return prediction that cooperates with the main RAS.

### 10.1 **Overall Architecture and Pipeline**
On S1, the predictor classifies the CFI as call or return. A call computes `specPushAddr = startPc + cfiPosition * 2 + 2` and queues a push; a return queues a pop. uRAS captures the same operation. On S2, pending operations advance, and the main stack reads the speculative or committed entry selected by its pointers. On S3, uRAS combines S1-S3 pending operations, redirect state, and `fullRetAddr` to produce `topRetAddr` and `isCanUse`. A valid override discards S1/S2 pending operations and uses the S3 real operation; an override pop is conservatively marked unusable because the main RAS has not yet applied the pop.

### 10.2 **Redirect, Commit, and Protection**
Redirect has priority over local override. `RASInternalMeta` restores `tosw`, `tosr`, `ssp`, and `sctr`, then replays the redirected call or return. uRAS clears its pending S2/S3 operations and suppresses output for the redirect cycle and the delayed recovery cycle. Commit transfers confirmed operations from the speculative queue to the committed stack. Repeated identical calls are compressed with `ctr`; otherwise a new entry is pushed. Returns decrement the counter or pop the committed stack. `bos` tracks the speculative boundary, while `specNearOverflow` blocks pushes near capacity. RASStack also implements write bypass so an entry written in the current cycle can be read immediately.

### 10.3 **Key Data Structures**
`RASEntry` stores `retAddr` and a repetition counter `ctr`. `RASPtr` contains a wrap flag and a queue index. `RASInternalMeta` snapshots `ssp`, `sctr`, `tosw`, `tosr`, and `nos`; `RASRedirectMeta` adds `topRetAddr`; `RASCommitMeta` carries `ssp` and `tosw`. `RASSpecInfo` and `MicroRASSpecIn` describe the CFI attribute, position, and start PC. `MicroRASSpecOut` returns `retTarget` and `isCanUse`.

### 10.4 **I/O Ports**
MicroRAS inputs are `stageCtrl`, `specIn`, `hasRedirect`, `overrideData`, and `fullRetAddr`; it outputs `specOut`, `trainReady`, and `sramResetDone`. The main RAS accepts `specIn`, `commit`, and `redirect`, and outputs `topRetAddr`, `redirectMeta`, and `commitMeta`. RASStack exposes speculative push/pop, commit push/pop, redirect metadata, current metadata, `specNearOverflow`, and debug state.

<!--
## 11. **PHR 寄存器**
### **11.1 PHR 寄存器总体架构**
PHR（Path History Register，路径历史寄存器）是分支预测单元（BPU）中负责路径历史管理的核心组件。与记录全局方向历史（GHR）的CommonHR不同，PHR关注的是分支指令的PC（控制流指令地址）与分支目标地址之间的哈希关系，记录的是路径历史而非简单的taken/not-taken方向历史。

在 Kunminghu-v3 处理器的 BPU 顶层架构中，PHR 模块位于预测流水线的底层，为各级预测器提供折叠后的路径历史信息。如图 1 所示，PHR 模块接收来自 BPU 顶层的流水线控制信号和 FTQ 的训练接口信号，向 uTAGE（S1 阶段）、TAGE（S2 阶段）、SC 统计校正器（S2 阶段）以及 ITTAGE（S3 阶段）等子预测器输出不同阶段的折叠历史。



图1. PHR 模块在 BPU 中的位置与连接关系

PHR 模块的核心功能特性包括：（1）维护一个 532 位的循环缓冲区，通过 phr 寄存器与 phrPtr 指针协同管理路径历史；（2）支持推测更新与精确恢复的三级更新机制（Redirect > S3 Override > S1）；（3）为不同预测器生成多种规格的折叠历史（Folded History），满足 TAGE、ITTAGE、SC、uTAGE 等组件的差异化需求；（4）通过 PhrMeta 元数据实现预测时刻状态的快速保存与恢复，支撑推测执行环境下的历史回滚。

### **11.2 PHR 寄存器工作机制**
PHR寄存器的工作机制围绕预测流水线的阶段推进展开，涵盖S0至S3四个阶段的折叠历史传递，以及override、train/update、commit等关键处理流程。

**11.2.1 预测流水线与折叠历史传递**

PHR模块的预测流水线采用四级寄存器级联结构，折叠历史随BPU流水线逐级传递：S0阶段根据当前phr寄存器实时计算所有规格的折叠历史；S1/S2/S3阶段通过RegEnable寄存器逐级锁存前一阶段的折叠历史内容。各级预测器根据自身所处流水线阶段，从对应端口获取折叠历史信息。

| 流水线阶段 | 输出端口 | 使用者 | 用途 |
| :--- | :--- | :--- | :--- |
| S0 | s0_foldedPhr | uTAGE / TAGE / SC | S0阶段预测用折叠历史 |
| S1 | s1_foldedPhr | ITTAGE | S1阶段间接跳转预测用 |
| S2 | s2_foldedPhr | （内部传递） | S2级流水线锁存 |
| S3 | s3_foldedPhr | （内部传递） | S3级流水线锁存 |




uTAGE在S1阶段使用s0_foldedPhr进行条件分支的快速纠正；TAGE在S2阶段使用s0_foldedPhr进行精确方向预测；SC统计校正器在S2阶段使用s0_foldedPhr进行预测置信度评估；ITTAGE在S3阶段使用s1_foldedPhr进行间接跳转目标预测。

#### **11.2.2 三级更新优先级机制**
PHR采用推测更新机制，当S1、S3产生预测结果后即更新PHR中记录的分支历史。当FTQ发来Redirect信号时，根据重定向信号中的内容恢复分支历史。更新操作具有严格的优先级顺序（从高到低）：

| 优先级 | 触发条件 | 场景说明 |
| :--- | :--- | :--- |
| 1 (最高) | redirect.valid | 后端执行发现分支误预测，需要从错误点恢复历史 |
| 2 | s3_override | S3 精确预测与 S1 快速预测不一致，用 S3 结果修正 |
| 3 (最低) | s1_valid | S1 快速预测被确认，正常投机更新历史 |




第一优先级redirect处理分支误预测场景：当后端执行单元检测到分支方向或目标错误时，通过FTQ发送redirect信号，PHR模块根据phrMeta中保存的预测时刻指针与低位信息，将phr寄存器和phrPtr恢复至预测时的状态。若redirect伴随taken分支，则在恢复后的状态基础上追加新分支的路径哈希信息。

第二优先级s3_override处理S3阶段预测修正：当S3阶段TAGE/ITTAGE产生与S1阶段不同的预测结果时，使用s3_foldedPhrReg中锁存的S3预测起始状态恢复s0_foldedPhr，无需重新计算折叠历史。若修正后的分支为taken，则追加路径哈希更新。

第三优先级s1_valid处理正常投机更新：S1阶段预测确认后，使用s1_foldedPhrReg恢复s0_foldedPhr，并在taken条件下追加路径哈希。此为最常见的更新路径，发生在无预测冲突的正常执行流中。

#### **11.2.3 Train/Update 与 Commit 处理机制**
PHR模块维护两条训练相关通道：train通道和commit通道。train通道是核心功能通道，用于提供流水线控制信号和更新phr寄存器；commit通道是辅助验证通道，用于调试和训练时的历史一致性校验。

train通道通过PhrUpdate结构传递三级更新信号，包含stageCtr（流水线控制）、redirect（重定向）、s1_valid/s1_startPc（S1预测信息）、s3_override/s3_phrMeta（S3覆盖信息）等字段。commit通道通过BpuTrain结构传递FTQ的提交训练数据，包含startPc、branches（分支执行结果）、meta（含PhrMeta）等字段。

commit阶段的核心操作包括：（1）利用PhrMeta重建预测时刻的PHR状态，计算折叠历史供TAGE/SC等预测器训练使用；（2）执行commit-time check，验证"直接保存"与"恢复后计算"两种折叠历史获取路径的结果一致性。训练时使用的折叠历史必须与预测时刻的历史一致，以确保训练信息与预测上下文匹配。

### **11.3 PHR 寄存器内部关键结构**
PHR模块的内部结构围绕路径历史的存储、更新、恢复和折叠展开，核心包括参数配置、phr寄存器与指针、PhrMeta元数据、折叠历史寄存器以及路径哈希计算单元。

#### **11.3.1 核心参数配置**
PHR模块的参数配置决定了路径历史的存储容量、哈希精度和更新粒度。

| 参数 | 默认值 | 含义 |
| :--- | :--- | :--- |
| Shamt | 2 | 每次taken分支时PHR的移位量，即一次最多记录2位历史 |
| PathHashHighWidth | 13 | 路径哈希高位宽度，用于与phrLowBits异或更新 |
| PathHashWidth | 15 | 路径哈希的总位宽（PC[9:1]左移4位后与target[16:2]异或） |
| phrLowBits | 13 | 记录phrPtr指针向高位延伸的13位，即活跃历史区低位 |
| PhrHistoryLength | 532 | phr寄存器总长度，计算公式为((97+2×64+4+4-1)/4)×4 |
| EnableTwoTaken | false | 是否支持一个预测块内有两个taken分支（当前未启用） |
| HistoryAlign | 4 | 历史长度对齐值，方便十六进制显示 |




#### **11.3.2 phr 寄存器与 phrPtr 指针**
phr寄存器是一个532位宽的循环缓冲区，存储所有历史路径哈希的XOR累积结果。由于phrPtr指针采用循环队列机制，phr寄存器本质上是一个环形缓冲区：指针右侧（低位）为空闲/可覆盖区，左侧（高位）为更早历史。phrPtr指向当前历史位置的下一个位置，即新历史写入位置，也可理解为最老的历史边界。

phrPtr包含两个字段：flag用于区分队列是否绕了一圈（用于判断空/满状态），value为实际指针值，指向大小为PhrHistoryLength（532）的环形缓冲区。读取phr寄存器时，通过Cat(phr.asUInt, phr.asUInt)创建双倍长度向量，右移ptr.value+1位后截取低PhrHistoryLength位，结果中最低位为最新历史，最高位为最老历史。

#### **11.3.3 PhrMeta 元数据结构**
PhrMeta 是实现状态保存与快速恢复的核心。在分支预测开始时，一个 PhrMeta 快照会被创建并随流水线传递。其关键字段定义如下表所示。

| 字段名称 | 功能描述 |
| :--- | :--- |
| phrPtr | 记录预测时刻的 PHR 指针位置，用于重定向时定位历史数据。 |
| phrLowBits | 记录预测时刻 phr 寄存器的高位部分，用于重建完整历史。 |
| predFoldedHist | 调试模式下保存的预测时折叠历史快照，用于一致性检查。 |




#### **11.3.4 折叠历史寄存器（PhrAllFoldedHistories）**
折叠历史寄存器通过五个步骤构建：Step 1遍历所有预测器（TAGE、ITTAGE、SC、uTAGE）的表配置，提取每个表所需的折叠历史规格，合并为去重后的Set；Step 2将Set转换为Seq并按(HistoryLength, FoldedLength)升序排序；Step 3为每个规格实例化PhrFoldedHistory；Step 4利用MixedVec打包所有实例（支持向量元素位宽不同）；Step 5形成最终的Bundle结构。

折叠历史的计算采用异或折叠方式：将全局历史按FoldedLength分组，每组内各位异或压缩为1位。例如，15位全局历史压缩为6位折叠历史时，h[0]与h[6]、h[12]异或得到结果第0位，h[1]与h[7]、h[13]异或得到结果第1位，以此类推。

#### **11.3.5 路径哈希计算**
路径哈希是PHR更新的核心运算，将分支指令PC与目标地址映射为固定宽度的哈希值。哈希计算过程：取PC的[9:1]位（9位），左移4位补零扩展为13位；与目标地址的[16:2]位（15位）进行异或运算；截取低15位作为最终哈希值。哈希值分为两部分：低Shamt位（2位）作为shiftBits移入PHR循环缓冲区；高位（13位）作为hashHigh与PHR高位进行异或更新。

### **11.4 PHR 寄存器端口定义**
PHR模块的I/O端口分为输入信号、输出信号两类。输入包括来自BPU顶层的train控制信号和来自FTQ的commit训练信号；输出包括各级流水线的折叠历史和训练用折叠历史。

#### **11.4.1 输入端口**
| 信号名称 | 含义 |
| :--- | :--- |
| train | 来自 BPU 顶层的流水线更新信号，用于投机地更新 PHR 状态。 |
| train.s0_stall | S0 阶段阻塞信号。 |
| train.stageCtrl | 携带 s0_fire, s1_fire, s2_fire, s3_fire 等流水线控制信号。 |
| train.redirectValid | 后端重定向有效信号，携带重定向信息，触发 PHR 状态恢复。 |
| train.s1_valid | S1 阶段预测有效信号。 |
| train.s1_prediction | S1 阶段的预测结果 Bundle。 |
| train.s3_override | S3 阶段覆盖 S1 预测的指示信号。 |
| train.s3_phrMeta | 随 S3 预测传递的 PHR 元数据，用于状态恢复。 |
| commit | 来自 FTQ 的训练接口，用于提供非投机的训练数据和调试信息。 |
| commit.bits.meta.phr | 提交时用于恢复预测时刻历史的 PHR 元数据。 |




#### **11.4.2  输出端口**
| 信号 | 含义 |
| :--- | :--- |
| s0_foldedPhr | S0 阶段使用的折叠历史（供预测器使用） |
| s1_foldedPhr | S1 级流水线锁存的折叠历史 |
| s2_foldedPhr | S2 级流水线锁存的折叠历史 |
| s3_foldedPhr | S3 级流水线锁存的折叠历史 |
| phr | 原始 PHR 寄存器（位向量形式） |
| phrMeta | 元数据（包含 phrPtr 和低位信息），用于恢复 |
| trainFoldedPhr | 训练时使用的折叠历史 |


-->
## 11. **PHR Register**
PHR (Path History Register) records path hashes derived from branch PCs and targets, unlike CommonHR's taken/not-taken global history. In Kunminghu-v3 it supplies folded histories to uTAGE, TAGE, SC, and ITTAGE. A 532-bit circular `phr` buffer and `phrPtr` maintain the history. Updates follow `redirect > s3_override > s1_valid`, and `PhrMeta` snapshots permit exact recovery.

### 11.1 **Pipeline and Update Rules**
S0 computes all configured folded histories from the current PHR; S1, S2, and S3 latch them through `RegEnable` registers. uTAGE/TAGE/SC consume `s0_foldedPhr`, while ITTAGE consumes `s1_foldedPhr`. A redirect restores the saved pointer and low PHR bits and then appends the redirected taken path when applicable. An S3 override restores the S3 snapshot and applies the corrected path; ordinary S1 confirmation performs the speculative update. The train path carries `PhrUpdate` control and prediction metadata, while commit carries FTQ training data and checks that reconstructed folded histories match the prediction-time state.

### 11.2 **Storage, Folding, and Hashing**
Key parameters include `Shamt = 2`, `PathHashHighWidth = 13`, `PathHashWidth = 15`, `phrLowBits = 13`, and `PhrHistoryLength = 532`. `phrPtr` identifies the oldest-history boundary in the circular buffer; reading uses a doubled vector (`Cat(phr, phr)`) shifted by the pointer. `PhrMeta` stores `phrPtr`, the saved high/low bits, and optional folded-history snapshots. `PhrAllFoldedHistories` deduplicates the requested `(HistoryLength, FoldedLength)` configurations and instantiates one folded-history register per configuration. The path hash XORs `PC[9:1] << 4` with `target[16:2]`; the low two bits shift into the circular buffer and the upper 13 bits update the stored history.

### 11.3 **I/O Ports**
Inputs include speculative `train`, stage fire/stall controls, redirect information, S1 prediction, S3 override metadata, and FTQ `commit`. Outputs include `s0_foldedPhr` through `s3_foldedPhr`, raw `phr`, `phrMeta`, and `trainFoldedPhr`.

<!--
## 12. **CommonHR 寄存器**
### **12.1 模块定位与核心职责**
CommonHR（Common History Register）是香山 Kunminghu-v3 处理器 BPU（Branch Prediction Unit）中的公共历史寄存器管理模块，负责维护全局分支历史信息，为 TAGE、SC、ITTAGE 等高级预测器提供统一的分支历史数据源。作为 BPU 顶层的关键基础设施，CommonHR 在分支预测流水线的推测执行与精确恢复之间承担着核心桥梁作用。

CommonHR 的核心职责可归纳为以下四个方面：

1）维护全局分支历史（GHR）：记录条件分支的 taken/not-taken 历史序列，位宽由 SC 的全局表最大历史长度决定（GhrHistoryLength）。GHR 是 TAGE-SC 预测器进行历史索引的核心依据，直接影响条件分支方向的预测精度。

2）维护后退历史（BW History）：专门记录后向跳转条件分支的历史，位宽由 SC 的后向表最大历史长度决定（BWHistoryLength）。后向跳转通常对应循环结构，具有特殊的预测价值，BW History 为 SC 的后向表提供独立的索引历史。

3）管理 IMLI 计数器：记录最近连续后向跳转次数（饱和计数），用于识别循环迭代模式，供 SC 的 IMLI 表索引。IMLI（Iteration Miss-predict Loop Indicator）计数器能够有效捕捉循环边界处的分支行为特征，提升循环密集型工作负载的预测准确率。

4）支持流水线推测与恢复：通过 histQueue 循环队列缓存不同流水级的历史快照，支持预测错误时的精确状态回滚。该机制确保了在 BPU 发生重定向（redirect）或 S3 阶段覆盖（override）时，能够快速恢复至正确的历史状态，避免错误推测路径对后续预测造成持续污染。

### **12.2 CommonHR 工作机制**
CommonHR 的工作机制围绕预测流水线展开，涵盖 S0~S3 四个流水级的历史读取、传递、更新与恢复。同时，模块支持三种特殊处理机制：S3 覆盖（override）、重定向恢复（redirect）以及 IMLI 计数器的独立更新。

#### **12.2.1 预测流水线与历史传递**
CommonHR 的预测流水线与 BPU 顶层四级流水严格同步，通过 stageCtrl 接口接收 s0_fire、s1_fire、s2_fire、s3_fire 控制信号。历史寄存器在流水线中的传递路径如下：

1. S0 阶段（预测读取）：根据当前流水线状态，通过四级优先级选择逻辑（MuxCase）确定 s0_commonHR 的输出值。选择优先级从高到低依次为：重定向有效（r0_valid）→ S3 覆盖有效（s3_override）→ 队列同步旁路（sync）→ 正常队列读取（histQueue(predPtr)）。s0_imli 同步输出当前 IMLI 计数器值。

2. S1~S2 阶段（流水传递）：s0_commonHR/s0_imli 随流水级逐级寄存为 s1_commonHR/s1_imli、s2_commonHR/s2_imli、s3_commonHR/s3_imli。每一级的历史快照均与对应预测块的 PC 绑定，确保后续恢复时可精确定位。

3. S3 阶段（解析更新）：当 s3_fire 有效时，模块根据 io.update 提供的最终分支信息（taken、condHitMask、position 等）计算历史移位量，生成 s3_newCommonHR 并写入 commonHR 寄存器，同时更新 histQueue 中对应条目。

#### **12.2.2 S3 覆盖（Override）机制**
重定向机制在分支预测错误或异常发生时执行历史状态的精确回滚。当 redirect.valid 有效时，模块执行以下步骤：

1. 提取重定向信息：从 redirect.meta 中恢复预测时的历史快照（ghr、bw、imli）、分支命中掩码（hitMask）、分支属性（attribute）及位置信息（position）。

2. 计算恢复参数：重建条件分支命中掩码并去重，统计位于重定向前方的分支数 numLess 及总命中数 numHit。

3. 生成恢复历史：以元数据中的 ghr、bw 为基础，调用 getNewHR 分别生成恢复后的 GHR 与 BW 历史。

4. 执行恢复：r0_valid 有效时将恢复历史写入 commonHR 寄存器，同时重置队列指针至 writePtr，并将恢复历史存入对应队列位置，确保后续预测使用正确状态。

重定向恢复的精度依赖于预测时保存的 CommonHRMeta 元数据快照。该快照包含了预测时刻的完整历史状态与分支信息，使得即使在多分支折叠的复杂场景下，也能准确计算出回滚后的历史值。

#### **12.2.3 IMLI 计数器更新机制**
IMLI（Iteration Mean Loop Iteration）计数器用于记录连续后向条件跳转次数，其更新遵循统一规则，按优先级响应以下事件：

1. 重定向时（redirect.valid）：若重定向分支为后向条件跳转且 Taken，则 IMLI 饱和递增（全 1 时不变）；否则清零。

2. S3 覆盖时（s3_override）：若覆盖分支为后向条件跳转且 Taken，则 IMLI 饱和递增；否则清零。

3. S1 流水级有效时（s1_fire）：若 s1_imliTaken 为真（S1 阶段检测到后向条件跳转且 Taken），则 IMLI 饱和递增；否则清零。

4. 无上述事件时：imli 寄存器保持原值，s0_imli 输出该寄存器值。

IMLI 的更新与 s0_imli 输出同步驱动，确保 SC 预测器在 S0 阶段获取的 IMLI 值始终反映最新的循环迭代上下文。

### **12.3 核心数据结构与参数**
CommonHR 模块的设计依赖于一系列精心定义的参数和结构化数据类型。

#### **12.3.1 关键配置参数**
| 参数名 | 来源/决定因素 | 说明 |
| :--- | :--- | :--- |
| GhrHistoryLength | SC 的 GlobalTableInfos 中最大 HistoryLength | SC 全局表所需的历史位数，决定了 GHR 的宽度。 |
| BWhistoryLength | SC 的 BackwardTableInfos 中最大 HistoryLength | SC 后向表所需的历史位数，决定了 BW 历史的宽度。 |
| NumBtbResultEntries | mBTB 的路数 NumWay × NumAlignBanks | 一个预测块内最多可检测到的分支数量，决定了历史移位计算的上限。 |
| CfiPositionWidth | log2Ceil(FetchBlockInstNum) | 取指块中指令数量的对数，用于表示控制流指令位置的位宽。 |
| HistQueueSize | CommonHRParameters（默认为 8） | 历史队列的深度，决定了流水线可容忍的最大推测深度。 |
| ImliWidth | CommonHRParameters（默认为 8） | IMLI 计数器的位宽，用于饱和计数。 |




#### **12.3.2  历史寄存器条目（CommonHREntry）**
| 字段名 | 含义 |
| :--- | :--- |
| valid | 条目有效性标志 |
| ghr | 全局历史寄存器，记录条件分支 taken/not-taken 的历史序列 |
| bw | 后向跳转历史，记录后向分支跳转的特殊历史 |
| predStartPc | 对应预测块的起始 PC（调试用） |




其中，GHR（Global History Register）记录所有条件分支的 taken/not-taken 结果，用于 TAGE、SC 等预测器的历史索引；BW History 专门记录后向跳转分支的历史，后向跳转通常对应循环结构，具有特殊的预测价值。两者在物理上分离但逻辑上协同，共同构成 SC 预测器的复合历史索引。

#### **12.3.3 历史队列（histQueue）与指针体系**
CommonHR 内部维护一个深度为 HistQueueSize（默认 8）的循环队列 histQueue，每个条目为 CommonHREntry 类型。四个 HistPtr 指针协同管理队列的读写与恢复：

| 指针 | 功能 | 更新时机 |
| :--- | :--- | :--- |
| enqPtr | 入队指针，指向下一个空白历史的写入位置 | s0_fire 有效时递增 |
| predPtr | 预测读取指针，指向当前 S0 阶段应使用的历史 | s0_fire 且满足 predEnable 时递增 |
| writePtr | 写入指针，指向 S3 阶段应更新的历史条目 | s3_fire 有效时递增 |
| recoverPtr | 恢复指针，记录流水线中已知正确的最老历史位置 | 在 s3_fire 且满足 recoverInc 时递增；重定向或覆盖时跳变 |




#### **12.3.4 CommonHRUpdate（更新信息）**
CommonHRUpdate Bundle 承载了从 S3 阶段发送过来的分支解析结果，用于更新历史。

| 字段名 | 说明 |
| :--- | :--- |
| taken | S3 阶段确认的最终跳转方向（指预测块内第一条Taken分支的方向）。 |
| s3Override | S3 覆盖标志，表示实际存在分支但预测阶段未识别。 |
| condHitMask | 预测块内检测到的条件分支命中掩码。 |
| position | 每个条件分支在取指块内的偏移量。 |
| firstTakenBranch | 预测块内第一条 Taken 分支的完整信息（目标地址、属性等）。 |
| startPc | 预测块的起始 PC。 |
| target | 最终跳转目标地址，用于判断是否为后向跳转。 |




#### **12.3.5 CommonHRRedirect（重定向信息）**
CommonHRRedirect Bundle 包含了重定向请求所需的所有信息，用于在预测失败时恢复历史状态。

| 字段名 | 说明 |
| :--- | :--- |
| valid | 重定向请求有效标志。 |
| cfiPc | 实际跳转分支的 PC 地址。 |
| taken | 实际跳转方向。 |
| attribute | 分支属性（条件/间接/返回/调用等）。 |
| target | 实际跳转目标。 |
| meta | 预测时保存的元数据快照（CommonHRMeta 类型），用于恢复历史状态。 |




#### **12.3.6 CommonHRMeta（重定向元数据）**
此 Bundle 是重定向机制的核心，它保存了预测时流水线的相关参数。

| 字段名 | 说明 |
| :--- | :--- |
| ghr、bw、imli | 预测时的 GHR、BW 历史和 IMLI 计数器值。 |
| hitMask | 预测时检测到的分支命中掩码。 |
| attribute | 预测块内所有命中分支的属性序列。 |
| position | 预取块内所有命中分支的位置序列。 |


### **12.4 CommonHR 端口定义**
#### **12.4.1 输入端口**
| 端口名 | 方向 | 来源/目的地 | 功能描述 |
| :--- | :--- | :--- | :--- |
| stageCtrl | Input | BPU 顶层 | 流水线控制信号，包含 s0_fire、s1_fire 等，用于同步流水级寄存器与队列操作。 |
| s1_imliTaken | Input | BPU 顶层 | S1 阶段检测到后向条件跳转且 Taken 时置位，用于提前更新 IMLI 计数器。 |
| update | Input | BPU 顶层 (S3) | S3 阶段解析完成的更新信息，用于正常推测路径上的历史更新。 |
| redirect | Input | FTQ（重定向） | 分支预测错误或异常时的恢复请求，携带正确分支信息及预测元数据。 |
| s0_startPc | Input | BPU 顶层 | 当前预测块的起始 PC，仅用于调试，写入 histQueue 以供一致性检查。 |




#### **12.4.2 输出端口**
| 端口名 | 方向 | 来源/目的地 | 功能描述 |
| :--- | :--- | :--- | :--- |
| s0_imli | Output | SC 预测器 | 当前 IMLI 计数器值，为 SC 的 IMLI 表提供索引。 |
| s0_commonHR | Output | SC 预测器 | 当前历史寄存器内容（ghr + bw），供 SC 的全局历史表索引。 |
| s3ResolveMeta | Output | FTQ / 训练逻辑 | S3 预测完成时的历史状态元数据快照，用于后续的重定向恢复和预测器训练。 |
-->
## 12. **CommonHR Register**
CommonHR (Common History Register) is the shared history source for the Kunminghu-v3 BPU. It maintains the global taken/not-taken history (`GHR`), a separate backward-branch history (`BW`), and the saturating IMLI loop-iteration counter. A circular `histQueue` stores per-prediction snapshots so speculative updates can be recovered precisely on redirects or S3 overrides.

### 12.1 **Pipeline and Recovery**
At S0, `s0_commonHR` is selected by priority: redirect recovery, S3 override, synchronization bypass, then normal `histQueue(predPtr)` access. `s0_imli` exposes the current IMLI value. Histories are registered through S1-S3. At S3, `CommonHRUpdate` describes the resolved taken branch, hit mask, positions, and target; the module computes the shift and writes the new GHR/BW state and queue entry. Redirect reconstructs the prediction-time GHR, BW, and IMLI from `CommonHRMeta`, recomputes the history before and after the redirected CFI, and resets the queue pointers. IMLI increments on a taken backward conditional branch (with saturation) and clears on other resolved events; redirect has priority over override, which has priority over S1 update.

### 12.2 **Key Structures and Parameters**
`GhrHistoryLength` and `BWhistoryLength` derive from the largest SC table histories. `NumBtbResultEntries` bounds the number of branches in a fetch block, `HistQueueSize` defaults to 8, and `ImliWidth` defaults to 8. `CommonHREntry` contains `valid`, `ghr`, `bw`, and `predStartPc`. The queue uses `enqPtr`, `predPtr`, `writePtr`, and `recoverPtr` for enqueue, prediction, S3 update, and recovery. `CommonHRUpdate` carries the resolved branch direction, override flag, conditional hit mask, positions, first taken branch, start PC, and target. `CommonHRRedirect` carries the actual CFI and `CommonHRMeta`; the metadata snapshots GHR, BW, IMLI, hit masks, attributes, and positions.

### 12.3 **I/O Ports**
Inputs are `stageCtrl`, `s1_imliTaken`, S3 `update`, `redirect`, and `s0_startPc`. Outputs are `s0_imli` for SC's IMLI table, `s0_commonHR` for SC global-history indexing, and `s3ResolveMeta` for FTQ recovery and predictor training.
