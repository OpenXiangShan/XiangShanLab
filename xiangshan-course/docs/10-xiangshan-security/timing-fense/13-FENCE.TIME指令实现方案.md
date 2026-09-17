# FENCE.TIME 的 BPU 子集实验实现需求

> 本文档记录 `FENCE.TIME` 针对 BPU 的子集实验实现。其顺序执行和指令流重取机制参考 `FENCE.I`，但 Fence 功能单元中的实际执行任务与 `FENCE.I` 明确区分。本文不宣称实现完整的 `FENCE.TIME` 微架构时序隔离语义。
>
> 本文的全部 FENCE.TIME 实验硬件复用 [00-BPU 刷新机制编译开关](00-BPU刷新机制编译开关实现方案.md) 定义的 elaboration-time 开关 `HasBpuFlush`，不新增 `HasFenceTime`。`HasBpuFlush=false` 时，FENCE.TIME 译码项、Fence FU 专用执行路径、跨模块请求端口与寄存器均不生成，该指令编码按非法指令处理。

## 1. 实现目标

`FENCE.TIME` 是用于本地 hart 微架构时序状态隔离的屏障指令。本期仅实现其 BPU 子集，且只覆盖 `AS`、`SD`、`VM` 切换。以下功能目标均以 `HasBpuFlush=true` 为前提：

1. 按指定编码识别并译码 `FENCE.TIME`；
2. 实现与 `FENCE.I` 一致的顺序执行约束；
3. 当 `AS`、`SD`、`VM` 中任一标志置位时，由 Fence 功能单元向 BPU 发送刷新请求；
4. 指令完成并到达 ROB 头部后产生 redirect，从 `FENCE.TIME` 的下一条指令重新取指；
5. 对满足刷新条件的指令，使用当前 `timing-fense` 分支已实现的 BPU 刷新协议完成实际刷新。

`HasBpuFlush=true` 时，`FENCE.TIME` 不写通用、浮点或向量寄存器，不修改内存。它在**架构状态上等效于 NOP**；当 `AS`、`SD`、`VM` 中任一标志置位时，它会改变 BPU 微架构状态。

### 1.1 编译开关边界

`HasBpuFlush` 同时决定 BPU 刷新机制和本文 FENCE.TIME BPU 子集是否存在：

| `HasBpuFlush` | FENCE.TIME 译码 | Fence FU 专用路径 | `bpuFlush` 通路 | 指令行为 |
|:---:|---|---|---|---|
| `true` | 生成 | 生成 | 生成 | 按本文实现 BPU 子集 |
| `false` | 不生成 | 不生成 | 不生成 | 该编码未匹配译码表，按非法指令处理 |

`Instructions.FENCE_TIME` 和 `FenceOpType.fencetime` 是 Scala 端的编码常量，本身不会生成寄存器、端口或组合逻辑，因此可保留无条件定义；它们的所有硬件消费点必须由 `HasBpuFlush` 守卫。具体编码和验收要求见第 7 章。

### 1.2 威胁模型边界

本期防御范围仅包含由 `AS`、`SD` 或 `VM` 变化引起的跨域 BPU 状态残留。单纯的 U/S/M 特权级切换不在本期威胁模型内；本实现不提供、也不声明跨特权级 BPU 时序隔离保证。因此，仅 `Priv` 置位时不触发 BPU 刷新，这是本期范围限定，而非基于当前 BPU 已按特权级完成硬件分区的结论。

## 2. 指令定义、译码与发射前通路

### 2.1 指令格式与定义

`FENCE.TIME` 的指令格式、字段布局、编码要求以及 `VM/SD/AS/Priv` 标志位定义，参见 [《FENCE 指令》](../../SupportingDocument/FENCE指令.md) 第 3.3、3.4 节。本文不再重复描述，只说明 XiangShan 中对应的 RTL 修改。

`FENCE.TIME` 与 `FENCE.I` 同属 `MISC-MEM` 指令，应加入与 `FENCE_I` 相同的指令定义表。按照官方编码，仅 `inst[23:20]` 的 `VM/SD/AS/Priv` 四个标志位可变；`inst[31:24]`、`rs1` 和 `rd` 均固定为 0，`funct3=011`，`opcode=0001111`：

```scala
// rocket-chip/src/main/scala/rocket/Instructions.scala
object Instructions {
  // existing definitions
  def FENCE      = BitPat("b?????????????????000?????0001111")
  def FENCE_I    = BitPat("b?????????????????001?????0001111")
  def FENCE_TIME = BitPat("b00000000????00000011000000001111") // new i
}
```

> [!Important]
> `FENCE_TIME` 的 `BitPat` 要求 `inst[31:24]`、`rs1` 和 `rd` 均为 0，并检查 `funct3=011` 和 `opcode=0001111`。只有 `inst[23:20]` 的四个标志位使用通配符，从高位到低位依次为 `VM/SD/AS/Priv`。
>
> 本期不额外增加 `HasFenceTime`；FENCE.TIME 译码表项复用 `HasBpuFlush` 进行 elaboration-time 裁剪。开关打开时不额外增加特权级非法指令判断；开关关闭时因译码项不存在，该编码自然落入现有非法指令路径。

> [!Note]
> 本项目当前直接在 `rocket-chip` 子仓库的 `Instructions.scala` 中增加 `FENCE_TIME` 定义，并已将该修改提交、推送至远程 `timing-fense` 分支。XiangShan 主仓库通过更新 `rocket-chip` 子模块的 commit ID 记录并共享该修改。
>
> 由于**该文件标注为由 `parse_opcodes` 自动生成**，后续若重新生成指令表，需要确认生成结果仍包含 `FENCE_TIME` 定义。

### 2.2 译码修改

译码时应为 `FENCE.TIME` 分配独立的 Fence FU 操作类型。`b10101` 当前未与其他 `FenceOpType` 编码冲突：

```scala
// src/main/scala/xiangshan/package.scala
object FenceOpType {
  // existing definitions ...
  def fencetime = "b10101".U
}
```

`FENCE.TIME` 大部分复用 `FENCE.I` 的译码控制，但必须使用独立的 Fence FU 操作类型，并选择 I-Type 立即数以传递 `inst[31:20]`。具体要求如下：

- `FenceOpType.fencetime` 用于 Fence FU 在发射后选择 FENCE.TIME 专用执行路径；
- `SelImm.IMM_I` 将完整的 `inst[31:20]` 提取并送入现有立即数通路，其中 `inst[23:20]` 到达 Fence FU 后对应 `uop.data.imm(3,0)`；
- 其余字段与 `FENCE.I` 保持一致。

FENCE.TIME 表项必须从无条件的 `XDecode.table` 中拆出，并仿照现有可选译码表在 `DecodeUnit` 中按参数追加：

```scala
// src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
object FenceTimeDecode extends DecodeConstants {
  val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
    FENCE_TIME -> XSDecode(SrcType.pc, SrcType.imm, SrcType.X, FuType.fence,
      FenceOpType.fencetime, SelImm.IMM_I, noSpec = T, blockBack = T, flushPipe = T),
  )
}

class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstants {
  val decode_table = XDecode.table ++
    // existing decode tables ...
    (if (HasBpuFlush) FenceTimeDecode.table else Array.empty[(BitPat, List[BitPat])])
}
```

`FENCE_I` 和 `FENCE` 仍保留在无条件的基础译码表中，其表项不受 `HasBpuFlush` 影响。

### 2.3 发射前立即数传递通路

现有 FENCE.I 在 Decode 阶段被译码为 `FuType.fence` 和 `FenceOpType.fencei`，其立即数类型为 `SelImm.X`。Rename 阶段会对所有 `FuType.fence` 指令重写 `uop.imm`，将指令中的 `rs2` 和 `rs1` 编号打包为 `Cat(lsrc(1), lsrc(0))`。该字段随后经过 `DynInst`、Issue Queue 和 `ExuInput` 传递到 Fence FU。FENCE.I 的执行由 `FenceOpType.fencei` 选择，不需要从 `uop.imm` 中读取指令立即数。

FENCE.TIME 复用同一条 `imm` 字段传递通路，但需要保留指令中的 4 bit 标志位。为此，Decode 应选择 `SelImm.IMM_I`，将 `inst[31:20]` 写入 `uop.imm[11:0]`；`FenceCfg` 应显式声明 I-Type 立即数需求；Rename 则应在处理 `FenceOpType.fencetime` 时跳过现有的 `lsrc(1/0)` 覆盖逻辑。这样，`inst[31:20]` 可沿现有 `DynInst` → Issue Queue → `ExuInput` 通路到达 Fence FU，其中 `uop.data.imm(3,0)` 对应 `inst[23:20]`，不需要增加专用 Bundle 字段。

示例修改如下：

```scala
// src/main/scala/xiangshan/backend/fu/FuConfig.scala
val FenceCfg: FuConfig = FuConfig(
  // existing parameters ...
  immType = Set(Imm_I()),
)
```

```scala
// src/main/scala/xiangshan/backend/rename/Rename.scala
when (
  io.out(i).bits.fuType === FuType.fence.U &&
  (if (HasBpuFlush) io.out(i).bits.fuOpType =/= FenceOpType.fencetime else true.B)
) {
  io.out(i).bits.imm := Cat(io.in(i).bits.lsrc(1), io.in(i).bits.lsrc(0))
}
```

`FenceCfg.immType` 是 Issue/EXU 的共享立即数能力描述，不是 FENCE.TIME 专用寄存器或端口；可保留 `Imm_I()` 声明。关闭配置中 FENCE.TIME 译码项不生成，Rename 表达式必须在 Scala `if` 的关闭分支恢复原始 Fence 立即数覆盖条件。若生成 RTL 对比显示 `Imm_I()` 仍导致基线之外的硬件，则必须进一步将该能力参数化，不得以共享描述为由豁免结构零增量验收。

---

## 3. 顺序执行约束

`HasBpuFlush=true` 时，`FENCE.TIME` 采用与 `FENCE.I` 一致的后端顺序化控制：

| 译码控制                          | 作用                                             |
| --------------------------------- | ------------------------------------------------ |
| `FuType.fence`                  | 将指令路由到 Fence 功能单元                      |
| `noSpec` / `waitForward`      | 等待更老指令离开 ROB，禁止推测越过执行           |
| `blockBack` / `blockBackward` | 阻止更年轻指令越过该屏障进入 ROB                 |
| `flushPipe`                     | 完成并到达 ROB 头部后产生`flushAfter` redirect |

正常执行顺序为：

```text

更老指令全部离开 ROB
        → FENCE.TIME 单独进入 ROB 并发射到 Fence FU
        → Fence FU 检查 AS/SD/VM 标志
            ├─ 任一置位：发出 BPU phase-1 flush 请求，使 BPU 进入 waiting 状态
            └─ 均未置位（包括仅 Priv 置位）：不发出 BPU phase-1 flush 请求
        → Fence FU 报告执行完成
        → ROB 根据完成结果中的 flushPipe 产生 flushAfter redirect
        → FENCE.TIME 随后提交并离开 ROB，解除 blockBackward；在此之前更年轻指令不能进入 ROB
        → redirect 到达前端和 BPU：触发 BPU phase-2 刷新，并清除前端已有的年轻指令
        → 前端从 FENCE.TIME 的顺序下一 PC 重新取指
        → 若触发了刷新，BPU 与后续指令执行并行地保持 bpuFlushing，直至所有参与模块的聚合 resetDone 置位
```

---

## 4. Fence 功能单元的修改

本章描述的 FENCE.TIME 专用逻辑均位于 `HasBpuFlush=true` 分支；开关关闭时 Fence FU 恢复为引入本功能前的状态机与接口。

### 4.1 FENCE.TIME 路径

`FENCE.TIME` 进入 Fence 功能单元后，Fence 单元需完成以下任务：

1. 接收并锁存 `FENCE.TIME` 微操作；
2. 从锁存微操作的立即数字段中取得并保留 `VM/SD/AS/Priv` 标志；
3. 识别独立的 `FenceOpType.fencetime`；
4. 进入专用的 BPU 刷新请求状态；
5. 当 `AS`、`SD`、`VM` 中任一标志置位时，向 BPU 发出 phase-1 flush 请求，使 BPU 进入 `s_waiting`；
6. 完成请求发送后，通过 Fence FU 写回/完成通路拉高 `io.out.valid`；
7. 依靠该微操作的 `flushPipe` 属性，在 ROB 头部产生 redirect。

Fence 单元负责的是“发送 BPU 刷新请求”和“报告指令执行完成”。**Fence 单元不直接生成前端 redirect**，redirect 由 ROB 在指令到达头部时精确产生。设计上，FENCE.I 与 FENCE.TIME 在进入 Fence FU 后采用两条独立路径：

- FENCE.I 的 `s_wait → s_icache`、`sbuffer` 和 `fencei` 逻辑全部保持不变。
- Fence FU 接收 FENCE.TIME 时，在入口处根据 `FenceOpType.fencetime` 直接转入新增的 `s_bpu_flush` 状态；该状态持续一拍，并根据 `AS/SD/VM` 决定是否产生 BPU phase-1 请求。
- 现有的 `io.out.valid := state =/= s_idle && state =/= s_wait` 和完成状态返回 `s_idle` 的逻辑已经覆盖 `s_bpu_flush`，无需修改。

```text
FENCE.I    : s_idle → s_wait --sbEmpty--> s_icache
                       │                    ├─ fencei = 1
                       └─ flushSb = 1       └─ io.out.valid = 1

FENCE.TIME : s_idle → s_bpu_flush
                       ├─ bpuFlush = AS || SD || VM
                       └─ io.out.valid = 1
```

四个安全域标志随 FENCE.TIME 微操作一起锁存到 `uop` 中；`AS`、`SD`、`VM` 作为 BPU 刷新使能条件，`Priv` 不参与刷新。需要修改的代码如下：

```scala
// src/main/scala/xiangshan/backend/fu/Fence.scala
// 修改：增加 FENCE.TIME 专用状态编码。Enum 常量可无条件保留
val s_idle :: s_wait :: s_tlb :: s_icache :: s_fence :: s_nofence :: s_bpu_flush :: Nil = Enum(7)

// 修改：开关关闭时恢复原始 state := s_wait 路径
when (state === s_idle && io.in.valid) {
  if (HasBpuFlush) {
    when (io.in.bits.ctrl.fuOpType === FenceOpType.fencetime) {
      state := s_bpu_flush
    }.otherwise {
      state := s_wait
    }
  } else {
    state := s_wait
  }
}

// 新增：标志提取、比较和输出驱动均不得在开关外 elaboration
if (HasBpuFlush) {
  val fenceTimeFlags = uop.data.imm(3, 0)
  val vmChange   = fenceTimeFlags(3) // inst[23]
  val sdChange   = fenceTimeFlags(2) // inst[22]
  val asChange   = fenceTimeFlags(1) // inst[21]
  val privChange = fenceTimeFlags(0) // inst[20]

  // AS/SD/VM 任一置位时发送 phase-1 请求；Priv 不参与刷新
  io.fenceio.get.bpuFlush.get :=
    state === s_bpu_flush && (asChange || sdChange || vmChange)
}
```

`s_bpu_flush` 是 Scala/Chisel 端的 `UInt` 编码常量，本身不生成独立寄存器或组合逻辑，因此不要求对 `Enum(7)` 声明本身加编译开关。基线 6 个状态与增加 FENCE.TIME 后的 7 个状态均使用 3 bit 编码，不改变 `state` 寄存器宽度，原有 `s_idle`～`s_nofence` 的编码也保持不变。

`HasBpuFlush=false` 时，必须确认与 `s_bpu_flush` 相关的译码、可达状态转移、标志比较及输出逻辑均不存在。所有对 `s_bpu_flush` 的硬件消费必须位于 Scala `if (HasBpuFlush)` 内，使其在关闭配置 elaboration 后成为未使用常量并被消除。若未来状态数变化跨越 2 的幂次边界并导致 `state` 寄存器增宽，则需重新评估是否对状态集合本身进行条件构造。

### 4.2 Fence 到 Frontend/BPU 的独立刷新信号

`FENCE.TIME` 需要独立于 `fencei` 的 BPU phase-1 请求信号。首先在 `FenceIO` 中增加输出：

```scala
// src/main/scala/xiangshan/backend/fu/Fence.scala
class FenceIO(implicit p: Parameters) extends XSBundle {
  val sfence = Output(new SfenceBundle)
  val fencei = Output(Bool())
  val bpuFlush: Option[Bool] = Option.when(HasBpuFlush)(Output(Bool()))
  val sbuffer = new FenceToSbuffer
}

class Fence(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
  // existing connections ...
  // bpuFlush 仅在 if (HasBpuFlush) 块内通过 .get 消费
}
```

后端到前端的接口应分别传递 `FENCE.I` 的 `fencei` 信号和 `FENCE.TIME` 的新增 BPU 刷新请求。现阶段必须保留 `FENCE.I` 触发 BPU 刷新的实验通路，因此 BPU 的 phase-1 请求由两条信号共同触发，ICache 仍只由 `fencei` 触发：

```scala
// FrontendIO：增加独立可选输入
val fencei   = Input(Bool())
val bpuFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))

// XSCore：分别连接 Fence FU 的两个输出
frontend.io.fencei := backend.io.fenceio.fencei
if (HasBpuFlush) {
  frontend.io.bpuFlush.get := backend.io.fenceio.bpuFlush.get
}

// Frontend：fencei 是既有通路，无条件保留
val fenceiReg = RegNext(io.fencei)
icache.io.fencei := fenceiReg // FENCE.I 保持原有 ICache 失效语义

// FENCE.TIME 专用 RegNext 和可选端口的 .get 均放入同一 Scala if
if (HasBpuFlush) {
  val bpuFlushReg = RegNext(io.bpuFlush.get)
  bpu.io.flush.get := fenceiReg || bpuFlushReg
}
```

不得使用 `io.bpuFlush.getOrElse(false.B)` 把 `RegNext` 留在开关外；根据 00 文档 §3.1 和 01 文档 §6.1，刷新专用端口用 `Option.when` 声明，其连线、寄存器及组合逻辑整段放入 Scala `if (HasBpuFlush)` 内并以 `.get` 访问。

当 `AS`、`SD`、`VM` 中任一标志置位时，`bpuFlush` 用于 phase-1，使 `BpuFlushCtrl` 进入 `s_waiting`；随后 ROB redirect 通过现有 FTQ/BPU redirect 通路触发 phase-2，使其进入 `s_flushing`。仅 `Priv` 置位或四个标志均未置位时，`bpuFlush` 保持无效，redirect 不触发 BPU 刷新。

---

## 5. FENCE.TIME 与 FENCE.I 的区别

下表中 FENCE.TIME 列描述 `HasBpuFlush=true` 时的行为；`HasBpuFlush=false` 时该编码按非法指令处理。

| 行为                       |         `FENCE.I`         |       `FENCE.TIME`       |
| -------------------------- | :-------------------------: | :-------------------------: |
| 使用 Fence FU              |             是             |             是             |
| 等待更老指令离开 ROB       |             是             |             是             |
| 阻止更年轻指令越过         |             是             |             是             |
| 排空 Store Buffer/Uncache  |             是             |        **否**        |
| 产生 ICache`fencei` 刷新 |             是             |        **否**        |
| 发送 BPU flush 请求        | `HasBpuFlush=true` 时为是 | `HasBpuFlush=true` 且 `AS/SD/VM` 任一置位时为是 |
| ROB 头部产生 redirect      |             是             |             是             |
| 从指令的下一条重新取指     |             是             |             是             |

### 5.1 FENCE.I 在 Fence 单元中的任务

`FENCE.I` 的正式语义和最终设计保持不变。Fence 单元仍按原有路径完成数据流与指令流同步。

当前 `timing-fense` 分支中还没有实现 `FENCE.TIME` 的指令编码、译码和编译器支持，因此**现阶段临时借用 `FENCE.I` 作为 BPU 刷新实验的触发通路**：前端将 `FENCE.I` 的刷新信号抄送给 BPU，随后由该指令产生的 redirect 触发实际刷新。这只是用于验证两阶段 BPU 刷新协议的阶段性实现，不属于 `FENCE.I` 的新增架构语义，也**不是最终设计**。

在 `FENCE.TIME` 的独立通路实现后，保留当前由 `FENCE.I` 触发 BPU phase-1 请求的通路，同时新增由 `FENCE.TIME` 触发的独立通路；两者在 Frontend 中合并后送入 BPU。`FENCE.I` 原有的 ICache 同步语义保持不变，`FENCE.TIME` 不得触发 ICache 失效。

### 5.2 FENCE.TIME 在 Fence 单元中的任务

`FENCE.TIME` 的任务及系统级执行顺序以第 3 章为准。它采用与 `FENCE.I` 一致的后端顺序化控制，但不具有 `FENCE.I` 的数据流与指令流同步语义，因此在 Fence 单元中不走 `s_wait → s_icache` 路径，而应使用独立的 `FenceOpType.fencetime` 和专用 `s_bpu_flush` 状态。

在 FENCE.TIME 的指令执行过程中，Fence 单元负责识别 `FENCE.TIME`、检查标志并报告执行完成；仅当 `AS`、`SD`、`VM` 中任一标志置位时发出 phase-1 请求。redirect 由 ROB 产生；BPU 只有在 phase-1 后处于 waiting 状态时，才由 redirect 启动 phase-2 实际刷新。`FENCE.TIME` 不应等待或排空 Store Buffer/Uncache、清除 ICache valid bits、取消 ICache MSHR。

---

## 6. VM/SD/AS/Priv 标志处理

`HasBpuFlush=true` 时，`VM/SD/AS/Priv` 位于指令字 `inst[23:20]`，具体为 `inst[23]=VM`、`inst[22]=SD`、`inst[21]=AS`、`inst[20]=Priv`。这些标志用于说明执行 `FENCE.TIME` 时发生了哪类安全域切换，而不是由软件直接指定刷新某个 BPU 组件的掩码。

| 标志     | 含义                                                           | 本期处理方式                                                                                     |
| -------- | -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `Priv` | 特权级发生切换，例如 U、S、M 模式之间的切换                    | 不在本期威胁模型内；不刷新，不提供跨特权级 BPU 时序隔离保证                                      |
| `AS`   | 地址空间发生切换，即 ASID 发生变化，通常对应进程或地址空间切换 | 在新地址空间开始执行前，通过`FENCE.TIME` 清理旧地址空间留下的 BPU 状态                         |
| `SD`   | Supervisor Domain 发生切换，即 SDID 发生变化                   | 在新的 Supervisor Domain 开始执行前，通过`FENCE.TIME` 清理旧 Supervisor Domain 留下的 BPU 状态 |
| `VM`   | 虚拟机上下文发生切换，即 VMID 发生变化                         | 在新虚拟机开始执行前，通过`FENCE.TIME` 清理旧虚拟机留下的 BPU 状态                             |

发生 `AS`、`SD` 或 `VM` 切换时，负责上下文切换的系统软件应在旧安全域退出之后、新安全域的代码开始执行之前，执行置有相应标志的 `FENCE.TIME`。

当前实现中，`AS`、`SD`、`VM` 参与刷新使能：任一标志置位时执行 BPU 刷新；`Priv` 不参与刷新，只有 `Priv` 置位时不执行 BPU 刷新。若 `Priv` 与 `AS`、`SD` 或 `VM` 同时置位，仍由后者触发刷新。

> `Priv` 被排除是本期实验范围的明确取舍，不代表编译器防护与硬件时序状态隔离等价。若后续威胁模型扩展至跨特权级攻击，需另行实现 `Priv` 触发的刷新或按特权级分区的 BPU。

---

## 7. 编译裁剪契约与验收

### 7.1 统一编码规则

本文必须遵循 00 文档的“分散守卫”和 01 文档 §6.1 的可选 IO 消费规范：

| 层级 | 裁剪对象 | 声明/消费方式 | `HasBpuFlush=false` 时要求 |
|---|---|---|---|
| 指令常量 | `FENCE_TIME`、`FenceOpType.fencetime` | 保留 Scala 常量 | 不得有硬件消费点 |
| 译码 | FENCE.TIME 译码表项 | `if (HasBpuFlush)` 追加子表 | 表项不存在，编码进入非法指令路径 |
| Rename | `fencetime` 立即数覆盖例外 | Scala `if` 在表达式中返回原条件 | 不生成 `fencetime` 比较器 |
| Fence FU | 标志提取、`s_bpu_flush` 转移和请求生成 | 整段放入 Scala `if` | 专用路径不生成，原 Fence/FENCE.I 状态机恢复基线行为 |
| `FenceIO` / `FrontendIO` | `bpuFlush` | `Option.when` 声明，`if + .get` 消费 | 端口不存在 |
| XSCore / Frontend | `bpuFlush` 连线、`bpuFlushReg` 及与 `fenceiReg` 的合并 | 整段放入 Scala `if` | 连线、寄存器与合并逻辑不生成 |

只有 Bundle/IO 的可选字段使用 `Option.when(HasBpuFlush)(...)`。刷新专用 `RegNext`、比较、状态转移和合并逻辑必须放入 Scala `if (HasBpuFlush)`，不得使用 `getOrElse(false.B)` 代替结构性守卫。FENCE.I 的 `fencei` 端口、`fenceiReg`、ICache 驱动和 Fence FU 原状态转移均不受本开关影响。

### 7.2 双配置 elaboration 与 RTL 检查

必须分别以 `HasBpuFlush=true` 和 `HasBpuFlush=false` 完成 elaboration 与 RTL 生成，两种配置均不得出现 Scala/Chisel 异常、FIRRTL 错误、未连接端口或方向冲突。

`HasBpuFlush=false` 的生成 RTL 必须满足：

- FENCE.TIME 编码不命中合法译码，非法指令回归通过；
- 不存在 FENCE.TIME 专用的 `fuOpType` 选择、Rename 比较、`s_bpu_flush` 可达转移、标志比较和 `bpuFlush` 生成逻辑；
- `FenceIO` 和 `FrontendIO` 中不存在 `bpuFlush` 端口，XSCore 中不存在对应连线，Frontend 中不存在 `bpuFlushReg` 及 OR 合并逻辑；
- FENCE.I 仍正常译码，其 `s_wait → s_icache`、`fenceiReg → ICache` 及 Store Buffer/Uncache 相关行为与基线一致。

`HasBpuFlush=true` 的生成 RTL 必须保留本文第 2～6 章定义的译码、顺序化控制、Fence FU 专用路径和 `FenceIO → XSCore → Frontend → BPU` phase-1 通路。不能仅依据信号名搜索得出裁剪结论；应对比关闭配置与未引入 FENCE.TIME/BPU 刷新功能的基线 RTL，确认不存在额外端口、寄存器和组合门控。
