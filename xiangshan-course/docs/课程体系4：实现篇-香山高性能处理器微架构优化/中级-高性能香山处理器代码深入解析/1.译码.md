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
