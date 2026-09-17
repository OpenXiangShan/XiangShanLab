# 00-BPU刷新机制编译开关实现方案

> 为 BPU 上下文刷新机制实现**编译期开关**（elaboration-time 参数控制，并在 Chisel/CIRCT 编译流程内完成裁剪）。`HasBpuFlush=false` 时，所有仅服务于上下文刷新的 CSR 位、IO、状态机、寄存器及完成握手逻辑不得生成；对既有数据通路追加的恒等门控允许在 Chisel/CIRCT 编译流程内被常量折叠。共享基础设施不计入裁剪范围，最终以生成 RTL 与未引入刷新功能的基线 RTL 对比为准。
>
> 1. **功能边界**：本文实现的是 BPU 上下文刷新硬件的编译期裁剪，不涉及 BPU 预取策略或预取性能目标。
> 2. **参考 bitmap 机制**：定义层、配置层借鉴 `HasBitmapCheck` 编译开关范式（详见 [Bitmap检查机制开关实现.md](../../SupportingDocument/Bitmap检查机制开关实现.md)），选择逻辑层使用“集中裁剪 + 分散守卫”。
> 3. **Sticky 总使能**：编译期 `HasBpuFlush` 决定刷新硬件是否存在；`HasBpuFlushDefault` 决定 `sbpctl.BPU_FLUSH_EN` 是复位后默认为 0、允许软件一次性置 1，还是复位后固定为 1。`BPU_FLUSH_EN` 一旦为 1，在下一次复位前不能清 0（详见 [01-BPU刷新方案概览](01-BPU刷新方案概览.md) §2.1）。
> 4. **职责划分**：本文统一定义编译裁剪契约、覆盖范围与验收标准；02～12 共同构成完整 BPU 刷新设计。02～10 定义 `predictors` 中各预测器的真实刷新，11-PHR 与 12-CommonHR 定义两个固定参与模块的真实刷新；本文不以当前代码实现进度缩减设计覆盖范围。

---

## 1 设计范式：集中裁剪与分散守卫

### 1.1 bitmap 的实现方式：分散守卫

bitmap 的 `HasBitmapCheck` 呈定义层、配置层、选择逻辑层三层结构。其功能逻辑嵌入 PTW 流程且使用点分散，因此每个使用点分别使用 `if (HasBitmapCheck)` 或 `Option.when(HasBitmapCheck)(...)` 守卫。

BPU 刷新开关借鉴 bitmap 的定义层和配置层，但不能简化为 BPU 顶层的单点裁剪。

### 1.2 BPU 刷新的裁剪策略

BPU 刷新采用两类裁剪方式：

1. **集中裁剪**
   - `BpuFlushCtrl` 模块及其状态机、刷新 mask 事务级锁存，以及根据 BPU 顶层聚合后的 `resetDone` 完成电平进行状态迁移的逻辑；
   - `Bpu.scala` 中的刷新触发接线、`currentFlushMask` 生成、按 `activeFlushMask` 的逐预测器分发、PHR/CommonHR 固定分发与 `resetDone` 完成聚合；
   - 通过 `if (HasBpuFlush) { Module(...) }` 和同一条件块内的连线完成裁剪。
2. **分散裁剪**
   - CSR 刷新位及 CSR → `BpuCtrl` 映射；
   - Frontend → BPU 的 `flush` 端口与连线；
   - `BasePredictorIO`、`PhrIO`、`CommonHRIO` 的刷新握手端口；
   - 02～12 各模块内部的清零、读写/更新阻塞、输出门控、pending 与 `resetDone` 逻辑；

只有两类裁剪全部完成，才能声明刷新专用逻辑在 `HasBpuFlush=false` 时结构性消失。状态机的集中裁剪不能代替各使用点的分散守卫。

---

## 2 编译开关定义

### 2.1 开关字段

在核心参数 case class 中新增字段：

`src/main/scala/xiangshan/Parameters.scala`

```scala
case class XSCoreParameters(
  ...
  HasBpuFlush: Boolean = true,
  HasBpuFlushDefault: Boolean = false,
  ...
)
```

在 `HasXSParameter` trait 中暴露：

```scala
def HasBpuFlush: Boolean = coreParams.HasBpuFlush
def HasBpuFlushDefault: Boolean = coreParams.HasBpuFlushDefault
```

`HasBpuFlush=true` 表示生成刷新硬件。`HasBpuFlushDefault=false` 时，`BPU_FLUSH_EN` 复位为 0，软件可将其置 1；一旦置 1，后续写 0 无效，直到复位才恢复为 0。`HasBpuFlushDefault=true` 时，`BPU_FLUSH_EN` 固定为 1，软件写入无效。`HasBpuFlush=false` 时该位及刷新硬件均不生成。生成网表、模块接口与 PPA 相对未实现刷新的基线会发生变化，不应表述为“现有硬件配置不变”。

### 2.2 配置入口

BPU 上下文刷新为通用特性，不绑定 CVM 等特定场景。提供两条配置入口：

| 入口 | 文件 | 方式 |
|------|------|------|
| YAML（推荐） | `src/main/scala/top/YamlParser.scala` | 新增 `EnableBpuFlush` 与 `EnableBpuFlushDefault` 字段 |
| Scala Config（可选） | `src/main/scala/top/Configs.scala` | 提供默认关闭后可 sticky 开启、默认固定开启两类配置 |

YAML 入口：

```scala
// YamlConfig case class 新增字段
EnableBpuFlush: Option[Boolean],
EnableBpuFlushDefault: Option[Boolean],

// 解析入口
yamlConfig.EnableBpuFlush.foreach { enable =>
  newConfig = newConfig.alter((site, here, up) => {
    case XSTileKey => up(XSTileKey).map(_.copy(HasBpuFlush = enable))
  })
}

yamlConfig.EnableBpuFlushDefault.foreach { enable =>
  newConfig = newConfig.alter((site, here, up) => {
    case XSTileKey => up(XSTileKey).map(_.copy(HasBpuFlushDefault = enable))
  })
}
```

Scala Config 入口示例（参考 `CVMCompile`/`CVMConfig` 的组合方式，可直接作为 `CONFIG=BPUFlushConfig` 使用）：

```scala
class BPUFlushCompile extends Config((site, here, up) => {
  case XSTileKey => up(XSTileKey).map(_.copy(
    HasBpuFlush = true,
    HasBpuFlushDefault = false
  ))
})

class BPUFlushDefaultOnCompile extends Config((site, here, up) => {
  case XSTileKey => up(XSTileKey).map(_.copy(
    HasBpuFlush = true,
    HasBpuFlushDefault = true
  ))
})

class BPUFlushConfig(n: Int = 1) extends Config(
  new BPUFlushCompile
    ++ new DefaultConfig(n)
) with DeprecatedConfigWarning
```

`BPUFlushCompile` 生成“复位默认关闭、软件置 1 后锁定”的刷新硬件；`BPUFlushDefaultOnCompile` 生成“复位即固定开启”的刷新硬件。对应完整 Config 分别为 `BPUFlushConfig` 和 `BPUFlushDefaultOnConfig`。关闭配置使用 YAML `EnableBpuFlush=false`。

### 2.3 编译配置与 sticky 总使能的关系

| `HasBpuFlush` | `HasBpuFlushDefault` | `BPU_FLUSH_EN` | 结果 |
|:---:|:---:|---|---|
| `true` | `false` | 复位 0；可写 1；置 1 后锁定 | 未开启时不接受 `fence.i` 刷新；软件置 1且传递到 BPU 后，刷新机制持续开启到下一次复位 |
| `true` | `true` | 固定为 1 | 状态机始终可接收 `fence.i` 触发，软件写入无效 |
| `false` | × | 位不生成 | 刷新专用结构不生成，相关 CSR 位读 0/写忽略 |

`BPU_FLUSH_EN` 连接到 `s_idle → s_waiting` 入口。sticky 语义消除了开启后的 `1→0` 运行时切换，因此不会因软件中途关闭而使状态机停在中间态；默认关闭模式仍需验证首次 `0→1` 与 phase-1 `flush` 的传递时序。BPU 刷新与 bitmap/MPT 无强制互斥关系。

---

## 3 编译裁剪契约

### 3.1 统一编码规则

| 层级 | 裁剪对象 | 声明方式 | 消费方式 | `false` 时要求 |
|---|---|---|---|---|
| 参数 | `HasBpuFlush` | Scala `Boolean` | Scala `if` | elaboration 期常量 |
| CSR | `sbpctl` bit[14:7] | `Option.when` | 条件映射 | 读 0、写忽略 |
| Frontend/BPU IO | `flush` | `Option.when` | `if + .get` | 端口不存在 |
| BPU FSM | `BpuFlushCtrl` | 条件实例化 | 条件连线 | 模块不存在 |
| `BasePredictorIO` | 三个刷新握手端口 | `Option.when` | `if + .get` | 端口不存在 |
| `PhrIO` / `CommonHRIO` | 各自的三个刷新握手端口 | `Option.when` | `if + .get` | 端口不存在 |
| 模块内部新增结构 | 模块、寄存器、存储、清零 `when`、pending、完成逻辑 | 普通 Chisel 逻辑 | 整段放入 Scala `if` | 结构不生成 |
| 既有数据通路门控 | 读写请求、valid、fire、Mux 选择条件 | 修改原 Chisel 表达式 | Scala `if` 返回刷新表达式或恒等项 | 生成 RTL 恢复原表达式 |

具体规则：

- Bundle/IO 可选字段使用 `Option.when(HasBpuFlush)(...)`。
- 刷新专用模块实例、寄存器、存储、`RegNext`、清零 `when`、独立 `Mux` 或计算逻辑必须由 Scala `if (HasBpuFlush)` 包围；分支内可用 `.get` 访问端口。
- 对既有数据通路追加门控时，允许以下两类等价写法：① `if (HasBpuFlush) 刷新表达式 else 原表达式`；② 在原表达式中内联 Scala `if`，关闭分支返回布尔恒等项，例如 `raw && (if (HasBpuFlush) !io.bpuFlushing.get else true.B)`。第二类写法可能在 Chisel 中暂时形成 `&& true.B`，但必须在 Chisel/CIRCT 编译流程内折叠，生成 RTL 不得保留额外门控。
- 若一个由 Scala `if` 定义的刷新辅助量在关闭分支返回 `true.B`/`false.B`，可继续用于既有 `Mux` 或布尔表达式；关闭分支中的刷新专用 `RegNext` 等结构不会 elaboration，常量传播后生成 RTL 必须恢复原选择条件。这里允许消除的是**刷新新增的门控**，不要求删除原数据通路本来就存在的 `Mux`。
- 单纯对可选端口进行常量连接时允许 `Option.foreach`，但建议统一使用 `if + .get` 以便检索与审计。
- 禁止用 `getOrElse(false.B)`/`getOrElse(true.B)` 代替刷新专用模块、寄存器、存储、清零块或完成逻辑的 Scala 守卫；这些结构必须在关闭配置下不参与 elaboration。可选端口仍应仅在 `HasBpuFlush=true` 的 Scala 分支中通过 `.get` 访问，以避免隐藏裁剪边界。
- 关闭分支必须等价于引入刷新机制前的原始数据通路，不得保留额外延迟、门控或默认值。

### 3.2 CSR 侧

`NewCSR/CSRCustom.scala` 的 `SbpctlBundle` 新增 bit7 sticky `BPU_FLUSH_EN` 与 bit[14:8] 可读写逐预测器刷新使能，均使用 `Option.when(HasBpuFlush)(...)` 声明。bit7 字段定义为复位 0 的 `RW`，由 `sbpctl` 的定制 `CSRModule` 覆盖默认写行为：当前值为 0 时允许写入，一旦为 1则锁定；`HasBpuFlushDefault=true` 时直接驱动为 1。`SbpctlBundle` 需带 `implicit p` 并混入 `HasXSParameter`。

`NewCSR/NewCSR.scala` 中 CSR 到 `BpuCtrl` 的映射使用 `if (HasBpuFlush) { ... .get ... }`。关闭时两侧字段均为 `None`，不需要 `else` 连接。

实现时必须通过 CSR 模型或生成 RTL 验证：bit[14:7] 不生成时，其读值为 0、写入被忽略，且 bit[6:0] 原有预测器使能位的位置与行为不变。

### 3.3 Frontend 与 BPU 顶层

| 文件 | 裁剪对象 | 方式 |
|---|---|---|
| `frontend/Frontend.scala` | `RegNext(fencei)` 到 BPU 的抄送连线 | `BpuIO.flush` 用 `Option.when` 声明，连线放入 `if (HasBpuFlush)` |
| `bpu/Bundles.scala` | `BpuCtrl` 中 `bpuFlushEn` 及 `*FlushEnable` | `BpuCtrl` 继承 `BpuBundle`，字段用 `Option.when` 声明 |
| `bpu/Abstracts.scala` | `contextFlush`、`bpuFlushing`、`resetDone` | `BasePredictorIO` 中用 `Option.when` 声明 |
| `bpu/BpuFlushCtrl.scala` | 状态机、刷新 mask 锁存，并根据 BPU 顶层聚合后的 `resetDone` 进行状态迁移 | 整个模块仅在 `HasBpuFlush=true` 时实例化 |
| `bpu/Bpu.scala` | 触发接线、`currentFlushMask` 生成、逐预测器 mask 分发、PHR/CommonHR 固定分发与 `resetDone` 完成聚合 | 与 `BpuFlushCtrl` 实例位于同一 Scala `if` 中 |

状态机边界清晰，应抽成独立 `BpuFlushCtrl` 模块，以便集中裁剪。该做法只裁剪顶层编排逻辑，不代替子模块内部的分散守卫。状态机语义、BPU 顶层的参与者完成聚合及 `fence.i → redirect` 协议见 01 文档；其功能正确性不由本编译开关替代论证。

逐预测器刷新使能必须在 phase-1 请求被接受时锁存为事务级 `activeFlushMask`。同一事务中，02～10 预测器的 `contextFlush`/`bpuFlushing` 分发和 `resetDone` 聚合均使用该快照，不得直接使用中途变化的 CSR 使能；PHR/CommonHR 不占 mask 位，每次已接受事务都固定接收刷新信号。`Bpu.scala` 将 mask 选中的预测器完成电平与 PHR/CommonHR 的 `resetDone` 共同与归约，再将单一聚合完成电平传给 `BpuFlushCtrl`，直接驱动 `s_flushing → s_done`，不增加 `allResetDone` 锁存；`s_done` 为后续完成处理逻辑预留，当前停留 1 拍后返回 `s_idle`。

### 3.4 子预测器侧

各 02～10 预测器必须同时裁剪端口与真实刷新消费逻辑：

- `contextFlush`、`bpuFlushing`、`resetDone` 由 `BasePredictorIO` 以 `Option.when` 声明；
- 寄存器清零、SRAM 额外复位、WriteBuffer 清空、替换状态清零、pending 与 `resetDone` 生成等刷新专用结构，均放入 `if (HasBpuFlush)`；
- 对既有数据通路增加的门控可采用 §3.1 定义的整式分支或恒等项写法；关闭分支及常量折叠后的生成 RTL 必须恢复原始表达式；
- 每个有可刷状态的预测器都必须按 02～10 的具体方案生成真实 `resetDone`，不得以恒真占位代替真实完成条件；无可刷状态的 `FallThroughPredictor` 可以 `true.B` 表示实时完成。

### 3.5 PHR、CommonHR 独立模块侧

PHR、CommonHR 不属于 `predictors`，不占用 `activeFlushMask` 位，也没有独立逐模块 CSR mask。每次被 `bpuFlushEn` 接受的刷新事务中，BPU 顶层必须向两者固定分发 `contextFlush` 和 `bpuFlushing`，并将两者的真实 `resetDone` 无条件加入完成聚合。`PhrIO`/`CommonHRIO` 的刷新端口、模块内清零、更新门控、输出隔离和完成逻辑均受 `HasBpuFlush` 结构性裁剪，具体契约分别见 [11-PHR](11-PHR设计分析与刷新方案.md) 和 [12-CommonHR](12-CommonHR设计分析与刷新方案.md)。

---

## 4 设计覆盖清单

### 4.1 参数与配置直接修改项

| 文件 | 改动类型 |
|---|---|
| `src/main/scala/xiangshan/Parameters.scala` | 新增 `HasBpuFlush`、`HasBpuFlushDefault` 及对应 trait def |
| `src/main/scala/top/YamlParser.scala` | 新增 `EnableBpuFlush`、`EnableBpuFlushDefault` 字段及解析入口 |
| `src/main/scala/top/Configs.scala` | 新增 sticky/default-on 两类 Compile 与完整 Config |

### 4.2 必须接受编译开关约束的跨文档修改项

| 范围 | 主要文件/文档 | 验收责任 |
|---|---|---|
| CSR | `CSRCustom.scala`、`NewCSR.scala` | 刷新位与映射结构性消失，原 CSR 位不变 |
| Frontend/BPU 接口 | `Frontend.scala`、`bpu/Bundles.scala`、`bpu/Abstracts.scala` | 可选端口与连线完整裁剪 |
| BPU 顶层聚合与分发 | `bpu/Bpu.scala` | 触发接线、mask 生成、逐预测器 mask 分发、PHR/CommonHR 固定分发与完成聚合不生成 |
| 刷新控制器 | `bpu/BpuFlushCtrl.scala` | FSM、mask 锁存、刷新信号生成及聚合完成电平的接收逻辑不生成 |
| 02～10 预测器 | 各预测器顶层及其内部模块 | 真实清零、SRAM reset、pending、读写/训练/输出门控和完成逻辑完整裁剪 |
| PHR | `history/phr/Phr.scala`、[11-PHR](11-PHR设计分析与刷新方案.md) | 可选 IO、状态清零、更新/输出门控和 `resetDone` 完整裁剪 |
| CommonHR | `history/commonhr/CommonHR.scala`、[12-CommonHR](12-CommonHR设计分析与刷新方案.md) | 可选 IO、状态清零、流水/更新/输出门控和 `resetDone` 完整裁剪 |

02～12 的真实刷新细节由各自 SPEC 描述；编译开关的覆盖完整性与关闭配置的结构性裁剪由本文统一验收，不以当前代码实现进度改变设计契约。

---

## 5 验证与验收

### 5.1 三配置 elaboration

必须分别完成：

- `HasBpuFlush=false`；
- `HasBpuFlush=true, HasBpuFlushDefault=false`；
- `HasBpuFlush=true, HasBpuFlushDefault=true`。

三种配置均不得出现 Scala/Chisel 异常、FIRRTL 错误、未连接端口或方向冲突。YAML 还应验证两个字段缺省时使用参数默认值，并分别正确覆盖所有目标 tile。

### 5.2 关闭配置的 RTL 检查

`HasBpuFlush=false` 的生成 RTL 中不得出现：

- `BpuFlushCtrl` 及刷新 FSM 状态；
- `bpuFlushEn`、各 `*FlushEnable`、`contextFlush`、`bpuFlushing`；
- 上下文刷新专用 `resetDone`/pending 寄存器；
- 仅服务于上下文刷新的额外 SRAM reset、WriteBuffer 清空、替换状态清零及训练/输出门控逻辑。
- PHR/CommonHR 的刷新可选端口、清零块、更新/输出门控及完成逻辑。

不能只通过信号名搜索得出“零面积”结论；应对比关闭配置与未引入刷新功能的基线 RTL/综合结果，确认无刷新专用寄存器、组合门控和额外端口。

### 5.3 CSR 验证

`HasBpuFlush=true && HasBpuFlushDefault=false` 时，`BPU_FLUSH_EN`（bit7）复位读取值必须为 0；写 0保持 0，写 1后变为 1，之后写 0仍保持 1，复位后恢复为 0。`HasBpuFlushDefault=true` 时 bit7 固定读取为 1且写入无效。bit[14:8] 在两种配置下均保持可读写。

`HasBpuFlush=false` 时：

- `sbpctl` bit[14:7] 读取为 0；
- 对 bit[14:7] 的写入被忽略；
- bit[6:0] 原有预测器使能位的位置、复位值和读写行为不变。

### 5.4 功能回归

- `HasBpuFlush=false`：通过原有 BPU/Frontend 回归，与未引入刷新功能的基线行为一致；
- `HasBpuFlush=true && HasBpuFlushDefault=false && BPU_FLUSH_EN=0`：不触发刷新；软件置 1并等待其传递到 BPU 后，后续 `fence.i` 均可触发刷新，且无法在复位前关闭；
- `HasBpuFlush=true && BPU_FLUSH_EN=1`：02～10 中本事务 mask 选中的预测器与固定参与的 PHR/CommonHR 均必须执行真实清零、窗口阻断和完成握手，聚合完成时刻由最慢参与者决定；
- 逐预测器刷新使能关闭时，被关闭的子功能既不接收刷新请求，也不得阻塞顶层完成聚合。
- PHR/CommonHR 不受逐预测器 CSR mask 影响，每次已接受事务都必须接收刷新并参与完成聚合。
- 刷新进行中改写逐预测器 CSR 使能时，当前事务的 `activeFlushMask`、分发对象与聚合对象不得变化；新值仅对下一次刷新事务生效。

---

## 6 总结

BPU 刷新编译开关采用“定义层 + 配置层 + 集中裁剪 + 分散守卫”的完整范式。`BpuFlushCtrl` 模块边界用于集中裁剪顶层编排逻辑；CSR、可选 IO、02～10 预测器以及 PHR/CommonHR 的真实清零、窗口门控和完成逻辑必须在各使用点执行 elaboration-time 守卫。02～12 共同构成本方案的完整刷新与编译裁剪范围。

`HasBpuFlush=true` 决定硬件存在；`HasBpuFlushDefault=false` 提供 BME 同款“复位 0、可置 1、置 1 后锁定”语义，`HasBpuFlushDefault=true` 则将总使能固定为 1。`HasBpuFlush=false` 时是否达到结构性零增量，必须通过配置 elaboration、生成 RTL/综合差异、CSR 语义与功能回归共同验收，不以顶层单点 `if` 作为充分证据。
