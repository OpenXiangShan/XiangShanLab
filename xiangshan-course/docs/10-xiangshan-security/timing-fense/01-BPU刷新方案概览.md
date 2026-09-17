# 01-BPU刷新方案概览与顶层实现

> 本文档是 BPU 上下文切换刷新机制的**总览与顶层实现**文档。
> §1~§2 为总览：`fence.i` 机制背景、修改方案概述与接口契约汇总；
> §3~§6 为设计："CSR 扩展 / 刷新信号置位 / BPU 顶层控制 / 子模块真实刷新统一契约" 的具体方案；§7 记录待闭环的已知时序问题。
>
> **范围**：CSR 扩展、`fencei` → BPU 刷新信号通路、BPU 顶层 IO 契约与 `contextFlush`/`bpuFlushing`/`resetDone` 握手语义。
> **不在范围**：
> 1. BPU 内部在刷新窗口期间的行为（中间寄存器清空、预测结果无效化、禁止存储读写与旧更新等）由 02～12 各子模块方案定义；本文状态机仅控制刷新握手（`contextFlush`/`bpuFlushing`/`resetDone`），不代替模块内部刷新论证。顶层状态机详见 §5.2.2，统一刷新契约详见 §6，具体方案见 02～12 文档。
> 2. **完整参与者范围**：02～10 对应 `predictors` 中受 `activeFlushMask` 控制的预测器；[11-PHR](11-PHR设计分析与刷新方案.md) 和 [12-CommonHR](12-CommonHR设计分析与刷新方案.md) 不在 `predictors` 中、不占 mask 位，每次已接受刷新事务都固定参与。本文中“全刷”指默认使能的全部 predictors 以及 PHR/CommonHR 共同刷新。
>
> 完整模块刷新方案见 [02-uBTB](02-uBTB设计分析与刷新方案.md)、[03-aBTB](03-aBTB设计分析与刷新方案.md)、[04-mBTB](04-mBTB设计分析与刷新方案.md)、[05-uTAGE](05-uTAGE设计分析与刷新方案.md)、[06-RAS](06-RAS设计分析与刷新方案.md)、[07-uRAS](07-uRAS设计分析与刷新方案.md)、[08-TAGE](08-TAGE设计分析与刷新方案.md)、[09-SC](09-SC设计分析与刷新方案.md)、[10-ITTAGE](10-ITTAGE设计分析与刷新方案.md)、[11-PHR](11-PHR设计分析与刷新方案.md) 和 [12-CommonHR](12-CommonHR设计分析与刷新方案.md)。

---

## 1. `fence.i` 指令的工作机制

当 `fence.i` 指令进入 ROB 后，阻塞后续指令入队。当 `fence.i` 指令正式执行后，由 Fence 模块先向 Store Buffer 发送刷新请求，等待 SB 为空后进入 `s_icache`：该拍向 iCache 发送刷新脉冲，同时完成 Fence FU 写回。随后 `fence.i` 的 `flushPipe` 属性在 ROB 提交侧产生 Redirect，经 FTQ 到达 BPU（从 `fence.i` 指令的下一条 PC 开始预测）。

详细内容可参考：[香山fence.i指令](https://my.feishu.cn/wiki/GXs5wQ1xqiPIFYkKnRecabvsnXe)

---
## 2. BPU 刷新机制修改方案
### 2.1 修改方案概述

为支持 `fence.i` 指令触发的 BPU 上下文切换刷新，本方案涉及三处修改，分别为 "开关—触发—执行" 三个层级：

1. **刷新开关：CSR 扩展**
    - 对 `sbpctl` 控制寄存器向高位扩展 1 bit（bit7 `BPU_FLUSH_EN`），作为 BPU 刷新机制的 sticky 全局使能。默认配置下复位为 0，软件可写 1开启；一旦开启，后续写 0无效，直到再次复位。`HasBpuFlushDefault=true` 时复位后固定为 1。
    - 继续对 `sbpctl` 控制寄存器向高位扩展 7 bit，作为 BPU 各预测器的刷新使能信号，复位值为 1（默认全部参与）。当 sticky 总使能被置 1，或使用 default-on 配置时，默认 mask 使本次刷新覆盖所有可配置预测器，从而**消除 "禁用→残留→再使能" 泄露**。

2. **刷新触发：刷新信号置位**
    - Store Buffer 刷新完成后向 iCache 发送刷新信号时，抄送 BPU，该信号仅作为**刷新使能（armed）**，使 BPU 进入**刷新等待状态**；
    - 进入刷新等待状态后，**一旦接收到 redirect 信号就刷新 BPU**（不区分来源）：通常首个到达的是 `fence.i` 的 `flushPipe` 在 ROB 提交侧产生并经 FTQ 到达 BPU 的 Redirect，也可能是 IFU 流水线残留的旧上下文 redirect，二者均安全（§4.3）。

3. **刷新执行：BPU 控制逻辑修改**
    - 我们在 BPU 顶层内部新增一个状态机：
        - 当 BPU 顶层接收到从 iCache 抄送的刷新信号时，进入**waiting 状态**；
        - 当 BPU 顶层接收到 redirect 信号且处于 waiting 状态时，进入**flushing 状态**；
        - 当 BPU 顶层处于 flushing 状态且聚合 `resetDone` 置位（本事务 mask 选中的 predictors 以及固定参与的 PHR/CommonHR 全部清零完成）时，进入**done 状态**，并在下一个 cycle 进入**idle 状态**。`done` 作为后续完成处理逻辑的预留阶段，当前仅停留 1 拍。
    - 逐预测器刷新使能控制逻辑
        - 在 phase-1 请求被接受时，将当拍逐预测器刷新使能锁存为事务级 `activeFlushMask`。它只用于固定本事务的 predictors 参与者集合：同一事务的 predictors 分发与完成聚合始终使用同一份快照，不受软件中途改写 CSR 影响；PHR/CommonHR 的固定参与身份不由该 mask 表示。
        - BPU 顶层生成 `contextFlush`：只在进入 flushing 态的第 1 拍拉高，之后立即拉低；但状态机仍停留在 flushing 态等待聚合 `resetDone`。
        - BPU 顶层生成 `bpuFlushing`：覆盖 `contextFlush` 单拍 ~ 聚合 `resetDone` 整个刷新窗口的电平信号，下发给所有本事务参与模块，用于阻断旧训练、旧更新与旧历史输出（详见 §5.2.2、§5.4）。
        - 分发：02～10 predictors 的 `contextFlush`/`bpuFlushing` 分别与本事务 `activeFlushMask(i)` 相与后下发；PHR/CommonHR 不经 mask，固定下发。
        - 聚合刷新完成信号：各参与模块的 `resetDone` 完成后保持高电平；BPU 顶层将 `activeFlushMask` 选中的 predictors 与 PHR/CommonHR 共同与归约得到聚合 `resetDone`。

---
### 2.2 接口契约汇总与改动文件清单

#### 2.2.1 接口契约

| 接口 | 方向 | 信号 | 语义 |
|:--|:--|:--|:--|
| NewCSR → BpuCtrl | 经 `bp_ctrl` | `bpuFlushEn` (bit7) + `*FlushEnable` (bit[14:8]) | sticky 总使能（默认复位0、置1后锁定；可配置固定为1）+ 可读写逐预测器刷新使能(复位1) |
| Frontend → BPU | `bpu.io.flush` | 1-cycle 脉冲 | `RegNext(fencei)`，phase-1 触发 |
| FTQ → BPU | `io.fromFtq.redirect` | Valid | phase-2 触发（fence.i 提交 redirect） |
| BPU → 子预测器 | `p.io.contextFlush` | Bool | `contextFlush && activeFlushMask(i)`，本预测器清零脉冲（1-cycle） |
| BPU → 子预测器 | `p.io.bpuFlushing` | Bool | `bpuFlushing && activeFlushMask(i)`，BPU 整体刷新窗口电平，用于延长训练阻塞至聚合 `resetDone`（详见 §5.2.2、§5.4） |
| 子预测器 → BPU | `p.io.resetDone` | Bool | 收到有效 `contextFlush` 后拉低，本地清零完成后置位并保持至下一次有效 `contextFlush` |
| BPU → PHR/CommonHR | `io.contextFlush` | Bool | 不经 mask 的 1-cycle 清零脉冲，每次已接受事务固定下发 |
| BPU → PHR/CommonHR | `io.bpuFlushing` | Bool | 不经 mask 的整体刷新窗口电平 |
| PHR/CommonHR → BPU | `io.resetDone` | Bool | 两者各自的真实完成电平，无条件加入顶层聚合 |

#### 2.2.2 改动文件清单

- [xiangshan/Parameters.scala](../../../src/main/scala/xiangshan/Parameters.scala)（新增 `HasBpuFlush` 参数及访问方法）
- [top/YamlParser.scala](../../../src/main/scala/top/YamlParser.scala)（新增 `EnableBpuFlush` 配置入口）
- [top/Configs.scala](../../../src/main/scala/top/Configs.scala)（可选：增加关闭刷新功能的 Scala Config）
- [NewCSR/CSRCustom.scala](../../../src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala)（`SbpctlBundle`）
- [NewCSR/NewCSR.scala](../../../src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala)（`bp_ctrl` 映射）
- [bpu/Bundles.scala](../../../src/main/scala/xiangshan/frontend/bpu/Bundles.scala)（`BpuCtrl`）
- [bpu/Abstracts.scala](../../../src/main/scala/xiangshan/frontend/bpu/Abstracts.scala)（`BasePredictorIO`）
- [frontend/Frontend.scala](../../../src/main/scala/xiangshan/frontend/Frontend.scala)（抄送 `flush`）
- `src/main/scala/xiangshan/frontend/bpu/BpuFlushCtrl.scala`（刷新状态机）
- [bpu/Bpu.scala](../../../src/main/scala/xiangshan/frontend/bpu/Bpu.scala)（实例化 `BpuFlushCtrl` + 逐预测器 mask 分发 + PHR/CommonHR 固定分发 + 完成聚合）
- 02～10 所列各预测器顶层及内部模块（真实清零、读写/训练/输出门控和完成握手）
- `src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala`（详见 [11-PHR](11-PHR设计分析与刷新方案.md)）
- `src/main/scala/xiangshan/frontend/bpu/history/commonhr/CommonHR.scala`（详见 [12-CommonHR](12-CommonHR设计分析与刷新方案.md)）

> 除上述清单外，Fence 单元（`Fence.scala`）、XSCore（`XSCore.scala`）及 `FenceIO` 接口定义均不变。

---

### 2.3 刷新信号时序与生命周期

BPU 刷新机制由 `contextFlush`、`bpuFlushing`、`resetDone` 三个信号协同。本节给出它们的时序与生命周期总览，生成代码与状态机实现详见 §5.2、§5.3。

#### 2.3.1 信号定义与职责

| 信号 | 类型 | 生成位置 | 职责 |
|:--|:--|:--|:--|
| `contextFlush` | 单拍脉冲 | BPU 顶层状态机 | T 拍拉高，触发本事务所有参与模块清零（寄存器型 1 拍清零，SRAM 型启动多拍清零） |
| `bpuFlushing` | 电平 | BPU 顶层状态机 | 覆盖 T ~ 聚合 `resetDone` 整个刷新窗口，驱动读写、训练、更新和输出阻断 |
| `resetDone` | 组合归约 | BPU 顶层 | mask 选中的 predictors、PHR 与 CommonHR 完成电平共同与归约，驱动状态机 `s_flushing → s_done` 迁移 |

#### 2.3.2 时序关系

以下为 02～12 完整刷新方案的目标时序。假设 T 拍 `contextFlush` 为单拍脉冲，T+k 拍 `resetDone` 首次置位（k 由最慢参与者决定）：

| 时间段       | `contextFlush` | `bpuFlushing` | `resetDone` | 状态机              |
| :---------- | :------------: | :-----------: | :---------: | :------------------ |
| 上电 ~ T-1  |     false      |     false     |    true     | idle / waiting      |
| T 拍        |    **true**    |   **true**    |    false    | waiting → flushing |
| T+1 ~ T+k-1 |     false      |   **true**    |    false    | flushing            |
| T+k 拍      |     false      |   **true**    |  **true**   | flushing → done    |
| T+k+1 拍    |     false      |   **false**   |    true     | done → idle        |
| T+k+2 拍起  |     false      |   **false**   |    true     | idle               |

- **`contextFlush`**：T 拍拉高，次拍拉低。
- **`bpuFlushing`**：T 拍拉高，T+k+1 拍（状态机进入 `s_done`）拉低。
- **`resetDone`**：T+k 拍首次置位（取最慢参与者），当拍驱动状态机迁移。各参与模块可在不同周期完成刷新；其 `resetDone` 为完成电平，完成后须保持为高，直至下一次有效 `contextFlush`，因此顶层与归约会自然等待最慢参与者完成。

> predictors 的 `contextFlush` 与 `bpuFlushing` 经 phase-1 时锁存的 `activeFlushMask` 门控后下发；PHR/CommonHR 不经 mask 固定下发。各参与模块自维护本地 `resetDone`（§5.3.3、§5.3.4）。

---

## 3. CSR 扩展与控制通路

本章对应 §2.1 中 "修改一：刷新开关" 的详细实现，分为寄存器位域扩展、CSR → BPU 传递通路两部分。BPU 侧 `BpuCtrl` Bundle 的扩展属于 BPU 模块修改，详见 §5.1.2。

> **编译开关**：本章代码已按 [00-编译开关实现方案](00-BPU刷新机制编译开关实现方案.md) 应用编译期裁剪--刷新位用 `Option.when(HasBpuFlush)` 声明，CSR 映射用 `if (HasBpuFlush) { ... .get ... }`。`HasBpuFlush` 关闭时刷新位结构性消失、CSR 通路相应分支不生成，详见 00 文档 §2、§3。

### 3.1 sbpctl 寄存器扩展方案

在现有 `sbpctl`（0x5C0）寄存器的基础上，向高位扩展 1 bit 用于 BPU 刷新机制总使能，再扩展 7 bit 用于细粒度控制各子预测器的刷新使能。扩展后的位域定义如下：

| 字段名称 | 字段位置 | 初始值 | 描述 |
|:---|:---:|:---:|:---|
| UBTB_ENABLE | 0 | 1 | 设 1 代表开启 UBTB (L0 BTB) |
| ABTB_ENABLE | 1 | 1 | 设 1 代表开启 ABTB (L1 BTB) |
| MBTB_ENABLE | 2 | 1 | 设 1 代表开启 MBTB (Main BTB) |
| TAGE_ENABLE | 3 | 1 | 设 1 代表开启 TAGE 预测器 |
| SC_ENABLE | 4 | 1 | 设 1 代表开启 SC (统计相关) 预测器 |
| ITTAGE_ENABLE | 5 | 1 | 设 1 代表开启 ITTAGE (间接跳转) 预测器 |
| RAS_ENABLE | 6 | 1 | 设 1 代表开启 RAS (返回地址栈) 预测器 |
| **BPU_FLUSH_EN** | 7 | 0/1 | BPU 上下文刷新 sticky 总使能；默认复位0且可置1，置1后锁定；`HasBpuFlushDefault=true` 时固定为1 |
| **UBTB_FLUSH_ENABLE** | 8 | 1 | uBTB 刷新使能 |
| **ABTB_FLUSH_ENABLE** | 9 | 1 | aBTB 刷新使能 |
| **MBTB_FLUSH_ENABLE** | 10 | 1 | mBTB 刷新使能 |
| **TAGE_FLUSH_ENABLE** | 11 | 1 | TAGE 刷新使能 |
| **SC_FLUSH_ENABLE** | 12 | 1 | SC 刷新使能 |
| **ITTAGE_FLUSH_ENABLE** | 13 | 1 | ITTAGE 刷新使能 |
| **RAS_FLUSH_ENABLE** | 14 | 1 | RAS 刷新使能 |

**扩展要点**：

- bit[6:0] 保持原有预测使能位不变，复用现有 `SbpctlBundle` 定义。
- bit7 为新增的 sticky `BPU_FLUSH_EN`。`HasBpuFlushDefault=false` 时字段定义为复位0的 `RW`：当前值为0时允许软件写入，一旦写成1，后续写0无效，直到复位才恢复为0。`HasBpuFlushDefault=true` 时直接固定为1。该语义复用 Bitmap Check `BME` 的“开启后锁定”机制。
- bit[14:8] 为新增的逐预测器刷新使能位，复位值全 1（默认允许刷新）。软件可按需关闭某些预测器的刷新（如调试场景下保留状态以观测）。
- `uTAGE`/`uRAS` 无独立刷新使能位，随总使能一起刷新。

---
### 3.2 CSR → BPU 控制通路修改方案

现有的 CSR → BPU 控制通路已将 `sbpctl` 的 bit[6:0] 传递到 BPU 顶层，传递链路为：

```
Sbpctl(0x5C0) → NewCSR → CSR 模块 → ALU0 → ExuBlock → intRegion → Backend → XSCore → Frontend → BPU
```

具体地，`NewCSR` 中将 `sbpctl.regOut.*_ENABLE` 逐位连接到 `csrMod.io.status.custom.bp_ctrl`，经 `CustomCSRCtrlIO` 接口沿上述链路传递到 Frontend，最终通过 `bpu.io.ctrl := csrCtrl.bp_ctrl` 注入 BPU（经 `CsrCtrlPortDelay` 延迟）。

**修改方案**：在现有通路基础上增加 `BPU_FLUSH_EN` 及 7 位 `*_FLUSH_ENABLE`，复用相同的传递链路，最小化改动：

1. **`SbpctlBundle` 扩展**：新增 `BPU_FLUSH_EN` 及 `*_FLUSH_ENABLE` 字段（见 3.1）。`SbpctlBundle` 扩展代码示意：
    ```scala
    // 带 implicit p + HasXSParameter 以访问 HasBpuFlush（同 HgeieBundle 范式）；
    // 刷新位用 Option.when，关闭时结构性消失（CSRBundle.getElements 跳过 None、do_asUInt 补 0）
    class SbpctlBundle(implicit val p: Parameters) extends CSRBundle with HasXSParameter {
      // 原有预测使能位（bit 6:0，保持不变）
      val RAS_ENABLE    = RW(6).withReset(true.B)
      val ITTAGE_ENABLE = RW(5).withReset(true.B)
      val SC_ENABLE     = RW(4).withReset(true.B)
      val TAGE_ENABLE   = RW(3).withReset(true.B)
      val MBTB_ENABLE   = RW(2).withReset(true.B)
      val ABTB_ENABLE   = RW(1).withReset(true.B)
      val UBTB_ENABLE   = RW(0).withReset(true.B)
      // 新增：刷新机制使能（HasBpuFlush 关闭时这些 bit 不生成）
      val BPU_FLUSH_EN        = Option.when(HasBpuFlush)(RW(7).withReset(false.B))
      val RAS_FLUSH_ENABLE    = Option.when(HasBpuFlush)(RW(14).withReset(true.B))
      val ITTAGE_FLUSH_ENABLE = Option.when(HasBpuFlush)(RW(13).withReset(true.B))
      val SC_FLUSH_ENABLE     = Option.when(HasBpuFlush)(RW(12).withReset(true.B))
      val TAGE_FLUSH_ENABLE   = Option.when(HasBpuFlush)(RW(11).withReset(true.B))
      val MBTB_FLUSH_ENABLE   = Option.when(HasBpuFlush)(RW(10).withReset(true.B))
      val ABTB_FLUSH_ENABLE   = Option.when(HasBpuFlush)(RW(9) .withReset(true.B))
      val UBTB_FLUSH_ENABLE   = Option.when(HasBpuFlush)(RW(8) .withReset(true.B))
    }
    ```
   `BPU_FLUSH_EN` 的 sticky 写行为由 `sbpctl` 的定制 `CSRModule` 实现：
   ```scala
   val sbpctl = Module(new CSRModule("Sbpctl", new SbpctlBundle) {
     if (HasBpuFlush) {
       val bpuFlushLock = reg.BPU_FLUSH_EN.get.asBool
       if (!HasBpuFlushDefault) {
         reg.BPU_FLUSH_EN.get := Mux(
           wen && !bpuFlushLock,
           wdata.BPU_FLUSH_EN.get,
           reg.BPU_FLUSH_EN.get,
         )
       } else {
         reg.BPU_FLUSH_EN.get := 1.U
       }
     }
   }).setAddr(0x5C0)
   ```
2. **`NewCSR` 输出扩展**：在 `NewCSR.io.status.custom.bp_ctrl` 中增加 8 位输出：
   ```scala
   // bp_ctrl 的刷新使能字段为 Option，用 if + .get 映射；
   // 关闭时两侧字段皆不生成（None），整段被编译期裁剪，无需 else DontCare
   if (HasBpuFlush) {
     io.status.custom.bp_ctrl.bpuFlushEn       .get := sbpctl.regOut.BPU_FLUSH_EN       .get.asBool
     io.status.custom.bp_ctrl.ubtbFlushEnable  .get := sbpctl.regOut.UBTB_FLUSH_ENABLE  .get.asBool
     io.status.custom.bp_ctrl.abtbFlushEnable  .get := sbpctl.regOut.ABTB_FLUSH_ENABLE  .get.asBool
     io.status.custom.bp_ctrl.mbtbFlushEnable  .get := sbpctl.regOut.MBTB_FLUSH_ENABLE  .get.asBool
     io.status.custom.bp_ctrl.tageFlushEnable  .get := sbpctl.regOut.TAGE_FLUSH_ENABLE  .get.asBool
     io.status.custom.bp_ctrl.scFlushEnable    .get := sbpctl.regOut.SC_FLUSH_ENABLE    .get.asBool
     io.status.custom.bp_ctrl.ittageFlushEnable.get := sbpctl.regOut.ITTAGE_FLUSH_ENABLE.get.asBool
     io.status.custom.bp_ctrl.rasFlushEnable   .get := sbpctl.regOut.RAS_FLUSH_ENABLE   .get.asBool
   }
   ```
3. **`BpuCtrl` Bundle 扩展**：在 `BpuCtrl` Bundle 中新增 `bpuFlushEn` 及 7 位 `*FlushEnable` 字段，用于 BPU 顶层接收 CSR 刷新使能信号。该 Bundle 定义于 BPU 模块内（`bpu/Bundles.scala`），**具体扩展实现详见 §5.1.2**。
4. **通信链路**：`CustomCSRCtrlIO` 沿 Backend → XSCore → Frontend 透传，由于接口类型统一为 `CustomCSRCtrlIO`，新增字段随接口自动传递，中间模块无需修改。
5. **BPU 接收**：Frontend 中已有的 `bpu.io.ctrl := csrCtrl.bp_ctrl`（经 `CsrCtrlPortDelay` 延迟）自动携带新增字段，无需额外连线。

**改动文件**：仅 `SbpctlBundle`（CSRCustom.scala）、`NewCSR` 输出映射（NewCSR.scala）两处，通路其余部分零改动。`BpuCtrl` Bundle 扩展属于 BPU 模块修改，详见 §5.1.2。

---

## 4. `fence.i` 刷新信号通路修改

本章对应 §2.1 中"修改二：刷新触发"的详细实现，说明两阶段触发信号如何从 Fence 单元到达 BPU。核心思路：**复用现有 `fencei` 信号通路与 redirect 通路，仅 Frontend 新增 1 行抄送**（抄送受 `HasBpuFlush` 门控，详见 §4.2.1）。

### 4.1 现有 fencei 信号通路分析

`fencei` 信号由 Fence 单元在 `s_icache` 态产生（1-cycle 脉冲），即 "iCache 刷新请求"。该信号经 Backend → XSCore 到达 Frontend，并在 [Frontend.scala](../../../src/main/scala/xiangshan/frontend/Frontend.scala) 中寄存后驱动 iCache。**该寄存器输出即 BPU 可抄送的信号源，同处 Frontend 模块内，无需跨模块连线**。

---
### 4.2 两阶段触发信号通路

两阶段触发信号均复用现有通路，**仅 Frontend.scala 新增 1 行抄送**。

#### 4.2.1 阶段 1：接收 `fencei`，进入 `waiting`

根据 4.1 可知，只需直接将 iCache 的刷新信号抄送 BPU 即可：

```scala
// 紧邻 iCache 抄送，共享同一 RegNext 实例
val fencei_reg = RegNext(io.fencei)
icache.io.fencei := fencei_reg
// 新增：bpu.io.flush 为 Option[Bool] 端口（见 §5.1.1），用 if + .get 抄送；HasBpuFlush 关闭时不连线
if (HasBpuFlush) { bpu.io.flush.get := fencei_reg }
```

Fence 单元（`Fence.scala`）、XSCore（`XSCore.scala`）及 `FenceIO` 接口定义均不变。

#### 4.2.2 阶段 2：接收 `redirect`，进入 `flushing`

`fence.i` 在 Fence 单元的 `s_icache` 态同拍发出 iCache 刷新请求并完成 Fence FU 写回；其 `flushPipe` 属性在 ROB 提交侧产生 Redirect（从 `fence.i` 下一条 PC 开始预测），再经 FTQ 的**现有 redirect 通路**到达 BPU。该 Redirect 不是 iCache 的完成响应，但在现有 fence.i 执行流中位于 phase-1 `flush` 之后。这与普通 redirect（分支误预测、异常等）走完全相同的通路，**无需修改 FenceIO、XSCore 或 FTQ/BPU redirect 接口**。

BPU 不解析 redirect 的类型或来源；在 `s_waiting` 中使用首个 `redirect.valid` 触发，刷新动作与 fence.i 最终 Redirect **无需对齐**（§4.3 论证其语义正确性）。

---
### 4.3 提前触发安全性与多次刷新规避

`io.flush`（phase-1）仅在 `fence.i` 执行、iCache 刷新启动时拉高，此时旧上下文的预测器状态已成残留。`s_waiting` 期间到达的任意 `redirect.valid` 有两类来源：

- **IFU 流水线残留的 younger 块 redirect**：fence.i 之前已取指、尚未排空的旧上下文块产生的 redirect；
- **fence.i 在 ROB 提交侧产生的 redirect**：即 fence.i 的最终 redirect（phase-2）。

**提前触发为何安全**：两类 redirect 到达时，预测器状态均属旧上下文残留，清零是正确行为。因此 `contextFlush` 由 `s_waiting` 期间**第一个** `redirect.valid` 触发即可，无需区分 redirect 来源；即便提前在残留 redirect 上触发，清零的也是本就该清的陈旧状态。

**刷新语义：`flush` 作刷新使能 + 首个 redirect 触发，不要求与 fence.i Redirect 对齐**：`io.flush`（phase-1）仅作为刷新使能（armed）使状态机进入 `s_waiting`；此后**一旦接收到 redirect 信号就刷新 BPU**，`contextFlush` 由 `s_waiting` 期间第一个 `redirect.valid` 触发。刷新动作与 fence.i 最终 Redirect **无需对齐**。

这一语义下，"刷新窗口必须覆盖 fence.i Redirect" 不再是正确性前提。若 predictors 中只选中寄存器型模块（软件关闭全部 SRAM 型预测器的刷新使能），则连同同为寄存器型的固定参与者 PHR/CommonHR，整体刷新一拍内即可完成。状态机随即经 `s_done` 回到 `s_idle`；此时 fence.i 的 Redirect 可能**晚于刷新完成到达（错开）**，但**不影响刷新语义**：

- 触发刷新的 redirect（无论残留 redirect 还是 fence.i Redirect 本身）到达时，被清零的都是旧上下文残留状态，清零行为正确；
- fence.i 的 Redirect 只是新上下文取指的起点，不携带需要 BPU 额外响应的刷新请求：它晚到时预测器已处于干净状态，且状态机已回 `s_idle`、无新的 phase-1，该 Redirect 不会再次触发刷新（§4.3 末段）；
- 默认全刷配置下，SRAM 型子预测器（uTAGE 约 130 拍，mBTB/TAGE 等）仍会把刷新窗口拉长至多拍量级，但这只是配置的自然结果，**设计不依赖它保证正确性**。因此无需识别 Redirect 来源，也不需要由 `activeFlushMask` 实现额外的“窗口长度判定”。`activeFlushMask` 的职责仅是把 phase-1 当拍确定的 predictors 参与者集合稳定保持到本事务结束；PHR/CommonHR 始终固定参与。

**如何防止多次触发**：刷新的触发条件是 "处于 `s_waiting` 且 `redirect.valid` 有效"，其中 `s_waiting` 只能由 phase-1 `io.flush` 进入。`contextFlush` 拉高当拍状态机即迁入 `s_flushing`，此后 `redirect.valid` 不再参与状态迁移（详见 §5.2.2），后续到达的 redirect 不会重复触发 `contextFlush`，各参与模块（尤其 SRAM 型预测器）的清零过程不会被打断；即便寄存器型单拍刷新已完成、状态机回到 `s_idle`，fence.i 最终 Redirect 到达时因无新的 phase-1 `io.flush`，同样不会再次触发刷新。

状态机迁移逻辑与 `contextFlush` 生成代码详见 §5.2。

---

## 5. BPU 顶层模块修改

本章对应 §2.1 中"修改三：刷新执行"的详细设计，涵盖 BPU 顶层 I/O 接口扩展、两阶段状态机转移逻辑、逐预测器 mask 控制、PHR/CommonHR 固定分发与聚合完成信号生成。改动范围包括 `bpu/Bundles.scala`、`bpu/Abstracts.scala`、`bpu/BpuFlushCtrl.scala`、`bpu/Bpu.scala`以及 02～12 所列各参与模块。

### 5.1 BPU I/O 接口扩展

#### 5.1.1 顶层 I/O 新增端口

BPU 顶层新增一个输入端口用于接收 phase-1 触发信号：

```scala
// BpuIO 是 Bpu 的内部类，可访问外层 BpuModule 的 HasBpuFlush；
// 保持现有 extends Bundle 不变。Option.when 使关闭时端口结构性消失。
val flush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
```

> Frontend 侧用 `if (HasBpuFlush) { bpu.io.flush.get := fencei_reg }` 抄送（见 §4.2.1）。

**I/O 问题与修改结论**：原文的问题仅是注释误称 `BpuIO` 继承 `BpuBundle`；当前 RTL 中它实际是 `Bpu` 的内部 `Bundle` 类。由于 Scala 内部类可访问外层 `Bpu` 的 `HasBpuFlush`，`Option.when` 的写法仍成立，不存在端口方向或参数传递错误。因此只修正文档注释，**不将 `BpuIO` 改为 `BpuBundle`，除本节定义的 `flush` 外不再增加其他 `BpuIO` 端口**。`BpuCtrl` 与 `BasePredictorIO` 的接口扩展分别见 §5.1.2、§5.1.3。

#### 5.1.2 BpuCtrl Bundle 扩展

`BpuCtrl` Bundle（定义于 `bpu/Bundles.scala`）承载 CSR 传来的控制信号。为支持刷新机制，新增 `bpuFlushEn` 总使能与 7 位 `*FlushEnable` 逐预测器使能字段：

```scala
// 改为 BpuBundle（获得 HasBpuFlush 访问）；3 处 new BpuCtrl 调用点（Bpu.scala / Bundle.scala / CSRBundles.scala）均在 implicit p 作用域内，改基类安全
class BpuCtrl(implicit p: Parameters) extends BpuBundle {
  // s1 predictor enable (原有)
  val ubtbEnable:   Bool = Bool()
  val abtbEnable:   Bool = Bool()
  // s3 predictor enable (原有)
  val mbtbEnable:   Bool = Bool()
  val tageEnable:   Bool = Bool()
  val scEnable:     Bool = Bool()
  val ittageEnable: Bool = Bool()
  val rasEnable:    Bool = Bool()
  // 新增：BPU 上下文刷新使能（Option.when，HasBpuFlush 关闭时结构性消失）
  val bpuFlushEn:        Option[Bool] = Option.when(HasBpuFlush)(Bool())
  val ubtbFlushEnable:   Option[Bool] = Option.when(HasBpuFlush)(Bool())
  val abtbFlushEnable:   Option[Bool] = Option.when(HasBpuFlush)(Bool())
  val mbtbFlushEnable:   Option[Bool] = Option.when(HasBpuFlush)(Bool())
  val tageFlushEnable:   Option[Bool] = Option.when(HasBpuFlush)(Bool())
  val scFlushEnable:     Option[Bool] = Option.when(HasBpuFlush)(Bool())
  val ittageFlushEnable: Option[Bool] = Option.when(HasBpuFlush)(Bool())
  val rasFlushEnable:    Option[Bool] = Option.when(HasBpuFlush)(Bool())
}
```

> CSR 侧 `SbpctlBundle` 位域定义与 `NewCSR` 输出映射详见 §3.1、§3.2。

#### 5.1.3 BasePredictorIO 扩展

`BasePredictorIO`（定义于 `bpu/Abstracts.scala`）是 BPU 顶层与各子预测器之间的接口基类。新增三个端口用于刷新握手：

```scala
// BasePredictorIO 已有 implicit p + BpuBundle；三个刷新端口用 Option.when 声明，关闭时结构性消失
class BasePredictorIO(implicit p: Parameters) extends BpuBundle {
  // 原有端口 ...
  // 新增：上下文刷新握手
  val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))   // BPU → 子预测器：清零脉冲（1-cycle）
  val bpuFlushing:  Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))   // BPU → 子预测器：整体刷新窗口电平
  val resetDone:    Option[Bool] = Option.when(HasBpuFlush)(Output(Bool()))  // 子预测器 → BPU：清零完成
}
```

- `contextFlush`：BPU 顶层生成的逐预测器清零脉冲，仅在该预测器刷新使能打开时拉高（详见 §5.3.3）。负责**清零**状态。
- `bpuFlushing`：BPU 顶层生成的整体刷新窗口电平，同样经逐预测器刷新使能门控。
- `resetDone`：子预测器收到有效 `contextFlush` 后拉低，本地清零完成后自行置位并保持至下一次有效 `contextFlush`。它仅反映**本预测器自身清零完成**，可以早于全局 `bpuFlushing` 窗口结束，不等于训练阻塞结束。

#### 5.1.4 PHR/CommonHR 独立刷新接口

PHR 和 CommonHR 都不继承 `BasePredictorIO`，因此分别在 `PhrIO` 和 `CommonHRIO` 中独立声明同样的三个可选端口：

```scala
val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
val bpuFlushing:  Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
val resetDone:    Option[Bool] = Option.when(HasBpuFlush)(Output(Bool()))
```

两者的 `contextFlush`/`bpuFlushing` 不经 `activeFlushMask` 门控，每次已接受刷新事务都固定接收；本地 `resetDone` 无条件加入顶层聚合。具体接口和内部消费逻辑分别见 [11-PHR §4](11-PHR设计分析与刷新方案.md) 与 [12-CommonHR §4](12-CommonHR设计分析与刷新方案.md)。

---

### 5.2 状态机转移逻辑

#### 5.2.1 状态定义

BPU 顶层新增 4 状态有限状态机：

| 状态 | 含义 |
|:--|:--|
| `s_idle` | 空闲，正常预测 |
| `s_waiting` | 已收到 phase-1 `io.flush`，等待 phase-2 redirect |
| `s_flushing` | 已发出 `contextFlush`，等待 mask 选中的 predictors 以及 PHR/CommonHR 全部清零完成 |
| `s_done` | 本次刷新已完成，为后续完成处理逻辑预留；当前停留 1 拍后自动回到 `s_idle` |

状态机仅在 `s_idle` 接受 phase-1。非 idle 期间到达的新 `io.flush` 与当前或刚完成的刷新事务合并，不再单独记录或重复刷新：`s_waiting` 中的当前事务尚未发出 `contextFlush`，随后会完成清零；`s_flushing` 中已经处于清零及训练阻塞窗口；`s_done` 中刷新刚刚完成，再次刷新没有新增效果。

#### 5.2.2 状态迁移与 contextFlush 生成（BpuFlushCtrl 模块）

按 [00](00-BPU刷新机制编译开关实现方案.md) §3.3，状态机边界清晰，抽成独立模块 `bpu/BpuFlushCtrl.scala`，将顶层编排逻辑的编译裁剪收敛到 `Bpu` 顶层一处 `if`。CSR、Frontend IO 和 02～12 各模块内部仍按 00 文档分散守卫。模块内部逻辑同下，编译开关不改其语义。

**模块定义**（状态机 + `contextFlush` + `bpuFlushing` 内聚于此）：

```scala
class BpuFlushCtrlIO(numPredictors: Int) extends Bundle {
  val flush      = Input(Bool())       // phase-1: io.flush（Bpu 顶层 Option 端口的 .get）
  val redirectEn = Input(Bool())       // phase-2: io.fromFtq.redirect.valid
  val bpuFlushEn = Input(Bool())       // sticky enable: ctrl.bpuFlushEn（Option 的 .get）
  val flushMask  = Input(UInt(numPredictors.W))  // phase-1 当拍的逐预测器使能
  val resetDone  = Input(Bool())       // 聚合完成: mask 选中的 predictors + PHR + CommonHR
  val contextFlush = Output(Bool())
  val bpuFlushing  = Output(Bool())
  val activeFlushMask = Output(UInt(numPredictors.W)) // 本事务锁存的 mask
}

class BpuFlushCtrl(numPredictors: Int) extends Module {
  require(numPredictors > 0)
  val io = IO(new BpuFlushCtrlIO(numPredictors))

  val s_idle :: s_waiting :: s_flushing :: s_done :: Nil = Enum(4)
  val flushState = RegInit(s_idle)
  val activeFlushMask = RegInit(0.U(numPredictors.W))

  val acceptFlush = (flushState === s_idle) && io.flush && io.bpuFlushEn
  when(acceptFlush) {
    activeFlushMask := io.flushMask // 事务开始时快照，中途 CSR 改写不生效
  }
  io.activeFlushMask := activeFlushMask

  // 状态机迁移
  when(acceptFlush) {
    flushState := s_waiting
  }.elsewhen(flushState === s_waiting && io.redirectEn) {
    flushState := s_flushing   // contextFlush 在本拍拉高，下一拍进入 flushing
  }.elsewhen(flushState === s_flushing && io.resetDone) {
    flushState := s_done       // 聚合 resetDone 置位，本事务完成
  }.elsewhen(flushState === s_done) {
    flushState := s_idle       // 当前仅停留 1 拍，为后续完成处理逻辑预留
  }

  // contextFlush 仅在 waiting → flushing 迁移的那 1 拍拉高
  io.contextFlush := (flushState === s_waiting) && io.redirectEn

  // bpuFlushing 覆盖整个刷新窗口：T 拍 contextFlush ~ 状态机离开 s_flushing
  io.bpuFlushing := io.contextFlush || (flushState === s_flushing)
}
```

**`Bpu` 顶层实例化**（一处 `if` 守卫，连接用 `.get` 访问 Option 端口）：

```scala
if (HasBpuFlush) {
  val fc = Module(new BpuFlushCtrl(predictors.length))
  fc.io.flush      := io.flush.get                                       // BpuIO.flush (Option)
  fc.io.redirectEn := io.fromFtq.redirect.valid
  fc.io.bpuFlushEn := ctrl.bpuFlushEn.get                                // BpuCtrl.bpuFlushEn (Option)

  // 将 CSR 使能按 predictors 顺序编码，BpuFlushCtrl 在 phase-1 接受时锁存。
  val currentFlushMask = VecInit(predictors.map(p => getSubFlushEnable(p))).asUInt
  fc.io.flushMask := currentFlushMask

  // 仅对本事务 mask 选中的 predictors 等待完成；未选中项视为已完成。
  val predictorsDone = predictors.zipWithIndex.map { case (p, i) =>
    !fc.io.activeFlushMask(i) || p.io.resetDone.get
  }.reduce(_ && _)

  // PHR/CommonHR 不占 mask 位，每次已接受事务固定参与。
  fc.io.resetDone := predictorsDone && phr.io.resetDone.get && commonHR.io.resetDone.get

  // 逐预测器分发（见 §5.3.3），用 .get 访问 Option 端口
  predictors.zipWithIndex.foreach { case (p, i) =>
    p.io.contextFlush.get := fc.io.contextFlush && fc.io.activeFlushMask(i)
    p.io.bpuFlushing.get  := fc.io.bpuFlushing  && fc.io.activeFlushMask(i)
  }

  phr.io.contextFlush.get := fc.io.contextFlush
  phr.io.bpuFlushing.get  := fc.io.bpuFlushing

  commonHR.io.contextFlush.get := fc.io.contextFlush
  commonHR.io.bpuFlushing.get  := fc.io.bpuFlushing
}
// HasBpuFlush 关闭时：BpuFlushCtrl 不实例化，predictors/PHR/CommonHR 刷新端口为 None，
// 整段分发与聚合逻辑不生成（结构性零）
```

- `bpuFlushEn` 来自 sticky `ctrl.bpuFlushEn.get`，随 `BpuCtrl` 整体经 `DelayN(io.ctrl, 2)` 传递（详见 §3.2），并在 `s_idle` 入口门控是否进入 `s_waiting`。默认关闭模式下允许软件首次将其从0置1；置1后不能在复位前清0。首次开启以及逐预测器 bit[14:8] 的运行时改写与 phase-1 `flush` 的到达关系见 §7。
- `activeFlushMask` 在 `s_idle` 接受 phase-1 请求时锁存 `currentFlushMask`。后续的 `contextFlush`/`bpuFlushing` 分发和 `resetDone` 聚合均使用该快照，不直接使用事务中途变化的 CSR 使能。该寄存器在事务结束后无需清零：idle/done 状态下刷新输出均为 false、聚合结果不参与状态迁移；下一次 `acceptFlush` 会用新的 `currentFlushMask` 覆盖旧值。
- `redirect` 即 `io.fromFtq.redirect`，phase-2 触发信号（详见 §4.2.2）。
- `resetDone` 由 `activeFlushMask` 选中的 predictors、PHR 和 CommonHR 共同与归约得到（见 §5.3.4），状态机在 `s_flushing` 中直接使用该完成电平，不再增加 `allResetDone` 锁存。
- `contextFlush` 仅在 `s_waiting → s_flushing` 迁移的那 1 拍拉高，之后立即拉低；但状态机仍停留在 `s_flushing` 等待聚合 `resetDone`，与 §2.1 所述一致。
- `bpuFlushing` = `contextFlush || (flushState === s_flushing)`，是覆盖整个刷新窗口的电平信号：T 拍随 `contextFlush` 拉高，此后由 `s_flushing` 态维持，直到聚合 `resetDone` 置位、状态机迁入 `s_done` 才拉低。它与 `contextFlush` 单拍脉冲解耦，专供寄存器型子预测器延长训练阻塞（动机详见 §5.4，下发详见 §5.3.3）。

---

### 5.3 刷新使能控制逻辑

本节回答两个问题：**predictors 中本次事务刷新哪些预测器，以及固定参与者如何加入分发与聚合**。控制链路为：

```text
逐预测器 CSR → currentFlushMask → phase-1 接受时锁存 activeFlushMask
                                      ├─ 门控 contextFlush
                                      ├─ 门控 bpuFlushing
                                      └─ 门控 predictorsDone 聚合
PHR / CommonHR ─────────────────────────→ 固定分发 + 固定加入 resetDone 聚合
```

- `currentFlushMask`：实时 CSR 配置。
- `activeFlushMask`：本次事务的 predictors 参与者快照，仅保存在 BPU 顶层。
- 子预测器不接收 `activeFlushMask`，只接收 BPU 顶层按对应 mask 位门控后的 `contextFlush` 和 `bpuFlushing`。
- PHR/CommonHR 不属于 `predictors`、不占 mask 位，固定参与每次已接受事务。

#### 5.3.1 生成 `currentFlushMask`

BPU 顶层按 `predictors` 的固定顺序，将逐预测器 CSR 使能编码成 `currentFlushMask`：

```scala
  val currentFlushMask = VecInit(predictors.map(p => getSubFlushEnable(p))).asUInt
  fc.io.flushMask := currentFlushMask
```

| mask 位 | `predictors` 元素 | 使能来源 |
|:--:|:--|:--|
| 0 | fallThrough | 固定 1（无可刷状态，统一参与） |
| 1 | uBTB | `ubtbFlushEnable` |
| 2 | aBTB | `abtbFlushEnable` |
| 3 | uTAGE | 固定 1（随总使能） |
| 4 | uRAS | 固定 1（随总使能） |
| 5 | mBTB | `mbtbFlushEnable` |
| 6 | TAGE | `tageFlushEnable` |
| 7 | SC | `scFlushEnable` |
| 8 | ITTAGE | `ittageFlushEnable` |
| 9 | RAS | `rasFlushEnable` |


`getSubFlushEnable` 对当前全部预测器类型做穷举映射；若未来向 `predictors` 增加新类型但未定义刷新策略，则在 elaboration 阶段直接报错，避免 `_ => true.B` 静默把新预测器纳入刷新：

```scala
def getSubFlushEnable(p: BasePredictor): Bool = p match {
  case _: MicroBtb => ctrl.ubtbFlushEnable.get
  case _: AheadBtb => ctrl.abtbFlushEnable.get
  case _: MainBtb  => ctrl.mbtbFlushEnable.get
  case _: Tage     => ctrl.tageFlushEnable.get
  case _: Sc       => ctrl.scFlushEnable.get
  case _: Ittage   => ctrl.ittageFlushEnable.get
  case _: Ras      => ctrl.rasFlushEnable.get
  case _: FallThroughPredictor => true.B
  case _: MicroTage            => true.B
  case _: MicroRas             => true.B
  case other => throw new IllegalArgumentException(
    s"missing BPU flush-mask mapping for ${other.getClass.getName}"
  )
}
```
- `uTAGE`/`uRAS` 无独立使能位，由各自的显式类型分支返回 `true.B`，随总使能 `bpuFlushEn` 一起刷新。
- `fallThrough` 虽无可刷新状态，但仍是 `predictors` 中的 `BasePredictor` 实例；为保持 `predictors` 的统一遍历及 `BasePredictorIO` 接口结构一致，`HasBpuFlush=true` 时仍保留固定为 1 的 mask 位，以及 `contextFlush`、`bpuFlushing`、`resetDone` 三个刷新端口。前两个输入在 `FallThroughPredictor` 中为空操作，`resetDone` 恒为 `true.B`，不阻塞完成聚合。

#### 5.3.2 锁存 `activeFlushMask`

`currentFlushMask` 会随 CSR 实时变化，不能直接用于一笔正在执行的刷新事务。`BpuFlushCtrl` 只在 phase-1 被接受时锁存它：

```scala
val acceptFlush = (flushState === s_idle) && io.flush && io.bpuFlushEn
when(acceptFlush) {
  activeFlushMask := io.flushMask
}
```

锁存后，即使软件在 `waiting` 或 `flushing` 期间修改 CSR，本次事务的 `activeFlushMask` 也保持不变；新配置从下一次 `acceptFlush` 起生效。

#### 5.3.3 顶层门控并分发刷新信号

BPU 顶层使用 `activeFlushMask(i)` 分别门控每个预测器的刷新信号：

- mask 位为 1：该预测器收到单拍 `contextFlush` 执行清零，并在 `bpuFlushing` 有效期间阻塞训练。
- mask 位为 0：该预测器两个输入均为 0，不清零，也不阻塞训练。

```scala
predictors.zipWithIndex.foreach { case (p, i) =>
  p.io.contextFlush.get := fc.io.contextFlush && fc.io.activeFlushMask(i)
  p.io.bpuFlushing.get  := fc.io.bpuFlushing  && fc.io.activeFlushMask(i)
}

phr.io.contextFlush.get := fc.io.contextFlush
phr.io.bpuFlushing.get  := fc.io.bpuFlushing

commonHR.io.contextFlush.get := fc.io.contextFlush
commonHR.io.bpuFlushing.get  := fc.io.bpuFlushing
```

`activeFlushMask` 无需增加到子预测器 I/O；子预测器收到的 `contextFlush` 和 `bpuFlushing` 已经是针对自身的门控结果。PHR/CommonHR 的信号不经 mask，不受逐预测器 CSR 使能影响。`contextFlush` 负责清零，`bpuFlushing` 负责完整窗口内的旧数据阻断，详见 §5.2.2、§5.4。

#### 5.3.4 聚合全部参与者完成信号

各参与模块独立维护 `resetDone`。BPU 顶层先对 `activeFlushMask` 选中的 predictors 进行与归约，再与 PHR/CommonHR 的完成电平共同聚合（实现见 §5.2.2）：

```scala
val predictorsDone = predictors.zipWithIndex.map { case (p, i) =>
  !fc.io.activeFlushMask(i) || p.io.resetDone.get
}.reduce(_ && _)

fc.io.resetDone := predictorsDone && phr.io.resetDone.get && commonHR.io.resetDone.get
```

`resetDone` 为完成电平的与归约，状态机在 `s_flushing` 中直接使用它。各参与模块可在不同周期完成刷新；先完成者必须将自身 `resetDone` 保持为高，直至下一次有效 `contextFlush`，使顶层与归约能够等待最慢参与者完成。

未被 `activeFlushMask` 选中的预测器收不到有效 `contextFlush`，并由 `!activeFlushMask(i)` 在顶层聚合中直接视为完成，因而不依赖其 `resetDone` 当拍取值。对被选中的预测器以及固定参与的 PHR/CommonHR，`resetDone` 必须遵守“收到有效 `contextFlush` 后为低，本地清零完成后保持为高直至下一次有效 `contextFlush`”的接口契约。

- **与 `sramResetDone` 的区别**：本文 `resetDone` 用于上下文切换刷新握手；`sramResetDone` 是各预测器对其内部 SRAM `resetDone` 的聚合，底层时序由通用 `SRAMTemplate`/`FoldedSRAMTemplate` 定义，用于上电或 `extraReset` 清零完成并门控 `s0_fire`。二者相互独立，本文不重新定义 SRAM 模板内部语义。

#### 5.3.5 设计闭环

`activeFlushMask` 必须同时用于信号分发和完成聚合，不能一处使用实时 CSR、另一处使用锁存值：

| 场景 | 预期结果 |
|:--|:--|
| `activeFlushMask(i)=1` | 向预测器发送刷新信号，并等待其 `resetDone=1` |
| `activeFlushMask(i)=0` | 不发送刷新信号，并通过 `!activeFlushMask(i)` 将其视为已完成 |
| PHR/CommonHR | 不经 mask 固定发送刷新信号，并固定等待两者 `resetDone=1` |
| 事务期间 CSR 改写 | 不影响本次分发和完成聚合，只影响下一次事务 |

至此形成完整闭环：**predictors 生成实时配置 → 事务开始时锁存 → 按同一 mask 分发与聚合；PHR/CommonHR 固定分发与聚合 → 所有参与者完成后结束事务**。

---

### 5.4 旧数据流入防护：bpuFlushing

§4.3 的"提前触发安全"论证仅覆盖**触发时刻**的状态清零，未覆盖**触发之后**的旧数据流入：`contextFlush` 是单拍脉冲（T 拍清零、T+1 拍寄存器型状态即干净），但 BPU 状态机仍停留在 `s_flushing` 等待所有参与者（含多拍清零的 SRAM 型）的聚合 `resetDone`（见 §2.3.2 时序表）。
**寄存器型子预测器**（uBTB、uRAS、RAS）1 拍清零、`resetDone` 在 T+1 即置位，若其训练保护仅绑 `contextFlush` 单拍，则 T+1 起训练通路即恢复，而此时 BPU 仍在 flushing，旧上下文指令的训练数据可能仍在到达并写入已清空的预测器，污染新上下文。

为此，BPU 顶层新增覆盖**整个刷新窗口**的电平信号 `bpuFlushing`（生成见 §5.2.2，下发见 §5.3.3），与 `contextFlush` 单拍脉冲**解耦**：

- **清零**仍由 `contextFlush` 单拍负责（避免清零组合链每拍重跑的时序问题，如 uBTB 的 16 级 `flushTouches` 链）；
- **训练阻塞**改由 `bpuFlushing` 电平负责，覆盖 T ~ 状态机离开 `s_flushing`。

`bpuFlushing`（窗口阻断）与 `resetDone`（状态机迁移）**职责解耦**——前者维持到聚合 `resetDone` 置位，后者仅反映本模块自身清零完成。各寄存器型预测器用 `bpuFlushing` 延长训练阻塞（uBTB 详见 [02-uBTB §4.4](02-uBTB设计分析与刷新方案.md)）；SRAM 型预测器使用本地复位窗口（如 `inResetWindow` 或 `flushPending`）覆盖清零过程，并由 `bpuFlushing` 将读写、输出与训练门控延长至全局窗口。PHR 在窗口内阻断 redirect、S3 override、S1 prediction 更新及旧训练历史输出（见 [11-PHR §4.4/§4.5](11-PHR设计分析与刷新方案.md)）；CommonHR 阻断 S0～S3 流水推进、redirect、override 与历史输出（见 [12-CommonHR §4.3/§4.4](12-CommonHR设计分析与刷新方案.md)）。

---

## 6. 子模块真实刷新统一契约

02～12 共同构成完整 BPU 刷新设计。所有有可刷状态的参与模块都必须消费 `contextFlush`/`bpuFlushing`，实现真实清零、窗口阻断和完成握手；不得以 `resetDone := true.B` 恒真占位代替真实完成条件。无可刷状态的 `FallThroughPredictor` 以 `resetDone := true.B` 表示实时完成，这是其真实契约而非占位。具体职责索引如下：

| 范围 | 参与方式 | 具体方案 |
|:--|:--|:--|
| 02～10 预测器 | 位于 `predictors`，按 `activeFlushMask` 选择参与 | [02-uBTB](02-uBTB设计分析与刷新方案.md)、[03-aBTB](03-aBTB设计分析与刷新方案.md)、[04-mBTB](04-mBTB设计分析与刷新方案.md)、[05-uTAGE](05-uTAGE设计分析与刷新方案.md)、[06-RAS](06-RAS设计分析与刷新方案.md)、[07-uRAS](07-uRAS设计分析与刷新方案.md)、[08-TAGE](08-TAGE设计分析与刷新方案.md)、[09-SC](09-SC设计分析与刷新方案.md)、[10-ITTAGE](10-ITTAGE设计分析与刷新方案.md) |
| PHR | 不在 `predictors`，不经 mask 固定参与 | [11-PHR](11-PHR设计分析与刷新方案.md) |
| CommonHR | 不在 `predictors`，不经 mask 固定参与 | [12-CommonHR](12-CommonHR设计分析与刷新方案.md) |

### 6.1 统一刷新处理模板

各参与模块的刷新专用模块、寄存器、存储、`RegNext`、清零 `when` 和完成逻辑必须落在 `if (HasBpuFlush) { ... }` **之内**（内部用 `.get` 访问端口），使开关关闭时不参与 elaboration。对既有数据通路追加的门控可由 Scala `if` 在关闭分支返回原表达式，也可返回 `true.B`/`false.B` 恒等项并由 Chisel/CIRCT 在生成 RTL 前完成常量折叠；最终必须与刷新前基线数据通路一致。禁止用 `getOrElse` 代替刷新专用结构的 Scala 守卫，可选端口仍应仅在 `HasBpuFlush=true` 的分支内通过 `.get` 访问。

**参考模板**（四类消费点 before → after）：

```scala
// (a) 清零：last-connect 覆盖，置于正常写之后
//   before:  when(io.contextFlush) { reg := 0.U }
if (HasBpuFlush) { when(io.contextFlush.get) { reg := 0.U } }

// (b) 输入门控：整式放进 if，else 给无门控原式
//   before:  t0_fire := raw && !io.bpuFlushing
t0_fire := (if (HasBpuFlush) raw && !io.bpuFlushing.get else raw)

// (b.1) 等价的恒等项写法；关闭时 && true.B 由 Chisel/CIRCT 折叠
t0_fire := raw && (if (HasBpuFlush) !io.bpuFlushing.get else true.B)

// (c) 输出 Mux
//   before:  val out = Mux(io.contextFlush, 0.U, rawOut)
val out = if (HasBpuFlush) Mux(io.contextFlush.get, 0.U, rawOut) else rawOut

// (d) 刷新辅助 valid：关闭分支不生成 RegNext；后续 && true.B 恢复原 Mux 条件
val responseValid =
  if (HasBpuFlush) !io.bpuFlushing.get || RegNext(requestValid, false.B)
  else true.B
val response = Mux(responseValid && originalCond, tableResult, 0.U)

```

**反模式（必须避免）**：

| 写法 | 问题 |
|------|------|
| `val cf = io.contextFlush.getOrElse(false.B); when(cf){...}` | 用常量替代结构性守卫，`when` 体仍参与 elaboration；若其中声明模块、寄存器或存储，不能保证关闭配置下结构不生成 |
| `raw && io.bpuFlushing.map(!_).getOrElse(true.B)` | 虽可常量折叠，但隐藏 `HasBpuFlush` 裁剪边界，不采用；改用上文显式内联 Scala `if` |
| `when(io.contextFlush.get){...}` 不套 `if` | 关闭时端口 `None`，`.get` 抛异常 |

上述恒等项写法的合规对象仅限对既有组合数据通路追加的门控。关闭配置验收时，应确认刷新分支中的 `RegNext` 等结构不存在，且生成 RTL 中新增的 `&& true.B`、`|| false.B` 或常量 Mux 选择已消除；原数据通路本来存在的 Mux 无需消失。

本节模板为 02～12 的统一编码契约；每个模块的具体清零对象、窗口门控与真实完成条件以对应 02～12 文档为准。

---

## 7. 已知问题记录：刷新使能与 phase-1 `flush` 的时序关系

sticky `BPU_FLUSH_EN` 不允许开启后的 1→0 切换，因此不存在“已开启机制被软件中途关闭”的竞争；但 `HasBpuFlushDefault=false` 时仍允许首次 0→1。此外，bit[14:8] 逐预测器刷新使能保持运行时可写。两类写入都需考虑与新触发通路的时序关系。**本节只记录问题现象、成立条件和潜在风险；本文不继续验证、不提供规避方案，也不将其作为当前顶层方案的验收项。**

BPU 中的 CSR 控制信号不直接使用 `io.ctrl`，而是为时序目的统一延迟两拍：

```scala
private val ctrl = DelayN(io.ctrl, 2)
```

刷新状态机在 phase-1 `io.flush` 有效当拍采样 `ctrl.bpuFlushEn` 并锁存 `currentFlushMask`。两者都经过两拍延迟。由于 phase-1 `io.flush` 是单拍脉冲，若该拍仍为旧值，本次事务不会在后续周期自动补采样新配置。

因此需要论证：当软件首次写 `BPU_FLUSH_EN=1` 或修改逐预测器刷新使能后紧跟 `fence.i` 时，新总使能和 mask 能否在 phase-1 `io.flush` 到达 BPU 前传递完成。

若 phase-1 `io.flush` 早于 CSR 新值到达，可能出现两种结果：首次开启值尚未到达时，本次刷新被忽略；逐预测器配置尚未到达时，状态机锁存旧 mask。该问题的核心是确认：**phase-1 `io.flush` 有效当拍，`ctrl.bpuFlushEn` 与 `currentFlushMask` 是否已反映最近一次架构上生效的 CSR 写入**。
