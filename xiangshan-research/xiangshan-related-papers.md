# 香山相关论文汇总

整理日期：2026-08-25  
来源文件：`paper.md`、`papers.md`  
整理口径：合并两个文件中的论文条目，按年份从早到晚排序；对重复论文去重，并将 fuzz 相关补充信息合并到对应条目中。明确以 XiangShan/香山作为平台、案例、实现对象或官方列为 Research on XiangShan 的论文列入主表；未确认以香山为平台的通用 RISC-V/CPU fuzz 工作放入背景参考。

## 分类统计

| 主分类 | 数量 | 说明 |
| --- | ---: | --- |
| 设计性能优化 | 14 | 香山架构设计、微架构优化、DSE、功耗建模、内存/加速器机制等 |
| 验证工具 | 15 | 功能验证、DFT、RTL 仿真加速、硬件加速验证、ATPG、FPGA 仿真/重放、fuzz 等 |
| 安全 | 6 | TEE 漏洞发现、Spectre V1 安全评估、缓存/竞争侧信道等 |
| 敏捷工具/方法 | 2 | 香山敏捷开发方法论、面向设计/验证/调试/评估的工具链实践 |
| 合计 | 37 | 不含教程、报告、未能确认正式发表的海报/预印本；不含背景参考论文 |

## 论文明细（按时间顺序）

| 年份 | 论文 | venue/状态 | 主分类 | 标签 | 与香山的关系 |
| --- | --- | --- | --- | --- | --- |
| 2022 | [Towards Developing High Performance RISC-V Processors Using Agile Methodology](https://doi.org/10.1109/MICRO56248.2022.00080) | MICRO 2022；IEEE Micro Top Pick 2023；DOI: https://doi.org/10.1109/MICRO56248.2022.00080 | 敏捷工具/方法 | 设计、验证、调试、性能评估 | 介绍香山处理器及其高性能 RISC-V 处理器敏捷开发实践；官方页面称覆盖设计、功能验证、调试、性能验证等工具。 |
| 2023 | [XiangShan Open-Source High Performance RISC-V Processor Design and Implementation / 香山开源高性能 RISC-V 处理器设计与实现](https://doi.org/10.7544/issn1000-1239.202221036) | Journal of Computer Research and Development 2023, 60(3): 476-493；DOI: https://doi.org/10.7544/issn1000-1239.202221036 | 设计性能优化 | 开源处理器、乱序执行、实现 | 系统介绍香山开源高性能 RISC-V 处理器的设计与实现。 |
| 2023 | [Functional Verification for Agile Processor Development: A Case for Workflow Integration](https://doi.org/10.1007/s11390-023-3285-8) | JCST 2023, 38(4): 737-754；DOI: https://doi.org/10.1007/s11390-023-3285-8 | 验证工具 | 敏捷验证、流程集成、覆盖率引导 fuzz、DiffTest、XFUZZ | 以 NutShell 和 XiangShan RISC-V 处理器评估整体流程，并用香山 L2 cache 功能 bug 作为案例；XFUZZ 是 DiffTest 集成的测试生成插件，负责基于设计覆盖率生成测试输入，DiffTest 负责与参考模型协同仿真并定位不一致。 |
| 2023 | [Structured DFT Development Approach for Chisel-Based High Performance RISC-V Processors](https://doi.org/10.1109/ITC-Asia58802.2023.10301174) | ITC-Asia 2023；DOI: https://doi.org/10.1109/ITC-Asia58802.2023.10301174 | 验证工具 | DFT、Chisel、可测性设计 | 面向基于 Chisel 的高性能 RISC-V 处理器，官方列为香山发表论文。 |
| 2023 | [Imprecise Store Exceptions](https://doi.org/10.1145/3579371.3589087) | ISCA 2023 | 设计性能优化 | 存储异常、体系结构 | 香山官方将其列为 Research on XiangShan；公开仓库说明使用 XiangShan over FireSim 作为论文基础设施。 |
| 2023 | [TEESec: Pre-Silicon Vulnerability Discovery for Trusted Execution Environments](https://doi.org/10.1145/3579371.3589070) | ISCA 2023 | 安全 | TEE、预硅漏洞发现 | 香山官方列为使用 XiangShan 作为评估平台的研究。 |
| 2023 | [Fast, Robust and Transferable Prediction for Hardware Logic Synthesis](https://doi.org/10.1145/3613424.3623794) | MICRO 2023 | 敏捷工具/方法 | 逻辑综合预测、EDA、迁移学习 | 香山官方列为 Research on XiangShan；可归入设计自动化/敏捷工具支撑。 |
| 2023 | [Khronos: Fusing Memory Access for Improved Hardware RTL Simulation](https://doi.org/10.1145/3613424.3614301) | MICRO 2023 | 验证工具 | RTL 仿真加速、内存访问融合 | 香山官方列为 Research on XiangShan；用于改进硬件 RTL 仿真效率。 |
| 2023 | [A Transfer Learning Framework for High-Accurate Cross-Workload Design Space Exploration of CPU](https://doi.org/10.1109/ICCAD57390.2023.10323840) | ICCAD 2023 | 设计性能优化 | DSE、迁移学习、跨负载预测 | 香山官方列为 Research on XiangShan；面向 CPU 设计空间探索。 |
| 2023 | [A Distributed ATPG System Combining Test Compaction Based on Pure MaxSAT](https://doi.org/10.1109/ATS59501.2023.10317948) | ATS 2023 | 验证工具 | ATPG、测试压缩、MaxSAT | 香山官方列为 Research on XiangShan；属于测试生成与验证基础设施。 |
| 2023 | [REMU: Enabling Cost-Effective Checkpointing and Deterministic Replay in FPGA-based Emulation](https://doi.org/10.1109/ICCD58817.2023.00014) | ICCD 2023 | 验证工具 | FPGA 仿真、检查点、确定性重放 | 香山官方列为 Research on XiangShan；用于 FPGA-based emulation 的检查点与重放。 |
| 2024 | [PathFuzz: Broadening Fuzzing Horizons with Footprint Memory for CPUs](https://doi.org/10.1145/3649329.3655911) | DAC 2024；DOI: https://doi.org/10.1145/3649329.3655911 | 验证工具 | 处理器 fuzz、footprint memory、路径探索、OpenXiangShan/xfuzz | OpenXiangShan/xfuzz README 将 PathFuzz 列为采用该开源工具的研究；针对处理器 fuzz 中覆盖率代理与输入探索受限的问题，引入 footprint memory 思路拓展路径探索空间。 |
| 2024 | [XiangShan: An Open-Source Project for High-Performance RISC-V Processors Meeting Industrial-Grade Standards](https://doi.org/10.1109/HCS61935.2024.10665293) | Hot Chips 36, 2024；DOI: https://doi.org/10.1109/HCS61935.2024.10665293 | 设计性能优化 | 工业级开源处理器、协同开发 | 官方页面称该文介绍香山作为高性能 RISC-V 开源项目，并采用与产业伙伴协同的设计、实现和验证模式。 |
| 2025 | [DiffTest-H: Toward Semantic-Aware Communication in Hardware-Accelerated Processor Verification](https://doi.org/10.1145/3725843.3756108) | MICRO 2025；DOI: https://doi.org/10.1145/3725843.3756108 | 验证工具 | 硬件加速协同仿真、语义感知通信、DiffTest | 官方页面称 DiffTest-H 已开源并部署在香山验证流程中。 |
| 2025 | [GSIM: Accelerating RTL Simulation for Large-Scale Designs](https://doi.org/10.1109/DAC63849.2025.11133142) | DAC 2025；DOI: https://doi.org/10.1109/DAC63849.2025.11133142 | 验证工具 | RTL 仿真加速、大规模设计 | 官方列为香山发表论文，主题为大规模设计 RTL 仿真加速。 |
| 2025 | [Asynchronous Memory Access Unit: Exploiting Massive Parallelism for Far Memory Access](https://doi.org/10.1145/3663479) | ACM TACO | 设计性能优化 | 远端内存、异步访存、并行性 | 香山官方列为 Research on XiangShan；面向远端内存访问的微架构机制。 |
| 2025 | [Single-Address-Space FaaS with Jord](https://doi.org/10.1145/3695053.3731108) | ISCA 2025 | 设计性能优化 | FaaS、单地址空间、系统架构 | 香山官方列为 Research on XiangShan；以香山作为评估基础之一。 |
| 2025 | [FirePower: Towards a Foundation with Generalizable Knowledge for Architecture-Level Power Modeling](https://doi.org/10.1145/3658617.3697554) | ASP-DAC 2025 | 设计性能优化 | 体系结构级功耗建模、性能/功耗评估 | 香山官方列为 Research on XiangShan；面向架构级功耗建模。 |
| 2025 | [CoroAMU: Unleashing Memory-Driven Coroutines through Latency-Aware Decoupled Operations](https://doi.org/10.1109/PACT65351.2025.00046) | PACT 2025；DOI: https://doi.org/10.1109/PACT65351.2025.00046 | 设计性能优化 | 协程、解耦访存、FPGA 原型 | 官方列为 Research on XiangShan；arXiv 页面说明其用 LLVM 和开源 XiangShan RISC-V 处理器在 FPGA 平台实现。 |
| 2025 | [DiveFuzz: Enhancing CPU Fuzzing via Diverse Instruction Construction](https://doi.org/10.1145/3719027.3765167) | ACM CCS 2025: 1964-1978；DOI: https://doi.org/10.1145/3719027.3765167 | 验证工具 | CPU fuzz、指令构造、write-back 数据多样性、opcode 分布调节 | ACM 页面明确说明在 XiangShan、CVA6、Rocket 和 NutShell 四个开源 RISC-V CPU 上评估；发现 26 个新 bug，其中 15 个有 CVE 标识。 |
| 2025 | [DejaVuzz: Disclosing Transient Execution Bugs with Dynamic Swappable Memory and Differential Information Flow Tracking Assisted Processor Fuzzing](https://doi.org/10.1145/3676642.3736115) | ASPLOS 2025, Volume 3: 64-80；DOI: https://doi.org/10.1145/3676642.3736115 | 安全 | transient execution、处理器 fuzz、dynamic swappable memory、differential information flow tracking | 论文 artifact 和作者页面说明 DejaVuzz 在 BOOM 与 XiangShan 两个乱序 RISC-V 处理器上评估，并发现 5 个此前未知的瞬态执行漏洞变体。 |
| 2025 | [GhostCache: Timer- and Counter-Free Cache Attacks Exploiting Weak Coherence on RISC-V and ARM Chips](https://doi.org/10.1145/3719027.3744833) | ACM CCS 2025: 3795-3809；DOI: https://doi.org/10.1145/3719027.3744833 | 安全 | 免计时器缓存攻击、弱一致性、L1I cache、RISC-V/ARM | 论文/项目页面说明 GhostCache 影响 6 款商用和 3 款开源 RISC 处理器；公开报道列出的开源 RISC-V 处理器包括 Rocket-Chip、SonicBOOM 和 XiangShan，并提到香山团队确认/致谢。 |
| 2025 | [Sonar: A Hardware Fuzzing Framework to Uncover Contention Side Channels in Processors](https://doi.org/10.1145/3725843.3756136) | MICRO 2025: 125-139；DOI: https://doi.org/10.1145/3725843.3756136 | 安全 | 硬件 fuzz、contention side channel、处理器安全 | MICRO 2025/DBLP 记录确认该论文；当前公开记录显示其为处理器竞争侧信道 fuzz 框架，尚未在摘要级信息中确认 XiangShan 是否为评估对象，待全文核对。 |
| 2026 | [SimFuzz: Similarity-guided Block-level Mutation for RISC-V Processor Fuzzing](https://arxiv.org/abs/2601.11838) | arXiv:2601.11838，提交于 2026-01-17 | 验证工具 | RISC-V 处理器 fuzz、块级变异、历史 bug 输入、指令相似性 | arXiv 摘要明确说明在 Rocket、BOOM 和 XiangShan 三个开源 RISC-V 处理器上评估；论文报告共发现 17 个 bug，其中 14 个此前未知，7 个获得 CVE 编号。 |
| 2026 | [TraceRTL: Hardware-Accelerated RTL Simulation with Tailored Trace Generation](https://2026.hpca-conf.org/details/hpca-2026-main-conference/27/TraceRTL-Agile-Performance-Evaluation-for-Microarchitecture-Exploration) | HPCA 2026 | 设计性能优化 | RTL 仿真、硬件加速、trace generation、处理器评估 | 2026 年公开论文记录中包含 XiangShan；属于面向复杂 RTL/处理器仿真的 trace 生成与加速方向，待补充 DOI 和更细评估配置。 |
| 2026 | [HartBreaker](https://doi.org/10.5281/zenodo.19417381) | ISCA 2026 | 安全 | 处理器安全、漏洞发现、RISC-V、硬件安全 | 用户指定的 ISCA 论文；检索到 HartBreaker/HartBreak 与 ISCA 2026、XiangShan 相关线索，题名、页码和 DOI 待补充核验。 |
| 2026 | [Democratizing and Accelerating Hardware Verification with Software-Native Optimization](https://doi.org/10.1109/ISCA66397.2026.00154) | ISCA 2026: 2173-2188；DOI: https://doi.org/10.1109/ISCA66397.2026.00154 | 验证工具 | UCV、UCAgent、software-native verification、XiangShan、RocketChip | 论文提出 UnityChip Verification (UCV) 软件原生硬件验证平台；ISCA 2026 会议页面和论文摘要显示评估对象包括 XiangShan 和 RocketChip，并与 UCAgent/万众一芯验证生态相关。 |
| 2026 | [TurboFuzz: FPGA Accelerated Hardware Fuzzing for Processor Agile Verification](https://arxiv.org/abs/2509.10400) | HPCA 2026 | 验证工具 | FPGA 加速、硬件 fuzz、处理器敏捷验证 | 2026 年处理器 fuzz/香山验证方向论文；公开题名显示面向 processor agile verification，是否在正文明确评估 XiangShan 及具体配置待全文核验。 |
| 2026 | [Lyra: A Hardware-Accelerated RISC-V Verification Framework with Generative Model-Based Processor Fuzzing](https://arxiv.org/abs/2512.13686) | DAC 2026 | 验证工具 | 硬件加速验证、生成式模型、处理器 fuzz、RISC-V | 2026 年 RISC-V 处理器验证/fuzz 方向论文；是否在正文明确包含 XiangShan 待全文核验。 |
| 2026 | [How Secure is a High-Performance RISC-V Core? A Spectre V1 Case Study on XiangShan Open-Source CPU](https://doi.org/10.1145/3803525.3804986) | EuroSec 2026；DOI: https://doi.org/10.1145/3803525.3804986 | 安全 | Spectre V1、投机执行、侧信道 | DBLP 记录为 EuroSec 2026 论文；公开代码仓库说明测试 XiangShan V2 Nanhu 与 V3 Kunminghu 的 Spectre V1。 |
| 2026 | [ReadyPower: A Reliable, Interpretable, and Handy Architectural Power Model Based on Analytical Framework](https://doi.org/10.1109/ASP-DAC66049.2026.11420445) | ASP-DAC 2026；DOI: https://doi.org/10.1109/ASP-DAC66049.2026.11420445 | 设计性能优化 | 功耗模型、架构级建模 | 论文页面摘要说明在 BOOM 与 XiangShan CPU 架构上评估功耗模型。 |
| 2026 | [BigPower: A Modularized and Efficient Architecture-Level Power Modeling Framework for Complex Out-of-Order Cores](https://arxiv.org/abs/2606.13747) | ASP-DAC 2026 | 设计性能优化 | 架构级功耗模型、复杂乱序核、模块化建模 | 2026 年公开论文记录包含 XiangShan；面向复杂乱序核的架构级功耗建模，与 ReadyPower/FirePower 同属香山相关功耗建模脉络。 |
| 2026 | [Chat-A2: An LLM-aided Design Space Exploration Framework for High-Performance CPU Design](https://doi.org/10.1109/ASP-DAC66049.2026.11420663) | ASP-DAC 2026: 540-546；DOI: https://doi.org/10.1109/ASP-DAC66049.2026.11420663 | 设计性能优化 | LLM、RTL 到周期级建模、性能评估、跨抽象层 | 2026 年公开论文记录包含 XiangShan；面向高性能 CPU 的 LLM 辅助设计空间探索；DBLP/论文记录显示在 XiangShan CPU 上评估。 |
| 2026 | [Mini-MDP: Revisiting the Accuracy-Complexity Tradeoff in Memory Dependence Prediction](https://doi.org/10.1016/j.sysarc.2026.103925) | Journal of Systems Architecture 2026；DOI: https://doi.org/10.1016/j.sysarc.2026.103925 | 设计性能优化 | 内存依赖预测、面积/功耗/IPC 权衡 | 期刊页面说明 Mini-MDP 在 XiangShan Nanhu 乱序 RISC-V 处理器上实现并综合。 |
| 2026 | [AExec: Asynchronous Multi-accelerator Execution and Management Mechanism](https://doi.org/10.1145/3801487.3801807) | ACM Computing Frontiers 2026；DOI: https://doi.org/10.1145/3801487.3801807 | 设计性能优化 | 多加速器、异步执行、硬件软件协同 | ACM 页面说明 xAExec 在 XiangShan 处理器 RTL 级实现。 |
| 2026 | [ModFuzz: Adaptive Module-Level Fuzzing of Processors](https://doi.org/10.1109/TIFS.2026.3674691) | IEEE Transactions on Information Forensics and Security 21: 3463-3478；DOI: https://doi.org/10.1109/TIFS.2026.3674691 | 验证工具 | 处理器 fuzz、模块级覆盖、NSGA-II、IMDM 种子选择 | TIFS/DBLP 记录确认该期刊论文；公开摘要说明在五个开源 RISC-V 处理器上评估并发现 16 个新 bug，均有 CVE 标识。摘要未列出具体处理器名称，是否包含 XiangShan 待全文确认。 |
| 2026 | [FCovFuzz: Enhancing Processor Fuzzing via Functional-Behavioral Coverage Guidance](https://doi.org/10.1109/TIFS.2026.3709126) | IEEE Transactions on Information Forensics and Security 21: 6347-6362；DOI: https://doi.org/10.1109/TIFS.2026.3709126 | 验证工具 | 处理器 fuzz、functional-behavioral coverage、ISA 预仿真反馈、seed optimizer | TIFS/DBLP 记录确认该期刊论文；公开摘要说明在六个设计中发现 23 个 bug，其中 20 个新 bug、19 个获得 CVE。摘要未列出具体处理器名称，是否包含 XiangShan 待全文确认。 |

## 香山 Fuzz 基础设施

| 工具 | 维护方 | 与香山验证流程的关系 | 能力 |
| --- | --- | --- | --- |
| OpenXiangShan/xfuzz | OpenXiangShan | README 给出了 fuzz XiangShan 的构建示例，可与 NEMU 参考模型、DiffTest 和 XiangShan `FuzzConfig` 配合使用。 | 基于 LibAFL 构建，生成 `libfuzzer.a` 并链接到仿真 runner；支持 LLVM sanitizer C++ 分支覆盖和 Chisel/FIRRTL 覆盖插桩；可通过 DiffTest 捕获覆盖信息并驱动 fuzz。 |

## 可能相关但未计入正式统计

| 年份 | 条目 | 原因 |
| --- | --- | --- |
| 2026 | [CEC: The Circuit Education Cloud Based on Server-Multi-FPGA Platform with AI Engine](https://epapers2.org/apccas2026/ESR/paper_details.php?paper_id=2218) | APCCAS 2026 poster 页面提到提升 XiangShan Nanhu Processor 验证效率，但更偏教育/平台海报，未计入正式论文统计。 |
| 2026 | [An Open-Source RISC-V VM-Level TEE Architecture Implemented on XiangShan Processor](https://cfp.riscv-europe.org/eu-summit-2026/speaker/J3PA8H/) | RISC-V Summit Europe 2026 session/poster 信息，未检索到正式论文出版记录，暂不计入。 |
| 2026 | [MicroEvo: Knowledge-Guided LLM Sampling for Efficient Microarchitecture Design Space Exploration](https://arxiv.org/abs/2608.06183) | 检索到预印本/开放评审页面，尚未确认正式出版；若后续录用，可归入设计性能优化/敏捷工具。 |

## 代表性背景工作（未确认基于香山）

| 年份 | 工具/论文 | venue/状态 | 关系说明 |
| --- | --- | --- | --- |
| 2022 | [LibAFL: A Framework to Build Modular and Reusable Fuzzers](https://doi.org/10.1145/3548606.3560602) | ACM CCS 2022；DOI: https://doi.org/10.1145/3548606.3560602 | 通用模块化 fuzz 框架；OpenXiangShan/xfuzz README 明确说明其底层使用 LibAFL。 |
| 2023 | [ProcessorFuzz: Processor Fuzzing with Control and Status Registers Guidance](https://doi.org/10.1109/HOST55118.2023.10133714) | IEEE HOST 2023；DOI: https://doi.org/10.1109/HOST55118.2023.10133714 | 以 CSR transition coverage 引导处理器状态探索，评估 Rocket、BOOM 和 BlackParrot；未检索到以 XiangShan 为平台的证据。 |
| 2025 | [BMCFuzz: Hybrid Verification of Processors by Synergistic Integration of Bound Model Checking and Fuzzing](https://doi.org/10.1109/ICCAD66269.2025.11240887) | ICCAD 2025；DOI: https://doi.org/10.1109/ICCAD66269.2025.11240887 | OpenXiangShan/xfuzz README 将 BMCFuzz 列为采用该工具的研究；BMCFuzz 公开 README 显示评估对象为 NutShell、Rocket 和 BOOM，未显示 XiangShan。 |
| 2026 | [DRVFuzz: Data-Sensitive RISC-V CPU Fuzzing](https://www.usenix.org/conference/usenixsecurity26/presentation/yu-zehong) | USENIX Security 2026 | 面向数据敏感语义与状态转移的 RISC-V CPU fuzz；截至原始检索记录，公开页面未显示 XiangShan 是评估对象。 |

## 研究脉络简述

香山相关论文从 2022 年的敏捷开发方法论开始，逐步覆盖处理器设计实现、功能验证、DFT、RTL 仿真加速、设计空间探索、安全评估、功耗建模、访存机制和多加速器管理等方向。验证方向中，DiffTest 和 XFUZZ 体现了在线差分验证与覆盖率引导 fuzz 的结合；后续 PathFuzz、DiveFuzz、SimFuzz、ModFuzz 和 FCovFuzz 则进一步围绕处理器状态、路径探索、历史缺陷输入、块级变异、指令执行结果多样性、模块级覆盖和功能行为覆盖展开。安全方向中，DejaVuzz、GhostCache、Sonar 与 HartBreaker 代表了从单纯功能 bug 检测扩展到微架构侧信道、处理器漏洞发现与攻击面的趋势。2026 年新增的 TraceRTL、BigPower、Chat-A2、UCV/UCAgent、TurboFuzz 和 Lyra 说明香山相关研究继续向 RTL 仿真加速、功耗/性能建模、LLM 辅助建模和硬件加速 fuzz 延伸。

## 来源

- 香山官方 Publications：https://docs.xiangshan.cc/zh-cn/latest/tutorials/publications/
- 香山 GitHub README：https://github.com/OpenXiangShan/XiangShan
- J-CRAD 论文页：https://crad.ict.ac.cn/cn/article/Y2023/I3/476
- Functional Verification for Agile Processor Development: A Case for Workflow Integration：https://www.sciopen.com/article/10.1007/s11390-023-3285-8
- OpenXiangShan/xfuzz：https://github.com/OpenXiangShan/xfuzz
- PathFuzz DBLP 记录：https://dblp.org/rec/conf/dac/0001WTSB24
- PathFuzz DOI：https://doi.org/10.1145/3649329.3655911
- SimFuzz arXiv 页面：https://arxiv.org/abs/2601.11838
- DiveFuzz ACM 页面：https://doi.org/10.1145/3719027.3765167
- GSIM DOI：https://doi.org/10.1109/DAC63849.2025.11133142
- DiveFuzz GitHub：https://github.com/In2Sec/DiveFuzz
- DejaVuzz DOI：https://doi.org/10.1145/3676642.3736115
- DejaVuzz GitHub：https://github.com/sycuricon/DejaVuzz
- DejaVuzz artifact 数据：https://doi.org/10.5281/zenodo.15861610
- GhostCache DBLP：https://dblp.org/rec/conf/ccs/0010S0QZD25
- GhostCache 项目页：https://www.thu-haslab.org/publication/2025-ghostcache/
- GhostCache GitHub：https://github.com/THU-HAS/GhostCache
- Sonar DBLP：https://dblp.org/rec/conf/micro/ZhangLLTDL000025
- Sonar DOI：https://doi.org/10.1145/3725843.3756136
- ModFuzz DOI：https://doi.org/10.1109/TIFS.2026.3674691
- ModFuzz DBLP：https://dblp.org/rec/journals/tifs/WangFCLYSM26
- FCovFuzz DBLP：https://dblp.org/rec/journals/tifs/FangWYCCYTSM26
- FCovFuzz DOI：https://doi.org/10.1109/TIFS.2026.3709126
- TraceRTL HPCA 2026 页面：https://2026.hpca-conf.org/details/hpca-2026-main-conference/27/TraceRTL-Agile-Performance-Evaluation-for-Microarchitecture-Exploration
- HartBreaker artifact 页面：https://doi.org/10.5281/zenodo.19417381
- UCV / UCAgent ISCA 2026 会议页面：https://www.iscaconf.org/isca2026/program/
- UCV / UCAgent DOI：https://doi.org/10.1109/ISCA66397.2026.00154
- UCAgent GitHub：https://github.com/XS-MLVP/UCAgent
- UCAgent 官方介绍：https://open-verify.cc/opensource_tools/ucagent/
- TurboFuzz arXiv 页面：https://arxiv.org/abs/2509.10400
- Lyra arXiv 页面：https://arxiv.org/abs/2512.13686
- BigPower arXiv 页面：https://arxiv.org/abs/2606.13747
- Chat-A2 DOI：https://doi.org/10.1109/ASP-DAC66049.2026.11420663
- XiangShan over FireSim / Imprecise Store Exceptions 仓库：https://github.com/parsa-epfl/xsofs
- EuroSec 2026 DBLP：https://dblp.org/rec/conf/eurosec/ParaulaPBC26.html
- XiangShan Spectre 代码仓库：https://github.com/necst/xiangshan-spectre
- ReadyPower 论文页：https://researchportal.hkust.edu.hk/en/publications/readypower-a-reliable-interpretable-and-handy-architectural-power/
- Mini-MDP 论文页：https://www.sciencedirect.com/science/article/abs/pii/S1383762126002432
- AExec 论文页：https://doi.org/10.1145/3801487.3801807
- CoroAMU arXiv 页面：https://arxiv.org/abs/2511.14990
- LibAFL 论文页：https://www.eurecom.fr/publication/6973
- ProcessorFuzz 论文页：https://colab.ws/articles/10.1109%2Fhost55118.2023.10133714
- BMCFuzz GitHub：https://github.com/iscas-versys/BMCFuzz
- BMCFuzz ICCAD 2025 接收信息：https://versys.ios.ac.cn/blog/ICCAD25/
- DRVFuzz USENIX Security 2026：https://www.usenix.org/conference/usenixsecurity26/presentation/yu-zehong
