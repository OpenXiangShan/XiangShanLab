# 4. Register Renaming
<!-- # 4. 寄存器重命名 -->

:::warning
<!-- **<font style="background-color:rgb(255,245,235);">学习目标</font>**<font style="background-color:rgb(255,245,235);">：</font> -->
**Learning objectives**:

<!-- * <font style="background-color:rgb(255,245,235);">🧭</font><font style="background-color:rgb(255,245,235);"> 彻底理解寄存器重命名的</font>**<font style="background-color:rgb(255,245,235);">核心目的</font>**<font style="background-color:rgb(255,245,235);">：消除伪数据相关（RAW/WAW/WAR）</font> -->
* 🧭 Understand the core purpose of register renaming: eliminate WAW/WAR false dependencies while preserving true RAW dependencies.
<!-- * <font style="background-color:rgb(255,245,235);">📋</font><font style="background-color:rgb(255,245,235);"> 掌握三种主流重命名架构：</font>**<font style="background-color:rgb(255,245,235);">ROB重命名、ARF拓展重命名、统一PRF重命名</font>** -->
* 📋 Master the three mainstream schemes: ROB renaming, extended-ARF renaming, and unified-PRF renaming.
<!-- * <font style="background-color:rgb(255,245,235);">🔍</font><font style="background-color:rgb(255,245,235);"> 吃透重命名映射表的读写、Bypass、Freelist空闲链表完整工作机制</font> -->
* 🔍 Understand map-table reads/writes, bypassing, and the complete FreeList mechanism.
<!-- * <font style="background-color:rgb(255,245,235);">🗺️</font><font style="background-color:rgb(255,245,235);"> 掌握Redirect、Snapshot快照、ROB压缩三大流水线恢复机制</font> -->
* 🗺️ Master the recovery mechanisms (Redirect and Snapshot) and the ROB-compression optimization.
<!-- * <font style="background-color:rgb(255,245,235);">⚡</font><font style="background-color:rgb(255,245,235);"> 理解多指令并行重命名、带宽瓶颈、跨宽度缓冲队列工程设计</font> -->
* ⚡ Understand parallel renaming, bandwidth bottlenecks, and width-adaptation buffering.
<!-- * <font style="background-color:rgb(255,245,235);">📦</font><font style="background-color:rgb(255,245,235);"> 掌握特殊寄存器 </font>**<font style="background-color:rgb(255,245,235);">VCSR.vl</font>**<font style="background-color:rgb(255,245,235);"> 的专属重命名逻辑</font> -->
* 📦 Understand the dedicated renaming logic for the special register **VCSR.vl**.

:::

<!-- ## 4.1 寄存器重命名核心原理：解决伪数据相关 -->
## 4.1 Core Principle of Register Renaming: Removing False Dependencies

<!-- 在学习乱序执行时，最大的性能阻碍并非真实数据依赖，而是**架构寄存器数量稀缺导致的伪相关（False Dependency）**。很多指令之间没有真实数据交互，仅仅因为复用了同一个架构寄存器，就被流水线强行串行执行，严重压制乱序吞吐。 -->
When learning out-of-order execution, the largest performance obstacle is often not a true data dependency but a **false dependency caused by the limited number of architectural registers**. Instructions with no real data exchange can be forced to execute serially simply because they reuse an architectural register name, severely limiting out-of-order throughput.

<!-- 寄存器重命名，就是乱序处理器的\*\*“去伪依赖”核心机制\*\*：将程序可见的少量架构寄存器（ARF），映射为硬件大量的物理寄存器（PRF），让指令真正做到“无相关则并行、乱序执行”。 -->
Register renaming is the out-of-order processor's **core mechanism for removing false dependencies**: map the small set of program-visible architectural registers (ARF) onto a much larger set of hardware physical registers (PRF), so independent instructions can execute in parallel and out of order.

> <!-- *通俗比喻：架构寄存器如同有限的“房间号”，物理寄存器如同海量的“真实房间”。程序只能识别有限房间号，硬件通过重命名，把同一个房间号映射到不同真实房间，避免新老指令互相挤占、强行等待。* -->
> *Analogy: architectural registers are a limited set of room numbers, while physical registers are many actual rooms. Renaming maps the same room number to different rooms at different times, so old and new instructions do not have to wait for one another.*
>
> <!-- ***香山的实现细节：香山并非只有一套统一的 ARF→PRF 映射。RISC-V 的整数寄存器、浮点寄存器、向量寄存器是独立的命名空间，因此香山维护了 5 套独立的 RAT 和 FreeList*** -->
> ***XiangShan implementation detail: XiangShan does not have one unified ARF-to-PRF map. RISC-V integer, floating-point, and vector registers occupy independent namespaces, so XiangShan maintains five independent RATs and FreeLists.***

<!-- | ***寄存器类别*** | ***架构寄存器*** | ***RAT*** | ***FreeList*** |
| --- | --- | --- | --- |
| *整数 (Int)* | *x0~x31（32个）* | *intRAT* | *intFreeList（MEFreeList）* |
| *浮点 (FP)* | *f0~f31（32个）* | *fpRAT* | *fpFreeList（StdFreeList）* |
| *向量 (Vec)* | *v1~v31（31个）* | *vecRAT* | *vecFreeList（StdFreeList）* |
| *向量掩码 (V0)* | *v0（1个）* | *v0RAT* | *v0FreeList（StdFreeList）* |
| *向量长度 (VL)* | *vl（1个）* | *vlRAT* | *vlFreeList（StdFreeList）* | -->

| ***Register class*** | ***Architectural registers*** | ***RAT*** | ***FreeList*** |
| --- | --- | --- | --- |
| *Integer (Int)* | *x0-x31 (32)* | *intRAT* | *intFreeList (MEFreeList)* |
| *Floating point (FP)* | *f0-f31 (32)* | *fpRAT* | *fpFreeList (StdFreeList)* |
| *Vector (Vec)* | *v1-v31 (31)* | *vecRAT* | *vecFreeList (StdFreeList)* |
| *Vector mask (V0)* | *v0 (1)* | *v0RAT* | *v0FreeList (StdFreeList)* |
| *Vector length (VL)* | *vl (1)* | *vlRAT* | *vlFreeList (StdFreeList)* |

> <!-- ***每套系统独立分配物理寄存器、独立维护映射关系，互不干扰。重命名的核心原理对每套系统都一样——消除 WAW/WAR 伪相关，但实现上它们是并行的 5 条独立数据通路。*** -->
> ***Each system allocates physical registers and maintains mappings independently. The principle is the same in all five systems—remove WAW/WAR false dependencies—but the implementation consists of five parallel datapaths.***

<!-- ### 4.1.1 三种数据相关与重命名解决方案 -->
### 4.1.1 Three Dependency Types and Renaming Solutions

<!-- 数据相关分为**真相关（RAW）**和**伪相关（WAW/WAR）**，寄存器重命名可彻底消除两类伪相关，优化真相关调度逻辑。 -->
Dependencies are divided into **true dependencies (RAW)** and **false dependencies (WAW/WAR)**. Renaming completely removes the two false types and makes scheduling around true dependencies more efficient.

<!-- | **相关类型** | **全称** | **产生原因** | **是否为真依赖** | **重命名解决方式** |
| --- | --- | --- | --- | --- |
| RAW | 写后读 | 前一条指令写寄存器，后一条指令读取该寄存器，需要等待真实结果 | ✅ 真相关 | **无法消除**，通过重命名映射+Bypass转发，缩短等待延迟 |
| WAW | 写后写 | 前后两条指令连续写同一个架构寄存器，无数据传递，仅寄存器复用冲突 | ❌ 伪相关 | **完全消除**：两次写操作映射到不同物理寄存器，互不干扰、可乱序执行 |
| WAR | 读后写 | 前指令读寄存器，后指令写同寄存器，无数据冲突，仅资源复用冲突 | ❌ 伪相关 | **完全消除**：读写绑定不同物理寄存器，读旧值、写新值，并行执行 | -->

| **Dependency** | **Full name** | **Cause** | **True dependency?** | **Renaming solution** |
| --- | --- | --- | --- | --- |
| RAW | Read after write | A later instruction reads a value produced by an earlier instruction and must wait | ✅ True | **Cannot be removed**; use renaming and bypass forwarding to shorten the wait |
| WAW | Write after write | Consecutive writes to one architectural register with no data transfer | ❌ False | **Removed completely**: map the writes to different physical registers |
| WAR | Write after read | A later write reuses a register that an earlier instruction reads | ❌ False | **Removed completely**: bind the read and write to different physical registers |

:::warning
<!-- **<font style="background-color:rgb(255,245,235);">关键认知</font>**<font style="background-color:rgb(255,245,235);">：很多新手误区认为重命名能解决所有数据相关，实际上</font>**<font style="background-color:rgb(255,245,235);">仅消除 WAW、WAR 伪相关</font>**<font style="background-color:rgb(255,245,235);">，RAW真相关是程序逻辑固有依赖，只能通过流水线转发、乱序调度优化延迟，无法消除。</font> -->
**Key point**: Renaming does not solve every dependency. It removes only WAW and WAR false dependencies; RAW is inherent to the program and can only be shortened through forwarding and out-of-order scheduling.

:::

<!--
```plain
// 1. RAW 真相关（写后读，必须等待，无法消除）
add  x10, x1, x2  // I1：写x10
add  x11, x10, x3 // I2：读x10，必须等待I1写完，真实数据依赖

// 2. WAW 伪相关（写后写，无数据传递，重命名可消除）
add  x10, x1, x2  // I1：写x10
add  x10, x3, x4  // I2：覆盖写x10，两条指令无数据交互，仅寄存器复用

// 3. WAR 伪相关（读后写，无数据冲突，重命名可消除）
add  x11, x10, x2 // I1：读x10
add  x10, x1, x3  // I2：写x10，读写无依赖，可乱序并行执行
```
-->
```plain
add  x10, x1, x2
add  x11, x10, x3

add  x10, x1, x2
add  x10, x3, x4

add  x11, x10, x2
add  x10, x1, x3
```

<!-- ## 4.2 三大寄存器重命名实现方式 -->
## 4.2 Three Register-Renaming Implementations

<!-- 业界主流 RISC 架构处理器共有三种重命名实现方案，香山南湖架构采用**统一PRF重命名**，同时兼容部分ARF拓展逻辑，下面对比三种方案的原理、优劣与适用场景。 -->
Mainstream RISC processors use three renaming schemes. XiangShan's Nanhu design uses **unified-PRF renaming** with some extended-ARF behavior. The following sections compare their principles, advantages, disadvantages, and use cases.

<!-- ### 4.2.1 ROB 进行重命名 -->
### 4.2.1 ROB Renaming

<!-- ROB 重命名是最经典的早期乱序处理器方案，不依赖独立物理寄存器堆，直接将 ROB 条目作为"临时物理寄存器"使用。所有指令的运算结果先写入 ROB，提交后再更新到架构寄存器 ARF。 -->
ROB renaming is the classic early out-of-order scheme. It has no separate physical register file and uses ROB entries as temporary physical registers. Results are written to the ROB first and copied to the architectural register file (ARF) at commit.

<!-- **核心流程**：指令译码后分配 ROB 条目 → 将架构寄存器映射为 ROB 编号 → 结果写入 ROB → 提交后回写 ARF。 -->
**Core flow**: allocate a ROB entry after decode → map the architectural register to the ROB index → write the result to the ROB → write back to the ARF at commit.

<!-- **优缺点**：硬件成本低、结构简单；但 ROB 读写端口压力极大，延迟高，只适合小规模乱序架构。 -->
**Pros and cons**: low hardware cost and simple structure, but very high ROB port pressure and latency; it is suitable only for small out-of-order designs.

<!--
> *\*\*ROB 重命名极简示例：指令 \*\**<code>_**add x10, x1, x2**_</code>*\*\* 译码后分配 ROB#5，直接将架构寄存器 x10 映射为 ROB#5，运算结果先存入 ROB#5，指令提交后再将 ROB#5 数据写入 ARF 的 x10 寄存器\*\**

-->
> **ROB renaming example**: after decoding `add x10, x1, x2`, allocate ROB#5, map architectural x10 to ROB#5, write the result to ROB#5, and copy it to ARF x10 at commit.

<!-- ### 4.2.2 ARF 拓展进行重命名 -->
### 4.2.2 Extended-ARF Renaming

<!-- ARF拓展方案在原有架构寄存器堆基础上，**为每个ARF寄存器拓展多个影子寄存器**。当出现寄存器复用冲突时，自动调用影子寄存器完成重命名，无冲突时直接使用原生ARF。 -->
The extended-ARF scheme adds **multiple shadow registers for each ARF register**. A shadow register is selected when a reuse conflict occurs; otherwise the native ARF register is used.

<!-- **核心流程**：检测寄存器复用 → 分配拓展影子寄存器 → 指令执行写影子寄存器 → 提交后覆盖原生ARF。 -->
**Core flow**: detect register reuse → allocate an extended shadow register → execute and write the shadow register → overwrite the native ARF at commit.

<!-- **优缺点**：兼容原生架构、恢复逻辑简单；拓展数量有限，无法支撑大规模超乱序执行，灵活性差。 -->
**Pros and cons**: compatible with the native architecture and simple to recover, but limited shadow capacity cannot support a large out-of-order window and offers little flexibility.

<!-- **ARF拓展重命名极简示例**： -->
**Minimal extended-ARF example**:

<!--
> ***ARF 拓展重命名极简示例：连续两条指令写 x10：***<code>_**add x10,x1,x2**_</code>***、***<code>_**sub x10,x3,x4**_</code>***，原生 x10 无法同时存储两个结果，硬件调用 x10 对应的影子寄存器 S-x10，第二条指令写入 S-x10，规避 WAW 伪相关，提交后覆盖原生 x10。***

-->
> **Extended-ARF example**: two consecutive writes to x10 cannot use the native register simultaneously. The second instruction uses shadow register S-x10, avoiding the WAW false dependency, and replaces native x10 at commit.

<!-- ### 4.2.3 统一 PRF 进行重命名（香山采用） -->
### 4.2.3 Unified-PRF Renaming (Used by XiangShan)

<!-- 统一物理寄存器堆（PRF）是当前高性能处理器的主流方案，香山南湖架构采用该设计。硬件配备**远多于架构寄存器的物理寄存器**，所有指令读写均直接操作PRF，ARF仅保存“最新合法状态”。 -->
The unified physical register file (PRF) is the mainstream scheme for current high-performance processors, and is used by Nanhu. Hardware provides **many more physical than architectural registers**; all instruction reads and writes target the PRF directly, while the ARF retains only the latest committed state.

<!-- **核心流程**：译码获取架构寄存器号 → 查询重命名映射表 → 分配空闲PRF条目 → 指令读写物理寄存器 → 指令提交后更新映射表、回收旧物理寄存器。 -->
**Core flow**: decode architectural register numbers → query the rename map → allocate a free PRF entry → read and write physical registers → update the map and reclaim the old physical register at commit.

<!-- **优缺点**：物理资源充足、重命名带宽高、乱序能力强；硬件开销大，需要配套Freelist、映射表、快照恢复复杂逻辑。 -->
**Pros and cons**: abundant physical resources, high rename bandwidth, and strong out-of-order capability; the cost is substantial hardware, including FreeLists, mapping tables, and snapshot-recovery logic.

<!-- 每套系统独立分配、独立回收、独立维护映射关系。物理寄存器总数远超架构寄存器——仅整数 PRF 就有约 196 个物理寄存器，是架构寄存器的 6 倍以上，为乱序执行窗口提供充足的寄存器资源。 -->
Each namespace allocates, reclaims, and maintains mappings independently. The physical files are much larger than the architectural files; the integer PRF alone has about 196 entries, more than six times the architectural count, providing ample resources for the out-of-order window.

<!-- 由本章开篇可知，每套系统独立分配、独立回收、独立维护映射关系。仅整数 PRF 就有约 196 个物理寄存器，是架构寄存器的 6 倍以上。 -->
As noted at the beginning of this chapter, each namespace manages its mappings independently; the integer PRF alone contains about 196 physical registers, over six times the architectural count.

<!-- ### FreeList 分配逻辑 -->
### FreeList Allocation Logic

<!-- 以 <code>**StdFreeList**</code> 为例，物理寄存器分配的核心代码： -->
For <code>**StdFreeList**</code>, the core physical-register allocation code is:

<!--
```scala
// StdFreeList.scala：空闲物理寄存器队列，初始化时填入编号 numLogicRegs ~ numLogicRegs+freeListSize-1
val freeList = RegInit(VecInit(Seq.tabulate(freeListSize)(i => (i + numLogicRegs).U(PhyRegIdxWidth.W))))

// 只有空闲寄存器数量 >= RenameWidth 时才允许分配
val freeRegCnt = Wire(UInt())
io.canAllocate := GatedValidRegNext(freeRegCnt >= RenameWidth.U)

// ：根据 head 指针和当前分配请求，输出物理寄存器编号
val phyRegCandidates = Mux1H(headPtrOHVec(0), freeListVec)
for (i <- 0 until RenameWidth) {
  io.allocatePhyReg(i) := phyRegCandidates(PopCount(io.allocateReq.take(i)))
}
```
-->
```scala
val freeList = RegInit(VecInit(Seq.tabulate(freeListSize)(i => (i + numLogicRegs).U(PhyRegIdxWidth.W))))

val freeRegCnt = Wire(UInt())
io.canAllocate := GatedValidRegNext(freeRegCnt >= RenameWidth.U)

val phyRegCandidates = Mux1H(headPtrOHVec(0), freeListVec)
for (i <- 0 until RenameWidth) {
  io.allocatePhyReg(i) := phyRegCandidates(PopCount(io.allocateReq.take(i)))
}
```

<!-- ### 重命名分配的耦合机制 -->
### Coupling in Rename Allocation

<!-- 5 套 FreeList 必须全部就绪才能进行分配——任何一套 FreeList 空间不足都会阻塞整条重命名流水线： -->
All five FreeLists must be ready before allocation can proceed; exhaustion of any one blocks the entire rename pipeline:

<!--
```scala
// Rename.scala：所有 FreeList 都能分配时，才允许本 FreeList 分配
intFreeList.io.doAllocate := fpFreeList.io.canAllocate && vecFreeList.io.canAllocate
&& v0FreeList.io.canAllocate && vlFreeList.io.canAllocate && dispatchCanAcc || io.rabCommits.isWalk
fpFreeList.io.doAllocate  := intFreeList.io.canAllocate && vecFreeList.io.canAllocate
&& v0FreeList.io.canAllocate && vlFreeList.io.canAllocate && dispatchCanAcc || io.rabCommits.isWalk
// ...其余 3 套同理

// 重命名输出的前提：所有 FreeList 就绪 + Dispatch 可接收 + 非回滚状态
val canOut = dispatchCanAcc && fpFreeList.io.canAllocate && intFreeList.io.canAllocate
&& vecFreeList.io.canAllocate && v0FreeList.io.canAllocate && vlFreeList.io.canAllocate && !io.rabCommits.isWalk
```
-->
```scala
intFreeList.io.doAllocate := fpFreeList.io.canAllocate && vecFreeList.io.canAllocate
&& v0FreeList.io.canAllocate && vlFreeList.io.canAllocate && dispatchCanAcc || io.rabCommits.isWalk
fpFreeList.io.doAllocate  := intFreeList.io.canAllocate && vecFreeList.io.canAllocate
&& v0FreeList.io.canAllocate && vlFreeList.io.canAllocate && dispatchCanAcc || io.rabCommits.isWalk

val canOut = dispatchCanAcc && fpFreeList.io.canAllocate && intFreeList.io.canAllocate
&& vecFreeList.io.canAllocate && v0FreeList.io.canAllocate && vlFreeList.io.canAllocate && !io.rabCommits.isWalk
```

<!-- ### 判断指令是否需要分配物理寄存器 -->
### Determining Whether an Instruction Needs a Physical Register

<!-- 不同类别的指令需要从不同 FreeList 分配物理寄存器： -->
Different instruction classes allocate physical registers from different FreeLists:

<!--
```scala
def needDestReg[T <: DecodeOutUop](reg_t: RegType, x: T): Bool = reg_t match {
  case Reg_I  => x.rfWen   // 整数寄存器写使能
  case Reg_F  => x.fpWen   // 浮点寄存器写使能
  case Reg_V  => x.vecWen  // 向量寄存器写使能
  case Reg_V0 => x.v0Wen   // v0 掩码寄存器写使能
  case Reg_Vl => x.vlWen   // vl 向量长度写使能
}

// 在重命名循环中，为每条指令分别判断 5 种寄存器的分配需求
needV0Dest(i)  := io.in(i).valid && needDestReg(Reg_V0, io.in(i).bits)
needVlDest(i)  := io.in(i).valid && needDestReg(Reg_Vl, io.in(i).bits)
needVecDest(i) := io.in(i).valid && needDestReg(Reg_V,  io.in(i).bits)
needFpDest(i)  := io.in(i).valid && needDestReg(Reg_F,  io.in(i).bits)
needIntDest(i) := io.in(i).valid && needDestReg(Reg_I,  io.in(i).bits)
```
-->
```scala
def needDestReg[T <: DecodeOutUop](reg_t: RegType, x: T): Bool = reg_t match {
  case Reg_I  => x.rfWen
  case Reg_F  => x.fpWen
  case Reg_V  => x.vecWen
  case Reg_V0 => x.v0Wen
  case Reg_Vl => x.vlWen
}

needV0Dest(i)  := io.in(i).valid && needDestReg(Reg_V0, io.in(i).bits)
needVlDest(i)  := io.in(i).valid && needDestReg(Reg_Vl, io.in(i).bits)
needVecDest(i) := io.in(i).valid && needDestReg(Reg_V,  io.in(i).bits)
needFpDest(i)  := io.in(i).valid && needDestReg(Reg_F,  io.in(i).bits)
needIntDest(i) := io.in(i).valid && needDestReg(Reg_I,  io.in(i).bits)
```

> <!-- ***香山 PRF 重命名示例：*** -->
> ***XiangShan PRF renaming example:***
>
> <!-- 1. <code>_**add x10, x1, x2**_</code>***：RAT 查询 x1→p5、x2→p8，FreeList 分配 p20，RAT 更新 x10→p20*** -->
> 1. `add x10, x1, x2`: RAT maps x1→p5 and x2→p8; the FreeList allocates p20; RAT updates x10→p20.
> <!-- 2. <code>_**sub x10, x3, x4**_</code>***：RAT 查询 x3→p12、x4→p15，FreeList 分配 p21，RAT 更新 x10→p21*** -->
> 2. `sub x10, x3, x4`: RAT maps x3→p12 and x4→p15; the FreeList allocates p21; RAT updates x10→p21.
> <!-- 3. ***两条指令分别写 p20、p21，彻底消除 WAW 伪相关，可乱序并行执行*** -->
> 3. The two instructions write p20 and p21, removing the WAW false dependency and allowing out-of-order parallel execution.
> <!-- 4. ***提交后，p20（旧映射）归还 FreeList，p21 成为 x10 的最新映*** -->
> 4. At commit, p20 (the old mapping) returns to the FreeList and p21 becomes x10's newest mapping.

:::warning
<!-- **<font style="background-color:rgb(255,245,235);">背景知识</font>**<font style="background-color:rgb(255,245,235);">：</font>香山选择统一 PRF 方案，核心目的是支撑高 IPC、超乱序、多发射流水线，适配高性能算力场景，牺牲部分硬件面积换取极致执行效率。整数 FreeList 采用 MEFreeList（更高效的分配策略），其余采用 StdFreeList，体现了不同寄存器类别的使用模式差异——整数寄存器写入最频繁，对分配效率要求最高。 -->
**Background**: XiangShan chooses a unified PRF to support high IPC, a large out-of-order window, and a multi-issue pipeline. It trades some area for execution efficiency. The integer FreeList uses MEFreeList for more efficient allocation, while the other classes use StdFreeList; integer registers are written most often and therefore need the highest allocation efficiency.

:::

<!-- ## 4.3 重命名映射表工作机制 -->
## 4.3 Rename-Map Operation

<!-- 重命名映射表是重命名模块的核心，负责记录架构寄存器 → 物理寄存器的实时映射关系。香山采用**双表设计**：投机映射表（<code>**spec_table**</code>）+ 架构映射表（<code>**arch_table**</code>），所有读写、转发、恢复逻辑均依托双表实现。 -->
The rename map is the core of the rename unit and records architectural-to-physical register mappings. XiangShan uses two tables: the speculative map (<code>**spec_table**</code>) and the architectural map (<code>**arch_table**</code>). All reads, writes, forwarding, and recovery rely on this pair.

<!--
```plain
// RenameTable.scala：双表结构
val spec_table = RegInit(rename_table_init)   // 投机映射表：记录当前最新的投机映射
val spec_table_next = WireInit(spec_table)
val arch_table = RegInit(rename_table_init)    // 架构映射表：记录已提交的架构状态
val arch_table_next = WireDefault(arch_table)
```
-->
```plain
val spec_table = RegInit(rename_table_init)
val spec_table_next = WireInit(spec_table)
val arch_table = RegInit(rename_table_init)
val arch_table_next = WireDefault(arch_table)
```

<!-- 核心分为 Bypass、Read、Write 三大场景。 -->
The main cases are Bypass, Read, and Write.

<!-- ### 4.3.1 Rename bypass（重命名转发） -->
### 4.3.1 Rename Bypass

<!-- 同一周期内多条指令经过重命名时，若前序指令改写了某架构寄存器的映射，后序指令读取该寄存器时必须拿到最新映射。Bypass 逻辑在**读表结果的基础上**，用同周期写端口的数据覆盖，实现零延迟依赖传递。 -->
When several instructions are renamed in one cycle, a later instruction must see the mapping just written by an earlier instruction in that cycle. Bypass overlays same-cycle write-port data on the table result, forwarding the dependency with no extra latency.

<!--
```plain
// RenameTable.scala：Bypass 逻辑
for ((r, i) <- io.readPorts.zipWithIndex) {
  // t0_bypass：检测当前周期写端口是否写了本读端口要查的架构寄存器
  val t0_bypass = io.specWritePorts.map(w => w.wen && Mux(r.hold, w.addr === t1_raddr(i), w.addr === r.addr))
  val t1_bypass = RegNext(Mux(io.redirect, 0.U.asTypeOf(VecInit(t0_bypass)), VecInit(t0_bypass)))
  val bypass_data = ParallelPriorityMux(t1_bypass.reverse, t1_wSpec.map(_.data).reverse)
  // 有 bypass 命中时用 bypass 数据，否则用表读结果
  r.data := Mux(t1_bypass.asUInt.orR, bypass_data, t1_rdata_use_t1_raddr(i))
}
```
-->
```plain
for ((r, i) <- io.readPorts.zipWithIndex) {
  val t0_bypass = io.specWritePorts.map(w => w.wen && Mux(r.hold, w.addr === t1_raddr(i), w.addr === r.addr))
  val t1_bypass = RegNext(Mux(io.redirect, 0.U.asTypeOf(VecInit(t0_bypass)), VecInit(t0_bypass)))
  val bypass_data = ParallelPriorityMux(t1_bypass.reverse, t1_wSpec.map(_.data).reverse)
  r.data := Mux(t1_bypass.asUInt.orR, bypass_data, t1_rdata_use_t1_raddr(i))
}
```

<!-- **时序优化**：为优化关键路径，RAT 的读写采用 T0/T1 两级流水（RenameTable.scala）： -->
**Timing optimization**: RAT reads and writes use a two-stage T0/T1 pipeline to shorten the critical path (RenameTable.scala):

<!-- * T0 周期：读地址到达，查表获取映射
* T0 周期：写数据同时 bypass 到 T1 的读结果
* T1 周期：最终读数据 = bypass 命中 ? bypass 数据 : 表读结果 -->
* T0: the read address arrives and the table is queried.
* T0: write data is bypassed to the T1 read result.
* T1: final read data = bypass hit ? bypass data : table data.

<!--
```plain
// 同周期两条指令，存在 RAW 依赖
add  x10, x1, x2  // I1：重命名 x10->p30，写 specWritePorts
and  x11, x10, x3 // I2：读 x10，查表得到旧映射
// Bypass 检测到 specWritePorts 写了 x10
// I2 的 psrc 直接获得 p30，无需等待 I1 写回 PRF
```
-->
```plain
add  x10, x1, x2
and  x11, x10, x3
```

> <!-- *\*\*注意：Bypass 不是"不查表"，而是"查表后再用写端口数据覆盖"。RAT 读始终先查 \*\**<code>_**spec_table**_</code>***，Bypass 只在同周期有写冲突时生效。*** -->
> ***Note***: Bypass does not skip the table. The RAT always reads <code>_**spec_table**_</code> first, and bypass replaces that result only when a same-cycle write conflict exists.

<!-- ### 4.3.2 Read 映射（读阶段） -->
### 4.3.2 Read Mapping

<!-- 指令读源寄存器时，重命名模块根据架构寄存器号查询 <code>**spec_table**</code>，取出对应的物理寄存器号（PR），发送给保留站与功能单元。读映射为只读查询，不修改映射表内容。 -->
When an instruction reads a source register, the rename unit queries <code>**spec_table**</code> with its architectural number, obtains the physical register (PR), and sends it to the reservation station and functional unit. This is read-only and does not change the table.

<!--
```scala
// Rename.scala ：根据源操作数类型选择对应 RAT 的读端口
uops(i).psrc(0) := Mux1H(uops(i).srcType(0)(2, 0), Seq(
  intReadPortsData(i)(0),  // 整数 RAT
  fpReadPortsData(i)(0),   // 浮点 RAT
  vecReadPortsData(i)(0)   // 向量 RAT
))
uops(i).psrc(3) := v0ReadPortsData(i)(0)   // v0 RAT
uops(i).psrcVl  := vlReadPortsData(i).head  // vl RAT
```
-->
```scala
uops(i).psrc(0) := Mux1H(uops(i).srcType(0)(2, 0), Seq(
  intReadPortsData(i)(0),
  fpReadPortsData(i)(0),
  vecReadPortsData(i)(0)
))
uops(i).psrc(3) := v0ReadPortsData(i)(0)   // v0 RAT
uops(i).psrcVl  := vlReadPortsData(i).head  // vl RAT
```

<!-- 每条指令最多读 5 种 RAT（int/fp/vec/v0/vl），由 <code>**srcType**</code> 决定实际读取哪些端口。 -->
An instruction can read up to five RATs (int/fp/vec/v0/vl); <code>**srcType**</code> selects the ports actually used.

<!-- ### 4.3.3 Write 映射（写阶段） -->
### 4.3.3 Write Mapping

<!-- 指令写目的寄存器时，重命名模块从 FreeList 分配新物理寄存器，将 <code>**spec_table**</code> 中该架构寄存器的映射更新为新 PR 号，同时保存旧 PR 号用于提交后回收。 -->
When an instruction writes a destination register, the rename unit allocates a new physical register from the FreeList, updates that architectural register's entry in <code>**spec_table**</code>, and saves the old PR for reclamation after commit.

<!--
```scala
// Rename.scala：分配物理寄存器并写入 pdest
uops(i).pdest := MuxCase(0.U, Seq(
  needIntDest(i) -> intFreeList.io.allocatePhyReg(i),
  needFpDest(i)  -> fpFreeList.io.allocatePhyReg(i),
  needVecDest(i) -> vecFreeList.io.allocatePhyReg(i),
  needV0Dest(i)  -> v0FreeList.io.allocatePhyReg(i),
))
uops(i).pdestVl := vlFreeList.io.allocatePhyReg(i)

// Rename.scala：投机写使能——满足所有条件才允许更新 spec_table
intSpecWen(i) := needIntDest(i) && intFreeList.io.canAllocate
  && intFreeList.io.doAllocate && !io.rabCommits.isWalk && !io.redirect.valid
```
-->
```scala
uops(i).pdest := MuxCase(0.U, Seq(
  needIntDest(i) -> intFreeList.io.allocatePhyReg(i),
  needFpDest(i)  -> fpFreeList.io.allocatePhyReg(i),
  needVecDest(i) -> vecFreeList.io.allocatePhyReg(i),
  needV0Dest(i)  -> v0FreeList.io.allocatePhyReg(i),
))
uops(i).pdestVl := vlFreeList.io.allocatePhyReg(i)

intSpecWen(i) := needIntDest(i) && intFreeList.io.canAllocate
  && intFreeList.io.doAllocate && !io.rabCommits.isWalk && !io.redirect.valid
```

<!--
```scala
// RenameTable.scala：spec_table 写入逻辑
val t1_wSpec_addr = t1_wSpec.map(w => Mux(w.wen, UIntToOH(w.addr), 0.U))
for ((next, i) <- spec_table_next.zipWithIndex) {
  val matchVec = t1_wSpec_addr.map(w => w(i))
  val wMatch = ParallelPriorityMux(matchVec.reverse, t1_wSpec.map(_.data).reverse)
  next := Mux(
    RegNext(t1_redirect),                                               // 冲刷：恢复快照或 arch_table
    Mux(t2_snpt.useSnpt, snapshots(t2_snpt.snptSelect)(i), arch_table(i)),
    Mux(VecInit(matchVec).asUInt.orR, wMatch, spec_table(i))            // 正常：写入新映射
  )
}
```
-->
```scala
val t1_wSpec_addr = t1_wSpec.map(w => Mux(w.wen, UIntToOH(w.addr), 0.U))
for ((next, i) <- spec_table_next.zipWithIndex) {
  val matchVec = t1_wSpec_addr.map(w => w(i))
  val wMatch = ParallelPriorityMux(matchVec.reverse, t1_wSpec.map(_.data).reverse)
  next := Mux(
    RegNext(t1_redirect),
    Mux(t2_snpt.useSnpt, snapshots(t2_snpt.snptSelect)(i), arch_table(i)),
    Mux(VecInit(matchVec).asUInt.orR, wMatch, spec_table(i))
  )
}
```

<!-- **写映射会同时更新两个表**： -->
**A write mapping updates two tables at different times**:

<!-- * <code>**spec_table**</code>：重命名阶段立即更新（投机状态）
* <code>**arch_table**</code>：指令提交后才更新（架构状态） -->
* <code>**spec_table**</code>: updated immediately during rename (speculative state).
* <code>**arch_table**</code>: updated only after commit (architectural state).

<!-- **旧映射的保存与回收**： -->
**Saving and reclaiming old mappings**:

<!--
```scala
// RenameTable.scala：提交时更新 arch_table 并判断旧 PR 是否可释放
for ((w, i) <- io.archWritePorts.zipWithIndex) {
  when (w.wen) { arch_table_next(w.addr) := w.data }
  // old_pdest：该架构寄存器在 arch_table 中的旧映射
  old_pdest(i) := MuxCase(arch_table(w.addr) & arch_mask,
    io.archWritePorts.take(i).reverse.map(x => (x.wen && x.addr === w.addr, x.data & arch_mask)))
}
// need_free：旧 PR 不再被任何架构寄存器引用时，才可释放
for (((old, free), i) <- (old_pdest zip need_free).zipWithIndex) {
  val hasDuplicate = old_pdest.take(i).map(_ === old)
  val blockedByDup = if (i == 0) false.B else VecInit(hasDuplicate).asUInt.orR
  free := VecInit(arch_table.map(_ =/= old)).asUInt.andR && !blockedByDup
}
```
-->
```scala
for ((w, i) <- io.archWritePorts.zipWithIndex) {
  when (w.wen) { arch_table_next(w.addr) := w.data }
  old_pdest(i) := MuxCase(arch_table(w.addr) & arch_mask,
    io.archWritePorts.take(i).reverse.map(x => (x.wen && x.addr === w.addr, x.data & arch_mask)))
}
for (((old, free), i) <- (old_pdest zip need_free).zipWithIndex) {
  val hasDuplicate = old_pdest.take(i).map(_ === old)
  val blockedByDup = if (i == 0) false.B else VecInit(hasDuplicate).asUInt.orR
  free := VecInit(arch_table.map(_ =/= old)).asUInt.andR && !blockedByDup
}
```

<!--
> *\*\*写映射示例：执行 \*\**<code>_**add x10, x5, x6**_</code>***，***<code>_**intFreeList**_</code>*\*\* 分配 p35，\*\**<code>_**spec_table**_</code>*\*\* 更新 x10→p35（旧映射 x10→p30 被覆盖）。指令提交后，\*\**<code>_**arch_table**_</code>*\*\* 更新 x10→p35，旧 p30 经 \*\**<code>_**need_free**_</code>*\*\* 判断后归还 FreeList。\*\**

-->
> **Write-mapping example**: `intFreeList` allocates p35 for `add x10, x5, x6`; `spec_table` changes x10 to p35 and overwrites the old p30 mapping. At commit, `arch_table` changes to p35 and `need_free` returns p30 when no architectural entry still references it.

<!-- ## 4.4 Freelist 空闲链表机制 -->
## 4.4 FreeList Mechanism

<!-- FreeList 是物理寄存器的资源管理器，负责统一管理所有 PRF 的分配与回收，保证物理寄存器资源循环利用。香山有**两种 FreeList 实现**： -->
The FreeList manages physical-register allocation and reclamation so PRF resources can be reused. XiangShan has **two FreeList implementations**:

<!-- | **类型** | **使用场景** | **分配策略** | **回收策略** |
| --- | --- | --- | --- |
| <code>**MEFreeList**</code> | 整数寄存器（intFreeList） | 与 StdFreeList 相同 | 引用计数归零时释放 |
| <code>**StdFreeList**</code> | fp/vec/v0/vl | 从空闲队列头部取 | 提交时直接归还队尾 | -->

| **Type** | **Use** | **Allocation** | **Reclamation** |
| --- | --- | --- | --- |
| <code>**MEFreeList**</code> | Integer registers (intFreeList) | Same as StdFreeList | Release when the reference count reaches zero |
| <code>**StdFreeList**</code> | fp/vec/v0/vl | Take from the queue head | Return directly to the tail at commit |

<!-- ### 4.4.1 Freelist Update（更新） -->
### 4.4.1 FreeList Update

<!-- 重命名阶段分配新物理寄存器时，FreeList 从空闲队列头部取出可用 PR 号，分配给当前写指令，同时 head 指针前移。 -->
When rename allocates a physical register, the FreeList takes an available PR from the head of its free queue, assigns it to the writing instruction, and advances the head pointer.

<!--
```scala
// StdFreeList.scala：空闲队列初始化，编号从 numLogicRegs 开始（前 numLogicRegs 个保留给初始映射）
val freeList = RegInit(VecInit(Seq.tabulate(freeListSize)(i => (i + numLogicRegs).U(PhyRegIdxWidth.W))))

//空闲寄存器数量 >= RenameWidth 时才允许分配
val freeRegCnt = Wire(UInt())
io.canAllocate := GatedValidRegNext(freeRegCnt >= RenameWidth.U)

//根据 head 指针和当前分配请求数，输出物理寄存器编号
val phyRegCandidates = Mux1H(headPtrOHVec(0), freeListVec)
for (i <- 0 until RenameWidth) {
  io.allocatePhyReg(i) := phyRegCandidates(PopCount(io.allocateReq.take(i)))
}
```
-->
```scala
val freeList = RegInit(VecInit(Seq.tabulate(freeListSize)(i => (i + numLogicRegs).U(PhyRegIdxWidth.W))))

val freeRegCnt = Wire(UInt())
io.canAllocate := GatedValidRegNext(freeRegCnt >= RenameWidth.U)

val phyRegCandidates = Mux1H(headPtrOHVec(0), freeListVec)
for (i <- 0 until RenameWidth) {
  io.allocatePhyReg(i) := phyRegCandidates(PopCount(io.allocateReq.take(i)))
}
```

<!-- **Move 指令优化**：整数 Move 指令（如 <code>**mv x10, x5**</code>）不需要分配新物理寄存器，直接将源操作数的物理寄存器号作为目标映射： -->
**Move optimization**: an integer Move instruction such as <code>**mv x10, x5**</code> allocates no new physical register; it uses the source PR as the destination mapping:

<!--
```scala
// Rename.scala ：Move 指令跳过 intFreeList 分配
intFreeList.io.allocateReq(i) := needIntDest(i) && !isMove(i)
```
-->
```scala
intFreeList.io.allocateReq(i) := needIntDest(i) && !isMove(i)
```

<!--
> *\*\*分配示例：初始空闲队列 \*\**<code>_**[p32, p33, p34, ...]**_</code>*\*\*，执行 \*\**<code>_**sub x11, x2, x3**_</code>*\*\*，从队首取出 p32 分配给 x11，head 指针前移，空闲队列变为 \*\**<code>_**[p33, p34, ...]**_</code>

-->
> **Allocation example**: with free queue `[p32, p33, p34, ...]`, `sub x11, x2, x3` takes p32 for x11, advances the head, and leaves `[p33, p34, ...]`.

<!-- ### 4.4.2 Freelist Release（释放回收） -->
### 4.4.2 FreeList Release

<!-- 指令提交后，其占用的**旧物理寄存器**不再是架构寄存器的最新映射，此时触发 Release 回收逻辑。 -->
After an instruction commits, its **old physical register** is no longer the latest mapping of the architectural register, so release logic reclaims it.

<!-- #### StdFreeList 的回收 -->
#### StdFreeList Reclamation

<!-- 提交时直接将旧 PR 号归还空闲队列尾部 -->
At commit, the old PR is returned directly to the tail of the free queue.

<!--
```scala
// StdFreeList.scala：将提交释放的旧物理寄存器写入空闲队列尾部
val freePtr = VecInit(Seq.tabulate(commitWidth)(i => tailPtr + PopCount(io.freeReq.take(i))))
for (i <- 0 until freeListSize) {
  val freeReqOH = VecInit(io.freeReq.zipWithIndex.map { case (w, idx) =>
    w && freePtr(idx).value === i.U
  })
  val freePhyReg = Mux1H(freeReqOH, io.freePhyReg)
  when(freeReqOH.asUInt.orR) {
    freeList(i) := freePhyReg
  }
}
tailPtr := tailPtr + PopCount(io.freeReq)  // tail 指针前移
```
-->
```scala
val freePtr = VecInit(Seq.tabulate(commitWidth)(i => tailPtr + PopCount(io.freeReq.take(i))))
for (i <- 0 until freeListSize) {
  val freeReqOH = VecInit(io.freeReq.zipWithIndex.map { case (w, idx) =>
    w && freePtr(idx).value === i.U
  })
  val freePhyReg = Mux1H(freeReqOH, io.freePhyReg)
  when(freeReqOH.asUInt.orR) {
    freeList(i) := freePhyReg
  }
}
tailPtr := tailPtr + PopCount(io.freeReq)
```

<!-- #### MEFreeList 的回收 -->
#### MEFreeList Reclamation

<!-- MEFreeList 采用**引用计数**机制 -->
MEFreeList uses **reference counting**.

<!--
```scala
// MEFreeList.scala：当引用计数归零时才释放物理寄存器
val freePtr = VecInit(Seq.tabulate(commitWidth)(i => tailPtr + PopCount(io.freeReq.take(i))))
for (i <- 0 until size) {
  val freeReqOH = VecInit(io.freeReq.zipWithIndex.map { case (w, idx) =>
    w && freePtr(idx).value === i.U
  })
  val freePhyReg = Mux1H(freeReqOH, io.freePhyReg)
  when(freeReqOH.asUInt.orR) {
    freeList(i) := freePhyReg
  }
}
```
-->
```scala
val freePtr = VecInit(Seq.tabulate(commitWidth)(i => tailPtr + PopCount(io.freeReq.take(i))))
for (i <- 0 until size) {
  val freeReqOH = VecInit(io.freeReq.zipWithIndex.map { case (w, idx) =>
    w && freePtr(idx).value === i.U
  })
  val freePhyReg = Mux1H(freeReqOH, io.freePhyReg)
  when(freeReqOH.asUInt.orR) {
    freeList(i) := freePhyReg
  }
}
```

<!-- MEFreeList 的 <code>**freeReq**</code> 来自 RAT 的 <code>**need_free**</code> 信号（RenameTable.scala），只有当旧 PR 不再被 <code>**arch_table**</code> 中任何条目引用时才释放——因为整数寄存器可能被 Move 指令共享同一个物理寄存器，必须等所有引用消失后才能回收。 -->
MEFreeList receives <code>**freeReq**</code> from the RAT's <code>**need_free**</code> signal (RenameTable.scala). It releases an old PR only when no entry in <code>**arch_table**</code> refers to it. Integer registers can share a PR through Move elimination, so every reference must disappear first.

<!-- #### 回收的前提：<code>**need_free**</code> 判断 -->
#### Reclamation Condition: <code>**need_free**</code>

<!--
```scala
// RenameTable.scala：判断旧 PR 是否可释放
free := VecInit(arch_table.map(_ =/= old)).asUInt.andR && !blockedByDup
// 条件1：旧 PR 不被 arch_table 中任何条目引用
// 条件2：本次提交中没有更早的同号 old_pdest（避免重复释放）
```
-->
```scala
free := VecInit(arch_table.map(_ =/= old)).asUInt.andR && !blockedByDup
```

<!--
> ***回收示例：***<code>_**add x10, x5, x6**_</code>*\*\* 提交时，x10 的旧映射 p30 检查 \*\**<code>_**need_free**_</code>*\*\*——若 \*\**<code>_**arch_table**_</code>*\*\* 中没有其他条目映射到 p30，则 p30 归还 FreeList；若 p30 仍被某 Move 指令的映射引用，则暂不释放。\*\**

-->
> **Release example**: at commit, `need_free` checks x10's old p30 mapping. If no other `arch_table` entry refers to p30, it returns to FreeList; if a Move still shares p30, release is deferred.

:::danger
<!-- **关键易错点**：物理寄存器绝对不能在指令执行完成后立即回收，必须等待指令提交！乱序执行中指令可能冲刷作废，提前回收会导致数据覆盖、程序出错。此外，即使提交后，也必须通过 <code>**need_free**</code> 检查确认旧 PR 确实无人引用后才能释放。 -->
**Common pitfall**: Never reclaim a physical register immediately after execution; wait for commit. An out-of-order instruction may be flushed, and early reuse can overwrite live data. Even after commit, release only after <code>**need_free**</code> confirms that no reference remains.

:::

<!-- ## 4.5 Redirect 重定向机制 -->
## 4.5 Redirect Recovery

<!-- Redirect 是流水线异常冲刷、分支预测错误时的全局状态恢复机制。当 Redirect 发生时，所有 5 套 RAT、5 套 FreeList、VTypeBuffer 都需要协同回退到正确的映射状态。 -->
Redirect is the global recovery mechanism for pipeline flushes and branch mispredictions. When it occurs, all five RATs, five FreeLists, and the VTypeBuffer must roll back together to the correct mapping state.

<!-- ### 4.5.1 Redirect 触发条件 -->
### 4.5.1 Redirect Triggers

<!-- 满足以下任意场景，触发重定向： -->
Redirect is triggered by any of the following:

<!-- * **分支预测错误**，流水线全局冲刷
* **指令执行触发异常、中断**，流水线回退
* **投机执行路径作废**，需要恢复正确架构状态 -->
* **Branch misprediction**, causing a global flush.
* **An exception or interrupt during execution**, causing rollback.
* **Cancellation of a speculative path**, requiring the correct architectural state.

<!-- ### 4.5.2 RAT恢复机制 -->
### 4.5.2 RAT Recovery

<!-- Redirect 发生时，投机映射表（<code>**spec_table**</code>）的恢复有两条路径： -->
When Redirect occurs, <code>**spec_table**</code> can be restored in two ways:

<!--
```scala
// RenameTable.scala：spec_table 恢复逻辑
next := Mux(
  RegNext(t1_redirect),
  Mux(t2_snpt.useSnpt, snapshots(t2_snpt.snptSelect)(i), arch_table(i)),  // 有快照→用快照，无快照→用 arch_table
  Mux(VecInit(matchVec).asUInt.orR, wMatch, spec_table(i))                 // 无 redirect→正常写入
)
```
-->
```scala
next := Mux(
  RegNext(t1_redirect),
  Mux(t2_snpt.useSnpt, snapshots(t2_snpt.snptSelect)(i), arch_table(i)),
  Mux(VecInit(matchVec).asUInt.orR, wMatch, spec_table(i))
)
```

<!-- * **有可用快照**（<code>**useSnpt=true**</code>）：从 <code>**SnapshotGenerator**</code> 中选取对应快照，直接覆盖 <code>**spec_table**</code>，一步恢复
* **无可用快照**：用 <code>**arch_table**</code>（已提交的架构映射表）覆盖 <code>**spec_table**</code>，再通过 ROB Walk 逐步恢复 -->
* **Snapshot available** (<code>**useSnpt=true**</code>): select the snapshot from <code>**SnapshotGenerator**</code> and overwrite <code>**spec_table**</code> in one step.
* **No snapshot**: overwrite <code>**spec_table**</code> with the committed <code>**arch_table**</code>, then rebuild speculative mappings through a ROB walk.

<!-- 两条路径对所有 5 套 RAT 都一样——intRAT、fpRAT、vecRAT、v0RAT、vlRAT 共享同一个 <code>**SnapshotGenerator**</code> 和同一套快照选择逻辑。 -->
Both paths apply to all five RATs (intRAT, fpRAT, vecRAT, v0RAT, and vlRAT), which share one <code>**SnapshotGenerator**</code> and selection logic.

<!-- ### 4.5.3 VType 恢复机制 -->
### 4.5.3 VType Recovery

<!-- VType 寄存器**不通过 RAT 重命名**，而是由独立的 <code>**VTypeBuffer**</code> 管理。VTypeBuffer 是一个环形队列（VTypeBuffer.scala），每个 VSET 指令入队一个 VType 条目，提交时出队。 -->
The VType register is **not renamed through a RAT**. It is managed by the independent <code>**VTypeBuffer**</code>, a circular queue in which each VSET instruction enqueues a VType entry and commit dequeues it.

<!-- Redirect 时 VTypeBuffer 的恢复也依赖快照： -->
VTypeBuffer recovery on Redirect also relies on snapshots:

<!--
```scala
// VTypeBuffer.scala：VTypeBuffer 的两个快照
private val walkPtrSnapshots = SnapshotGenerator(enqPtr, io.snpt.snptEnq, io.snpt.snptDeq,
  io.redirect.valid, io.snpt.flushVec)                    // 快照1：入队指针
private val walkVTypeSnapshots = SnapshotGenerator(enqVType, io.snpt.snptEnq, io.snpt.snptDeq,
  io.redirect.valid, io.snpt.flushVec)                    // 快照2：VType 值
```
-->
```scala
private val walkPtrSnapshots = SnapshotGenerator(enqPtr, io.snpt.snptEnq, io.snpt.snptDeq,
  io.redirect.valid, io.snpt.flushVec)
private val walkVTypeSnapshots = SnapshotGenerator(enqVType, io.snpt.snptEnq, io.snpt.snptDeq,
  io.redirect.valid, io.snpt.flushVec)
```

<!-- * <code>**walkPtrSnapshots**</code>：保存 VTypeBuffer 的入队指针，用于确定回退的起始位置
* <code>**walkVTypeSnapshots**</code>：保存当时的 VType 值，用于恢复投机 VType 状态 -->
* <code>**walkPtrSnapshots**</code>: saves the enqueue pointer and identifies where rollback starts.
* <code>**walkVTypeSnapshots**</code>: saves the VType value for restoring speculative VType state.

<!-- 回退时，VTypeBuffer 进入 Walk 状态，从快照指定的位置开始逆序遍历（VTypeBuffer.scala）： -->
During rollback, VTypeBuffer enters Walk state and traverses backward from the snapshot position (VTypeBuffer.scala):

<!--
```scala
// VTypeBuffer.scala：Walk 指针的恢复
walkPtrNext := MuxCase(walkPtr, Seq(
  (state === s_idle && stateNext === s_walk) -> walkPtrSnapshots(snptSelect),       // 从快照恢复
  (state === s_spcl_walk && stateNext === s_walk) -> deqPtrVecNext.head,
  (state === s_walk && io.snpt.useSnpt && io.redirect.valid) -> walkPtrSnapshots(snptSelect),
  (state === s_walk) -> (walkPtr + walkCount),                                        // 正常 Walk 递增
))
```
-->
```scala
walkPtrNext := MuxCase(walkPtr, Seq(
  (state === s_idle && stateNext === s_walk) -> walkPtrSnapshots(snptSelect),
  (state === s_spcl_walk && stateNext === s_walk) -> deqPtrVecNext.head,
  (state === s_walk && io.snpt.useSnpt && io.redirect.valid) -> walkPtrSnapshots(snptSelect),
  (state === s_walk) -> (walkPtr + walkCount),
))
```

> <!-- ***VType Redirect 恢复示例：*** -->
> ***VType Redirect recovery example:***
>
> <!-- 1. ***执行 VSETVLI，当前 VType=0b1010、vl=8，VTypeBuffer 入队该条目，同时创建快照*** -->
> 1. Execute VSETVLI with VType=0b1010 and vl=8; enqueue the entry and create a snapshot.
> <!-- 2. ***投机执行后续 VSETVLI，临时修改 VType=0b1100，VTypeBuffer 追加新条目*** -->
> 2. Speculatively execute a later VSETVLI, temporarily changing VType to 0b1100 and appending an entry.
> <!-- 3. ***触发分支预测错误，流水线冲刷*** -->
> 3. A branch misprediction triggers a pipeline flush.
> <!-- 4. ***VTypeBuffer 从快照恢复 walk 指针，逆序回退到 VType=0b1010 的条目*** -->
> 4. VTypeBuffer restores its walk pointer and walks backward to the VType=0b1010 entry.
> <!-- 5. ***vlRAT 同时从快照恢复 vl 的物理寄存器映射*** -->
> 5. vlRAT simultaneously restores vl's physical-register mapping from the snapshot.

<!-- **注意**：vl 和 VType 的恢复机制不同——vl 通过标准 RAT + FreeList 重命名恢复，VType 通过独立的 VTypeBuffer 环形队列 + 快照恢复。二者共享同一个 <code>**SnapshotGenerator**</code> 的创建/删除/选择信号，但数据通路完全独立。 -->
**Note**: vl and VType recover differently. vl uses the standard RAT + FreeList renaming path; VType uses the independent VTypeBuffer queue and snapshots. They share SnapshotGenerator create/delete/select controls, but their datapaths are independent.

<!-- **Vtype Redirect恢复示例**： -->
**VType Redirect recovery example**:

<!-- 1. 执行向量指令，当前Vtype=0b1010、vl=8，硬件备份该快照； -->
1. Execute a vector instruction with Vtype=0b1010 and vl=8; hardware saves a snapshot.

<!-- 2. 投机执行后续向量指令，临时修改Vtype=0b1100； -->
2. Speculatively execute later vector instructions and temporarily change Vtype to 0b1100.

<!-- 3. 触发分支错误流水线冲刷； -->
3. A branch error triggers a pipeline flush.

<!-- 4. Redirect机制读取备份快照，将Vtype恢复为0b1010，保证后续向量指令执行合法。 -->
4. Redirect reads the saved snapshot and restores Vtype=0b1010 so later vector instructions remain legal.

<!-- ## 4.6 重命名表快照恢复（Snapshot） -->
## 4.6 Rename-Table Snapshot Recovery

<!-- Snapshot 是香山重命名模块的核心恢复机制。当 Redirect 发生时，快照提供了**比 arch\_table 全量覆盖更精准**的恢复方式——直接恢复到分支点时的映射状态，避免漫长的 ROB Walk。 -->
Snapshot is the rename unit's core recovery mechanism. On Redirect it offers a **more precise alternative to overwriting arch\_table wholesale**: restore the mapping at the branch point directly and avoid a long ROB walk.

<!-- ### 4.6.1 Snapshot架构 -->
### 4.6.1 Snapshot Architecture

<!-- #### 香山使用统一的 <code>**SnapshotGenerator**</code> 管理所有快照（Snapshot.scala）： -->
#### XiangShan uses one <code>**SnapshotGenerator**</code> for all snapshots (Snapshot.scala):

<!--
```scala
// Snapshot.scala：快照存储结构
val snapshots = Reg(Vec(RenameSnapshotNum, chiselTypeOf(dataType)))// RenameSnapshotNum 个快照槽位
val snptEnqPtr = RegInit(0.U.asTypeOf(new SnapshotPtr))            // 入队指针
val snptDeqPtr = RegInit(0.U.asTypeOf(new SnapshotPtr))            // 出队指针
val snptValids = RegInit(VecInit.fill(RenameSnapshotNum)(false.B)) // 每个槽位的有效位
```
-->
```scala
val snapshots = Reg(Vec(RenameSnapshotNum, chiselTypeOf(dataType)))// RenameSnapshotNum
val snptEnqPtr = RegInit(0.U.asTypeOf(new SnapshotPtr))
val snptDeqPtr = RegInit(0.U.asTypeOf(new SnapshotPtr))
val snptValids = RegInit(VecInit.fill(RenameSnapshotNum)(false.B))
```

<!-- 快照的数量 <code>**RenameSnapshotNum**</code> 是有限的硬件资源，决定了处理器能同时追踪的最大投机分支数。 -->
The number of snapshots, <code>**RenameSnapshotNum**</code>, is a finite hardware resource and bounds the number of speculative branches tracked concurrently.

<!-- ### 4.6.2 Snapshot Create（创建快照） -->
### 4.6.2 Snapshot Create

<!-- 当流水线检测到分支指令时，自动创建当前周期的重命名映射表快照。创建逻辑（Snapshot.scala）： -->
When the pipeline detects a branch, it automatically snapshots the rename map for the current cycle (Snapshot.scala):

<!--
```scala
// Snapshot.scala：快照创建
when(!io.redirect && !isFull(snptEnqPtr, snptDeqPtr) && io.enq) {
  snapshots(snptEnqPtr.value) := io.enqData   // 保存当前 spec_table 的完整拷贝
  snptValids(snptEnqPtr.value) := true.B
  snptEnqPtr := snptEnqPtr + 1.U
}
```
-->
```scala
when(!io.redirect && !isFull(snptEnqPtr, snptDeqPtr) && io.enq) {
  snapshots(snptEnqPtr.value) := io.enqData
  snptValids(snptEnqPtr.value) := true.B
  snptEnqPtr := snptEnqPtr + 1.U
}
```

<!-- 在 <code>**RenameTable**</code> 中，快照的创建入口 -->
The snapshot creation entry in <code>**RenameTable**</code> is:

<!--
```scala
// RenameTable.scala：所有 5 套 RAT 共享同一个 SnapshotGenerator 的控制信号
val snapshots = SnapshotGenerator(spec_table, t1_snpt.snptEnq, t1_snpt.snptDeq, t1_redirect, t1_snpt.flushVec)
```
-->
```scala
val snapshots = SnapshotGenerator(spec_table, t1_snpt.snptEnq, t1_snpt.snptDeq, t1_redirect, t1_snpt.flushVec)
```

<!-- 关键点：**所有 5 套 RAT 使用相同的 **<code>**snptEnq**</code>**/**<code>**snptDeq**</code>**/**<code>**flushVec**</code>\*\* 信号\*\*，保证快照的创建、删除、选择在所有 RAT 之间严格同步。VTypeBuffer 也使用相同的控制信号创建快照。 -->
**Key point**: all five RATs use the same <code>**snptEnq**</code>, <code>**snptDeq**</code>, and <code>**flushVec**</code> controls, keeping snapshot creation, deletion, and selection synchronized. VTypeBuffer uses the same controls.

<!-- ### 4.6.3 Snapshot Delete（删除快照） -->
### 4.6.3 Snapshot Delete

<!-- 当分支指令预测正确、正常提交后，对应快照被删除，释放快照槽位 -->
When a branch is predicted correctly and commits, its snapshot is deleted and the slot is released.

<!--
```scala
// Snapshot.scala：快照删除
when(!io.redirect && io.deq) {
  snptValids(snptDeqPtr.value) := false.B
  snptDeqPtr := snptDeqPtr + 1.U
}
```
-->
```scala
when(!io.redirect && io.deq) {
  snptValids(snptDeqPtr.value) := false.B
  snptDeqPtr := snptDeqPtr + 1.U
}
```

<!-- 当 Redirect 冲刷导致某个快照作废时，通过 <code>**flushVec**</code> 批量无效化 -->
When a Redirect flush invalidates snapshots, <code>**flushVec**</code> invalidates them in bulk:

<!--
```scala
// Snapshot.scala：冲刷时批量无效化快照
snptValids.zip(io.flushVec).foreach { case (valid, flush) =>
  when(flush) { valid := false.B }
}
```
-->
```scala
snptValids.zip(io.flushVec).foreach { case (valid, flush) =>
  when(flush) { valid := false.B }
}
```

<!-- 冲刷后，<code>**snptEnqPtr**</code> 需要跳过被无效化的槽位，回收到第一个空闲位置（Snapshot.scala），这是一个复杂的指针重定位逻辑。 -->
After a flush, <code>**snptEnqPtr**</code> must skip invalidated slots and relocate to the first free slot (Snapshot.scala), which requires non-trivial pointer repositioning.

<!-- ### 4.6.4 Snapshot Restore（恢复快照） -->
### 4.6.4 Snapshot Restore

<!-- 当分支预测错误、投机路径作废时，恢复逻辑在 <code>**RenameTable**</code> 中执行 -->
When a branch is mispredicted and the speculative path is cancelled, <code>**RenameTable**</code> performs recovery:

<!--
```scala
// RenameTable.scala：Redirect 后 spec_table 恢复
next := Mux(
  RegNext(t1_redirect),
  Mux(t2_snpt.useSnpt, snapshots(t2_snpt.snptSelect)(i), arch_table(i)),
  Mux(VecInit(matchVec).asUInt.orR, wMatch, spec_table(i))
)
```
-->
```scala
next := Mux(
  RegNext(t1_redirect),
  Mux(t2_snpt.useSnpt, snapshots(t2_snpt.snptSelect)(i), arch_table(i)),
  Mux(VecInit(matchVec).asUInt.orR, wMatch, spec_table(i))
)
```

<!-- 恢复时的选择： -->
Recovery selects one of two paths:

<!-- * **有可用快照**（<code>**useSnpt=true**</code>）：用 <code>**snapshots(snptSelect)**</code> 直接覆盖 <code>**spec_table**</code> 每一项，一步恢复到分支点的映射状态
* **无可用快照**：用 <code>**arch_table**</code> 覆盖 <code>**spec_table**</code>，然后通过 ROB Walk 逐步重建投机映射 -->
* **Snapshot available** (<code>**useSnpt=true**</code>): overwrite each <code>**spec_table**</code> entry with <code>**snapshots(snptSelect)**</code> and restore the branch-point mapping in one step.
* **No snapshot**: overwrite <code>**spec_table**</code> with <code>**arch_table**</code> and rebuild speculative mappings through a ROB walk.

<!-- 快照恢复的时序经过两级流水： -->
Snapshot restoration uses a two-stage pipeline:

<!--
```scala
// RenameTable.scala：快照恢复的时序优化
val t1_redirect = GatedValidRegNext(io.redirect, false.B) // T0→T1：redirect 信号延迟一拍
val t1_snpt = RegNext(io.snpt, 0.U.asTypeOf(io.snpt))     // T0→T1：快照控制信号延迟一拍
val t2_snpt = RegNext(t1_snpt, 0.U.asTypeOf(io.snpt))     // T1→T2：快照选择信号再延迟一拍
```
-->
```scala
val t1_redirect = GatedValidRegNext(io.redirect, false.B)
val t1_snpt = RegNext(io.snpt, 0.U.asTypeOf(io.snpt))
val t2_snpt = RegNext(t1_snpt, 0.U.asTypeOf(io.snpt))
```

<!-- T0 周期检测到 Redirect → T1 周期锁存 Redirect 信号和快照指针 → T2 周期实际执行 spec\_table 覆盖。这个两级延迟是为了优化关键路径时序。 -->
Redirect is detected in T0, its signal and snapshot pointer are latched in T1, and <code>spec_table</code> is overwritten in T2. The two-cycle delay improves critical-path timing.

<!-- ### 4.6.5 快照机制运行示例 -->
### 4.6.5 Snapshot Walkthrough

<!--
```plain
// 1. 分支指令 B1，创建快照 S1
//    快照保存：intRAT(x10→p21)、fpRAT 全表、vlRAT 全表、VTypeBuffer 入队指针
beq  x1, x2, loop

// 2. 投机执行分支路径指令，持续更新映射
add  x10, x5, x6   // intRAT 更新：x10→p25（旧映射 p21 仍在快照 S1 中）
sub  f1,  f2, f3    // fpRAT 更新：f1→p40
vsetvli x0, x1, e8  // vlRAT 更新：vl→p_vl_5；VTypeBuffer 追加新条目

// 3. 分支结果校验：预测错误，分支不跳转
// 4. 触发 Snapshot Restore（useSnpt=true, snptSelect=S1）：
//    intRAT 恢复至 S1：x10→p21（p25 后续由 FreeList 回收）
//    fpRAT 恢复至 S1：f1→旧映射
//    vlRAT 恢复至 S1：vl→旧映射
//    VTypeBuffer walk 指针恢复至 S1 的入队指针位置
// 5. 删除 S1 及之后的所有快照，从正确地址重新取指执行
```
-->
```plain
beq  x1, x2, loop

add  x10, x5, x6
sub  f1,  f2, f3
vsetvli x0, x1, e8

```

<!-- **关键认知**：快照不是 Redirect 的"替代方案"，而是 Redirect 恢复过程中的**加速路径**。没有快照时，Redirect 仍然可以通过 arch\_table + ROB Walk 完成恢复，只是需要更多周期。快照的数量（<code>**RenameSnapshotNum**</code>）决定了处理器能同时追踪的最大投机分支深度——当快照槽位全部占满时，新的分支指令必须等待旧快照释放后才能继续推进。 -->
**Key point**: A snapshot is not a replacement for Redirect; it is an **accelerated recovery path**. Without one, Redirect can still restore state through arch\_table + ROB Walk, but it takes more cycles. <code>**RenameSnapshotNum**</code> limits speculative branch depth; when all slots are full, a new branch waits for an old snapshot to be released.

<!-- ## 4.7 Rename 和 ROB Compressed 工作原理 -->
## 4.7 Rename and ROB Compression

<!-- ROB Compress（ROB 压缩）是香山架构的关键优化机制——**将多条可压缩指令合并到同一个 ROB 条目中**，减少 ROB 项占用，扩大等效指令窗口。注意：这与"连续写同一寄存器"无关，压缩条件由 <code>**canRobCompress**</code> 标记决定。 -->
ROB compression is a key XiangShan optimization: **merge multiple compressible instructions into one ROB entry**, reducing ROB occupancy and increasing the effective instruction window. It is unrelated to consecutive writes of one register; <code>**canRobCompress**</code> determines eligibility.

<!-- ### 4.7.1 ROB Compressed 触发条件 -->
### 4.7.1 ROB Compression Triggers

<!-- 一条指令能否参与 ROB 压缩，由 <code>**canRobCompress**</code> 位控制（译码阶段设置）。<code>**CompressUnit**</code> 在重命名阶段检测连续可压缩指令，将它们合并到同一个 ROB 条目： -->
The decode stage sets <code>**canRobCompress**</code>. During rename, <code>**CompressUnit**</code> detects consecutive eligible instructions and merges them into one ROB entry:

<!--
```scala
// CompressUnit.scala：压缩条件
val noExc = io.in.map(in => !in.bits.exceptionVec.orR && !TriggerAction.isDmode(in.bits.trigger))
val uopCanCompress = io.in.map(_.bits.canRobCompress)
val canCompress = io.in.zip(noExc).zip(uopCanCompress).map { case ((in, noExc), canComp) =>
  in.valid && in.bits.lastUop && noExc && canComp
}
```
-->
```scala
val noExc = io.in.map(in => !in.bits.exceptionVec.orR && !TriggerAction.isDmode(in.bits.trigger))
val uopCanCompress = io.in.map(_.bits.canRobCompress)
val canCompress = io.in.zip(noExc).zip(uopCanCompress).map { case ((in, noExc), canComp) =>
  in.valid && in.bits.lastUop && noExc && canComp
}
```

<!-- 可压缩的条件： -->
Compression requires:

<!-- * 指令有效且是最后一个 uop（<code>**lastUop**</code>）
* 无异常、无 Debug 触发
* 译码时标记了 <code>**canRobCompress=true**</code>
* 属于同一 Fetch Block（跨 FTQ 边界的指令对不压缩） -->
* The instruction is valid and is the final uop (<code>**lastUop**</code>).
* It has no exception or Debug trigger.
* Decode marked <code>**canRobCompress=true**</code>.
* It belongs to the same Fetch Block (instructions across an FTQ boundary are not compressed).

<!-- 此外，<code>**blockBackward**</code> 类指令（如 VSET）会被排除在压缩之外： -->
Instructions classified as <code>**blockBackward**</code> (for example, VSET) are excluded:

<!--
```scala
//blockBackward 类指令强制不压缩
Seq((FuType.isBlockBackCompress(in.bits.fuType) && in.valid && backendParams.robCompressEn.B) || canComp, ...)
```
-->
```scala
Seq((FuType.isBlockBackCompress(in.bits.fuType) && in.valid && backendParams.robCompressEn.B) || canComp, ...)
```

<!-- ### 4.7.2 压缩结果：needRobFlags 与 instrSizes -->
### 4.7.2 Compression Results: needRobFlags and instrSizes

<!-- <code>**CompressUnit**</code> 输出两个关键信号 -->
<code>**CompressUnit**</code> outputs two key signals:

<!-- * <code>**needRobFlags**</code>：每条指令是否需要独占一个 ROB 项。被压缩的指令 <code>**needRobFlag=false**</code>，与前面的指令共享 ROB 条目
* <code>**instrSizes**</code>：每个 ROB 条目压缩了几条指令，用于跟踪写回计数 -->
* <code>**needRobFlags**</code>: whether each instruction needs a private ROB entry. A compressed instruction has <code>**needRobFlag=false**</code> and shares the preceding entry.
* <code>**instrSizes**</code>: the number of instructions compressed into each entry, used to count writebacks.

<!--
```scala
// CompressUnit.scala
val out = new Bundle {
  val needRobFlags = Vec(RenameWidth, Output(Bool()))  // 是否需要独立 ROB 项
  val instrSizes   = Vec(RenameWidth, Output(UInt(...))) // 压缩的指令数
  val masks        = Vec(RenameWidth, Output(UInt(RenameWidth.W))) // 压缩掩码
}
```
-->
```scala
// CompressUnit.scala
val out = new Bundle {
  val needRobFlags = Vec(RenameWidth, Output(Bool()))
  val instrSizes   = Vec(RenameWidth, Output(UInt(...)))
  val masks        = Vec(RenameWidth, Output(UInt(RenameWidth.W)))
}
```

<!-- ### 4.7.3 压缩指令的 ROB 索引分配 -->
### 4.7.3 ROB Index Allocation for Compressed Instructions

<!-- 被压缩的指令共享前一条指令的 ROB 索引，<code>**numWB**</code> 设为压缩组内需写回的 uop 数： -->
Compressed instructions share the previous instruction's ROB index, and <code>**numWB**</code> is the number of uops in the group that must write back:

<!--
```scala
// Rename.scala：ROB 索引分配——只给 needRobFlag=true 的指令分配新 ROB 项
uops(i).robIdx := robIdxHead + PopCount(
  io.in.zip(needRobFlags).zip(io.validVec).take(i)
    .map{ case((in, needRobFlag), valid) => valid && in.bits.lastUop && needRobFlag}
)

//被压缩的指令（needRobFlag=false）共享前一条指令的 ROB 索引
if (i > 0) {
  when(!needRobFlags(i - 1)) {
    uops(i).firstUop := false.B
    uops(i).ftqPtr   := uops(i - 1).ftqPtr
    uops(i).ftqOffset := uops(i - 1).ftqOffset
    // numWB 设为压缩组内实际需要写回的 uop 数
    uops(i).numWB := instrSizesVec(i) - PopCount(compressMasksVec(i) & (Cat(isMove.reverse) | Cat(fusionValidVec.reverse)))
  }
}
when(!needRobFlags(i)) {
  uops(i).lastUop := false.B  // 被压缩的指令不标记为 lastUop
}
```
-->
```scala
uops(i).robIdx := robIdxHead + PopCount(
  io.in.zip(needRobFlags).zip(io.validVec).take(i)
    .map{ case((in, needRobFlag), valid) => valid && in.bits.lastUop && needRobFlag}
)

if (i > 0) {
  when(!needRobFlags(i - 1)) {
    uops(i).firstUop := false.B
    uops(i).ftqPtr   := uops(i - 1).ftqPtr
    uops(i).ftqOffset := uops(i - 1).ftqOffset
    uops(i).numWB := instrSizesVec(i) - PopCount(compressMasksVec(i) & (Cat(isMove.reverse) | Cat(fusionValidVec.reverse)))
  }
}
when(!needRobFlags(i)) {
  uops(i).lastUop := false.B
}
```

<!--
### 4.7.4 压缩机制示例

```plain
// 3 条可压缩的简单整数指令
add  x10, x1, x2    // canRobCompress=true
add  x11, x3, x4    // canRobCompress=true
add  x12, x5, x6    // canRobCompress=true

// 普通模式：3 条指令分配 3 个 ROB 条目
// 压缩模式：
//   - needRobFlags = [true, false, false]
//   - 只有第 1 条分配新 ROB 条目，后 2 条共享
//   - instrSizes = [3, 0, 0]（ROB 条目内包含 3 条指令）
//   - numWB = 3（需 3 次写回才算完成）
//   - ROB 占用从 3 项压缩为 1 项
```

> *\*\*与原文的关键差异：原文称 ROB 压缩是"连续无冲突写同一架构寄存器时触发"——这是错误的。压缩条件与是否写同一寄存器无关，而是由 \*\**<code>_**canRobCompress**_</code>*\*\* 位和异常状态决定。向量指令拆分出的多个 uop（LMUL>1）是典型的压缩场景——8 个 uop 共享 1 个 ROB 项。\*\**

## 4.8 多指令并行重命名实现原理

香山处理器支持每周期 <code>**RenameWidth**</code> 条指令同时重命名。核心实现：多端口映射表、多通路 FreeList 分配、并行 Bypass 转发。

**多端口 FreeList 分配**

5 套 FreeList 各自独立提供 <code>**RenameWidth**</code> 个分配端口，每条指令按需请求：

```scala
// Rename.scala：5 套 FreeList 的并行分配请求
fpFreeList.io.allocateReq(i)  := needFpDest(i)    // 浮点
vecFreeList.io.allocateReq(i) := needVecDest(i)    // 向量
v0FreeList.io.allocateReq(i)  := needV0Dest(i)     // v0 掩码
vlFreeList.io.allocateReq(i)  := needVlDest(i)     // vl 长度
intFreeList.io.allocateReq(i) := needIntDest(i) && !isMove(i)  // 整数（Move 指令不分配）
```

注意整数 FreeList 的特殊处理：**Move 指令不分配新的物理寄存器**（<code>**!isMove(i)**</code>），而是直接复用源操作数的物理寄存器——这就是 Move 消除优化。

**并行 Bypass：处理同周期内写后读**

同一周期内，前一条指令写某个架构寄存器，后一条指令读同一个架构寄存器时，RAT 还未更新，后一条指令需要通过 Bypass 获取新的物理寄存器编号，而非读旧映射。

**全部就绪才允许输出**

5 套 FreeList 全部可分配 + Dispatch 可接收时，重命名才能输出：

```scala
// Rename.scala
val canOut = dispatchCanAcc && fpFreeList.io.canAllocate && intFreeList.io.canAllocate
  && vecFreeList.io.canAllocate && v0FreeList.io.canAllocate && vlFreeList.io.canAllocate
  && !io.rabCommits.isWalk
```
-->

### 4.7.4 ROB Compression Example

Three simple integer instructions can be compressed when `canRobCompress=true`:

```plain
add  x10, x1, x2    // canRobCompress=true
add  x11, x3, x4    // canRobCompress=true
add  x12, x5, x6    // canRobCompress=true

Normal mode: three instructions allocate three ROB entries.
Compressed mode:
  - needRobFlags = [true, false, false]
  - only the first instruction allocates a new entry; the other two share it
  - instrSizes = [3, 0, 0]
  - numWB = 3; three writebacks are required for completion
  - ROB occupancy falls from three entries to one
```

The compression condition is `canRobCompress` plus the exception/Debug checks; it is unrelated to whether consecutive instructions write the same architectural register. A vector instruction split into multiple uops (LMUL > 1) is a typical compression case.

## 4.8 Parallel Multi-Instruction Renaming

XiangShan renames `RenameWidth` instructions per cycle using multi-port map tables, parallel FreeList allocation, and same-cycle Bypass forwarding.

Each of the five FreeLists provides `RenameWidth` allocation ports:

```scala
fpFreeList.io.allocateReq(i)  := needFpDest(i)
vecFreeList.io.allocateReq(i) := needVecDest(i)
v0FreeList.io.allocateReq(i)  := needV0Dest(i)
vlFreeList.io.allocateReq(i)  := needVlDest(i)
intFreeList.io.allocateReq(i) := needIntDest(i) && !isMove(i)
```

Integer Move instructions do not allocate a new physical register; they reuse the source physical register, which is the Move Elimination optimization.

**Parallel Bypass**: If an earlier instruction in the same cycle writes an architectural register and a later instruction reads it, the RAT still contains the old mapping. Bypass supplies the new physical-register number without waiting for the earlier instruction to write the PRF.

**All resources must be ready before output**:

```scala
val canOut = dispatchCanAcc && fpFreeList.io.canAllocate && intFreeList.io.canAllocate
  && vecFreeList.io.canAllocate && v0FreeList.io.canAllocate && vlFreeList.io.canAllocate
  && !io.rabCommits.isWalk
```

<!--
## 4.9 重命名带宽瓶颈分析

重命名模块的核心带宽瓶颈集中在三大场景：

| **瓶颈** | **根因** | **香山缓解措施** |
| --- | --- | --- |
| PR 分配带宽 | 多指令同时写寄存器，FreeList 端口不足 | 5 套独立 FreeList 并行分配 |
| 映射表读写端口 | 高频读写 RAT，端口数随 RenameWidth 二次增长 | RAT 读写端口分离，读端口每指令每源操作数一个 |
| 快照保存 | 密集分支场景下快照创建占用带宽 | <code>**SnapshotGenerator**</code><br/> 复用机制，限制最大快照数 |

其中 FreeList 的分配阻塞是最直接的瓶颈——<code>**canAllocate**</code> 要求空闲寄存器数 ≥ RenameWidth：

```scala
// StdFreeList.scala
io.canAllocate := GatedValidRegNext(freeRegCnt >= RenameWidth.U)
```

当 FreeList 空间不足时，整条重命名流水线停顿，且因为 5 套 FreeList 耦合（任意一套不足即全停），停顿概率被放大。

## 4.10 Decode 与 Rename 宽度匹配

前端 Decode 译码宽度与后端 Rename 重命名宽度无需完全一致，流水线通过握手协议自然解耦。

香山的 Decode 和 Rename 之间**没有独立的异步缓冲队列**，而是通过 <code>**DecoupledIO**</code> 握手协议直接连接。当 Rename 无法接收新指令时（<code>**io.out.ready = false**</code>），Decode 停顿等待；当 Decode 无有效指令时，Rename 空转。宽度差异通过以下机制吸收：

* **CompressUnit**：将译码输出中的无效指令（被融合标记为无效的指令）压缩掉，使有效指令紧凑排列
* **背压停顿**：Rename 满时直接停住 Decode，不丢失指令
* **ROB 入队带宽**：Rename 的出队宽度受 ROB 入队端口数约束

```scala
// Rename.scala：背压逻辑——canOut 为 false 时所有输入停顿
io.in(i).ready := !io.in(0).valid || canOut
```

> ***与原文的关键差异：原文称香山采用"异步缓冲队列（Decode-Rename Buffer）"解耦 Decode 和 Rename，实际上香山并没有独立的 Decode-Rename Buffer 模块，而是依靠标准流水线握手 + CompressUnit 压缩来处理宽度不匹配。***

## 4.11 VL 向量长度寄存器重命名机制

VCSR.vl 是 RISC-V 向量架构的特殊状态寄存器，记录当前向量指令的有效长度。**香山对 vl 采用的是与通用寄存器相同的物理寄存器重命名机制，而非独立的状态快照管理。**

```plain
// Rename.scala：vl 使用与通用寄存器完全相同的 StdFreeListval
vlFreeList = Module(new StdFreeList(VlPhyRegs - VlLogicRegs, VlLogicRegs, Reg_Vl, RabCommitWidth, 1))
```

vl 拥有独立的 FreeList（<code>**vlFreeList**</code>）和 RAT（<code>**vlRAT**</code>），重命名流程与整数/浮点寄存器完全一致：

:::warning
**<font style="background-color:rgb(255,245,235);">新手建议</font>**<font style="background-color:rgb(255,245,235);">：初学者重点掌握通用寄存器重命名、三种架构差异、Freelist与快照核心逻辑，VCSR.vl特殊重命名、带宽瓶颈、缓冲队列设计属于工程进阶内容，可后续结合源码深挖。</font>

:::

## 4.12 分级学习路径指引

🟢 **入门必掌握**：伪相关概念、WAW/WAR消除原理、统一PRF重命名流程、Freelist基础分配回收逻辑

🔵 **进阶需理解**：映射表读写与Bypass、Redirect恢复、Snapshot快照机制、ROB压缩原理

🟣 **精通深挖**：多指令并行重命名、带宽瓶颈优化、宽窄模块缓冲队列设计、VCSR.vl特殊重命名、源码状态机实现

## 4.13 本章总结

✅ **核心知识点回顾**：

* 寄存器重命名核心价值：**消除WAW、WAR伪数据相关**，释放乱序执行能力，RAW真相关无法消除
* 三种重命名架构中，香山采用**统一PRF重命名**，适配高性能超乱序流水线
* 映射表、Freelist是重命名核心组件，负责映射管理与物理寄存器资源调度
* Redirect、Snapshot、ROB压缩三大机制，分别解决快速冲刷、精准回退、性能优化问题
* 通过多通路并行设计实现多指令每周期重命名，缓冲队列解决上下游带宽不匹配问题
* VCSR.vl为特殊状态寄存器，采用**快照式状态管理**，无物理寄存器分配逻辑


> 更新: 2026-06-22 11:06:43
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/elk8qkx27stlorvf>
-->

## 4.9 Rename Bandwidth Bottlenecks

Rename bandwidth is constrained by three main areas:

| **Bottleneck** | **Root cause** | **XiangShan mitigation** |
| --- | --- | --- |
| PR allocation bandwidth | Several instructions write registers at once and FreeList ports are finite | Five independent FreeLists allocate in parallel |
| Map-table read/write ports | Frequent RAT access grows rapidly with `RenameWidth` | Separate read and write ports; one read port per source operand |
| Snapshot saving | Dense branches consume snapshot-creation bandwidth | `SnapshotGenerator` reuse with a bounded snapshot count |

FreeList allocation is the most direct source of stalls because `canAllocate` requires at least `RenameWidth` free registers:

```scala
io.canAllocate := GatedValidRegNext(freeRegCnt >= RenameWidth.U)
```

When any FreeList lacks capacity, rename stalls. Because the five FreeLists are coupled, exhaustion of one domain blocks the full rename pipeline.

## 4.10 Decode and Rename Width Matching

Decode width and Rename width need not be identical. The pipeline is naturally decoupled through handshaking.

XiangShan does not have a separate asynchronous Decode-Rename buffer. It directly uses `DecoupledIO`: when Rename cannot accept input (`io.out.ready = false`), Decode stalls; when Decode has no valid instruction, Rename idles. Width differences are absorbed by:

* **CompressUnit**: removes invalid/fused decode outputs so valid instructions pack together.
* **Backpressure**: Rename directly stalls Decode without losing instructions.
* **ROB enqueue bandwidth**: Rename output is limited by ROB enqueue ports.

```scala
io.in(i).ready := !io.in(0).valid || canOut
```

The source distinction is important: XiangShan uses normal pipeline handshaking plus CompressUnit compression, not an independent Decode-Rename Buffer module.

## 4.11 VL Register Renaming

`VCSR.vl` records the active vector length. XiangShan renames `vl` through the same physical-register mechanism as ordinary register classes rather than through separate snapshot-only state management.

```scala
val vlFreeList = Module(new StdFreeList(
  VlPhyRegs - VlLogicRegs, VlLogicRegs, Reg_Vl, RabCommitWidth, 1))
```

`vl` has its own `vlFreeList` and `vlRAT`, and its allocation, mapping, commit, and recovery behavior matches the other register domains.

:::warning
**Beginner's note**: First learn ordinary register renaming, the three architectural schemes, FreeList allocation/release, and snapshot recovery. VL renaming, width bottlenecks, and buffering details are advanced topics best studied alongside the source code.

:::

## 4.12 Suggested Learning Path

* **Foundation**: false dependencies, WAW/WAR elimination, unified-PRF rename flow, and basic FreeList allocation/reclamation.
* **Intermediate**: map-table read/write and bypass, Redirect recovery, snapshots, and ROB compression.
* **Advanced**: parallel multi-instruction renaming, bandwidth optimization, width-adaptation design, VL-specific renaming, and source-level state machines.

## 4.13 Chapter Summary

* Register renaming removes WAW and WAR false dependencies; RAW is a true program dependency and cannot be removed.
* Of the three renaming schemes, XiangShan uses unified-PRF renaming for a high-performance out-of-order pipeline.
* Mapping tables and FreeLists manage mappings and physical-register resources.
* Redirect, Snapshot, and ROB compression respectively support fast flushing, precise recovery, and performance optimization.
* Parallel datapaths enable multiple instructions to be renamed each cycle; handshaking and compression accommodate upstream/downstream bandwidth mismatch.
* `VCSR.vl` is a special vector state register with its own physical-register rename domain.

> Updated: 2026-06-22 11:06:43
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/elk8qkx27stlorvf>
