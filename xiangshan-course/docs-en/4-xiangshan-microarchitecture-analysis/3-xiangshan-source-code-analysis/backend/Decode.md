<!--
# 1. 译码

## 3.1 Decode informations（译码信息）

> 如果你是第一次接触处理器译码模块，可能会被"BitPat 匹配表"、"指令融合"、"向量微操作拆分"这些术语搞得有点头大。别担心——译码本质上就是一件事：把一串 0 和 1 翻译成后端能读懂的"工作指令"。让我们一步步来，你会发现它其实就像一个高效的翻译部门。

:::info
通过本节学习，你将能够：

* 🧭 理解译码模块在香山处理器中的**位置与角色**
* 📋 掌握**Decode Information**—— 一条指令被翻译成了哪些控制信号
* 🔍 了解**Pre-Decode 预译码**—— 前端如何"偷跑"获取分支信息
* 🔗 搞懂**Fusion 指令融合**—— 为什么要把两条指令合成一条，怎么合
* ✂️ 读懂**Translate/Split 宏指令拆分**—— 一条向量指令如何变成 N 个微操作
* 🗺️ 获得**从源码到实践的完整学习路径**

:::

### 3.1.1 译码阶段的全景认知

你可以把处理器想象成一座**现代化工厂**：

:::info

* **前端（IFU等）** 是**原料采购部门**——负责从内存中高效、准确地“取回”指令，为整个生产线提供原材料。它定义了工厂的整体节奏和宏观任务需求。
* **译码（Decode）** 是**翻译与任务分解车间**—— 负责将原始的指令“原料”拆解、翻译，并标注出每条指令需要哪些硬件资源、执行什么操作。它将一个宏观任务分解为无数个细小的、可执行的“工艺卡片”，并分发给后端各个执行单元。
* **后端（Rename → Issue → Execute）** 是**核心生产线**——严格按照“翻译车间”提供的“工艺卡片”，在流水线上完成指令的乱序调度、资源分配和实际执行，最终产出运算结果。

:::

没有译码这个"翻译官"，后端拿到的是一堆无意义的 0/1 比特；有了译码，每条指令才能被正确调度和执行。

### 3.1.2 译码流水线全景图

香山的译码并不是一个简单的"查表模块"，而是一条**多阶段、多层次**的流水线：

```plain
┌──────────── IFU（前端）─────────────┐
│                                     │
│  PreDecode（预译码）                 │  ← 轻量级：只提取分支信息
│  ├─ 分支类型识别                     │
│  ├─ 跳转偏移计算                     │
│  └─ RVC 压缩指令检测                 │
│         ↓                           │
│  PreDecodeInfo ──→ BPU / FTQ        │  ← 辅助分支预测
│                                     │
└──────────────┬──────────────────────┘
               ↓
┌────────── DecodeStage（后端译码）──────────────────────────────────┐
│                                                                    │
│  IBuffer ──→ decoders[0..N-1]（简单译码器，每条指令一个）            │
│                │         │         │                                │
│                ↓         ↓         ↓                                │
│           查表生成控制信号 + uopSplitType                            │
│                │         │         │                                │
│                ├─ isSimple? ──→ 直接输出 1 个 uop                   │
│                └─ isComplex? ──→ 送去进一步处理                     │
│                                    │                               │
│                    ┌───────────────┼───────────────┐               │
│                    ↓                               ↓               │
│             FusionDecoder                   DecodeUnitComp          │
│           （指令融合检测）               （复杂指令拆分状态机）        │
│           相邻指令对匹配                 逐拍输出 N 个 uop           │
│           2条→1条                       1条→N条                    │
│                    │                               │               │
│                    └───────────┬───────────────────┘               │
│                                ↓                                   │
│                      finalDecodedInst                              │
│                      （最终微操作序列）                              │
│                                ↓                                   │
│                           → Rename                                 │
│                                                                    │
│  辅助模块：UopInfoGen（计算 uop 数量）│ VTypeGen（维护 VType 状态）   │
└────────────────────────────────────────────────────────────────────┘

```

> **图表解读： 译码实际上分为"前端预译码"和"后端正式译码"两个战场。前端预译码像侦察兵，快速扫描指令获取分支情报；后端正式译码像翻译官，逐条生成完整的控制信号。而在后端内部，又分为"简单译码"（标量 1:1 映射）和"复杂译码"（融合 + 拆分），确保各类指令都能高效处理。**

:::color4
\*\*❤\*\***新手建议：**

现阶段你只需记住：**译码 = 翻译（查表）+ 优化（融合）+ 拆分（向量指令）**。不必一开始就纠结状态机细节，先建立整体观，后面我们逐个击破。

:::

## 3.2 Decode Information——一条指令被翻译成了什么？

### 3.2.1 为什么需要 Decode Information？

你可以将一条 32 位的 RISC-V 机器指令，想象成一张**加密的、高度浓缩的“工作订单”**。它用最简练的编码（对人而言如同“天书”）描述了一个任务，但处理器后端的“工人们”（各种功能单元）却看不懂这份原始订单。

**“译码”（Decode）的核心作用，就是充当这位“翻译官”和“任务分解师”。** 它负责解读这张加密订单，并将其**拆解、细分成一张张后端流水线“工人们”能直接看懂并执行的“标准工艺卡片”（即译码信息）**。

具体来说，译码单元需要从这条 32 位的指令中，解读并生成以下关键信息，来回答后端执行流水线的所有疑问：

:::info

* **【任务识别】**：**这是什么类型的任务？**

  ```
   （是算术运算、内存访问，还是控制跳转？这将决定后续的流程。）
  ```

* **【资源需求】**：**完成这个任务，需要哪些“原材料”？从哪里来？**

  ```
    操作数 1 来自哪个寄存器？还是一个直接写在指令里的数字（立即数）？

    操作数 2 又来自哪里？
  ```

* **【派工指示】**：**这个任务应该派给哪个“车间”（功能单元）去做？具体做什么工序？**

  ```
    送到整数运算单元 (ALU) 做加法，还是送到乘法器？

    如果是访存，是“读”操作 (Load) 还是“写”操作 (Store)？
  ```

* **【交付说明】**：**任务完成后，“成品”（结果）要存放到哪里？**

  ```
    写回整数寄存器堆，还是浮点寄存器堆？
  ```

* **【特殊备注】**：**这个任务有没有需要特别留意的地方？**

  ```
    它是一条必须严格执行、不能被取消的指令吗？

    执行后是否需要立刻刷新流水线（如一些同步指令）？
  ```

:::

**总结来说**，没有“译码信息”，后端就是一堆不知该做什么的强大硬件。译码阶段通过**解读与细分**，将一条复杂的宏观指令，转化为一系列定义清晰的微操作，并为每个微操作规划好完整的“资源-执行-写回”路径，从而驱动整个处理器流水线有序、高效地运转。

### 3.2.2 XSDecode——译码控制信号全貌

香山用 <code>**XSDecode**</code> 类来描述一条指令的所有译码控制信号。（定义于 `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`第94-110行附近）

```scala
case class XSDecode(
  src1: BitPat, src2: BitPat, src3: BitPat,  // 三个源操作数的类型
  fu: FuType.OHType, fuOp: BitPat,            // 功能单元 + 操作码
  selImm: BitPat,                              // 立即数选择
  uopSplitType: BitPat = UopSplitType.X,       // 微操作拆分类型
  xWen: Boolean = false,                       // 整数寄存器写使能
  fWen: Boolean = false,                       // 浮点寄存器写使能
  vWen: Boolean = false,                       // 向量寄存器写使能
  mWen: Boolean = false,                       // 掩码寄存器写使能
  noSpec: Boolean = false,                     // 不能推测执行
  blockBack: Boolean = false,                  // 阻塞后续指令
  flushPipe: Boolean = false,                  // 冲刷管线
  canRobCompress: Boolean = false,             // ROB 可压缩优化
)
```

这就像一张**工艺卡片**，每个字段对应一个"加工指令"。

#### “工艺卡片”的核心字段解析

**1. 【资源与操作定义】—— “用什么材料？做什么工序？”**

* `src1, src2, src3`：指明**源操作数**的来源类型（例如，来自通用寄存器`Reg`、浮点寄存器`FPReg`，或是立即数`Imm`）。这回答了“原材料从哪里来”。
* `fu`与 `fuOp`：这是核心的“派工单”。
  * `fu`(**FuType**) 指定指令应派发到哪个**功能单元**，如整数ALU (`ALU`)、乘法器(`MulDiv`)、访存单元(`Mem`)等。
  * `fuOp`则指定在该功能单元内执行的具体**操作**，如加法(`ADD`)、加载(`LD`)等。
* `selImm`：当操作数包含立即数时，此字段控制如何从指令编码中正确提取并扩展这个立即数。

**2. 【写回目标】—— “成品存放到哪个仓库？”**

* `xWen, fWen, vWen, mWen`：这是一组**写使能信号**，像仓库的入库许可。它们分别控制计算结果是否需要写回**整数寄存器堆**、**浮点寄存器堆**、**向量寄存器堆**或**掩码寄存器**。通常，一条指令只会使能其中之一。

**3. 【流水线特殊控制】—— “有无特殊作业要求？”**

* `noSpec`：标记此指令**不能进行推测执行**（如某些同步指令），必须等其变成“老指令”确定要执行时，才能开始处理。
* `blockBack`：标记此指令会**阻塞后续指令的发射**，常用于序列化点。
* `flushPipe`：标记此指令执行后需要**冲刷（清空）流水线**，例如执行完一条`fence.i`（指令同步栅栏）后。
* `canRobCompress`：这是一个**性能优化标记**，提示重排序缓冲(ROB)在特定条件下可对此类指令进行压缩优化，以节省空间。

**4. 【指令复杂度标记】—— “这是简单零件还是需要组装的套件？”**

* `uopSplitType`：标记此条**宏指令**是否需要以及如何**拆分为多条微操作(Uop)**。例如，一条复杂的向量归约指令，可能会被标记为需要拆分为多个更基础的微操作在流水线中执行。

总结如下：

| **分类** | **字段名** | **类型** | **核心含义（后端动作）** |
| --- | :---: | :---: | --- |
| **输入资源**(Input) | `src1`/ `src2`/ `src3` | `BitPat` | \*\*\*\*指定操作数1/2/3是从整数寄存器(GPR)、浮点寄存器(FPR)还是立即数(Imm)中获取。 |
| **执行控制**(Execution) | `fu` | `FuType.OHType` | \*\*\*\*指定使用哪个功能单元（如 ALU、乘法器、加载存储单元）。 |
| | `fuOp` | `BitPat` | 在该功能单元内执行的具体微操作（如 ADD, SLL, MUL）。 |
| | `selImm` | `BitPat` | 当原材是立即数时，指导硬件如何从指令编码中截取并符号扩展。 |
| **输出写回**(Output) | `xWen` | `Boolean` | 是否将结果写回整数寄存器堆 (Int RegFile)。 |
| | `fWen` | `Boolean` | 是否将结果写回浮点寄存器堆 (Float RegFile)。 |
| | `vWen` | `Boolean` | 是否将结果写回向量寄存器堆 (Vector RegFile)。 |
| | `mWen` | `Boolean` | 是否将结果写回掩码寄存器 (Mask RegFile)。 |
| **流水线行为**(Pipeline Ctrl) | `noSpec` | `Boolean` | 标记为不可推测执行，必须严格按顺序等待执行完毕。 |
| | `blockBack` | `Boolean` | 触发流水线停顿（Stall），阻塞后续指令发射。 |
| | `flushPipe` | `Boolean` | 执行该指令后需要冲刷流水线（通常用于 Fence 等指令）。 |
| **微码优化**(Micro-op) | `uopSplitType` | `BitPat` | 指示复杂指令是否需要被解码器拆分为多个微操作 (Uop)。 |
| | `canRobCompress` | `Boolean` | 允许重排序缓存 (ROB) 将此指令与其他兼容指令合并压缩，以节省空间。 |

### 3.2.3 译码表——指令编码到控制信号的映射

:::info
香山使用经典的 **BitPat 匹配表**进行译码，你可以将其想象成一本**专用翻译字典**：

* **字典左侧**是“外文单词”，即指令的二进制编码模式（`BitPat`）。
* **字典右侧**是对应的“中文释义”，即该指令对应的完整控制信号集合（`XSDecode`实例）。

:::

译码器的工作就是“查字典”：拿到一条32位的机器指令，在表中找到匹配的编码模式，然后将其替换为一组定义好的、驱动后端硬件工作的控制信号。

```scala
object XDecode extends DecodeConstants {
  // 译码表：一个数组，每个元素是 (指令模式 -> 控制信号) 的键值对
  val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
    // RV64I 基础指令示例
    LW   -> XSDecode(
             SrcType.reg,     // 源操作数1类型：寄存器
             SrcType.imm,     // 源操作数2类型：立即数
             SrcType.X,       // 源操作数3类型：无
             FuType.ldu,      // 功能单元：加载存储单元
             LSUOpType.lw,    // 具体操作：加载字
             SelImm.IMM_I,    // 立即数格式：I型
             xWen = true      // 使能：写回整数寄存器
           ),
    ADD  -> XSDecode(
             SrcType.reg, SrcType.reg, SrcType.X, // 两个源都来自寄存器
             FuType.alu,      // 功能单元：算术逻辑单元
             ALUOpType.add,   // 具体操作：加法
             SelImm.X,        // 无立即数
             xWen = true,
             canRobCompress = true // 优化：允许ROB压缩
           ),
    JAL  -> XSDecode(
             SrcType.pc,      // 源操作数1类型：程序计数器(PC)
             SrcType.imm,     // 源操作数2类型：立即数
             SrcType.X,
             FuType.jmp,      // 功能单元：跳转单元
             JumpOpType.jal,  // 具体操作：跳转并链接
             SelImm.IMM_UJ,   // 立即数格式：UJ型
             xWen = true
           ),
    // ... 表中实际包含所有支持的指令，可达数百条
  )
}
```

（定义于 `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`第134-200行附近）

:::info
译码器接收到指令后的“查表”过程如下：

1. **取指**：获取一条32位的RISC-V指令二进制码。
2. **匹配**：将该二进制码与`decodeArray`中每个`BitPat`（位模式）进行比对。`BitPat`支持通配符，例如`0`和`1`表示固定位，`?`表示该位可匹配0或1。
3. **输出**：一旦找到匹配的模式（如`ADD`），译码器便可以提取右侧对应的“释义”（`XSDecode`对象）。
4. **传递**：将`XSDecode`对象中封装的所有控制信号（功能单元、操作类型、写回使能等）输出，传递给流水线的下一阶段（如重命名）。

:::

这张静态的译码表，是实现指令集架构（ISA）语义的基石，它将软件开发者看到的指令（如`ADD`），精确地翻译成了硬件执行单元能够理解的动作序列。

### 3.2.4 默认值——查不到怎么办？

如果一条指令的编码**匹配不到任何合法条目**，就会命中默认值 <code>**decodeDefault**</code>，其 <code>**selImm**</code> 被设为 <code>**INVALID_INSTR**</code>，触发**非法指令异常**：

```scala
def decodeDefault: List[BitPat] =
List(SrcType.X, SrcType.X, SrcType.X, FuType.X, FuOpType.X,
       N, N, N, N, N, N, N, UopSplitType.X, SelImm.INVALID_INSTR)
```

（定义于 `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`第50-61行附近）

:::danger
***这就像翻译官遇到了一个不认识的词——既然看不懂，就标记为"异常"，交给更高级的处理机制。***

:::

### 3.2.5 译码表分类——多本"专业字典"

香山按指令集扩展分组，维护多本"专业字典"，各有侧重：

| **译码表对象** | **覆盖范围** | **对应源码** |
| --- | --- | --- |
| <code>**XDecode**</code> | RV32/64I、M 扩展、系统指令 | DecodeUnit.scalaL133 |
| <code>**FDecode**</code> | 浮点 F/D 扩展 | DecodeUnit.scala |
| <code>**VecDecoder**</code> | 向量 V 扩展（OPIVV/OPIVX/OPMVV 等） | VecDecoder.scalaL186 |
| <code>**BitmanipDecode**</code> | B 扩展位操作 | DecodeUnit.scala |
| <code>**ScalarCryptoDecode**</code> | 标量加密扩展 | DecodeUnit.scala |

各表独立定义，在 <code>**DecodeUnit**</code> 中合并为一张完整的查找表，进行并行匹配。

## 3.3 Pre-Decode 预译码——前端的"侦察兵"

### 3.3.1 为什么需要预译码？

:::danger
你可能会问：**后端不是有正式译码吗，为什么前端还要再做一遍？**

:::

答案是：**时间就是性能**。分支预测器（BPU）需要在取指阶段就做出"下一条指令从哪取"的决策，等到后端译码就来不及了。Pre-Decode 就像派到前线的侦察兵——**不做全面翻译，只快速提取最重要的情报**：分支信息。

### 3.3.2 预译码提取了什么？

这个阶段不会进行完整的译码，而是生成一个名为 <code>**PreDecodeInfo**</code> 的轻量级信息包。这个信息包就像是给后续流水线的一个“速查摘要”，专门用于提前判断和处理可能改变程序执行流程的指令。

| **提取的字段** | **作用与含义** |
| :--- | :--- |
| <code>**valid**</code> | **指令有效性标志**。快速判断当前读取的比特位是否构成一条合法的指令。如果无效，可提前触发异常。 |
| <code>**isRVC**</code> | **压缩指令标识**。判断该指令是标准的32位指令，还是16位的RISC-V压缩指令（RVC）。这直接影响下一条指令的地址计算（+4或+2）。 |
| <code>**brAttribute**</code> | **分支属性**。标识指令的类型，特别是它如何改变控制流：   • 无条件跳转（如 `JAL`）   • 间接跳转（如 `JALR`）   • 条件分支（如 `BEQ`、`BNE`）   • 非分支指令 |
| <code>**jumpOffset**</code> | **跳转偏移量**。对于像 `JAL`和条件分支这类带有立即数偏移的指令，预译码会提前提取出这个偏移量，用于**快速计算潜在的目标地址**（Target PC = Current PC + Offset）。 |

### 3.3.3 预译码的分支匹配表

在预译码阶段，核心任务之一是**快速、准确地识别出所有可能改变程序执行流向的指令**，即分支/跳转指令。为了高效完成此任务，香山处理器使用了一张预先定义好的 <code>**brTable**</code>**（分支匹配表）**。

它不关心指令的具体运算细节，只专注回答一个关键问题：

> **“当前这条指令是不是分支指令？如果是，它属于哪种分支类型？”**

```scala
object PreDecodeInst {
  // 分支匹配表：Array[(指令操作码, 对应的分支属性列表)]
  val brTable = Array(
    // 非分支指令示例
    C_EBREAK -> List(BranchAttribute.BranchType.None),       // 类型：非分支

    // 压缩格式指令 (RVC) 示例
    C_J      -> List(BranchAttribute.BranchType.Direct),      // 类型：无条件直接跳转
    C_JALR   -> List(BranchAttribute.BranchType.Indirect),    // 类型：间接跳转
    C_BRANCH -> List(BranchAttribute.BranchType.Conditional), // 类型：条件分支

    // 标准32位指令示例
    JAL      -> List(BranchAttribute.BranchType.Direct),      // 类型：无条件直接跳转
    JALR     -> List(BranchAttribute.BranchType.Indirect),    // 类型：间接跳转
    BRANCH   -> List(BranchAttribute.BranchType.Conditional)  // 类型：条件分支
  )
}
```

（定义于 `src/main/scala/xiangshan/frontend/ifu/PreDecode.scala`第22-43行附近）

表中定义的三种基本分支类型，决定了处理器后续不同的处理策略：

| **分支属性** | **含义** | **典型指令** | **后续动作关键点** |
| :--- | :--- | :--- | :--- |
| <code>**None**</code> | **非分支指令**。程序顺序执行下一条。 | `EBREAK`, `ADD`, `LW` | 无需特殊处理，继续顺序取指。 |
| <code>**Direct**</code> | **无条件直接跳转**。目标地址由当前PC+偏移量**直接计算**得出。 | `JAL`, `C.J` | 预译码需提取`jumpOffset`，分支预测器可**直接算出**目标地址。 |
| <code>**Indirect**</code> | **间接跳转**。目标地址来自于**寄存器的值**，运行时才能确定。 | `JALR`, `C.JALR` | 目标地址未知。 |
| <code>**Conditional**</code> | **条件分支**。是否跳转取决于**条件判断结果**。 | `BEQ`, `BNE`, `C.BEQZ` | 预译码需提取偏移量。预测器需进行**方向预测**（跳转/不跳转）和**目标地址预测**。 |

### 3.3.4 Pre-Decode vs 完整 Decode 对比

| **维度** | **Pre-Decode（前端侦察兵）** | **Decode（后端翻译官）** |
| --- | --- | --- |
| **位置** | IFU 取指流水线 | 后端 DecodeStage |
| **目的** | 快速提取分支信息，辅助 BPU | 生成完整微操作控制信号 |
| **输出** | 分支类型、跳转偏移、RVC 标记 | FuType、SrcType、FuOpType 等十几个字段 |
| **延迟敏感度** | ⚡ 极高（影响取指带宽） | 中等（影响分派带宽） |
| **处理范围** | 只看部分跳转指令 | 所有指令 |

:::danger
***Pre-Decode 像海关安检——快速扫一眼护照，判断你是不是"需要特别关注的人"；Decode 像入境审查——详细审核你的签证、行程、资金，决定你能做什么。***

:::

***

## 3.4 Fusion 指令融合——两条合一，效率翻倍

在处理器设计中，**指令融合** 是一项关键的微架构优化技术。其核心思想是：**将多条连续的、共同完成一个明确语义的机器指令，在译码或后续阶段合并为一条更复杂的内部微操作**，从而减少对后端流水线资源的占用，提升整体执行效率。

### 3.4.1 为什么需要 Fusion？

编译器生成的代码通常严格遵循指令集架构的规范，有时会将一个高级语言中的简单操作拆分为多条基础指令以确保正确性。然而，从硬件执行的角度看，这可能导致效率低下。

***

**一个典型案例：32位零扩展**

考虑以下C语言函数，它将一个32位无符号整数零扩展为64位：

```c
// C 语言：将 32 位值零扩展到 64 位
uint64_t zext(uint32_t x) { return (uint64_t)x; }
```

编译器可能生成：

```plain
slli r1, r0, 32  // 逻辑左移32位：高32位变为原值，低32位补0
srli r1, r1, 32  // 逻辑右移32位：高32位补0，低32位变回原值
// 最终效果：r1[31:0] = r0[31:0]; r1[63:32] = 0
```

这两条指令的**组合语义**等价于一条 <code>**ADD.UW r1, r0, zero**</code>（即伪指令 <code>**zext.w**</code>）。但从硬件视角看，它们却被当作两个独立的任务来处理。

如果能把它们**融合为一条微操作**，将带来立竿见影的性能与能效收益：

| **资源/指标** | **融合前 (2条指令)** | **融合后 (1条微操作)** |
| :--- | :--- | :--- |
| **发射队列条目** | 2 | 1 |
| **ROB 条目** | 2 | 1 |
| **寄存器读写** | 多次 (读/写) | 1次写 |
| **后端资源占用** | 高 | 低 |

:::danger
**一句话总结*****：***

*Fusion 通过识别并合并这些“编译器生成的、语义上可合并的指令对”，*将多条指令转换为一条更复杂的微操作（Micro-op）*，从而显著减轻后端流水线的资源压力，提升整体执行吞吐率和能效。*

:::

### 3.4.2 Fusion 原则

香山的指令融合不是"乱来"的，它遵循严格的规则：

#### 原则一：仅融合相邻指令对

融合检测器只检查译码宽度内**相邻的两条指令**，绝不跨越边界：

```scala
abstract class BaseFusionCase(pair: Seq[Valid[UInt]])(implicit p: Parameters) {
  require(pair.length == 2)  // 严格两条
}
```

（定义于 `src/main/scala/xiangshan/backend/decode/FusionDecoder.scala`第31-33行附近）

:::danger
***这就像超市的"买一送一"——只有******紧挨着******的两件商品才能凑单，隔了一个货架的不算。***

:::

#### 原则二：数据依赖必须是 Producer-Consumer 模式

第一条指令的结果必须是第二条指令的输入：

```scala
protected def withSameDest: Bool = instr1Rd === instr2Rd   // 同一目标寄存器
def destToRs1: Bool = instr1Rd === instr2Rs1               // 第一条的 rd 流入第二条的 rs1
protected def destToRs2: Bool = instr1Rd === instr2Rs2     // 第一条的 rd 流入第二条的 rs2
```

（定义于 `src/main/scala/xiangshan/backend/decode/FusionDecoder.scala`第46-48行附近）

:::danger
***第一条指令是"生产者"，第二条是"消费者"，融合的前提是它们之间存在明确的供需关系。***

:::

#### 原则三：融合只改变有限的控制信号

为了时序优化，融合只允许修改以下控制信号：

| **可修改字段** | **含义** |
| --- | --- |
| <code>**fuType**</code> | 功能单元类型 |
| <code>**fuOpType**</code> | 操作码 |
| <code>**src2Type**</code> | 第二源操作数类型 |
| <code>**selImm**</code>/ <code>**imm**</code> | 立即数选择与值 |

（参见 `src/main/scala/xiangshan/backend/decode/FusionDecoder.scala`第76-84行附近）

#### 原则四：两种融合模式

| **模式** | **说明** | **例子** |
| --- | --- | --- |
| **融合为已有指令** | 第一条的 op 替换为另一条合法指令的译码结果 | <code>**SLLI+ADD**</code>→ <code>**SH1ADD**</code> |
| **融合为自定义操作码** | 第一条的 fuOpType 替换为香山自定义的内部操作码 | <code>**SLLI32+SRLI31**</code> → <code>**szewl1**</code> |

#### 原则五：第二条指令被"吞噬"

融合生效后，第二条指令从流水线中消失，第一条指令携带融合后的控制信号继续执行。

### 3.4.3 Fusion 的工作机制

<code>**FusionDecoder**</code> 模块对译码宽度内每对相邻指令**并行检测所有融合模式**，优先级由列表顺序决定：

```scala
class FusionDecoder(implicit p: Parameters) extends XSModule {
  val fusionList = Seq(
    new FusedAdduw(pair),      // slli32+srli32 → add.uw
    new FusedZexth(pair),      // slli48+srli48 → zext.h
    new FusedSexth(pair),      // slliw16+sraiw16 → sext.h
    new FusedSh1add(pair),     // slli1+add → sh1add
    new FusedSh2add(pair),     // slli2+add → sh2add
    new FusedSh3add(pair),     // slli3+add → sh3add
    new FusedLui32(pair),      // lui+addi → lui32
    // ... 共 20+ 种融合模式
  )
}
```

（参见 `src/main/scala/xiangshan/backend/decode/FusionDecoder.scala`第571-594行附近）

当某种融合匹配成功时，<code>**FusionDecodeReplace**</code> 结构携带需要替换的控制信号，更新到第一条指令上，同时将第二条指令标记为"清除"：

```scala
class FusionDecodeReplace(implicit p: Parameters) extends XSBundle {
  val fuType = Valid(FuType())         // 可选：替换功能单元类型
  val fuOpType = Valid(FuOpType())     // 可选：替换操作码
  val lsrc2 = Valid(UInt(...))         // 可选：替换第二源寄存器
  val src2Type = Valid(SrcType())      // 可选：替换第二源类型
  val selImm = Valid(SelImm())         // 可选：替换立即数选择
  val imm = Valid(UInt(32.W))          // 可选：替换立即数值
}
```

（参见 `src/main/scala/xiangshan/backend/decode/FusionDecoder.scala`第522-550行附近）

### 3.4.4 支持的融合案例速查表

| **融合模式** | **源指令对** | **融合结果** | **典型用途** |
| --- | --- | --- | --- |
| 零扩展字 | <code>**SLLI 32**</code>+<code>**SRLI 32**</code> | <code>**ADD.UW**</code>（zext.w） | 32→64 位零扩展 |
| 零扩展半字 | <code>**SLLI 48**</code>+<code>**SRLI 48**</code> | <code>**PACKW**</code>（zext.h） | 16→64 位零扩展 |
| 符号扩展半字 | <code>**SLLIW 16**</code>+<code>**SRAIW 16**</code> | <code>**SEXT.H**</code> | 16→32 位符号扩展 |
| 移位加 | <code>**SLLI n**</code>+<code>**ADD**</code> | <code>**SHnADD**</code>（n=1,2,3,4） | 地址计算 |
| 右移加 | <code>**SRLI n**</code>+<code>**ADD**</code> | <code>**SRnADD**</code>（n=29,30,31,32） | 高位提取+加法 |
| 奇数加 | <code>**ANDI 1**</code>+<code>**ADD/W**</code> | <code>**ODDADD/ODDADDW**</code> | 对齐计算 |
| 加法取字节 | <code>**ADDW**</code>+<code>**ANDI 0xFF**</code> | <code>**ADDWBYTE**</code> | 字节提取 |
| 加法取位 | <code>**ADD**</code>+<code>**ANDI 0x1**</code> | <code>**ADDWBIT**</code> | 位提取 |
| 逻辑取 LSB | 逻辑指令 + <code>**ANDI 1**</code> | <code>**logiclsb**</code> | 逻辑运算后取最低位 |
| 32位立即数 | <code>**LUI**</code>+<code>**ADDI/W**</code> | <code>**lui32add/lui32addw**</code> | 大立即数构造 |
| 7位乘法 | <code>**ANDI 127**</code>+<code>**MULW**</code> | <code>**mulw7**</code> | 小范围乘法优化 |

:::danger
新手提示：

不需要记住所有融合模式！重点理解 **"为什么融合"（减少后端压力）** 和 **"融合的条件"（相邻、有数据依赖、只改有限信号）**。具体的融合模式可以当作速查手册使用。

:::

***

## 3.5 Translate/Split 宏指令拆分 —— 一条变 N 条

### 3.5.1 为什么需要拆分？

RISC-V 中有些指令的语义**无法在一个微操作中完成**。最典型的就是**向量指令**——一条 <code>**VADD.VV**</code> 可能同时操作 1 组甚至 8 组向量寄存器（取决于 LMUL），但后端执行单元每次只能处理有限数量的元素。

:::danger
***比喻：****\*\* 一条向量指令就像一个"团购订单"——一份订单可能包含 8 组同样的商品，但仓库每次只能发一组。所以必须把一份订单\*\*****拆成 8 个子订单**\_\_**，逐个发货。***

:::

### 3.5.2 简单指令 vs 复杂指令

香山将指令分为两类，由不同的译码路径处理：

| **类型** | **映射关系** | **处理模块** | **例子** |
| --- | --- | --- | --- |
| **简单指令** | 1 条宏指令 → 1 条微操作 | <code>**DecodeUnit**</code>（简单译码器） | 标量 ADD、SUB、LW |
| **复杂指令** | 1 条宏指令 → N 条微操作 | <code>**DecodeUnitComp**</code>（复杂译码器） | 向量 VADD、vsetvli、VLSE8 |

判断标准由 <code>**uopSplitType**</code> 字段决定——如果它不等于 <code>**UopSplitType.X**</code>（即不是"无需拆分"），就是复杂指令。

### 3.5.3 两级译码架构

```plain
           ┌─────────────────────────────────────────────┐
           │            DecodeStage                       │
           │                                              │
IBuffer ──→│  decoders[0]  decoders[1]  ...  decoders[N]  │
           │      │            │                │         │
           │      ↓            ↓                ↓         │
           │  isComplex?   isComplex?      isComplex?     │
           │   │  No          │ Yes           │  No       │
           │   ↓              ↓               ↓          │
           │ 直接输出    ┌─────────────┐    直接输出      │
           │            │FusionDecoder│                  │
           │            │ 指令融合检测 │                  │
           │            └──────┬──────┘                  │
           │                   ↓                         │
           │            ┌──────────────┐                 │
           │            │DecodeUnitComp│ ← 复杂拆分状态机  │
           │            │ 逐拍输出 uop │                 │
           │            └──────┬───────┘                 │
           │                   ↓                         │
           │          finalDecodedInst                    │
           │          （复杂uop在前，简单uop在后）          │
           │                   ↓                         │
           │               → Rename                      │
           └─────────────────────────────────────────────┘

```

参见 `src/main/scala/xiangshan/backend/decode/ DecodeStage.scala` L113-L238

**核心思想：** 复杂译码器 <code>**DecodeUnitComp**</code> 的结果被排在输出序列的**前面**，简单译码器的结果排在**后面**。这就像超市收银——买了很多东西的顾客（复杂指令拆出的多个 uop）先过秤，买了一件的顾客（简单指令）紧随其后，保证程序顺序不乱。

### 3.5.4 拆分类型（UopSplitType）一览

| **UopSplitType** | **含义** | **典型指令** | **拆分数量** |
| --- | --- | --- | --- |
| <code>**X**</code> | 无需拆分 | 标量 ADD, LW | 1 |
| <code>**VSET**</code> | vset 配置指令 | vsetvli, vsetvl | 2（配置 + 写回） |
| <code>**VEC_VVV**</code> | 向量 OPIVV | VADD.VV | LMUL |
| <code>**VEC_VXV**</code> | 向量 OPIVX/OPIVI | VADD.VX, VADD.VI | LMUL |
| <code>**VEC_0XV**</code> | 向量（0号源特殊） | VMACC.VV | LMUL |
| <code>**VEC_US_LDST**</code> | 向量 unit-stride/strided load/store | VLE8.V, VLSE8.V | EMUL×(NF+1) |
| <code>**VEC_IX_LDST**</code> | 向量 indexed load/store | VLUXEI8.V | Max(LMUL,EMUL)×(NF+1) |
| <code>**VEC_RGATHER**</code> | vrgather | VRGATHER.VV | 按LMUL和SEW计算 |
| <code>**VEC_COMPRESS**</code> | vcompress | VCOMPRESS | 按LMUL计算 |
| <code>**VEC_SLIDE**</code> | vslide | VSLIDEUP | 按LMUL计算 |

### 3.5.5 UopInfoGen——计算"要拆成几个"

<code>**UopInfoGen**</code> 模块就像一个**计算器**——根据 <code>**uopSplitType**</code> 和当前 VType 配置（LMUL、SEW、EMUL），精确算出每条指令需要拆成几个微操作：

```scala
// 基本的向量运算拆分数量 = LMUL
val numOfWB = MuxLookup(typeOfSplit, 1.U)(Seq(
  UopSplitType.VEC_VVV -> lmul,     // LMUL=2 → 2个uop
  UopSplitType.VEC_VXV -> lmul,
  UopSplitType.VEC_0XV -> lmul,
  // ...
))
```

参见 UopInfoGen.scala L197-L200。

### 3.5.6 三种拆分场景详解

#### 场景一：Scalar + Scalar（标量拆分）

大部分标量指令**1:1 映射**，不拆分。极少数特殊标量指令会拆为 2 个 uop：

| **指令** | **拆分方式** | **原因** |
| --- | --- | --- |
| <code>**vsetvli**</code> | 2 uop：① 配置 VType ② 写回 VL 到 x 寄存器 | 需要同时完成"改配置"和"返回值"两件事 |
| <code>**vsetvl**</code> | 2 uop：同上 | 同上 |

```scala
case class VSET(vli: Boolean, vtypei: Boolean, fuOp: BitPat, ...)
extends XSDecodeBase {
  def generate() = XSDecode(..., uopSplitType = UopSplitType.VSET, ...).generate()
}
```

参见 VecDecoder.scala L155-L162。

***这就像去银行办业务——改密码和取钱虽然是同一趟来办，但得分两步操作。***

#### 场景二：Scalar + Vector（标量-向量混合拆分）

当向量指令的源操作数**包含标量寄存器**时，拆分策略需要额外处理标量数据的广播/搬运：

**OPIVX 类型**（如 <code>**VADD.VX**</code>，向量 + 标量）：

```scala
case class OPIVX(
  fu: FuType.OHType, fuOp: BitPat, ...
  uopSplitType: BitPat = UopSplitType.VEC_VXV,
  src1: BitPat = SrcType.xp,   // 标量源（来自 x 寄存器）
  src2: BitPat = SrcType.vp,   // 向量源（来自 v 寄存器）
) extends XSDecodeBase { ... }
```

参见 VecDecoder.scalaL44-L58。

* 每个 uop 都读取同一个标量寄存器，但操作不同的向量寄存器组
* 拆分数量 = LMUL

**OPIVI 类型**（如 <code>**VADD.VI**</code>，向量 + 立即数）：

* 与 OPIVX 类似，只是 src1 变成立即数
* 同样使用 <code>**UopSplitType.VEC_VXV**</code>

***比喻：标量-向量混合拆分就像"一个老师（标量）给多个班级（向量分组）上课"——老师只有一个，但每个班级都需要单独上一遍。***

#### 场景三：Vector + Vector（纯向量拆分）

这是最复杂的拆分场景。微操作数量取决于 **LMUL**（向量寄存器组长度倍率）和**指令类型**：

| **指令类型** | **LMUL=1** | **LMUL=2** | **LMUL=4** | **LMUL=8** |
| --- | --- | --- | --- | --- |
| VADD.VV（OPIVV） | 1 uop | 2 uop | 4 uop | 8 uop |
| VLE8.V（unit-stride load） | 1 | 2 | 4 | 8 |
| VRGATHER.VV | 1 | 4 | 16 | **64** |
| VCOMPRESS | 1 | 4 | 13 | 43 |
| VSLIDEUP | 1 | 3 | 10 | 36 |

参见 UopInfoGen.scalaL127-L145。

:::warning 注意\
vrgather 和 vcompress 这类指令的 uop 数量增长**非常快**（LMUL=8 时可达 64 个 uop），这是向量指令在乱序处理器中的主要性能瓶颈之一。\
:::

### 3.5.7 DecodeUnitComp 拆分状态机

复杂译码器使用状态机**逐拍输出**拆分后的微操作：

```scala
val s_idle :: s_active :: Nil = Enum(2)   // 两种状态：空闲 / 工作中
val state = RegInit(s_idle)
val numDecodedUop = RegInit(0.U)           // 已输出的 uop 计数
val uopRes = RegInit(0.U)                  // 剩余待输出 uop 数
```

参见 DecodeUnitComp.scalaL178-L183。

**工作流程：**

1. <code>**s_idle**</code>：等待复杂指令到来
2. 检测到 <code>**isComplex**</code> 信号 → 锁存指令，进入 <code>**s_active**</code>
3. <code>**s_active**</code>：每拍输出一个 uop，同时递增 <code>**numDecodedUop**</code>，递减 <code>**uopRes**</code>
4. 每个 uop 自动携带：
   * **firstUop / lastUop 标记**：标记序列的起止
   * **寄存器偏移**：根据 uop 索引计算 vs2/vd 的偏移（如 LMUL=2 时，第 0 个 uop 操作 v0~~v1，第 1 个操作 v2~~v3）
   * **vlsInstr 标记**：向量 load/store 的特殊处理
5. <code>**uopRes**</code> 归零 → 回到 <code>**s_idle**</code>

### 3.5.8 Segment 指令的拆分（进阶）

Segment load/store（NF > 0，即一次操作多个字段）的拆分更为复杂，uop 数量为 <code>**EMUL × (NF + 1)**</code>：

```scala
val isUsSegment = instFields.MOP === 0.U && nf =/= 0.U  // unit-stride segment
val isIxSegment = instFields.MOP(0) === 1.U && nf =/= 0.U  // indexed segment
val isSdSegment = instFields.MOP === "b10".U && nf =/= 0.U  // strided segment
```

参见 DecodeUnitComp.scalaL185-L187。

对于 indexed load/store，vs2 和 vd 可能有不同的 EMUL/LMUL，需要专门的**查找表**计算每个 uop 对应的寄存器偏移：

```scala
class indexedLSUopTable(uopIdx: Int) extends Module {
  def genCsBundle_VEC_INDEXED_LDST(lmul: Int, emul: Int, uopIdx: Int): (Int, Int)
  // 使用 QMCMinimizer 综合为硬件查找表
  val out = decoder(QMCMinimizer, src, TruthTable(...))
}
```

参见 DecodeUnitComp.scalaL38-L77。

:::tip 新手建议\
Segment 拆分是"进阶中的进阶"，现阶段只需理解"不同字段对应不同寄存器组偏移"这个概念即可。等熟悉了基本向量拆分流程后，再回头研究不迟。\
:::

***

## 3.6 译码模块源码导航

最后，给你一张"源码地图"，方便你按图索骥：

| **模块** | **源码路径** | **职责** |
| --- | --- | --- |
| **DecodeStage** | DecodeStage.scala | 译码流水线顶层，协调简单/复杂译码器和融合 |
| **DecodeUnit** | DecodeUnit.scala | 简单译码器，BitPat 查表 + 控制信号生成 |
| **DecodeUnitComp** | DecodeUnitComp.scala | 复杂译码器，向量指令拆分状态机 |
| **FusionDecoder** | FusionDecoder.scala | 指令融合检测器，20+ 种融合模式 |
| **VecDecoder** | VecDecoder.scala | 向量指令译码表（OPIVV/OPIVX/OPMVV 等） |
| **UopInfoGen** | UopInfoGen.scala | 微操作数量计算器 |
| **VTypeGen** | VTypeGen.scala | VType 状态维护模块 |
| **PreDecode** | PreDecode.scala | 前端预译码 |
| **PreDecodeInst** | predecode.scala | 预译码分支匹配表 |
| **Instructions** | Instructions.scala | 自定义扩展指令 BitPat 定义 |

***

## 3.7 学习路径建议

| **阶段** | **学习目标** | **建议阅读** |
| --- | --- | --- |
| 🟢 入门 | 理解译码的作用和整体架构 | 本文档 3.1~3.2 + DecodeUnit.scala 前 200 行 |
| 🔵 进阶 | 掌握 Fusion 和 Split 的原理 | 本文档 3.4~3.5 + FusionDecoder.scala + DecodeUnitComp.scala 前 200 行 |
| 🟣 精通 | 能修改/扩展译码逻辑 | 完整阅读上述源码 + VecDecoder.scala + UopInfoGen.scala |

***

## 3.8 总结

恭喜你走到了这里！让我们回顾一下本章的核心收获：

* 🧭 **译码的位置**：连接前端（取指）和后端（执行）的桥梁，负责将指令编码翻译为控制信号
* 📋 **Decode Information**：每条指令被翻译为包含 srcType、fuType、fuOpType 等十几个字段的控制信号，通过 BitPat 匹配表完成查表
* 🔍 **Pre-Decode**：前端轻量级预译码，只提取分支信息，帮助 BPU 尽早决策
* 🔗 **Fusion**：将两条有数据依赖的相邻指令融合为一条，减少后端资源消耗，提升性能
* ✂️ **Split**：将一条复杂指令（尤其是向量指令）拆分为多个微操作，通过简单/复杂两级译码器协作完成

**给新手的建议**：不要一开始就试图理解所有细节。先跑通整体流程，理解"译码在做什么"，再针对你关心的模块深入源码。遇到不懂的地方，对照本文档的"源码导航"去定位，比漫无目的地翻代码高效得多。

**下一步**，建议你前往 [<font style="color:rgb(0, 176, 170);">重命名与分派阶段</font>](https://zread.ai/OpenXiangShan/XiangShan/12-rename-and-dispatch-stage)，看看译码输出的微操作是如何被重命名和分派到执行单元的——那才是译码产出的"工艺卡片"真正被使用的阶段。


> 更新: 2026-05-29 16:47:09

## 验证特别注意

> 本节依据 `tools/verification-driver/skills` 中的 FSM、冲突、前向进展、索引/哈希、缓存结构、异常/虚拟化和性能瓶颈规则生成。每个期望必须以当前 `kunminghu-v2` 有效 Chisel 为准。

| Verification ID | 风险 / 不变量 | 定向激励 | 期望观察 | Checker / Coverage |
| --- | --- | --- | --- | --- |
| `F_HOLD_BACKPRESSURE` | Rename 反压时多路译码结果不得漂移或越过 | 令 `io.out.head.ready=0`，同时保持简单与复杂指令输入有效 | `readyCounter`、`complexValid`、输出 valid/payload 和输入 ready 按接受条件保持；证据 [backend/decode/DecodeStage.scala:94-150](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v2/src/main/scala/xiangshan/backend/decode/DecodeStage.scala#L94-L150) | Handshake checker；lane-order scoreboard |
| `DECODE_COMPLEX_EXPAND` | 复杂指令扩展的 uop 数量与顺序错误 | 覆盖不同 `complexNum`，前后夹简单指令 | 复杂 uop 在简单译码结果前输出，且不超过 Rename 可接收宽度；证据 [backend/decode/DecodeStage.scala:141-181](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v2/src/main/scala/xiangshan/backend/decode/DecodeStage.scala#L141-L181) | Expansion scoreboard；uop-count cross |
| `F_REQ_AND_FLUSH` | redirect 与复杂译码/vtype 更新竞争 | `decoderComp.io.in.fire` 同拍拉高 redirect | 错误路径不得更新 vtype，复杂译码状态与输出被杀死；证据 [backend/decode/DecodeStage.scala:156-176](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v2/src/main/scala/xiangshan/backend/decode/DecodeStage.scala#L156-L176) | Flush/FSM checker；vtype scoreboard |
| `DECODE_ILLEGAL_PRIORITY` | 非法/虚拟指令异常与既有异常优先级错误 | 构造非法编码、虚拟指令和前端异常组合 | `EX_II`/`EX_VI` 只落到对应指令，最老非法指令选择与代码一致；证据 [backend/decode/DecodeStage.scala:131-139](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v2/src/main/scala/xiangshan/backend/decode/DecodeStage.scala#L131-L139) | Architecture exception scoreboard |
| `C_MULTI_COMPLEX` | 同拍多条复杂指令只能选择一条处理 | 多个 lane 同时标记 `isComplex` | PriorityMux 只接受代码选中的最老候选，其余输入保持/重试且不丢失 | Arbiter checker；oldest-wins cover |
| `DECODE_DEFAULT_SAFE` | 未知编码默认控制信号形成幽灵写回或访存 | 随机保留/非法 opcode、funct 与扩展组合 | DecodeUnitComp 输出默认值、异常和功能单元选择安全；证据 [backend/decode/DecodeUnitComp.scala:108-220](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v2/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala#L108-L220) | Decode truth-table checker；illegal cross |
| `PB_RECOVERY_THROUGHPUT` | 复杂译码与反压解除后吞吐无法恢复 | 简单/复杂混合流饱和输入，周期性 redirect 和阻塞 Rename | 恢复后无重复/丢失 uop，并回到代码允许的持续译码带宽 | Performance checker；decode IPC/recovery latency |

### 通用判定原则

- `valid && !ready` 期间 payload 必须稳定；只有 `fire` 才能推进指针、状态或训练一次。
- flush/redirect/replay 的胜负关系必须按代码优先级检查；错误路径不得提交、写表、训练预测器或暴露异常/数据。
- 资源填满后必须验证可排空；重复冲突、retry 或 redirect 不得形成 deadlock/livelock，并检查低优先级旧请求是否饥饿。
- 环形指针必须覆盖最大值到零的 wrap；表索引必须构造 same-index/different-tag 和同拍 read/write 冲突组。
- 性能覆盖至少记录占用率、反压周期、redirect 恢复延迟、重试次数和恢复后的持续吞吐。
-->

# 1. Decode

## 3.1 Decode Information

If you are new to processor decode, terms such as "BitPat match table", "instruction fusion", and "vector micro-op splitting" may look intimidating. Decode has one basic purpose: translate a stream of zeros and ones into work instructions that the backend can understand. We will build the picture step by step.

:::info
After this section, you will be able to:

- Understand the position and role of the decode module in the XiangShan processor.
- Understand Decode Information: the control signals generated for an instruction.
- Understand Pre-Decode: how the frontend obtains branch information early.
- Understand Fusion: why and how two instructions can be combined into one.
- Read Translate/Split: how one vector instruction becomes N micro-ops.
- Follow a complete path from source code to practical verification.
:::

### 3.1.1 A Big-Picture View of the Decode Stage

Think of the processor as a modern factory:

:::info
- The **frontend (IFU and related units)** is the raw-material procurement department. It retrieves instructions from memory, supplies the production line, and establishes the overall pace and high-level workload.
- **Decode** is the translation and task-decomposition workshop. It translates the raw instruction, annotates the required hardware resources and operation, decomposes a macro-instruction into executable micro-ops, and sends them to backend units.
- The **backend (Rename -> Issue -> Execute)** is the main production line. It performs out-of-order scheduling, resource allocation, and execution according to those micro-ops.
:::

Without this translator, the backend would receive meaningless 0/1 bits. With decoding, each instruction can be scheduled and executed correctly.

### 3.1.2 Decode Pipeline Overview

XiangShan decode is not a single lookup table. It is a multi-stage, hierarchical pipeline:

~~~plain
+---------------- IFU (frontend) ----------------+
| PreDecode: lightweight branch extraction      |
|   - branch-type recognition                   |
|   - jump-offset calculation                   |
|   - RVC compressed-instruction test           |
|        |                                      |
|        +--> PreDecodeInfo --> BPU / FTQ       |
+-------------------+----------------------------+
                    |
+-------------------v DecodeStage ---------------+
| IBuffer -> decoders[0..N-1]                   |
|   lookup -> control signals + uopSplitType    |
|   simple instruction -> one uop                |
|   complex instruction -> further processing   |
|             |                                  |
|       +-----+---------------------+            |
|       |                           |            |
| FusionDecoder                DecodeUnitComp  |
| adjacent-pair matching       complex split FSM|
| two instructions -> one       one -> N uops   |
|       +-------------+-------------------------+
|                     |
|              finalDecodedInst -> Rename       |
| Helpers: UopInfoGen and VTypeGen              |
+------------------------------------------------+
~~~

**How to read the diagram:** frontend pre-decode is a scout that quickly extracts branch intelligence. Backend decode is the translator that generates complete control signals. Backend decode further separates simple scalar 1:1 mapping from complex processing (fusion and splitting).

:::color4
**Beginner tip:** Remember only this at first: **Decode = lookup-based translation + fusion optimization + vector splitting**. Do not start with every FSM detail; establish the overall picture first.
:::

## 3.2 Decode Information - What Does an Instruction Become?

### 3.2.1 Why Is Decode Information Needed?

A 32-bit RISC-V machine instruction is an encrypted, highly compressed work order. Its encoding describes a task, but backend functional units cannot execute the raw encoding directly.

Decode acts as both translator and task decomposer. It interprets the instruction and converts it into standard process cards, or decode information, that backend units can directly understand and execute.

The decode unit must answer the following questions:

:::info
- **Task identification:** Is this arithmetic, a memory access, or a control transfer?
- **Resource requirements:** Do the operands come from registers or from an immediate encoded in the instruction?
- **Dispatch:** Which functional unit should execute the task, and which operation should it perform? For a memory instruction, is it a load or a store?
- **Writeback destination:** Should the result be written to the integer or floating-point register file?
- **Special handling:** Is the instruction non-speculative, or must it flush the pipeline after execution?
:::

Without decode information, the backend is powerful hardware with no description of the work it must perform. Decode transforms each macro-instruction into clearly defined micro-ops and plans its resource, execution, and writeback path.

### 3.2.2 XSDecode - Complete Decode Control Signals

XiangShan uses the <code>XSDecode</code> class to describe an instruction's decode control signals. It is defined around lines 94-110 of <code>src/main/scala/xiangshan/backend/decode/DecodeUnit.scala</code>.

~~~scala
case class XSDecode(
  src1: BitPat, src2: BitPat, src3: BitPat,  // types of the three source operands
  fu: FuType.OHType, fuOp: BitPat,            // functional unit and operation code
  selImm: BitPat,                              // immediate selection
  uopSplitType: BitPat = UopSplitType.X,       // micro-op split type
  xWen: Boolean = false,                       // integer-register write enable
  fWen: Boolean = false,                       // floating-point-register write enable
  vWen: Boolean = false,                       // vector-register write enable
  mWen: Boolean = false,                       // mask-register write enable
  noSpec: Boolean = false,                     // must not execute speculatively
  blockBack: Boolean = false,                  // block following instructions
  flushPipe: Boolean = false,                  // flush the pipeline
  canRobCompress: Boolean = false,             // ROB compression is allowed
)
~~~

This is a process card: each field describes one part of the work assigned to the backend.

#### Core Fields of the Process Card

**1. Resources and operation**

- <code>src1</code>, <code>src2</code>, and <code>src3</code> identify source-operand types, such as an integer register (<code>Reg</code>), floating-point register (<code>FPReg</code>), or immediate (<code>Imm</code>).
- <code>fu</code> selects a functional unit, such as the integer ALU (<code>ALU</code>), multiplier/divider (<code>MulDiv</code>), or memory unit (<code>Mem</code>).
- <code>fuOp</code> selects the operation inside that functional unit, such as <code>ADD</code> or <code>LD</code>.
- <code>selImm</code> tells the hardware how to extract and extend an immediate from the instruction encoding.

**2. Writeback destination**

<code>xWen</code>, <code>fWen</code>, <code>vWen</code>, and <code>mWen</code> are write-enable signals for the integer, floating-point, vector, and mask register files. In the usual case, only the register file targeted by the instruction is enabled.

**3. Special pipeline controls**

- <code>noSpec</code> marks an instruction as non-speculative. It must wait until the processor knows that it will really execute.
- <code>blockBack</code> blocks issue of younger instructions and is typically used at a serialization point.
- <code>flushPipe</code> requests a pipeline flush after execution, for example after <code>fence.i</code>.
- <code>canRobCompress</code> marks an instruction that the reorder buffer (ROB) may compress under suitable conditions.

**4. Instruction complexity**

<code>uopSplitType</code> says whether and how a macro-instruction must be split into multiple micro-ops. A complex vector reduction, for example, may be represented by several simpler micro-ops.

| Category | Field | Type | Meaning |
| --- | --- | --- | --- |
| Input resources | <code>src1</code>/<code>src2</code>/<code>src3</code> | <code>BitPat</code> | Select integer-register, floating-point-register, vector, mask, or immediate sources. |
| Execution control | <code>fu</code> | <code>FuType.OHType</code> | Selects the functional unit. |
|  | <code>fuOp</code> | <code>BitPat</code> | Selects the operation performed by that unit. |
|  | <code>selImm</code> | <code>BitPat</code> | Selects and extends the immediate encoding. |
| Writeback | <code>xWen</code> | <code>Boolean</code> | Writes the result to the integer register file. |
|  | <code>fWen</code> | <code>Boolean</code> | Writes the result to the floating-point register file. |
|  | <code>vWen</code> | <code>Boolean</code> | Writes the result to the vector register file. |
|  | <code>mWen</code> | <code>Boolean</code> | Writes the result to the mask register file. |
| Pipeline behavior | <code>noSpec</code> | <code>Boolean</code> | Prevents speculative execution. |
|  | <code>blockBack</code> | <code>Boolean</code> | Blocks issue of younger instructions. |
|  | <code>flushPipe</code> | <code>Boolean</code> | Flushes the pipeline after execution. |
| Micro-op handling | <code>uopSplitType</code> | <code>BitPat</code> | Identifies the split procedure for a complex instruction. |
|  | <code>canRobCompress</code> | <code>Boolean</code> | Allows ROB compression when the instruction is eligible. |

### 3.2.3 Decode Table - Mapping Instruction Encodings to Control Signals

:::info
XiangShan uses a classic **BitPat match table**. The left side is an instruction encoding pattern; the right side is the corresponding <code>XSDecode</code> control-signal set.
:::

The decoder looks up a 32-bit instruction, finds the matching pattern, and emits the control signals needed by the backend.

~~~scala
object XDecode extends DecodeConstants {
  // Array of (instruction pattern -> control signal) pairs.
  val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
    // Example from the RV64I base instruction set.
    LW -> XSDecode(
      SrcType.reg,     // source 1: register
      SrcType.imm,     // source 2: immediate
      SrcType.X,       // source 3: unused
      FuType.ldu,      // load/store unit
      LSUOpType.lw,    // load word
      SelImm.IMM_I,    // I-type immediate
      xWen = true      // write back to the integer register file
    ),
    ADD -> XSDecode(
      SrcType.reg, SrcType.reg, SrcType.X, // both sources are registers
      FuType.alu,                          // arithmetic and logic unit
      ALUOpType.add,                       // addition
      SelImm.X,                            // no immediate
      xWen = true,
      canRobCompress = true                // allow ROB compression
    ),
    JAL -> XSDecode(
      SrcType.pc,      // source 1: program counter
      SrcType.imm,     // source 2: immediate
      SrcType.X,
      FuType.jmp,      // jump unit
      JumpOpType.jal,  // jump and link
      SelImm.IMM_UJ,   // UJ-type immediate
      xWen = true
    ),
    // The real table contains all supported instructions.
  )
}
~~~

Defined around lines 134-200 of <code>src/main/scala/xiangshan/backend/decode/DecodeUnit.scala</code>.

:::info
The lookup procedure is:

1. **Fetch:** obtain the 32-bit instruction encoding.
2. **Match:** compare it with every <code>BitPat</code> in <code>decodeArray</code>. A <code>0</code> or <code>1</code> is fixed; <code>?</code> is a wildcard.
3. **Output:** retrieve the <code>XSDecode</code> object associated with the matching pattern.
4. **Pass on:** send the functional-unit selection, operation type, write enables, and other signals to the next stage, such as Rename.
:::

The static table is the foundation of the ISA implementation: it maps software-visible instructions such as <code>ADD</code> to actions understood by hardware execution units.

### 3.2.4 Default Values - What Happens When There Is No Match?

If no legal entry matches an instruction encoding, the decoder selects <code>decodeDefault</code>. Its <code>selImm</code> is <code>INVALID_INSTR</code>, which triggers an illegal-instruction exception.

~~~scala
def decodeDefault: List[BitPat] =
  List(SrcType.X, SrcType.X, SrcType.X, FuType.X, FuOpType.X,
       N, N, N, N, N, N, UopSplitType.X, SelImm.INVALID_INSTR)
~~~

Defined around lines 50-61 of <code>src/main/scala/xiangshan/backend/decode/DecodeUnit.scala</code>. It is analogous to a translator encountering an unknown word: mark the instruction as an exception and let the architectural exception mechanism handle it.

### 3.2.5 Decode-Table Categories - Specialized Dictionaries

XiangShan groups instructions by ISA extension and maintains several specialized tables:

| Decode-table object | Coverage | Source |
| --- | --- | --- |
| <code>XDecode</code> | RV32/64I, M extension, and system instructions | <code>DecodeUnit.scala</code>, around L133 |
| <code>FDecode</code> | Floating-point F/D extensions | <code>DecodeUnit.scala</code> |
| <code>VecDecoder</code> | Vector V extension (OPIVV, OPIVX, OPMVV, and related forms) | <code>VecDecoder.scala</code>, around L186 |
| <code>BitmanipDecode</code> | B-extension bit-manipulation instructions | <code>DecodeUnit.scala</code> |
| <code>ScalarCryptoDecode</code> | Scalar cryptographic extensions | <code>DecodeUnit.scala</code> |

The tables are defined independently and merged by <code>DecodeUnit</code> for parallel matching.

## 3.3 Pre-Decode - The Frontend Scout

### 3.3.1 Why Is Pre-Decode Needed?

:::danger
If the backend already performs full decode, why does the frontend do it again?
:::

Time is performance. The branch prediction unit (BPU) must decide where to fetch next during the fetch stage. Waiting for backend decode would be too late. Pre-Decode therefore behaves like a scout: it does not translate everything, but quickly extracts the most important intelligence, namely branch information.

### 3.3.2 What Does Pre-Decode Extract?

Pre-Decode does not generate complete decode information. It creates a lightweight <code>PreDecodeInfo</code> summary for early handling of instructions that can change control flow.

| Extracted field | Role and meaning |
| --- | --- |
| <code>valid</code> | Instruction-valid flag. It indicates whether the bits form a legal instruction and can enable an early exception. |
| <code>isRVC</code> | Compressed-instruction flag. It distinguishes a standard 32-bit instruction from a 16-bit RISC-V compressed instruction and therefore affects the next PC increment (+4 or +2). |
| <code>brAttribute</code> | Branch attribute: non-branch, direct jump, indirect jump, or conditional branch. |
| <code>jumpOffset</code> | Jump offset extracted from an immediate-bearing jump or branch so that a potential target can be computed as <code>Target PC = Current PC + Offset</code>. |

### 3.3.3 Pre-Decode Branch Match Table

Pre-Decode must identify, quickly and accurately, instructions that may redirect control flow. XiangShan uses a predefined <code>brTable</code> for this purpose. It ignores detailed arithmetic semantics and answers one question: is this instruction a branch, and what kind?

~~~scala
object PreDecodeInst {
  // Array[(instruction pattern, branch-attribute list)].
  val brTable = Array(
    // Non-branch compressed instruction.
    C_EBREAK -> List(BranchAttribute.BranchType.None),

    // Compressed instructions.
    C_J      -> List(BranchAttribute.BranchType.Direct),
    C_JALR   -> List(BranchAttribute.BranchType.Indirect),
    C_BRANCH -> List(BranchAttribute.BranchType.Conditional),

    // Standard 32-bit instructions.
    JAL      -> List(BranchAttribute.BranchType.Direct),
    JALR     -> List(BranchAttribute.BranchType.Indirect),
    BRANCH   -> List(BranchAttribute.BranchType.Conditional)
  )
}
~~~

Defined around lines 22-43 of <code>src/main/scala/xiangshan/frontend/ifu/PreDecode.scala</code>.

| Branch attribute | Meaning | Typical instructions | Key follow-up action |
| --- | --- | --- | --- |
| <code>None</code> | Non-branch; execution continues in program order. | <code>EBREAK</code>, <code>ADD</code>, <code>LW</code> | Continue sequential fetch. |
| <code>Direct</code> | Unconditional direct jump; target is current PC plus the immediate offset. | <code>JAL</code>, <code>C.J</code> | Extract <code>jumpOffset</code> so the predictor can calculate the target directly. |
| <code>Indirect</code> | Indirect jump; target comes from a register and is known at run time. | <code>JALR</code>, <code>C.JALR</code> | The target is not available from the instruction alone. |
| <code>Conditional</code> | Conditional branch; direction depends on a condition result. | <code>BEQ</code>, <code>BNE</code>, <code>C.BEQZ</code> | Extract the offset; predict taken/not-taken direction and target. |

### 3.3.4 Pre-Decode versus Full Decode

| Dimension | Pre-Decode (frontend scout) | Decode (backend translator) |
| --- | --- | --- |
| Location | IFU fetch pipeline | Backend <code>DecodeStage</code> |
| Purpose | Extract branch information early and assist the BPU | Generate complete micro-op control signals |
| Output | Branch type, jump offset, and RVC flag | Fields such as <code>FuType</code>, <code>SrcType</code>, and <code>FuOpType</code> |
| Latency sensitivity | Very high; it affects fetch bandwidth | Moderate; it affects dispatch bandwidth |
| Scope | Selected jump and branch patterns | All instructions |

:::danger
Pre-Decode is like customs screening: it quickly checks whether someone needs special attention. Full Decode is like an immigration review: it examines the complete record before deciding what the instruction may do.
:::

***

## 3.4 Fusion - Two Instructions Become One

Instruction fusion is a microarchitectural optimization. Several adjacent machine instructions that jointly implement a clear semantic operation can be merged into one more complex internal micro-op during decode or a later stage. This reduces backend resource use and improves execution efficiency.

### 3.4.1 Why Is Fusion Needed?

Compilers follow the ISA and may express a simple high-level operation as several basic instructions. That is correct, but processing the pieces independently can be inefficient.

**Example: 32-bit zero extension**

~~~c
// Zero-extend a 32-bit value to 64 bits.
uint64_t zext(uint32_t x) { return (uint64_t)x; }
~~~

A compiler may emit:

~~~plain
slli r1, r0, 32  // move the low 32 bits into the high half
srli r1, r1, 32  // clear the high half and restore the low half
// Final effect: r1[31:0] = r0[31:0]; r1[63:32] = 0
~~~

The combined semantics are equivalent to <code>ADD.UW r1, r0, zero</code>, the <code>zext.w</code> pseudo-instruction. Without fusion, hardware still treats the pair as two independent tasks.

Fusing the pair provides these benefits:

| Resource or metric | Before fusion (2 instructions) | After fusion (1 micro-op) |
| --- | --- | --- |
| Issue-queue entries | 2 | 1 |
| ROB entries | 2 | 1 |
| Register reads and writes | Multiple reads and writes | One write |
| Backend resource use | High | Low |

:::danger
Fusion recognizes compiler-generated instruction pairs whose semantics can be combined, converts them into one more complex micro-op, and reduces backend pressure while improving throughput and energy efficiency.
:::

### 3.4.2 Fusion Principles

XiangShan fusion follows strict rules.

#### Principle 1: Fuse Only Adjacent Pairs

The detector examines only two adjacent instructions within the decode width and never crosses a boundary:

~~~scala
abstract class BaseFusionCase(pair: Seq[Valid[UInt]])(implicit p: Parameters) {
  require(pair.length == 2)
}
~~~

Defined around lines 31-33 of <code>src/main/scala/xiangshan/backend/decode/FusionDecoder.scala</code>. Two instructions separated by another instruction cannot form a fusion pair.

#### Principle 2: The Dependency Must Be Producer-Consumer

The result of the first instruction must feed an input of the second:

~~~scala
protected def withSameDest: Bool = instr1Rd === instr2Rd
def destToRs1: Bool = instr1Rd === instr2Rs1
protected def destToRs2: Bool = instr1Rd === instr2Rs2
~~~

Defined around lines 46-48 of <code>src/main/scala/xiangshan/backend/decode/FusionDecoder.scala</code>. The first instruction is the producer and the second is the consumer.

#### Principle 3: Fusion Changes Only a Limited Set of Signals

For timing and correctness, a replacement may modify only selected fields:

| Modifiable field | Meaning |
| --- | --- |
| <code>fuType</code> | Functional-unit type |
| <code>fuOpType</code> | Operation code |
| <code>src2Type</code> | Type of the second source |
| <code>selImm</code> / <code>imm</code> | Immediate selection and value |

See around lines 76-84 of <code>FusionDecoder.scala</code>.

#### Principle 4: Two Fusion Modes

| Mode | Description | Example |
| --- | --- | --- |
| Fuse into an existing instruction | Replace the first operation with the decode result of another legal instruction | <code>SLLI + ADD</code> -> <code>SH1ADD</code> |
| Fuse into a custom operation | Replace <code>fuOpType</code> with a XiangShan internal operation code | <code>SLLI32 + SRLI31</code> -> <code>szewl1</code> |

#### Principle 5: The Second Instruction Is Consumed

After fusion succeeds, the second instruction is removed from the output stream. The first instruction continues with the fused control signals.

### 3.4.3 How Fusion Works

<code>FusionDecoder</code> checks every adjacent pair in parallel. The order of <code>fusionList</code> determines priority:

~~~scala
class FusionDecoder(implicit p: Parameters) extends XSModule {
  val fusionList = Seq(
    new FusedAdduw(pair),  // slli32 + srli32 -> add.uw
    new FusedZexth(pair),   // slli48 + srli48 -> zext.h
    new FusedSexth(pair),   // slliw16 + sraiw16 -> sext.h
    new FusedSh1add(pair),  // slli1 + add -> sh1add
    new FusedSh2add(pair),  // slli2 + add -> sh2add
    new FusedSh3add(pair),  // slli3 + add -> sh3add
    new FusedLui32(pair)    // lui + addi -> lui32
    // More than 20 fusion patterns exist.
  )
}
~~~

See around lines 571-594 of <code>FusionDecoder.scala</code>.

When a pattern matches, <code>FusionDecodeReplace</code> carries the fields that must replace the first instruction and marks the second instruction for removal:

~~~scala
class FusionDecodeReplace(implicit p: Parameters) extends XSBundle {
  val fuType = Valid(FuType())
  val fuOpType = Valid(FuOpType())
  val lsrc2 = Valid(UInt(...))
  val src2Type = Valid(SrcType())
  val selImm = Valid(SelImm())
  val imm = Valid(UInt(32.W))
}
~~~

See around lines 522-550 of <code>FusionDecoder.scala</code>.

### 3.4.4 Supported Fusion Cases

| Fusion pattern | Source pair | Fused result | Typical use |
| --- | --- | --- | --- |
| Word zero extension | <code>SLLI 32</code> + <code>SRLI 32</code> | <code>ADD.UW</code> (<code>zext.w</code>) | Zero-extend 32 to 64 bits |
| Halfword zero extension | <code>SLLI 48</code> + <code>SRLI 48</code> | <code>PACKW</code> (<code>zext.h</code>) | Zero-extend 16 to 64 bits |
| Halfword sign extension | <code>SLLIW 16</code> + <code>SRAIW 16</code> | <code>SEXT.H</code> | Sign-extend 16 to 32 bits |
| Shift-add | <code>SLLI n</code> + <code>ADD</code> | <code>SHnADD</code> (n = 1, 2, 3, 4) | Address calculation |
| Shift-right-add | <code>SRLI n</code> + <code>ADD</code> | <code>SRnADD</code> (n = 29, 30, 31, 32) | High-bit extraction and addition |
| Odd add | <code>ANDI 1</code> + <code>ADD/W</code> | <code>ODDADD/ODDADDW</code> | Alignment calculation |
| Add and extract byte | <code>ADDW</code> + <code>ANDI 0xFF</code> | <code>ADDWBYTE</code> | Byte extraction |
| Add and extract bit | <code>ADD</code> + <code>ANDI 0x1</code> | <code>ADDWBIT</code> | Bit extraction |
| Logical LSB extraction | logical instruction + <code>ANDI 1</code> | <code>logiclsb</code> | Extract the lowest bit after a logical operation |
| 32-bit immediate construction | <code>LUI</code> + <code>ADDI/W</code> | <code>lui32add/lui32addw</code> | Construct a large immediate |
| 7-bit multiplication | <code>ANDI 127</code> + <code>MULW</code> | <code>mulw7</code> | Small-range multiplication |

:::danger
You do not need to memorize every fusion pattern. Focus on why fusion reduces backend pressure and on its conditions: adjacency, a producer-consumer dependency, and a limited replacement set.
:::

***

## 3.5 Translate/Split - One Macro-Instruction Becomes N Micro-Ops

### 3.5.1 Why Is Splitting Needed?

Some RISC-V instructions cannot be completed by one micro-op. A vector instruction such as <code>VADD.VV</code> may operate on one or as many as eight vector-register groups, depending on LMUL, while the backend unit can process only a limited amount at a time.

A vector instruction is therefore like a bulk order: the order may contain eight groups of goods, but the warehouse ships one group at a time. The macro-instruction must be split into sub-orders and issued separately.

### 3.5.2 Simple versus Complex Instructions

XiangShan uses two decode paths:

| Type | Mapping | Processing module | Example |
| --- | --- | --- | --- |
| Simple instruction | 1 macro-instruction -> 1 micro-op | <code>DecodeUnit</code> | Scalar ADD, SUB, LW |
| Complex instruction | 1 macro-instruction -> N micro-ops | <code>DecodeUnitComp</code> | Vector VADD, <code>vsetvli</code>, VLSE8 |

If <code>uopSplitType</code> is not <code>UopSplitType.X</code>, the instruction requires the complex path.

### 3.5.3 Two-Level Decode Architecture

~~~plain
+------------------------- DecodeStage --------------------------+
| IBuffer -> decoders[0] ... decoders[N]                       |
|             |                 |                                |
|          simple            complex                            |
|        direct uop       FusionDecoder                         |
|                              |                                |
|                        DecodeUnitComp                         |
|                        one uop per cycle                      |
|                              |                                |
|                    finalDecodedInst -> Rename                 |
|                    complex uops precede simple uops            |
+---------------------------------------------------------------+
~~~

See <code>src/main/scala/xiangshan/backend/decode/DecodeStage.scala</code>, lines 113-238.

The complex decoder's results are placed at the front of the output sequence, followed by simple-decoder results. This preserves program order while allowing one complex instruction to expand into several uops.

### 3.5.4 UopSplitType at a Glance

| UopSplitType | Meaning | Typical instruction | Split count |
| --- | --- | --- | --- |
| <code>X</code> | No split | Scalar ADD, LW | 1 |
| <code>VSET</code> | vset configuration instruction | <code>vsetvli</code>, <code>vsetvl</code> | 2 (configuration + writeback) |
| <code>VEC_VVV</code> | Vector OPIVV | <code>VADD.VV</code> | LMUL |
| <code>VEC_VXV</code> | Vector OPIVX/OPIVI | <code>VADD.VX</code>, <code>VADD.VI</code> | LMUL |
| <code>VEC_0XV</code> | Vector form with special source 0 | <code>VMACC.VV</code> | LMUL |
| <code>VEC_US_LDST</code> | Unit-stride or strided vector load/store | <code>VLE8.V</code>, <code>VLSE8.V</code> | EMUL x (NF + 1) |
| <code>VEC_IX_LDST</code> | Indexed vector load/store | <code>VLUXEI8.V</code> | max(LMUL, EMUL) x (NF + 1) |
| <code>VEC_RGATHER</code> | <code>vrgather</code> | <code>VRGATHER.VV</code> | Computed from LMUL and SEW |
| <code>VEC_COMPRESS</code> | <code>vcompress</code> | <code>VCOMPRESS</code> | Computed from LMUL |
| <code>VEC_SLIDE</code> | <code>vslide</code> | <code>VSLIDEUP</code> | Computed from LMUL |

### 3.5.5 UopInfoGen - Computing the Split Count

<code>UopInfoGen</code> computes the number of uops from <code>uopSplitType</code> and the current VType configuration (LMUL, SEW, and EMUL):

~~~scala
// For basic vector operations, the split count is LMUL.
val numOfWB = MuxLookup(typeOfSplit, 1.U)(Seq(
  UopSplitType.VEC_VVV -> lmul,
  UopSplitType.VEC_VXV -> lmul,
  UopSplitType.VEC_0XV -> lmul
  // ...
))
~~~

See <code>UopInfoGen.scala</code>, lines 197-200.

### 3.5.6 Three Splitting Scenarios

#### Scenario 1: Scalar + Scalar

Most scalar instructions map 1:1. A few special scalar instructions expand to two uops:

| Instruction | Split | Reason |
| --- | --- | --- |
| <code>vsetvli</code> | 2 uops: configure VType, then write VL to an x register | It changes configuration and returns a value. |
| <code>vsetvl</code> | 2 uops: same as above | Same reason. |

~~~scala
case class VSET(vli: Boolean, vtypei: Boolean, fuOp: BitPat, ...)
    extends XSDecodeBase {
  def generate() =
    XSDecode(..., uopSplitType = UopSplitType.VSET, ...).generate()
}
~~~

See <code>VecDecoder.scala</code>, lines 155-162.

#### Scenario 2: Scalar + Vector

When a vector instruction has a scalar source, each split uop must reuse the scalar value while selecting a different vector-register group.

~~~scala
case class OPIVX(
  fu: FuType.OHType, fuOp: BitPat, ...
  uopSplitType: BitPat = UopSplitType.VEC_VXV,
  src1: BitPat = SrcType.xp,   // scalar source from an x register
  src2: BitPat = SrcType.vp    // vector source from a v register
) extends XSDecodeBase { ... }
~~~

See <code>VecDecoder.scala</code>, lines 44-58. Every uop reads the same scalar register but operates on a different vector group; the split count is LMUL. OPIVI uses an immediate instead of <code>src1</code> and uses the same split type.

#### Scenario 3: Vector + Vector

The most complex case depends on LMUL and the instruction class:

| Instruction | LMUL=1 | LMUL=2 | LMUL=4 | LMUL=8 |
| --- | --- | --- | --- | --- |
| <code>VADD.VV</code> (OPIVV) | 1 uop | 2 uops | 4 uops | 8 uops |
| <code>VLE8.V</code> (unit-stride load) | 1 | 2 | 4 | 8 |
| <code>VRGATHER.VV</code> | 1 | 4 | 16 | 64 |
| <code>VCOMPRESS</code> | 1 | 4 | 13 | 43 |
| <code>VSLIDEUP</code> | 1 | 3 | 10 | 36 |

See <code>UopInfoGen.scala</code>, lines 127-145. The count for operations such as <code>vrgather</code> and <code>vcompress</code> grows rapidly, reaching 64 uops at LMUL=8 and becoming a significant vector-performance bottleneck.

### 3.5.7 DecodeUnitComp Split State Machine

The complex decoder uses a state machine to emit one split uop per cycle:

~~~scala
val s_idle :: s_active :: Nil = Enum(2)
val state = RegInit(s_idle)
val numDecodedUop = RegInit(0.U)  // number of uops already emitted
val uopRes = RegInit(0.U)         // number of uops still to emit
~~~

See <code>DecodeUnitComp.scala</code>, lines 178-183.

The workflow is:

1. <code>s_idle</code> waits for a complex instruction.
2. When <code>isComplex</code> is detected, the instruction is latched and the state changes to <code>s_active</code>.
3. In <code>s_active</code>, one uop is emitted per cycle; <code>numDecodedUop</code> increments and <code>uopRes</code> decrements.
4. Every uop carries <code>firstUop</code>/<code>lastUop</code> markers, register offsets derived from the uop index, and the <code>vlsInstr</code> marker for vector load/store handling.
5. When <code>uopRes</code> reaches zero, the state returns to <code>s_idle</code>.

### 3.5.8 Splitting Segment Instructions (Advanced)

For a segment load/store (NF > 0), one instruction accesses multiple fields and the split count is <code>EMUL x (NF + 1)</code>:

~~~scala
val isUsSegment = instFields.MOP === 0.U && nf =/= 0.U
val isIxSegment = instFields.MOP(0) === 1.U && nf =/= 0.U
val isSdSegment = instFields.MOP === "b10".U && nf =/= 0.U
~~~

See <code>DecodeUnitComp.scala</code>, lines 185-187.

For indexed load/store, <code>vs2</code> and <code>vd</code> can have different EMUL/LMUL values. A dedicated lookup table computes the register offset for each uop:

~~~scala
class indexedLSUopTable(uopIdx: Int) extends Module {
  def genCsBundle_VEC_INDEXED_LDST(
    lmul: Int, emul: Int, uopIdx: Int
  ): (Int, Int)
  // QMCMinimizer synthesizes the hardware lookup table.
  val out = decoder(QMCMinimizer, src, TruthTable(...))
}
~~~

See <code>DecodeUnitComp.scala</code>, lines 38-77. Segment splitting is advanced; first understand that different fields map to different register-group offsets, then study the detailed table.

***

## 3.6 Decode-Module Source Map

| Module | Source path | Responsibility |
| --- | --- | --- |
| <code>DecodeStage</code> | <code>DecodeStage.scala</code> | Top-level decode pipeline; coordinates simple/complex decode and fusion. |
| <code>DecodeUnit</code> | <code>DecodeUnit.scala</code> | Simple decoder; BitPat lookup and control-signal generation. |
| <code>DecodeUnitComp</code> | <code>DecodeUnitComp.scala</code> | Complex decoder; vector-instruction split FSM. |
| <code>FusionDecoder</code> | <code>FusionDecoder.scala</code> | Fusion detector with more than 20 patterns. |
| <code>VecDecoder</code> | <code>VecDecoder.scala</code> | Vector decode tables, including OPIVV, OPIVX, and OPMVV. |
| <code>UopInfoGen</code> | <code>UopInfoGen.scala</code> | Micro-op count calculator. |
| <code>VTypeGen</code> | <code>VTypeGen.scala</code> | VType state-maintenance module. |
| <code>PreDecode</code> | <code>PreDecode.scala</code> | Frontend pre-decode. |
| <code>PreDecodeInst</code> | <code>predecode.scala</code> | Pre-decode branch match table. |
| <code>Instructions</code> | <code>Instructions.scala</code> | BitPat definitions for custom extension instructions. |

## 3.7 Suggested Learning Path

| Stage | Learning goal | Suggested reading |
| --- | --- | --- |
| Beginner | Understand the role and overall architecture of decode. | Sections 3.1-3.2 and the first 200 lines of <code>DecodeUnit.scala</code>. |
| Intermediate | Understand Fusion and Split. | Sections 3.4-3.5, <code>FusionDecoder.scala</code>, and the first 200 lines of <code>DecodeUnitComp.scala</code>. |
| Advanced | Modify or extend decode logic. | The complete source map, plus <code>VecDecoder.scala</code> and <code>UopInfoGen.scala</code>. |

## 3.8 Summary

The key points are:

- **Position:** Decode bridges frontend fetch and backend execution by translating instruction encodings into control signals.
- **Decode Information:** A BitPat match table produces fields such as <code>srcType</code>, <code>fuType</code>, and <code>fuOpType</code>.
- **Pre-Decode:** A lightweight frontend pass extracts branch information so the BPU can decide early.
- **Fusion:** Two adjacent, data-dependent instructions become one micro-op, reducing backend resource use.
- **Split:** A complex instruction, especially a vector instruction, becomes multiple micro-ops through cooperation between simple and complex decoders.

Do not try to understand every detail at once. First follow the overall flow and understand what decode does; then use the source map to inspect the modules that matter to you.

Next, read the [Rename and Dispatch Stage](https://zread.ai/OpenXiangShan/XiangShan/12-rename-and-dispatch-stage) to see how decoded micro-ops are renamed and dispatched to execution units.

> Updated: 2026-05-29 16:47:09

## Verification Notes

This section follows the FSM, conflict, forward-progress, index/hash, cache-structure, exception/virtualization, and performance-bottleneck rules in <code>tools/verification-driver/skills</code>. Every expectation must be based on valid Chisel for <code>kunminghu-v2</code>.

| Verification ID | Risk / invariant | Directed stimulus | Expected observation | Checker / coverage |
| --- | --- | --- | --- | --- |
| <code>F_HOLD_BACKPRESSURE</code> | Decode results must not drift or pass through while Rename applies backpressure. | Set <code>io.out.head.ready=0</code> while simple and complex inputs remain valid. | <code>readyCounter</code>, <code>complexValid</code>, output valid/payload, and input ready remain stable under the acceptance rules; evidence [DecodeStage.scala:94-150](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v2/src/main/scala/xiangshan/backend/decode/DecodeStage.scala#L94-L150). | Handshake checker; lane-order scoreboard |
| <code>DECODE_COMPLEX_EXPAND</code> | Complex expansion can produce the wrong uop count or order. | Cover several <code>complexNum</code> values with simple instructions before and after. | Complex uops appear before simple results and do not exceed Rename's receive width; evidence [DecodeStage.scala:141-181](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v2/src/main/scala/xiangshan/backend/decode/DecodeStage.scala#L141-L181). | Expansion scoreboard; uop-count cross |
| <code>F_REQ_AND_FLUSH</code> | Redirect can race with complex decode and VType update. | Assert redirect in the same cycle as <code>decoderComp.io.in.fire</code>. | The wrong path must not update VType; complex-decode state and output are killed; evidence [DecodeStage.scala:156-176](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v2/src/main/scala/xiangshan/backend/decode/DecodeStage.scala#L156-L176). | Flush/FSM checker; VType scoreboard |
| <code>DECODE_ILLEGAL_PRIORITY</code> | Illegal/virtual-instruction exceptions may receive the wrong priority. | Combine illegal encodings, virtual instructions, and frontend exceptions. | <code>EX_II</code>/<code>EX_VI</code> apply only to the corresponding instruction and the oldest illegal instruction is selected as required by the code; evidence [DecodeStage.scala:131-139](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v2/src/main/scala/xiangshan/backend/decode/DecodeStage.scala#L131-L139). | Architectural exception scoreboard |
| <code>C_MULTI_COMPLEX</code> | At most one complex instruction may be processed in a cycle. | Mark multiple lanes <code>isComplex</code> simultaneously. | <code>PriorityMux</code> accepts the oldest candidate selected by the code; other inputs are held or retried without loss. | Arbiter checker; oldest-wins coverage |
| <code>DECODE_DEFAULT_SAFE</code> | Unknown encodings must not create ghost writeback or memory access. | Randomize reserved/illegal opcodes, funct fields, and extension combinations. | <code>DecodeUnitComp</code> emits safe defaults, exceptions, and functional-unit selections; evidence [DecodeUnitComp.scala:108-220](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v2/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala#L108-L220). | Decode truth-table checker; illegal cross |
| <code>PB_RECOVERY_THROUGHPUT</code> | Throughput may fail to recover after complex decode or backpressure is released. | Saturate a mixed simple/complex stream with periodic redirects and Rename blocking. | After recovery, no uop is duplicated or lost and sustained decode bandwidth returns to the level allowed by the implementation. | Performance checker; decode IPC and recovery-latency coverage |

### General Evaluation Principles

- During <code>valid && !ready</code>, the payload must remain stable; only <code>fire</code> may advance a pointer or state or perform one training update.
- Check flush, redirect, and replay precedence according to the implementation. The wrong path must not commit, write a table, train a predictor, or expose an exception or data.
- After a resource fills, verify that it can drain. Repeated conflicts, retries, or redirects must not create deadlock or livelock; check starvation of older low-priority requests.
- Circular pointers must cover wraparound from the maximum value to zero. Table tests must include same-index/different-tag and same-cycle read/write conflicts.
- At minimum, record occupancy, backpressure cycles, redirect recovery latency, retry count, and sustained throughput after recovery.
