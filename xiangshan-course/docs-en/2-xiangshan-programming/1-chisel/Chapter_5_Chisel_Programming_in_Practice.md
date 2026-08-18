# 第五章 Chisel 编程实践

TODO

将当前笔试的题，生成对应的教程

***

# 🎓 用 Chisel 造一个非阻塞缓存——从零开始的硬件设计之旅

> **如果你是第一次接触 Chisel，看到 **`VecInit`**、**`SyncReadMem`**、**`ChiselEnum`\*\* 这些关键词可能会感到不知所措。别担心——最好的学习方式就是动手造一个真实的东西。\*\* 今天我们就要从零开始，一步一步造出一个支持多未完成请求的非阻塞缓存。每造一个零件，你就学会一批 Chisel 技能。等这个 Cache 跑通的那一刻，你会发现 Chisel 已经是你的老朋友了。

:::info
**通过本章，你将——**

理解层面：

* ✅ 理解非阻塞缓存为什么比阻塞缓存更"聪明"
* ✅ 建立整个系统的宏观架构认知
* ✅ 洞悉一次缓存请求从发出到返回的完整生命周期

实践层面：

* ✅ 从零搭建开发环境，跑通第一次仿真
* ✅ 亲手造出 Bram → CacheTag → CacheData → CacheCtrl → MSHR → 顶层组装
* ✅ 掌握 Chisel 最核心的技能集：模块定义、寄存器、存储器、状态机、向量操作、测试

规划层面：

* ✅ 获得从入门到精通的分阶段学习路线图

:::

***

# 一、项目全景——我们要造什么？

## 1.1 非阻塞缓存：为什么不让你干等？

**核心思想：阻塞缓存让 CPU 干等，非阻塞缓存让 CPU 不闲着。**

想象你在一个图书馆借书。**阻塞缓存**就像每次你想要的书不在架上，你就必须站在原地等管理员去仓库取——在这期间你什么也做不了。而**非阻塞缓存**就像你可以继续浏览其他书架，管理员把书取回来后会通知你——等待期间你仍然可以发出新的借阅请求。

本项目实现了一个 **16KB、4路组相联、非阻塞 L1 数据缓存**，用 Chisel 3 硬件描述语言编写，设计用于 RV64 系统中 CPU 与主存之间的桥梁。

**仓库地址**：<https://github.com/HisionWang/NonBlockingCache>

本项目最初是一道开芯院的笔试题——设计一个 Non-blocking Cache（支持多 outstanding miss），用于连接 CPU 与 memory。要求如下：

* Cpu 请求的 PA 宽度 48bit（假设 RV64）
* Memory 接口 64bit
* Data cache: 16-KB, 4-way set associative, 64-byte line size
* 支持至少 4 个 miss 请求而不阻塞
* 给出 RTL 代码以及验证代码和结果

## 1.2 设计规格一览

| 参数 | 值 | 含义 |
| --- | --- | --- |
| 缓存容量 | 16 KB | 64组 × 4路 × 64字节 |
| 组织结构 | 4路组相联 | 每组 4 个缓存行候选 |
| 缓存行大小 | 64 字节 | 每次从内存搬运的数据块 |
| 地址宽度 | 48 位 | RV64 物理地址 |
| Tag / Index / Offset | 36 / 6 / 6 位 | 地址三段式分解 |
| CPU 数据接口 | 64 位 | 单次读写 64 位 |
| 内存数据接口 | 64 位突发 | 8 周期传完一个缓存行 |
| 替换算法 | Tree-PLRU | 3 位树实现近似 LRU |
| 非阻塞能力 | 4 个不重叠 / 8 个部分重叠 miss | MSHR 4项 + Load Table 8项 |

**它"非阻塞"在哪里？** 体现在三个层面：

1. **多未完成请求**：CPU 发出 miss 后无需等待，可继续发出新的请求
2. **请求合并**：多个 miss 如果访问同一缓存行，只发一次内存请求——就像多个人要同一本书，管理员只跑一趟
3. **乱序响应**：内存返回数据的顺序可以和请求发出的顺序不同——先回来的先处理，不浪费等待时间

## 1.3 整体架构：一座"现代化工厂"

**核心思想：理解系统 = 理解数据从哪里来、经过谁、到哪里去。**

你可以把整个系统想象成一座**现代化工厂**，由三个"车间"协同工作：

```plain
┌─────────────┐     ┌──────────────────────────────────────┐     ┌─────────────┐
│   CPU 车间  │───▶│         缓存核心车间                  │────▶│  内存车间   │
│ (FakeCPU)   │◀───│  (Tag + Data + Ctrl + MSHR + Bram)   │◀───│ (FakeRAM)   │
└─────────────┘     └──────────────────────────────────────┘     └─────────────┘
   下单 & 验收               加工 & 调度                      供货 & 配送
```

* **CPU 车间**：不断发出"我要读这个地址"的请求，并验收返回的数据是否正确
* **缓存核心车间**：判断数据是在本地（命中）还是需要向内存车间调货（缺失），负责整个调度流程
* **内存车间**：按请求配送数据，但配送时间不确定（随机延迟），甚至可能先送后到的订单

> 📊 **图表解读**：更详细的架构图请查看 [架构图](https://github.com/HisionWang/NonBlockingCache/blob/main/doc/架构图.svg)，你会看到三个主要子系统——**标记存储**（CacheTag + PLRU）、**数据存储**（CacheData + 分体 BRAM）和**非阻塞控制平面**（CacheCtrl + MSHR）。所有模块间的连线由顶层 `NonBlockingCache` 管理，它本身只是一个纯粹的"布线容器"。

:::info
现阶段你只需要理解"三个车间"的大致分工即可，不必深究每个模块内部的时序细节。我们会在后面的实操章节逐一拆解。

:::

## 1.4 核心组件：谁负责什么？

**核心思想：每个模块都有明确的单一职责——知道"谁负责什么"，就理解了系统。**

### 设计模块

| 模块 | 文件 | 职责 | 比喻 |
| --- | --- | --- | --- |
| Bram | [Bram.scala](src/main/scala/Bram.scala) | 双端口 BRAM 原语封装 | 基础砖块 |
| CacheTag | [CacheTag.scala](src/main/scala/CacheTag.scala) | 4路并行标签比较，Tree-PLRU 替换算法 | 仓库目录索引 |
| CacheData | [CacheData.scala](src/main/scala/CacheData.scala) | Bank 存储阵列，并行数据读写 | 仓库货架 |
| CacheCtrl | [CacheCtrl.scala](src/main/scala/CacheCtrl.scala) | 命中/缺失状态机，调度 MSHR，管理突发传输 | 生产线主管 |
| MSHR | [MSHR.scala](src/main/scala/MSHR.scala) | 追踪未完成缺失，请求合并，乱序响应 | 订单跟踪系统 |
| NonBlockingCache | [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala) | 集成所有子模块，定义 CPU 与内存接口 | 工厂总调度室 |

### 验证模块

| 组件 | 文件 | 职责 | 比喻 |
| --- | --- | --- | --- |
| CacheSpec | [CacheSpec.scala](src/test/scala/CacheSpec.scala) | 集成测试环境，控制仿真流程 | 质检主管 |
| FakeCPU | [FakeCPU.scala](src/test/scala/FakeCPU.scala) | 生成带局部性的请求，实时验证返回数据 | 模拟客户 |
| FakeRAM | [FakeNonBlockingRAM.scala](src/test/scala/FakeNonBlockingRAM.scala) | 随机延迟响应，支持突发传输和乱序返回 | 模拟供应商 |
| GlobalVars | [GlobalVars.scala](src/test/scala/GlobalVars.scala) | 测试数量、随机种子等参数 | 实验参数表 |
| Tool | [Tool.scala](src/test/scala/Tool.scala) | 数据格式化、RAM 数据生成 | 辅助工具箱 |

## 1.5 一次请求的生命周期

**核心思想：数据从 CPU 发出请求到最终返回，经过了完整的"命中检查 → 缺失处理 → 数据回填"流水线。**

让我们跟踪一次\*\*缓存缺失（Cache Miss）\*\*的完整旅程：

```plain
CPU 发出请求
    │
    ▼
┌─────────────┐  命中？──是──▶ 直接返回数据 ──▶ CPU 验收 ✓
│  标签比较    │
│  (CacheTag) │
└──────┬──────┘
       │ 否
       ▼
┌─────────────┐
│  MSHR 登记  │──── 同一地址已有 miss？──是──▶ 请求合并（不重复发内存请求）
│ (MSHR.scala)│
└──────┬──────┘
       │ 否（新缺失）
       ▼
┌─────────────┐
│ 发起内存请求 │────▶ 64位突发传输（8个周期填满64字节）
│ (CacheCtrl) │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 乱序数据返回 │──── 内存可能先返回后发的请求，没关系！
│ (MSHR 处理) │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  缓存行回填  │──── 写入 CacheData + 更新 CacheTag
│  + 数据返回  │
└──────┬──────┘
       │
       ▼
   CPU 验收数据 ✓
```

> 💡 **关键洞察**：整个过程最精妙之处在于——当 CPU 在等待某个 miss 的数据时，它可以继续发出新的请求。如果新请求命中了缓存，立即返回；如果又 miss 了，MSHR 会记录下来，等内存空闲时再处理。这就是"非阻塞"的本质。

***

# 二、开发环境搭建——把工厂建起来

**核心思想：工欲善其事，必先利其器。先把环境跑通，后面的学习才能心无旁骛。**

## 2.1 环境要求

| 依赖 | 版本要求 | 检查命令 | 安装指引 |
| --- | --- | --- | --- |
| JDK | 8 或更高 | `java -version` | [Adoptium](https://adoptium.net/) |
| sbt | 最新版 | `sbt --version` | [sbt 官方文档](https://www.scala-sbt.org/download.html) |

> Chisel 和 Scala 的依赖由 sbt 自动管理，无需手动安装。本项目使用的版本为 Scala 2.13.14 + Chisel 3.6.1 + ChiselTest 0.6.2，详见 [build.sbt](https://github.com/HisionWang/NonBlockingCache/blob/main/build.sbt)。

## 2.2 获取项目

```bash
git clone https://github.com/HisionWang/NonBlockingCache.git
cd NonBlockingCache
```

## 2.3 项目结构一览

```plain
NonBlockingCache/
├── README.md                        # 项目说明
├── build.sbt                        # sbt 构建配置（Chisel 版本、依赖）
├── project/                         # sbt 项目元数据
│   └── build.properties
├── src/
│   ├── main/scala/                  # 📌 设计代码（我们手写的部分）
│   │   ├── Bram.scala               #   双端口 BRAM 原语
│   │   ├── CacheTag.scala           #   标签存储 + PLRU
│   │   ├── CacheData.scala          #   数据存储阵列
│   │   ├── CacheCtrl.scala          #   缓存控制器
│   │   ├── MSHR.scala               #   Miss 状态保持寄存器
│   │   └── NonBlockingCache.scala   #   顶层模块
│   └── test/scala/                  # 📌 测试代码（验证设计正确性）
│       ├── CacheSpec.scala          #   主测试套件
│       ├── FakeCPU.scala            #   模拟 CPU
│       ├── FakeNonBlockingRAM.scala #   模拟内存
│       ├── GlobalVars.scala         #   全局配置参数
│       └── Tool.scala               #   工具函数
├── TestData/                        # 测试数据（自动生成）
├── generated/                       # 生成的 Verilog 代码
├── doc/                             # 设计文档和验证结果
│   ├── 设计、验证报告.pdf
│   ├── 架构图.svg
│   └── 验证结果（1000项测试）/
└── test_run_dir/                    # 测试运行时输出（波形等）
```

## 2.4 开发流程：从设计到验证

**核心思想：Chisel 开发 = 写设计 → 仿真验证 → 生成 Verilog，三步走。**

如果你把设计工具想象成**建筑师的绘图板**，仿真工具就像**在工厂里用模型测试产品**，那么整个开发流程就是：

```plain
   Chisel 代码         ChiselTest 仿真           Verilog 输出
  (画设计图纸)    →    (用模型验证设计)      →    (出施工图纸)
   sbt compile         sbt test                  sbt run
```

| 阶段 | 命令 | 做什么 | 预期耗时 |
| --- | --- | --- | --- |
| 编译 | `sbt compile` | 将 Chisel/Scala 代码编译 | 首次约 2-5 分钟（下载依赖） |
| 测试 | `sbt test` | 运行仿真验证 | 50 条约 20 秒，1000 条约数分钟 |
| 生成 | `sbt run` | 输出 Verilog 文件 | 约 20 秒 |

> 💡 **小技巧**：首次运行 `sbt` 会下载大量依赖，耐心等待即可。如果你经常使用，可以在 shell 配置中设置别名：`alias st='sbt test'`。

## 2.5 第一次运行：5 分钟跑通测试

### 第一步：调整测试参数（可选）

编辑 [GlobalVars.scala](src/test/scala/GlobalVars.scala) 自定义测试规模：

```scala
object GlobalVars {
  val TEST_NUM = 50      // 测试请求数量，首次建议 10-50
  var randomSeed: Long = 6688L  // 随机种子，改变它 = 不同的测试场景
  val TEST_CYCEL = 10000  // 最大测试周期数
}
```

> 💡 **小技巧**：首次测试建议将 `TEST_NUM` 设为 `10`，几十秒就能看到完整结果，增强信心。确认通过后再调大到 50 或 1000。

### 第二步：运行测试

```bash
sbt test
```

### 第三步：读懂输出

**预期输出**（以 1 条请求为例）：

```latex
[info] welcome to sbt 1.12.0 (Ubuntu Java 21.0.10)
[info] loading settings for project nonblockingcache from build.sbt...

Generating RAM data in ./TestData/Ram.txt ......
Generating request data in ./TestData/Req.txt ......
Start testing ......

[CPU ] SEND     [Time    2] REQ [  0] addr=0x8a505179bda1 rd=21
[RAM ] RECEIVE  [Time    5] REQ [  0] addr=0x00008a505179bda1
[RAM ] BACKDATA [Time   17] REQ [  0] addr=0x00008a505179bda1

[CPU ] RECIEVE  [Time   28] REQ [  0] rd=21 data=0x3bc67b4522d425d6
┌──────────────────────────────────────────────────────────────────┐
│ [VERIFY] SendReq: 0                                              │
├──────────────────────────────────────────────────────────────────┤
│ Addr        : 0x00008a505179bda1                                 │
│ LineIndex   : 0x36       BankIndex   : 0x04                      │
├──────────────────────────────────────────────────────────────────┤
│ Expected    : Data: 0x3bc67b4522d425d6 RdIdx: 21                 │
│ Actual      : Data: 0x3bc67b4522d425d6 RdIdx: 21                 │
└──────────────────────────────────────────────────────────────────┘
 --------> PASS

[info] All tests passed.
[success] Total time: 20 s
```

**输出解读**：

| 日志标签 | 含义 |
| --- | --- |
| `[CPU ] SEND` | CPU 发出了一个请求，包含地址和寄存器号 |
| `[RAM ] RECEIVE` | 内存收到了这个请求 |
| `[RAM ] BACKDATA` | 内存返回了数据 |
| `[CPU ] RECIEVE` | CPU 收到了返回的数据 |
| `[VERIFY]` | 自动验证：Expected vs Actual 是否一致 |
| `PASS` | 这条请求验证通过 ✅ |

:::info
如果你看到了 `All tests passed`，恭喜你——你已经成功运行了一个非阻塞缓存的完整验证！如果你暂时看不懂日志中的地址分解（LineIndex、BankIndex），不用着急，这些会在后续步骤中详细讲解。

:::

### 第四步：生成 Verilog（进阶）

```bash
sbt run
```

生成的文件位于 `generated/` 目录：

| 文件 | 说明 |
| --- | --- |
| `NonBlockingCache.v` | Verilog 硬件描述文件——可以直接用于 FPGA 或综合工具 |
| `NonBlockingCache.fir` | FIRRTL 中间表示——Chisel 编译器的"汇编语言" |
| `NonBlockingCache.anno.json` | 注释信息——用于综合工具的元数据 |

***

# 三、动手造 Cache——Step 1：Bram，你的第一块砖

> **从本节开始，我们进入"边造边学"环节。** 每造一个零件，我都会先讲它"是什么"，再讲"怎么用 Chisel 写出来"，最后讲"Chisel 给了你什么新武器"。

**核心思想：一切伟大的建筑都从一块砖开始。Bram 是 Cache 中最基础的存储单元。**

## 3.1 我们需要什么？

我们需要一个**双端口 BRAM**——一个端口写、一个端口读。你可以把它想象成一个带编号的储物柜：

* **写**：告诉它"把 XX 东西放进第 N 号柜子"
* **读**：告诉它"把第 N 号柜子的东西拿出来给我"

## 3.2 Chisel 新武器：Module、IO、Bundle

在 Chisel 中，每一个硬件模块都是 `Module` 的子类。模块对外暴露的接口用 `IO(new Bundle { ... })` 定义，就像给模块开窗户——外面的人只能通过这些窗户和模块交互。

```scala
import chisel3._
import chisel3.util._

class CacheDualPortBRAM extends Module {
  val io = IO(new Bundle {
    // 写端口 —— "我要往第N号柜子放东西"
    val wr_en    = Input(Bool())      // 写使能：我要放东西了吗？
    val wr_index = Input(UInt(6.W))   // 写索引：放进哪个柜子？（6位 → 64个柜子）
    val wr_data  = Input(UInt(64.W))  // 写数据：放什么东西？
    
    // 读端口 —— "我要从第N号柜子取东西"
    val rd_en    = Input(Bool())      // 读使能：我要取东西了吗？
    val rd_index = Input(UInt(6.W))   // 读索引：从哪个柜子取？
    val rd_data  = Output(UInt(64.W)) // 读数据：取出来的东西
  })
```

> 📖 **Chisel 小课堂**：
>
> * `Input(Bool())` —— 一位输入信号，类似 Verilog 的 `input wire`
> * `Input(UInt(6.W))` —— 6 位无符号整数输入，`6.W` 表示位宽
> * `Output(UInt(64.W))` —— 64 位无符号整数输出
> * `Bundle` —— 一组信号的集合，类似 Verilog 的 `struct`

## 3.3 Chisel 新武器：SyncReadMem

Chisel 提供了 `SyncReadMem` 来推断 FPGA 上的 BRAM 资源。它和 `Reg` 的区别就像"储物柜"和"桌面"——`Reg` 是少量数据放桌面随时拿，`SyncReadMem` 是大量数据放柜子里，需要一个时钟周期才能取出来。

```scala
  // 数据存储器：64 个柜子，每个柜子放 64 位数据
  val data_mem = SyncReadMem(64, UInt(64.W))
```

> 📖 **Chisel 小课堂**：
>
> * `SyncReadMem(深度, 数据类型)` —— 同步读存储器，读操作需要一个时钟周期
> * 对比 `Mem`（异步读）和 `Reg`（组合逻辑读）
> * `SyncReadMem` 会被综合工具映射为 FPGA 上的真实 BRAM 资源

## 3.4 Chisel 新武器：when

写入操作只在 `wr_en` 为真时发生，这用 `when` 实现：

```scala
  // 写操作：如果写使能，就把数据写进指定位置
  when(io.wr_en) {
    data_mem.write(io.wr_index, io.wr_data)
  }
  
  // 读操作：根据读索引和读使能读取数据
  io.rd_data := data_mem.read(io.rd_index, io.rd_en)
```

> 📖 **Chisel 小课堂**：
>
> * `when(条件) { 操作 }` —— 类似 Verilog 的 `if`，但生成的是硬件选择器（MUX）
> * `data_mem.write(地址, 数据)` —— 写存储器
> * `data_mem.read(地址, 使能)` —— 读存储器，使能为真时下一周期输出数据
> * 链式条件：`when(...).elsewhen(...).otherwise(...)`，类似 `if / else if / else`

## 3.5 完整代码与回顾

完整代码见 [Bram.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/Bram.scala)，总共只有 33 行——你的第一个 Chisel 模块就这么简单！

```scala
class CacheDualPortBRAM extends Module {
  val io = IO(new Bundle {
    val wr_en    = Input(Bool())
    val wr_index = Input(UInt(6.W))
    val wr_data  = Input(UInt(64.W))
    val rd_en    = Input(Bool())
    val rd_index = Input(UInt(6.W))
    val rd_data  = Output(UInt(64.W))
  })
  val data_mem = SyncReadMem(64, UInt(64.W))
  when(io.wr_en) { data_mem.write(io.wr_index, io.wr_data) }
  io.rd_data := data_mem.read(io.rd_index, io.rd_en)
}
```

:::info
现在你只需要记住：**模块 = 接口 + 内部逻辑**。接口用 `IO(new Bundle)` 声明，存储用 `SyncReadMem`，条件用 `when`。这就是 Chisel 的基本骨架，后面的模块都是在这个骨架上添砖加瓦。

:::

***

# 四、动手造 Cache——Step 2：CacheTag，给仓库做索引

**核心思想：Tag 模块回答一个核心问题——"你要的数据在不在缓存里？在哪个路？"**

## 4.1 我们需要什么？

缓存有 4 路（Way 0 ~ Way 3），每路有 64 个组（Set），每个组存着一个标签。当 CPU 发来一个地址，Tag 模块需要：

1. 从地址中分解出 **Tag / Index / Offset**
2. 用 Index 找到 4 路中的标签
3. 和请求的 Tag 比较 → 命中 or 缺失
4. 如果缺失，决定替换哪一路（Tree-PLRU）

## 4.2 Chisel 新武器：参数化模块

我们的模块接受三个参数——地址各字段的位宽。这让模块可以复用到不同配置的缓存中：

```scala
class CacheTag(
  INDEX_WD: Int,   // Index 位宽 = 6
  TAG_WD: Int,     // Tag 位宽 = 36
  OFFSET_WD: Int   // Offset 位宽 = 6
) extends Module {
```

> 📖 **Chisel 小课堂**：
>
> * 参数在 `()` 中声明，类型是 Scala 的 `Int`（不是 Chisel 的 `UInt`！）
> * 这些参数在**编译时**确定，用来控制生成多少硬件——就像 Verilog 的 `parameter`
> * 但比 Verilog 更强大：你可以用 Scala 的全部能力（循环、条件、函数）来生成硬件

## 4.3 Chisel 新武器：位提取——地址分解

48 位地址被切分成三段，这是缓存设计最基础的操作：

```scala
val OFFSET_LSB = 0
val OFFSET_MSB = OFFSET_WD - 1       // 5
val INDEX_LSB  = OFFSET_WD            // 6
val INDEX_MSB  = OFFSET_WD + INDEX_WD - 1  // 11
val TAG_LSB    = INDEX_MSB + 1        // 12
val TAG_MSB    = TAG_LSB + TAG_WD - 1 // 47

// 从 48 位地址中切出各字段——就像切蛋糕 🎂
val req_offset = io.req_addr(OFFSET_MSB, OFFSET_LSB)  // [5:0]   块内偏移
val req_index  = io.req_addr(INDEX_MSB, INDEX_LSB)     // [11:6]  组索引
val req_tag    = io.req_addr(TAG_MSB, TAG_LSB)         // [47:12] 标签
```

> 📖 **Chisel 小课堂**：
>
> * `x(高位, 低位)` —— 位提取，类似 Verilog 的 `x[高:低]`
> * 注意 Chisel 的参数顺序是**高位在前**，和 Verilog 相同

## 4.4 Chisel 新武器：RegInit + VecInit——寄存器向量

每一路需要 64 个标签存储项，每项是"1 位 Valid + 36 位 Tag"：

```scala
val INDEX_NUM = 1 << INDEX_WD  // 64

// Way0 的标签存储：64 个项，每项 37 位，初始全 0
val tag_way0 = RegInit(
  VecInit(Seq.fill(INDEX_NUM)(
    0.U((TAG_WD + 1).W)  // 1位Valid + 36位Tag
  ))
)
```

这行代码做了三件事：

1. `Seq.fill(64)(0.U(37.W))` —— Scala 层面生成 64 个 37 位零值的序列
2. `VecInit(...)` —— 将 Scala 序列转为 Chisel 的硬件向量
3. `RegInit(...)` —— 将整个向量初始化为给定值，复位后生效

> 📖 **Chisel 小课堂**：
>
> * `Seq.fill(N)(value)` —— Scala 标准库函数，创建 N 个相同元素的序列
> * `VecInit` —— 把 Scala 序列包装成 Chisel 的 `Vec`，使其可以动态索引
> * `RegInit` —— 带复位初值的寄存器，类似 Verilog 的 `reg [37:0] tag_way0 [0:63] = {64{37'b0}}`
> * `tag_way0(index)` —— 动态索引读取，硬件会综合出 MUX

## 4.5 Chisel 新武器：Cat——位拼接 + 命中判断

判断命中的逻辑：把 Valid 位和请求的 Tag 拼起来，和存储的标签比较：

```scala
// 把 Valid(1) 和 req_tag 拼成 37 位
val validTag = Cat(1.U(1.W), req_tag)

// Way0 命中？——请求有效 且 标签匹配
val hit_way0 = req && (validTag === tag_way0(req_index))
// Way1、Way2、Way3 同理 ...

// 命中信号：4位独热码，哪一位为1表示命中了哪一路
val hit  = Cat(hit_way3, hit_way2, hit_way1, hit_way0)
// 缺失信号：没有任何一路命中
val miss = req && ~(hit_way0 || hit_way1 || hit_way2 || hit_way3)
```

> 📖 **Chisel 小课堂**：
>
> * `Cat(a, b, c)` —— 位拼接，高位在前。类似 Verilog 的 `{a, b, c}`
> * `===` —— Chisel 的**硬件比较**运算符。注意是三个等号！`==` 是 Scala 的引用比较
> * `.orR` —— 对向量的所有位做 OR 归约。`hit.orR` 等价于"至少有一路命中"

## 4.6 Tree-PLRU 替换算法（选读）

当缓存缺失时，需要决定替换哪一路。Tree-PLRU 用一棵 3 位二叉树来近似 LRU：

```plain
          b2
        /    \
      b1      b0
     /  \    /  \
   way0 way1 way2 way3
```

每次访问后，更新树的方向让指针"远离"刚访问的路。替换时，沿树的方向走到叶节点即可：

```scala
val lru = RegInit(VecInit(Seq.fill(INDEX_NUM)(0.U(3.W))))  // 64组，每组3位

// 访问 Way0 时更新树
when(hit_way0 || (replace_cache && replacePointer(0))) {
  lru(use_index) := Cat(1.U(1.W), 1.U(1.W), lru(use_index)(0))
}
// Way1、Way2、Way3 类似 ...

// 替换时沿树方向选择
val b2 = lru(replace_index)(2)
val b1 = lru(replace_index)(1)
val b0 = lru(replace_index)(0)
replacePointer := Mux(!b2,
  Mux(!b1, 1.U, 2.U),   // 0001:way0  0010:way1
  Mux(!b0, 4.U, 8.U)    // 0100:way2  1000:way3
)
```

> 📖 **Chisel 小课堂**：
>
> * `Mux(条件, 真值, 假值)` —— 二选一多路选择器，类似 Verilog 的 `条件 ? 真值 : 假值`
> * `lru(use_index)(0)` —— 双重动态索引：先选组，再选位

:::info
PLRU 的更新逻辑比较绕，不用一次理解透。先记住"3 棵树指针决定替换哪一路"就好，后面配合波形调试会豁然开朗。

:::

完整代码见 [CacheTag.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/CacheTag.scala)。

***

# 五、动手造 Cache——Step 3：CacheData，搭仓库货架

**核心思想：Data 模块负责存储和读取缓存的实际数据。它用 Bram 做基本单元，组织成 4 路 × 8 Bank 的存储阵列。**

## 5.1 我们需要什么？

一个缓存行 64 字节 = 8 × 64 位。每路需要 8 个 BRAM（每个存 64 位宽的一段），4 路共 32 个 BRAM。当命中时，需要根据地址从正确的路和 Bank 中读出数据。

## 5.2 Chisel 新武器：Seq.fill + for 循环生成硬件

这是 Chisel 比 Verilog 强大得多的地方——用 Scala 的循环来批量生成重复的硬件结构：

```scala
// 一个缓存行分成 8 个 Bank（每个 64 位）
val CACHE_LINE = 1 << (OFFSET_WD - 3)  // 8

// 一路数据 = 8 个 BRAM 实例
val Way0_brams = Seq.fill(CACHE_LINE)(Module(new CacheDualPortBRAM))
// 读数据线：8 根 64 位的线
val rdata_way0 = Wire(Vec(CACHE_LINE, UInt(64.W)))

// 用 for 循环给每个 BRAM 接线
for (n <- 0 until CACHE_LINE) {
  // 写信号
  val wr_en    = io.replace_fromCtrl && io.pointer_fromTag(0)  // Way0 被替换
  val wr_index = refill_index
  val startBit = n * 64
  val endBit   = (n + 1) * 64 - 1
  val wr_data  = io.newCacheline_fromCtrl(endBit, startBit)  // 从512位中切出64位
  
  Way0_brams(n).io.wr_en    := wr_en
  Way0_brams(n).io.wr_index := wr_index
  Way0_brams(n).io.wr_data  := wr_data
  
  // 读信号
  val rd_en    = io.req && bank_sel(n) && io.hit_fromTag(0)
  val rd_index = req_index
  Way0_brams(n).io.rd_en    := rd_en
  Way0_brams(n).io.rd_index := rd_index
  rdata_way0(n) := Way0_brams(n).io.rd_data
}
```

> 📖 **Chisel 小课堂**：
>
> * `Seq.fill(N)(Module(new SomeModule))` —— 批量实例化 N 个模块！这在 Verilog 中需要手写 N 遍或用 `generate`
> * `for (n <- 0 until N)` —— **Scala 编译期循环**，每次迭代生成不同的硬件连接
> * `Wire(Vec(N, UInt(64.W)))` —— 声明一个 N 元素的线网向量
> * `x(高位, 低位)` —— 从 512 位缓存行中切出第 n 个 64 位段

:::info
`Seq.fill` + `for` 循环生成硬件是 Chisel 最强大的能力之一。在 Verilog 中，你需要写 32 个 BRAM 的接线代码（或用 `generate`），而在 Chisel 中只需一个循环。这就像从"手工缝 32 件衣服"升级到"缝纫机批量生产"。

:::

## 5.3 Chisel 新武器：Mux1H——独热码多路选择

命中某一路后，需要从 8 个 Bank 的读数据中选出正确的那个。`Mux1H` 是 Chisel 提供的独热码选择器——当选择信号是独热码时，它比 `Mux` 链更高效：

```scala
// bank_sel 是独热码，如 00000010 表示选第 1 个 Bank
val bank_sel = 1.U << req_offset(5, 3)  // 地址[5:3]决定选哪个 Bank

// 从 8 个 Bank 读数据中选出命中的那个
val selected_way0_data = Mux1H(bank_sel.asBools, rdata_way0)

// 再从 4 路中选出命中的那路的数据
when(io.hit_fromTag(0)) {
  hit_selected_data := selected_way0_data
}.elsewhen(io.hit_fromTag(1)) {
  hit_selected_data := selected_way1_data
}.elsewhen(io.hit_fromTag(2)) {
  hit_selected_data := selected_way2_data
}.elsewhen(io.hit_fromTag(3)) {
  hit_selected_data := selected_way3_data
}.otherwise {
  hit_selected_data := 0.U
}
```

> 📖 **Chisel 小课堂**：
>
> * `Mux1H(选择向量, 数据向量)` —— 独热码选择器。当选择向量只有一位为 1 时，选中对应数据
> * `.asBools` —— 把 `UInt` 转成 `Vec[Bool]`，`Mux1H` 需要这种输入
> * `1.U << n` —— 生成独热码的经典方法，左移 n 位
> * `Mux1H` 比 `Mux` 链的综合结果更优，因为综合工具知道只有一条通路有效

完整代码见 [CacheData.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/CacheData.scala)。

***

# 六、动手造 Cache——Step 4：CacheCtrl，生产线主管

**核心思想：控制器是整个 Cache 的"大脑"，它协调 Tag 判断、数据读取、MSHR 操作和内存交互。**

## 6.1 我们需要什么？

控制器要处理两类事务：

1. **Hit 路径**：CPU 请求命中 → 通知 Data 模块读数据 → 两周期后返回给 CPU
2. **Miss 路径**：CPU 请求缺失 → 写入 MSHR → 等内存返回 → 回填缓存 → 返回给 CPU

这两条路径可以同时发生（MSHR 有数据就绪时，CPU 也可能命中），所以需要**两个并行状态机**。

## 6.2 Chisel 新武器：ChiselEnum + switch/is——状态机

Chisel 提供了 `ChiselEnum` 来定义状态机的状态，比手编数字编码更安全、更可读：

```scala
object CacheState extends ChiselEnum {
  val IDLE, CPU_BACK_ADDROK, CPU_BACK_DATA, MSHR_OK, WRITE_MSHR = Value
}
import CacheState._

// 处理 Hit 的状态机
val hitState = RegInit(IDLE)
switch(hitState) {
  is(IDLE) {
    when(mshr.io.cpu_rsp_valid) {       // MSHR 有就绪数据
      hitState := MSHR_OK
    }.elsewhen(io.req_fromCPU && io.hit_fromTag.orR) {  // CPU 命中
      hitState := CPU_BACK_ADDROK
    }.otherwise {
      hitState := IDLE
    }
  }
  is(MSHR_OK)         { hitState := IDLE }
  is(CPU_BACK_ADDROK) { hitState := CPU_BACK_DATA }
  is(CPU_BACK_DATA)   { hitState := IDLE }
}

// 处理 Miss 的状态机（独立寄存器，天然并行）
val missState = RegInit(IDLE)
switch(missState) {
  is(IDLE) {
    when(io.req_fromCPU && io.miss_fromTag && mshr.io.cpu_can_accept) {
      missState := WRITE_MSHR
    }
  }
  is(WRITE_MSHR) { missState := IDLE }
}
```

> 📖 **Chisel 小课堂**：
>
> * `ChiselEnum` —— 枚举类型，每个状态自动分配编码，不需要手写 `2'b00`、`2'b01`
> * `RegInit(IDLE)` —— 状态寄存器，复位值为 `IDLE`
> * `switch(state) { is(XX) { ... } }` —— 类似 Verilog 的 `case`，但编译器会检查是否穷举
> * 两个状态机用**不同的寄存器**（`hitState` 和 `missState`），天然并行运行

## 6.3 Chisel 新武器：RegNext——流水线寄存器

Hit 路径需要两个周期（地址握手 → 数据返回），中间需要用 `RegNext` 打一拍寄存器：

```scala
// 将 Data 模块的读出数据延迟一拍，和状态机的时序对齐
val rdata_fromData_reg = RegNext(io.rdata_fromData)
val rdIdx_fromCPU_reg  = RegNext(io.rdIdx_fromCPU)
val reqNum_fromCPU_reg = RegNext(io.reqNum_fromCPU)
```

> 📖 **Chisel 小课堂**：
>
> * `RegNext(x)` —— 自动生成一个寄存器，下一周期输出 x 的当前值
> * `RegNext(x, init)` —— 带复位初值的版本
> * 在流水线设计中，`RegNext` 是对齐时序的利器——就像在传送带上加一个工位

## 6.4 突发传输组装

内存以 64 位宽度突发传输，8 个周期传完一个 64 字节缓存行。控制器用计数器 + 缓冲区拼装数据：

```scala
val burst_counter = RegInit(0.U(3.W))
val data_buffer   = Reg(Vec(8, UInt(64.W)))  // 8个64位缓冲槽

when(io.data_valid_fromMem) {
  data_buffer(burst_counter) := io.rdata_fromMem  // 存入当前槽
  when(io.data_ok_fromMem) {
    burst_counter := 0.U       // 最后一个，复位
    rsp_valid_reg := true.B    // 拼装完成！
  }.otherwise {
    burst_counter := burst_counter + 1.U  // 继续收
  }
}

// 拼成 512 位后送给 MSHR（高位在前）
mshr.io.mem_rsp_data := Cat(
  data_buffer(7), data_buffer(6), data_buffer(5), data_buffer(4),
  data_buffer(3), data_buffer(2), data_buffer(1), data_buffer(0)
)
```

> 📖 **Chisel 小课堂**：
>
> * `Reg(Vec(N, UInt(W.W)))` —— 寄存器向量，可以动态索引写入
> * `Cat(a, b, ...)` 拼接时高位在前——`Cat(buf(7), ..., buf(0))` 使得 buf(7) 在最高位

完整代码见 [CacheCtrl.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/CacheCtrl.scala)。

***

# 七、动手造 Cache——Step 5：MSHR，订单追踪系统

**核心思想：MSHR 是非阻塞缓存之所以"非阻塞"的关键。它追踪每一个未完成的缺失请求，支持请求合并和乱序响应。**

> 如果你前面的步骤都理解了，但觉得 MSHR 比较复杂——这是正常的。MSHR 是整个项目中最复杂的模块，但拆开来看，每个部分都只用到了前面学过的 Chisel 知识。

## 7.1 MSHR 是什么？

MSHR = Miss Status Holding Register，你可以把它想象成餐厅的**订单追踪板**：

```plain
┌─ MSHR Table (4项) ──────────────────┐    ┌─ Load Table (8项) ───────────────┐
│ #0: 地址=0x1A00 已发出 数据未到       │    │ #0: 属MSHR#0, 寄存器R3, 数据已到  │
│ #1: 地址=0x2B00 未发出               │    │ #1: 属MSHR#0, 寄存器R7, 数据未到  │
│ #2: 空                               │    │ #2: 属MSHR#1, 寄存器R5, 数据未到  │
│ #3: 空                               │    │ #3~#7: 空                         │
└──────────────────────────────────────┘    └──────────────────────────────────┘
```

* **MSHR Table**（4项）：追踪 4 个不同的缓存行缺失，每项对应一次内存请求
* **Load Table**（8项）：追踪 8 个具体的 CPU 读请求。多个请求可以指向同一个 MSHR 表项 → 请求合并

## 7.2 Chisel 新武器：自定义 Bundle——结构化数据

MSHR 的表项有多个字段，用自定义 `Bundle` 组织：

```scala
class MSHREntry extends Bundle {
  val valid         = Bool()       // 该表项是否有请求
  val issued        = Bool()       // 请求是否已发往内存
  val data_valid    = Bool()       // 内存数据是否已返回
  val written_cache = Bool()       // 数据是否已写入缓存
  val addr          = UInt(48.W)   // 请求地址
  val data          = UInt(512.W)  // 返回的整行数据
  val req_id        = UInt(16.W)   // 请求编号
}

class LoadEntry extends Bundle {
  val valid        = Bool()
  val mshr_id      = UInt(2.W)     // 指向哪个 MSHR 表项
  val req_id       = UInt(16.W)    // 该请求的唯一标识
  val req_idx      = UInt(5.W)     // 回写寄存器
  val data         = UInt(64.W)    // 该请求的 64 位数据
  val data_valid   = Bool()
  val offset       = UInt(3.W)     // 块内偏移
  val returned_cpu = Bool()        // 是否已返回给 CPU
}
```

> 📖 **Chisel 小课堂**：
>
> * `Bundle` 类似 C 的 `struct`，把多个信号打包在一起
> * 字段类型用 Chisel 类型（`Bool()`、`UInt(N.W)`），不是 Scala 类型
> * 自定义 Bundle 可以用 `.` 访问字段：`entry.valid`、`entry.addr`

## 7.3 Chisel 新武器：VecInit 初始化复杂 Bundle

初始化含 Bundle 的寄存器向量时，需要逐字段赋初值：

```scala
val mshr_entries = RegInit(VecInit(Seq.fill(4) {
  val entry = Wire(new MSHREntry)  // 创建一个 Wire 作为模板
  entry.valid         := false.B
  entry.issued        := false.B
  entry.data_valid    := false.B
  entry.written_cache := false.B
  entry.addr          := 0.U
  entry.data          := 0.U
  entry.req_id        := 0.U
  entry               // 返回这个模板
}))
```

> 📖 **Chisel 小课堂**：
>
> * 不能直接写 `0.U` 来初始化 Bundle——必须先 `Wire` 一个实例，逐字段赋值
> * `VecInit(Seq.fill(N) { ... })` —— 生成 N 个相同初值的向量
> * 这和 Step 2 中 `VecInit(Seq.fill(64)(0.U(37.W)))` 本质一样，只是初值更复杂

## 7.4 Chisel 新武器：map + reduce——向量操作

MSHR 中大量使用 Scala 的函数式操作来处理向量信号：

```scala
// 提取所有表项的 valid 字段
val mshr_valid_vec = mshr_entries.map(_.valid)

// 判断 MSHR 是否已满（所有项都 valid）
val mshr_full = mshr_valid_vec.reduce(_ && _)

// 判断 MSHR 是否为空（所有项都非 valid）
val mshr_empty = !mshr_valid_vec.reduce(_ || _)
```

> 📖 **Chisel 小课堂**：
>
> * `.map(f)` —— 对向量每个元素应用函数 f，返回新向量
> * <code>.reduce(_ && _)</code> —— 用 `&&` 把所有元素折叠成一个值。等价于 `v(0) && v(1) && ... && v(N)`
> * 这些是 **Scala 的函数式编程**特性，但在 Chisel 中它们生成的是**硬件逻辑**（与/或门树）

## 7.5 Chisel 新武器：zipWithIndex + PriorityEncoder——优先级编码

查找空闲表项、匹配地址等操作用到了 `zipWithIndex` 和 `PriorityEncoder`：

```scala
// 检查地址是否与现有 MSHR 表项匹配（请求合并的关键）
val addr_match = mshr_entries.zipWithIndex.map { case (entry, i) =>
  entry.valid && (entry.addr(47,6) === io.cpu_req_addr(47,6))
}
val addr_match_any = VecInit(addr_match).reduce(_ || _)    // 有没有匹配的？
val match_mshr_id  = PriorityEncoder(VecInit(addr_match))  // 匹配的是第几个？

// 查找空闲的 MSHR 表项
val mshr_free_vec = mshr_entries.zipWithIndex.map { case (entry, i) =>
  !entry.valid || (entry.data_valid && entry.written_cache && entry.valid)
}
val mshr_free_oh = PriorityEncoderOH(mshr_free_vec)  // 独热码编码
val mshr_free_id = OHToUInt(mshr_free_oh)            // 转成二进制索引
```

> 📖 **Chisel 小课堂**：
>
> * `.zipWithIndex` —— 给每个元素附上索引号，变成 `(元素, 索引)` 的序列
> * `PriorityEncoder(vec)` —— 返回向量中第一个为真的索引（二进制编码）
> * `PriorityEncoderOH(vec)` —— 返回独热码编码（只有一位为 1）
> * `OHToUInt(独热码)` —— 独热码转二进制索引
> * 独热码的好处：可以和 `Mux1H` 配合做多路选择，综合效果更好

## 7.6 MSHR 的五大任务

MSHR 内部管理五大任务，形成一条完整的处理流水线：

| 任务 | 做什么 | 关键 Chisel 技法 |
| --- | --- | --- |
| A. 接收 miss | 写入 MSHR + Load Table | `when` 条件写寄存器，`Mux` 选表项 ID |
| B. 发内存请求 | 从 MSHR 找未发出的请求 | `PriorityEncoderOH` + `Mux1H` |
| C. 收内存响应 | 根据请求 ID 匹配表项，广播到 Load Table | `zipWithIndex.map` + `for` 循环广播 |
| D. 写缓存 | 数据有效后写 CacheData + CacheTag | `PriorityEncoderOH` + `Mux1H` |
| E. 返回 CPU | Load Table 数据就绪后返回 | `PriorityEncoderOH` + `Mux1H` |

:::info
MSHR 代码有 366 行，但不要被吓到。它的核心就是"两张表 + 五个任务"。建议先通读 [MSHR.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/MSHR.scala) 中的注释，理解每个任务的输入输出，再看具体实现。特别注意任务 A 中的**请求合并**逻辑——当新 miss 地址与已有 MSHR 表项匹配时，只写 Load Table 不写 MSHR Table。

:::

完整代码见 [MSHR.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/MSHR.scala)。

***

# 八、动手造 Cache——Step 6：顶层组装，把零件焊在一起

**核心思想：顶层模块就像 PCB 板——它不包含逻辑，只负责把各模块的引脚正确地连接起来。**

## 8.1 Chisel 新武器：模块实例化与连线

```scala
class NonBlockingCache(
  INDEX_WD: Int, TAG_WD: Int, OFFSET_WD: Int
) extends Module {
  val io = IO(new CacheIO)

  // 实例化三个子模块（MSHR 在 CacheCtrl 内部实例化）
  val cache_tag  = Module(new CacheTag(INDEX_WD, TAG_WD, OFFSET_WD))
  val cache_ctrl = Module(new CacheCtrl(INDEX_WD, TAG_WD, OFFSET_WD))
  val cache_data = Module(new CacheData(INDEX_WD, TAG_WD, OFFSET_WD))

  // 连线——Ctrl 是中心，连接 Tag、Data 和外部接口
  cache_ctrl.io.req_fromCPU      := io.req
  cache_ctrl.io.hit_fromTag      := cache_tag.io.hit
  cache_ctrl.io.miss_fromTag     := cache_tag.io.miss
  cache_ctrl.io.rdata_fromData   := cache_data.io.rdata_toCtrl
  cache_tag.io.replace_cache     := cache_ctrl.io.replace_cache
  cache_tag.io.replace_addr      := cache_ctrl.io.refill_addr
  cache_data.io.hit_fromTag      := cache_tag.io.hit
  cache_data.io.pointer_fromTag  := cache_tag.io.replace_pointer
  // ... 其他连线 ...
}
```

> 📖 **Chisel 小课堂**：
>
> * `Module(new SomeModule(params))` —— 实例化子模块，类似 Verilog 的 `SomeModule #(.PARAM(val)) inst_name (...)`
> * `子模块.io.信号名 := 值` —— 连接线网。`:=`\*\* 是 Chisel 的连线操作符\*\*
> * `dontTouch(子模块.io)` —— 防止综合工具优化掉看似未使用的信号（调试利器）

## 8.2 Chisel 新武器：withClock——自定义时钟

本项目中有一个特殊的处理——使用反相时钟：

```scala
val clkSignal = !clock.asBool
withClock(clkSignal.asClock) {
  // 所有子模块在反相时钟下运行
  val cache_tag = Module(new CacheTag(INDEX_WD, TAG_WD, OFFSET_WD))
  // ...
}
```

> 📖 **Chisel 小课堂**：
>
> * `clock.asBool` → `!` 取反 → `.asClock` 转回 Clock 类型
> * `withClock(自定义时钟) { ... }` —— 让块内的寄存器使用指定时钟沿

:::info
`withClock` 取反时钟是本项目的一个"临时解决方案"（如代码注释所说），正常设计中不应该需要这样做。了解这个语法即可，不必深究原因。

:::

## 8.3 Chisel 新武器：emitVerilog——生成 Verilog

最后，用 `App` 对象生成 Verilog：

```scala
object NonBlockingCache extends App {
  emitVerilog(
    new NonBlockingCache(6, 36, 6),       // 实例化顶层模块
    Array("--target-dir", "generated")     // 输出目录
  )
}
```

运行 `sbt run` 即可在 `generated/` 目录下生成 Verilog 文件。

完整代码见 [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala#L1-L135)。

***

# 九、测试与验证——质检上线

**核心思想：硬件设计没有测试就像没有安全网的走钢丝——你不知道什么时候会摔。**

## 9.1 Chisel 新武器：ChiselTest 基础

ChiselTest 是 Chisel 的仿真测试框架，核心操作就三个——`poke`（写输入）、`step`（推进时钟）、`peek`（读输出）：

```scala
test(new NonBlockingCache(6, 36, 6))       // 实例化被测模块
  .withAnnotations(Seq(WriteVcdAnnotation)) // 开启波形输出
  { dut =>

  dut.reset.poke(true.B)    // poke：往输入端口写值
  dut.clock.step(10)         // step：推进 10 个时钟周期
  dut.reset.poke(false.B)

  for (cycle <- 0 until TEST_CYCEL) {
    ram.step(cycle)          // RAM 模型推进
    cpu.step(cycle)          // CPU 模型推进
    dut.clock.step(1)        // 推进一个周期
  }
}
```

> 📖 **Chisel 小课堂**：
>
> * `dut.端口.poke(value)` —— 给输入端口赋值。`true.B` 是 Chisel 的 Bool 常量
> * `dut.端口.peek()` —— 读取输出端口值
> * `dut.clock.step(N)` —— 推进 N 个时钟周期
> * `WriteVcdAnnotation` —— 生成 VCD 波形文件，可用 GTKWave 查看

## 9.2 测试架构

本项目的测试用三个角色协同工作：

| 角色 | 文件 | 职责 | 比喻 |
| --- | --- | --- | --- |
| FakeCPU | [FakeCPU.scala](src/test/scala/FakeCPU.scala) | 发请求、验数据 | 模拟客户下单+验货 |
| FakeRAM | [FakeNonBlockingRAM.scala](src/test/scala/FakeNonBlockingRAM.scala) | 随机延迟返回数据 | 模拟快递（可能晚到、可能乱序） |
| CacheSpec | [CacheSpec.scala](src/test/scala/CacheSpec.scala) | 编排测试流程 | 质检主管 |

FakeCPU 的请求不是纯随机的——它刻意制造了三种典型场景：

| 场景 | 做什么 | 验证什么 |
| --- | --- | --- |
| 时间局部性 | 反复访问相同地址 | 命中路径正确性 |
| 索引冲突 | 访问相同 Set 不同 Tag | 替换算法正确性 |
| 完全随机 | 任意地址 | 系统鲁棒性 |

## 9.3 日常开发流程

```plain
  修改 Chisel 代码
        │
        ▼
   sbt test        ──▶ 全部 PASS？──是──▶ 继续下一步修改
        │                         │
        │ 否                      │
        ▼                         ▼
   检查日志 & 波形          sbt run（可选，生成 Verilog）
        │
        ▼
   定位问题，修改代码，重来
```

**常用操作速查：**

| 我想… | 命令 |
| --- | --- |
| 只跑测试 | `sbt test` |
| 只生成 Verilog | `sbt run` |
| 文件保存后自动重跑测试 | `sbt ~test` |
| 查看波形 | 打开 `test_run_dir/` 下的 `.vcd` 文件（需要 GTKWave） |

***

# 十、常见问题与排错

**核心思想：遇到问题别慌，大部分是环境或语法的问题。**

## 10.1 编译类问题

### ❓ `sbt test` 报编译错误

**常见语法陷阱**——对照检查清单：

| 错误写法 | 正确写法 | 原因 |
| --- | --- | --- |
| `a == b` | `a === b` | `==` 是 Scala 引用比较，`===` 才是硬件比较 |
| `x := y` | `io.x := y` | `:=` 只能连接 Chisel 信号，不能连接 Scala 变量 |
| `when ... end` | `when(...) { ... }` | Chisel 用花括号，不需要 `end` |
| `UInt(6)` | `UInt(6.W)` | 位宽要用 `.W` 后缀 |
| `val x = 3` 在硬件逻辑中 | `val x = 3.U` | Scala 的 `3` 不会变成硬件常量 |

### ❓ 首次 `sbt` 运行极慢

**原因**：sbt 在下载 Chisel、ChiselTest 等依赖。\
**解决**：耐心等待（可能需要 1-10 分钟），可配置 sbt 镜像源加速。

## 10.2 仿真类问题

### ❓ 测试运行时 PASS 率不是 100%

**原因**：如果你修改了设计代码，可能引入了 bug。\
**解决**：

1. 查看 `[VERIFY]` 块中 Expected 和 Actual 的差异
2. 打开 `.vcd` 波形文件，用 GTKWave 分析时序

### ❓ 想修改缓存参数但不知道改哪里

**三个地方需要同步修改**：

1. [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala#L41-L44) 的类参数
2. [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala#L131-L134) 的 `emitVerilog` 调用
3. [CacheSpec.scala](src/test/scala/CacheSpec.scala#L12) 的 `test` 调用

> 如果以上方法都无法解决，可以在 GitHub 仓库的 [Issues](https://github.com/HisionWang/NonBlockingCache/issues) 页面提问。

***

# 十一、学习路径与总结

## 11.1 你的 Chisel 技能树

🎉 **恭喜你走到了最后！** 让我们回顾一下你沿途收获的 Chisel 技能：

| Chisel 技能 | 你在哪个 Step 学会的 | 用在哪 |
| --- | --- | --- |
| `Module` / `IO` / `Bundle` | Step 1 — Bram | 每个模块 |
| `SyncReadMem` / `when` | Step 1 — Bram | 存储器读写 |
| 参数化模块 / `RegInit` / `VecInit` | Step 2 — CacheTag | 寄存器向量 |
| 位提取 / `Cat` / `Mux` | Step 2 — CacheTag | 地址分解、命中判断 |
| `Seq.fill` / `for` 生成硬件 | Step 3 — CacheData | 批量实例化 |
| `Mux1H` / 独热码选择 | Step 3 — CacheData | 多路数据选择 |
| `ChiselEnum` / `switch`/`is` | Step 4 — CacheCtrl | 状态机 |
| `RegNext` / 流水线对齐 | Step 4 — CacheCtrl | 时序对齐 |
| 自定义 `Bundle` | Step 5 — MSHR | 结构化数据 |
| `map` / `reduce` / `zipWithIndex` | Step 5 — MSHR | 向量操作 |
| `PriorityEncoder` / `PriorityEncoderOH` | Step 5 — MSHR | 优先级查找 |
| 模块实例化与连线 / `emitVerilog` | Step 6 — 顶层 | 组装与生成 |
| `ChiselTest` / `poke`/`peek`/`step` | Step 9 — 测试 | 仿真验证 |

***

> 🌟 **最后一句话**：Chisel 的本质就是"用 Scala 的编程能力来生成硬件"。`for` 循环、`map`/`reduce`、参数化——这些都是 Scala 在编译期帮你写重复的 Verilog 代码。当你不再觉得"这是在写软件"而是"这是在用编程生成硬件"的时候，你就真正理解 Chisel 了。**每个 Chisel 大佬都是从 **`sbt test`** 第一次 PASS 开始的——你已经迈出了最重要的一步。**

*最后更新：2026年5月26日 · 项目作者：HisionWang*

***


> 更新: 2026-05-26 18:33:31  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/qx1hvfi3p5w1h4gi>
