<!--
# 3. 结构冲突 vs. 数据冲突 vs. 控制冲突

[附件: 流水线冒险：结构、数据与控制三大冲突深度解析.pptx](./attachments/44Zo57oNQ1Ru96CJ/流水线冒险：结构、数据与控制三大冲突深度解析.pptx)
-->

# 3. Structural Hazards vs. Data Hazards vs. Control Hazards

[Attachment: In-Depth Analysis of the Three Pipeline Hazards: Structural, Data, and Control](./attachments/44Zo57oNQ1Ru96CJ/流水线冒险：结构、数据与控制三大冲突深度解析.pptx)

<!--
## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">💡</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">学习目标</font>**

* <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">理解三类流水线冒险的核心本质与产生根源</font>
* <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">掌握结构、数据、控制冒险的典型场景与主流解决方案</font>
* <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">了解香山 RISC-V 处理器针对各类冒险的工程优化实践</font>
* <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">建立从顺序流水线到乱序执行的完整优化逻辑认知</font>
-->

## 💡 **Learning Objectives**

* Understand the fundamental nature and root causes of the three pipeline hazards.
* Master typical scenarios and mainstream solutions for structural, data, and control hazards.
* Learn how the XiangShan RISC-V processor applies engineering optimizations to each type of hazard.
* Build a complete understanding of the optimization path from in-order pipelines to out-of-order execution.

<!--
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">流水线架构通过指令重叠执行实现了性能飞跃，但同时也带来了不可避免的核心挑战：</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">多条指令在流水线内并行执行时，会破坏程序原本的顺序执行语义，或硬件资源无法满足并行执行需求，最终导致指令执行结果错误、流水线停顿、性能损失</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">。这是所有流水线处理器设计必须解决的核心问题。</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">三类核心冒险的本质差异可通过下表快速区分：</font>

| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">冲突类型</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">核心本质</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">产生根源</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">核心影响</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">典型场景</font>** |
| :--- | :--- | :--- | :--- | :--- |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">结构冲突</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">硬件资源争抢</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">并行执行的多条指令，同一周期同时申请</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">独占性硬件资源</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">流水线停顿，资源利用率下降</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">单内存端口同时被取指和访存争抢、单执行单元被多条指令同时申请</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">数据冲突</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令间数据依赖</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">后续指令需要提前读取前序指令</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">未就绪的运算结果</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，读取到错误数据</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令执行结果错误，必须通过停顿 / 转发修正</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Load-Use 冒险、算术指令的 RAW 真依赖</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">控制冲突</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令流控制流改变</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">分支 / 跳转 / 异常等改变 PC 值，导致流水线中预取的</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">错误路径指令全部失效</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">流水线冲刷，产生大量空泡，性能大幅损失</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">条件分支预测错误、函数返回地址预测失败</font> |
-->

The pipeline architecture achieves a major performance gain by overlapping instruction execution, but this also introduces an unavoidable challenge: **when multiple instructions execute in parallel in the pipeline, they can violate the program's original in-order semantics, or the hardware may not have enough resources to satisfy all parallel requests. The result can be incorrect instruction results, pipeline stalls, and performance loss**. This is a fundamental problem that every pipelined processor must address.

The essential differences among the three hazards can be quickly distinguished as follows:

| **Hazard type** | **Fundamental nature** | **Root cause** | **Primary impact** | **Typical scenario** |
| :--- | :--- | :--- | :--- | :--- |
| Structural hazard | Competition for hardware resources | Multiple instructions executing in parallel request the same **exclusive hardware resource** in the same cycle | Pipeline stalls and lower resource utilization | A single memory port is requested by instruction fetch and data access at once; multiple instructions request one execution unit |
| Data hazard | Data dependence between instructions | A later instruction reads a **result that the earlier instruction has not produced yet**, and therefore reads incorrect data | Incorrect results; corrected with stalls and/or forwarding | Load-use hazard; a true RAW dependence between arithmetic instructions |
| Control hazard | A change in the instruction stream's control flow | A branch, jump, or exception changes the PC, invalidating all prefetched **wrong-path instructions** | Pipeline flushes, many bubbles, and substantial performance loss | A mispredicted conditional branch or a failed function-return address prediction |

***

<!--
## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">一、结构冲突（Structural Hazard）</font>
-->

## I. Structural Hazard

<!--
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">1. 核心定义与产生根源</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">结构冲突是</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">唯一由硬件资源不足导致的冲突</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，和指令的语义、依赖无关。</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">核心矛盾是：硬件资源的「串行独占性」（同一周期只能被 1 条指令使用），与流水线的「并行重叠执行」（同一周期有多条指令在不同流水级运行）不匹配。当多条指令在同一个时钟周期，同时争抢同一个无法共享的硬件资源时，就会触发结构冲突。</font>
-->

### 1. Definition and Root Cause

A structural hazard is the **only hazard caused solely by insufficient hardware resources**; it is unrelated to instruction semantics or data dependence. The fundamental mismatch is between the **serialized exclusivity** of a hardware resource (only one instruction can use it in a cycle) and the pipeline's **overlapped parallel execution** (multiple instructions occupy different stages in the same cycle). A structural hazard occurs when multiple instructions contend for the same non-shareable hardware resource during one clock cycle.

<!--
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">2. 典型场景（贴合经典 5 级流水线）</font>

1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">最经典的指令 / 数据内存冲突</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">冯・诺依曼架构（指令和数据存在同一块内存）中，IF 级的取指指令和 MEM 级的访存指令，会在同一周期争抢同一个内存端口 / 总线。比如周期 4 时，</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">lw</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令在 MEM 级读数据内存，同时第 4 条指令在 IF 级取指令，二者争抢同一个内存，直接触发结构冲突。</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">哈佛架构（指令内存、数据内存物理分离）就是为了彻底解决这个结构冲突，也是所有流水线处理器的标配设计。</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">执行单元资源不足</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">超标量流水线只有 1 个整数乘法器 / 浮点运算单元，同一周期有 2 条同类型指令同时进入 EX 级，争抢唯一的执行单元，触发冲突。</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">寄存器堆端口冲突</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">经典 5 级流水线中，ID 级需要 2 个读端口读取源寄存器，WB 级需要 1 个写端口回写结果；若超标量处理器每周期要译码 2 条指令，需要 4 个读端口，但寄存器堆仅配置 2 个，就会触发端口争抢的结构冲突。</font>
4. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">低成本分时复用带来的冲突</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">多周期处理器中复用 ALU 做地址计算和运算，到流水线中就会因重叠执行触发冲突，也是流水线架构必须拆分 ALU 功能的核心原因。</font>
-->

### 2. Typical Scenarios (Using the Classic Five-Stage Pipeline)

1. **The classic instruction/data memory conflict.** In a von Neumann architecture (where instructions and data share one memory), the instruction fetched in the IF stage and the memory operation in the MEM stage contend for the same memory port or bus in the same cycle. For example, in cycle 4, an `lw` instruction reads data memory in MEM while the fourth instruction is fetched in IF; the two requests target the same memory and immediately create a structural hazard. A Harvard architecture, which physically separates instruction and data memories, eliminates this conflict and is standard in pipelined processors.
2. **Insufficient execution-unit resources.** If a superscalar pipeline has only one integer multiplier or floating-point unit, two instructions of that type entering EX in the same cycle compete for the sole unit and create a hazard.
3. **Register-file port conflicts.** In the classic five-stage pipeline, ID needs two read ports for source registers and WB needs one write port for the result. If a superscalar processor decodes two instructions per cycle, it needs four read ports; a register file with only two ports therefore experiences a structural conflict.
4. **Conflicts caused by low-cost time multiplexing.** A multicycle processor may reuse one ALU for address calculations and arithmetic operations. Once execution overlaps in a pipeline, that reuse creates conflicts, which is a key reason why a pipelined design must separate ALU functions.

<!--
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">3. 主流解决方案</font>

1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">资源复制（最直接、最主流）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：哈佛架构、多端口寄存器堆、多组同类型执行单元，从根源上消除资源争抢，是现代高性能处理器的标配。</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">资源分时复用（低成本优化）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：经典 5 级流水线的「前半周期寄存器写、后半周期寄存器读」设计，让 WB 级和 ID 级在同一个周期分时复用寄存器堆，无需额外增加端口，消除了基础的读写端口冲突。</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">流水线停顿（插入气泡）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：极简低成本方案，让后序指令暂停 1 个周期，等资源释放后再执行，缺点是直接损失 CPI，降低流水线吞吐率。</font>
4. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">动态调度优化</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：乱序执行的保留站 / 发射队列，将争抢资源的指令缓存排队，等资源空闲后再发射，避免整个流水线冻结。</font>
-->

### 3. Mainstream Solutions

1. **Resource replication (the most direct and common approach):** Harvard memories, multiported register files, and multiple execution units of the same type eliminate contention at its source and are standard in modern high-performance processors.
2. **Time-multiplexing resources (a low-cost optimization):** The classic five-stage pipeline writes the register file in the first half of a cycle and reads it in the second half, allowing WB and ID to share the register file in one cycle without extra ports and removing the basic read/write-port conflict.
3. **Pipeline stalls (inserting bubbles):** This minimal-cost approach pauses a later instruction for one cycle until the resource is released. Its drawback is a direct CPI cost and lower pipeline throughput.
4. **Dynamic scheduling:** Reservation stations or an issue queue in an out-of-order processor buffer contending instructions and issue them when the resource becomes available, avoiding a freeze of the entire pipeline.

***

<!--
## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">二、数据冲突（Data Hazard）</font>
-->

## II. Data Hazard

<!--
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">1. 核心定义与产生根源</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">数据冲突是流水线中最常见的冲突，核心是</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令之间的数据依赖，导致后续指令提前读取了前序指令未就绪的运算结果，最终执行错误</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">。</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">它的根源是程序的真数据依赖，流水线的重叠执行打破了「前序指令完全执行完，后序指令才开始执行」的顺序语义，导致后序指令读错数据。</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">首先明确数据依赖的 3 种类型，对应 3 类数据冲突：</font>

| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">依赖类型</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">全称</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">本质</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">能否消除</font>** |
| :--- | :--- | :--- | :--- |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">RAW</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">写后读（Read After Write）</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">真依赖：前序指令写寄存器，后序指令读同一个寄存器，必须等前序写完才能读</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">无法消除，只能优化延迟</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">WAR</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">读后写（Write After Read）</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">名相关（反依赖）：前序指令读寄存器，后序指令写同一个寄存器，不能提前写</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">可通过寄存器重命名完全消除</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">WAW</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">写后写（Write After Write）</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">名相关（输出依赖）：两条指令写同一个寄存器，必须保证最终写回顺序正确</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">可通过寄存器重命名完全消除</font> |
-->

### 1. Definition and Root Cause

A data hazard is the most common pipeline hazard. Its essence is **a data dependence between instructions that causes a later instruction to read an operand before the earlier instruction has produced it, leading to an incorrect result**. The root cause is a true data dependence in the program: overlapped pipeline execution breaks the in-order semantics in which a later instruction starts only after the earlier instruction has fully completed, so the later instruction can read the wrong value.

There are three data-dependence types, corresponding to three classes of data hazards:

| **Dependence type** | **Full name** | **Essence** | **Can it be eliminated?** |
| :--- | :--- | :--- | :--- |
| RAW | Read After Write | True dependence: the earlier instruction writes a register and the later instruction reads it; the read must wait for the write | Cannot be eliminated; only the latency can be optimized |
| WAR | Write After Read | Name dependence (anti-dependence): the earlier instruction reads a register and the later instruction writes it; the write must not occur too early | Can be completely eliminated by register renaming |
| WAW | Write After Write | Name dependence (output dependence): two instructions write the same register, so the final write-back order must be preserved | Can be completely eliminated by register renaming |

<!--
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">2. 典型场景</font>

1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Load-Use 冒险（最特殊、必须插入暂停的 RAW 冲突）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">就是你之前图中的核心场景：</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">lw $s0, 40($0)</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 指令要到</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">MEM 级结束时</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">才能从数据内存中拿到正确的</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">$s0</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">值，但后续的</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">and $t0, $s0, $s1</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">在</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">EX 级前半周期</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">就要用</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">$s0</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">做运算，时序完全错位，哪怕用数据转发也无法解决，必须插入 1 个周期的暂停，是唯一一类无法仅靠转发解决的数据冲突。</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">1 周期延迟的算术指令 RAW 冲突</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">前序是算术指令</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">add $s0, $s1, $s2</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">（EX 级结束就能拿到运算结果），后序是</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">and $t0, $s0, $s3</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，后序 EX 级要用</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">$s0</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，前序 EX 级才出结果，差 1 个周期，是最常见的 RAW 冲突。</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">名相关导致的 WAR/WAW 冲突</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">比如两条指令</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">add $s0, $s1, $s2</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">和</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">addi $s0, $s3, 1</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，都写同一个架构寄存器</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">$s0</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，若后序指令提前写回，就会导致 WAW 冲突；乱序执行中这类冲突非常普遍，是寄存器重命名技术的核心优化目标。</font>
-->

### 2. Typical Scenarios

1. **Load-use hazard (the special RAW hazard that requires a stall).** This is the canonical case: `lw $s0, 40($0)` cannot obtain the correct `$s0` value from data memory until the **end of MEM**, while the following `and $t0, $s0, $s1` needs `$s0` in the **first half of EX**. The timing is completely misaligned. Even forwarding cannot solve it, so a one-cycle stall is required; this is the only data hazard that cannot be handled by forwarding alone.
2. **A one-cycle-latency RAW hazard for an arithmetic instruction.** The earlier instruction `add $s0, $s1, $s2` produces its result at the end of EX, and the later `and $t0, $s0, $s3` needs `$s0` in its EX stage. The result is therefore one cycle too late, making this the most common RAW hazard.
3. **WAR/WAW hazards caused by name dependences.** For example, `add $s0, $s1, $s2` and `addi $s0, $s3, 1` both write the same architectural register `$s0`. If the later instruction writes back early, a WAW hazard occurs. Such hazards are common in out-of-order execution and are a primary target of register renaming.

<!--
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">3. 主流解决方案</font>

1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">数据旁路（转发，Forwarding/Bypassing）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：最核心的优化技术，将前序指令 EX/MEM、MEM/WB 流水线寄存器中的结果，直接转发给后续指令的 EX 级 ALU，无需等待写回寄存器堆，能解决 90% 以上的 RAW 冲突（除 Load-Use）。</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">流水线停顿（插入气泡）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：基础兜底方案，针对 Load-Use 这类无法靠转发解决的冲突，插入 1 个周期的气泡，等待数据就绪，就是你之前例子中的标准操作。</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">编译器指令调度</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：编译器静态调整指令顺序，把无依赖的指令插到有依赖的指令之间，填充气泡周期，无需硬件停顿就能消除冲突，是嵌入式处理器的常用优化手段。</font>
4. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">寄存器重命名</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：乱序执行的核心基础技术，通过物理寄存器堆（PRF）和寄存器别名表（RAT），将架构寄存器动态映射到不同的物理寄存器，彻底消除 WAR、WAW 名相关，突破指令的顺序限制。</font>
5. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">乱序执行 + 保留站</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：将等待数据的指令缓存到保留站中，仅唤醒操作数就绪的指令发射执行，避免整个流水线因数据依赖冻结，最大化执行资源利用率。</font>
-->

### 3. Mainstream Solutions

1. **Data bypass (forwarding):** The central optimization sends a result from a preceding instruction's EX/MEM or MEM/WB pipeline register directly to the following instruction's EX-stage ALU, without waiting for register-file write-back. It resolves more than 90% of RAW hazards (except load-use hazards).
2. **Pipeline stalls (inserting bubbles):** This basic fallback handles hazards such as load-use that forwarding cannot solve by inserting a one-cycle bubble until the data is ready.
3. **Compiler instruction scheduling:** The compiler statically reorders instructions and places independent instructions between dependent ones to fill bubble cycles. This avoids hardware stalls and is common in embedded processors.
4. **Register renaming:** Using a physical register file (PRF) and a register alias table (RAT), out-of-order hardware dynamically maps architectural registers to different physical registers. This removes WAR and WAW name dependences and breaks the restriction imposed by program order.
5. **Out-of-order execution with reservation stations:** Instructions waiting for operands are buffered in reservation stations; only instructions whose operands are ready are woken up and issued, preventing a data dependence from freezing the entire pipeline and maximizing execution-unit utilization.

***

<!--
## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">三、控制冲突（Control Hazard）</font>
-->

## III. Control Hazard

<!--
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">1. 核心定义与产生根源</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">控制冲突是现代高性能处理器的</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">性能天花板</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，核心是</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">分支、跳转、异常、中断等操作改变了程序计数器 PC 的值，导致流水线中已经预取、译码的指令全部是错误路径的无效指令，必须清空流水线重新取指</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，产生大量流水线气泡，造成严重的性能损失。</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">它的根源是：经典 5 级流水线中，条件分支指令要到</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">EX 级</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">才能算出「是否跳转」和「跳转目标地址」，但此时流水线已经预取了分支后面的 2-3 条指令，一旦确定跳转，这些指令必须全部清空（流水线冲刷 Flush），每一次误判都会损失 2-3 个周期的性能。</font>
-->

### 1. Definition and Root Cause

A control hazard is the **performance ceiling** for modern high-performance processors. A branch, jump, exception, or interrupt changes the program counter (PC), making all instructions already fetched and decoded in the pipeline invalid wrong-path instructions; the pipeline must be emptied and refilled, creating many bubbles and severe performance loss. The root cause is that, in a classic five-stage pipeline, a conditional branch cannot determine **whether to take the branch** and its **target address** until EX. By then, two or three instructions after the branch have already been fetched. Once the branch is taken, all of them must be discarded (a pipeline flush), costing two or three cycles for every misprediction.

<!--
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">2. 典型场景</font>

1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">条件分支指令（beq、bne 等）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：最核心的控制冲突来源，占程序指令的 10%-20%，是分支预测技术的核心优化目标，你之前了解的香山 TAGE 分支预测器就是专门解决这类冲突的。</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">间接跳转 / 函数返回指令（jalr、ret 等）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：跳转目标地址存在寄存器中，无法在编译期确定，预测难度极高，是控制冲突的第二大来源，香山的返回地址栈（RAS）、ITTAGE 间接分支预测器就是专门解决这类问题的。</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">异常、中断、系统调用</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：会直接打断正常的指令流，清空流水线，进入异常处理程序，属于非预期的控制冲突，靠 ROB 重排序缓冲的精确异常机制处理。</font>
-->

### 2. Typical Scenarios

1. **Conditional branches (`beq`, `bne`, and so on):** The main source of control hazards, these account for 10%-20% of program instructions and are the primary target of branch-prediction technology. XiangShan's TAGE branch predictor is designed to handle this class of hazard.
2. **Indirect jumps and function returns (`jalr`, `ret`, and so on):** The target address is held in a register and cannot be determined at compile time, making prediction difficult. They are the second-largest source of control hazards; XiangShan's return-address stack (RAS) and ITTAGE indirect branch predictor target these cases.
3. **Exceptions, interrupts, and system calls:** These directly interrupt the normal instruction stream, flush the pipeline, and enter an exception handler. They are unexpected control hazards handled by the precise-exception mechanism of the reorder buffer (ROB).

<!--
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">3. 主流解决方案</font>

1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">动态分支预测（现代高性能处理器标配）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：就是你之前了解的香山处理器的核心技术，通过 TAGE 系列条件分支预测器、分支目标缓冲（BTB）、返回地址栈（RAS）、ITTAGE 间接分支预测器，硬件实时记录分支的历史行为，提前预测「是否跳转」和「跳转目标地址」，提前取正确路径的指令，商用负载下预测准确率可达 99% 以上，从根源上减少流水线冲刷的次数。</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">静态分支预测</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：极简低成本方案，由编译器完成，比如默认「分支不跳转」提前取顺序指令，或预测循环分支总是跳转，准确率约 70%，多用于嵌入式低功耗处理器。</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">分支延迟槽</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：MIPS/RISC-V 经典技术，在分支指令后面设置 1-2 个延迟槽，放入无论分支是否跳转都一定会执行的无依赖指令，这些指令不会被清空，彻底消除分支带来的气泡。</font>
4. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">乱序执行 + ROB 重排序缓冲</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：分支预测错误时，直接清空 ROB 中错误路径的所有指令，配合 Checkpoint 检查点技术，单周期恢复寄存器别名表（RAT）的状态，最小化分支误判带来的性能损失。</font>
5. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">流水线冻结 + 停顿</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：最基础的兜底方案，遇到分支指令就直接冻结 PC，暂停流水线，等分支结果确定后再取指，缺点是每条分支都会损失 2-3 个周期，性能损失极大，现代处理器几乎不用。</font>
-->

### 3. Mainstream Solutions

1. **Dynamic branch prediction (standard in modern high-performance processors):** XiangShan's core approach combines TAGE-family conditional predictors, a branch target buffer (BTB), a return-address stack (RAS), and the ITTAGE indirect branch predictor. Hardware records branch history in real time, predicts whether a branch is taken and its target, and fetches the predicted path early. Accuracy can exceed 99% on commercial workloads, substantially reducing pipeline flushes.
2. **Static branch prediction:** This minimal-cost approach is performed by the compiler. Examples include assuming that a branch is not taken and fetching the sequential path, or predicting that a loop branch is always taken. Accuracy is about 70%, so it is mainly used in low-power embedded processors.
3. **Branch delay slots:** A classic MIPS/RISC-V technique places one or two delay slots after a branch and fills them with independent instructions that execute regardless of whether the branch is taken. These instructions are not flushed, eliminating the bubbles caused by the branch.
4. **Out-of-order execution with a reorder buffer (ROB):** On a misprediction, all wrong-path instructions in the ROB are discarded. Combined with checkpointing, this can restore the register alias table (RAT) state in one cycle and minimize the performance cost of a misprediction.
5. **Pipeline freeze and stall:** The most basic fallback freezes the PC and pauses the pipeline whenever it encounters a branch, then resumes fetching after the branch result is known. Every branch costs two or three cycles, so modern processors almost never use this approach.

***

<!--
## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">四、核心总结与处理器演进关联</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">结合之前学习的单周期→多周期→流水线→乱序执行的演进路径，三者的核心边界和优化逻辑非常清晰：</font>

1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">单周期 / 多周期处理器</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：没有指令重叠执行，不存在数据冲突和控制冲突；仅多周期处理器会因资源复用出现少量结构冲突。</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">顺序流水线处理器</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：三类冲突全部出现，结构冲突靠哈佛架构 / 资源复制解决，数据冲突靠数据转发 + 少量停顿解决，控制冲突靠基础分支预测解决，是流水线设计的基础。</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">乱序高性能处理器（如香山 RISC-V）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：结构冲突靠超标量多执行单元彻底解决；数据冲突靠寄存器重命名 + 乱序调度最大化消除停顿；控制冲突靠高精度多级分支预测体系突破性能天花板，三者的优化共同构成了现代高性能处理器的核心设计逻辑。</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">一句话速记：</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">结构冲突是硬件不够用，数据冲突是数据没准备好，控制冲突是指令走错路了</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">。</font>
-->

## IV. Summary and Connection to Processor Evolution

Along the evolution path from single-cycle to multicycle, pipelined, and out-of-order execution, the boundaries and optimization logic of the three hazards are clear:

1. **Single-cycle and multicycle processors:** They do not overlap instruction execution, so they have no data or control hazards. Only a multicycle processor can experience a small number of structural hazards because of resource reuse.
2. **In-order pipelined processors:** All three hazards appear. Structural hazards are addressed with Harvard memories and resource replication, data hazards with forwarding plus a small number of stalls, and control hazards with basic branch prediction. This is the foundation of pipeline design.
3. **Out-of-order high-performance processors (such as XiangShan RISC-V):** Multiple superscalar execution units largely eliminate structural hazards; register renaming and out-of-order scheduling minimize stalls from data hazards; and a high-accuracy, multilevel branch-prediction system breaks through the performance ceiling imposed by control hazards. Together, these optimizations form the core design logic of modern high-performance processors.

**One-line summary:** **A structural hazard means the hardware is insufficient, a data hazard means the data is not ready, and a control hazard means the instruction took the wrong path.**

<!--
> 更新: 2026-05-12 11:08:48  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/emmsmg4qt9kh258g>
-->

> Updated: 2026-05-12 11:08:48
> Source: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/emmsmg4qt9kh258g>
