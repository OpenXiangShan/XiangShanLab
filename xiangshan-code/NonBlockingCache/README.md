# Non-Blocking Cache 项目

## 项目简介

本项目实现了一个支持多未完成缺失请求（Non-blocking）的缓存（Cache）系统。缓存采用4路组相联结构，支持乱序响应和突发传输，能够显著提高处理器的访存效率。项目使用Chisel硬件描述语言编写（初学Chisel，第一次使用Chisel编码并验证，代码风格可能不太成熟请见谅），通过ScalaTest框架构建了完整的验证环境，确保设计的正确性和可靠性。

## 笔试题目

题目：Non-blocking Cache设计与验证
设计一个Non-blocking Cache（支持多outstanding miss），用于连接CPU读取（这里只读不写）和memory。
Cpu请求的PA宽度48bit（假设RV64）。Memory接口64bit。data cache: 16-KB, 4-way set associative, 64-byte line size。
要求：

1. 组织方式
   任意替换算法
2. Outstanding Miss
   支持至少4个miss请求而不阻塞
   给出RTL代码（Chisel，SV，VHDL都可以）以及验证代码和结果，增加文档说明。

## 项目结构

```
NonBlockingCache/
├── README.md                        # 本文件
├── build.sbt                        # sbt构建配置文件
├── project/                         # sbt项目配置
├── doc/                             # 设计文档和验证结果
│   ├── 设计、验证报告.pdf
│   ├── 架构图.svg
│   ├── 过程记录文档.xlsx
│   └── 验证结果/
│       ├── NonBlockingCache.vcd     # 波形文件
│       └── 运行结果.png
├── src/
│   ├── main/scala/                  # 主设计代码
│   │   ├── NonBlockingCache.scala   # 顶层模块
│   │   ├── CacheCtrl.scala          # 缓存控制器
│   │   ├── CacheData.scala          # 数据存储模块
│   │   ├── CacheTag.scala           # 标签存储模块
│   │   ├── MSHR.scala               # Miss Status Holding Register
│   │   └── Bram.scala               # BRAM封装模块
│   └── test/scala/                  # 测试代码
│       ├── CacheSpec.scala          # 主测试套件
│       ├── FakeCPU.scala            # 模拟CPU请求
│       ├── FakeNonBlockingRAM.scala # 模拟内存响应
│       ├── GlobalVars.scala         # 全局配置
│       └── Tool.scala               # 工具函数
├── TestData/                        # 测试数据
│   ├── Ram.txt                      # 内存数据文件
│   └── Req.txt                      # 请求序列文件
├── generated/                       # 生成的Verilog代码
│   ├── NonBlockingCache.v
│   ├── NonBlockingCache.fir
│   └── NonBlockingCache.anno.json
├── target/                          # 构建输出目录
└── test_run_dir/                    # 测试运行时目录
```

## 文档说明

完整的设计文档、验证报告和架构图位于 `doc/` 目录：

- **设计验证报告**：`doc/设计、验证报告.pdf`
  - 包含架构设计、模块实现、验证策略等
- **系统架构图**：`doc/架构图.svg`
  - 可视化展示模块间连接和数据流向
- **验证结果**：`doc/验证结果/`
  - 包含波形文件和运行截图

## 设计规格

本项目实现了一个符合以下规格的缓存系统：

- **缓存容量**：16KB
- **组织结构**：4路组相联
- **缓存行大小**：64字节
- **地址宽度**：48位（RV64物理地址）
- **数据接口**：CPU端64位，内存端64位突发传输
- **支持特性**：最多4个未完成不重复/8个未完成部分重复的缺失请求，Tree-PLRU替换算法
- **缺失处理**：支持请求合并、乱序响应、突发传输

## 快速开始

### 环境要求

- JDK 8或更高版本
- sbt
- Chisel
- Scala

### 运行测试

1. **修改测试参数**：
   编辑 `src/test/scala/GlobalVars.scala` 文件，可以调整以下参数：

   - `randomSeed`：随机数生成器种子
   - `TEST_NUM`：测试请求数量
2. **执行测试**：
   在项目根目录下运行以下命令：

   ```
   sbt test
   ```

   测试程序将自动生成测试数据并运行完整的验证流程。
3. **测试输出**：（对于测试环境的介绍请详见设计、验证报告）
   测试运行时会显示详细的交互信息，包括：

- CPU请求发送时间、地址和寄存器号
- 内存接收请求和返回数据
- 数据验证结果（PASS/ERR）
- 最终测试统计信息

### 测试输出示例（仅一条请求数据）

```
[info] welcome to sbt 1.12.0 (Ubuntu Java 21.0.10)
[info] loading project definition from /mnt/d/myCPU_new/NonBlockingCache/project
[info] loading settings for project nonblockingcache from build.sbt...
[info] set current project to nonblockingcache (in build file:/mnt/d/myCPU_new/NonBlockingCache/)
[info] compiling 1 Scala source to /mnt/d/myCPU_new/NonBlockingCache/target/scala-2.13/test-classes ...
Generating RAM data in ./TestData/Ram.txt ......
Generating request data in ./TestData/Req.txt ......
Start testing ......

[CPU ] SEND     [Time    2] REQ [  0] addr=0x8a505179bda1 rd=21
[RAM ] RECEIVE  [Time    5] REQ [  0] addr=0x00008a505179bda1
[RAM ] BACKDATA [Time   17] REQ [  0] addr=0x00008a505179bda1
                                   data=0xb683e5eaaf04e5edf1a0a647a7d9d14acd74dae963e0e2623bc67b4522d425d6a0fc3350fa89c46376725e1a7588f169ef601b656211aa02a43d23faae685a85

[CPU ] RECIEVE  [Time   28] REQ [  0] rd=21 data=0x3bc67b4522d425d6
┌──────────────────────────────────────────────────────────────────┐
│ [VERIFY] SendReq: 0                                              │
├──────────────────────────────────────────────────────────────────┤
│ Addr        : 0x00008a505179bda1                                 │
│ LineIndex   : 0x36                                               │
│ BankIndex   : 0x04                                               │
├──────────────────────────────────────────────────────────────────┤
│ Expected    : Data: 0x3bc67b4522d425d6 RdIdx: 21                 │
│ Actual      : Data: 0x3bc67b4522d425d6 RdIdx: 21                 │
└──────────────────────────────────────────────────────────────────┘
 --------> PASS
Tested Num: 1     All Test Num: 1
[info] CacheSpec:
[info] Non-BlockingCache
[info] - should using random delays through randomly generated test cases
[info] Run completed in 16 seconds, 985 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 20 s, completed Feb 20, 2026, 5:48:12 PM
```

### 生成Verilog代码

要生成Verilog硬件描述代码，运行以下命令：

```
sbt run
```

生成的Verilog文件将保存在 `generated/` 目录中，包括：

- `NonBlockingCache.v`：主Verilog模块
- `NonBlockingCache.fir`：FIRRTL中间表示
- `NonBlockingCache.anno.json`：注释信息

## 模块说明

### 顶层模块 (`NonBlockingCache.scala`)

- 集成所有子模块的顶层容器
- 定义CPU和内存接口协议
- 协调数据流和控制信号传递

### 缓存控制器 (`CacheCtrl.scala`)

- 处理命中/缺失状态机
- 调度MSHR请求和内存访问
- 管理突发传输数据组装
- 协调标签和数据存储更新

### 标签存储 (`CacheTag.scala`)

- 4路并行标签比较
- Tree-PLRU替换算法实现
- 标签有效位管理和更新
- 命中/缺失判断逻辑

### 数据存储 (`CacheData.scala`)

- Bank存储阵列
- 并行数据读取和选择
- 缓存行回填接口

### MSHR模块 (`MSHR.scala`)

- 追踪最多4个未完成缺失请求
- 管理8个Load表项
- 支持请求合并和冲突检测
- 处理乱序数据返回
- 协调缓存更新和数据返回

## 验证框架

### 测试组件

1. **FakeCPU**：

   - 模拟CPU请求生成，具有局部性特征
   - 随机间隔发送请求，模拟真实访存行为
   - 实时验证返回数据的正确性
2. **FakeNonBlockingRAM**：

   - 模拟带有随机延迟的内存子系统
   - 支持突发传输（8周期，64位/周期）
   - 乱序返回数据，验证缓存鲁棒性
3. **CacheSpec**：

   - 集成测试环境的主测试套件
   - 控制仿真流程和周期推进
   - 生成VCD波形文件便于调试

### 测试特性

- **可重复性**：固定随机种子确保测试可重复
- **全面性**：覆盖命中、缺失、请求合并、冲突等场景
- **实时验证**：每个返回数据立即验证正确性
- **详细日志**：格式化输出便于问题定位

## 设计特点

### 性能优化

1. **非阻塞设计**：支持最多4个并发缺失请求，隐藏内存访问延迟
2. **请求合并**：相同地址的请求自动合并，减少内存访问
3. **乱序响应**：支持内存数据的乱序返回，提高系统吞吐量
4. **突发传输**：64位接口通过突发传输填充512位缓存行

### 硬件效率

1. **Tree-PLRU算法**：3位状态实现近似LRU，硬件开销小
2. **Bank化存储**：Bank结构支持并行访问
3. **模块化设计**：清晰的功能划分，易于维护和扩展
4. **参数化配置**：关键参数可配置，适应不同需求

### 验证完备性

1. **自动验证**：每个测试用例自动验证数据正确性
2. **多种场景**：覆盖局部性访问、随机访问、冲突访问
3. **随机延迟**：内存延迟随机变化，验证系统鲁棒性
4. **波形生成**：生成VCD波形便于调试和分析

---

*最后更新：2026年2月20日*
