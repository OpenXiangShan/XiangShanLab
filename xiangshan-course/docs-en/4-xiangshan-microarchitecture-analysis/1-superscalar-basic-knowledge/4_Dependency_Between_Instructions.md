<!--
# 4. 指令间的相关性

[附件: 指令间的相关性分析：流水线效率的隐形枷锁.pptx](./attachments/gS6GTRPG5f3hdD5i/指令间的相关性分析：流水线效率的隐形枷锁.pptx)
-->

# 4. Dependencies Between Instructions

[Attachment: Analyzing Dependencies Between Instructions: The Hidden Constraint on Pipeline Efficiency](./attachments/gS6GTRPG5f3hdD5i/指令间的相关性分析：流水线效率的隐形枷锁.pptx)

<!--
 在处理器流水线执行、超标量执行或乱序执行架构中，指令并非完全独立 —— 后续指令可能依赖于前置指令的执行结果，或争夺相同的寄存器 / 存储单元资源，这种依赖关系被称为**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令间的相关性</font>**。其中，数据相关性是最核心的类型，主要表现为读后读（RAR）、写后读（RAW）、读后写（WAR）、写后写（WAW）四种形式，直接影响处理器的执行效率与正确性，也是流水线数据冒险的核心诱因。  
-->

In pipelined, superscalar, or out-of-order processors, instructions are not completely independent. A later instruction may depend on an earlier instruction's result or compete for the same register or memory resource; this relationship is called an **instruction dependence**. Data dependences are the most important type and mainly appear in four forms: read after read (RAR), read after write (RAW), write after read (WAR), and write after write (WAW). They directly affect execution efficiency and correctness and are the primary cause of pipeline data hazards.

<!--
# <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">4.1 读后读（RAR, Read After Read）</font>
## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">4.1.1 定义</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">读后读（RAR）指两条指令依次对同一个寄存器（或存储单元）执行 “读操作”，即第一条指令完成读操作后，第二条指令读取该位置的数据。</font>

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">4.1.2 核心特征与影响</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">RAR 是</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">无冲突的相关性</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：由于两条指令仅读取数据、不修改数据，无论执行顺序如何，读取到的内容始终一致，因此不会引发数据冒险，也不会阻碍处理器的并行 / 流水线执行。</font>

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 4.1.3 示例</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">（以 MIPS 汇编为例）</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">  </font>
```plain
# 指令I1：读取寄存器$t0的值，赋值给$t1
add $t1, $t0, $zero  
# 指令I2：读取寄存器$t0的值，赋值给$t2
add $t2, $t0, $zero  
```

 I1 和 I2 仅读取 $t0 的值，无修改操作，二者可完全并行执行，不存在任何冲突。  
-->

# 4.1 Read After Read (RAR)
## 4.1.1 Definition

Read after read (RAR) means that two instructions successively perform a read operation on the same register (or memory location): after the first instruction completes its read, the second instruction reads the data at that location.

## 4.1.2 Key Characteristics and Impact

RAR is a **conflict-free dependence**. Because both instructions only read data and do not modify it, they obtain the same value regardless of execution order. RAR therefore causes no data hazard and does not prevent parallel or pipelined execution.

## 4.1.3 Example (MIPS assembly)

```plain
# Instruction I1: read $t0 and assign it to $t1
add $t1, $t0, $zero
# Instruction I2: read $t0 and assign it to $t2
add $t2, $t0, $zero
```

I1 and I2 only read `$t0` and do not modify it, so they can execute fully in parallel without any conflict.

<!--
# 4.2 写后读（RAW, Read After Write）  
## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">4.2.1 定义</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">写后读（RAW）指第一条指令对寄存器 / 存储单元执行 “写操作”，第二条指令在其之后读取该位置的数据 —— 第二条指令的读操作依赖于第一条指令的写操作结果，是最典型的</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">真数据相关</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">（True Data Dependency）。</font>

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">4.2.2 核心特征与影响</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">RAW 是流水线 “数据冒险” 的核心诱因：若未做特殊处理，第二条指令可能在第一条指令完成写操作前读取数据，导致读取到旧值（错误数据）。RAW 无法通过调整指令顺序消除（本质是数据依赖），是处理器性能优化的核心解决对象。</font>

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 4.2.3 示例  </font>
```plain
# 指令I1：将立即数10写入寄存器$t0（写操作）
addi $t0, $zero, 10  
# 指令I2：读取$t0的值，与$t1相加后写入$t2（读操作依赖I1的写结果）
add $t2, $t1, $t0  
```

 I2 必须读取 I1 写入的 $t0 值（10），若 I2 提前读取，会获取 $t0 的原始旧值，导致计算错误。  

##  4.2.4 常见解决方式  
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">数据前推（Forwarding/Bypassing）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：将第一条指令的写结果直接传递到第二条指令的读端口，无需等待写操作完成；</font>
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">流水线暂停（Stall）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：临时暂停第二条指令执行，直至第一条指令完成写操作；</font>
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令重排</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：在不影响语义的前提下，插入无关指令填充等待周期，减少性能损耗。</font>
-->

# 4.2 Read After Write (RAW)
## 4.2.1 Definition

Read after write (RAW) means that the first instruction writes a register or memory location and the second instruction subsequently reads it. The second instruction's read depends on the first instruction's write result, making RAW the canonical **true data dependence**.

## 4.2.2 Key Characteristics and Impact

RAW is the main cause of pipeline data hazards. Without special handling, the second instruction may read before the first instruction completes its write and obtain the old value. RAW cannot be eliminated by reordering instructions because it is an inherent data dependence, so it is a central target of processor performance optimization.

## 4.2.3 Example

```plain
# Instruction I1: write the immediate value 10 to $t0
addi $t0, $zero, 10
# Instruction I2: read $t0, add it to $t1, and write the result to $t2
add $t2, $t1, $t0
```

I2 must read the value 10 written to `$t0` by I1. If I2 reads too early, it obtains the original value of `$t0` and computes an incorrect result.

## 4.2.4 Common Solutions

+ **Data forwarding (Forwarding/Bypassing):** Send I1's write result directly to I2's read port without waiting for the write to complete.
+ **Pipeline stall:** Temporarily stop I2 until I1 has completed its write.
+ **Instruction reordering:** Without changing program semantics, insert independent instructions to fill the wait cycles and reduce the performance cost.

<!--
#  4.3 读后写（WAR, Write After Read）  
##  4.3.1 定义  
 读后写（WAR）指第一条指令读取某个寄存器 / 存储单元的数据，第二条指令在其之后对该位置执行写操作 —— 也被称为**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">反相关（Anti-dependency）</font>**，冲突仅在 “指令执行顺序被打乱” 时显现。  

##  4.3.2 核心特征与影响  
 WAR 是**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">伪数据相关</font>**（False Dependency）：按序执行时，第一条指令先读、第二条指令后写，无冲突；但在乱序 / 超标量执行架构中，若写操作指令被调度到读操作指令之前，会导致读操作读取到错误的新值。  

##  4.3.3 示例  
 按序执行无冲突：  

```plain
# 指令I1：读取$t0的值，与$t1相加后写入$t2（读$t0）
add $t2, $t1, $t0  
# 指令I2：将立即数20写入$t0（写$t0）
addi $t0, $zero, 20  
```

 按序执行时，I1 先读取 $t0 旧值，I2 后写入新值，结果符合预期；若乱序执行将 I2 调度到 I1 之前，I1 会读取到 20（错误值）。  

##  4.3.4 常见解决方式  
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">寄存器重命名</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：为写操作分配新的物理寄存器，避免与读操作的逻辑寄存器冲突（逻辑寄存器与物理寄存器分离）；</font>
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">乱序调度优化</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：调度器避免将写操作指令提前到依赖其的读操作指令之前执行。</font>
-->

# 4.3 Write After Read (WAR)
## 4.3.1 Definition

Write after read (WAR) means that the first instruction reads a register or memory location and the second instruction subsequently writes it. It is also called an **anti-dependence**; the hazard appears only when execution order is changed.

## 4.3.2 Key Characteristics and Impact

WAR is a **false data dependence**. In in-order execution, the first instruction reads before the second writes, so there is no conflict. In an out-of-order or superscalar processor, however, scheduling the write before the read can make the read see an incorrect new value.

## 4.3.3 Example

There is no conflict when the instructions execute in order:

```plain
# Instruction I1: read $t0, add it to $t1, and write the result to $t2
add $t2, $t1, $t0
# Instruction I2: write the immediate value 20 to $t0
addi $t0, $zero, 20
```

In order, I1 reads the old `$t0` value before I2 writes the new value, so the result is correct. If out-of-order execution schedules I2 before I1, I1 reads 20, which is incorrect.

## 4.3.4 Common Solutions

+ **Register renaming:** Allocate a new physical register for the write, separating it from the logical register read by the first instruction.
+ **Out-of-order scheduling constraints:** The scheduler prevents a write instruction from executing before a read instruction that depends on the old value.

<!--
#  4.4 写后写（WAW, Write After Write）  
##  4.4.1 定义  
 写后写（WAW）指两条指令依次对同一个寄存器 / 存储单元执行 “写操作”—— 也被称为**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">输出相关（Output Dependency）</font>**，同样是仅在乱序执行场景下显现的伪相关。  

##  4.4.2 核心特征与影响  
 按序执行时，最终寄存器 / 存储单元的值为第二条指令的写结果，符合语义；但在乱序执行中，若第一条指令的写操作延迟、第二条指令的写操作先完成，会导致最终值为第一条指令的结果，违背语义。  

##  4.4.3 示例  
 按序执行符合语义：  

```plain
# 指令I1：将10写入$t0（写操作1）
addi $t0, $zero, 10  
# 指令I2：将20写入$t0（写操作2）
addi $t0, $zero, 20  
```

 按序执行后，$t0 的值为 20（符合预期）；若乱序执行导致 I1 的写操作晚于 I2 完成，最终 $t0 的值为 10（错误结果）。  

##  4.4.4 常见解决方式  
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">寄存器重命名</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：为两条写指令分配不同的物理寄存器，最终通过重排序缓冲区（ROB）保证逻辑寄存器的最终值符合语义；</font>
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">重排序缓冲区（ROB）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：乱序执行架构中，写操作先写入 ROB，待指令按序提交时再写入物理寄存器，避免乱序写导致的结果错误。</font>
-->

# 4.4 Write After Write (WAW)
## 4.4.1 Definition

Write after write (WAW) means that two instructions successively write the same register or memory location. It is also called an **output dependence** and, like WAR, is a false dependence that appears only with out-of-order execution.

## 4.4.2 Key Characteristics and Impact

In in-order execution, the final register or memory value is the second instruction's write result, as required by program semantics. In out-of-order execution, if the first instruction's write is delayed while the second instruction completes first, the final value can instead be the first instruction's result, violating the semantics.

## 4.4.3 Example

In-order execution preserves the intended semantics:

```plain
# Instruction I1: write 10 to $t0 (write 1)
addi $t0, $zero, 10
# Instruction I2: write 20 to $t0 (write 2)
addi $t0, $zero, 20
```

After in-order execution, `$t0` is 20 as expected. If out-of-order execution causes I1's write to complete after I2, the final `$t0` value is 10, which is incorrect.

## 4.4.4 Common Solutions

+ **Register renaming:** Allocate different physical registers for the two writes, then use the reorder buffer (ROB) to ensure that the final architectural-register value is committed according to program order.
+ **Reorder buffer (ROB):** In an out-of-order processor, writes first enter the ROB and are committed to the physical register file when instructions retire in order, preventing an out-of-order write from corrupting the result.

<!--
#  4.5 小结  
 四类数据相关性中，RAW 是真数据相关，直接制约流水线并行效率；RAR 无冲突，不影响执行；WAR 和 WAW 是伪相关，仅在乱序 / 超标量执行中引发冲突。现代处理器通过数据前推、寄存器重命名、重排序缓冲区等技术，在保证执行正确性的前提下，最大化缓解相关性带来的性能损耗。  
-->

# 4.5 Summary

Among the four types of data dependence, RAW is a true data dependence and directly limits pipeline parallelism; RAR is conflict-free and does not affect execution; WAR and WAW are false dependences that cause hazards only in out-of-order or superscalar execution. Modern processors use forwarding, register renaming, reorder buffers, and related techniques to minimize the performance cost of these dependences while preserving execution correctness.

<!--
> 更新: 2026-05-12 11:33:01  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/wvhord4qmv9n611i>
-->

> Updated: 2026-05-12 11:33:01
> Source: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/wvhord4qmv9n611i>
