# 香山昆明湖 V3 Issue #6132 深度分析：自修改代码下 `fence.i` 与 `cfVec` 旧指令机制解析

## 1. 文档目标与核心结论
本 Issue 讨论的是一个极易误判的前端微架构现象：自修改代码执行 `fence.i` 后，修改前的旧指令仍会短暂出现在前端送往后端的 `cfVec` 接口上。这一现象到底代表 ICache 一致性失效，还是仅为一批注定被重定向冲掉的推测指令在内部接口上的瞬时表现？本文将从现象、机制、波形到安全影响逐层拆解。

**先行核心结论**
1.  **现象可复现**：波形明确显示，ICache 侧注册 `fence.i` 信号后，`cfVec` 仍会连续两拍携带修改前的指令字，这一微架构表现客观存在。
2.  **不构成功能错误**：昆明湖 V3 将 `fence.i` 的职责拆分为两条路径：专用 `fence.i` 通路负责失效 ICache 元数据、处理在途 miss；必然伴随的后端重定向负责冲刷前端流水线与 WayLookup。旧指令会在产生功能影响前被清除。
3.  **存在96周期推测窗口**：本地 FST 验证显示，`[C4235, C4331)` 区间内共有 11 条动态指令经过 `cfVec`。它们未进入执行单元，但窗口内 Rename、前端队列与 Cache 层级均发生了真实的状态变化。
4.  **属于安全暴露面**：窗口内 L1D 安装 2 条新 Cache Line、L2 安装 6 条新 Line，合计 8 次 Line 安装、7 个唯一地址；部分 L2 状态在重定向后仍会保留并影响后续访问。**“最终没有旧指令退休”不等于“没有安全风险”**。
5.  **因果需区分**：Cache 安装行为源自更早的 load/store 指令与窗口前已发出的 ICache 预取，而非这 11 条错误路径指令本身。已证实的是：旧指令可见性与持久微架构状态变化重叠，构成潜在侧信道面；尚未证实的是：年轻旧指令直接引发秘密相关 Cache 副作用，或已形成可稳定利用的侧信道。
6.  **最终分类**：该 Issue 不属于已验证的功能 Bug，而是安全相关的功能增强请求。从安全审计角度，96 周期窗口仍属于需收敛的攻击面——Cache 状态不会随重定向自动回滚，缩短窗口、隔离过期请求、增加断言具备明确的纵深防御价值。
7.  **V2对照验证**：本地 `kunminghu-v2` 波形清晰展示了 `fence.i` 的两条独立路径：Fence FU 到 ICache `flushAll` 的直接失效路径，以及退休后通过 ROB/FTQ 重定向冲刷流水线的恢复路径，支持上述机制解释。

> 本文核心方法论：**内部总线上“观测到旧指令”，不等于该指令在重定向后仍然有效，更不等于它被执行或退休。**

## 2. 全文框架
本文按照「背景 → Issue 事实 → 实现机制 → 波形归因 → 安全方案 → 验证计划」的逻辑展开。
- 快速入门：建议优先阅读第 1、3、4、6、7.9、8 章
- 细节复核：可查阅第 7.3-7.8 节的完整周期级波形表

| 章节 | 核心目的 | 当前状态 |
| --- | --- | --- |
| 背景知识 | 建立自修改代码、`fence.i`、重定向与 `cfVec` 的基础概念 | 已完成 |
| Issue 内容复述 | 整理最小 PoC、关键周期、对照实验与核心主张 | 已完成 |
| 讨论结论 | 说明实现契约、证据边界与最终分类 | 已完成 |
| Issue 深度分析 | 区分已证事实、设计约束、推断与未证实安全影响 | 已完成 |
| 源码执行链分析 | 从译码到 ICache、IFU、后端冲刷的全链路追踪 | V2 对照分析完成；V3 待核对 |
| 96周期推测窗口 | 追踪错误路径指令、Rename 状态与 Cache 痕迹的真实来源 | V2 深度分析完成，确认安全相关微架构暴露面；V3 待核对 |
| 独立复现 | 固化构建、运行与监视器流程 | V2 控制链对照完成；#6132 PoC 待复现 |
| 波形分析 | 以重定向、冲刷、微操作为生命周期主线验证 | V2 `fence.i` 个案完成；V3 旧指令生命周期待验证 |
| 安全硬化方案 | 设计预译码、前端屏障、`fence.i` 前后过滤与 ICache 完成握手 | 实现与测试方案完成 |

## 3. 背景知识
### 3.1 自修改代码为什么需要 `fence.i`
自修改代码的典型执行流是：先将新指令作为普通数据写入内存，再跳转到被修改的地址执行。但数据侧写入完成，不代表指令侧会立即丢弃旧的缓存内容，也不代表在途的取指结果会立刻失效。

因此标准验证流程采用如下序列：
```text
写入新指令 -> fence rw, rw -> fence.i -> 跳转到被修改的函数
```

`fence.i` 需要解决两类本质不同的问题：
- **指令存储一致性**：后续取指不能再命中旧的 ICache 元数据，也不能被旧的在途 miss 重新填回 ICache。
- **推测流水线失效**：`fence.i` 执行前已经进入取指、IFU、IBuffer 或后端早期阶段的指令，必须被标记为错误路径并丢弃。

从架构定义上，`fence.i` 只保证后续有效执行看到的指令流是正确的，并不要求内部所有接口在同一时刻立刻清零。因此不能仅凭某条内部总线上的数据值，直接判定实现违反架构规范。

### 3.2 `cfVec` 不是退休接口
`frontend.io_backend_cfVec` 是前端向后端传输取指与控制流信息的内部向量接口。处于这个边界的指令，仍可能因为重定向、异常或更年轻的推测路径失效而被丢弃。

相关执行路径可简化为：
```text
ICache -> IFU / IBuffer -> cfVec -> 译码与后端流水 -> 执行 -> 退休
                  ^                    |
                  +---- redirect ------+
```

分析 `cfVec` 现象时，必须同时回答四个问题：
1.  条目是否有效，是否真正完成握手？
2.  它来自 `fence.i` 前的取指，还是 `fence.i` 后的重新取指？
3.  后端是否在它进入有效执行路径前将其冲掉？
4.  它是否到达执行单元、LSQ、Cache 或退休点，并产生可观测影响？

仅检查指令字和时间先后，只能证明“数据曾出现在接口上”，无法跨越上述阶段直接得出架构结论。

### 3.3 昆明湖 V3 的两条冲刷通路
Issue 对应提交中，ICache 顶层的冲刷职责可概括为下表：

| 目标结构 | 专用 `io.fencei` 通路 | `io.fromFtq.redirectFlush` 重定向通路 |
| --- | --- | --- |
| MetaArray | 失效全部有效位 | 不负责 |
| MissUnit | 标记/取消与 `fence.i` 冲突的在途 miss | 也接收普通重定向冲刷 |
| PrefetchPipe | 不直接连接 | 冲刷 |
| MainPipe | 不直接连接 | 冲刷 |
| WayLookup | 不直接连接 | 冲刷 |

官方设计文档明确说明：`fence.i` 逻辑上需要冲刷 MainPipe、PrefetchPipe 和 WayLookup，但实现上保证 `io.fencei` 必然伴随后端重定向，因此这些结构复用重定向冲刷通路即可，无需重复接入专用 `fence.i` 信号。

在 MainPipe 内部，s0/s1 级的冲刷条件包含 `io.flush`；输出 `toIfu.valid` 来自 `s1_fire`，而 `s1_fire` 要求 `!s1_flush`。这意味着重定向冲刷到达 MainPipe 后，该级不会再向 IFU 发送有效响应。但端到端正确性仍需结合重定向的实际周期、下游队列与后端失效条件验证，不能仅依据局部代码下结论。

### 3.4 常用术语说明
| 术语 | 本文含义 |
| --- | --- |
| 预译码（predecode） | 前端在完整后端译码之前，预先识别指令长度、控制流类型等少量属性 |
| 取指块（fetch block） | 前端一次预测和取指覆盖的一组连续指令槽位 |
| 推测窗口（speculative window） | 一条指令已被前端观测到，但其路径仍可能被更老事件撤销的时间区间 |
| 重定向/冲刷（redirect/flush） | 放弃重定向前的取指结果，从新 PC 恢复取指的控制动作 |
| `fence.i` 前后标记 | 标明请求或条目位于 `fence.i` 前还是后，用于拒绝 `fence.i` 前请求的迟到响应 |
| 前端屏障（barrier） | 在 `fence.i` 完成前，阻止更年轻取指和指令继续前进的控制状态 |
| MSHR | 记录尚未完成的 Cache Miss 的硬件表项 |
| 微操作（uop） | 后端调度和执行所使用的内部操作 |
| 握手成功（fire） | 解耦接口上 `valid && ready`，表示本周期真正完成传输 |
| Cache 痕迹（cache footprint） | Cache 标签、有效位、一致性状态、替换状态或访问延迟留下的可观测变化 |

## 4. Issue 内容复述
### 4.1 基本信息
| 项目 | 内容 |
| --- | --- |
| Issue 编号 | #6132 |
| 分支 | `kunminghu-v3` |
| 测试提交 | `3931c5112c528299a23c256bdd77fb90813afa6e` |
| 状态 | open |
| 当前分类 | `topic: security`、`type: feature/requested` |
| 创建/更新时间 | 2026-06-25 |
| 编译器 | `riscv64-unknown-elf-gcc 15.1.0` |
| 仿真方式 | `emu --no-diff --dump-wave-full` |

报告的核心现象：自修改代码覆盖已驻留在 ICache 中的函数，执行 `fence rw, rw; fence.i` 后，修改前的指令仍会出现在后端 `cfVec` 接口上。

### 4.2 最小直接加载 PoC
目标函数位于 `0x80000080`，修改前后的指令如下：

| PC 地址 | 修改前指令 | 修改后指令 |
| --- | --- | --- |
| `0x80000080` | `ld t5, 0(a1)` (`0x0005bf03`) | `addi a0, zero, 0x5a` (`0x05a00513`) |
| `0x80000084` | `addi a0, zero, 0x11` (`0x01100513`) | `ret` (`0x00008067`) |
| `0x80000088` | `ret` (`0x00008067`) | `nop` |

测试流程：
1.  先调用旧函数，使旧指令加载进入 ICache。
2.  通过 store 指令覆盖目标地址，写入新函数体。
3.  执行 `fence rw, rw` 和 `fence.i`。
4.  用 `jal` 再次调用目标函数。
5.  同时监视 `fence.i` 与 `cfVec`，检查旧的 PC/指令字组合是否再次出现。

从架构执行结果看，函数调用返回的是新函数写入的 `0x5a`，而非旧函数的 `0x11`，程序最终结果正确。

### 4.3 波形关键时间线
直接加载版本的监视器报告如下关键节点：

| 周期 | 观测事件 |
| --- | --- |
| 16746-16747 | 后端侧识别到 `fence.i` |
| 16748-16749 | ICache 侧注册收到 `fence.i` |
| 16750-16751 | `cfVec` 再次出现旧函数的三个指令字 |
| 16856-16857 | `cfVec` 出现修改后的 `addi 0x5a; ret` |

旧函数在 16750 和 16751 两拍均以三个 slot 的形式出现：
```text
slot 0: pc=0x80000080, instr=0x0005bf03
slot 1: pc=0x80000084, instr=0x01100513
slot 2: pc=0x80000088, instr=0x00008067
```

Issue 报告据此将“ICache 注册后的 `fence.i` 最后一拍”作为分界，定义了 `post-fence stale cfVec` 谓词，并推测专用 `fence.i` 没有覆盖 MainPipe、WayLookup 和预取响应路径，导致在途旧响应逃逸到 `cfVec`。

### 4.4 两个补充对照实验
Issue 还提供了两个有价值的变体实验：
- **秘密索引变体**：旧函数包含一次秘密值加载、移位、地址相加以及探针加载。该版本同样在 `cfVec` 上复现旧指令块，但更严格的监视器未发现选定探针 Cache 行被访问，也未发现旧 load 进入 LSQ 或产生写回。
- **顺序落入对照组**：执行 `fence.i` 后不使用 `jal` 跳转，而是顺序落入修改后的目标地址。该版本没有复现旧目标块，仅观察到修改后的指令。

第一个变体限定了安全结论：它展示了具有“秘密相关访问形状”的旧指令可以出现在前后端边界，但没有展示这条访问真正执行并留下 Cache 痕迹。第二个变体说明现象与重定向、重新取指的时序有关，而非 `fence.i` 后所有取指都会返回旧数据。

### 4.5 Issue 自身的声明边界
原始报告主动将结论限定在“前端 `cfVec` 的旧输出”：
- 没有声称旧指令退休。
- 没有声称旧 load 完成执行。
- 没有声称已经形成 Spectre 风格侧信道。
- 没有声称程序得到错误的架构结果。

这个限定非常重要：标题中的 “stale” 描述的是接口上观察到的旧数据，不能自动解释为“架构上仍然有效”。

## 5. Issue 讨论结论
Issue 讨论的核心问题是：**`cfVec` 上的旧指令是否应由 redirect 清除？** 相关实现结论如下：

1.  现有实现约定每条 `fence.i` 都会伴随后端重定向，也就是 ICache 的 `io.flush` 来源。因此专用 `fence.i` 只需冲刷 MetaArray 和 MissUnit，避免旧数据继续保留或被重新填入 ICache；流水中的旧条目由随后的 redirect 清除。
2.  在这个设计契约下，`cfVec` 短暂携带旧数据被视为正常的推测流水现象，后端应在它产生功能影响前将其冲掉，因此现有证据不构成功能 Bug。
3.  `FENCE_I` 在译码表中同时带有 `noSpec = true`、`blockBack = true` 和 `flushPipe = true`。这些属性表达了阻止更年轻指令越过屏障并触发流水刷新的设计意图，但不是完整的形式化安全证明；PoC 的无副作用结果也只覆盖了本次观测窗口和监视信号。
4.  Issue 最终被归为功能增强请求，诉求集中在两点：更清晰地记录冲刷契约，以及把 `fence.i` 直接纳入更多前端失效路径作为纵深防御。
5.  官方设计文档的 ICache 冲刷表及脚注明确记录了 `fence.i` 依赖后端重定向冲刷流水的行为；在源码中补充注释和断言可以降低误读概率。

最终，Issue 保持 open，并带有安全主题和功能增强请求标签。核心共识是：看到旧指令不等于发现功能错误，但仍需检查它们是否在被失效前改变了不可回滚的微架构状态。

## 6. Issue 深度分析
### 6.1 原始推断缺失了重定向环节
原始推断的逻辑可以写成：
```text
io.fencei 没有直接接到 MainPipe/WayLookup
        +
io.fencei 之后 cfVec 看见旧指令
        =
fence.i 冲刷覆盖 Bug
```

第一项和第二项分别都能从源码及波形得到支持，但等号并不成立，因为它漏掉了一个实现不变量：
```text
fence.i -> 后端 redirect -> redirectFlush
                         -> MainPipe / PrefetchPipe / WayLookup flush
                         -> 后端丢弃错误路径条目
```

如果这个不变量始终成立，那么把 `io.fencei` 再直接接到所有流水级只是冗余的纵深防御，而不是架构正确性的必要条件。反过来，如果能找到 `fence.i` 没有产生 redirect、redirect 漏冲某个队列，或旧条目在失效前产生不可撤销副作用的路径，就需要重新评估现有实现。

### 6.2 “时间上在 `fence.i` 之后”≠“逻辑上属于 `fence.i` 之后”
监视器把 ICache 注册 `fence.i` 的周期作为前后分界，这对发现现象很有效，却不是完整的正确性边界。流水线中需要区分三个维度：
- **信号采样时间**：某个周期在 `cfVec` 上看见了什么。
- **相对 `fence.i` 的位置**：条目来自 `fence.i` 前发出的请求，还是 `fence.i` 后由 redirect 重新发出的请求。
- **生效资格**：条目是否在 redirect/flush 后仍被后端接受、执行和退休。

旧请求的响应可能在物理时间上晚于 `io.fencei`，但逻辑上仍来自 `fence.i` 前发出的请求，并将被后续冲刷清除。正确性测试不能只比较周期大小，还要判断条目位于 `fence.i` 前还是后，以及它是否仍然有效。

### 6.3 证据边界梳理
| 命题 | 当前证据 | 判断 |
| --- | --- | --- |
| ICache 注册后的 `fence.i` 后，`cfVec` 能看到旧指令字 | 两拍明确波形记录 | 已证实 |
| 存在96周期的错误路径推测窗口 | `[C4235, C4331)` 内 11 条 `cfVec.fire`，随后由 redirect 清除 | 已证实（V2 对照波形） |
| 窗口内发生真实 Cache 状态变化 | L1D 安装 2 条 Line，L2 安装 6 条 Line，部分状态在 redirect 后保留 | 已证实，属于安全相关微架构暴露面 |
| 最终重新取到修改后的函数 | 后续 `cfVec` 和程序返回值 | 已证实 |
| 旧指令进入 LSQ | 严格监视器未观察到 | 未证实，现有结果反对该命题 |
| 旧 load 写回或触发指定 DCache 探针 | 严格监视器未观察到 | 未证实，现有结果反对该命题 |
| 旧指令执行或退休 | 没有相应证据 | 未证实 |
| 发生架构功能错误 | 程序得到修改后的结果 | 未证实，当前实验表现正确 |
| 11 条年轻旧指令亲自造成 Cache 副作用 | 全端口归因扫描显示它们没有进入 LSQ/EXU/DCache；Cache 请求来自更老或窗口前活动 | 未证实 |
| 已形成可利用侧信道 | 没有秘密相关、由年轻旧指令触发的完整观测链 | 未证实；但 96 周期窗口和持久 Cache 痕迹构成真实安全风险 |

最稳妥且完整的表述是：**该 Issue 复现了“旧指令在内部前后端边界上的瞬时可见性”，并且对照波形证明这个窗口与真实、可持续的 Cache 状态变化重叠；但尚未证明旧指令在 `fence.i` 后继续有效执行，或已经形成可利用侧信道。**

### 6.4 `blockBack` 是重要证据，但不是终点
讨论引用的译码项把 `FENCE_I` 标为：
```scala
noSpec = true, blockBack = true, flushPipe = true
```

这三个属性共同表达了很强的设计意图：`fence.i` 自身不能被随意推测执行，它会阻挡更年轻指令，并触发流水刷新。它解释了为什么秘密索引版本虽然在 `cfVec` 上可见，却没有继续进入 LSQ/DCache。

不过，安全审计不能止于译码属性。还需要确认 `blockBack` 在 dispatch/issue 端的实际门控范围、redirect 到各队列的优先级、同拍握手的处理，以及是否存在可在失效前发生且无法回滚的微架构更新。当前 Issue 没有给出这条完整证明链，所以既不能据 `cfVec` 现象宣称存在侧信道，也不宜仅凭一个译码位宣称所有安全风险已被形式化排除。

### 6.5 如何设计更准确的监视器
功能正确性监视器应跟踪一条指令从取指到退休的完整生命周期，而不是只匹配 `cfVec` 的 PC/指令字。至少应加入以下观测点：
1.  `fence.i` 对应 redirect 的发出和各级接收周期。
2.  `cfVec` 的 valid/ready、FTQ 指针，或其他可判断条目位于 `fence.i` 前还是后的标识。
3.  旧条目是否进入译码、rename、dispatch、ROB 和各执行队列。
4.  旧 load 是否 issue、进入 LSQ、发出内存请求或写回。
5.  退休点是否只出现修改后的目标指令。

功能判据可以写成：
```text
在 fence.i 对应的 redirect 生效后，任何由 fence.i 前请求取得的目标指令都不能退休；
重定向后第一个能够退休的目标函数体必须是修改后的版本。
```

安全判据则更严格：即使旧指令最终被失效，也要检查它是否在失效前更新预测器、TLB、Cache、预取器或其他攻击者可观测状态。Issue 附带的严格监视器已经覆盖 LSQ、写回和一个 DCache 探针，这是正确方向，但尚不是完整的微架构信息流检查。

### 6.6 对改进方案的评价
一种改进方向是把 `fence.i` 作为完整前端失效点，直接加入 MainPipe、WayLookup、预取响应和 IFU/IBuffer 的清理条件。这个方向的价值主要是：
- 缩小专用 `fence.i` 与 redirect 之间的短暂窗口。
- 降低实现对“每次 `fence.i` 必然产生且正确传播 redirect”这一跨模块不变量的依赖。
- 让内部接口行为更直观，也缩小潜在瞬时执行面的审计范围。

但它不是无成本的改动。需要评估重复 flush 的同拍优先级、时序路径、在途 refill 响应以及是否可能丢失 redirect 后的新请求。若没有先证明当前路径存在可观察副作用，就应把它定位为安全硬化/功能增强，而不是未经验证地称为功能修复。

成本最低的改进，是在 ICache 连接处添加注释，明确写出“MainPipe/PrefetchPipe/WayLookup 的 `fence.i` 逻辑冲刷由伴随的 backend redirect 实现”，并为这个不变量增加断言或定向测试。

## 7. 本地对照实验：`kunminghu-v2` 的 `fence.i` 波形与源码链
### 7.1 实验对象与 Issue #6132 的关系
本节使用本地 FST 对照实验分析 `fence.i` 的控制链。实验程序为当前目录下的 `smc_fencei_direct_probe.S`，其旧目标函数、补丁内容和 `fence rw,rw; fence.i; jal` 序列与 Issue 中的直接加载 PoC 相同。波形文件路径：
```text
/nfs/home/yanyusong/XiangShanLab/xiangshan-course/docs/6-xiangshan-debug/
  xiangshan-issue-6132/bug-replay/2026-08-31-11-43-57.fst
```
文件大小 83,971,898 字节，SHA-256 为：
```text
496bc90fb306ced17a1ff52e37cbd551a1631a67a741a43e6d338c0b0b356d78
```

分析对应源码分支 `kunminghu-v2`，提交为 `0fa7bb8259a7922481289d8d5932797afce84030`。这与 #6132 报告使用的 `kunminghu-v3`、提交 `3931c511...` 不同。因此本节可用于解释 Fence FU、ICache 全量失效和 ROB/FTQ 恢复之间的关系，但不能直接证明 V3 上的旧 `cfVec` 现象已经被独立复现或排除。

本节重点跟踪 `PC=0x800004c`、编码 `0x0000100f` 的 `fence.i`。波形中这条静态指令在 `cfVec[0]` 上出现两次：
1.  **C4235 / T8470**：第一次 `cfVec.fire`。这个动态副本后来被更老的 `PC=0x80000048`、`ROB=22` 的普通 `fence` 引发的流水清空杀掉，没有进入 ROB、Issue Queue 或 Fence FU。
2.  **C4331 / T8662**：重取后的第二次 `cfVec.fire`。这是实际执行并退休的动态副本，分配 `ROB=23`、`FTQ=(flag=0,value=10), offset=0`。

对实际存活的第二个副本：
- 前端通过 `cfVec[0]` 发给后端：C4331。
- 后端 Fence FU 产生 `fencei`，组合直连到 `frontend.io_fencei`：C4339。
- Frontend 经过一个 `RegNext` 后，ICache 收到 `io.fencei`，MetaArray 收到 `flushAll`：C4340。
- `cfVec.fire -> frontend.io_fencei` 为 8 个周期。
- `cfVec.fire -> ICache.io_fencei/MetaArray.flushAll` 为 9 个周期。

这份波形展示了两条必须分开的控制路径：
- **直接 ICache 全量失效路径**：Fence FU `fencei` -> XSCore 直连 -> Frontend `RegNext` -> ICache `fencei` -> MetaArray `flushAll`，发生在 C4339/C4340。
- **`flushPipe` 通用恢复路径**：Fence 写回 -> ROB `flushOut` -> CtrlBlock 延迟 -> FTQ backend redirect -> ICache `io.flush`，发生在 C4342-C4348。它负责清理前端流水状态并从 `0x80000050` 重取，不是再次清空全部 ICache tag。

### 7.2 采样方法与判定口径
关键层次前缀为：
```text
CLK  = TOP.SimTop.clock
CORE = TOP.SimTop.cpu.l_soc.core_with_l2.core
BE   = CORE.backend
FE   = CORE.frontend
```

分析使用 `wavekit.FstReader` 并设置 `sample_on_posedge=True`。该 FST 中 `FST time = 2 * cycle`，例如 C4331 对应 T8662。下文的 Cxxxx 都是从仿真开始计数的绝对上升沿周期。

对所有 `Decoupled` 接口，只有 `valid && ready` 才记为实际传输，即 `fire`。数据字段在 `valid=0` 时即使保留旧值，也不能作为指令身份或流水事件的证据。动态身份通过以下字段交叉跟踪：
- 前端：`pc=0x8000004c`、`instr=0x0000100f`、`ftqPtr/ftqOffset`。
- 后端：`fuOpType=0x12`、`flushPipe=1`，以及实际副本的 `ROB=23`。
- 完成与恢复：Fence FU 输入/输出、ROB 写回、ROB `flushOut`、frontend redirect 和 Difftest commit。

`FenceOpType.fencei` 在这版源码中为二进制 `10010`，即 `0x12`，见 `package.scala:272`。

### 7.3 为什么波形里有两次 `cfVec.fire`
#### 7.3.1 两个动态副本
| 动态副本 | 周期 / 时间 | `cfVec[0]` 状态 | PC / 指令 | FTQ 身份 | 最终结果 |
| --- | ---: | --- | --- | --- | --- |
| 第一次 | C4235 / T8470 | `valid=1, ready=1, fire=1` | `0x8000004c / 0x0000100f` | flag 0, value 9, offset 12 | 在 Dispatch 等待时被更老的普通 `fence` 清除 |
| 第二次 | C4331 / T8662 | `valid=1, ready=1, fire=1` | `0x8000004c / 0x0000100f` | flag 0, value 10, offset 0 | 分配 ROB23，进入 Fence FU，完成并退休 |

两次都位于 lane 0，且 `pred_taken=0`、前端异常向量为 0。前端到后端接口是 IBuffer 的 `Decoupled[CtrlFlow]` 输出：
```scala
// Bundle.scala
class FrontendToCtrlIO(implicit p: Parameters) extends XSBundle {
  val cfVec = Vec(DecodeWidth, DecoupledIO(new CtrlFlow))
  ...
}

// Frontend.scala
io.backend.cfVec <> ibuffer.io.out

// CtrlBlock.scala
val decodeFromFrontend = io.frontend.cfVec
```

相关源码见 `Bundle.scala:484`、`Frontend.scala:439` 和 `CtrlBlock.scala:423`。

#### 7.3.2 第一个副本如何被清除
| 周期 | 事件 |
| ---: | --- |
| C4234 | 更老的 `PC=0x80000048`、编码 `0x0330000f` 的普通 `fence rw,rw` 从 `cfVec` 进入后端，后来分配 ROB22。 |
| C4235 | 目标 `fence.i` 第一次 `cfVec[0].fire`，FTQ9/off12。 |
| C4236-C4285 | 目标副本停在 Decode->Rename 边界，`valid=1, ready=0`。 |
| C4286 | Decode->Rename 终于 `fire`。数据里出现候选 `ROB=23`，但此时尚未真正分配 ROB。 |
| C4287-C4317 | 目标副本停在 Rename->Dispatch，持续 `valid=1, ready=0`；`dispatch.io_enqRob.req[0].valid` 从未对它形成有效入队。 |
| C4290 | 更老的普通 `fence` 进入 Fence FU，`fuOpType=0x10`、ROB22。 |
| C4291-C4311 | Fence FU 处于 `s_wait`，`flushSb=1`，而 `sbIsEmpty=0`。 |
| C4312 | `sbIsEmpty` 变为 1。 |
| C4313 | ROB22 的普通 `fence` 进入 `s_fence` 并完成。 |
| C4316 | ROB22 产生 `rob.io_flushOut.valid=1`，身份为 FTQ9/off10。 |
| C4317 | `s1_robFlushRedirect` 生效；目标的第一个副本仍在 Dispatch，但不能 fire。 |
| C4318 | 第一个目标副本从 Dispatch 消失，证明它被清除而不是继续执行。 |
| C4322 | backend redirect 到达前端，FTQ9/off10，重定向目标为 `0x8000004c`。 |
| C4331 | 目标 PC 重取后，第二次 `cfVec.fire`。 |

因此，不能把 C4235 的前端发送与 C4339 的 `fencei` 拼成一条连续流水线。机械相减得到的 `4339 - 4235 = 104` 个周期包含一次 squash 和 refetch，不是单个 uop 的执行延迟。

### 7.4 实际存活副本的逐周期流水线
下表从第二次、实际存活的 `cfVec.fire` 一直跟踪到后端信号到达前端，并额外列出 ICache 真正消费信号的一拍。除非另有说明，关键接口上的 valid/ready 均为 1。

| 周期 | FST 时间 | 流水阶段 | 本周期工作与波形证据 |
| ---: | ---: | --- | --- |
| **C4331** | 8662 | Frontend -> Decode | `BE.io_frontend_cfVec_0_valid=1` 且 `ready=1`，PC=`0x8000004c`、instr=`0x0000100f`、FTQ10/off0，完成传输。同周期 Decode input/output 和 `decodePipeRename` 输入 fire；结果为 `fuType=fence`、`fuOpType=0x12`、`flushPipe=1`。同周期还有一次与本 uop 回传无关的 FTQ redirect 类 `ICache.io_flush`，但 `fencei/flushAll=0`。 |
| **C4332** | 8664 | Rename | `decodePipeRename` 输出、Rename 输入/输出及 `renamePipeDispatch` 输入 fire；目标获得 ROB 候选值 23，身份仍为 FTQ10/off0。 |
| **C4333** | 8666 | Dispatch / IQ enqueue | `renamePipeDispatch` 输出与 `dispatch.fromRename[0]` fire；`dispatch.io_enqRob.req[0].valid=1`。`dispatch.io_toIssueQueues_6` fire，并在 `IssueQueueAluCsrFenceDiv.io_enq_0` 入队；ROB23/op0x12/flushPipe=1 一致。 |
| **C4334** | 8668 | ROB enqueue / Issue select | CtrlBlock 对 ROB 请求寄存一拍后，`rob.io_enq.req[0].valid=1`。同周期 `IssueQueueAluCsrFenceDiv.deqBeforeDly_1` 选中并 fire，仍为 ROB23/op0x12。 |
| **C4335** | 8670 | Issue delay / DataPath s0 | `io_deqDelay_1` 有效，`intScheduler.io_toDataPathAfterDelay_3_1` 与 `dataPath.io_fromIntIQ_3_1` fire；完成 Issue Queue 的一拍 `deqDelay`。 |
| **C4336** | 8672 | DataPath s1 / Bypass input | `DataPath.s1_toExuValid_3_1=1`；`io_toIntExu_3_1` 和 `bypassNetwork2intExuBlock_7.io_in` fire。ROB23/op0x12/flushPipe=1 继续随 uop 传递。 |
| **C4337** | 8674 | Exu7 / Fence FU input | `bypassNetwork2intExuBlock_7.io_out`、`exus_7.io_in` 和 `exus_7.Fence.io_in` fire。Fence FSM 位于 `s_idle`，锁存 ROB23 的 uop 并转向 `s_wait`。 |
| **C4338** | 8676 | Fence `s_wait` | `state=1`，`flushSb=1`，`sbIsEmpty=1`，`fencei=0`，Fence output 尚未有效。即使 Store Buffer 已空，FSM 仍有这一拍必经等待态；本拍决定下一拍进入 `s_icache`。 |
| **C4339** | 8678 | Fence `s_icache` / Backend -> Frontend | `state=3`，Fence output 和 Exu7 output fire，ROB23、`flushPipe=1`。`Fence.io_fenceio_fencei=1`，并通过 Backend/XSCore 组合连线令 `frontend.io_fencei=1`。 |
| **C4340** | 8680 | Frontend register / ICache invalidate | 原始 `frontend.io_fencei` 已回到 0，但 Frontend 中 `RegNext(io.fencei)` 的输出为 1，故 `ICache.io_fencei=1`、`MetaArray.io_flushAll=1`、`MissUnit.io_fencei=1`。所有 fetch/prefetch MSHR 的 `io_fencei` 同时为 1；MetaArray 在该边沿清空所有 way 的 valid bitmap。ROB 写回端口 7 同周期看到 ROB23/flushPipe。 |

阶段序列可简化为：
```text
cfVec/Decode
  -> Rename
  -> Dispatch + Issue Queue enqueue
  -> ROB enqueue + Issue select
  -> Issue deqDelay + DataPath s0
  -> DataPath s1 + bypass pipeline input
  -> Exu7/Fence input
  -> Fence s_wait / flush Store Buffer
  -> Fence s_icache / raw fencei reaches Frontend
  -> Frontend RegNext / ICache flushAll
```

延迟计算如下：
```text
后端信号到达前端：C4339 - C4331 = 8 cycles
ICache 实际收到 fencei：C4340 - C4331 = 9 cycles
```

相应 FST 时间差为 `8678 - 8662 = 16` 和 `8680 - 8662 = 18` 个时间单位；该波形每周期为 2 个 FST 时间单位。若把第一次、被杀掉的 C4235 副本作为时钟起点，则结果为 104/105 周期，但这些数字包含更老 fence 的等待、清空和重取，不能称为目标 `fence.i` 的流水延迟。

### 7.5 源码如何实现 `fence.i` 回传与 ICache 全量失效
#### 7.5.1 解码属性决定 Fence FU 和 `flushPipe`
`DecodeUnit.scala:228` 中的关键解码为：
```scala
SFENCE_VMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X,
  FuType.fence, FenceOpType.sfence, SelImm.X,
  noSpec = T, blockBack = T, flushPipe = T),
FENCE_I -> XSDecode(SrcType.pc, SrcType.imm, SrcType.X,
  FuType.fence, FenceOpType.fencei, SelImm.X,
  noSpec = T, blockBack = T, flushPipe = T),
FENCE -> XSDecode(SrcType.pc, SrcType.imm, SrcType.X,
  FuType.fence, FenceOpType.fence, SelImm.X,
  noSpec = T, blockBack = T, flushPipe = T),
```

目标 uop 因而具有三项关键属性：
- `FuType.fence`：送往 Fence FU；该参数配置将 `FenceCfg` 放在 BJU3，见 `Parameters.scala:411`。
- `FenceOpType.fencei=0x12`：令 Fence FSM 选择 ICache 动作态。
- `flushPipe=true`：除 Fence 动作本身外，还要求 ROB 头部走通用恢复/重定向路径。

Decode、Rename、Dispatch 与 ROB/IQ 的连接见 `CtrlBlock.scala:576` 和 `CtrlBlock.scala:703`；Issue Queue 的 `deqDelay` 寄存见 `IssueQueue.scala:765`，DataPath 的 s0->s1 寄存见 `DataPath.scala:570`。

#### 7.5.2 Fence FSM 先清 Store Buffer，再发单周期 `fencei`
`Fence.scala:47` 的核心逻辑为：
```scala
val s_idle :: s_wait :: s_tlb :: s_icache :: s_fence :: s_nofence :: Nil = Enum(6)
val state = RegInit(s_idle)
val uop = RegEnable(io.in.bits, io.in.fire)
val func = uop.ctrl.fuOpType

sbuffer := state === s_wait
fencei  := state === s_icache

when (state === s_idle && io.in.valid) { state := s_wait }
when (state === s_wait && func === FenceOpType.fencei && sbEmpty) { state := s_icache }
when (state =/= s_idle && state =/= s_wait) { state := s_idle }

io.in.ready  := state === s_idle
io.out.valid := state =/= s_idle && state =/= s_wait
io.out.bits.ctrl.robIdx := uop.ctrl.robIdx
io.out.bits.ctrl.flushPipe.get := uop.ctrl.flushPipe.get
```

这解释了 C4337-C4339 的时序：C4337 接受 uop，C4338 必经 `s_wait` 并断言 `flushSb`；由于 `sbIsEmpty=1`，C4339 转入只持续一拍的 `s_icache`。`fencei` 与 Fence output 同拍有效，输出不写通用寄存器，只携带 ROB/flush 元数据。

Store Buffer 握手的顶层连接位于 `XSCore.scala:190` 和 `XSCore.scala:224`。

#### 7.5.3 Backend 到 Frontend 直连，Frontend 到 ICache 多一拍
`XSCore.scala:128` 与 `Frontend.scala:209` 的关键连接是：
```scala
// XSCore.scala
frontend.io.fencei <> backend.io.fenceio.fencei

// Frontend.scala
icache.io.flush  := ftq.io.icacheFlush
icache.io.fencei := RegNext(io.fencei)
```

因此：
- C4339 时 Backend 和 Frontend 的原始 `fencei` 是同一个组合网络；FST 中 `backend.io_fenceio_fencei`、`frontend.io_fencei` 和 Fence FU 输出共享物理 handle `33254`。
- C4340 时 Frontend 的显式 `RegNext` 输出有效；`ICache.io_fencei`、`metaArray.io_flushAll` 和 `missUnit.io_fencei` 共享物理 handle `290624`，只拉高一个周期。

#### 7.5.4 ICache 中的全量清空动作
`ICache.scala:634` 将 `fencei` 分发给 MetaArray 和 MissUnit：
```scala
metaArray.io.flushAll := io.fencei
metaArray.io.flush <> mainPipe.io.metaArrayFlush
...
missUnit.io.fencei := io.fencei
missUnit.io.flush  := io.flush
```

MetaArray 对 `flushAll` 的动作位于 `ICache.scala:362`：
```scala
// flush standalone set
when(io.flush.map(_.valid).reduce(_ || _)) {
  ... // only selected set/way
}

// flush all (e.g. fence.i)
when(io.flushAll) {
  (0 until nWays).foreach(w => valid_array(w) := 0.U)
}
```

C4340 的 `metaArray.io_flushAll=1` 因而直接把每个 way 的 valid bitmap 清零。同时：
- 4 个 fetch MSHR 和 10 个 prefetch MSHR 的 `io_fencei` 全部为 1。
- MSHR 阻止接收新请求和发出新的 acquire；尚未发出的条目可立即失效，见 `ICacheMissUnit.scala:127`。
- Priority FIFO 用 `io.flush || io.fencei` 清空，见 `ICacheMissUnit.scala:311`。
- 返回的 refill 在 `flush/fencei` 时不能写 Meta/Data SRAM，见 `ICacheMissUnit.scala:394`。

### 7.6 `flushPipe` 的 ROB/FTQ 恢复链路
这条链路发生在直接 `fencei/flushAll` 之后，作用是让前端从 `fence.i` 的下一条指令重新开始，而不是实现 tag 全量失效本身。

ROB 入队时，`flushPipe` 被记录为 `needFlush`：
```scala
// RobBundles.scala
robEntry.needFlush := robEnq.hasException || robEnq.flushPipe
```

代码见 `RobBundles.scala:139`。Fence output 在 C4339 产生后，CtrlBlock 先做一拍过滤/寄存，C4340 才写 ROB，对应 `CtrlBlock.scala:125` 和 `CtrlBlock.scala:763`。ROB 在头部确认 `commit_w`、ExceptionGen 的 `flushPipe` 和 ROB 身份匹配后产生 `deqHasFlushPipe/flushOut`，见 `Rob.scala:571` 和 `Rob.scala:622`。

后续逐周期事件如下：

| 周期 | 事件 |
| ---: | --- |
| C4340 | ROB 写回端口 7 收到 ROB23/flushPipe；同周期 ICache 的 `fencei/flushAll` 有效。 |
| C4341 | ROB23 已有 `commit_w=1`、`needFlush=1`，ROB 头部开始满足 flushPipe 识别条件。 |
| C4342 | `rob.io_flushOut.valid=1`，ROB23、FTQ10/off0、level=`flushAfter`。同周期确有 `FTQ.io_icacheFlush/ICache.io_flush=1`，但波形证明其来源是 IFU `pdWb.misOffset`：`fromIfuRedirect.valid=1`、`fromBackendRedirect.valid=0`；它与 ROB `flushOut` 同拍只是巧合。 |
| C4343 | `s1_robFlushRedirect.valid=1`，锁存 ROB23/FTQ10/off0。 |
| C4344 | CtrlBlock ROB flush 延迟链第 1 拍。 |
| C4345 | CtrlBlock ROB flush 延迟链第 2 拍。 |
| C4346 | CtrlBlock ROB flush 延迟链第 3 拍。 |
| C4347 | `s5_flushFromRobValidAhead=1`，即第 4 个 `DelayN` 阶段。 |
| C4348 | `s6_flushFromRobValid=1`，backend redirect 到达 Frontend/FTQ：FTQ10/off0、target=`0x80000050`。此时 `FTQ.io_icacheFlush=1`、`ICache.io_flush=1`，但 `ICache.io_fencei=0`、`MetaArray.io_flushAll=0`。 |
| C4349 | ROB/Difftest 出现 PC `0x8000004c`、instr `0x0000100f`、ROB23 的正常提交记录，`rfWen=0`。Frontend 用上一拍 redirect 刷新 IBuffer/取指状态。 |

CtrlBlock 将 ROB flush 定义为 T0 到 T6 的路径，见 `CtrlBlock.scala:333`：
```scala
val s5_flushFromRobValidAhead = DelayN(s1_robFlushRedirect.valid, 4)
val s6_flushFromRobValid = GatedValidRegNext(s5_flushFromRobValidAhead)
...
io.frontend.toFtq.redirect.valid := s6_flushFromRobValid || s3_redirectGen.valid

// T0: rob.io.flushOut
// T1: s1_robFlushRedirect
// ...
// T6: io.frontend.toFtq.stage2Redirect.valid
```

FTQ 的普通 ICache 流水冲刷由任意 backend 或 IFU redirect 触发，见 `NewFtq.scala:1261`：
```scala
val redirectVec = VecInit(backendRedirect, fromIfuRedirect)
io.icacheFlush := redirectVec.map(r => r.valid).reduce(_ || _)
```

不能把任意一次 `ICache.io_flush` 都称作 `fence.i` 全量失效。该波形中三次相关事件分别为：

| 周期 | `ICache.io_fencei` / `MetaArray.flushAll` | `ICache.io_flush` | 来源与语义 |
| ---: | ---: | ---: | --- |
| C4340 | 1 | 0 | Fence FU 直接路径；清空全部 ICache valid bits，并通知所有 MSHR |
| C4342 | 0 | 1 | IFU predecode redirect；普通取指流水/请求状态清空 |
| C4348 | 0 | 1 | ROB23 `flushPipe` 的 backend redirect；普通取指流水/请求状态清空 |

`FTQ.io_icacheFlush` 与 `ICache.io_flush` 在 FST 中共享 handle `305942`；它们和 handle `290624` 的 `fencei/flushAll` 是两张不同的物理网络。

### 7.7 对 Issue #6132 的补充认识
该对照实验给 #6132 的分析增加了四项具体证据：
1.  同一静态 `fence.i` 可以因为更老指令的 flush 出现多个动态副本；波形分析必须用 FTQ/ROB 身份区分副本，不能只按 PC 关联事件。
2.  ICache 全量元数据失效和前端流水 redirect 在实现中确实是两条路径，且相隔若干周期。只看 `io.fencei` 或只看 `io.flush` 都会误读完整语义。
3.  在这份 V2 波形中，实际 `fence.i` 于 C4340 完成 ICache 全量失效，于 C4348 才把前端重定向到下一条指令。这种时间差解释了为什么内部接口可能在两个控制事件之间仍有 `fence.i` 前取得的数据可见。
4.  实验观察到 `fence.i` 正常提交，且第一个错误路径副本被更老 fence 的 redirect 清除，说明使用 `cfVec.fire` 作为“执行过”的同义词是不成立的。

但该实验尚未回答 #6132 最关键的 V3 问题：C16750-C16751 的旧目标函数条目具有怎样的 FTQ 身份，它们来自 `fence.i` 前还是 `fence.i` 后，是否真正被后端接收，以及在哪一级被 kill。这个问题仍需要在 Issue 对应提交或等价 V3 构建上追踪 `cfVec -> dispatch -> ROB/LSQ -> retire`。

### 7.8 96 周期推测窗口的完整波形事件表
下面完整保留 `deep-analysis.md` 中的周期/事件表。主窗口定义为 **`[C4235, C4331)`**：包含第一次目标 `fence.i` 的 `cfVec.fire`，排除第二次、实际存活的 `cfVec.fire`。窗口共 96 个周期；如果严格采用开区间 `C4235 < C < C4331`，只需去掉 C4235 的两条记录，其他因果判断不变。

这些表格统一遵循同一个判定口径：只有 `valid && ready` 才算 `Decoupled` 传输；周期中的残留数据字段不算事件；动态身份由 PC、指令编码、FTQ pointer/offset 和 ROB 身份联合确定。这样可以把“波形上曾出现过的条目”“真正进入后端的条目”和“最终产生副作用的请求”分开。

阅读这些长表时，建议先看 7.8.1 的动态指令清单、7.8.2 的总时间线和 7.8.9 的状态汇总；需要核对具体因果关系时，再进入 7.8.3-7.8.8 查看后端、SQ/SBuffer、L1D、L1I 和 L2 的端口证据。

#### 7.8.1 窗口内进入后端的完整指令清单
窗口内 `cfVec.fire` 只发生在 C4235、C4242、C4287 三拍，共 11 条动态指令、10 个静态 PC；`0x800005c` 因预测回跳出现两个动态副本。

| 周期 / lane | PC / 指令 | 反汇编 | FTQ | 前端预测 | 到达的最深位置 | 最终结果 |
| --- | --- | --- | --- | --- | --- | --- |
| C4235 / 0 | `0x8000004c / 0000100f` | `fence.i` | 9/off12 | not-taken | C4286 Rename fire；候选 ROB23；随后停在 Dispatch | C4318 被冲刷；无 ROB/IQ/EXU |
| C4235 / 1 | `0x80000050 / 030000ef` | `jal ra,0x8000080` | 9/off14 | taken，call | C4286 Rename fire；候选 ROB24、pdest18；随后停在 Dispatch | C4318 被冲刷；RAT/free-list 回滚 |
| C4242 / 0 | `0x80000080 / 0005bf03` | `ld t5,0(a1)` | 10/off0 | not-taken | C4286 进入 Decode->Rename pipeline；C4287-C4317 Rename 侧 stall | 未实际 Rename/LSQ；C4318 被冲刷 |
| C4242 / 1 | `0x80000084 / 01100513` | `addi a0,zero,17` | 10/off2 | not-taken | 同上；只出现候选 ROB26/pdest20 | 未分配；C4318 被冲刷 |
| C4242 / 2 | `0x80000088 / 00008067` | `jalr zero,0(ra)` (`ret`) | 10/off4 | taken，ret | 同上；只出现候选 ROB27 | 未分配；C4318 被冲刷 |
| C4287 / 0 | `0x80000054 / 05a00e13` | `addi t3,zero,90` | 11/off0 | not-taken | Decode->Rename 输入，`ready=0` | C4318 被冲刷 |
| C4287 / 1 | `0x80000058 / 01c51863` | `bne a0,t3,0x8000068` | 11/off2 | not-taken | Decode->Rename 输入，`ready=0` | C4318 被冲刷 |
| C4287 / 2 | `0x8000005c / 000061b7` | `lui gp,0x6` | 11/off4 | not-taken | Decode->Rename 输入，`ready=0` | C4318 被冲刷 |
| C4287 / 3 | `0x80000060 / 00d1819b` | `addiw gp,gp,13` | 11/off6 | not-taken | Decode->Rename 输入，`ready=0` | C4318 被冲刷 |
| C4287 / 4 | `0x80000064 / ff9ff06f` | `jal zero,0x800005c` | 11/off8 | taken | Decode->Rename 输入，`ready=0` | C4318 被冲刷 |
| C4287 / 5 | `0x8000005c / 000061b7` | `lui gp,0x6`，第二动态副本 | 12/off0 | not-taken | Decode->Rename 输入，`ready=0` | C4318 被冲刷 |

前端实际展开的错误路径为：
```text
0x8000004c fence.i
0x80000050 jal/call  --预测 taken--> 0x8000080
0x80000080 ld
0x80000084 li a0,17
0x80000088 ret       --预测 taken--> 0x8000054
0x80000054 ...
0x80000064 j         --预测 taken--> 0x800005c
```

call、ret 和无条件跳转的方向都由前端预测产生。它们确实越过了 `cfVec` 边界，但这张表同时显示：错误路径最深只到 Decode/Rename/Dispatch 等待，不能把 `cfVec.fire` 当作 BJU 执行或指令退休。

#### 7.8.2 96 周期流水时间线
| 周期 | 事件 |
| --- | --- |
| C4235 | 第一个 `fence.i` 和 `jal` 的 `cfVec.fire`；两者进入 Decode->Rename pipeline 输入。 |
| C4236-C4285 | 两者停在 Decode->Rename 输出，持续 `valid=1, ready=0`。 |
| C4242 | `ld/addi/ret` 的 `cfVec.fire`；它们在前一组后方等待。 |
| C4286 | `fence.i/jal` 完成 Rename 并进入 Rename->Dispatch 寄存组；`ld/addi/ret` 同拍只进入 Decode->Rename pipeline。 |
| C4287 | 更老的普通 `fence`（`PC=0x80000048, ROB22`）进入 ROB；其 `blockBackward/flushPipe` 阻止所有年轻指令继续 Dispatch；同拍又有最后 6 条 `cfVec.fire`。 |
| C4287-C4317 | `fence.i/jal` 在 Dispatch 持续 `valid=1, ready=0`；`ld/addi/ret` 在 Rename 入口等待；最后 6 条在 Decode 入口等待。 |
| C4290-C4313 | ROB22 的普通 `fence` 在 Fence FU 等待 SQ/SBuffer 排空，随后完成。 |
| C4316 | `rob.io_flushOut.valid=1`，身份为 ROB22。 |
| C4317 | redirect 到达 Rename/Dispatch 清空路径。 |
| C4318 | 11 条年轻指令的各级 valid 消失；它们没有继续执行。 |
| C4322 | backend redirect 到达 Frontend，目标 `0x8000004c`。 |
| C4331 | 第二个、实际存活的 `fence.i` 重新 `cfVec.fire`；该拍不属于主窗口。 |

这条时间线给出窗口的安全审计边界：C4318 只证明年轻流水条目被 kill，并不证明窗口内所有其他请求都被撤销；Cache 层已经发出的请求可以在 C4318 之后继续完成。

#### 7.8.3 后端负证据交叉扫描
对 C4235-C4330 的所有相关端口做身份匹配，得到以下结果：

| 波形端口 / 检查范围 | 匹配结果 |
| --- | --- |
| `rob.io_enq.req[0..5]` | 没有候选 ROB23-27；C4287 的真实入队是更老的 `PC=0x80000048 / ROB22`。 |
| `dispatch.io_toIssueQueues[0..33]` | 没有候选 ROB23-27 的 enqueue。 |
| `dispatch.io_toMem.lsqEnqIO.req[0..5]` | 没有年轻 `ld` 的 LQ enqueue。 |
| 27 路后端 writeback | 没有候选 ROB23-27 的写回。 |
| ROB commit | 11 条窗口动态身份没有退休；C4283 的 `0x8000080/84/88` 属于更早 ROB11/12/13 副本。 |

按动作归并后：

| 检查动作 | 数量 / 结果 |
| --- | ---: |
| 真实 ROB allocation（匹配候选 ROB23-27） | 0 |
| IQ enqueue / issue | 0 |
| EXU/Fence FU 接受 | 0 |
| LQ/SQ allocation（匹配年轻指令） | 0 |
| Load/Store DCache 请求（匹配年轻指令） | 0 |
| 寄存器写回（匹配候选 ROB23-27） | 0 |
| 退休（匹配这 11 条动态身份） | 0 |

扫描中唯一的真实 ROB 入队是 C4287 的更老 `PC=0x80000048 / ROB22`。C4283 虽有 `0x8000080/84/88` 的提交记录，但属于更早的 ROB11/12/13 动态副本，不能与本窗口 FTQ10 的候选 ROB25/26/27 拼接。因而，年轻错误路径没有进入 LSQ 或发出 DCache 请求这一结论有端口级负证据支持，而不是只根据 `ready=0` 推测。

#### 7.8.4 Rename 和 redirect 的周期证据
| 周期 | Rename/恢复事件 |
| --- | --- |
| C4286 | `jal ra,0x8000080` 为 `x1/ra` 分配候选物理寄存器 p18；`fence.i` 得到候选 ROB23。 |
| C4288 | speculative RAT 的 `x1` 从架构映射 p8 暂时改为 p18；arch RAT 仍为 p8。 |
| C4317 | redirect 触发 speculative 状态恢复。 |
| C4320 | speculative RAT 的 `x1` 恢复为 p8，p18 回到可分配状态；p18 没有任何 writeback。 |

后续 `ld/addi/ret` 在组合输出上可能显示 pdest19/pdest20 等候选值，但 `intFreeList.io_doAllocate=0`，没有真实消耗物理寄存器。这个表说明 Rename 状态会在窗口内变化并可回滚，而 Cache 状态不具有同样的自动回滚保证。

#### 7.8.5 SQ/SBuffer 周期事件
窗口内 SQ/SBuffer 确实有活动，但来源是更老、最终提交的三条 store，而不是 11 条年轻错误路径指令。

三条 store 的动态身份和写入内容如下：

| PC / 指令 | 反汇编 | ROB / SQ | 地址 | 数据 | byte mask |
| --- | --- | --- | --- | --- | --- |
| `0x80000028 / 00542023` | `sw t0,0(s0)` | ROB17 / SQ0 | `0x80000080` | `0x05a00513` | `0x00f` |
| `0x8000002c / 00642223` | `sw t1,4(s0)` | ROB18 / SQ1 | `0x80000084` | `0x00008067` | `0x0f0` |
| `0x80000030 / 00424223` | `sw zero,8(s0)` | ROB19 / SQ2 | `0x80000088` | `0x00000000` | `0xf00` |

| 周期 | SQ/SBuffer 事件 |
| --- | --- |
| C4235 | ROB17 分配 SQ0；只是时间上与第一个 `fence.i cfVec.fire` 同拍，store 本身更老。 |
| C4236 | ROB18、ROB19 分配 SQ1、SQ2；`sqEmpty` 随后变为 0。 |
| C4239-C4240 | 三条 store 的地址和数据分别写入 SQ。 |
| C4242 | SQ0/1/2 均达到 `addrvalid && datavalid`；此时尚未提交，理论上仍可取消。 |
| C4285 | ROB 给 SQ `scommit=3`。 |
| C4288 | SQ0/1/2 的 `committed` 全部为 1。 |
| C4289 | SQ0、SQ1 同拍送入 SBuffer；两者同一 64 B line，`sameTag=1`，共同使用 entry1。 |
| C4290 | SQ2 命中 entry1 并继续 merge；最终合并 mask 为 `0x0fff`。 |
| C4291-C4292 | 三个 SQ entry 依次完成并释放。 |
| C4293 | `sqEmpty=1`。 |
| C4292-C4313 | 更老普通 `fence` 断言 SBuffer flush，促使 SBuffer drain。 |
| C4293 | SBuffer 进入 `x_drain_all`，选择 entry1。 |
| C4294 | SBuffer 向 DCache 发出 `addr=0x80000080, mask=0x0fff, id=1` 的整行 store 请求。 |
| C4309 | DCache 完成该请求，SBuffer entry1 被释放。 |
| C4311 | `sbempty=1` 且 `flush.empty=1`；一直保持到第二次 fire 前。 |
| C4318 | redirect 到达 SQ 时三项早已 committed 并释放，`sqCancelCnt=0`。 |

这里有一个对安全分析很重要的区别：SQ/SBuffer 的活动不是年轻错误路径造成的，但它们在窗口内把修改后的代码写入并安装到 DCache；redirect 只取消未提交项，不能撤销已经 committed 的 store。

#### 7.8.6 L1D 新 cache line 的周期归属
当前 DCache block size 为 64 B。窗口内归并所有 DCache refill/meta/tag/data write 后，只有以下两条 L1D line 被安装：

| line base | 原始请求发生时间 | 窗口内安装时间 | 来源 | 安装后的状态 | 是否由 11 条年轻指令造成 |
| --- | --- | --- | --- | --- | --- |
| `0x80001000` | C4228 load 请求；C4230 miss；C4231 Acquire | C4282 | 更老的 `ld t5,0(a1)`，ROB11/LQ0 | `coh=2`，Trunk | 否；请求和 Acquire 都早于 C4235 |
| `0x80000080` | C4294 SBuffer 请求；C4296 store miss；C4297 Acquire | C4309 | 更老的 ROB17/18/19 三条已提交 store | `coh=3`，Dirty | 否；是旧 store 的 write-allocate |

##### `0x80001000`：旧 load 的延迟完成
更老的动态 `PC=0x8000080, instr=0x0005bf03` 使用 ROB11/LQ0：C4225 分配 LQ，C4228 请求 `0x80001000`，C4230 形成 load miss，C4231 已发出 64 B Acquire；这些事件都早于 C4235。其返回落在窗口内：

| 周期 | L1D refill 证据 |
| --- | --- |
| C4281 | `DiffRefillEvent.valid=1, addr=0x80001020, mask=0xff`；这是当前/最后一个 refill beat 地址，不是 line base。 |
| C4282 | MainPipe `meta_write.valid=1, idx=0x40, way_en=1, coh=2`，同时 `data_write.valid=1, wmask=0xff, addr=0x80001000`；证明完整 64 B line 安装到 L1D。 |

`0x80001020` 按 64 B line 归一化后是 `0x80001000`。因此，C4282 的真实 Cache 状态变化发生在 speculative window 内，但不能归因于窗口内的年轻 `ld`。

##### `0x80000080`：旧 store 的 write-allocate 周期证据
| 周期 | DCache 证据 |
| --- | --- |
| C4295 | MainPipe s1：`source=STORE, addr=0x80000080, tag_match=0`。 |
| C4296 | MainPipe s2：`hit=0, coh=Nothing`；`miss_req.fire`，`store_mask=0x0fff`。 |
| C4297 | TileLink Acquire：`opcode=6, param=1, size=6`，请求 64 B block `0x80000080`。 |
| C4305 | refill request 回到 MainPipe，`source=1, cmd=1, addr=0x80000080`。 |
| C4306-C4307 | 收到原 line；低 128 位为 `0x0000001300008067011005130005bf03`。 |
| C4308 | `DiffRefillEvent.valid=1, addr=0x800000a0, mask=0xff`；归一化 line base 为 `0x80000080`。 |
| C4309 | `tag_write/meta_write/data_write` 同拍有效：set `0x2`、way0、tag `0x80000`、`coh=Dirty`、`wmask=0xff`。 |

返回的原 line 低四个指令字及合并结果为：
```text
0x80000080: 0005bf03   ld t5,0(a1)
0x80000084: 01100513   addi a0,zero,17
0x80000088: 00008067   ret
0x8000008c: 00000013   nop

bank0 = 0x0000806705a00513  # 0x80: li a0,90; 0x84: ret
bank1 = 0x0000001300000000  # 0x88: 0;        0x8c: nop
mask  = 0x0fff
```

因此 C4309 是一次明确的 write-allocate/cold miss，而不是已有 line 上的普通 store hit。到 C4330，`0x80000080` 仍以 Dirty 状态驻留 L1D；窗口内没有 DCache writeback 或 TileLink Release，状态没有被回滚。

#### 7.8.7 L1I 在窗口内的周期证据
| 周期 / 范围 | L1I 事件 |
| --- | --- |
| C4203（窗口前） | 目标 `0x80000080` line 最近一次 ICache refill；旧 `ld/li/ret` 已装入 L1I。 |
| C4235-C4330 | ICache `meta_write.valid`、`data_write.valid`、`DiffRefillEvent.valid` 全部为 0；没有新的 L1I miss allocation 或 Acquire。 |
| C4239-C4242 | 错误路径 call 命中并 touch 旧的 `0x80000080` line，随后 C4242 将旧 `ld/li/ret` 送入后端。 |
| C4340（窗口外） | 第二个、实际存活的 `fence.i` 才使 `ICache.io_fencei=1`、`MetaArray.flushAll=1`，清空全部 L1I valid bitmap。 |

波形中目标 set2/way0 的 set-PLRU state 在 touch 前后都为 `0b101`；这次 hit 没有翻转目标 set 的既有 PLRU bits。关键瞬态因此是：
```text
L1I[0x80000080]：仍是 C4203 装入的旧 ld/li/ret
L1D[0x80000080]：C4309 新装入并由 store 覆盖，状态 Dirty
```

此外，窗口前已发出的 6 组 ICache prefetch 在 C4237-C4263 返回，但因 MSHR 已被 flush，`write_sram_valid = fetch_resp_valid && !flush && !fencei` 为 0；它们没有写入 L1I。这解释了“L1I 零安装”与“L2 仍有安装”可以同时成立。

#### 7.8.8 L2 line 安装与持久 footprint
对 CoupledL2 四个 slice 同时扫描 MainPipe tag/meta write 与 DataStorage 真实写使能，主窗口内有 6 次真实的新 line 安装：

| 周期 | L2 line base | 请求来源 | L2 安装状态 | 与本窗口 11 条年轻指令的关系 |
| --- | --- | --- | --- | --- |
| C4238 | `0x800002c0` | C4192 发出的 ICache prefetch | TIP，clients=0，dirty=0 | 请求早于窗口，且已在 L1I 被 flush |
| C4243 | `0x80000300` | C4197 发出的 ICache prefetch | TIP，clients=0，dirty=0 | 同上 |
| C4250 | `0x80000340` | C4202 发出的 ICache prefetch | TIP，clients=0，dirty=0 | 同上 |
| C4253 | `0x80000380` | C4207 发出的 ICache prefetch | TIP，clients=0，dirty=0 | 同上 |
| C4258 | `0x800003c0` | C4212 发出的 ICache prefetch | TIP，clients=0，dirty=0 | 同上 |
| C4276 | `0x80001000` | ROB11/LQ0 的旧 load | TRUNK，clients=1，dirty=0 | load 请求早于窗口 |

前五条 prefetch 在 L1I 的 MSHR 已被 flush，所以不会进入 L1I；但更下层 miss 不能撤销，最终仍向 L2 tag/meta/data SRAM 写入，在窗口结束后保留可持续的 L2 cache footprint。它们属于窗口前启动的前端预取活动，不属于 11 条年轻后端动态指令，更不能归因于被冲刷的年轻 `ld`。

`0x80000080` 的 store Acquire 于 C4299 到达 L2，C4301 的 directory result 为 hit，只有 metadata write：旧状态 `TIP, clients=0, accessed=0` 变为 `TRUNK, clients=1, accessed=1, dirty=0`；没有 L2 tag/data write、L2 MSHR allocation 或新的 CHI 请求。这是 metadata-only hit，不是第七条 L2 line 安装。

| 周期 | L2 metadata-only hit 证据 |
| --- | --- |
| C4299 | `0x80000080` 的 store Acquire 到达 L2。 |
| C4301 | directory result 为 hit；仅写 coherence/client/access metadata，状态由 `TIP, clients=0, accessed=0` 变为 `TRUNK, clients=1, accessed=1, dirty=0`。 |

#### 7.8.9 窗口结束时各类状态的归属
| 状态 | 窗口内是否变化 | 变化来源 | 第二次 fire 前状态 |
| --- | --- | --- | --- |
| Frontend/IBuffer/FTQ 流水状态 | 是 | 11 条错误路径指令的预测取指与排队 | ROB22 redirect 后清空并重取 |
| speculative RAT / int free-list | 是，短暂 | 错误路径 `jal` 为 x1 分配 p18 | 已恢复为 x1->p8，p18 可重用 |
| ROB | 否，针对这 11 条 | 仅更老 ROB22 在窗口内推进 | 年轻候选 ROB23-27 均未建立 entry |
| IQ / EXU / WB | 否，针对这 11 条 | 无 | 无年轻执行残留 |
| LQ | 否，针对这 11 条 | 旧 ROB11 load 等待 refill | 年轻 `ld` 从未分配 LQ |
| SQ | 是 | 旧 ROB17-19 stores 分配、提交、释放 | C4293 起为空 |
| SBuffer | 是 | 旧 stores merge；普通 fence 促使 drain | C4311 起为空 |
| L1D line `0x80001000` | 是 | 窗口前发出的旧 load miss | 新 Trunk line 保留 |
| L1D line `0x80000080` | 是 | 已提交旧 stores 的 write-allocate | 新 Dirty line 保留，未写回 |
| L1I tag/data line | 否 | 只有 hit；无 refill/write | 仍保留旧 `0x80000080` line |
| L1I set2 PLRU metadata | touch 但本次未翻转 | 错误路径 hit 到 `0x80000080`/way0 | set2 保持 `0b101` |
| ICache 全量失效 | 否 | 第一个 `fence.i` 被冲刷 | 真正失效发生在窗口外 C4340 |
| L2 五条 ICache-prefetch line | 是 | 窗口前发出、后来在 L1I 被 flush 的 prefetch | 新 TIP line 保留 |
| L2 line `0x80001000` | 是 | 旧 ROB11 load | 新 TRUNK line 保留 |
| L2 line `0x80000080` metadata | 是 | 旧 committed stores 的 Acquire hit | TIP/无 client -> TRUNK/client1；无 tag/data 新装 |

### 7.9 96 周期窗口的安全含义
这些周期表把“没有年轻指令退休”和“没有安全风险”明确分开：

1.  **窗口不是空白期**。C4235-C4330 内有 11 条错误路径指令经过前端/后端边界，并占用 Decode/Rename/Dispatch 流水；同时，窗口前启动的 load 和 prefetch 仍在 Cache 层次中完成。
2.  **Cache 状态确实发生变化**。L1D 在 C4282、C4309 安装两条 line；L2 在 C4238、C4243、C4250、C4253、C4258、C4276 安装六条 line。到 C4331 前，L1D Dirty line 和 L2 TIP/TRUNK line 仍存在，redirect 没有把它们恢复到窗口前状态。
3.  **这构成安全相关的微架构暴露面**。Cache 的 tag、coherence 状态、替换状态和访问延迟可能被后续代码观测；与可回滚的 speculative RAT 不同，已经完成的 refill、write-allocate 和 L2 metadata 更新不会因为 C4318 的 redirect 自动撤销。即使没有旧指令退休，也不能据此排除状态型侧信道或跨上下文信息泄露风险。
4.  **因果归因仍需谨慎**。当前 V2 波形的端口级扫描显示，11 条年轻指令没有进入 LSQ/EXU，也没有发出 DCache 请求；上述 line 的直接来源是更老的 ROB11 load、ROB17-19 committed stores，以及窗口前发出的 ICache prefetch。因此已确认的是“speculative window 与持久 Cache 状态变化重叠，攻击面真实存在”，尚未确认的是“被 `cfVec` 观察到的年轻旧 `ld` 已亲自造成秘密相关 Cache 访问”。
5.  **对 #6132 的工程含义**。即便现有 redirect 契约足以避免已观察到的功能错误，仍有充分理由把 96 周期窗口作为安全硬化目标：在 `fence.i` 或其伴随 redirect 之前隔离旧请求，阻止过期 prefetch 的下层落地，或在安全边界上清理/标记不可观察的 Cache 状态，并为这些不变量增加波形断言和定向测试。

风险证据可以分成三层：

| 风险层级 | 当前波形支持的命题 | 安全含义 |
| --- | --- | --- |
| **已证实：微架构暴露面** | 96 周期内有旧 `cfVec` 条目；L1D 安装 2 条 line、L2 安装 6 条 line；若干 line 在 C4318 redirect 后仍保留 | Cache tag、coherence、替换和访问延迟等状态可能被后续代码观测；不能把 redirect 当作 Cache 状态回滚 |
| **合理担忧：潜在利用链** | 过期前端活动可越过 L1I 的 MSHR flush 并落到 L2；旧代码和新数据在不同 Cache 层同时存在 | 在不同压力、参数或实现版本下，若旧指令在 kill 前获得 issue 机会，秘密相关访问可能把这类状态差异转化为侧信道；需要 V3 和攻击者可控实验验证 |
| **尚未证实：完整漏洞** | 本窗口 11 条年轻指令均未进入 LSQ/EXU/DCache；未观察到选定探针访问、写回或退休 | 不能声称已形成可稳定利用的 Spectre 风格侧信道，也不能声称年轻旧 `ld` 是这些 Cache line 的直接来源 |

因此，本节的安全结论不是“已证明可利用”，也不是“没有风险”，而是：**96 周期推测窗口内存在真实且部分持久的 Cache 状态变化，这足以构成需要审计和收敛的安全暴露面；利用链是否能由年轻旧指令稳定控制，仍需在 Issue 对应的 `kunminghu-v3` 波形上继续验证。**

## 8. `fence.i` 前端安全硬化方案
本章回答一个具体工程问题：能否在前端预译码阶段识别 `fence.i`，尽早阻止更年轻指令继续前进，从而缩短第 7 章观察到的推测窗口？

答案是：**可以在预译码阶段早识别、早截断，但真正的 ICache 全量失效仍必须由后端按序授权。** 推荐方案遵循三条原则：
1.  预译码负责发现 `fence.i`，并阻止更年轻的取指和指令进入后端。
2.  Fence FU 负责等待更老的 store 排空，再发出 ICache 失效请求。
3.  ICache 负责拒绝 `fence.i` 前请求的迟到响应，并在失效完成后返回确认；前端收到确认和后端重定向后才能恢复取指。

### 8.1 设计目标与总体结论
前端早期隔离和按序失效是两个不同职责：
- **前端早期隔离**：预译码识别出 `fence.i` 时，保留该指令，截断同一个取指块中位于它之后的槽位，并停止发出 `fence.i` 后的新取指。
- **按序 ICache 失效**：确认 `fence.i` 已经进入正确路径，而且此前的 store 已满足可见性要求后，再失效 ICache；完成后从 `fence.i` 的下一条指令恢复取指。

| 措施 | 是否采用 | 原因 |
| --- | --- | --- |
| 在预译码信息中增加 `isFenceI` | 是 | 只产生前端标记，不直接改变 Cache 状态 |
| 截断同一取指块中位于 `fence.i` 之后的槽位 | 是 | 能立即缩小送入 IBuffer 的年轻指令范围 |
| 预译码一识别就直接执行 `flushAll` | 否 | 预译码仍处于推测路径，且此时不能保证更老 store 已排空 |
| 用前端屏障阻止 `fence.i` 后的年轻指令 | 是 | `fixedRange` 只能约束当前取指块，无法覆盖 FTQ、IBuffer 和迟到响应 |
| 为 ICache 增加请求/完成握手 | 建议 | 当前一拍 `fencei` 脉冲不能向前端表达完整失效何时结束 |

目标安全属性可以写成：
> 从有效的 `fence.i` 屏障标记建立开始，任何比它年轻的指令都不能在 ICache 失效完成前产生 `cfVec.fire`、进入执行或发起新的取指请求；完成后必须从 `fence.i` 后的顺序 PC 恢复取指。

更老的指令和 `fence.i` 本身仍要按序排空。若把它们也一起阻塞，反而可能使 `fence.i` 永远无法到达后端，形成死锁。

### 8.2 为什么不能由预译码直接失效 ICache
预译码看到的 `fence.i` 仍处于推测路径，尚未获得后端的按序确认。若预译码直接驱动 `MetaArray.flushAll` 或清空 MSHR，会产生以下问题：
- 错误路径的 `fence.i` 造成真实 Cache 失效，之后即使被更老的 branch redirect kill，失效也不能回滚；
- 在前序 store 尚未排空时就失效 ICache，后续 refill 可能重新看到旧内存内容，破坏 `fence.i` 的顺序语义；
- 同一静态指令的多个动态副本可能重复触发失效，并与现有后端重定向产生优先级或活锁问题；
- 如果前端在等待“失效完成”的同时阻止 `fence.i` 自身进入后端，就会形成环形等待。

因此，预译码只能产生无副作用的 `fencei_hint`：它可以截断当前取指块、建立可取消的前端屏障候选，但不能直接发出真正的 ICache 失效请求。实际请求必须由后端按序路径授权。

### 8.3 为什么只修改 `fixedRange` 还不够
当前 `PreDecode.scala:361-375` 的 `remaskFault/fixedRange` 只根据 `jal/jalr/ret` 预测错误计算掩码；`IFU.scala:953-960` 再用这个掩码生成 IBuffer 入队。即使把 `fence.i` 纳入该掩码，也只能保证同一个取指块中 `fence.i` 后的槽位不入队，不能自动清除：
- 已经位于 IBuffer 的更年轻条目；
- FTQ 中已经分配的后续表项；
- IFU 各级寄存器和跨取指块半条指令状态；
- 在 `fence.i` 之前发出、但在之后才返回的 ICache/MSHR 响应；
- 已经向 L2 发出的预取或 Acquire 请求。

要达成安全属性，必须增加前端屏障状态，为各条目记录它位于 `fence.i` 前还是后，并为迟到响应定义丢弃规则。`fixedRange` 只是包内截断机制，不是完整的前端隔离协议。

### 8.4 当前实现：前端、后端和 ICache 的处理链
下表基于第 7 章使用的 `kunminghu-v2` 源码快照，目的是说明现有控制链。实施 V3 修改前，仍需在 Issue 对应提交上逐项核对接口名称和时序。

| 位置 | 当前行为 | 关键证据 |
| --- | --- | --- |
| 前端 PreDecode | `PreDecodeInfo` 只有 `valid/isRVC/brType/isCall/isRet`，`brTable` 只识别跳转/分支；`fence.i` 被当作非 CFI 普通字 | `PreDecode.scala:72-82`、`predecode.scala:22-43` |
| F3 重算 | F3Predecoder 只复制 branch/call/ret 字段；若新增字段，必须同步复制，否则 `f3PdDiff` 会报错或信息丢失 | `PreDecode.scala:264-278`、`IFU.scala:625-637` |
| IFU/IBuffer | `fixedRange` 不截断 `fence.i` 后的槽位；`io.toIbuffer.bits.enqEnable` 只看该掩码和指令有效位 | `IFU.scala:953-960` |
| FTQ predecode 压缩 | `Ftq_pd_Entry` 只保存 branch/jump/rvc 信息，`toPd` 会重新构造 `PreDecodeInfo`；新字段若需要跨 FTQ 写回，必须扩展该 bundle | `NewFtq.scala:95-129` |
| 后端 Decode | `FENCE_I -> FuType.fence/FenceOpType.fencei`，同时 `noSpec=true, blockBack=true, flushPipe=true` | `DecodeUnit.scala:228-230` |
| Dispatch/ROB | `blockBackward` 会阻挡后续 dispatch，并使 ROB 接受逻辑把屏障 uop 作为特殊边界；它发生在后端看到 uop 之后，不能替代前端早期隔离 | `NewDispatch.scala:720-837`、`CtrlBlock.scala:717` |
| Fence FU | 先在 `s_wait` 断言 SBuffer flush，等待 `sbIsEmpty`；随后 `s_icache` 保持一拍 `fencei` 脉冲 | `Fence.scala:47-87` |
| 信号前递 | `XSCore` 将 `backend.fenceio.fencei` 直接接到 Frontend；Frontend 再延迟一拍接到 ICache | `XSCore.scala:139`、`Frontend.scala:218-224` |
| ICache | `fencei` 直接清空 MetaArray valid，并通知 MissUnit；普通 MainPipe/WayLookup/PrefetchPipe 的 flush 仍来自 FTQ redirect | `ICache.scala:635-691`、`NewFtq.scala:1261-1266` |
| 在途响应 | MissUnit 在 `fencei/flush` 时禁止新的请求并禁止 SRAM 写入，但源码明确说明同拍仍可能向 MainPipe/PrefetchPipe 送出响应，依赖下游丢弃 | `ICacheMissUnit.scala:141-184`、`ICacheMissUnit.scala:394-399` |

现有链路的要点是：**后端 `blockBack` 和 redirect 负责阻止并淘汰 `fence.i` 后的年轻指令，专用 `fencei` 负责 ICache 元数据/MSHR 失效；两者是互补路径而不是同一个脉冲。** 当前接口没有 `invalidateDone`，所以“直到 Cache 刷完”并不是前端可观察的协议状态。

### 8.5 推荐方案：两阶段屏障和显式完成握手
推荐把硬化后的路径定义为下面的状态机，而不是把预译码结果直接接到 `flushAll`：

```text
前端预译码
  └─ fencei_hint（无副作用）
       ├─ 当前取指块：保留 fence.i，清除其后槽位
       └─ 建立可取消的前端屏障，标记条目位于 fence.i 前还是后
            ├─ 停止新的年轻取指、预取和 cfVec 输出
            ├─ 清理或标记 IBuffer、FTQ、IFU 中 fence.i 后的条目
            └─ 等待后端按序 fence.i

后端译码/Fence FU（保留现有语义）
  └─ blockBackward + 等待 SBuffer 清空
       └─ fencei_req（带握手的失效请求）
            └─ ICache 执行失效
                 ├─ 清有效位、取消或封存相关 MSHR
                 ├─ 丢弃 fence.i 前请求的迟到响应，禁止其写入 L1I SRAM
                 └─ fencei_done
                      └─ 后端完成/重定向 + 前端释放屏障
                           └─ 从 fence.i 后的顺序 PC 恢复取指
```

修改前后关键差异如下：

| 阶段 | 修改前 | 推荐修改后 | 安全意义 |
| --- | --- | --- | --- |
| 前端识别 | 不识别，继续按普通非 CFI 取指 | 标记 `isFenceI`，同一取指块只保留到屏障标记 | 缩短 `cfVec` 窗口，不产生 Cache 副作用 |
| 当前取指块之后 | 可能继续进入 IBuffer/后端早期级 | 前端屏障和 `fence.i` 前后标记阻止更年轻条目 | 防止其在被丢弃前进入可观测接口 |
| FTQ/IBuffer | 只有普通重定向才全局冲刷 | 对 `fence.i` 后的表项做选择性清理或按前后位置过滤 | 覆盖预译码到后端重定向之间的空档 |
| 后端译码 | `FuType.fence + fencei`, `noSpec/blockBack/flushPipe` | 保持不变，作为架构授权点 | 保证顺序执行和前序 store 排空 |
| Fence 信号 | `fencei: Bool` 一拍脉冲 | `fencei_req`/`fencei_done` 握手，或等价的等待/应答状态 | 可表达真实完成，避免重复或丢失请求 |
| ICache | MetaArray/MissUnit 失效；完成时间不可回传 | 失效期间阻塞请求，丢弃 `fence.i` 前请求的迟到响应，并明确完成时刻 | 防止过期响应再次进入 IFU/L1I |
| 恢复取指 | 依赖后端重定向的时序 | 完成后配合唯一重定向从顺序 PC 重启 | 避免重复重定向和错误路径重放 |

### 8.6 实现计划
#### 阶段 0：先固定安全不变量和接口语义
在改 RTL 前写下并评审以下不变量，并给每个不变量分配波形信号：
- `fence.i` 屏障标记本身可以到达后端，但比它年轻的 uop 不能在失效完成前产生 `cfVec.fire`、dispatch fire、ROB/IQ/LSQ 分配、EXU issue 或 DCache/ICache 新请求。
- 只有后端按序的 `fence.i` 才能发出实际 `fencei_req`；错误路径上的屏障候选只能被取消，不能驱动 ICache 失效。
- ICache 收到请求后，不再接受新的取指或预取；`fence.i` 前请求的迟到响应不能写 MetaArray/DataArray，也不能作为有效指令送给 IFU。
- 一个 `fencei_req` 只能对应一个 `fencei_done`；复位、更老的重定向或异常都能解除尚未获得后端授权的屏障候选，不得活锁。
- 释放条件是 `fencei_done` 和正确的后端重定向/顺序 PC 都已建立，而不是只看到一次组合 `flushAll` 信号。

建议显式建模 `NONE -> SEEN -> WAIT_COMMIT -> INVALIDATING -> RELEASE` 五个状态，并定义老 redirect 对 `SEEN/WAIT_COMMIT` 的取消优先级。

#### 阶段 1：增加共享的 `fence.i` 预译码元数据
修改范围：
1.  在 `PreDecode.scala:72-82` 的 `PreDecodeInfo` 增加 `isFenceI`（或更明确的 `isFenceIHint`），并更新所有 `DontCare` 和默认赋值。
2.  主 PreDecode 和 `F3Predecoder:267-278` 应复用与后端一致的 `FENCE_I` 解码定义，避免前后端规则随版本演进而不一致。
3.  检查 `PreDecodeInfo` 宽度变化对 `IFU.scala:636` 的 `f3PdDiff`、触发器接口、调试打印和任何 `asUInt` 比较的影响。
4.  如果屏障标记需要跨 FTQ 预译码写回保存，扩展 `NewFtq.scala:95-129` 的 `Ftq_pd_Entry`；否则应明确保证前端屏障使用原始 F3/IFU 信息，不依赖会丢字段的 `toPd` 重建。
5.  为 MMIO/跨页/last-half 路径定义一致结果。标准 `fence.i` 通常是 cacheable instruction，不应因为只测主 ICache 路径而遗漏异常路径。

交付物：一个只增加解码元数据、不改变 Cache 状态的 `isFenceI` 补丁，以及覆盖 `FENCE_I`、其他屏障/系统指令和非法指令的正反例单元测试。

#### 阶段 2：截断同一取指块中的年轻槽位
修改 `PredChecker.scala` 的现有逻辑（实际位于 `PreDecode.scala:361-375`）：
- 计算 `fenceiIdx`，只选择当前 `instrRange/instrValid` 中最早的有效 `isFenceI`；
- 将 `fixedRange` 截断为“包含屏障槽位、清除所有更年轻槽位”；
- 不把它伪装成 jal/jalr，不生成普通 `wb_redirect`，也不更新 BTB/RAS；
- 检查屏障位于取指块首/尾、16/32 位指令邻接、跨块半条指令和 cache-line 边界的所有组合；
- 明确同拍 CFI fault、异常和更老重定向的优先级：更老重定向应取消该取指块，不能让错误路径屏障锁住前端。

这一步只保证 `io.toIbuffer.bits.enqEnable` 的包内行为，不能单独声称已经完成全前端隔离。

#### 阶段 3：增加前端屏障、选择性清理和 `fence.i` 前后标记
在 IFU/Frontend/IBuffer/FTQ 之间增加一个专用控制协议：
1.  F3 预译码看到有效屏障时，先置位一个 **可取消的 `fencei_seen`**，立即阻止 `fence.i` 后的取指。屏障所在取指包必须保持稳定直到 `io.toIbuffer.fire`，再把 `ftqPtr + ftqOffset + PC` 及其前后边界信息提交为 `fencei_pending`。若更老的重定向或异常先到，应取消 `fencei_seen`，不能留下死锁状态。
2.  屏障取指包被接受后，保留 `fence.i` 自身和它之前的更老指令；`fence.i` 后的槽位不得旁路到 `cfVec`。如果 IBuffer 已经含有更年轻条目，必须根据它位于 `fence.i` 前还是后，或根据指令序号，进行选择性过滤。
3.  停止从 FTQ 接受新的年轻取指和预取请求；对于已经位于 IFU 管线中的响应，检查其请求位于 `fence.i` 前还是后，并丢弃应被清理的迟到响应。
4.  对 IBuffer 中 `fence.i` 后的条目使用选择性清理或按前后位置过滤。不能无条件复用现有 `ibuffer.io.flush`，因为它会把 `fence.i` 自身和可能仍需完成的更老指令一起清掉。若硬件只能全清，应明确设计“保存屏障、清空其余、再按序重放”的机制。
5.  FTQ 后续表项要么在分配时携带表示其位于 `fence.i` 前还是后的标记，并在屏障时失效；要么由一个 fence 屏障指针限制。不能只依赖 `io.icacheFlush` 的普通重定向。
6.  `cfVec` 输出端不能简单地把所有 lane 置零：只能屏蔽 `fence.i` 后的年轻条目，允许更老指令和屏障本身按序排空，并保持 Decoupled 握手稳定性。测试必须证明后端背压不会让屏障本身丢失。

建议明确标记每个请求位于 `fence.i` 前还是后，并按此过滤。这能处理“旧请求晚返回”，而不要求物理撤销已经发出的 TileLink 事务。实现该标记的字段可以很小，但必须覆盖未完成请求的最长寿命；复用某个标记值前，应确认使用该值的旧请求已经全部清空。

#### 阶段 4：增加后端授权与 ICache 完成握手
后端 Decode 的 `FENCE_I` 属性暂不改变。改动重点在 `Fence.scala:64-87`：
- 保留 `s_wait` 对 SBuffer 的排空要求；
- 将 `fencei` 从无条件一拍脉冲改为 `Valid/Decoupled fencei_req`，或者增加等待/应答状态，使 Fence FU 在 ICache 确认前不会报告完成；
- 在 `XSCore -> Frontend -> ICache` 增加 `fencei_done` 返回路径。若为了时序必须继续用一拍脉冲，也要定义精确的“接受周期、最后一个旧响应周期、完成周期”，并用寄存器隔离组合环；
- ICache 在 `fencei_req.fire` 后执行 MetaArray 全相失效、MSHR 标记/取消，以及 MainPipe/PrefetchPipe/WayLookup 的请求禁止和 `fence.i` 前请求迟到响应的丢弃；只有这些动作全部满足后才能产生 `done`；
- `done` 至少应保证两件事：全部 L1I 有效位已经清除；每个 `fence.i` 前的 MSHR 要么已经排空，要么已被永久标记为 `fence.i` 前请求，之后的返回只能被吸收，不能重新进入 L1I/IFU。这里的“完成”不表示下层 Cache 已撤销所有在途事务；
- 现有 MissUnit 注释说明 `flush/fencei` 同拍仍可能送出响应。安全实现必须在 IFU/PrefetchPipe 入口增加 `fence.i` 前后标记/有效位门控，不能把“不写 SRAM”误当成“不产生可观测响应”；
- 只允许一个模块负责发起实际全相失效，避免既由 predecode 又由 Fence FU 发出两次 invalidate。

数据阵列清零/随机化可以作为独立安全模式评估。架构正确性通常只需要清 valid；但如果威胁模型包含 SRAM 残留的功耗/电磁观测，清零或随机化应有单独的面积、功耗和时序预算，不能把它混入第一版功能修复的必要条件。

#### 阶段 5：只保留一条恢复路径
恢复顺序建议固定为：
```text
fencei_done
  -> Fence FU 完成 fence.i
  -> 后端产生唯一的 flushPipe/redirect
  -> FTQ/IFU/IBuffer 清理由 fence.i 前请求取得、且已经失效的条目
  -> 从 fence.i 的顺序后继 PC 重新取指
  -> fence.i 后的第一条目标指令允许进入 cfVec
```

不要为 `fence.i` 复用普通预测故障的 `wbRedirect`：这种重定向会混入预测故障统计，可能更新 FTQ/BPU，并与后端的正式重定向重复。需要提前停止流水时，应使用不参与预测器训练的专用前端屏障控制。

#### 阶段 6：检查性能、兼容性和发布门槛
- 评估 `isFenceI` 组合匹配对现有预译码关键路径的影响，必要时复用共享译码逻辑或增加寄存级；
- 对无 `fence.i` 的程序，前端屏障状态必须保持空闲，不能改变普通取指/IBuffer 时序；
- 确认多核语义仍遵循 RISC-V：本地 `fence.i` 不替代跨 hart 的 IPI/同步协议；
- 先以断言和波形通过为发布门槛，再评估 data-array 清零等更强策略。

### 8.7 测试计划
测试应同时覆盖功能、协议、性能和侧信道可观察状态。所有测试都要记录 `fencei_hint`、`fencei_req`、`fencei_done`、`fence.i` 前后标记、FTQ/IBuffer、`cfVec`、重定向、ROB/LSQ/EXU 和 ICache/L1D/L2 事件，不能只看最终寄存器值。

#### 8.7.1 解码与前端单元测试
| 用例 | 覆盖点 | 通过条件 |
| --- | --- | --- |
| `fencei_decode` | PoC 使用的 `fence.i` 指令 | 主 PreDecode、F3Predecoder 和后端 Decode 的识别结果一致 |
| `other_instruction_negative` | `FENCE`、CSR、ECALL/EBREAK 和非法指令 | 均不误报为 `fence.i` |
| `decode_policy_match` | 后端接受和拒绝的边界样本 | 前端提示与后端合法性策略一致 |
| `packet_position` | lane 0、中间和最后一个 lane | 保留屏障槽位，所有更年轻 lane 的 `enqEnable=0` |
| `last_half_and_rvc` | 16/32 位指令邻接、跨取指块和 cache line | 不丢失屏障，也不误截断前一条指令 |
| `f3_pd_consistency` | F2/F3 处理相同取指包 | `f3PdDiff` 不触发，`isFenceI` 在两级一致 |
| `ftq_pd_roundtrip` | 预译码写回后调用 `toPd` | 若字段跨 FTQ 保存则不能丢失；若不保存则屏障不得依赖重建字段 |
| `mmio_fault_priority` | MMIO/取指异常与屏障同拍 | 更老异常或重定向优先，不留下不可取消的屏障 |

#### 8.7.2 架构功能与自修改代码测试
| 用例 | 场景 | 通过条件 |
| --- | --- | --- |
| `smc_basic` | store 新指令 -> `fence rw,rw` -> `fence.i` -> jump | 只执行新指令，结果与 Spike/QEMU 一致 |
| `smc_icache_hit` | 旧目标已在 L1I，复现 #6132 直接加载 PoC | 旧字可在内部短暂出现也必须被 kill；新字最终退休 |
| `smc_line_boundary` | 目标跨 ICache line/set 边界 | 两条 line 都正确失效，无旧 line 重放 |
| `smc_no_store` | 无前序 store 的 fence.i | 不死锁，仍只发生一次 invalidate |
| `smc_many_stores` | SQ/SBuffer 满、合并和延迟 drain | `fencei_req` 只能在 `sbIsEmpty` 后出现 |
| `fencei_consecutive` | 连续两个/多个 fence.i | 每个请求一一对应 done，不重复或遗漏 |
| `fencei_exception_redirect` | `fence.i` 前异常、更老分支预测错误或后端重定向 | 更老事件取消候选屏障，架构路径可恢复 |
| `fencei_page/cache_race` | 页边界、ITLB miss、cache miss 同时发生 | 过期响应不能成为 `fence.i` 后的有效取指结果 |
| `multi_hart` | 一个 hart 修改代码，另一个 hart 通过同步后取指 | 只验证架构规定的本地/跨 hart 语义，不把本地 fence.i 当全局 IPI |

#### 8.7.3 握手、并发和死锁测试
| 用例 | 强制条件 | 通过条件 |
| --- | --- | --- |
| `mshr_inflight_on_fencei` | 多个取指/预取 MSHR 已发出 | 停止新请求；旧响应不写 L1I SRAM，也不产生有效 IFU 指令 |
| `grant_same_cycle` | TileLink grant 与 `fencei_req/done` 同拍 | 优先级确定，既不重复写也不丢失完成确认 |
| `mshr_full` | Fence 到达时 MSHR 满 | 屏障最终释放，无环形背压 |
| `ibuffer_full` | 屏障到达时 IBuffer 满或输出停顿 | 屏障不丢；年轻表项不旁路；最终能够排空 |
| `ftq_ahead` | FTQ 已有多个后续预测表项 | `fence.i` 后的表项全部按前后位置过滤或清理 |
| `redirect_race` | IFU 重定向、后端重定向和 `fencei_done` 同拍或相邻拍 | 只有一个恢复 PC，FTQ 指针不能回到 `fence.i` 前的错误取指状态 |
| `reset_during_barrier` | 屏障或失效过程中复位 | 状态清零，重启后没有残留请求 |
| `random_backpressure` | 对 ICache、IBuffer 和后端注入随机停顿 | 在公平响应假设下最终完成，无活锁或超时 |

建议在 RTL 中加入以下断言（名称可按项目风格调整）：

```text
assert(fencei_done -> prior_fencei_req_pending)
assert(no_duplicate_done_per_req)
assert(fencei_req -> prior_sb_drain_complete)
assert(fencei_block && post_fencei_entry -> !cfVec.fire)
assert(fencei_block && post_fencei_entry -> !ROB_alloc && !LSQ_alloc && !EXU_issue)
assert(pre_fencei_response -> !ICache_meta_write && !ICache_data_write && !IFU_valid)
assert(release -> fencei_done && ordered_redirect_seen)
```

其中 `post_fencei_entry` 必须由 FTQ/ROB 序号判断条目是否位于 `fence.i` 后，不能用“当前周期大于 fence 周期”代替；后者正是分析 96 周期窗口时最容易出现的误判。

### 8.7.4 安全与侧信道波形测试
安全测试的核心目标不是证明「`cfVec` 永远全零」，而是证明 **`fence.i` 后的年轻指令没有在不可回滚资源上留下未授权活动**。每个测试至少设置四组对照：

1.  **基线组**：采用当前实现的 #6132/V2 对照波形，标出 96 周期窗口 `[C4235,C4331)` 以及窗口内 L1D 两次、L2 六次 Line 安装事件。
2.  **仅预译码截断组**：确认 `cfVec` 与后端年轻 uop 数量减少，同时核查已发出的旧 MSHR/L2 缓存痕迹是否仍然存在，避免把「`cfVec` 条目减少」误判为「Cache 风险消失」。
3.  **完整屏障组**：加入前端屏障、完成握手与 `fence.i` 前后过滤机制后，检查从屏障建立到失效完成期间，`fence.i` 后的年轻指令没有 `cfVec.fire`、没有 ROB/IQ/LSQ/EXU 分配、也没有 DCache 请求，且过期响应无法写入 L1I 或成为有效 IFU 响应。
4.  **压力测试组**：调整 MSHR 数量、预取开关、Cache 命中率、后端停顿与秘密地址，寻找旧请求在屏障期间落入 L2/DCache 的差异。

波形判据应明确区分「必须满足的安全条件」与「不应使用的错误判据」：

| 观察对象 | 必须满足的安全条件 | 不应使用的过强/错误判据 |
| --- | --- | --- |
| `cfVec` | `fence.i` 后的年轻指令在 `done` 前不能触发 fire | 不能要求更老指令和 `fence.i` 自身也从接口消失 |
| IBuffer/FTQ | `fence.i` 后的表项被清理或按前后位置过滤，不能旁路 | 不能只看 valid 位在某一拍为 0 就判定已完全清空 |
| ICache MSHR | `fence.i` 前请求不再产生有效 IFU 响应，其迟到响应不能写入 L1I SRAM | 「收到 flush 就代表所有 TileLink 事务已撤销」不成立 |
| L1I | `fence.i` 后新取指不命中旧有效 Line；旧 Refill 不得重新写入 | 不应把 Data SRAM 物理残留直接等同于架构命中 |
| L1D/L2 | 记录 Line 安装、一致性状态、PLRU/元数据和持久时间，检查是否与年轻秘密访问存在因果关系 | 不能因为重定向后 Cache 未回滚就断言一定可利用 |

按 `fence.i` 前后过滤只能阻止过期响应继续进入 L1I/IFU，不能自动撤销已经发往 L2 的 TileLink 事务。若安全目标要求同时消除 L2 预取痕迹，还需要跨 Cache 层的取消、隔离或分区机制，并单独验证其一致性与死锁性质。

针对本次深度分析，验收报告应至少给出以下指标：
- `fencei_hint` 到前端屏障建立的周期数；
- 前端屏障建立到 `fencei_req`、`fencei_done` 和唯一后端重定向的周期数；
- 窗口长度相对原 96 周期的变化幅度；
- 窗口内年轻 `cfVec.fire` 数量、年轻 ROB/LSQ/EXU/DCache 事件数；
- L1I/L1D/L2 新 Line 安装数，以及相关请求是在 `fence.i` 前还是后发起的；
- 重定向后仍存留的标签、一致性状态、PLRU 和预取痕迹；
- 所有过期响应是否被过滤，以及是否发生重复失效。

### 8.7.5 DiffTest 与性能回归
- **架构正确性验证**：以 Spike/QEMU 为参考模型，验证自修改代码、异常优先级、连续 `fence.i` 和普通无 `fence.i` 程序的寄存器与内存结果一致性。
- **无 `fence.i` 基准测试**：对比普通程序下的 IPC、前端流水线气泡和预译码时序，设定项目可接受的回归阈值（建议先要求功能零回归，再单独评估 <1% 的性能目标是否合理）。
- **`fencei_heavy` 压力测试**：统计每次前端屏障的平均/最大延迟、吞吐和功耗代理；确认频繁 `fence.i` 不会导致普通取指饥饿。
- **失效延迟分项测试**：分别测量 `req->done`、`done->first_new_fetch` 和 `first_new_fetch->retire` 三段延迟，避免把「Cache 清有效位的一拍」误报为「完整恢复延迟」。
- **参数化回归**：使用不同 `PredictWidth/DecodeWidth/IBufSize/MSHR` 参数完成至少一轮扫描，因为窗口大小和同拍握手优先级会随配置变化。

### 8.8 方案的安全边界与最终建议
将 `fence.i` 识别前移到预译码阶段是有价值的纵深防御，但它解决的是「尽早发现并隔离年轻取指」这一层问题，不会自动解决所有 Cache 侧信道风险。

1.  **能直接解决的问题**：同一取指块内的年轻后缀指令、尚未进入 IBuffer 的年轻指令，以及前端屏障期间新发起的取指请求。
2.  **必须额外处理的问题**：已经进入 IBuffer/FTQ 的表项、旧 MSHR 的延迟响应、已经发往 L2 的预取/Acquire 请求、L2 元数据/替换状态和 DCache 写分配痕迹。
3.  **不能仅靠此方案证明的问题**：攻击者是否能控制秘密相关地址、年轻旧指令是否在失效前派发、以及是否形成跨上下文可稳定测量的侧信道。

结合本报告 96 周期窗口的分析结果，建议采用如下发布顺序：
```text
先增加 isFenceI 解码与同块截断
  -> 再加前端屏障与 fence.i 前后标记，禁止 fence.i 后的 cfVec/后端事件
  -> 保留后端按序 SBuffer 排空逻辑
  -> 为 ICache 增加 req/done 握手与过期响应过滤
  -> 用波形断言证明窗口内无未授权年轻副作用
  -> 最后再评估数据阵列清零或随机化等高成本策略
```

如果只能选择一个最小改动，优先实施 **预译码标记 + 同块截断 + 为后端重定向/ICache 冲刷契约补充断言与注释**。如果目标是明确的侧信道安全硬化，则至少需要阶段 3、4 的前端屏障、按 `fence.i` 前后过滤和完成握手。**预译码识别后立即执行 `flushAll`，再用组合 ready 等待 Cache 完成的方案，不应作为第一版实现。**

## 9. Issue 对应 V3 的后续独立复现计划
第 7 章已完成 V2 平台上直接加载程序的 `fence.i` 控制链对照，但 Issue 对应的 V3 独立复现仍应包含三组测试程序：直接加载 PoC、秘密索引 PoC 和顺序落入对照组。每组需要记录：
- XiangShan 精确提交号、编译器版本与编译参数。
- ELF 反汇编结果，确认目标地址和指令编码。
- 仿真器完整命令、结束原因和波形文件路径。
- `fence.i`、重定向、`cfVec`、Dispatch、ROB、LSQ、DCache 和退休事件的时序。
- 架构结果与微架构副作用分别是否满足判据。

复现结果应能独立回答两个问题：接口现象能否稳定重现，以及旧条目是否越过了预期的失效边界。

## 10. Issue 对应 V3 的后续波形分析计划
波形分析将以「因果链」而非单一信号截图组织：
```text
store 修改代码
  -> fence / fence.i 执行
  -> ICache 元数据与 miss 失效
  -> backend redirect
  -> 前端各级 flush
  -> 旧 cfVec 条目被 kill
  -> 新目标重新取指
  -> 修改后的指令退休
```

如果旧条目曾进入后端，还要继续追踪其 ROB/LSQ 身份，定位它在哪一级被清除；如果发现 Cache、预测器或预取器副作用，则需要单独评估该副作用是否可被攻击者稳定区分。

## 11. 教学小结
这个案例并不是简单的「报告错误」或「实现错误」，而是一次观察边界与正确性边界不一致的典型调试案例：

1.  数据出现在内部接口上，只是事实链的起点。
2.  对于乱序推测处理器，redirect/flush 是解释指令有效性的必要上下文。
3.  `fence.i` 的逻辑语义可以由多条物理控制通路共同完成，不能只按同名信号追踪。
4.  功能 Bug、瞬时可见性和可利用侧信道是三个不同层次的命题，需要不同强度的证据。
5.  好的 PoC 不仅要有正例，还要有对照组，并明确列出自己没有证明的内容。

截至本次分析，最符合证据的分层定性是：**已确认前端旧指令瞬时暴露，以及 96 周期窗口内真实、部分持久的 Cache 状态变化；未确认架构功能错误、年轻旧指令直接造成的秘密相关访问或可利用侧信道；该问题仍应作为文档澄清、断言增强和纵深防御议题继续保留。**

V2 对照波形没有改变这一定性，但把实现机制具体化了：ICache 全量失效与前端 redirect 是两条不同且错时发生的路径；分析内部旧数据时，必须同时追踪动态指令身份和最终 kill/commit 结果。

## 参考资料
- [XiangShan Issue #6132](https://github.com/OpenXiangShan/XiangShan/issues/6132)
- [昆明湖 V3 ICache 设计文档：冲刷](https://docs.xiangshan.cc/projects/design/zh-cn/kunminghu-v3/frontend/ICache/#sec:icache-flush)
- [昆明湖 V3 ICache 设计文档：`fence.i` 冲刷脚注](https://docs.xiangshan.cc/projects/design/zh-cn/kunminghu-v3/frontend/ICache/#fn:redirect_tab_fencei)
- [Issue 所用提交 ICacheImp.scala](https://github.com/OpenXiangShan/XiangShan/blob/3931c5112c528299a23c256bdd77fb90813afa6e/src/main/scala/xiangshan/frontend/icache/ICacheImp.scala)
- [Issue 所用提交 ICacheMainPipe.scala](https://github.com/OpenXiangShan/XiangShan/blob/3931c5112c528299a23c256bdd77fb90813afa6e/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala)
- [FENCE_I 译码定义](https://github.com/OpenXiangShan/XiangShan/blob/96c3f568f943a096ffd3d712dc6f462ac4b1ba33/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L250)
- [本地 V2 对照程序](/nfs/home/yanyusong/XiangShanLab/xiangshan-course/docs/6-xiangshan-debug/xiangshan-issue-6132/bug-replay/smc_fencei_direct_probe.S)
- [本地 V2 对照波形](/nfs/home/yanyusong/XiangShanLab/xiangshan-course/docs/6-xiangshan-debug/xiangshan-issue-6132/bug-replay/2026-08-31-11-43-57.fst)
