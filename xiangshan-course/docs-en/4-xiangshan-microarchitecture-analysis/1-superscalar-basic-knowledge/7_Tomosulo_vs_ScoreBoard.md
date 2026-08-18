<!--
# 7. Tomosulo vs. ScoreBoard

## 2[附件: 超标量处理器核心调度机制：Scoreboard vs. Tomasulo.pptx](./attachments/TmgZkHVV_tGSVWim/超标量处理器核心调度机制：Scoreboard vs. Tomasulo.pptx)
## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">💡</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">学习目标</font>**
+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">理解动态指令调度的核心思想和解决的问题</font>
+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">掌握 ScoreBoard 算法的工作原理和局限性</font>
+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">掌握 Tomasulo 算法的核心机制和创新点</font>
+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">清晰对比两种算法的关键差异和适用场景</font>
+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">了解香山 RISC-V 处理器中 Tomasulo 算法的实现与优化</font>

---

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">1. 为什么我们需要动态调度？</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">上一章我们学习了顺序与乱序，它可以在一个时钟周期内发射多条指令。但在实际程序中，指令之间往往存在各种依赖关系，这些依赖会导致流水线停顿，严重影响多发射处理器的性能。</font>

**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">数据冒险的三种类型</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：</font>

+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">RAW（写后读）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：后一条指令需要读取前一条指令还未写入的结果</font>
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">WAR（读后写）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：后一条指令会写入前一条指令需要读取的寄存器</font>
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">WAW（写后写）</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：两条指令写入同一个寄存器，顺序错误会导致结果错误</font>

**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">静态调度 vs. 动态调度</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：</font>

+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">静态调度</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：由编译器在编译时重新排列指令顺序，避免冒险</font>
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">动态调度</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：由硬件在运行时动态检查指令依赖，重新安排指令执行顺序</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">✅</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">关键结论</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：动态调度能够挖掘出编译器无法发现的指令级并行性，是现代高性能乱序执行处理器的核心技术。ScoreBoard 和 Tomasulo 是两种最经典的动态调度算法。</font>

---

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">2. ScoreBoard 算法详解</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">ScoreBoard（记分板）算法是最早的动态调度算法，由 CDC 6600 计算机在 1964 年首次实现。它的核心思想是用一个集中式的 "记分板" 来跟踪所有指令的状态和资源使用情况。</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">2.1 ScoreBoard 的核心组件</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">ScoreBoard 算法由四个主要部分组成：</font>

| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">组件</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">功能</font>** |
| :--- | :--- |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令状态表</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">跟踪每条指令的执行阶段（发射、读操作数、执行、写回）</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">寄存器结果状态表</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">记录哪个功能单元将写入哪个寄存器</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">功能单元状态表</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">跟踪每个功能单元的忙闲状态和操作数准备情况</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">控制逻辑</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">根据三个表的信息，决定指令何时可以进入下一个阶段</font> |


### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">2.2 ScoreBoard 的四个执行阶段</font>
1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">发射 (Issue)</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">检查结构冒险：所需功能单元是否空闲</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">检查 WAW 冒险：没有其他指令正在写入同一个目标寄存器</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">如果都满足，将指令发射到对应的功能单元</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">读操作数 (Read Operands)</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">检查 RAW 冒险：所有源操作数都已准备好</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">如果满足，从寄存器堆中读取操作数到功能单元</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">执行 (Execution)</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">功能单元执行运算</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">运算完成后，通知记分板</font>
4. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">写回 (Write Result)</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">检查 WAR 冒险：没有其他指令正在读取目标寄存器</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">如果满足，将结果写入寄存器堆</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">2.3 ScoreBoard 的工作示例</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">我们来看一个简单的指令序列在 ScoreBoard 中的执行过程：</font>

```plain
DIV.D F0, F2, F4   # F0 = F2 / F4
ADD.D F6, F0, F8   # F6 = F0 + F8 (RAW依赖于DIV.D)
SUB.D F8, F10, F12 # F8 = F10 - F12 (WAR依赖于ADD.D)
```

**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">关键观察</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：</font>

+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">ADD.D 必须等待 DIV.D 执行完成才能读取 F0（RAW 冒险）</font>
+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">SUB.D 必须等待 ADD.D 读取 F8 之后才能写入 F8（WAR 冒险）</font>
+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">ScoreBoard 能够正确处理这些依赖，避免错误</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">2.4 ScoreBoard 的优缺点</font>
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">优点</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">缺点</font>** |
| :--- | :--- |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">实现相对简单</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">无法有效解决 WAR 和 WAW 冒险</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">硬件开销较小</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">集中式控制成为性能瓶颈</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">能够挖掘一定的指令级并行性</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">功能单元之间没有直接通信，必须通过寄存器堆</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">不需要编译器支持</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">发射宽度有限，难以扩展到宽发射处理器</font> |


---

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">3. Tomasulo 算法详解</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Tomasulo 算法由 IBM 在 1967 年为 System/360 Model 91 计算机设计，它解决了 ScoreBoard 算法的主要缺陷，成为现代所有乱序执行处理器的基础。</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">3.1 Tomasulo 的核心创新</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Tomasulo 算法最关键的创新是</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">寄存器重命名 (Register Renaming)</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：</font>

+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">用一组物理寄存器替换架构寄存器</font>
+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">每条指令的目标寄存器都被重命名为一个新的物理寄存器</font>
+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">彻底消除了 WAR 和 WAW 冒险（因为不再有同名寄存器的冲突）</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">3.2 Tomasulo 的核心组件</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Tomasulo 算法引入了几个新的关键组件：</font>

| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">组件</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">功能</font>** |
| :--- | :--- |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">保留站 (Reservation Station, RS)</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">每个功能单元前都有一组保留站，缓存等待执行的指令和操作数</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">寄存器重命名表 (Register Alias Table, RAT)</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">记录每个架构寄存器对应的物理寄存器编号</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">物理寄存器堆 (Physical Register File, PRF)</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">存储所有物理寄存器的值</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">公共数据总线 (Common Data Bus, CDB)</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">所有功能单元将结果广播到 CDB，等待该结果的保留站和寄存器堆同时接收</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">重排序缓冲区 (Reorder Buffer, ROB)</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">确保指令按程序顺序提交，支持精确异常</font> |


### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">3.3 Tomasulo 的四个执行阶段</font>
1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">发射 (Issue)</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">检查结构冒险：有空闲的保留站和物理寄存器</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">进行寄存器重命名：将目标架构寄存器映射到一个新的物理寄存器</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">将指令和操作数（或操作数来源）发送到对应的保留站</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">执行 (Execute)</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">当所有操作数都准备好后，功能单元开始执行运算</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">操作数可以来自寄存器堆，也可以来自 CDB</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">写结果 (Write Result)</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">将运算结果和目标物理寄存器编号广播到 CDB</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">所有等待该结果的保留站和物理寄存器同时接收结果</font>
4. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">提交 (Commit)</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">当指令到达 ROB 头部且结果已准备好时，将结果从物理寄存器提交到架构状态</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">如果是分支指令，更新程序计数器；如果是存储指令，写入数据缓存</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">3.4 Tomasulo 的工作示例</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">我们还是用刚才的指令序列，看看 Tomasulo 如何处理：</font>

```plain
DIV.D F0, F2, F4   # 重命名F0为P1
ADD.D F6, F0, F8   # 重命名F6为P2，源操作数F0指向P1
SUB.D F8, F10, F12 # 重命名F8为P3
```

**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">关键观察</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：</font>

+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">ADD.D 仍然需要等待 DIV.D 的结果（RAW 冒险无法消除）</font>
+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">SUB.D 不需要等待 ADD.D 读取 F8，因为它写入的是新的物理寄存器 P3（WAR 冒险被消除）</font>
+ <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">两条指令可以并行执行，性能比 ScoreBoard 提升显著</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">3.5 Tomasulo 的优缺点</font>
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">优点</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">缺点</font>** |
| :--- | :--- |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">彻底消除了 WAR 和 WAW 冒险</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">硬件复杂度高，设计和验证难度大</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">分布式控制，可扩展性好</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">硬件开销大（需要大量保留站和物理寄存器）</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">能够挖掘更多的指令级并行性</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">CDB 成为潜在的性能瓶颈</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">支持乱序执行和精确异常</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">需要复杂的控制逻辑</font> |


---

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">4. ScoreBoard vs. Tomasulo 全面对比</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">这是本章最核心的内容，我们从多个维度对两种算法进行全面对比：</font>

| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">对比维度</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">ScoreBoard 算法</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Tomasulo 算法</font>** |
| :--- | :--- | :--- |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">核心思想</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">集中式跟踪指令状态和资源</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">分布式保留站 + 寄存器重命名</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">寄存器重命名</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">不支持</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">核心特性</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">冒险处理</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">只能部分解决 RAW 冒险</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">彻底解决 RAW、WAR、WAW 冒险</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">控制方式</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">集中式控制</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">分布式控制</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">操作数存储</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">只能存储在寄存器堆</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">可以存储在保留站和物理寄存器堆</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">结果传播</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">必须通过寄存器堆</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">通过 CDB 广播到所有需要的地方</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">硬件复杂度</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">低</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">高</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">硬件开销</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">小</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">大</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">可扩展性</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">差（难以超过 2 发射）</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">好（可以扩展到 4-8 发射）</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">ILP 挖掘能力</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">有限</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">充分</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">精确异常支持</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">不支持</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">支持（通过 ROB）</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">出现时间</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">1964 年（CDC 6600）</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">1967 年（IBM System/360 Model 91）</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">现代应用</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">几乎不再使用</font> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">所有现代高性能处理器的基础</font> |


<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">✅</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">关键结论</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：Tomasulo 算法通过寄存器重命名和分布式保留站，解决了 ScoreBoard 算法的所有主要缺陷。虽然硬件复杂度更高，但它能够挖掘出更多的指令级并行性，因此成为了现代乱序执行处理器的标准架构。</font>

---

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5. 香山 RISC-V 中的 Tomasulo 实现</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">香山南湖架构采用了基于 Tomasulo 算法的乱序执行核心，并针对 RISC-V 指令集进行了大量优化。</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.1 南湖架构的 Tomasulo 核心参数</font>
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">组件</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">参数</font>** |
| :--- | :--- |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">发射宽度</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">4 条指令 / 周期</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">重排序缓冲区 (ROB) 大小</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">256 项</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">物理寄存器数量</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">192 个（整数）+ 192 个（浮点）</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">整数保留站数量</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">32 项</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">浮点保留站数量</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">24 项</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">加载队列大小</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">64 项</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">存储队列大小</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">64 项</font> |
| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">CDB 数量</font>** | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">4 条</font> |


### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.2 香山 Tomasulo 实现的设计亮点</font>
1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">分层式保留站设计</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">整数和浮点指令使用独立的保留站</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">不同类型的执行单元有专门的保留站入口</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">提高了保留站的利用率和指令分发效率</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">多 CDB 架构</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">4 条独立的公共数据总线</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">每个周期最多可以广播 4 个结果</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">解决了单 CDB 的性能瓶颈问题</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">物理寄存器堆优化</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">整数和浮点物理寄存器堆分离</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">采用读写端口分区设计，降低了硬件复杂度</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">支持寄存器重命名的快速回收机制</font>
4. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">精确异常处理</font>**
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">通过 ROB 确保指令按程序顺序提交</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">异常发生时，可以精确回滚到异常指令之前的状态</font>
    - <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">支持中断和异常的快速处理</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.3 代码示例：香山寄存器重命名逻辑</font>
```scala
// 香山RISC-V寄存器重命名逻辑（简化版）
class Rename(implicit p: Parameters) extends XiangShanModule {
  val io = IO(new Bundle {
    val in = Vec(RenameWidth, Flipped(Decoupled(new DecodeInst)))
    val out = Vec(RenameWidth, Decoupled(new RenameInst))
    val robAlloc = Vec(RenameWidth, Output(new RobAllocInfo))
    val commit = Input(new CommitInfo)
  })

  // 寄存器重命名表
  val rat = RegInit(VecInit(Seq.fill(32)(0.U(PhyRegIdxWidth.W))))

  // 空闲物理寄存器列表
  val freeList = Module(new FreeList(PhyRegsNum, 32))

  when (io.in(0).fire) {
    // 为第一条指令分配物理寄存器
    val pd = freeList.io.allocate(0).bits
    val ps1 = rat(io.in(0).bits.rs1)
    val ps2 = rat(io.in(0).bits.rs2)

    // 更新重命名表
    rat(io.in(0).bits.rd) := pd

    // 输出重命名后的指令
    io.out(0).bits.pd := pd
    io.out(0).bits.ps1 := ps1
    io.out(0).bits.ps2 := ps2
  }

  // 提交时回收物理寄存器
  when (io.commit.valid) {
    freeList.io.free(0).valid := true
    freeList.io.free(0).bits := io.commit.oldPd
  }
}
```

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">🔗</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">查看完整代码</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：</font>[<font style="color:rgb(0, 102, 255);background-color:rgba(0, 0, 0, 0);">GitHub - XiangShan 重命名模块</font>](https://github.com/OpenXiangShan/XiangShan/tree/master/src/main/scala/xiangshan/core/rename)

---

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">6. 进阶思考与拓展</font>
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">思考题</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">🤔</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 既然 Tomasulo 算法比 ScoreBoard 算法好这么多，为什么还有人研究 ScoreBoard 算法？在什么情况下 ScoreBoard 可能是更好的选择？</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">拓展阅读</font>
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Tomasulo 算法的演进</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：从原始 Tomasulo 到现代的 ROB-based 乱序执行</font>
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">寄存器重命名的实现方式</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：对比基于映射表和基于交换的重命名</font>
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">乱序执行的极限</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：为什么现代处理器的 ROB 大小很少超过 512 项？</font>
+ **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">SMT 与乱序执行</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：超线程技术如何与 Tomasulo 算法结合</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">📚</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">相关链接</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：</font>

+ [<font style="color:rgb(0, 102, 255);background-color:rgba(0, 0, 0, 0);">乱序执行深度解析</font>](https://blog.csdn.net/gjq_1988/article/details/39520729?ops_request_misc=elastic_search_misc&request_id=a437da1034bdc2bb6d69558affbc8b92&biz_id=0&utm_medium=distribute.pc_search_result.none-task-blog-2~all~top_positive~default-1-39520729-null-null.142^v102^pc_search_result_base7&utm_term=%E4%B9%B1%E5%BA%8F%E6%89%A7%E8%A1%8C&spm=1018.2226.3001.4187)
+ [<font style="color:rgb(0, 102, 255);background-color:rgba(0, 0, 0, 0);">寄存器重命名技术原理解析</font>](register-renaming.html)
+ [<font style="color:rgb(0, 102, 255);background-color:rgba(0, 0, 0, 0);">香山 RISC-V 微架构文档</font>](https://docs.xiangshan.cc/zh-cn/latest/integration/overview/#microarchitecture)

---

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">7. 本章总结</font>
1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">动态调度</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">是解决指令依赖、挖掘指令级并行性的关键技术，ScoreBoard 和 Tomasulo 是两种最经典的动态调度算法。</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">ScoreBoard 算法</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">通过集中式的记分板跟踪指令状态和资源使用情况，实现了基本的动态调度，但无法有效解决 WAR 和 WAW 冒险，可扩展性差。</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Tomasulo 算法</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">通过</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">寄存器重命名</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">彻底消除了 WAR 和 WAW 冒险，通过</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">分布式保留站</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">和</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">公共数据总线</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">实现了高效的指令调度，成为现代所有高性能乱序执行处理器的基础。</font>
4. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">关键差异</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：Tomasulo 算法在冒险处理能力、可扩展性和 ILP 挖掘能力上都显著优于 ScoreBoard 算法，但硬件复杂度和开销也更高。</font>
5. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">香山实践</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：南湖架构采用了基于 Tomasulo 算法的乱序执行核心，通过分层式保留站、多 CDB 架构和优化的物理寄存器堆，实现了高性能和低功耗的平衡。</font>



> 更新: 2026-06-03 15:47:05  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/ls3en2b6g41pqp2e>
-->

# 7. Tomasulo vs. Scoreboard

[Attachment: Core scheduling mechanisms in superscalar processors: Scoreboard vs. Tomasulo](./attachments/TmgZkHVV_tGSVWim/超标量处理器核心调度机制：Scoreboard vs. Tomasulo.pptx)

## Learning objectives

- Understand why dynamic scheduling is needed in a superscalar pipeline.
- Explain the data structures and four phases of the Scoreboard and Tomasulo algorithms.
- Compare their handling of RAW, WAR, and WAW hazards, scalability, and hardware cost.
- Relate Tomasulo-style scheduling, register renaming, and precise exceptions to the XiangShan/Nanhu implementation.

## 1. Why dynamic scheduling?

In an in-order pipeline, one long-latency instruction can block younger independent instructions. Dynamic scheduling lets hardware select a ready instruction when its operands and functional unit are available, exposing instruction-level parallelism (ILP) at run time. The scheduler must preserve architectural correctness and, in a modern core, commit results in program order.

## 2. Scoreboard algorithm

The Scoreboard, introduced with the CDC 6600, is a centralized table that tracks functional-unit occupancy, operand readiness, and instruction state. It has four conceptual phases:

1. **Issue**: Decode an instruction and reserve a free functional unit. Issue is held if the unit is busy or if issuing would create a structural conflict.
2. **Read operands**: Wait until all source operands are available, then read them from the register file. The scoreboard delays reads to avoid RAW hazards.
3. **Execute**: Run the operation in the reserved unit. Multiple independent units may execute concurrently.
4. **Write result**: Write the result to the register file when the unit finishes, while checking for WAR hazards so an older reader is not bypassed.

A simplified example is:

```plain
DIV.D F0, F2, F4
ADD.D F6, F0, F8
SUB.D F8, F10, F12
```

`ADD.D` waits for the divide because it has a true RAW dependence on `F0`. `SUB.D` writes `F8`, which an older instruction may still need to read, so the scoreboard can also delay its write for a WAR hazard. Since architectural registers are not renamed, WAW hazards likewise require centralized ordering checks.

### Strengths and limitations

| Strengths | Limitations |
| --- | --- |
| Simpler hardware and a single centralized control table | Centralized wakeup and hazard checks become a timing bottleneck |
| Can issue independent instructions dynamically | WAR and WAW hazards are not removed; they must be stalled |
| Useful for teaching and small, low-cost designs | Limited scalability beyond a small issue width and unit count |
| No large physical-register structure is required | The register file is the main result-communication path |

## 3. Tomasulo algorithm

Tomasulo's key innovations are **register renaming**, **distributed reservation stations**, and a **common data bus (CDB)**. A destination is associated with a tag (or physical register) rather than only an architectural register name. Consumers wait for the tag, so WAR and WAW name dependences disappear while RAW true dependences remain.

### 3.1 Core components

- **Reservation stations** buffer instructions near their functional units and hold either a ready operand or the tag of its producer.
- **Register alias table / physical register file** maps architectural destinations to physical registers (modern implementations use a PRF and a ROB).
- **CDB or result network** broadcasts a completed value and its tag to every waiting station.
- **Reorder buffer (ROB)** records program order and commits results in order, providing precise exceptions.

### 3.2 Four execution phases

1. **Issue/rename**: Allocate a reservation-station entry and a destination tag; capture ready operands and record tags for pending operands.
2. **Execute**: When all operands are ready and the functional unit is available, execute the operation.
3. **Write result**: Broadcast the result and tag on the CDB/result network. All matching stations and the physical register receive it.
4. **Commit**: When the instruction reaches the ROB head and is complete, update architectural state. Branches update the PC and stores update the memory system at the appropriate commit point.

For the same instruction sequence:

```plain
DIV.D F0, F2, F4   # rename F0 to P1
ADD.D F6, F0, F8   # rename F6 to P2; source F0 refers to P1
SUB.D F8, F10, F12 # rename F8 to P3
```

`ADD.D` still waits for `P1` (RAW). `SUB.D` can proceed without waiting for the older read of `F8`, because it writes `P3` (WAR removed). Independent instructions can execute in parallel.

### Strengths and limitations

| Strengths | Limitations |
| --- | --- |
| Register renaming removes WAR and WAW hazards | More state, ports, tags, and verification complexity |
| Distributed scheduling scales better than one central table | Reservation stations and physical registers consume area and power |
| CDB/result forwarding exposes more ILP | A single CDB can become a bandwidth bottleneck; modern cores use several result buses |
| Works naturally with out-of-order execution and precise commit | Requires sophisticated recovery and control logic |

## 4. Scoreboard vs. Tomasulo

| Dimension | Scoreboard | Tomasulo |
| --- | --- | --- |
| Main idea | Centralized instruction/resource tracking | Distributed reservation stations plus renaming |
| Register renaming | Not supported | Core feature |
| Hazard handling | Partial RAW handling; WAR/WAW require stalls | RAW remains; WAR/WAW are removed by renaming |
| Control | Centralized | Distributed wakeup and selection |
| Operand storage | Register file | Reservation stations and physical register file |
| Result propagation | Through the register file | CDB/result network broadcasts to all consumers |
| Hardware complexity | Lower | Higher |
| Scalability | Poor beyond a small issue width | Good; modern designs scale to multiple issue |
| ILP extraction | Limited | Higher |
| Precise exceptions | Not inherent | ROB provides ordered commit |
| Historical example | CDC 6600 (1964) | IBM System/360 Model 91 (1967) |
| Modern use | Mainly educational or specialized | Foundation of high-performance out-of-order CPUs |

**Conclusion:** Tomasulo's extra hardware buys more ILP and removes the major name hazards. That trade-off is why Tomasulo-style scheduling, with a ROB and multiple result paths, underlies most modern high-performance cores.

## 5. Tomasulo in XiangShan's Nanhu core

The Nanhu architecture uses a Tomasulo-like out-of-order backend adapted for RISC-V. The source chapter lists these representative parameters:

| Component | Stated parameter |
| --- | --- |
| Issue width | 4 instructions per cycle |
| ROB | 256 entries |
| Physical registers | 192 integer + 192 floating-point |
| Integer reservation stations | 32 entries |
| Floating-point reservation stations | 24 entries |
| Load queue | 64 entries |
| Store queue | 64 entries |
| CDB/result paths | 4 |

The implementation highlights are:

1. **Hierarchical reservation stations:** Integer and floating-point instructions use separate structures, with entries specialized for different execution units.
2. **Multiple result buses:** Four independent result paths can broadcast up to four completions per cycle, avoiding the single-CDB bottleneck.
3. **Partitioned physical register files:** Integer and floating-point PRFs are separated and their read/write ports are organized to reduce wiring and complexity; old physical registers can be reclaimed quickly after commit.
4. **Precise exceptions:** The ROB commits in program order and can roll back to the state before a faulting instruction, supporting fast interrupts and exceptions.

### Simplified register-renaming example

```scala
// Simplified XiangShan RISC-V register-renaming logic
class Rename(implicit p: Parameters) extends XiangShanModule {
  val io = IO(new Bundle {
    val in = Vec(RenameWidth, Flipped(Decoupled(new DecodeInst)))
    val out = Vec(RenameWidth, Decoupled(new RenameInst))
    val robAlloc = Vec(RenameWidth, Output(new RobAllocInfo))
    val commit = Input(new CommitInfo)
  })

  val rat = RegInit(VecInit(Seq.fill(32)(0.U(PhyRegIdxWidth.W))))
  val freeList = Module(new FreeList(PhyRegsNum, 32))

  when (io.in(0).fire) {
    val pd = freeList.io.allocate(0).bits
    val ps1 = rat(io.in(0).bits.rs1)
    val ps2 = rat(io.in(0).bits.rs2)
    rat(io.in(0).bits.rd) := pd
    io.out(0).bits.pd := pd
    io.out(0).bits.ps1 := ps1
    io.out(0).bits.ps2 := ps2
  }

  when (io.commit.valid) {
    freeList.io.free(0).valid := true
    freeList.io.free(0).bits := io.commit.oldPd
  }
}
```

## 6. Further questions and reading

Why study Scoreboard when Tomasulo is more capable? Scoreboard can still be attractive when a design needs minimal state, predictable centralized control, or a small number of functional units. The trade-off is lower ILP and poorer scalability.

Suggested topics include the evolution from original Tomasulo to ROB-based out-of-order execution, mapping-table versus swap-based renaming, practical ROB-size limits, and the interaction between SMT and dynamic scheduling.

- [A detailed introduction to out-of-order execution](https://blog.csdn.net/gjq_1988/article/details/39520729)
- [Register-renaming principles](register-renaming.html)
- [XiangShan RISC-V microarchitecture documentation](https://docs.xiangshan.cc/zh-cn/latest/integration/overview/#microarchitecture)

## 7. Chapter summary

1. Dynamic scheduling resolves dependences and exposes ILP; Scoreboard and Tomasulo are the two classic algorithms.
2. Scoreboard uses centralized state tracking and provides basic dynamic scheduling, but it does not remove WAR/WAW hazards and scales poorly.
3. Tomasulo uses register renaming, distributed reservation stations, and result broadcasting to remove name hazards and schedule instructions efficiently. With an ROB, it supports precise, in-order commit.
4. Tomasulo is more scalable and extracts more ILP, at the cost of more hardware and control complexity.
5. XiangShan/Nanhu balances the cost with hierarchical reservation stations, multiple result buses, and optimized physical register files.

> Updated: 2026-06-03 15:47:05
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/ls3en2b6g41pqp2e>
