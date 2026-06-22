# 11. Physical Register Cache

上一章我们聊到了物理寄存器堆——那个所有人都要去存取货物的"总仓库"。但总仓库的窗口有限，排队太久了怎么办？香山的答案是：在总仓库旁边开一家**便利店**——RegCache。

🏪读完本章，你将能够：

* ✅ 理解 RegCache 的设计动机与核心价值
* ✅ 掌握 RegCache 的读写机制与 Tag 匹配流程
* ✅ 认识 RegCache 的替换策略与 Age Timer
* ✅ 理解 IntRegCache 与 MemRegCache 的 Bank Set 划分

***

## 11.1 整体定位：为什么需要 RegCache？

在 Read Before Issue 策略下，每次发射指令都要读物理寄存器堆（RF）。但 RF 的读端口非常昂贵——端口越多，面积和延迟越大。而实际情况是：

***刚写回的数据最有可能被立即消费，而这些数据其实不需要再从 RF 读——它们就在写回通路上。***

但写回通路的数据稍纵即逝，下一拍就没了。如果后续指令没能赶上当拍的前递，就只好老老实实去 RF 排队读取。

RegCache 的作用就是**抓住这些稍纵即逝的数据**——在写回 RF 的同时，把数据也存一份到 RegCache 中。后续指令如果能从 RegCache 命中，就不用竞争 RF 读端口。

| **特性** | **RF（总仓库）** | **RegCache（便利店）** |
| --- | --- | --- |
| 容量 | 全部物理寄存器（~200项） | 最近写回的子集（~16项） |
| 读端口 | 多但竞争激烈 | 少且快速 |
| 命中率 | 100%（总能读到） | 依赖时间局部性 |
| 延迟 | 1 拍（地址先打一拍） | 1 拍，但路径更短 |
| 用途 | 主存储 | 辅助加速，减少 RF 读压力 |

***

## 11.2 RegCache 的整体架构

RegCache 由三个核心模块协作完成：

```plain
┌──────────────────────────────────────────────────────────┐
│                      RegCache 顶层                    	 │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐   │
│  │ RegCacheData │  │ RegCacheData │  │ RegCacheAge   │   │
│  │ (IntRegCache)│  │ (MemRegCache)│  │ Timer         │   │
│  │   存储数据   	│  │   存储数据   	│  │ 决定替换谁     │   │
│  └──────────────┘  └──────────────┘  └───────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────────┐│
│  │ RegCacheTagTable                                     ││
│  │ 维护 Tag→Index 映射，处理读查询与写分配               	││
│  └──────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────┘
```

***

## 11.3 RegCache 的读取流程

当 Issue Queue 发射一条指令时，DataPath 需要判断每个源操作数应该从哪里获取。对于标记为 <code>**regcache**</code> 来源的操作数，读取流程如下：

### 第一步：Tag 查询

RegCacheTagTable 接收**物理寄存器编号**，在 Tag 表中查找是否命中。

* 如果命中：返回 RegCache 内部索引和 Bank 标识
* 如果未命中：该操作数需要退回到 RF 读取

### 第二步：数据读取

根据 Tag 查询返回的索引，从对应的 RegCacheDataModule 中读出数据。

### 第三步：结果选择

RegCache 的读端口同时连接 IntRegCache 和 MemRegCache，通过地址的最高位选择数据来源：

| **地址最高位** | **选择** | **含义** |
| --- | --- | --- |
| 0 | IntRegCache | 整数执行单元写回的数据 |
| 1 | MemRegCache | 访存执行单元写回的数据 |

***

## 11.4 RegCache 的写入流程

### 11.4.1 写入时机

RegCache 的写入与**IQ 唤醒**绑定——当一条指令从 Issue Queue 发射后，其写回信息通过 Wakeup Queue 传播。在写回生效时，数据同时写入 RF 和 RegCache。

### 11.4.2 写入条件

并非所有写回都需要写入 RegCache。写入需要同时满足：

| **条件** | **说明** |
| --- | --- |
| <code>**wakeup.valid**</code> | 写回信号有效 |
| <code>**rfWen**</code> | 目标是整数寄存器堆（RegCache 只缓存整数数据） |
| <code>**!LoadShouldCancel**</code> | Load 没有被取消 |
| <code>**!og0Cancel**</code> | 发射当拍没有被取消 |

### 11.4.3 写入内容

写入 RegCache 时需要两部分信息：

* **数据**：执行单元的计算结果（写入 RegCacheDataModule）
* **标签**：物理寄存器编号 <code>**pdest**</code>（写入 RegCacheTagModule，建立 Tag→Index 映射）

***

## 11.5 RegCache 的替换策略：Age Timer

RegCache 的容量有限（IntRegCache 和 MemRegCache 各约 8 项），写满后必须替换。替换谁？香山使用 **Age Timer** 策略——替换**最老**的那项。

### 11.5.1 Age Timer 的工作原理

Age Timer 为 RegCache 的每一项维护一个**计时器**，记录该项自最后一次写入以来经历了多少个时钟周期。计时器值越大，说明该项越"老"，越久没被使用，越适合替换。

这与 Issue Queue 的 Age Matrix 有异曲同工之妙——只不过 Age Matrix 比的是"谁先入队"，Age Timer 比的是"谁最久没用"。

### 11.5.2 替换索引的计算

AgeDetector 根据 Age Timer 提供的信息，为每个写端口选出最老的 RegCache 项，作为替换目标。替换索引通过 <code>**RegCacheAgeDetector**</code> 计算：

```plain
每个写端口 → AgeDetector → 输出最老项的索引 → 作为新数据的存放位置
```

### 11.5.3 替换索引的延迟对齐

一个关键细节：替换索引在写回时计算，但数据写入 RegCache 需要经过 3 拍延迟（Wakeup Queue 的流水级）。因此替换索引也需要**延迟 3 拍**，确保索引和数据同步到达 RegCache 的写端口。

***

## 11.6 RegCache 的取消机制

RegCache 的 Tag 表可能需要**取消**已写入的映射，发生在以下场景：

| **取消原因** | **触发条件** | **效果** |
| --- | --- | --- |
| **新物理寄存器分配** | Rename 阶段分配了新的 pdest，旧映射失效 | Tag 表中对应项标记为无效 |
| **Tag 被覆盖** | 另一个写回写入了同一个物理寄存器编号 | 旧项被替换，Tag 更新 |
| **Load Cancel** | Load 违约定向，数据无效 | 对应 RegCache 项标记为无效 |

```plain
取消条件 = (新分配覆盖 || Tag被更新 || Load取消) && 该项有效
```

:::warning
💡核心思想\
RegCache 的取消机制与 Issue Queue 的 Cancel 机制一脉相承——都是\*\*"乐观写入 + 悲观撤回"**。数据先写进来（乐观），如果后来发现写错了就取消掉（悲观）。这保证了 RegCache 中只保存**确实有效\*\*的数据。

:::

***

## 11.7 Register Cache Bank Set（RegCache 分体）

### 11.7.1 为什么 RegCache 也要分体？

RegCache 虽然容量比 RF 小得多，但它面临的**写端口压力**并不低——每个能产生整数写回的执行单元都需要一个写端口。如果所有写端口都连在一个单体 RegCache 上，面积和延迟仍然不可忽视。

### 11.7.2 IntRegCache 与 MemRegCache

香山将 RegCache 分为**两个独立的 Bank Set**：

| **Bank Set** | **名称** | **服务对象** | **写回来源** |
| --- | --- | --- | --- |
| **Bank 0** | IntRegCache | 整数执行单元 | ALU、MUL、BJU 等 |
| **Bank 1** | MemRegCache | 访存执行单元 | LDU、STU 等 |

### 11.7.3 Bank 选择机制

两个 Bank Set 的选择通过 RegCache 索引的**最高位**实现：

* 地址最高位 = 0 → IntRegCache
* 地址最高位 = 1 → MemRegCache

```plain
RegCache Index: [BankBit | Bank内部索引]
                 ↑
            这一位决定去哪个Bank
```

### 11.7.4 分体的优势与代价

| **优势** | **代价** |
| --- | --- |
| 每个 Bank 的端口数减半 → 面积减小 | 读端口需要同时连接两个 Bank → Mux 开销 |
| 写端口不冲突——Int 和 Mem 天然分离 | Tag 查询需要查两张表 |
| 替换决策独立——Int 和 Mem 各自维护 Age Timer | 两个 Bank 之间不能共享空间 |

### 11.7.5 Tag 表的 Bank Set

与数据模块对应，Tag 表也分为 **IntRCTagTable** 和 **MemRCTagTable** 两部分。查询时同时查两张表，合并结果：

* 如果 IntRCTagTable 命中 → 返回 Bank 0 的索引
* 如果 MemRCTagTable 命中 → 返回 Bank 1 的索引
* 如果都没命中 → RegCache 未命中，退回 RF 读取

:::warning
💡 新手建议\
RegCache 分 Int/Mem 两个 Bank Set 是一个自然的选择——整数执行单元和访存执行单元本就属于不同的调度域，写回时序和端口需求不同，分开管理顺理成章。这就像便利店把生鲜区和日用品区分开——顾客按需选择，管理也更方便。

:::

***

## 11.8 RegCache 与 DataPath 的协作

RegCache 并不是独立工作的，它嵌入在 DataPath 的数据通路中，与 RF 读取和旁路网络紧密协作：

```plain
Issue Queue 发射 uop
       │
       ▼
  DataPath 接收
       │
       ├──→ DataSource = reg     → RFReadArbiter → RF 读取
       ├──→ DataSource = regcache → RegCacheTagTable → RegCache 读取
       ├──→ DataSource = forward/bypass → BypassNetwork 前递
       └──→ DataSource = imm/zero → 直接提取
```

关键点在于：**每个源操作数只能从一种来源获取数据**。DataSource 的值在 Issue Queue 中就已经确定，DataPath 只是忠实地执行选择。RegCache 的价值在于——当 DataSource 指示 <code>**regcache**</code> 时，操作数不需要竞争 RF 的读端口，从而释放了 RF 的带宽给其他指令。

***

## 11.9 总结

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">✅</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 核心要点总结</font>

* **RegCache 的定位**：RF 旁边的"便利店"，缓存最近写回的整数数据，减少 RF 读端口竞争
* **读写机制**：写入与 IQ 唤醒绑定，同时写数据和 Tag；读取通过 Tag 查询物理寄存器编号，命中则从 RegCache 取数据，未命中则退回 RF
* **替换策略**：Age Timer 计时，替换最久未使用的项；替换索引延迟 3 拍与数据对齐
* **取消机制**：新分配覆盖 / Tag 更新 / Load Cancel 三种场景触发取消，保证 RegCache 只存有效数据
* **Bank Set 划分**：IntRegCache（服务整数执行单元）和 MemRegCache（服务访存执行单元），通过地址最高位选择，独立管理替换和 Tag 表

**核心原则**：RegCache 是\*\*"用空间换带宽"\*\*的典型设计——用少量额外的存储空间，换取 RF 读端口的显著减负。而分 Bank Set 则进一步将写端口压力分摊到两个独立的存储体上，实现更精细的资源管理。


> 更新: 2026-06-01 16:39:54  
