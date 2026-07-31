# 一条STORE指令的简单分析过程

# 一、前言

通过对一条 ADD 指令的简单分析，最大的感受是：Chisel 代码的可读性对新手（不熟悉 Chisel 或不了解香山代码的同学）而言，存在一定难度。很多写法对创作者来说极大地提高了开发效率和便捷性，但对阅读者来说却可能是一场“灾难”。

> 例如，在 Verilog 中，对某个寄存器的赋值通常只能在一个 always 块内完成，但在 Chisel 中，赋值语句可能分散在代码的多个位置。再加上一些高度抽象或灵活的语法，要完整理解一段代码的逻辑，难度相当大。

因此，整体建议是：在结合波形阅读代码时，最好也能同时对照生成的 Verilog 代码一起查看。很多信号在 Verilog 中能更直观地看出受哪些信号影响，以及在何种条件下会被驱动成什么值。所以，强烈推荐使用 VCS + Verdi 等工具查看波形，并利用其 Trace 功能关联波形与代码。这样，Chisel 源码、生成的 Verilog 代码以及仿真波形三者可以相互对照、相互印证，极大地提升代码理解和问题定位的效率。

# 二、VCS ＋ Verdi等工具的使用方法

暂略

# 三、找到一条合适的store相关的指令

<font style="color:rgb(38, 38, 38);">打开反汇编文件（即压缩包中的 </font><font style="color:rgb(38, 38, 38);background-color:rgba(0, 0, 0, 0.06);">hello-riscv64-xs.txt</font><font style="color:rgb(38, 38, 38);">文件）：</font>

<font style="color:rgb(38, 38, 38);"></font>

此处选择位于程序计数器（pc）地址 `0x80000134`的指令，其原始内容为压缩指令 `0xe406`。经过前端解压后，实际指令为 `0x00113423`。

![1774337920540-c32536b1-31fc-4425-85eb-c7e66d0406d6.png](../../img/simple-analysis-process-of-a-store-instruction/figure-001-store-instruction-select.png)

<font style="color:rgb(38, 38, 38);">单独分析这条指令，对照指令集手册：</font>

![1774338126317-1e223e54-5a15-49b9-8c27-e74d1095a38d.png](../../img/simple-analysis-process-of-a-store-instruction/figure-002-store-instruction-analysis.png)

<font style="color:rgb(38, 38, 38);">（ 本图来源于 </font>[<font style="color:rgb(22, 119, 255);">链接</font>](https://ai-embedded.com/risc-v/riscv-isa-manual/)<font style="color:rgb(38, 38, 38);"> ）</font>

这是一条访存相关的存数（Store）指令，其功能为：

* **目标内存地址**：由 rs1 寄存器（2 号寄存器）的值加上立即数 8 计算得出。
* **待存数据**：来自 rs2 寄存器（1 号寄存器）的值。

经分析，指令执行时：

* 逻辑 2 号寄存器的值应为 `0x80009fe0`。
* 逻辑 1 号寄存器的值应为 `0x80000172`。
* 立即数为 8。

因此，该指令的执行行为是：在内存地址 `0x80009fe0 + 0x8 = 0x80009fe8`处，写入数据 `0x80000172`。

***

**逻辑 1 号寄存器 (**<code>**ra**</code>\*\*) 的值应为 \*\*<code>**0x80000172**</code>

* 这个推导是清晰和正确的。`ra`的值由调用 `main`函数的那条指令设置。
* 调用 `main`的指令位于 `0x8000016e`：`jal ra, 8000012a <main>`。这是一条跳转并链接指令，硬件会将其**下一条指令的地址**存入 `ra`寄存器。

![1774339065865-55d1aa5d-c100-4f3b-80cc-5fcdd95ddf6c.png](../../img/simple-analysis-process-of-a-store-instruction/figure-003-store-instruction-jal.png)

* `0x8000016e`的下一条指令地址是 `0x80000172`。
* 因此，当 CPU 跳转到 `main`函数入口 (`0x8000012a`) 开始执行时，`ra`寄存器的值就是 `0x80000172`。这个值在 `main`函数执行期间保持不变，直到被其他跳转指令修改。
* 所以，在 `main`函数内执行 `sd ra, 8(sp)`(`0x80000134`) 时，要保存的 `ra`值正是 `0x80000172`。

**逻辑 2 号寄存器 (**<code>**sp**</code>\*\*) 的值暂不能轻易确定，**要计算 `sp`在 `main`函数内的值，我们需要知道**进入 \*\*<code>**_trm_init**</code>\*\*时 \*\*<code>**sp**</code>**的初始值**。这个值通常由系统启动代码设置，暂不花费精力往前深究。

***

# 四、波形分析

## （1）译码模块（Decode）

基于前面“一条ADD指令的简单分析”中所积累的基础，这里不再对译码过程和Chisel代码的阅读方法等重复内容进行赘述，相关细节请参考前文。

因此，我们可以直接查看译码模块（Decode）的输出波形。（经过一段迷迷糊糊的查找，最终确认目标指令被注入到了下标为3的那一路译码器（decoder\_3），时刻为16767）：

![1774339860686-48a4c0a2-c4db-4a1b-a8d7-b93ea509ccbb.png](../../img/simple-analysis-process-of-a-store-instruction/figure-004-decode-stage-inspect-waveform.png)

因此，我们直接提取该模块中下标为 3 的译码信号，即与 `io_*_3_*`相关的信号，具体如下：

![1774345192952-c99cc082-20fe-4bd5-b329-afc3a8e9ce63.png](../../img/simple-analysis-process-of-a-store-instruction/figure-005-decode-stage-signal-io.png)

在仿真时间为 16767 ps 的时刻，可以观察到译码产生的信号如上图所示。在 `valid`信号有效的前提下，我们可以先检查译码得到的信号是否符合预期。

首先，这个时刻对应的指令码是 `0x00113423`，这正是我们前面定位到的那条指令。接下来，我们看译码模块对其解析出了哪些信号：

`commitType`为 `0x3`。可以发现，这与前面加法指令的 `commitType`（`0x0`）明显不同。那么这个信号代表什么含义呢？我们可以直接查看 Chisel 代码中的相关描述：

![1774343079698-02fdf73d-b3a8-4113-9f80-6e9f37bfb00c.png](../../img/simple-analysis-process-of-a-store-instruction/figure-006-decode-stage-commit-type.png)

![1774343128355-8fc71d94-d48e-416f-9b8e-8d6bf056e57a.png](../../img/simple-analysis-process-of-a-store-instruction/figure-007-decode-stage-commit-type.png)

通过代码描述基本可以确定：如果一条指令是 store 类指令但非 AMO 指令，`commitType[0]`位会被拉高；如果该指令会使用到 LDU 或 STU（即进行访存操作），`commitType[1]`位也会被拉高。可以非常清晰地判断出，我们所追踪的这条 sd 指令同时满足以上两个条件，因此其 `commitType`将被赋值为 `0x3`。波形中的实际数据完全符合预期。

接下来的其他数据还包括 `fuType`和 `fuOpType`两个信号。结合前面对加法指令的分析，基本可以确定这两个信号代表：这条指令将使用哪个功能单元，以及在该单元上执行的具体操作是什么。对于一条 store 指令，可以确定它将使用 STU 这类访存单元，具体操作是存储一条 64 位的数据。

从波形中可以看到，`fuType`的值为 `0x10000`，其独热编码的第 17 位（下标16）为高。现在来看它实际代表的意义：\
![1774344232024-cdc4d6ab-c8a7-4d76-8ff8-c5ca93553788.png](../../img/simple-analysis-process-of-a-store-instruction/figure-008-decode-stage-waveform-fu.png)

你可以手动数一下，确认 `stu`是否被安排在第 17 个位置。从波形中可以看出，其行为完全符合预期。

对于 `fuOpType`的值为 `0x3`，我们同样在代码中查找它所代表的具体含义：

![1774344482009-dc228c97-0c61-4396-a9cb-be42bc9c375f.png](../../img/simple-analysis-process-of-a-store-instruction/figure-009-decode-stage-fu-op.png)

既然这条指令的 `fuType`是“stu”，那么其 `fuOpType`的属性自然对应 `LSUOpType`。我们需要在其中查找当值为 `0x3`时所代表的意义。从上图的代码中可以清楚地看到，`0x3`表示当前这条存数指令具体是一条“sd”指令，即存储的数据宽度为 64 位。

其余的信号就很好理解了。例如：

* `rfWen`信号为低，表明一条 `sd`指令不会回写寄存器堆，这符合预期。

`lsrc_0`和 `lsrc_1`分别为 2 和 1，对应我们前面分析中提到的 2 号寄存器（`sp`）和 1 号寄存器（`ra`）。两个操作数的 `srcType`均为 1，表明它们都来自寄存器堆。

当然，对于一条sd指令而言，与立即数相关的信号也不能忽略：

* `imm`值为 `0x8`，这很好理解，即立即数数据为 8。
* `selImm`信号值为 `0xe`。那么，`selImm`代表什么意义呢？

观察代码：

![1774345359595-b56c2e06-6044-437f-bd4a-9f9e068a4380.png](../../img/simple-analysis-process-of-a-store-instruction/figure-010-decode-stage-signal-sel.png)

实际上，由于不同类型的指令，其立即数的编码格式也不同，因此需要一个信号来指示当前指令的类型。`selImm`值为 `0xe`，根据代码描述，这明确表示该指令是一条 S 型指令，意味着其立即数应按照 S 型的格式进行解析。这个行为是完全正确的。

译码模块的内容相对简单，核心在于理清每个信号值所对应的具体含义，并不涉及复杂的处理逻辑（此处我们仅关注简单指令，不考虑复杂指令）。因此，对译码模块的描述就到这里。

总结一下，译码模块将向下一级流水线（即重命名模块）传递以下这些主要信号：

好的，根据译码模块的输出波形，传递至重命名模块的主要信号及其值总结如下：

* <code>**instr**</code>：`0x00113423`，对应 `sd ra, 8(sp)`指令。
* <code>**lsrc_0**</code>：`0x02`，源操作数0来自逻辑寄存器2号（`sp`）。
* <code>**srcType_0**</code>：`0x1`，源操作数0的类型为寄存器。
* <code>**lsrc_1**</code>：`0x01`，源操作数1来自逻辑寄存器1号（`ra`）。
* <code>**srcType_1**</code>：`0x1`，源操作数1的类型为寄存器。
* <code>**selImm**</code>：`0xe`，表示该指令为S型，立即数需按S型格式处理。
* <code>**imm**</code>：`0x8`，指令的立即数值为8。
* <code>**fuType**</code>：`0x10000`，功能单元类型，指示将使用存储单元（`stu`）。
* <code>**fuOpType**</code>：`0x3`，功能单元操作类型，表示具体的64位存储操作（`sd`）。
* <code>**rfWen**</code>：`0x0`，寄存器写使能为低，`sd`指令不写回寄存器文件。

## （2）重命名模块（Rename）

依旧基于前面“一条ADD指令的简单分析”所积累的基础，我们首先回顾重命名模块中的核心部件，并推测一条store指令可能涉及的部件操作。

在重命名模块中，最重要的两个部件是：

1. **RAT**：重命名地址映射表，用于将逻辑寄存器映射为物理寄存器。
2. **FreeList**：空闲物理寄存器列表，用于分配新的物理寄存器。

对于RAT，指令需要进行**读操作**，以获取其源操作数所映射的物理寄存器。对于FreeList，则需要**读取**一个空闲的物理寄存器，以便分配给需要回写结果的指令，并更新RAT的相应映射。此外，该模块还会计算一个**ROB表项编号**，传递给后续流水级。

对于一条store指令，其执行依赖于两个源寄存器的值，因此**必定**会读取RAT表，以获取这两个逻辑寄存器当前所映射的物理寄存器编号。然而，store指令不产生需要回写寄存器的结果，因此：

1. **不会**触发FreeList分配新的物理寄存器。
2. **不会**对RAT表进行写操作。

综上所述，一条store指令在重命名模块中的行为可以归纳为：

1. **读取RAT表的映射关系**，获取其源操作数所映射的物理寄存器编号。
2. **取得新分配的ROB表项**，为该指令在重排序缓冲区中预留位置。

完成这两项操作后，指令即可进入后续的流水线阶段。

### （2.1）RAT表的读操作

从前面的分析可知，RAT表的读操作实际上在译码阶段就已经启动。这部分过程的详细解析可以参考“一条ADD指令的简单分析”中的2.3节。

流程大致如下：在译码阶段，相关的读取信号就已经被送入RAT。之后，会经过一系列复杂的处理，主要包括各种旁路（bypass）选择操作。最终，我们直接观察读取到的信号和结果：

![1774925844853-0644a2c3-cf1e-448f-84d5-634a2e37c316.png](../../img/simple-analysis-process-of-a-store-instruction/figure-011-rat-flow-decode-stage.png)

通过对前面指令的分析，我们知道这条指令的两个源操作数分别是逻辑 2 号寄存器（`sp`）和逻辑 1 号寄存器（`ra`）。因此，在译码阶段就会将这两个地址（2 和 1）向外发出。经过一个周期的延迟，我们观察 RAT 读取的结果。

可以看到，读出的值分别是 13 和 19。那么，这是否真的意味着：当前逻辑 2 号寄存器（`sp`）被映射到了物理寄存器 13 号，而逻辑 1 号寄存器（`ra`）被映射到了物理寄存器 19 号呢？

为了验证这一点，我们需要将最终从重命名模块输出的信号提取出来观察：

![1774926356657-204d64e2-78da-4019-8b07-6074ec806d15.png](../../img/simple-analysis-process-of-a-store-instruction/figure-012-rat-rename-stage-signal.png)

会惊奇地发现，最终的结果是：`rs2`（即逻辑 1 号寄存器，`ra`）确实被映射到了物理寄存器 19 号。但是，对于 `rs1`（即逻辑 1 号寄存器，`ra`），我们从 RAT 表读出的数据是 13 号寄存器，为什么最终传出的信号却显示它被映射到了物理寄存器 20 号呢？

原来，在读取操作中，还存在另一条旁路路径。在“一条ADD指令的简单分析”2.3节中，我们重点分析了同一路指令内部的旁路路径。`intReadPorts`的读取结果解决了同一路指令不同周期之间的数据依赖。然而，很容易想到，**不同路之间也应该存在旁路路径**，以处理跨路指令的数据相关。

举例来说，我们当前的 `sd`指令处于下标为 3 的这一路上，它需要逻辑寄存器 1 号（`ra`）的值。但如果此时在下标为 0（即下标更低、即逻辑顺序更靠前）的那一路上，有一条指令会对逻辑寄存器 1 号进行回写，那么根据重命名理论，当前 `sd`指令所依赖的 1 号寄存器，其映射关系就应该来自于那条指令所分配的新物理寄存器。

我们直接查看波形图来验证：

![1774927039698-20f19585-fd43-4949-b847-159e4b688968.png](../../img/simple-analysis-process-of-a-store-instruction/figure-013-rat-inspect-waveform-signal.png)

通过逐一比较各路信号可以发现，下标为 0 的那路指令确实在对 RAT 表进行写入。其写入地址是 2，表明要更新逻辑寄存器 2 号（`sp`）的映射关系。写入的数据是 20，说明该指令会对逻辑寄存器 2 号进行回写，并将结果存入 20 号物理寄存器。那么，我们的 `sd`指令若要获取正确的 2 号逻辑寄存器的值，就应该去 20 号物理寄存器读取。因此，`sd`指令获取的 2 号逻辑寄存器的映射关系，**应当是 20 号物理寄存器**，而 RAT 表直接读出的 13 号映射反而是过时的旧值。

通过此例，可以理解 RAT 表中处理数据相关的旁路路径至少存在于两处：

1. **同一路内，不同时刻前后两条指令之间的数据相关**。
2. **同一时刻，不同路之间指令可能引发的数据相关**。

至此，可以明确：这条 `sd`指令所需的 `rs1`和 `rs2`分别来自逻辑寄存器 2 号和 1 号。经过跨路旁路处理后，它们被正确映射到了**20 号**和**19 号**物理寄存器。

### <font style="color:rgb(38, 38, 38);">（2.2）分配Rob表项</font>

在重命名阶段，系统会自动记录每一次的分配情况，独立地为每条有效指令分配 ROB 表项值，而无需实时查询 ROB 页表的状态。其具体的代码实现和分配逻辑，详见“一条ADD指令的简单分析”。这里我们直接查看分配结果，确认其具体分配到了哪一个表项：

![1774938590514-77f973e6-afec-43ac-8781-7b5605e25967.png](../../img/simple-analysis-process-of-a-store-instruction/figure-014-rob-rename-stage-add.png)

可以确定，系统为该指令分配的 ROB 表项值是 49。这表明在后续的分发阶段，这条指令的相关信息将被写入 ROB 表的第 49 项。

对于一条 `sd`指令而言，在重命名阶段，简单来说，只需要完成以下两件事就足够了：

1. **获取两个源寄存器的物理映射值**，即其源操作数所对应的物理寄存器编号。
2. **取得系统分配的一个 ROB 表项**，为后续的顺序提交保留位置。

在完成上述操作后，指令将携带这些新生成的信息，并结合之前译码阶段得出的所有控制与数据信号，一同进入下一流水级，以进行后续的调度、执行等操作。

## （3）分发模块（dispatch）

在分发阶段，对于一条 `sd`指令而言，相较于普通的 `add`指令，其内部需要处理的事务相对更加复杂一些。

![1774941224480-ab60f680-9fed-474a-b21e-fa02bce593fd.png](../../img/simple-analysis-process-of-a-store-instruction/figure-015-dispatch-stage-sd-add.png)

通过查看架构图可以发现，在这个阶段，指令流首次开始与 LSQ 建立交互。

这里，我简要描述一下访存指令在后续流水线中的大致执行过程。访存指令在流水线中运行到分发阶段时，除了进行常规的与 ROB、BusyTable 的交互操作外，还会与 LSQ 进行交互，将当前的访存指令信息存入 LSQ 中。

那么，访存所需的地址和数据该如何获取呢？这需要访存指令继续向后续流水线（发射、执行等）推进，一路获取这两个关键信息，直至最终到达执行单元进行计算。

![1774941883793-43b57777-8a81-4b0b-828f-fb1564c9ea48.png](../../img/simple-analysis-process-of-a-store-instruction/figure-016-dispatch-stage-memory-address.png)

也就是上图中红色圆圈标识的地方，访存指令在计算出地址和数据后，会将这些信息送入 LDU 单元，随后进入访存流水线进行后续操作。

有一个特别重要的点需要注意，在前面分析一条加法指令的“4.（3.5）节”中，我们曾遇到这样一个问题：

> 我们先来弄清楚这里为什么会有编号 0 到 16 一共 17 个发射队列。可以查看架构图：
>
> ![1773975913411-cfd2bc9a-a17d-4338-941a-bf329715661b.png](../../img/simple-analysis-process-of-a-store-instruction/figure-017-dispatch-stage-analysis-add.png)
>
> 波形中的 17 个发射队列指的就是上图这些。你可能会数一数，发现图中一共画了 19 个方块，为什么数量对不上呢？对此，笔者暂时也没有完全弄懂原因，推测可能是在 `memScheduler`中有队列进行了合并。但对于前面的 `IntScheduler`部分，其序号应该是能对应上的。

在深入剖析了 sd 指令的发射过程后，就大致能解释这种“奇怪”现象出现的原因：

因为对于 store 指令，它需要“写地址”和“写数据”两类信息，而在香山中，这两类数据的计算会被分开、独立进行发射。

具体到架构图中：![1774337008924-947f75af-cf64-44bc-aee9-c1c58bfd3c57.png](../../img/simple-analysis-process-of-an-add-instruction/figure-063-store-execute-address.png)

* **STA (Store Address)** 队列负责发射地址计算。
* **STD (Store Data)** 队列负责发射数据计算。

这两者（STA 与 STD）是成对工作的。例如，图中的队列 1 和队列 3 就是一对；队列 2 和队列 4 是另一对

TODO：(目前还未确定到底有没有以上“配对关系”)

这意味着，**一条 store 指令会被同时送入两个不同的发射队列，分别独立发射**。这就是为什么看起来发射队列数量“增多”的原因。实际上，这是由于 store 指令的特殊性导致的“一分为二”的发射策略。

因此，必须明确一点：在发射标量 store 指令时，其“计算地址”和“获取数据”这两部分操作是分开的，并且会被分发到不同的发射队列中。所以，一条 store 指令最终会占用两个不同的发射队列资源。

清楚明白了以上内容之后，就可以逐步开始分析在这个模块中具体需要执行的操作。我们先从常规操作开始：

### （3.1）写入ROB表项

先来确认分发模块对 ROB 的操作方式，提取以下信号进行查看和验证：

![1774943734198-e9c6d3bc-80bf-468e-a8b6-00dcc336c063.png](../../img/simple-analysis-process-of-a-store-instruction/figure-019-rob-dispatch-stage-signal.png)

首先，根据 `*valid`信号确认当前进入分发阶段的指令是有效的。接着，查看这条指令是如何向 ROB 发起写入请求的：通过 `enqRob_req_*_valid`信号确认它在当前周期发起了请求，而 `*robIdx_value`表示要写入的 ROB 表项索引。可以看到，在当前请求周期内，系统将对 ROB 表项的第 49 项进行写入。这与我们之前在重命名阶段确认的、为该指令分配的 ROB 表项地址（49）完全一致。因此，这里发生的请求行为符合预期。

接下来，我们直接查看第 49 号表项被写入的具体信息：

![1774944497593-d5193878-92c0-4ce9-a061-8fd154a5576d.png](../../img/simple-analysis-process-of-a-store-instruction/figure-020-rob-inspect.png)

可以比较清晰地看到，ROB 第 49 号表项被写入了符合预期的信息：

* `*valid`信号被成功置高，表明这个表项被写入了有效指令。
* `*ftqIdx_value`被填入了正确的值，`0x10`实质上可以唯一标识这条 sd 指令（在实际流水线后端，指令的 PC 值通常不会完整跟随，而我们通常 用前端 `ftq`传来的 `ftqIdx`作为唯一标识）。
* `*uopNum`被写为 1，表明这条指令需要完成的操作数量是 1 个。
* `*rfWen`未被置高，这进一步验证了 store 指令不会回写寄存器的事实。
* `*commitType`被写为 3，这个特殊值正是我们在译码阶段计算出的结果，完全符合预期。

### （3.2）对lsq的写入操作

在分发阶段，如果识别到当前指令是一条访存相关的指令，系统会根据架构图中的设计，在以下位置（或对应模块）进行特殊处理：

![1774947085225-079c87a7-616c-4ed5-80e3-0a0811f39863.png](../../img/simple-analysis-process-of-a-store-instruction/figure-021-lsq-dispatch-stage-memory.png)

在指令进入分发阶段后，会向 `LsqEnqCtrl`模块发起请求。该模块会进行一系列处理，目前我们仅关心它如何识别当前访存指令是 store 操作还是 load 操作。根据不同的操作类型，它会计算出一个 `*sqIdx_value`或 `*lqIdx_value`，然后将这个值连同其他信号一起，向外部的 Mem 单元发送出去，从而将对应的访存信息填入相应的 Store Queue 或 Load Queue 中。

因此，我们直接查看它如何向外界发起请求：

![1774950083170-aa69f463-f37d-451b-b954-fb1dee03c0e0.png](../../img/simple-analysis-process-of-a-store-instruction/figure-022-lsq-inspect-rob-idx.png)

发送的写入请求会延迟一个周期生效。从请求的信息来看，将 `*robIdx`为 49 的这条访存指令写入到了 Store Queue 的第 2 个位置。通过 `*robIdx`的值可以确认，这正是我们一直追踪的那条 sd 指令。在流水线的后端，由于没有 PC 值作为标识，这个 ROB 索引可以被用作指令的唯一标识。

此外，请求中还清晰地包含了 `*fuType`和 `*fuOpType`的值，用以指示后续执行单元以何种方式执行这次访存操作。

TODO：StoreQuene的写入情况

观察以下信号即可确定对于StoreQuene的写入情况

![1775099186798-20d17aa9-88e6-4193-b624-5c1b5462ec65.png](../../img/simple-analysis-process-of-a-store-instruction/figure-023-lsq-signal-store-queue.png)

![1775099419893-a152e18c-92d3-4c0f-ad2c-5c314e7bc169.png](../../img/simple-analysis-process-of-a-store-instruction/figure-024-lsq-signal-store-queue.png)

其实可以观察到，刚写入进去之后，他的数据和地址都是没有准备好的

### （3.3）读BusyTable的操作

由前面的重命名模块可知，这条 sd 指令需要逻辑寄存器 1 号（`ra`）和 2 号（`sp`）的数据，它们分别被映射到了物理寄存器 20 号和 19 号。因此，此时必须读取 BusyTable 中这两个物理寄存器对应的表项，以确定它们的值是否已就绪，是否仍处于繁忙状态。这需要以物理寄存器地址 20 和 19 为索引，先行读取 BusyTable 来获取这两个寄存器的繁忙状态。

因此，在分发阶段拉出读取BusyTable的相关信号：

![1775008184845-2a7f3c3d-ff25-4c44-86ab-c7e88d854f79.png](../../img/simple-analysis-process-of-a-store-instruction/figure-025-busy-table-dispatch-stage.png)

从波形图中可以总结如下：

当前进入分发模块的信号指示，该指令的两个源操作数来自物理寄存器 20 号（对应逻辑 1 号寄存器）和 19 号（对应逻辑 2 号寄存器）。由于该指令是第 3 路的，而每一路有两个读取 BusyTable 的端口（用于两个源操作数），因此其对应的读端口编号为 6 和 7。

提取这两个端口的信号查看，其请求行为符合预期：

* 端口 6 用于读取 20 号物理寄存器的繁忙状态。
* 端口 7 用于读取 19 号物理寄存器的繁忙状态。

直接观察 `*resp`的返回结果：

* 20 号物理寄存器的返回状态为 **0**，表明其数据**尚未就位**。
* 19 号物理寄存器的返回状态为 **1**，表明其数据**已经就位**。

同时，我们也看到即将被传入下一流水级（IssueQueues）的相关信号，例如 `*srcState`，也已被正确赋值为 0 和 1。

至此，我们对这条 sd 指令在分发阶段的两个源操作数状态已基本清晰：

* 逻辑寄存器 1 号（`ra`）被映射到 20 号物理寄存器，但该物理寄存器的数据**尚未就位**。
* 逻辑寄存器 2 号（`sp`）被映射到 19 号物理寄存器，且该物理寄存器的数据**已经就位**。

### （3.4）准备进入发射单元

完成了以上对一条 sd 指令来说最基本的分发任务后，指令便可以准备发射到后续的流水线了。那么，它将进入哪个具体的发射队列呢？这需要通过以下信号来确认：

![1775011085578-8158d063-e468-42f9-bc2d-f39c96a01a19.png](../../img/simple-analysis-process-of-a-store-instruction/figure-026-sd-dispatch-signal.png)

可以观察到，下标为 3 的这路指令，其发射队列选择信号指向的是下标为 11 的队列。

根据前面“一条ADD指令的简单分析”的 3.(3.5) 节，以及本节“(3)分发模块”开头所讲解的内容：

<font style="color:#DF2A3F;"></font>

![1775011549585-5e45c60d-fb7b-493f-88c0-8f2b8ab378c9.png](../../img/simple-analysis-process-of-a-store-instruction/figure-027-issue-architecture-diagram.png)

<font style="color:#DF2A3F;">在香山的最新版本中，此处发射队列的架构图已经过时</font>

<font style="color:#DF2A3F;">在香山的最新版本中，要查看发射队列的架构，可以去看模块划分情况：</font>

<font style="color:#DF2A3F;">最新版本中是以三大类划分的：整数，浮点，向量：</font>

![1776926645934-07fdeb63-4913-45a0-9514-b9d064269542.png](../../img/simple-analysis-process-of-a-store-instruction/figure-028-issue-architecture-diagram-queue.png)

![1776926655380-773095cc-74a1-4d73-b274-7d29e061cf87.png](../../img/simple-analysis-process-of-a-store-instruction/figure-029-issue-architecture-diagram-queue.png)

![1776926661410-72af2876-0a5c-4077-864e-f8ceb56724de.png](../../img/simple-analysis-process-of-a-store-instruction/figure-030-sta-std-architecture.png)<font style="color:#DF2A3F;">（一共19个发射队列（sta和std为一个））</font>

虽然架构图中下标为 11 的发射队列标注为某个 `IssueQueueLdu`队列，但我们依然坚持“波形是检验真理的唯一标准”。而且，一条存储数据的“sd”指令，怎么可能被发射到一个名字里带有“ld”的队列中呢？

根据波形里的实际指示，这条 `sd`指令的“数据”和“地址”这两部分操作，实际被分别存入了以下两个发射队列：

![1775012126112-79dc4671-ec63-4aaf-9aec-70ad6987e2b2.png](../../img/simple-analysis-process-of-a-store-instruction/figure-031-issue-waveform-sd-address.png)

它们分别是 <code>**IssueQueueStaMou_1**</code> 和 <code>**IssueQueueStdMou_1**</code> 这两个发射队列，分别用于发射地址计算相关的操作和数据相关的操作。

## （4）发射与执行（memScheduler + DataPath + Bypass）

既然已经明确了指令将被存入\*\* **<code>**IssueQueueStaMou_1**</code>** \*\*和 <code>**IssueQueueStdMou_1**</code> 两个队列，我们接下来自然要拉出这两个队列中相应的信号进行观察：

![1775012630279-96e079f2-eeea-4029-be88-6114f39929ac.png](../../img/simple-analysis-process-of-a-store-instruction/figure-032-issue-execute-mem-scheduler.png)

如上图所示，蓝色信号来自 `IssueQueueStaMou_1`模块，绿色信号来自 `IssueQueueStdMou_1`模块。

可以观察到，在指令到达分发模块时，传入两个队列的请求信号 `*valid`就已经被拉高。这两个 uop 操作请求都来自同一项 ROB 表项，即第 49 号表项，可以确定这就是我们一直追踪的 `sd`指令。重点可以观察随这两个请求携带的 `*psrc`信号，即操作数的物理寄存器编号：

* 发往“sta”队列的物理寄存器编号是 20。这对应 `rs1`的物理寄存器编号，`rs1`的值与立即数相加将得到访存地址。因此，该操作数确实应当被发往“sta”队列进行处理，波形行为完全正确。
* 发往“std”队列的物理寄存器编号是 19。这对应 `rs2`的物理寄存器编号，`rs2`中存放的是将要存储的数据。因此，这个值理应被发往“std”队列。波形显示，传入 `IssueQueueStdMou_1`模块的 `*psrc`数据确实是物理寄存器编号 19，行为同样完全正确。

至此，基本可以确定：一条存数指令在分发阶段会被拆分为“sta”（地址计算）和“std”（数据准备）两个独立部分，分别交由不同的执行通路进行运算。运算结束后，再将得到的地址和数据送入访存流水线。接下来，我们继续观察这两个部分发射后的具体操作：

### （4.1）sta的发射与执行

在分发阶段结束后的两个周期，可以看到相关的指令信息被填入了 `entryReg*`对应的表项中：

![1775014139016-514c06c8-2a0b-430d-bcad-b8f09f56fae5.png](../../img/simple-analysis-process-of-a-store-instruction/figure-033-sta-issue-execute-dispatch.png)

`*robIdx_value`被填入了 49，这正是我们追踪的 sd 指令。同时，寄存器中还存放着 `*imm`立即数（值为 8）以及 `*psrc`源操作数（来自第 20 号物理寄存器）等相关信息，这与前面的分析完全一致。

在信息刚被填入时，可以观察到 `*srcState`的值未被拉高，因此相应的 `*issued`信号也为低电平。这表明这个操作尚未被发射（源操作数状态就绪是发射的必要条件，但并非唯一条件）。

然而，在信息被填入后，经过几个周期，可以看到 `*srcState`的值被拉高了。这表示它已接收到“第 20 号物理寄存器的数据已就绪”的消息。在同一周期，`*issued`信号也被拉高，表明与访存地址计算相关的这个操作（sta）被成功发射出去。

此时，我们就可以从这个可以发射的周期位置继续向后观察：

![1775024538854-1e1a5c2b-54a4-4cb1-a301-7f9c0990fa1b.png](../../img/simple-analysis-process-of-a-store-instruction/figure-034-sta-issue-execute-waveform.png)

（仅观察蓝色波形信号，绿色信号属于 std 通路的数据）

从上面的信号可以观察到，当满足发射条件时，对应的数据会向后端传递。可以看到，这个 `sta`操作已成功向 DataPath 发射。当前周期可以看到以下信号正在发往 DataPath：

* `*valid`信号被置高，`*robIdx_value`为 49，这依然指示这是我们一直追踪的 sd 指令。
* `*imm`的值为 8，符合预期。
* `*rf*addr`的值为 20，表明寄存器值来自物理寄存器 20 号，这正是我们 sd 指令的地址来源，符合预期。

另一个重要信息是 `*sqIdx_value`的值为 2。还记得在前面的分发阶段，我们的 sd 指令曾对 Store Queue 进行操作，当时已将信息存入该队列的第 2 号表项。因此，这个值正是为了在后续访问 Store Queue 并向访存流水线发射时，能够准确定位到该指令在队列中的位置。

在确认数据正常发射到 DataPath 之后，我们再来看看 DataPath 如何将其发往下一级流水线，即 MemExu 单元：

![1775025599681-cb81df2d-868a-4150-bf55-7b9188c59404.png](../../img/simple-analysis-process-of-a-store-instruction/figure-035-sta-issue-execute-mem.png)

可以看到，在下一个周期，数据成功发往了下一个流水级。在 `*valid`信号有效的情况下，我们依然可以通过 `*robIdx`和 `*sqIdx`的值来确认当前数据是否来自我们一直在追踪的指令。很明显，49 和 2 这两个数字我们已经非常熟悉了。`*imm`为 8，这也是正确的。

但读取出的寄存器值似乎与预期不符，它难道不应该是我们推测的 `0x80009fe0`吗？至于这里为什么会出现这种情况，目前尚不完全清楚。一个初步的猜测是：由于存在旁路路径，当这些数据在流水线中向后流动时，可能最终会从旁路网络获取到正确的值。

事实也确实如此。我们继续观察这些信息后续的流向，直接提取后续旁路网络的输出结果。从架构图中可以推测，旁路输出可能是在当前周期完成的：

![1775026086234-b32ad342-bcc2-4469-9533-f5fcf0e22f67.png](../../img/simple-analysis-process-of-a-store-instruction/figure-036-sta-issue-execute-result.png)

你会发现，DataPath 和 Bypass 之间没有寄存器，因此我们观察 Bypass 网络在当前周期的波形输入与输出：

![1775026255396-06f8dd74-62e5-4b99-9230-9d13cf19a6c5.png](../../img/simple-analysis-process-of-a-store-instruction/figure-037-sta-issue-execute-bypass.png)

可以清楚地发现，Bypass 网络的输入已经是我们预期要获取的正确数据，即我们推测的 `0x80009fe0`。在下一个周期，这个数据经过一拍寄存后，被传递到后续的流水线，也就是 Bypass 网络的输出。

在 Bypass 网络完成输出后，数据就进入了访存流水线的第 0 级。

![1775026658370-45a6178e-7ef8-4b1c-b8e7-0aacd7619029.png](../../img/simple-analysis-process-of-a-store-instruction/figure-038-sta-issue-execute-bypass.png)

至此，一条 `sd`指令的地址部分（`sta`）在后端的发射与进入执行单元的大致行为追踪就告一段落。

### （4.2）std的发射与执行

下面我们来看这条 `sd`访存指令的数据计算部分，即 `std`，在后端的发射与执行过程。

![1775026947474-dced5f29-d927-41d2-a55b-688b8d405a29.png](../../img/simple-analysis-process-of-a-store-instruction/figure-039-std-issue-execute-sd.png)

在分发阶段执行结束后，可以看到相关信息被填入了 `entryReg*`对应的表项。通过 `*robIdx`的值（49）可以确认当前数据是我们追踪的 sd 指令。`*psrc`被填入了 19，这明确表示将要写入内存的数据来自第 19 号物理寄存器。

值得注意的是，`*srcState`信号一直处于高电平状态。还记得在分发阶段读取 BusyTable 时，我们就已确认这个物理寄存器的数据是准备好的。因此，在队列中，它的状态自然一直显示为“就绪”。

接下来，我们直接查看下一周期它被发射到 DataPath 的数据情况：

![1775027408530-6a596e58-9bbc-4fa7-9958-258878331b88.png](../../img/simple-analysis-process-of-a-store-instruction/figure-040-std-issue-execute-inspect.png)

在 `*valid`信号有效时，根据 `*robIdx_value`或 `*sqIdx_value`的值，可以确认当前信息来自我们正在追踪的 `sd`指令。`*rf*addr`的值为 19，这符合预期，DataPath 即将接收到我们发送的 `std`操作信息。

继续观察数据向后传递的情况：

![1775027708495-03322e3c-8a44-4d65-8425-42277fafc088.png](../../img/simple-analysis-process-of-a-store-instruction/figure-041-std-issue-execute-bypass.png)

经过 Bypass 网络的处理，可以看到在下一周期，Bypass 网络输入的数据正是我们期待的值。`*toMemExu*src_0`的数据为 `0x80000172`，这与我们之前的预期相符，表明这个值将被写入指定的内存地址。

紧接着，数据继续向外传递。再经过一个周期，在离开 Bypass 网络后，它顺利进入了 StoreUnit 的 `std`处理单元：

![1775028162005-f1a6e5ff-a9e5-4f17-a283-2f73a63b2509.png](../../img/simple-analysis-process-of-a-store-instruction/figure-042-std-issue-execute-bypass.png)

![1775028256242-613a5909-6f01-4df7-96be-df3356f413ae.png](../../img/simple-analysis-process-of-a-store-instruction/figure-043-std-issue-execute-bypass.png)

至此，一条 `sd`指令的数据准备部分（`std`）在后端的主要执行流程就分析完毕了。

## （5）访存流水线中的sta及std（memBlock）

对于访存阶段的流水线通路，首先建议仔细阅读设计手册中的相关内容。

一条访存指令所需的数据分为两类：**地址**和**数据**。在前面的分析中，这两者被拆分为两个独立的 uop 操作，通过后端的发射与执行，最终各自获取了相应的数据。现在我们已经观察到，这些数据开始进入访存流水线阶段。接下来的任务，就是在访存流水线中观察这条 `sd`指令的具体行为。

### (5.1)Sta地址流水线StoreUnit以及对SQ的写入

Store 指令的地址计算通路走的是 **StoreUnit 流水线**（数据通路走的是 **StdExeUnit 流水线**）。StoreUnit 流水线分为 S0、S1、S2、S3、S4 五级，我们可以逐级观察。

首先在 **S0 阶段**，即第 0 级流水线。此时该流水级刚刚接收到来自执行单元的数据，我们直接查看相应的波形图：

![1775096461173-37e08fae-80c2-4728-b4cf-c20544a44684.png](../../img/simple-analysis-process-of-a-store-instruction/figure-044-sta-address-store-unit.png)

包括一个来自寄存器的源操作数 `*stin_src*`，其值为 `0x80009fe0`，以及地址所需的立即数 `*imm`，值为 `8`。在第 0 级流水线（S0），首先要完成的任务是根据这两个值计算出所需的虚拟地址。可以看到，在 S0 阶段，`s0_vaddr`的值已被成功计算出来，为 `0x80009fe8`，这完全符合预期。

除了计算虚拟地址，在当前流水级（S0）的同一周期，还有一个重要任务：向 TLB 发出读取请求。可以看到，`*tlb_req*`信号被拉高，并且传递给 TLB 的 `*req*vaddr`值正是我们刚刚计算出的虚拟地址 `0x80009fe8`。当然，当前流水级还会执行其他操作，例如异常检查等，这些我们暂不深入探究。

接着，我们查看 S1 流水阶段的重点波形：

![1775097660647-bb5122cc-bc84-4d09-8691-05cfe881cddb.png](../../img/simple-analysis-process-of-a-store-instruction/figure-045-sta-address-store-unit.png)

在此流水级（S1）阶段，最重要的事件是接收来自 TLB 的响应。信号 `*resp*paddr*`是 TLB 返回的物理地址。即便当前情况比较特殊，我们看到返回的物理地址与虚拟地址相同，但依然需要理解这里已经完成了虚拟地址到物理地址的转换。

除了接收来自 TLB 的物理地址外，在这一流水级还会将相关信息发送给 Store Queue。例如，通过 `*lsq_bits*`等信号，可以观察到系统正在向 Store Queue 发送有效信息。`*lsq_valid`信号确认了当前传输有效。`*robIdx_value`的值（49）告知 Store Queue 当前信息来自我们一直追踪的 sd 指令。同时，其他有效信号，如虚拟地址（`*vaddr`）和物理地址（`*paddr`），以及许多其他信号，也被一并传输过去。

![1775098792544-c4142b39-daa7-4e84-a5b8-aab6f822990d.png](../../img/simple-analysis-process-of-a-store-instruction/figure-046-sta-address-store-unit.png)

以上便是向 SQ 写入信息的全过程。

接着，我们继续观察 SQ 内部的写入情况。由于 `*sqIdx*`为 2，我们提取索引为 2 的表项数据进行查看：

![1775099605881-c13b2215-7a35-4e71-9423-1541291a68fd.png](../../img/simple-analysis-process-of-a-store-instruction/figure-047-sta-address-store-unit.png)

可以看到，相关数据已成功写入 SQ 中。

后续流水级涉及 MMIO 等内容，此处暂不深入分析。

### (5.2)Store数据流水线StdExeUnit以及对SQ的写入

Store 指令的数据通路走的是 **StdExeUnit 流水线**，我们可以简要观察一下：

实际上，这可能不称为一个典型的“流水线级”，因为此模块的逻辑较为简单：

![1775100557174-475ec0af-c8c0-4741-a1c2-48d62deaf7cf.png](../../img/simple-analysis-process-of-a-store-instruction/figure-048-store-std-unit-queue.png)

![1775100193935-9c2b8ab7-bdd8-4da9-a25e-37a6a6d1dc0c.png](../../img/simple-analysis-process-of-a-store-instruction/figure-049-store-std-unit-queue.png)

数据离开执行阶段后，便进入了上述模块。我们直接提取这个模块中对应的数据来查看：

![1775100314038-5ed0a0d3-2339-4670-a824-3f6f06878d89.png](../../img/simple-analysis-process-of-a-store-instruction/figure-050-store-std-unit-queue.png)

上图圈出的信号属于 `std`通路的输出。它将得到的数据向外传输，包括 `*valid`有效信号，并通过 `*robIdx*`的值来标识当前指令是我们一直追踪的 `sd`指令。接着，这个小模块的输出数据会传到 SQ 的写入端口，即上图中未被圈出的信号。可以看到 `*storeDataIn*`相关的所有信号，表明当前正在向 Store Queue 写入数据。

在写请求有效（`*valid`拉高）的情况下，`*sqIdx*`的值指示了要写入 SQ 的具体位置，`*data`信号也正是前面获取的正确值 `0x80000172`。随后，可以看到 SQ 中对应的存储单元被成功写入了正确的值。

接下来，我们可以再查看一下 Store Queue 的状态：

![1775100985103-fb0ce2d3-6608-447c-8886-505e0c94db31.png](../../img/simple-analysis-process-of-a-store-instruction/figure-051-store-std-unit-queue.png)

可以看到，`datavalid`的状态已经发生了改变。这表明 `std`操作已成功完成，并正确地将数据写入了 SQ。

### (5.3)SQ对Sbuffer的写入

SQ发出写SBuffer的请求，但是是再sta和std都OK之后，过了很久才发出来这些信息的，目前暂不清楚触发SQ对Sbuffer发出写请求的条件的。

![1775630721671-d5f2036f-2943-4926-85f2-075d7374e883.png](../../img/simple-analysis-process-of-a-store-instruction/figure-052-store-queue-buffer-sta.png)

![1775630756702-695d8f48-dc48-44da-87ad-814d52eee872.png](../../img/simple-analysis-process-of-a-store-instruction/figure-053-store-queue-buffer-sta.png)

结合SQ的状态来看这个写请求的发出：

![1775631118364-b18636db-00c6-4fb6-9e4e-2f73a78b5ea7.png](../../img/simple-analysis-process-of-a-store-instruction/figure-054-store-queue-buffer.png)

接着看SBuffer的情况：

![1775614509749-d58c95db-718f-401a-920a-128ead90894f.png](../../img/simple-analysis-process-of-a-store-instruction/figure-055-store-queue-buffer-trace.png)

往回trace就会发现这些玩意儿都是来自于StoreQ的，所以直接看后面某些时刻这些接口传入的信号。

![1775111795012-9b970317-ada8-44c6-b3c3-889bdb32e219.png](../../img/simple-analysis-process-of-a-store-instruction/figure-056-store-queue-buffer-trace.png)

写入 Sbuffer 的接口时序正确。由于地址 `0x80009fe8`的第 3 位为 1，且该指令是 64 位写操作，因此对应的字节掩码为 `0xff00`，这与预期相符。

### (5.4)指令提交到ROB

在上面的过程中，在sta和std大致结束之后，可以在memBlock这个模块中找到如下发往WriteBack的信号来观察

![1775619760115-c834a1dc-0f95-42ba-8a1c-9e5290899794.png](../../img/simple-analysis-process-of-a-store-instruction/figure-057-rob-sta-std-mem.png)

（trace这些信号可以确定他们来自于StoreUnit）

会发现在这sta以及std计算完成之后，memBlock自然地向写回阶段去回写数据了：

![1775619636299-7d311ad3-c3f2-4983-8491-78e45215c3c6.png](../../img/simple-analysis-process-of-a-store-instruction/figure-058-rob-sta-std-mem.png)

对于我们这条sd指令，他是包含着两个部分的，一个是sta一个是std，所以在这俩微操作就位之后，在robEntries中的opnum马上就变了：

![1775619829410-27a9f7fe-673d-45b2-96d2-9f0c763f8ee3.png](../../img/simple-analysis-process-of-a-store-instruction/figure-059-rob-sd-sta-std.png)

这也就意味着该指令本身当前已经具备提交条件了，之所以还没提交是因为前面有指令阻塞着嘞。

等到前面表项的指令提交之后，他自己也就可以顺理成章地提交了：

![1775629809932-001348a9-f96b-4f16-b0e5-528c7bfa4eb1.png](../../img/simple-analysis-process-of-a-store-instruction/figure-060-rob-signal-index-valid.png)

![1775629963194-9a0d0a58-7a17-4c2f-8880-f3c90dda8dd6.png](../../img/simple-analysis-process-of-a-store-instruction/figure-061-rob-signal-index-valid.png)

上图的信号表示当前周期的需要提交的指令，数据表示rob的index，\*valid拉高即表示当前周期正在提交。

## （6）Sbuffer与Dcache的交互

站在CPU的视角这些应该都会变成透明的了吧，CPU的提交等这种指令操作已经不再依赖于这边的信号了（除了重定向）

从下面开始，就开始换程序波形了.......

在这一节之前，依据的波形都是hello\_world的波形：/nfs/home/wanghao/xs-env/myWaves/helloWorld

然后在里面找到了一条十分珍贵的sd指令然后看他的行为。

但实际上，在这个给程序中，CPU核并未与Cache发生交互，因为Sbuffer的容量和大小足以支撑该程序中的所有存数相关的操作全部完成。

所以，如果要看Sbuffer和Cache的交互的话，我们也只能切换程序，寻找一个拥有非常多的存数指令的程序，并且指令多到能够促使Sbuffer对Cahce进行访问。因此选择了使用arch-fuzz框架随机包含了全部的上千条sd指令的程序：

/nfs/home/wanghao/xs-env/myWaves/onlysd

而Sbuffer满足什么条件才能对Dcahe发起访问呢，目前还未从波形中找到答案......

不过我们知道了满足一些常见的条件时例如被填满了时，就会访问Dcahe了，所以直接去看他访问Dcahe的接口就好。无论是看波形图还是看代码，都能比较清楚认出，Sbuffer访问Dcahe的接口协议是比较简单的，就是最基本的valid、ready握手协议：

![1775615927702-9b3f9b8c-7b8d-417a-bde5-b6b6fc092ee1.png](../../img/simple-analysis-process-of-a-store-instruction/figure-062-store-buffer-dcache-interface.png)

所以直接去观察这些信号就好咯

### （5.1）先介绍一下两者之间的整体接口

<code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">DCacheToSbufferIO</font></code> 是香山处理器中\*\*<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">DCache暴露给 SBuffer</font>**的核心交互接口，承载 SBuffer 与 DCache 之间**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Cache 行粒度</font>\*\*的存数请求 / 响应交互。接口继承 <code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">DCacheBundle</font></code>，复用 DCache 相关的基础参数（如 Cache 行大小、地址位宽、路数等）；所有信号的设计围绕 “存数请求下发→DCache 处理→响应返回” 的核心流程展开。

![1775530208179-d36f9f78-37ef-4e24-8b56-8fb9db17fac9.png](../../img/simple-analysis-process-of-a-store-instruction/figure-063-interface-dcache-store-buffer.png)

1. <code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">val req = Flipped(Decoupled(new DCacheLineReq))</font></code>

![1775530835520-5283304c-36f2-4f1d-b539-ff0b0b18243c.png](../../img/simple-analysis-process-of-a-store-instruction/figure-064-interface-val-req-flipped.png)

| <font style="color:black;">维度</font> | <font style="color:black;">具体说明</font> |
| --- | --- |
| <font style="color:black;">接口类型</font> | <font style="color:black;">Flipped(DecoupledIO)：- DecoupledIO 是香山标准的握手协议接口，包含 valid（请求有效）、ready（接收方就绪）、bits（请求数据）三个子信号，仅当 valid & ready（即 fire）时，请求才被真正传递；- Flipped 表示接口方向反转：原本 DecoupledIO 默认为 “DCache 作为主设备发请求”，反转后变为SBuffer 为主设备、DCache 为从设备（即 SBuffer 向 DCache 发请求，DCache 准备好后接收）。</font> |
| <font style="color:black;">核心用途</font> | <font style="color:black;">SBuffer 将「字粒度（如 sd 指令的 8B）」存数请求转换为「Cache 行粒度（通常 64B）」后，通过该接口将请求下发到 DCache 的主流水线（main pipe）。</font> |
| <font style="color:black;">承载数据（DCacheLineReq）</font> | <font style="color:black;">香山源码中DCacheLineReq是 Cache 行粒度的请求结构体，核心字段通常包含：</font><br/><font style="color:black;">- cmd：内存操作命令（如M\_XWR写操作、M\_XLR读操作，sd 指令对应写命令）；</font><br/><font style="color:black;">- addr：64B 对齐的物理地址（Cache 行基地址）；</font><br/><font style="color:black;">- data：512bit Cache 行数据（仅 sd 指令对应的 8B 位置有效，其余为 0）；</font><br/><font style="color:black;">- mask：64bit 写掩码；</font><br/><font style="color:black;">- id：请求 ID（用于响应和请求的一一匹配）；</font><br/><font style="color:black;">- source：请求来源标记（标记为 SBuffer，区分其他来源如 Load 流水线）。</font> |
| <font style="color:black;">交互时序</font> | <font style="color:black;">SBuffer 在req.valid置位时表示有 Cache 行请求要发送；</font><br/><font style="color:black;">DCache 在req.ready置位时表示主流水线空闲、可接收请求；</font><br/><font style="color:black;">两者同时为真时，请求被 DCache 接收，SBuffer 切换到等待响应状态。</font> |

如图所示，连续给dcahe发送了若干写Cache的请求：

![1775530543559-878963c3-503a-4c31-b345-e3d0de9aee2b.png](../../img/simple-analysis-process-of-a-store-instruction/figure-065-interface-dcache-cache-val.png)

2. <code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">val main_pipe_hit_resp = ValidIO(new DCacheLineResp)</font></code>

![1775530879052-3ed12305-2cde-41e6-acb0-c6c63e0f8dd9.png](../../img/simple-analysis-process-of-a-store-instruction/figure-066-interface-val-pipe-hit.png)

| <font style="color:black;">维度</font> | <font style="color:black;">具体说明</font> |
| --- | --- |
| <font style="color:black;">接口类型</font> | <font style="color:black;">ValidIO：包含 valid（响应有效）、bits（响应数据）两个子信号，无 ready 信号（DCache 主动推送响应，SBuffer 被动接收）。</font> |
| <font style="color:black;">命名拆解</font> | <font style="color:black;">main\_pipe：DCache 的主流水线（处理常规 Cache 访问的核心流水线，区别于 refill 填充流水线）；</font><br/><font style="color:black;">hit：存数请求命中DCache（目标 Cache 行已在 DCache 中，无需从下一级存储 / 内存填充）；</font><br/><font style="color:black;">resp：DCache 返回给 SBuffer 的响应。</font> |
| <font style="color:black;">核心用途</font> | <font style="color:black;">DCache 主流水线处理完 SBuffer 的存数请求后，若请求命中DCache，通过该信号返回 “命中响应”，告知 SBuffer：</font><br/><font style="color:black;">- 存数操作已完成（直接写入 DCache 的 Data Array）；</font><br/><font style="color:black;">- 携带请求 ID、无错误标记等关键信息。</font> |
| <font style="color:black;">承载数据（DCacheLineResp）</font> | <font style="color:black;">Cache 行粒度的响应结构体，核心字段通常包含：</font><br/><font style="color:black;">- id：请求 ID（与DCacheLineReq的 id 匹配，确保响应对应正确的请求）；</font><br/><font style="color:black;">- miss：Bool 型，标记是否未命中（命中时为false.B）；</font><br/><font style="color:black;">- error：Bool 型，标记是否有访问错误（如权限错误、ECC 错误）；</font><br/><font style="color:black;">- data：可选的 Cache 行数据（存数响应通常无需返回数据，Load 响应才会携带）。</font> |
| <font style="color:black;">交互场景</font> | <font style="color:black;">对应 sd 指令的 “Cache 命中” 场景：SBuffer 发写请求→DCache 主流水线命中→main\_pipe\_hit\_resp.valid置位→SBuffer 收到后，结束该存数请求的处理（回到空闲状态）。</font> |

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">（5.2）再观察一下在这种协议之下的具体波形行为</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">首先观察Dcahe对Sbuffer的接请求和回响应的两大组接口：</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">先看Sbuffer的接口，看他是怎么发出请求并且接收响应的。</font>

![1775631537116-adad6e29-6df5-47a3-ae01-c4f4361fbf09.png](../../img/simple-analysis-process-of-a-store-instruction/figure-067-store-buffer-interface.png)

![1775616835606-ff98ceda-71f1-4e18-9f0f-88942550d37b.png](../../img/simple-analysis-process-of-a-store-instruction/figure-068-store-buffer-interface.png)

实际上通过这个图就可以大致判断出来，id为1的那个请求这么早就被Cache响应回去了，可能是hit了的应该，其他的可能都miss了。

所以这时候就拉出来 Dcache与下一级L2cache的TileLink协议接口：

果然！你会发现，除了id为1的那个请求，也就是访问地址为0x80200fc0的那个请求之外的请求，其余的请求后续都让Dcache通过TileLink总线往下一级Cache发请求去了：

![1775617278409-61f735c4-ed35-4633-b62e-11b5508a149c.png](../../img/simple-analysis-process-of-a-store-instruction/figure-069-id-address-dcache.png)

![1775617073437-cfa78cf2-5697-4f16-aaf4-090dc7f2b7fe.png](../../img/simple-analysis-process-of-a-store-instruction/figure-070-id-address-dcache.png)

当然，这里也简单地提到了一个Dcahe与下一级L2Cache之间的交互接口，后续章节再探索他们俩之间的具体交互行为。

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">（5.3）Dcahe的具体波形行为</font>

？为什么Dcache的结构和数据位宽对不上？

![1775788873272-f4219fa3-f9ac-4ea2-b6bd-87c67a446ec8.png](../../img/simple-analysis-process-of-a-store-instruction/figure-071-dcache-waveform-tag-bits.png)

如果按以上规格，tag：34bits，index：8bits，offset：6bits

但是波形和手册里都是：

![1775789003351-8a2e49a0-c457-43b1-a737-18465648f262.png](../../img/simple-analysis-process-of-a-store-instruction/figure-072-dcache-waveform-manual-analysis.png)

![1775789028119-a8218208-80ca-477d-af37-2e6977f3aa72.png](../../img/simple-analysis-process-of-a-store-instruction/figure-073-dcache-waveform-manual-analysis.png)

那接着上文分析，我们就得详细地看一下Dcahe中具体有哪些行为可以判断当前请求时命中还是缺失呢。

从上文可以清楚知道，在Dcahe接受到来自于Sbuffer的请求之后，若这个请求命中，则可以在很短的时间内返回响应数据，如若这个请求所要的数据在Cache中缺失，则需要Dcahe通过TileLink总线往外下一级L2Cache进行数据交互拿取新数据。

所以我们来看数据从Sbuffer进入Dcache之后的详细过程：

首先观察addr传出后去了哪里：

通过信号追踪的功能，发现了地址进入了mainPipe的模块里面，通过IO传了进去：

![1775787701834-0bb88268-9303-4390-a4b2-f7c1787f23bc.png](../../img/simple-analysis-process-of-a-store-instruction/figure-074-dcache-waveform-signal-address.png)

再追踪这个传入的地址信号：

![1775787805988-7d430068-3042-45c3-bcd9-f4f8593575ec.png](../../img/simple-analysis-process-of-a-store-instruction/figure-075-dcache-waveform-address-signal.png)、

轻易地发现了命中信号的对比逻辑。

#### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">（5.3.1)当请求Hit时的行为</font>

所以先来看命中过程中的对比逻辑吧：在前面的分析可以清晰得知，访问地址为0x80200fc0的那个请求是命中的，所以我们就追着这条请求的相关信号看：

![1775789254721-0947e725-a1d8-4f5d-a0d2-2dbbfb053ad1.png](../../img/simple-analysis-process-of-a-store-instruction/figure-076-hit-analysis-address-signal.png)

![1775788724084-f1e621a2-94e2-4494-babe-7df1ddc8af29.png](../../img/simple-analysis-process-of-a-store-instruction/figure-077-hit-analysis-address-signal.png)

当地址信号进入MainPipa流水级之后，上图所描述的内容是：将被打一拍被赋值到信号s1\_req\_addr中，然后在该地址中提取tag数据（\[47:12]位中的数据为Tag信息），然后用index信号去找到4路way各自的对应位置的tag，去对比4路way各自的tag数据，最终的结果也就表明，当前是对比上了way0的数据的，也就是第一个way

，表明访问地址为0x80200fc0的那个请求所需要的数据目前是有效存在于cache块中的。并且我们还可以看到meta\*信号的值是0x2，先查找设计手册得知这个值所代表的意义：

![1775789526195-258a516b-9330-46b4-a0da-aec9224e1d3c.png](../../img/simple-analysis-process-of-a-store-instruction/figure-078-hit-address-cache-meta.png)

于是发现当前Cache块的状态是Trunk状态：“Trunk”通常意味着该缓存行是“主线有效”或“主路径”数据，可能表示数据已验证、未被修改、且是主流访问路径上的数据。可能表示“已确认一致性”或“共享有效”。

所以，综合以上信号，way0的hit信号被拉高，整体指示hit的信号s1\_tag\_match也被拉高了，指示当前请求的Cache块是命中的，可以直接对Cache进行操作。

![1775790530856-8d2665c7-6258-4083-b0e8-26a02779f547.png](../../img/simple-analysis-process-of-a-store-instruction/figure-079-hit-signal-tag-match.png)

可以看到hit信号是一级一级在Cache的流水线中不断往下传的。

其中具体的行为是（响应hit时）：

在io流水级：发出读tag表的请求

在s1流水级：接收信号并一一对比是否命中

在s2流水级：也许是要讲数据写入（后续再来确认）

在s3流水级：给SB返回响应

同时也可以看一下miss信号的传递过程：

![1775802545894-0bb21f2b-d70d-4e38-995e-0c3bb57abb80.png](../../img/simple-analysis-process-of-a-store-instruction/figure-080-hit-miss-signal.png)

也可以看到miss信号也是一直没有生效的

接下来可以看看响应是怎么返回去的。但我们是看的是，sd指令，Sbuffer对Dcache的访问就是写操作，所以Dcache只会接受来自Sbuffer的数据，在能成功接收时才会返一个指示信号表明此时已经接收，前文已经确认了这一点：

![1775616835606-ff98ceda-71f1-4e18-9f0f-88942550d37b.png](../../img/simple-analysis-process-of-a-store-instruction/figure-068-store-buffer-interface.png)

也就是这些响应信号。那么这个时候再来看看生成这些响应信号的逻辑吧。

![1775801814650-253ddb1c-5d77-4e10-a652-53e7e308ff00.png](../../img/simple-analysis-process-of-a-store-instruction/figure-081-hit-signal.png)

会发现总响应信号是和s3\_hit这个周期一起拉高的。

![1775802268149-5e2d9173-c5bf-4dbf-96c0-aa8bbed593c2.png](../../img/simple-analysis-process-of-a-store-instruction/figure-082-hit-signal-store-go.png)

![1775802861518-c8074b78-6092-40aa-8e16-d77f75517295.png](../../img/simple-analysis-process-of-a-store-instruction/figure-083-hit-signal-store-go.png)

可以看到该信号只要在s3有效的时候，让s3\_store\_can\_go信号有效时候就表示这条hit的Sbuffer访问Dcache的操作已经完成了，而这个信号自然在没有miss的情况下都是拉高，所以自然就在s3流水级拉高了。

因为这个信号表明在这个s3流水级接收到的数据请求不仅仅是有效的，并且还没有miss信号。所以那肯定就是hit了，所以直接给外面的Sbuffer返回有效信号了。

上面的内容差不读吧整个过程都顺了一遍，现在再回过头看看Dcaceh中是怎么处理数据的：

首先自然的，data数据进入流水线后，也会随着io、s1、s2等一直往下传，这个我们直接追踪波形就能确定：

![1775803610130-90cd01c5-3ebd-4aac-aaaa-ed66a7333609.png](../../img/simple-analysis-process-of-a-store-instruction/figure-084-hit-io-waveform-signal.png)

在io流水级和s1流水级，数据信号只是传递，没有其他的用途。但是一旦数据信号到达s2流水级成为s2\_req\_store\_data这个数据，就有其他的用途或走向了：

![1775803398872-c23a45f6-8308-4f98-982f-0a0f176f8af3.png](../../img/simple-analysis-process-of-a-store-instruction/figure-085-hit-io-signal-req.png)

会根据miss信号的值给另外的一个信号*new\_data*进行赋值，实际上，很容易猜出这个新赋值的信号的意义是什么，他表示将要往DataArray写的数据，而能往DataArray写的数据实际上总共就两个来源，要么是Store操作进行写入的值，要么就是充填更新Dcache时从L2Cache拿过来的值。这时再往上看那一串代码，实际上就是在做了一个选择操作，在这两个值中间选一个。那么很显然，在我们这条hit了的请求下 ，这时候请求进入s2流水级时要进行的操作肯定选第一个情况，往DataArray中写值。所以在上图中，会看到*new\_data*信号被赋上了s2\_req\_store\_data的值。

***

实际上还会发现在s2流水级，miss了的请求还会向外发送请求。

![1775804664582-cef5c34c-3fe2-4487-a754-cb23a7549190.png](../../img/simple-analysis-process-of-a-store-instruction/figure-086-hit-miss.png)

***

接下来就会看到这个一个64B的Cache块大小就被划分为8个Bank信号了，这也和前面设计手册所描述的Cache结构十分的统一：

![1775804784364-4fc943a5-4173-4a99-8139-e6270dc423e2.png](../../img/simple-analysis-process-of-a-store-instruction/figure-087-hit-b-cache-bank.png)

![1775805497301-ebf2218c-fa69-4002-ac86-2a3b0e967066.png](../../img/simple-analysis-process-of-a-store-instruction/figure-088-hit-b-cache-bank.png)

![1775805607667-11847a4c-d0de-43b4-9499-a47483a3eb1a.png](../../img/simple-analysis-process-of-a-store-instruction/figure-089-hit-b-cache-bank.png)

一点一点地追踪他的信号就会发现，最后会发现这个信号一步一步地去了DataArray模块

波形：

![1775806189127-66ad538b-8436-4602-80ba-85392aded38d.png](../../img/simple-analysis-process-of-a-store-instruction/figure-090-hit-waveform-array-bank.png)

会发现数据也是一点一点去到了ArrayBank中，去写ArrayBank去咯。

当然，写的时候还有写使能和信号：

![1775806313504-ef87ac0f-ac3e-4628-a33c-bd41e2322d2f.png](../../img/simple-analysis-process-of-a-store-instruction/figure-091-hit-signal.png)

写使能的来源：

![1775806387501-1b8885c7-d9ea-46bc-aed8-0fb27ee78abf.png](../../img/simple-analysis-process-of-a-store-instruction/figure-092-hit-waveform-signal-store.png)

![1775806458420-92a3c50a-3a92-4410-bf2d-990faac28108.png](../../img/simple-analysis-process-of-a-store-instruction/figure-093-hit-waveform-signal-store.png)

![1775806508218-3680eae6-8db3-40b7-ab5e-64757abf9408.png](../../img/simple-analysis-process-of-a-store-instruction/figure-094-hit-waveform-signal-store.png)

会发现，已经不需要看波形了，写使能信号的开源就是s3流水级的各种信号，当当前流水级有效，并且满足下面的条件之一：要么是store操作hit，目前正在写；要么就是一个miss的请求要回填数据了。

然后就会把写ArrayBank的相关信号拉高。

综上，应该是把一条hit的store请求的行为差不多已经顺了一遍了。

#### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">（5.3.2)当请求Miss时的行为</font>

还是结合前面看的波形信号，在下图Sbuffer与Dcache的交互时序中，图中的四个项请求中，唯有第二项请求是hit的，其他三项都是miss 的请求：

![1775617073437-cfa78cf2-5697-4f16-aaf4-090dc7f2b7fe.png](../../img/simple-analysis-process-of-a-store-instruction/figure-070-id-address-dcache.png)

所以我就选择第一个请求来看就好了，这条请求地址为0x80000a40，通过观察这条指令的行为来确定Dcache是如何去处理一条Miss请求的。固然，肯定还是需要经过那一条一条的流水线，所以就来看流水线的行为，其实在前面分析hit行为的时候，就提到过miss请求向外发请求的行为（应该是向MissQuene发请求），是在s2流水级。所以我们还是一点一点往后看吧。

从后往前看吧，因为目前不知道miss信号生成是什么逻辑，只能确定在后面的某个周期会向MissQ发送请求。那就从发送请求那边往回看：

![1775809895692-f0433368-4040-4042-8534-a6558017f952.png](../../img/simple-analysis-process-of-a-store-instruction/figure-095-miss-signal-q-mq.png)

![1775810448913-07e75112-a964-4d64-a559-47178cd1db1b.png](../../img/simple-analysis-process-of-a-store-instruction/figure-096-miss-signal-q-mq.png)

发现实际上就是过了两个周期就往MQ里面送发请求了。

那么最重要的肯定就是发请求的使能信号io\_miss\_req\_valid信号的使能生成逻辑了，往前跟踪：

![1775810503020-ab4dfcb1-3c07-44a3-8de4-a8ad32fa11dc.png](../../img/simple-analysis-process-of-a-store-instruction/figure-097-miss-signal-io-req.png)

发现用的s2\_can\_go\_to\_mq信号的逻辑来主要决定的,继续往前追踪：

![1775810583324-081b9093-e66a-48df-bc0b-dc979df045a1.png](../../img/simple-analysis-process-of-a-store-instruction/figure-098-miss-go-mq-signal.png)

发现是由这么一套逻辑生成的，我们还是主要着眼于看见“s1\_tag\_match”这个信号，发现当没有出现hit的信号的时候，也就主要会把这个值拉高。

![1775810815631-6aca9d7f-01c0-45ac-bd4d-0e6d21d8ce7e.png](../../img/simple-analysis-process-of-a-store-instruction/figure-099-miss-tag-match-signal.png)

所以，对MQ发送请求的使能信号的过程大致就是以上这种过程。（s1\_req\_miss的实际所代表的意义暂不明确）

总结一下对于一条普通的store请求，Dcache和Sbuffer的大致处理过程是这样的：

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">对于普通的 Store 请求，DCache 从 StoreBuffer 接收一条 store 指令后，使用 MainPipe 流水线计算地址 查询 tag 和 meta，判断是否命中，若命中缓存行则直接更新 DCache 数据并返回应答；若缺失则分配 MSHR 将 请求交给 MissQueue，向 L2 请求要回填到 Dcache 的原目标数据行，并等待 L2 Cache 返回的 hint 信号。当 l2\_hint 到达后，向 MainPipe 发起回填请求，进行替换路的选取并将重填数据块写入 DCache 存储单元，在完 成对该数据的 store 操作后向 StoreBuffer 返回应答；若被替换的块需要写回，则在 WritebackQueue 中向 L2 发送 Release 请求将其写回。如果缺失的请求分配 MSHR 项失败，DCache 会反馈一个 MSHR 分配失败的信 号，由 StoreBuffer 随后重新调度该 store 请求。  </font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"></font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">那下面应该就可以去看一看这些数据在Cache中大致的流向咯吧~</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font>


> 更新: 2026-05-08 09:51:28  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/ehf7z3to1gl1ih4i>
