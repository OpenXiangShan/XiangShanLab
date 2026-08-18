<!-- # 第五章 Chisel 编程实践 -->
# Chapter 5: Chisel Programming in Practice

TODO

<!-- 将当前笔试的题，生成对应的教程 -->
Generate a tutorial based on the current written-exam problem.

***

<!-- # 🎓 用 Chisel 造一个非阻塞缓存——从零开始的硬件设计之旅 -->
# 🎓 Building a Non-Blocking Cache in Chisel: A Hardware Design Journey from Scratch

<!-- > **如果你是第一次接触 Chisel，看到 **`VecInit`**、**`SyncReadMem`**、**`ChiselEnum`\*\* 这些关键词可能会感到不知所措。别担心——最好的学习方式就是动手造一个真实的东西。\*\* 今天我们就要从零开始，一步一步造出一个支持多未完成请求的非阻塞缓存。每造一个零件，你就学会一批 Chisel 技能。等这个 Cache 跑通的那一刻，你会发现 Chisel 已经是你的老朋友了。 -->
> **If Chisel is new to you, keywords such as **`VecInit`**, **`SyncReadMem`**, and **`ChiselEnum`** may look intimidating. Do not worry: the best way to learn is to build something real. Today we will build a non-blocking cache that supports multiple outstanding requests, one step at a time. Each component teaches a set of Chisel skills; by the time the cache runs, Chisel will feel like an old friend.**

:::info
<!-- **通过本章，你将——** -->
**By the end of this chapter, you will—**

<!-- 理解层面： -->
Understanding:

<!-- * ✅ 理解非阻塞缓存为什么比阻塞缓存更"聪明" -->
* ✅ Understand why a non-blocking cache is "smarter" than a blocking cache
<!-- * ✅ 建立整个系统的宏观架构认知 -->
* ✅ Build a high-level architectural view of the whole system
<!-- * ✅ 洞悉一次缓存请求从发出到返回的完整生命周期 -->
* ✅ Follow a cache request through its complete lifecycle, from issue to response

<!-- 实践层面： -->
Hands-on practice:

<!-- * ✅ 从零搭建开发环境，跑通第一次仿真 -->
* ✅ Set up the development environment and run the first simulation from scratch
<!-- * ✅ 亲手造出 Bram → CacheTag → CacheData → CacheCtrl → MSHR → 顶层组装 -->
* ✅ Build Bram → CacheTag → CacheData → CacheCtrl → MSHR → top-level integration yourself
<!-- * ✅ 掌握 Chisel 最核心的技能集：模块定义、寄存器、存储器、状态机、向量操作、测试 -->
* ✅ Master Chisel's core skills: module definitions, registers, memories, FSMs, vector operations, and testing

<!-- 规划层面： -->
Learning path:

<!-- * ✅ 获得从入门到精通的分阶段学习路线图 -->
* ✅ Get a staged roadmap from beginner to advanced practice

:::

***

<!-- # 一、项目全景——我们要造什么？ -->
# 1. Project Overview: What Are We Building?

<!-- ## 1.1 非阻塞缓存：为什么不让你干等？ -->
## 1.1 Non-Blocking Cache: Why Make You Wait?

<!-- **核心思想：阻塞缓存让 CPU 干等，非阻塞缓存让 CPU 不闲着。** -->
**Core idea: a blocking cache leaves the CPU idle, while a non-blocking cache keeps it busy.**

<!-- 想象你在一个图书馆借书。**阻塞缓存**就像每次你想要的书不在架上，你就必须站在原地等管理员去仓库取——在这期间你什么也做不了。而**非阻塞缓存**就像你可以继续浏览其他书架，管理员把书取回来后会通知你——等待期间你仍然可以发出新的借阅请求。 -->
Imagine borrowing a book from a library. A **blocking cache** is like having to stand still whenever the book you want is not on the shelf while the librarian retrieves it from storage; you cannot do anything else meanwhile. A **non-blocking cache** lets you browse other shelves and receive a notification when the librarian returns; you can issue new requests while waiting.

<!-- 本项目实现了一个 **16KB、4路组相联、非阻塞 L1 数据缓存**，用 Chisel 3 硬件描述语言编写，设计用于 RV64 系统中 CPU 与主存之间的桥梁。 -->
This project implements a **16-KB, four-way set-associative, non-blocking L1 data cache** in the Chisel 3 hardware description language, serving as the bridge between the CPU and main memory in an RV64 system.

<!-- **仓库地址**：<https://github.com/HisionWang/NonBlockingCache> -->
**Repository**: <https://github.com/HisionWang/NonBlockingCache>

<!-- 本项目最初是一道开芯院的笔试题——设计一个 Non-blocking Cache（支持多 outstanding miss），用于连接 CPU 与 memory。要求如下： -->
The project began as a Kaixin Institute written-exam problem: design a non-blocking cache (supporting multiple outstanding misses) to connect the CPU and memory. The requirements were:

<!-- * Cpu 请求的 PA 宽度 48bit（假设 RV64） -->
* CPU request PA width: 48 bits (assuming RV64)
<!-- * Memory 接口 64bit -->
* Memory interface: 64 bits
* Data cache: 16-KB, 4-way set associative, 64-byte line size
<!-- * 支持至少 4 个 miss 请求而不阻塞 -->
* Support at least four miss requests without blocking
<!-- * 给出 RTL 代码以及验证代码和结果 -->
* Provide the RTL, verification code, and verification results

<!-- ## 1.2 设计规格一览 -->
## 1.2 Design Specifications at a Glance

<!-- | 参数 | 值 | 含义 | -->
<!-- | --- | --- | --- | -->
<!-- | 缓存容量 | 16 KB | 64组 × 4路 × 64字节 | -->
<!-- | 组织结构 | 4路组相联 | 每组 4 个缓存行候选 | -->
<!-- | 缓存行大小 | 64 字节 | 每次从内存搬运的数据块 | -->
<!-- | 地址宽度 | 48 位 | RV64 物理地址 | -->
<!-- | Tag / Index / Offset | 36 / 6 / 6 位 | 地址三段式分解 | -->
<!-- | CPU 数据接口 | 64 位 | 单次读写 64 位 | -->
<!-- | 内存数据接口 | 64 位突发 | 8 周期传完一个缓存行 | -->
<!-- | 替换算法 | Tree-PLRU | 3 位树实现近似 LRU | -->
<!-- | 非阻塞能力 | 4 个不重叠 / 8 个部分重叠 miss | MSHR 4项 + Load Table 8项 | -->
| Parameter | Value | Meaning |
| --- | --- | --- |
| Cache capacity | 16 KB | 64 sets × 4 ways × 64 bytes |
| Organization | Four-way set associative | Four cache-line candidates per set |
| Cache-line size | 64 bytes | Block transferred from memory per refill |
| Address width | 48 bits | RV64 physical address |
| Tag / Index / Offset | 36 / 6 / 6 bits | Three-part address decomposition |
| CPU data interface | 64 bits | 64-bit read/write per transaction |
| Memory data interface | 64-bit burst | One cache line transferred in 8 cycles |
| Replacement algorithm | Tree-PLRU | Approximate LRU implemented with a 3-bit tree |
| Non-blocking capacity | 4 non-overlapping / 8 partially overlapping misses | Four MSHR entries + eight Load Table entries |

<!-- **它"非阻塞"在哪里？** 体现在三个层面： -->
**Where is the "non-blocking" behavior?** It appears at three levels:

<!-- 1. **多未完成请求**：CPU 发出 miss 后无需等待，可继续发出新的请求 -->
1. **Multiple outstanding requests**: after issuing a miss, the CPU need not wait and can issue another request.
<!-- 2. **请求合并**：多个 miss 如果访问同一缓存行，只发一次内存请求——就像多个人要同一本书，管理员只跑一趟 -->
2. **Request merging**: misses to the same cache line share one memory request, just as a librarian makes one trip for several people requesting the same book.
<!-- 3. **乱序响应**：内存返回数据的顺序可以和请求发出的顺序不同——先回来的先处理，不浪费等待时间 -->
3. **Out-of-order responses**: memory may return data in a different order from issue; process whichever returns first instead of wasting time waiting.

<!-- ## 1.3 整体架构：一座"现代化工厂" -->
## 1.3 Overall Architecture: A "Modern Factory"

<!-- **核心思想：理解系统 = 理解数据从哪里来、经过谁、到哪里去。** -->
**Core idea: understanding the system means understanding where data comes from, who handles it, and where it goes.**

<!-- 你可以把整个系统想象成一座**现代化工厂**，由三个"车间"协同工作： -->
Imagine the whole system as a **modern factory** whose three "workshops" cooperate:

```plain
┌─────────────┐     ┌──────────────────────────────────────┐     ┌─────────────┐
│   CPU 车间  │───▶│         缓存核心车间                  │────▶│  内存车间   │
│ (FakeCPU)   │◀───│  (Tag + Data + Ctrl + MSHR + Bram)   │◀───│ (FakeRAM)   │
└─────────────┘     └──────────────────────────────────────┘     └─────────────┘
   下单 & 验收               加工 & 调度                      供货 & 配送
```

<!-- * **CPU 车间**：不断发出"我要读这个地址"的请求，并验收返回的数据是否正确 -->
* **CPU workshop**: continuously issues "read this address" requests and checks returned data.
<!-- * **缓存核心车间**：判断数据是在本地（命中）还是需要向内存车间调货（缺失），负责整个调度流程 -->
* **Cache-core workshop**: determines whether data is local (a hit) or must be fetched from the memory workshop (a miss), and coordinates the whole flow.
<!-- * **内存车间**：按请求配送数据，但配送时间不确定（随机延迟），甚至可能先送后到的订单 -->
* **Memory workshop**: delivers data on request with unpredictable (random) latency, and may even complete orders out of order.

<!-- > 📊 **图表解读**：更详细的架构图请查看 [架构图](https://github.com/HisionWang/NonBlockingCache/blob/main/doc/架构图.svg)，你会看到三个主要子系统——**标记存储**（CacheTag + PLRU）、**数据存储**（CacheData + 分体 BRAM）和**非阻塞控制平面**（CacheCtrl + MSHR）。所有模块间的连线由顶层 `NonBlockingCache` 管理，它本身只是一个纯粹的"布线容器"。 -->
> 📊 **Diagram note**: see the detailed [architecture diagram](https://github.com/HisionWang/NonBlockingCache/blob/main/doc/%E6%9E%B6%E6%9E%84%E5%9B%BE.svg). It shows three main subsystems: **tag storage** (CacheTag + PLRU), **data storage** (CacheData + split BRAM), and the **non-blocking control plane** (CacheCtrl + MSHR). The top-level `NonBlockingCache` manages all inter-module connections and is simply a "wiring container."

:::info
<!-- 现阶段你只需要理解"三个车间"的大致分工即可，不必深究每个模块内部的时序细节。我们会在后面的实操章节逐一拆解。 -->
At this stage, understand only the broad responsibilities of the three workshops; do not worry about each module's internal timing yet. The hands-on sections will break them down one by one.

:::

<!-- ## 1.4 核心组件：谁负责什么？ -->
## 1.4 Core Components: Who Does What?

<!-- **核心思想：每个模块都有明确的单一职责——知道"谁负责什么"，就理解了系统。** -->
**Core idea: every module has a clear single responsibility; knowing "who does what" means understanding the system.**

<!-- ### 设计模块 -->
### Design Modules

<!-- | 模块 | 文件 | 职责 | 比喻 | -->
<!-- | --- | --- | --- | --- | -->
<!-- | Bram | [Bram.scala](src/main/scala/Bram.scala) | 双端口 BRAM 原语封装 | 基础砖块 | -->
<!-- | CacheTag | [CacheTag.scala](src/main/scala/CacheTag.scala) | 4路并行标签比较，Tree-PLRU 替换算法 | 仓库目录索引 | -->
<!-- | CacheData | [CacheData.scala](src/main/scala/CacheData.scala) | Bank 存储阵列，并行数据读写 | 仓库货架 | -->
<!-- | CacheCtrl | [CacheCtrl.scala](src/main/scala/CacheCtrl.scala) | 命中/缺失状态机，调度 MSHR，管理突发传输 | 生产线主管 | -->
<!-- | MSHR | [MSHR.scala](src/main/scala/MSHR.scala) | 追踪未完成缺失，请求合并，乱序响应 | 订单跟踪系统 | -->
<!-- | NonBlockingCache | [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala) | 集成所有子模块，定义 CPU 与内存接口 | 工厂总调度室 | -->
| Module | File | Responsibility | Analogy |
| --- | --- | --- | --- |
| Bram | [Bram.scala](src/main/scala/Bram.scala) | Dual-port BRAM primitive wrapper | Foundation brick |
| CacheTag | [CacheTag.scala](src/main/scala/CacheTag.scala) | Four-way parallel tag comparison and Tree-PLRU replacement | Warehouse catalog index |
| CacheData | [CacheData.scala](src/main/scala/CacheData.scala) | Banked storage array with parallel data access | Warehouse shelves |
| CacheCtrl | [CacheCtrl.scala](src/main/scala/CacheCtrl.scala) | Hit/miss FSM, MSHR scheduling, and burst-transfer control | Production-line supervisor |
| MSHR | [MSHR.scala](src/main/scala/MSHR.scala) | Tracks outstanding misses, merges requests, and handles out-of-order responses | Order-tracking system |
| NonBlockingCache | [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala) | Integrates all submodules and defines CPU/memory interfaces | Factory control room |

<!-- ### 验证模块 -->
### Verification Modules

<!-- | 组件 | 文件 | 职责 | 比喻 | -->
<!-- | --- | --- | --- | --- | -->
<!-- | CacheSpec | [CacheSpec.scala](src/test/scala/CacheSpec.scala) | 集成测试环境，控制仿真流程 | 质检主管 | -->
<!-- | FakeCPU | [FakeCPU.scala](src/test/scala/FakeCPU.scala) | 生成带局部性的请求，实时验证返回数据 | 模拟客户 | -->
<!-- | FakeRAM | [FakeNonBlockingRAM.scala](src/test/scala/FakeNonBlockingRAM.scala) | 随机延迟响应，支持突发传输和乱序返回 | 模拟供应商 | -->
<!-- | GlobalVars | [GlobalVars.scala](src/test/scala/GlobalVars.scala) | 测试数量、随机种子等参数 | 实验参数表 | -->
<!-- | Tool | [Tool.scala](src/test/scala/Tool.scala) | 数据格式化、RAM 数据生成 | 辅助工具箱 | -->
| Component | File | Responsibility | Analogy |
| --- | --- | --- | --- |
| CacheSpec | [CacheSpec.scala](src/test/scala/CacheSpec.scala) | Integration test environment and simulation orchestration | Quality-control supervisor |
| FakeCPU | [FakeCPU.scala](src/test/scala/FakeCPU.scala) | Generates locality-aware requests and checks responses online | Simulated customer |
| FakeRAM | [FakeNonBlockingRAM.scala](src/test/scala/FakeNonBlockingRAM.scala) | Random-delay responses with burst transfers and out-of-order returns | Simulated supplier |
| GlobalVars | [GlobalVars.scala](src/test/scala/GlobalVars.scala) | Test counts, random seeds, and other parameters | Experiment parameter sheet |
| Tool | [Tool.scala](src/test/scala/Tool.scala) | Data formatting and RAM-data generation | Utility toolbox |

<!-- ## 1.5 一次请求的生命周期 -->
## 1.5 Lifecycle of a Request

<!-- **核心思想：数据从 CPU 发出请求到最终返回，经过了完整的"命中检查 → 缺失处理 → 数据回填"流水线。** -->
**Core idea: from the CPU's request to the final response, data passes through a complete "hit check → miss handling → data refill" pipeline.**

<!-- 让我们跟踪一次\*\*缓存缺失（Cache Miss）\*\*的完整旅程： -->
Let us follow the complete journey of a **cache miss**:

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

<!-- > 💡 **关键洞察**：整个过程最精妙之处在于——当 CPU 在等待某个 miss 的数据时，它可以继续发出新的请求。如果新请求命中了缓存，立即返回；如果又 miss 了，MSHR 会记录下来，等内存空闲时再处理。这就是"非阻塞"的本质。 -->
> 💡 **Key insight**: while the CPU waits for one miss, it can continue issuing requests. A new hit returns immediately; another miss is recorded by the MSHR and handled when memory is available. That is the essence of being "non-blocking."

***

<!-- # 二、开发环境搭建——把工厂建起来 -->
# 2. Setting Up the Development Environment: Build the Factory

<!-- **核心思想：工欲善其事，必先利其器。先把环境跑通，后面的学习才能心无旁骛。** -->
**Core idea: a good craftsperson needs good tools. Get the environment working first so later learning can focus on the design.**

<!-- ## 2.1 环境要求 -->
## 2.1 Requirements

<!-- | 依赖 | 版本要求 | 检查命令 | 安装指引 | -->
<!-- | --- | --- | --- | --- | -->
<!-- | JDK | 8 或更高 | `java -version` | [Adoptium](https://adoptium.net/) | -->
<!-- | sbt | 最新版 | `sbt --version` | [sbt 官方文档](https://www.scala-sbt.org/download.html) | -->
| Dependency | Required version | Check command | Installation |
| --- | --- | --- | --- |
| JDK | 8 or later | `java -version` | [Adoptium](https://adoptium.net/) |
| sbt | Latest | `sbt --version` | [sbt documentation](https://www.scala-sbt.org/download.html) |

<!-- > Chisel 和 Scala 的依赖由 sbt 自动管理，无需手动安装。本项目使用的版本为 Scala 2.13.14 + Chisel 3.6.1 + ChiselTest 0.6.2，详见 [build.sbt](https://github.com/HisionWang/NonBlockingCache/blob/main/build.sbt)。 -->
> sbt manages the Chisel and Scala dependencies automatically, so no manual installation is required. This project uses Scala 2.13.14 + Chisel 3.6.1 + ChiselTest 0.6.2; see [build.sbt](https://github.com/HisionWang/NonBlockingCache/blob/main/build.sbt).

<!-- ## 2.2 获取项目 -->
## 2.2 Get the Project

```bash
git clone https://github.com/HisionWang/NonBlockingCache.git
cd NonBlockingCache
```

<!-- ## 2.3 项目结构一览 -->
## 2.3 Project Structure at a Glance

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

<!-- ## 2.4 开发流程：从设计到验证 -->
## 2.4 Development Flow: From Design to Verification

<!-- **核心思想：Chisel 开发 = 写设计 → 仿真验证 → 生成 Verilog，三步走。** -->
**Core idea: Chisel development takes three steps: write the design → verify it in simulation → generate Verilog.**

<!-- 如果你把设计工具想象成**建筑师的绘图板**，仿真工具就像**在工厂里用模型测试产品**，那么整个开发流程就是： -->
If the design tool is an **architect's drafting board** and the simulator is **a factory model for testing the product**, the full development flow is:

```plain
   Chisel 代码         ChiselTest 仿真           Verilog 输出
  (画设计图纸)    →    (用模型验证设计)      →    (出施工图纸)
   sbt compile         sbt test                  sbt run
```

<!-- | 阶段 | 命令 | 做什么 | 预期耗时 | -->
<!-- | --- | --- | --- | --- | -->
<!-- | 编译 | `sbt compile` | 将 Chisel/Scala 代码编译 | 首次约 2-5 分钟（下载依赖） | -->
<!-- | 测试 | `sbt test` | 运行仿真验证 | 50 条约 20 秒，1000 条约数分钟 | -->
<!-- | 生成 | `sbt run` | 输出 Verilog 文件 | 约 20 秒 | -->
| Stage | Command | Action | Expected time |
| --- | --- | --- | --- |
| Compile | `sbt compile` | Compile the Chisel/Scala code | About 2–5 minutes the first time (dependency download) |
| Test | `sbt test` | Run simulation-based verification | About 20 seconds for 50 cases; several minutes for 1,000 |
| Generate | `sbt run` | Emit Verilog files | About 20 seconds |

<!-- > 💡 **小技巧**：首次运行 `sbt` 会下载大量依赖，耐心等待即可。如果你经常使用，可以在 shell 配置中设置别名：`alias st='sbt test'`。 -->
> 💡 **Tip**: the first `sbt` invocation downloads many dependencies, so be patient. If you use it often, add an alias such as `alias st='sbt test'` to your shell configuration.

<!-- ## 2.5 第一次运行：5 分钟跑通测试 -->
## 2.5 First Run: Get the Tests Passing in Five Minutes

<!-- ### 第一步：调整测试参数（可选） -->
### Step 1: Adjust Test Parameters (Optional)

<!-- 编辑 [GlobalVars.scala](src/test/scala/GlobalVars.scala) 自定义测试规模： -->
Edit [GlobalVars.scala](src/test/scala/GlobalVars.scala) to customize the test size:

```scala
object GlobalVars {
  val TEST_NUM = 50      // 测试请求数量，首次建议 10-50
  var randomSeed: Long = 6688L  // 随机种子，改变它 = 不同的测试场景
  val TEST_CYCEL = 10000  // 最大测试周期数
}
```

<!-- > 💡 **小技巧**：首次测试建议将 `TEST_NUM` 设为 `10`，几十秒就能看到完整结果，增强信心。确认通过后再调大到 50 或 1000。 -->
> 💡 **Tip**: for the first test, set `TEST_NUM` to `10`; you will see the complete result in a few dozen seconds. Increase it to 50 or 1,000 after the test passes.

<!-- ### 第二步：运行测试 -->
### Step 2: Run the Tests

```bash
sbt test
```

<!-- ### 第三步：读懂输出 -->
### Step 3: Understand the Output

<!-- **预期输出**（以 1 条请求为例）： -->
**Expected output** (for one request):

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

<!-- **输出解读**： -->
**Reading the output**:

<!-- | 日志标签 | 含义 | -->
<!-- | --- | --- | -->
<!-- | `[CPU ] SEND` | CPU 发出了一个请求，包含地址和寄存器号 | -->
<!-- | `[RAM ] RECEIVE` | 内存收到了这个请求 | -->
<!-- | `[RAM ] BACKDATA` | 内存返回了数据 | -->
<!-- | `[CPU ] RECIEVE` | CPU 收到了返回的数据 | -->
<!-- | `[VERIFY]` | 自动验证：Expected vs Actual 是否一致 | -->
<!-- | `PASS` | 这条请求验证通过 ✅ | -->
| Log tag | Meaning |
| --- | --- |
| `[CPU ] SEND` | The CPU issued a request containing an address and register number |
| `[RAM ] RECEIVE` | The memory received the request |
| `[RAM ] BACKDATA` | The memory returned data |
| `[CPU ] RECIEVE` | The CPU received the returned data |
| `[VERIFY]` | Automatic check that Expected and Actual match |
| `PASS` | This request passed verification ✅ |

:::info
<!-- 如果你看到了 `All tests passed`，恭喜你——你已经成功运行了一个非阻塞缓存的完整验证！如果你暂时看不懂日志中的地址分解（LineIndex、BankIndex），不用着急，这些会在后续步骤中详细讲解。 -->
If you see `All tests passed`, congratulations: you have run a complete verification of a non-blocking cache. If the address fields in the log (LineIndex and BankIndex) are not yet clear, do not worry; later steps explain them in detail.

:::

<!-- ### 第四步：生成 Verilog（进阶） -->
### Step 4: Generate Verilog (Advanced)

```bash
sbt run
```

<!-- 生成的文件位于 `generated/` 目录： -->
The generated files are placed in the `generated/` directory:

<!-- | 文件 | 说明 | -->
<!-- | --- | --- | -->
<!-- | `NonBlockingCache.v` | Verilog 硬件描述文件——可以直接用于 FPGA 或综合工具 | -->
<!-- | `NonBlockingCache.fir` | FIRRTL 中间表示——Chisel 编译器的"汇编语言" | -->
<!-- | `NonBlockingCache.anno.json` | 注释信息——用于综合工具的元数据 | -->
| File | Description |
| --- | --- |
| `NonBlockingCache.v` | Verilog hardware description, ready for an FPGA or synthesis tool |
| `NonBlockingCache.fir` | FIRRTL intermediate representation, the Chisel compiler's "assembly language" |
| `NonBlockingCache.anno.json` | Annotation metadata for synthesis tools |

***

<!-- # 三、动手造 Cache——Step 1：Bram，你的第一块砖 -->
# 3. Build the Cache: Step 1 — Bram, Your First Brick

<!-- > **从本节开始，我们进入"边造边学"环节。** 每造一个零件，我都会先讲它"是什么"，再讲"怎么用 Chisel 写出来"，最后讲"Chisel 给了你什么新武器"。 -->
> **From this section onward, we learn by building.** For each component, I will first explain what it is, then how to write it in Chisel, and finally what new tool Chisel gives you.

<!-- **核心思想：一切伟大的建筑都从一块砖开始。Bram 是 Cache 中最基础的存储单元。** -->
**Core idea: every great structure starts with one brick. Bram is the cache's most basic storage unit.**

<!-- ## 3.1 我们需要什么？ -->
## 3.1 What Do We Need?

<!-- 我们需要一个**双端口 BRAM**——一个端口写、一个端口读。你可以把它想象成一个带编号的储物柜： -->
We need a **dual-port BRAM**—one port for writes and one for reads. Think of it as a numbered storage cabinet:

<!-- * **写**：告诉它"把 XX 东西放进第 N 号柜子" -->
* **Write**: tell it to "put item XX into cabinet N."
<!-- * **读**：告诉它"把第 N 号柜子的东西拿出来给我" -->
* **Read**: tell it to "take the item in cabinet N out for me."

<!-- ## 3.2 Chisel 新武器：Module、IO、Bundle -->
## 3.2 Chisel Tools: `Module`, `IO`, and `Bundle`

<!-- 在 Chisel 中，每一个硬件模块都是 `Module` 的子类。模块对外暴露的接口用 `IO(new Bundle { ... })` 定义，就像给模块开窗户——外面的人只能通过这些窗户和模块交互。 -->
In Chisel, every hardware module is a subclass of `Module`. Its externally visible interface is defined with `IO(new Bundle { ... })`, like opening windows in the module: outside logic can interact with it only through those windows.

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `Input(Bool())` —— 一位输入信号，类似 Verilog 的 `input wire` -->
> * `Input(Bool())` — a one-bit input signal, similar to Verilog's `input wire`
<!-- > * `Input(UInt(6.W))` —— 6 位无符号整数输入，`6.W` 表示位宽 -->
> * `Input(UInt(6.W))` — a 6-bit unsigned-integer input; `6.W` specifies the width
<!-- > * `Output(UInt(64.W))` —— 64 位无符号整数输出 -->
> * `Output(UInt(64.W))` — a 64-bit unsigned-integer output
<!-- > * `Bundle` —— 一组信号的集合，类似 Verilog 的 `struct` -->
> * `Bundle` — a collection of signals, similar to a Verilog `struct`

<!-- ## 3.3 Chisel 新武器：SyncReadMem -->
## 3.3 Chisel Tool: `SyncReadMem`

<!-- Chisel 提供了 `SyncReadMem` 来推断 FPGA 上的 BRAM 资源。它和 `Reg` 的区别就像"储物柜"和"桌面"——`Reg` 是少量数据放桌面随时拿，`SyncReadMem` 是大量数据放柜子里，需要一个时钟周期才能取出来。 -->
Chisel provides `SyncReadMem` to infer BRAM resources on an FPGA. The difference from `Reg` is like a storage cabinet versus a desktop: `Reg` keeps a small amount of data on the desktop for immediate access, while `SyncReadMem` stores a large amount in a cabinet and takes one clock cycle to read it.

```scala
  // 数据存储器：64 个柜子，每个柜子放 64 位数据
  val data_mem = SyncReadMem(64, UInt(64.W))
```

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `SyncReadMem(深度, 数据类型)` —— 同步读存储器，读操作需要一个时钟周期 -->
> * `SyncReadMem(depth, data type)` — a synchronous-read memory whose reads take one clock cycle
<!-- > * 对比 `Mem`（异步读）和 `Reg`（组合逻辑读） -->
> * Compare it with `Mem` (asynchronous read) and `Reg` (combinational read)
<!-- > * `SyncReadMem` 会被综合工具映射为 FPGA 上的真实 BRAM 资源 -->
> * Synthesis tools map `SyncReadMem` to physical BRAM resources on the FPGA

<!-- ## 3.4 Chisel 新武器：when -->
## 3.4 Chisel Tool: `when`

<!-- 写入操作只在 `wr_en` 为真时发生，这用 `when` 实现： -->
The write occurs only when `wr_en` is true; `when` expresses this condition:

```scala
  // 写操作：如果写使能，就把数据写进指定位置
  when(io.wr_en) {
    data_mem.write(io.wr_index, io.wr_data)
  }
  
  // 读操作：根据读索引和读使能读取数据
  io.rd_data := data_mem.read(io.rd_index, io.rd_en)
```

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `when(条件) { 操作 }` —— 类似 Verilog 的 `if`，但生成的是硬件选择器（MUX） -->
> * `when(condition) { operation }` — similar to Verilog `if`, but it generates hardware selection logic (a MUX)
<!-- > * `data_mem.write(地址, 数据)` —— 写存储器 -->
> * `data_mem.write(address, data)` — write the memory
<!-- > * `data_mem.read(地址, 使能)` —— 读存储器，使能为真时下一周期输出数据 -->
> * `data_mem.read(address, enable)` — read the memory; when enabled, the result appears in the next cycle
<!-- > * 链式条件：`when(...).elsewhen(...).otherwise(...)`，类似 `if / else if / else` -->
> * Chained conditions: `when(...).elsewhen(...).otherwise(...)`, analogous to `if / else if / else`

<!-- ## 3.5 完整代码与回顾 -->
## 3.5 Complete Code and Review

<!-- 完整代码见 [Bram.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/Bram.scala)，总共只有 33 行——你的第一个 Chisel 模块就这么简单！ -->
See the complete code in [Bram.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/Bram.scala). It is only 33 lines—your first Chisel module can be this simple!

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
<!-- 现在你只需要记住：**模块 = 接口 + 内部逻辑**。接口用 `IO(new Bundle)` 声明，存储用 `SyncReadMem`，条件用 `when`。这就是 Chisel 的基本骨架，后面的模块都是在这个骨架上添砖加瓦。 -->
For now, remember: **module = interface + internal logic**. Declare interfaces with `IO(new Bundle)`, storage with `SyncReadMem`, and conditions with `when`. This is Chisel's basic skeleton; later modules build on it.

:::

***

<!-- # 四、动手造 Cache——Step 2：CacheTag，给仓库做索引 -->
# 4. Build the Cache: Step 2 — CacheTag, Index the Warehouse

<!-- **核心思想：Tag 模块回答一个核心问题——"你要的数据在不在缓存里？在哪个路？"** -->
**Core idea: the Tag module answers one central question: "Is the data in the cache, and in which way?"**

<!-- ## 4.1 我们需要什么？ -->
## 4.1 What Do We Need?

<!-- 缓存有 4 路（Way 0 ~ Way 3），每路有 64 个组（Set），每个组存着一个标签。当 CPU 发来一个地址，Tag 模块需要： -->
The cache has four ways (Way 0–Way 3), each with 64 sets, and each set stores one tag. When the CPU supplies an address, the Tag module must:

<!-- 1. 从地址中分解出 **Tag / Index / Offset** -->
1. Decompose the address into **Tag / Index / Offset**.
<!-- 2. 用 Index 找到 4 路中的标签 -->
2. Use the Index to locate the tag in all four ways.
<!-- 3. 和请求的 Tag 比较 → 命中 or 缺失 -->
3. Compare against the requested Tag → hit or miss.
<!-- 4. 如果缺失，决定替换哪一路（Tree-PLRU） -->
4. On a miss, choose the way to replace (Tree-PLRU).

<!-- ## 4.2 Chisel 新武器：参数化模块 -->
## 4.2 Chisel Tool: Parameterized Modules

<!-- 我们的模块接受三个参数——地址各字段的位宽。这让模块可以复用到不同配置的缓存中： -->
Our module takes three parameters—the widths of the address fields—so it can be reused for caches with different configurations:

```scala
class CacheTag(
  INDEX_WD: Int,   // Index 位宽 = 6
  TAG_WD: Int,     // Tag 位宽 = 36
  OFFSET_WD: Int   // Offset 位宽 = 6
) extends Module {
```

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * 参数在 `()` 中声明，类型是 Scala 的 `Int`（不是 Chisel 的 `UInt`！） -->
> * Parameters are declared inside `()` and use Scala's `Int` type (not Chisel's `UInt`!).
<!-- > * 这些参数在**编译时**确定，用来控制生成多少硬件——就像 Verilog 的 `parameter` -->
> * They are fixed at **compile time** and control how much hardware is generated, like a Verilog `parameter`.
<!-- > * 但比 Verilog 更强大：你可以用 Scala 的全部能力（循环、条件、函数）来生成硬件 -->
> * They are more powerful than Verilog parameters: all of Scala's facilities (loops, conditions, and functions) can generate hardware.

<!-- ## 4.3 Chisel 新武器：位提取——地址分解 -->
## 4.3 Chisel Tool: Bit Extraction — Address Decomposition

<!-- 48 位地址被切分成三段，这是缓存设计最基础的操作： -->
A 48-bit address is split into three fields, the most basic operation in cache design:

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `x(高位, 低位)` —— 位提取，类似 Verilog 的 `x[高:低]` -->
> * `x(high, low)` — bit extraction, analogous to Verilog `x[high:low]`.
<!-- > * 注意 Chisel 的参数顺序是**高位在前**，和 Verilog 相同 -->
> * Chisel takes the **high bit first**, just like Verilog.

<!-- ## 4.4 Chisel 新武器：RegInit + VecInit——寄存器向量 -->
## 4.4 Chisel Tools: `RegInit` + `VecInit` — Register Vectors

<!-- 每一路需要 64 个标签存储项，每项是"1 位 Valid + 36 位 Tag"： -->
Each way needs 64 tag entries, each containing "1-bit Valid + 36-bit Tag":

```scala
val INDEX_NUM = 1 << INDEX_WD  // 64

// Way0 的标签存储：64 个项，每项 37 位，初始全 0
val tag_way0 = RegInit(
  VecInit(Seq.fill(INDEX_NUM)(
    0.U((TAG_WD + 1).W)  // 1位Valid + 36位Tag
  ))
)
```

<!-- 这行代码做了三件事： -->
This code performs three operations:

<!-- 1. `Seq.fill(64)(0.U(37.W))` —— Scala 层面生成 64 个 37 位零值的序列 -->
1. `Seq.fill(64)(0.U(37.W))` — generate a Scala sequence of 64 37-bit zero values.
<!-- 2. `VecInit(...)` —— 将 Scala 序列转为 Chisel 的硬件向量 -->
2. `VecInit(...)` — convert the Scala sequence into a Chisel hardware vector.
<!-- 3. `RegInit(...)` —— 将整个向量初始化为给定值，复位后生效 -->
3. `RegInit(...)` — initialize the entire vector to the given value, effective after reset.

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `Seq.fill(N)(value)` —— Scala 标准库函数，创建 N 个相同元素的序列 -->
> * `Seq.fill(N)(value)` — a Scala standard-library function that creates a sequence of N identical elements.
<!-- > * `VecInit` —— 把 Scala 序列包装成 Chisel 的 `Vec`，使其可以动态索引 -->
> * `VecInit` — wrap a Scala sequence as a Chisel `Vec` so it can be dynamically indexed.
<!-- > * `RegInit` —— 带复位初值的寄存器，类似 Verilog 的 `reg [37:0] tag_way0 [0:63] = {64{37'b0}}` -->
> * `RegInit` — a register with a reset value, similar to Verilog `reg [37:0] tag_way0 [0:63] = {64{37'b0}}`.
<!-- > * `tag_way0(index)` —— 动态索引读取，硬件会综合出 MUX -->
> * `tag_way0(index)` — a dynamically indexed read; synthesis produces a MUX.

<!-- ## 4.5 Chisel 新武器：Cat——位拼接 + 命中判断 -->
## 4.5 Chisel Tool: `Cat` — Bit Concatenation and Hit Detection

<!-- 判断命中的逻辑：把 Valid 位和请求的 Tag 拼起来，和存储的标签比较： -->
Hit detection concatenates the Valid bit with the requested Tag and compares the result with the stored tag:

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `Cat(a, b, c)` —— 位拼接，高位在前。类似 Verilog 的 `{a, b, c}` -->
> * `Cat(a, b, c)` — concatenate bits with the highest-order argument first, like Verilog `{a, b, c}`.
<!-- > * `===` —— Chisel 的**硬件比较**运算符。注意是三个等号！`==` 是 Scala 的引用比较 -->
> * `===` — Chisel's **hardware comparison** operator. It has three equals signs; `==` is Scala reference comparison.
<!-- > * `.orR` —— 对向量的所有位做 OR 归约。`hit.orR` 等价于"至少有一路命中" -->
> * `.orR` — OR-reduce all bits of a vector; `hit.orR` means "at least one way hit."

<!-- ## 4.6 Tree-PLRU 替换算法（选读） -->
## 4.6 Tree-PLRU Replacement (Optional Reading)

<!-- 当缓存缺失时，需要决定替换哪一路。Tree-PLRU 用一棵 3 位二叉树来近似 LRU： -->
On a cache miss, the design must choose a way to replace. Tree-PLRU approximates LRU with a three-bit binary tree:

```plain
          b2
        /    \
      b1      b0
     /  \    /  \
   way0 way1 way2 way3
```

<!-- 每次访问后，更新树的方向让指针"远离"刚访问的路。替换时，沿树的方向走到叶节点即可： -->
After each access, update the tree directions so the pointers point "away" from the way just accessed. For a replacement, follow the directions to a leaf:

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `Mux(条件, 真值, 假值)` —— 二选一多路选择器，类似 Verilog 的 `条件 ? 真值 : 假值` -->
> * `Mux(condition, trueValue, falseValue)` — a two-way multiplexer, like Verilog `condition ? trueValue : falseValue`.
<!-- > * `lru(use_index)(0)` —— 双重动态索引：先选组，再选位 -->
> * `lru(use_index)(0)` — a two-level dynamic index: select the set first, then the bit.

:::info
<!-- PLRU 的更新逻辑比较绕，不用一次理解透。先记住"3 棵树指针决定替换哪一路"就好，后面配合波形调试会豁然开朗。 -->
The PLRU update logic can be intricate, so do not try to absorb it all at once. For now, remember that "three tree pointers choose the replacement way"; waveform debugging will make the details clear later.

:::

<!-- 完整代码见 [CacheTag.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/CacheTag.scala)。 -->
See the complete code in [CacheTag.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/CacheTag.scala).

***

<!-- # 五、动手造 Cache——Step 3：CacheData，搭仓库货架 -->
# 5. Build the Cache: Step 3 — CacheData, Build the Warehouse Shelves

<!-- **核心思想：Data 模块负责存储和读取缓存的实际数据。它用 Bram 做基本单元，组织成 4 路 × 8 Bank 的存储阵列。** -->
**Core idea: the Data module stores and reads the cache's actual data. It uses Bram as the basic unit, arranged as a four-way × eight-bank storage array.**

<!-- ## 5.1 我们需要什么？ -->
## 5.1 What Do We Need?

<!-- 一个缓存行 64 字节 = 8 × 64 位。每路需要 8 个 BRAM（每个存 64 位宽的一段），4 路共 32 个 BRAM。当命中时，需要根据地址从正确的路和 Bank 中读出数据。 -->
A cache line is 64 bytes = 8 × 64 bits. Each way needs eight BRAMs (each stores one 64-bit segment), for 32 BRAMs across four ways. On a hit, the address selects the data from the correct way and bank.

<!-- ## 5.2 Chisel 新武器：Seq.fill + for 循环生成硬件 -->
## 5.2 Chisel Tools: `Seq.fill` + `for` Loops for Hardware Generation

<!-- 这是 Chisel 比 Verilog 强大得多的地方——用 Scala 的循环来批量生成重复的硬件结构： -->
This is where Chisel is much more powerful than Verilog: use Scala loops to generate repeated hardware structures in bulk:

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `Seq.fill(N)(Module(new SomeModule))` —— 批量实例化 N 个模块！这在 Verilog 中需要手写 N 遍或用 `generate` -->
> * `Seq.fill(N)(Module(new SomeModule))` — instantiate N modules in bulk; Verilog requires writing N instances or using `generate`.
<!-- > * `for (n <- 0 until N)` —— **Scala 编译期循环**，每次迭代生成不同的硬件连接 -->
> * `for (n <- 0 until N)` — a **Scala compile-time loop**; each iteration generates a different hardware connection.
<!-- > * `Wire(Vec(N, UInt(64.W)))` —— 声明一个 N 元素的线网向量 -->
> * `Wire(Vec(N, UInt(64.W)))` — declare an N-element vector of wires.
<!-- > * `x(高位, 低位)` —— 从 512 位缓存行中切出第 n 个 64 位段 -->
> * `x(high, low)` — slice the nth 64-bit segment from a 512-bit cache line.

:::info
<!-- `Seq.fill` + `for` 循环生成硬件是 Chisel 最强大的能力之一。在 Verilog 中，你需要写 32 个 BRAM 的接线代码（或用 `generate`），而在 Chisel 中只需一个循环。这就像从"手工缝 32 件衣服"升级到"缝纫机批量生产"。 -->
Generating hardware with `Seq.fill` and `for` is one of Chisel's most powerful capabilities. In Verilog you would write wiring for 32 BRAMs (or use `generate`); in Chisel one loop is enough. It is like upgrading from "sewing 32 garments by hand" to "batch production on a sewing machine."

:::

<!-- ## 5.3 Chisel 新武器：Mux1H——独热码多路选择 -->
## 5.3 Chisel Tool: `Mux1H` — One-Hot Multiplexing

<!-- 命中某一路后，需要从 8 个 Bank 的读数据中选出正确的那个。`Mux1H` 是 Chisel 提供的独热码选择器——当选择信号是独热码时，它比 `Mux` 链更高效： -->
After a way hits, choose the correct value from the eight bank read results. `Mux1H` is Chisel's one-hot selector; with a one-hot select signal, it is more efficient than a chain of `Mux`es:

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `Mux1H(选择向量, 数据向量)` —— 独热码选择器。当选择向量只有一位为 1 时，选中对应数据 -->
> * `Mux1H(selectVector, dataVector)` — a one-hot selector; when exactly one select bit is 1, it chooses the corresponding data.
<!-- > * `.asBools` —— 把 `UInt` 转成 `Vec[Bool]`，`Mux1H` 需要这种输入 -->
> * `.asBools` — convert a `UInt` to `Vec[Bool]`, the input form required by `Mux1H`.
<!-- > * `1.U << n` —— 生成独热码的经典方法，左移 n 位 -->
> * `1.U << n` — the classic way to generate a one-hot code: shift left by n bits.
<!-- > * `Mux1H` 比 `Mux` 链的综合结果更优，因为综合工具知道只有一条通路有效 -->
> * `Mux1H` synthesizes better than a `Mux` chain because the synthesis tool knows that only one path is active.

<!-- 完整代码见 [CacheData.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/CacheData.scala)。 -->
See the complete code in [CacheData.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/CacheData.scala).

***

<!-- # 六、动手造 Cache——Step 4：CacheCtrl，生产线主管 -->
# 6. Build the Cache: Step 4 — CacheCtrl, the Production Supervisor

<!-- **核心思想：控制器是整个 Cache 的"大脑"，它协调 Tag 判断、数据读取、MSHR 操作和内存交互。** -->
**Core idea: the controller is the cache's "brain," coordinating Tag decisions, data reads, MSHR operations, and memory transactions.**

<!-- ## 6.1 我们需要什么？ -->
## 6.1 What Do We Need?

<!-- 控制器要处理两类事务： -->
The controller handles two kinds of transactions:

<!-- 1. **Hit 路径**：CPU 请求命中 → 通知 Data 模块读数据 → 两周期后返回给 CPU -->
1. **Hit path**: a CPU request hits → tell the Data module to read → return to the CPU two cycles later.
<!-- 2. **Miss 路径**：CPU 请求缺失 → 写入 MSHR → 等内存返回 → 回填缓存 → 返回给 CPU -->
2. **Miss path**: a CPU request misses → write to the MSHR → wait for memory → refill the cache → return to the CPU.

<!-- 这两条路径可以同时发生（MSHR 有数据就绪时，CPU 也可能命中），所以需要**两个并行状态机**。 -->
The paths can occur simultaneously (the CPU may hit while the MSHR has data ready), so they require **two parallel state machines**.

<!-- ## 6.2 Chisel 新武器：ChiselEnum + switch/is——状态机 -->
## 6.2 Chisel Tools: `ChiselEnum` + `switch`/`is` — FSMs

<!-- Chisel 提供了 `ChiselEnum` 来定义状态机的状态，比手编数字编码更安全、更可读： -->
Chisel provides `ChiselEnum` for defining FSM states; it is safer and more readable than hand-coded numeric encodings:

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `ChiselEnum` —— 枚举类型，每个状态自动分配编码，不需要手写 `2'b00`、`2'b01` -->
> * `ChiselEnum` — an enumeration whose states receive encodings automatically; there is no need to write `2'b00`, `2'b01`, and so on.
<!-- > * `RegInit(IDLE)` —— 状态寄存器，复位值为 `IDLE` -->
> * `RegInit(IDLE)` — a state register reset to `IDLE`.
<!-- > * `switch(state) { is(XX) { ... } }` —— 类似 Verilog 的 `case`，但编译器会检查是否穷举 -->
> * `switch(state) { is(XX) { ... } }` — similar to Verilog `case`, but the compiler checks exhaustiveness.
<!-- > * 两个状态机用**不同的寄存器**（`hitState` 和 `missState`），天然并行运行 -->
> * The two FSMs use **different registers** (`hitState` and `missState`) and therefore run in parallel naturally.

<!-- ## 6.3 Chisel 新武器：RegNext——流水线寄存器 -->
## 6.3 Chisel Tool: `RegNext` — Pipeline Registers

<!-- Hit 路径需要两个周期（地址握手 → 数据返回），中间需要用 `RegNext` 打一拍寄存器： -->
The hit path takes two cycles (address handshake → data response), so insert one register stage with `RegNext`:

```scala
// 将 Data 模块的读出数据延迟一拍，和状态机的时序对齐
val rdata_fromData_reg = RegNext(io.rdata_fromData)
val rdIdx_fromCPU_reg  = RegNext(io.rdIdx_fromCPU)
val reqNum_fromCPU_reg = RegNext(io.reqNum_fromCPU)
```

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `RegNext(x)` —— 自动生成一个寄存器，下一周期输出 x 的当前值 -->
> * `RegNext(x)` — automatically generate a register whose output in the next cycle is the current value of `x`.
<!-- > * `RegNext(x, init)` —— 带复位初值的版本 -->
> * `RegNext(x, init)` — the variant with a reset value.
<!-- > * 在流水线设计中，`RegNext` 是对齐时序的利器——就像在传送带上加一个工位 -->
> * In pipeline designs, `RegNext` is a powerful timing-alignment tool—like adding a station to a conveyor belt.

<!-- ## 6.4 突发传输组装 -->
## 6.4 Assembling a Burst Transfer

<!-- 内存以 64 位宽度突发传输，8 个周期传完一个 64 字节缓存行。控制器用计数器 + 缓冲区拼装数据： -->
Memory transfers bursts 64 bits wide, completing one 64-byte cache line in eight cycles. The controller assembles the data with a counter and a buffer:

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `Reg(Vec(N, UInt(W.W)))` —— 寄存器向量，可以动态索引写入 -->
> * `Reg(Vec(N, UInt(W.W)))` — a register vector that supports dynamically indexed writes.
<!-- > * `Cat(a, b, ...)` 拼接时高位在前——`Cat(buf(7), ..., buf(0))` 使得 buf(7) 在最高位 -->
> * `Cat(a, b, ...)` concatenates with the high-order argument first; `Cat(buf(7), ..., buf(0))` places `buf(7)` at the highest bits.

<!-- 完整代码见 [CacheCtrl.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/CacheCtrl.scala)。 -->
See the complete code in [CacheCtrl.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/CacheCtrl.scala).

***

<!-- # 七、动手造 Cache——Step 5：MSHR，订单追踪系统 -->
# 7. Build the Cache: Step 5 — MSHR, the Order-Tracking System

<!-- **核心思想：MSHR 是非阻塞缓存之所以"非阻塞"的关键。它追踪每一个未完成的缺失请求，支持请求合并和乱序响应。** -->
**Core idea: the MSHR is what makes a non-blocking cache "non-blocking." It tracks every outstanding miss and supports request merging and out-of-order responses.**

<!-- > 如果你前面的步骤都理解了，但觉得 MSHR 比较复杂——这是正常的。MSHR 是整个项目中最复杂的模块，但拆开来看，每个部分都只用到了前面学过的 Chisel 知识。 -->
> If you understood the earlier steps but find the MSHR complicated, that is normal. It is the most complex module in the project, yet each part uses only Chisel concepts introduced above.

<!-- ## 7.1 MSHR 是什么？ -->
## 7.1 What Is an MSHR?

<!-- MSHR = Miss Status Holding Register，你可以把它想象成餐厅的**订单追踪板**： -->
MSHR means Miss Status Holding Register; think of it as a restaurant's **order-tracking board**:

```plain
┌─ MSHR Table (4项) ──────────────────┐    ┌─ Load Table (8项) ───────────────┐
│ #0: 地址=0x1A00 已发出 数据未到       │    │ #0: 属MSHR#0, 寄存器R3, 数据已到  │
│ #1: 地址=0x2B00 未发出               │    │ #1: 属MSHR#0, 寄存器R7, 数据未到  │
│ #2: 空                               │    │ #2: 属MSHR#1, 寄存器R5, 数据未到  │
│ #3: 空                               │    │ #3~#7: 空                         │
└──────────────────────────────────────┘    └──────────────────────────────────┘
```

<!-- * **MSHR Table**（4项）：追踪 4 个不同的缓存行缺失，每项对应一次内存请求 -->
* **MSHR Table** (four entries): tracks four different cache-line misses, one memory request per entry.
<!-- * **Load Table**（8项）：追踪 8 个具体的 CPU 读请求。多个请求可以指向同一个 MSHR 表项 → 请求合并 -->
* **Load Table** (eight entries): tracks eight individual CPU reads. Multiple reads can point to one MSHR entry → request merging.

<!-- ## 7.2 Chisel 新武器：自定义 Bundle——结构化数据 -->
## 7.2 Chisel Tool: Custom `Bundle`s — Structured Data

<!-- MSHR 的表项有多个字段，用自定义 `Bundle` 组织： -->
An MSHR entry has multiple fields, organized with a custom `Bundle`:

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `Bundle` 类似 C 的 `struct`，把多个信号打包在一起 -->
> * A `Bundle` is like a C `struct` that groups multiple signals.
<!-- > * 字段类型用 Chisel 类型（`Bool()`、`UInt(N.W)`），不是 Scala 类型 -->
> * Fields use Chisel types (`Bool()`, `UInt(N.W)`), not Scala types.
<!-- > * 自定义 Bundle 可以用 `.` 访问字段：`entry.valid`、`entry.addr` -->
> * Fields of a custom Bundle are accessed with `.`, for example `entry.valid` and `entry.addr`.

<!-- ## 7.3 Chisel 新武器：VecInit 初始化复杂 Bundle -->
## 7.3 Chisel Tool: `VecInit` for Complex Bundles

<!-- 初始化含 Bundle 的寄存器向量时，需要逐字段赋初值： -->
When initializing a register vector containing Bundles, assign an initial value to each field:

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * 不能直接写 `0.U` 来初始化 Bundle——必须先 `Wire` 一个实例，逐字段赋值 -->
> * You cannot initialize a Bundle directly with `0.U`; first create a `Wire` instance and assign each field.
<!-- > * `VecInit(Seq.fill(N) { ... })` —— 生成 N 个相同初值的向量 -->
> * `VecInit(Seq.fill(N) { ... })` — generate a vector of N identical initial values.
<!-- > * 这和 Step 2 中 `VecInit(Seq.fill(64)(0.U(37.W)))` 本质一样，只是初值更复杂 -->
> * This is conceptually the same as `VecInit(Seq.fill(64)(0.U(37.W)))` in Step 2, but with a more complex initial value.

<!-- ## 7.4 Chisel 新武器：map + reduce——向量操作 -->
## 7.4 Chisel Tools: `map` + `reduce` — Vector Operations

<!-- MSHR 中大量使用 Scala 的函数式操作来处理向量信号： -->
The MSHR uses Scala's functional operations extensively to process vector signals:

```scala
// 提取所有表项的 valid 字段
val mshr_valid_vec = mshr_entries.map(_.valid)

// 判断 MSHR 是否已满（所有项都 valid）
val mshr_full = mshr_valid_vec.reduce(_ && _)

// 判断 MSHR 是否为空（所有项都非 valid）
val mshr_empty = !mshr_valid_vec.reduce(_ || _)
```

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `.map(f)` —— 对向量每个元素应用函数 f，返回新向量 -->
> * `.map(f)` — apply function `f` to each vector element and return a new vector.
<!-- > * <code>.reduce(_ && _)</code> —— 用 `&&` 把所有元素折叠成一个值。等价于 `v(0) && v(1) && ... && v(N)` -->
> * <code>.reduce(_ && _)</code> — fold all elements into one value with `&&`, equivalent to `v(0) && v(1) && ... && v(N)`.
<!-- > * 这些是 **Scala 的函数式编程**特性，但在 Chisel 中它们生成的是**硬件逻辑**（与/或门树） -->
> * These are **Scala functional-programming** features, but in Chisel they generate **hardware logic** (AND/OR gate trees).

<!-- ## 7.5 Chisel 新武器：zipWithIndex + PriorityEncoder——优先级编码 -->
## 7.5 Chisel Tools: `zipWithIndex` + `PriorityEncoder` — Priority Encoding

<!-- 查找空闲表项、匹配地址等操作用到了 `zipWithIndex` 和 `PriorityEncoder`： -->
Finding free entries and matching addresses uses `zipWithIndex` and `PriorityEncoder`:

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `.zipWithIndex` —— 给每个元素附上索引号，变成 `(元素, 索引)` 的序列 -->
> * `.zipWithIndex` — attach an index to each element, producing a sequence of `(element, index)` pairs.
<!-- > * `PriorityEncoder(vec)` —— 返回向量中第一个为真的索引（二进制编码） -->
> * `PriorityEncoder(vec)` — return the index of the first true element (binary encoded).
<!-- > * `PriorityEncoderOH(vec)` —— 返回独热码编码（只有一位为 1） -->
> * `PriorityEncoderOH(vec)` — return a one-hot encoding (only one bit is 1).
<!-- > * `OHToUInt(独热码)` —— 独热码转二进制索引 -->
> * `OHToUInt(oneHot)` — convert a one-hot code to a binary index.
<!-- > * 独热码的好处：可以和 `Mux1H` 配合做多路选择，综合效果更好 -->
> * One-hot codes work with `Mux1H` for multiplexing and generally synthesize better.

<!-- ## 7.6 MSHR 的五大任务 -->
## 7.6 The MSHR's Five Tasks

<!-- MSHR 内部管理五大任务，形成一条完整的处理流水线： -->
The MSHR manages five tasks internally, forming a complete processing pipeline:

<!-- | 任务 | 做什么 | 关键 Chisel 技法 | -->
<!-- | --- | --- | --- | -->
<!-- | A. 接收 miss | 写入 MSHR + Load Table | `when` 条件写寄存器，`Mux` 选表项 ID | -->
<!-- | B. 发内存请求 | 从 MSHR 找未发出的请求 | `PriorityEncoderOH` + `Mux1H` | -->
<!-- | C. 收内存响应 | 根据请求 ID 匹配表项，广播到 Load Table | `zipWithIndex.map` + `for` 循环广播 | -->
<!-- | D. 写缓存 | 数据有效后写 CacheData + CacheTag | `PriorityEncoderOH` + `Mux1H` | -->
<!-- | E. 返回 CPU | Load Table 数据就绪后返回 | `PriorityEncoderOH` + `Mux1H` | -->
| Task | Action | Key Chisel technique |
| --- | --- | --- |
| A. Accept a miss | Write the MSHR + Load Table | Conditional register writes with `when`; select entry ID with `Mux` |
| B. Issue a memory request | Find an unissued request in the MSHR | `PriorityEncoderOH` + `Mux1H` |
| C. Accept a memory response | Match the entry by request ID and broadcast to the Load Table | `zipWithIndex.map` + broadcast `for` loop |
| D. Write the cache | Write CacheData + CacheTag when data is valid | `PriorityEncoderOH` + `Mux1H` |
| E. Respond to the CPU | Return when Load Table data is ready | `PriorityEncoderOH` + `Mux1H` |

:::info
<!-- MSHR 代码有 366 行，但不要被吓到。它的核心就是"两张表 + 五个任务"。建议先通读 [MSHR.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/MSHR.scala) 中的注释，理解每个任务的输入输出，再看具体实现。特别注意任务 A 中的**请求合并**逻辑——当新 miss 地址与已有 MSHR 表项匹配时，只写 Load Table 不写 MSHR Table。 -->
The MSHR implementation has 366 lines, but do not be intimidated. Its core is simply "two tables + five tasks." First read the comments in [MSHR.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/MSHR.scala) to understand each task's inputs and outputs, then study the implementation. Pay particular attention to **request merging** in task A: when a new miss address matches an existing MSHR entry, write only the Load Table, not the MSHR Table.

:::

<!-- 完整代码见 [MSHR.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/MSHR.scala)。 -->
See the complete code in [MSHR.scala](https://github.com/HisionWang/NonBlockingCache/blob/main/src/main/scala/MSHR.scala).

***

<!-- # 八、动手造 Cache——Step 6：顶层组装，把零件焊在一起 -->
# 8. Build the Cache: Step 6 — Top-Level Integration, Wire the Parts Together

<!-- **核心思想：顶层模块就像 PCB 板——它不包含逻辑，只负责把各模块的引脚正确地连接起来。** -->
**Core idea: the top-level module is like a PCB—it contains no functional logic, only the correct connections between module pins.**

<!-- ## 8.1 Chisel 新武器：模块实例化与连线 -->
## 8.1 Chisel Tools: Module Instantiation and Wiring

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `Module(new SomeModule(params))` —— 实例化子模块，类似 Verilog 的 `SomeModule #(.PARAM(val)) inst_name (...)` -->
> * `Module(new SomeModule(params))` — instantiate a child module, like Verilog `SomeModule #(.PARAM(val)) inst_name (...)`.
<!-- > * `子模块.io.信号名 := 值` —— 连接线网。`:=`\*\* 是 Chisel 的连线操作符\*\* -->
> * `child.io.signalName := value` — connect wires; `:=` is Chisel's connection operator.
<!-- > * `dontTouch(子模块.io)` —— 防止综合工具优化掉看似未使用的信号（调试利器） -->
> * `dontTouch(child.io)` — prevent synthesis from optimizing away apparently unused signals (a useful debugging aid).

<!-- ## 8.2 Chisel 新武器：withClock——自定义时钟 -->
## 8.2 Chisel Tool: `withClock` — Custom Clocks

<!-- 本项目中有一个特殊的处理——使用反相时钟： -->
This project has one special treatment: it uses an inverted clock:

```scala
val clkSignal = !clock.asBool
withClock(clkSignal.asClock) {
  // 所有子模块在反相时钟下运行
  val cache_tag = Module(new CacheTag(INDEX_WD, TAG_WD, OFFSET_WD))
  // ...
}
```

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `clock.asBool` → `!` 取反 → `.asClock` 转回 Clock 类型 -->
> * `clock.asBool` → invert with `!` → convert back to `Clock` with `.asClock`.
<!-- > * `withClock(自定义时钟) { ... }` —— 让块内的寄存器使用指定时钟沿 -->
> * `withClock(customClock) { ... }` — make registers in the block use the specified clock edge.

:::info
<!-- `withClock` 取反时钟是本项目的一个"临时解决方案"（如代码注释所说），正常设计中不应该需要这样做。了解这个语法即可，不必深究原因。 -->
The inverted clock used with `withClock` is a "temporary workaround" in this project (as the code comment says); a normal design should not need it. Learn the syntax without dwelling on the reason.

:::

<!-- ## 8.3 Chisel 新武器：emitVerilog——生成 Verilog -->
## 8.3 Chisel Tool: `emitVerilog` — Generate Verilog

<!-- 最后，用 `App` 对象生成 Verilog： -->
Finally, use an `App` object to emit Verilog:

```scala
object NonBlockingCache extends App {
  emitVerilog(
    new NonBlockingCache(6, 36, 6),       // 实例化顶层模块
    Array("--target-dir", "generated")     // 输出目录
  )
}
```

<!-- 运行 `sbt run` 即可在 `generated/` 目录下生成 Verilog 文件。 -->
Run `sbt run` to generate Verilog files in `generated/`.

<!-- 完整代码见 [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala#L1-L135)。 -->
See the complete code in [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala#L1-L135).

***

<!-- # 九、测试与验证——质检上线 -->
# 9. Testing and Verification: Quality Control Goes Live

<!-- **核心思想：硬件设计没有测试就像没有安全网的走钢丝——你不知道什么时候会摔。** -->
**Core idea: untested hardware design is like walking a tightrope without a safety net—you do not know when you will fall.**

<!-- ## 9.1 Chisel 新武器：ChiselTest 基础 -->
## 9.1 Chisel Tool: ChiselTest Basics

<!-- ChiselTest 是 Chisel 的仿真测试框架，核心操作就三个——`poke`（写输入）、`step`（推进时钟）、`peek`（读输出）： -->
ChiselTest is Chisel's simulation and testing framework. Its three core operations are `poke` (write inputs), `step` (advance the clock), and `peek` (read outputs):

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

<!-- > 📖 **Chisel 小课堂**： -->
> 📖 **Chisel mini-lesson**:
>
<!-- > * `dut.端口.poke(value)` —— 给输入端口赋值。`true.B` 是 Chisel 的 Bool 常量 -->
> * `dut.port.poke(value)` — assign an input port; `true.B` is a Chisel Bool literal.
<!-- > * `dut.端口.peek()` —— 读取输出端口值 -->
> * `dut.port.peek()` — read an output port value.
<!-- > * `dut.clock.step(N)` —— 推进 N 个时钟周期 -->
> * `dut.clock.step(N)` — advance the clock by N cycles.
<!-- > * `WriteVcdAnnotation` —— 生成 VCD 波形文件，可用 GTKWave 查看 -->
> * `WriteVcdAnnotation` — generate a VCD waveform file for viewing in GTKWave.

<!-- ## 9.2 测试架构 -->
## 9.2 Test Architecture

<!-- 本项目的测试用三个角色协同工作： -->
The tests in this project use three cooperating roles:

<!-- | 角色 | 文件 | 职责 | 比喻 | -->
<!-- | --- | --- | --- | --- | -->
<!-- | FakeCPU | [FakeCPU.scala](src/test/scala/FakeCPU.scala) | 发请求、验数据 | 模拟客户下单+验货 | -->
<!-- | FakeRAM | [FakeNonBlockingRAM.scala](src/test/scala/FakeNonBlockingRAM.scala) | 随机延迟返回数据 | 模拟快递（可能晚到、可能乱序） | -->
<!-- | CacheSpec | [CacheSpec.scala](src/test/scala/CacheSpec.scala) | 编排测试流程 | 质检主管 | -->
| Role | File | Responsibility | Analogy |
| --- | --- | --- | --- |
| FakeCPU | [FakeCPU.scala](src/test/scala/FakeCPU.scala) | Issues requests and checks data | Customer placing and inspecting an order |
| FakeRAM | [FakeNonBlockingRAM.scala](src/test/scala/FakeNonBlockingRAM.scala) | Returns data after random delays | Courier (may be late or out of order) |
| CacheSpec | [CacheSpec.scala](src/test/scala/CacheSpec.scala) | Orchestrates the test flow | Quality-control supervisor |

<!-- FakeCPU 的请求不是纯随机的——它刻意制造了三种典型场景： -->
FakeCPU's requests are not purely random; it deliberately creates three representative scenarios:

<!-- | 场景 | 做什么 | 验证什么 | -->
<!-- | --- | --- | --- | -->
<!-- | 时间局部性 | 反复访问相同地址 | 命中路径正确性 | -->
<!-- | 索引冲突 | 访问相同 Set 不同 Tag | 替换算法正确性 | -->
<!-- | 完全随机 | 任意地址 | 系统鲁棒性 | -->
| Scenario | Action | Verifies |
| --- | --- | --- |
| Temporal locality | Repeatedly access the same address | Hit-path correctness |
| Index conflict | Access different Tags in the same Set | Replacement-algorithm correctness |
| Fully random | Use arbitrary addresses | System robustness |

<!-- ## 9.3 日常开发流程 -->
## 9.3 Daily Development Flow

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

<!-- **常用操作速查：** -->
**Common operations at a glance:**

<!-- | 我想… | 命令 | -->
<!-- | --- | --- | -->
<!-- | 只跑测试 | `sbt test` | -->
<!-- | 只生成 Verilog | `sbt run` | -->
<!-- | 文件保存后自动重跑测试 | `sbt ~test` | -->
<!-- | 查看波形 | 打开 `test_run_dir/` 下的 `.vcd` 文件（需要 GTKWave） | -->
| I want to… | Command |
| --- | --- |
| Run only the tests | `sbt test` |
| Generate only Verilog | `sbt run` |
| Re-run tests automatically after saving | `sbt ~test` |
| View waveforms | Open the `.vcd` files under `test_run_dir/` (GTKWave required) |

***

<!-- # 十、常见问题与排错 -->
# 10. Common Problems and Troubleshooting

<!-- **核心思想：遇到问题别慌，大部分是环境或语法的问题。** -->
**Core idea: do not panic when something goes wrong; most issues are environmental or syntactic.**

<!-- ## 10.1 编译类问题 -->
## 10.1 Compilation Problems

<!-- ### ❓ `sbt test` 报编译错误 -->
### ❓ `sbt test` reports a compilation error

<!-- **常见语法陷阱**——对照检查清单： -->
**Common syntax traps**—use this checklist:

<!-- | 错误写法 | 正确写法 | 原因 | -->
<!-- | --- | --- | --- | -->
<!-- | `a == b` | `a === b` | `==` 是 Scala 引用比较，`===` 才是硬件比较 | -->
<!-- | `x := y` | `io.x := y` | `:=` 只能连接 Chisel 信号，不能连接 Scala 变量 | -->
<!-- | `when ... end` | `when(...) { ... }` | Chisel 用花括号，不需要 `end` | -->
<!-- | `UInt(6)` | `UInt(6.W)` | 位宽要用 `.W` 后缀 | -->
<!-- | `val x = 3` 在硬件逻辑中 | `val x = 3.U` | Scala 的 `3` 不会变成硬件常量 | -->
| Incorrect | Correct | Reason |
| --- | --- | --- |
| `a == b` | `a === b` | `==` is Scala reference comparison; `===` is the hardware comparison |
| `x := y` | `io.x := y` | `:=` connects Chisel signals, not Scala variables |
| `when ... end` | `when(...) { ... }` | Chisel uses braces; no `end` is needed |
| `UInt(6)` | `UInt(6.W)` | Widths require the `.W` suffix |
| `val x = 3` in hardware logic | `val x = 3.U` | Scala's `3` is not a hardware literal |

<!-- ### ❓ 首次 `sbt` 运行极慢 -->
### ❓ The first `sbt` run is very slow

<!-- **原因**：sbt 在下载 Chisel、ChiselTest 等依赖。\ -->
**Cause**: sbt is downloading dependencies such as Chisel and ChiselTest.\
<!-- **解决**：耐心等待（可能需要 1-10 分钟），可配置 sbt 镜像源加速。 -->
**Solution**: wait patiently (it may take 1–10 minutes), or configure an sbt mirror to speed up downloads.

<!-- ## 10.2 仿真类问题 -->
## 10.2 Simulation Problems

<!-- ### ❓ 测试运行时 PASS 率不是 100% -->
### ❓ The PASS rate is not 100% during testing

<!-- **原因**：如果你修改了设计代码，可能引入了 bug。\ -->
**Cause**: modifying the design code may have introduced a bug.\
<!-- **解决**： -->
**Solution**:

<!-- 1. 查看 `[VERIFY]` 块中 Expected 和 Actual 的差异 -->
1. Compare Expected and Actual in the `[VERIFY]` block.
<!-- 2. 打开 `.vcd` 波形文件，用 GTKWave 分析时序 -->
2. Open the `.vcd` waveform and analyze timing in GTKWave.

<!-- ### ❓ 想修改缓存参数但不知道改哪里 -->
### ❓ You want to change cache parameters but do not know where

<!-- **三个地方需要同步修改**： -->
**Three places must be changed consistently**:

<!-- 1. [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala#L41-L44) 的类参数 -->
1. The class parameters in [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala#L41-L44).
<!-- 2. [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala#L131-L134) 的 `emitVerilog` 调用 -->
2. The `emitVerilog` call in [NonBlockingCache.scala](src/main/scala/NonBlockingCache.scala#L131-L134).
<!-- 3. [CacheSpec.scala](src/test/scala/CacheSpec.scala#L12) 的 `test` 调用 -->
3. The `test` call in [CacheSpec.scala](src/test/scala/CacheSpec.scala#L12).

<!-- > 如果以上方法都无法解决，可以在 GitHub 仓库的 [Issues](https://github.com/HisionWang/NonBlockingCache/issues) 页面提问。 -->
> If these methods do not solve the problem, ask on the repository's GitHub [Issues](https://github.com/HisionWang/NonBlockingCache/issues) page.

***

<!-- # 十一、学习路径与总结 -->
# 11. Learning Path and Summary

<!-- ## 11.1 你的 Chisel 技能树 -->
## 11.1 Your Chisel Skill Tree

<!-- 🎉 **恭喜你走到了最后！** 让我们回顾一下你沿途收获的 Chisel 技能： -->
🎉 **Congratulations on reaching the end!** Let us review the Chisel skills you picked up along the way:

<!-- | Chisel 技能 | 你在哪个 Step 学会的 | 用在哪 | -->
<!-- | --- | --- | --- | -->
<!-- | `Module` / `IO` / `Bundle` | Step 1 — Bram | 每个模块 | -->
<!-- | `SyncReadMem` / `when` | Step 1 — Bram | 存储器读写 | -->
<!-- | 参数化模块 / `RegInit` / `VecInit` | Step 2 — CacheTag | 寄存器向量 | -->
<!-- | 位提取 / `Cat` / `Mux` | Step 2 — CacheTag | 地址分解、命中判断 | -->
<!-- | `Seq.fill` / `for` 生成硬件 | Step 3 — CacheData | 批量实例化 | -->
<!-- | `Mux1H` / 独热码选择 | Step 3 — CacheData | 多路数据选择 | -->
<!-- | `ChiselEnum` / `switch`/`is` | Step 4 — CacheCtrl | 状态机 | -->
<!-- | `RegNext` / 流水线对齐 | Step 4 — CacheCtrl | 时序对齐 | -->
<!-- | 自定义 `Bundle` | Step 5 — MSHR | 结构化数据 | -->
<!-- | `map` / `reduce` / `zipWithIndex` | Step 5 — MSHR | 向量操作 | -->
<!-- | `PriorityEncoder` / `PriorityEncoderOH` | Step 5 — MSHR | 优先级查找 | -->
<!-- | 模块实例化与连线 / `emitVerilog` | Step 6 — 顶层 | 组装与生成 | -->
<!-- | `ChiselTest` / `poke`/`peek`/`step` | Step 9 — 测试 | 仿真验证 | -->
| Chisel skill | Learned in | Used for |
| --- | --- | --- |
| `Module` / `IO` / `Bundle` | Step 1 — Bram | Every module |
| `SyncReadMem` / `when` | Step 1 — Bram | Memory reads and writes |
| Parameterized modules / `RegInit` / `VecInit` | Step 2 — CacheTag | Register vectors |
| Bit extraction / `Cat` / `Mux` | Step 2 — CacheTag | Address decomposition and hit detection |
| `Seq.fill` / `for` hardware generation | Step 3 — CacheData | Bulk instantiation |
| `Mux1H` / one-hot selection | Step 3 — CacheData | Multi-way data selection |
| `ChiselEnum` / `switch`/`is` | Step 4 — CacheCtrl | FSMs |
| `RegNext` / pipeline alignment | Step 4 — CacheCtrl | Timing alignment |
| Custom `Bundle` | Step 5 — MSHR | Structured data |
| `map` / `reduce` / `zipWithIndex` | Step 5 — MSHR | Vector operations |
| `PriorityEncoder` / `PriorityEncoderOH` | Step 5 — MSHR | Priority lookup |
| Module instantiation and wiring / `emitVerilog` | Step 6 — Top level | Integration and generation |
| `ChiselTest` / `poke`/`peek`/`step` | Step 9 — Testing | Simulation-based verification |

***

<!-- > 🌟 **最后一句话**：Chisel 的本质就是"用 Scala 的编程能力来生成硬件"。`for` 循环、`map`/`reduce`、参数化——这些都是 Scala 在编译期帮你写重复的 Verilog 代码。当你不再觉得"这是在写软件"而是"这是在用编程生成硬件"的时候，你就真正理解 Chisel 了。**每个 Chisel 大佬都是从 **`sbt test`** 第一次 PASS 开始的——你已经迈出了最重要的一步。** -->
> 🌟 **One final thought**: Chisel's essence is "using Scala's programming capabilities to generate hardware." `for` loops, `map`/`reduce`, and parameterization let Scala write repetitive Verilog at compile time. When you stop thinking "I am writing software" and start thinking "I am generating hardware with programming," you truly understand Chisel. **Every Chisel expert starts with the first PASS from **`sbt test`**—you have already taken the most important step.**

<!-- *最后更新：2026年5月26日 · 项目作者：HisionWang* -->
*Last updated: May 26, 2026 · Project author: HisionWang*

***


<!-- > 更新: 2026-05-26 18:33:31 -->
<!-- > 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/qx1hvfi3p5w1h4gi> -->
> Updated: 2026-05-26 18:33:31
> Source: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/qx1hvfi3p5w1h4gi>
