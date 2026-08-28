# 昆明湖 V2 L1 DCache Bank Conflict 实验与波形分析

## 1. 方法与结论摘要

本实验在 XiangShan `kunminghu-v2`（commit `485333f4dcc90156e5ada3d6420abeaad0058d22`）上运行了一个专门制造 L1 DCache 读读 bank conflict 的 Nexus-AM 程序。程序完成 64 轮，每一轮包含三个同时在窗口中飞行的 `ld`，EMU 输出为：

```text
CACHE_CONFLICT_PASS rounds=64
Core 0: HIT GOOD TRAP at pc = 0x800002f0
Core-0 instrCnt = 5,347, cycleCnt = 24,028, IPC = 0.222532
```

本分析使用 wavekit 开源仓库中的 `FstReader` 解析 FST，并用 clock-sampled `Waveform` 数组按 cycle 查询信号。核心 scope 为 `TOP.SimTop.cpu.l_soc.core_with_l2.core`，时钟为 `TOP.clock`，采样边沿为上升沿。Wavekit 对完整 FST 的扫描确认了预期的硬件动作：

| 预期行为（见 [analysis.md](/nfs/home/yanyusong/XiangShanLab/xiangshan-course/docs/5-xiangshan-scenarios-analysis/scenarios-analysis/conflict/cache-conflict/analysis.md:1)） | FST 证据 | 判定 |
|---|---|---|
| 三个请求命中同一 bank、属于不同 set，产生读读冲突 | cycle 13068，`c01=c02=c12=1`；三个 `bankMask=0x01`，地址分别为 `0x80002080/40/00` | 成立 |
| 按 LQ age 选择最老请求 | `winner_port=2`，其 `lq=7`；port 1/0 的 LQ 分别为 8/9 且 `oldest=1` | 成立 |
| loser 不占用 SRAM 读端口，winner 读 bank | `read_enable[0..7]=10000000`，只有 bank 0 被使能 | 成立 |
| 冲突通过一拍延迟的 `bank_conflict_slow` 反馈 | cycle 13068 原始冲突，cycle 13069 `slow=110` | 成立 |
| 纯 bank conflict 是 replay，不是真实 miss | 三个 LDU 的 `miss=0`、`mreq=0`；loser `replay=1` | 成立 |
| 首次失败走 fast replay；再次失败进入 LRQ | cycle 13070 fast replay；cycle 13073 port 0 以 `C_BC` 入 LRQ，cycle 13076/13078 再发出并完成 | 成立 |
| conflict 不错误 wakeup/writeback | winner 在 cycle 13070 `wake=1,wb=1`；loser 带 `c6=1`，直到无冲突重放才 writeback | 成立 |
| 不因普通 bank conflict 触发 memory-violation rollback | conflict 窗口 `redirect=0, mem_vio=0, rollback=000`；扫描区间 `mem_violation=0`、`loadunit_rollback=0` | 成立 |
| 最终按程序顺序提交，数据保持正确 | 三个目标 PC 各提交 64 次；程序 checksum 为 `0x9999999999999980` | 成立 |

因此，这个程序成功覆盖了“命中数据阵列上的 L1 DCache bank conflict”以及昆明湖实现中的完整处理链：**BankedDataArray 仲裁 -> 一拍 slow feedback -> LoadUnit fast replay -> 再冲突时 LoadQueueReplay（LRQ）-> 重放命中 -> writeback/ROB commit**。

这里的 `redirect` 不能简单地按总数归因于 bank conflict。程序循环分支的预测恢复也会产生 redirect；后文用 `debugIsCtrl=1`、`debugIsMemVio=0` 和分支 PC 将两类事件区分开。

## 2. 复现实验环境与产物

### 2.1 构建和运行

先加载香山开发环境：

```bash
source /nfs/home/yanyusong/cache-conflict-env/env.sh
```

测试目录为 `/nfs/home/yanyusong/cache-conflict-env/nexus-am/apps/cache-scenario`。构建命令：

```bash
cd /nfs/home/yanyusong/cache-conflict-env/nexus-am/apps/cache-scenario
make ARCH=riscv64-xs
```

运行命令（在 XiangShan 根目录）：

```bash
cd /nfs/home/yanyusong/cache-conflict-env/XiangShan
./build/emu --no-diff --dump-wave-full \
  -i /nfs/home/yanyusong/cache-conflict-env/nexus-am/apps/cache-scenario/build/cache-scenario-riscv64-xs.bin
```

本次使用的文件：

| 文件 | 路径 | SHA-256 |
|---|---|---|
| ELF | `/nfs/home/yanyusong/cache-conflict-env/nexus-am/apps/cache-scenario/build/cache-scenario-riscv64-xs.elf` | `07efed66940282839f26fc34041f756f8201f04a3867e45c9f711fc3f3353c15` |
| BIN | `/nfs/home/yanyusong/cache-conflict-env/nexus-am/apps/cache-scenario/build/cache-scenario-riscv64-xs.bin` | `8d6f7b48006901a4512e57b151923daad5faf43e456871c32736898b9bcf86c9` |
| FST | `/nfs/home/yanyusong/cache-conflict-env/XiangShan/build/2026-08-27-11-19-12.fst` | `6b12f27b93f06e811642896a4e8349184db20bd0c2c4af6ca5f028e6995f2e0b` |

FST 大小约 184 MB，仿真时间范围为 `0..48167`。Wavekit 使用 `/nfs/home/yanyusong/wavekit/.venv/bin/python` 和 `FstReader`，以 `TOP.clock` 上升沿采样；本报告的 cycle 是该时钟的采样序号，FST 中的 time 例如 `cycle=13068 -> time=26136`，本波形时间单位对应两个 cycle 采样单位。

Wavekit 的核心读取方式如下（实际脚本按同一方法读取了 BDA、三个 LDU、三个 LoadUnit、LRQ、ROB commit、redirect 和 CSR 信号）：

```python
from wavekit import FstReader

with FstReader("/nfs/home/yanyusong/cache-conflict-env/XiangShan/build/2026-08-27-11-19-12.fst") as reader:
    wf = reader.load_waveform(
        "TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.io_bank_conflict_slow_0",
        clock="TOP.clock",
        sample_on_posedge=True,
        begin_cycle=13055,
        end_cycle=16620,
    )
```

### 2.2 测试程序

测试入口见 [main.c](/nfs/home/yanyusong/cache-conflict-env/nexus-am/apps/cache-scenario/main.c:1)，汇编序列见 [cache_conflict.S](/nfs/home/yanyusong/cache-conflict-env/nexus-am/apps/cache-scenario/cache_conflict.S:1)。数据对象按 4 KiB 对齐，并在三个相隔一个 cache line 的位置放置三个不同的数值：

```c
__attribute__((aligned(4096), section(".data.cache_conflict")))
static volatile uint64_t conflict_data[24] = {
  [0] = 0x1111111111111111ULL,
  [8] = 0x2222222222222222ULL,
  [16] = 0x3333333333333333ULL,
};
```

每轮的三个 load 没有插入指令级依赖，因而可以同时进入三个 load pipe；warmup 阶段和每轮结束的 `fence rw, rw` 用来保证三条目标 load 已经填入 L1，并隔离相邻轮次，避免把前一轮的 replay 与下一轮的原始请求混在一起：

```asm
cache_conflict_trigger:
cache_conflict_load0:
  ld t0, 0(a0)
cache_conflict_load1:
  ld t1, 64(a0)
cache_conflict_load2:
  ld t2, 128(a0)
  add t0, t0, t1
  add a0, t0, t2
  ret
```

软件还检查地址布局、warmup 返回值以及 64 轮 checksum。`0x6666666666666666 * 64` 在 64 位模算术下为 `0x9999999999999980`，所以 PASS 同时是对 replay 后数据正确性的架构级检查。

## 3. 为什么这三个地址必然制造 bank conflict

昆明湖当前配置关闭 DWPU，因此 DCache 使用 `BankedDataArray`。相关配置见 [Parameters.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:260) 和 [DCacheWrapper.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1017)：

```scala
dwpuParameters: WPUParameters = WPUParameters(
  enWPU = false,
  algoName = "mmru",
  enCfPred = false,
  isICache = false,
),
```

```scala
val bankedDataArray = if(dwpuParam.enWPU) Module(new SramedDataArray) else Module(new BankedDataArray)
```

DCache 的物理划分在 [DCacheWrapper.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:126) 和 [DCacheWrapper.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:213) 定义：64-bit SRAM row、8 个 bank、64 B cache line；因此本实验使用的字段为：

| 地址 | `[5:3]` bank | `[13:6]` set | `bankMask`（波形） |
|---|---:|---:|---:|
| `0x80002000` | 0 | 128 | `0x01` |
| `0x80002040` | 0 | 129 | `0x01` |
| `0x80002080` | 0 | 130 | `0x01` |

`DCacheSetDiv=1`，所以三个请求的 div 相同；set 不同、bank mask 相交，正好满足读读冲突谓词。反汇编确认目标 PC 和指令编码：

| PC | 指令编码 | 指令 |
|---|---|---|
| `0x800002c0` | `0x00053283` | `ld t0,0(a0)` |
| `0x800002c4` | `0x04053303` | `ld t1,64(a0)` |
| `0x800002c8` | `0x08053383` | `ld t2,128(a0)` |

注意 BDA 的端口号不是这三个 PC 的自然顺序：在代表性冲突时，PC `0x800002c0` 位于 BDA port 2，PC `0x800002c4` 位于 port 1，PC `0x800002c8` 位于 port 0。报告中同时列出 PC、ROB/LQ 和地址，避免只凭端口号追踪指令。

## 4. 代表性完整时间线

以下均为 `TOP.clock` 上升沿采样；`time` 是 FST 时间戳。`p0/p1/p2` 是 BDA 读端口，`oldest=1` 表示该端口是冲突 loser（源码变量名是 `rr_bank_conflict_oldest`，但它实际上是“非选中且有冲突”的端口）。

| cycle (time) | 波形事件 | 关键值与含义 |
|---:|---|---|
| 13061 (26122) | Decode 输入/输出三路 handshake | `DIn/DOut` 的 PC 为 c0/c4/c8，均 `valid=ready=fire=1` |
| 13062 (26124) | Rename handshake | 三路均 fire，ROB index 分配为 100/101/102 |
| 13063 (26126) | Dispatch | PC c0/c4/c8 对应 LQ 7/8/9 |
| 13064 (26128) | ROB enqueue | 三个目标 uop 均 valid |
| 13068 (26136) | BDA 原始读读冲突 | `pair=111`，`winner_port=2`；p2(c0,LQ7) 胜出，p1(c4,LQ8)、p0(c8,LQ9) loser；三个 `ready=1` |
| 13068 (26136) | SRAM 选择 | `read_enable[0..7]=10000000`，仅 bank 0 读使能，说明 loser 没有驱动物理读端口 |
| 13069 (26138) | slow feedback / LoadUnit S2 | `slow=110`；LU0(c8) 和 LU1(c4) `bc=1,c6=1`，`miss=0,mreq=0,replay=1`；LU2(c0) `bc=0` |
| 13070 (26140) | 第一次 fast replay 与 winner 完成 | FO/FI 出现 c8/c4；winner c0 `S3 wake=1,wb=1` |
| 13071 (26142) | replay 后仍有一对冲突 | `pair=100`，p1(c4,LQ8) 选为 winner，p0(c8,LQ9) 继续 loser |
| 13072 (26144) | 第二次冲突的 slow feedback | `slow=100`；c8 仍 `fast=1,bc=1,c6=1`，c4 已 `fast=1,bc=0` |
| 13073 (26146) | LRQ 入队和 c4 完成 | `QE0(c8,ROB102,LQ9,C_BC=1)`；c4 `S3 wake=1,wb=1` |
| 13076 (26152) | LRQ 调度输出 | `QD1(c8,ROB102,LQ9,ready=1)` |
| 13078 (26156) | LRQ replay 回到 LoadUnit | LU1 为 c8，`loadReplay=1,bc=0,miss=0,mreq=0` |
| 13079 (26158) | c8 重放完成 | c8 `S3 wake=1,wb=1` |
| 13081 (26162) | 第一轮三条目标 load 提交 | ROB commit c8；c0/c4 已分别在 13072/13075 提交 |

这个时间线直接体现了“winner 使用本次 SRAM 读结果，loser 丢弃本次读结果并重新执行”的语义。尤其是 cycle 13069 的 `miss=0` 和 `mreq=0`，排除了“把 bank conflict 当成 cache miss 发送到 MSHR”的误读。

## 5. 从前端到提交的逐级波形分析

### 5.1 Decode、Rename、Dispatch、ROB

Wavekit 在代表性窗口捕获到连续的 valid/ready/fire：

```text
cycle=13061 time=26122
  DIn0/DOut0 pc=0x800002c0 v=1 r=1 fire=1
  DIn1/DOut1 pc=0x800002c4 v=1 r=1 fire=1
  DIn2/DOut2 pc=0x800002c8 v=1 r=1 fire=1
cycle=13062 time=26124
  RIn0/ROut0 pc=0x800002c0 v=1 r=1 fire=1 rob=100
  RIn1/ROut1 pc=0x800002c4 v=1 r=1 fire=1 rob=101
  RIn2/ROut2 pc=0x800002c8 v=1 r=1 fire=1 rob=102
cycle=13063 time=26126
  Disp0 pc=0x800002c0 rob=100 lq=7
  Disp1 pc=0x800002c4 rob=101 lq=8
  Disp2 pc=0x800002c8 rob=102 lq=9
```

因此冲突不是由前端停止或译码丢指令造成的；三个独立 load 确实同时进入后端。由于本次 FST 没有暴露一个可直接命名为“Issue FSM state”的稳定层次信号，issue 阶段用这些 handshake、ROB/LQ identity 和 LoadUnit `s2/s3_valid` 来定位；没有凭空构造不存在的 FSM 状态名。

前端 FTQ/IBuffer 的若干候选信号也用 Wavekit 的层次匹配和 target-PC 搜索过。最终 FST 没有同时暴露一个能把 `0x800002c0/4/8` 与 FTQ entry、预测 target 和 fetch-to-IBuffer `fire` 完整绑定的稳定信号名；因此本报告把 cycle 13061 的 decode 输入作为可证明的最早 backend anchor，并明确不把未捕获的 FTQ 数值当成事实。对本场景而言，decode/rename/dispatch 的连续 fire 已足以证明三个 load 没有在前端被吞掉。

在 issue/execute 边界，波形可见的目标身份是 LoadUnit 的 `s2_valid`、PC、ROB/LQ，以及之后的 `s3_out_valid`；没有发现一个独立的、带目标 PC 的 scheduler `issue_valid/ready` 接口可可靠采样。于是“已发射”只在 `s2_valid` 与对应 DCache response 同时出现时判定，不用静态 PC 邻近关系代替 handshake。

### 5.2 BDA 冲突检测、仲裁和 SRAM 读

冲突周期的原始信号为：

```text
cycle=13068 time=26136 pair=111 winner_port=2 sram_r=10000000 readline=0 write=0
  p0: addr=0x80002080 bankMask=0x01 lq=9 ready=1 lose=1
  p1: addr=0x80002040 bankMask=0x01 lq=8 ready=1 lose=1
  p2: addr=0x80002000 bankMask=0x01 lq=7 ready=1 lose=0
```

这里 `pair=111` 是 `(c01,c02,c12)`，不是某个单独的“冲突等级”。所有 read `ready=1` 很重要：读读冲突采用数据阵列内部仲裁，不通过 Decoupled ready 把请求退回。`winner_port=2` 与最小 LQ index 7 一致。

### 5.3 一拍延迟的 slow feedback

同一周期没有 `bank_conflict_slow`，下一采样点才出现：

```text
cycle=13068: slow=000
cycle=13069: slow=110
```

`slow=110` 按 port 0/1/2 展开，表示 p0 和 p1 收到 slow conflict，p2 没有。它与 `rr_bank_conflict_oldest` 的 loser 位严格对应，而不是与所有 pairwise probe 简单相等。

### 5.4 LoadPipe / LoadUnit：hit replay 而非 miss

cycle 13069 的三路状态：

```text
LU0 pc=0x800002c8 rob=102 lq=9  fast=0 lr=0 bc=1 dm=0 ff=0 c6=1
    DCache: replay=1 miss=0 miss_req=0
LU1 pc=0x800002c4 rob=101 lq=8  fast=0 lr=0 bc=1 dm=0 ff=0 c6=1
    DCache: replay=1 miss=0 miss_req=0
LU2 pc=0x800002c0 rob=100 lq=7  fast=0 lr=0 bc=0 dm=0 ff=0 c6=0
    DCache: replay=0 miss=0 miss_req=0
```

`dm=0`/`miss=0` 说明 tag/data 命中；`replay=1` 单独来自 bank conflict。`mreq=0` 说明没有向 miss queue/MSHR 发请求。loser 的 `c6=1` 是 `LoadReplayCauses.C_BC`，不是 `C_DM`。

winner c0 在 cycle 13070 进入 S3，`wake=1,wb=1`；两个 loser 的 `need_rep` 仍为真，所以不会把未被 SRAM 正确读取的数据当成可用值写回。cycle 13072 c4 已经无冲突并完成，c8 仍冲突；这正是动态仲裁随当前在飞行请求集合变化的表现。

### 5.5 Fast replay、LRQ 和再次执行

cycle 13070 可见两个 `fast_rep_out`/`fast_rep_in`，对应 PC c8/c4。c4 的第一次重放在 cycle 13072 成功，cycle 13073 writeback；c8 在 cycle 13072 再次遇到 c4，因此不能继续无限 fast replay，cycle 13073 以 `C_BC` 进入 LRQ。LRQ 随后在 cycle 13076 发出同一条 `(ROB102,LQ9,PC c8)`，cycle 13078 作为 `isLoadReplay=1` 回到 LoadUnit，cycle 13079 完成。

这是一个很有辨识度的身份链：

```text
原始 c8:       ROB=102, LQ=9, PC=0x800002c8
fast replay:   ROB=102, LQ=9, PC=0x800002c8
LRQ enqueue:   ROB=102, LQ=9, C_BC=1
LRQ dequeue:   ROB=102, LQ=9
LRQ replay S2:  ROB=102, LQ=9, isLoadReplay=1
最终提交:      PC=0x800002c8
```

LRQ 的调度不是“等待 cache refill”：本实验的 C_BC entry 下一周期即可解除 blocking，实际仍会受 replay port 的 age/priority 仲裁影响，因此从 enqueue 到 dequeue 有若干周期。

### 5.6 Writeback 和 ROB commit

三条目标 load 的首轮提交锚点如下：

```text
cycle=13072 time=26144 slot=0 pc=0x800002c0 instr=0x00053283 rob=100 lq=7 isLoad=1 isStore=0 rfwen=1 wdest=5
cycle=13075 time=26150 slot=0 pc=0x800002c4 instr=0x04053303 rob=101 lq=8 isLoad=1 isStore=0 rfwen=1 wdest=6
cycle=13081 time=26162 slot=0 pc=0x800002c8 instr=0x08053383 rob=102 lq=9 isLoad=1 isStore=0 rfwen=1 wdest=7
```

在 `13055..16620` 扫描区间内，三个 PC 各出现 64 次 commit；所有对应 load event 共 192 次，物理地址严格重复 `0x80002000, 0x80002040, 0x80002080`，`opType=3`、`isLoad=1`、`isAtomic=0`。这表明 replay 重新执行同一条 load，而不是生成重复的架构提交或错误地址访问。

## 6. 预期行为与波形证据逐项对应

### 6.1 冲突条件与最老者选择

[BankedDataArray.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala:725) 的谓词要求两个有效请求具有相同 div、相交 bank mask 且 set 不同；波形中的三组地址恰好满足。`selcetOldestPort` 使用 LQ pointer 做 oldest 选择，波形中 LQ7 的 c0 胜出，LQ8/LQ9 被屏蔽。第二次只剩 c4/c8 冲突时，LQ8 的 c4 胜出，说明仲裁每次按当前请求集合重新计算，而不是固定某一个物理端口优先。

### 6.2 loser 的物理路径

cycle 13068 的 `read_enable=10000000` 只允许 bank 0 的一次 SRAM 读。loser 的 `io_read.valid` 仍为 1，但 `ready` 也为 1；它们在内部 `bank_addr_matchs` 中被 `!rr_bank_conflict_oldest(i)` 排除。因此没有错误地从同一个 SRAM row 读取并 writeback。

### 6.3 replay 不是 miss

波形的 `DCache resp.replay=1, miss=0` 与 `miss_req.valid=0` 同时出现。这个组合是本实验最重要的判别条件：它说明 bank conflict 只要求重新发起 load，不需要 refill、MSHR 或 MissQueue。测试先 warmup 正是为了让此条件稳定为“纯 hit conflict”。

### 6.4 fast replay 到 LRQ 的升级

第一次 c4/c8 loser 走 fast replay；由于 c8 在下一次尝试仍和 c4 冲突，`isFastReplay=1` 使它不能无条件再走同一条 fast path，于是生成 `C_BC` 并进入 LRQ。LRQ dequeue 后同一 ROB/LQ 身份再次进入 LoadUnit，`bc=0`、`miss=0`，最终正常 wakeup/writeback。这正是分析文档所描述的“快速重试，重复失败后转慢速 replay”的完整动作。

### 6.5 不发生错误唤醒、回滚或架构异常

冲突窗口的 `s3_safe_wakeup/s3_safe_writeback` 只对 winner 或无 replay 的请求有效；loser 的 `c6=1`/`need_rep=1` 抑制了错误唤醒和写回。Wavekit 在 cycle 13067--13085 观察到：

```text
redirect=0 mem_vio=0 rollback=000
```

全 FST 扫描到 `mem_violation=0`、`loadunit_rollback=0`。CSR 快照在目标提交点保持：`priv=0x3`、`satp=0`、`mcause=0`、`mepc=0`、`mtval=0`，没有由该场景引起的异常。

## 7. Redirect 分析：区分控制流恢复和 cache conflict

不能因为全局存在 redirect 就认为 bank conflict 导致了 frontend flush。代表性冲突窗口没有任何 redirect；但整个 64 轮程序中确实有循环分支预测恢复。例如：

```text
cycle=13169 time=26338 front=0 back=1 ctrl=1 memdbg=0 mispred=1
  pc=0x800001d2 target=0x800001c0
cycle=13174 time=26348 front=1 back=0 ctrl=0 memdbg=0 mispred=0
  target=0x800001d2 level=1
```

`0x800001d2` 是反汇编中的 `bnez s0,0x800001c0`；`debugIsCtrl=1` 且 `debugIsMemVio=0`，所以这是控制流预测恢复。首次窗口外还有 `pc=0x80000192` 的地址检查分支恢复，性质相同。扫描区间统计为 `backend_redirect=66`、`frontend_redirect=66`，但 `mem_violation=0`；这些 redirect 应归因于程序控制流，而不是 bank conflict。

源码也明确区分两条 redirect 来源：[CtrlBlock.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:192) 给执行单元 redirect 设置 `debugIsCtrl=true, debugIsMemVio=false`，而 memory violation 路径在 [CtrlBlock.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:213) 设置 `debugIsCtrl=false, debugIsMemVio=true`。LoadUnit 的 rollback 条件只包含 fetch replay、load-load violation 和 misalign-buffer 条件，并不包含 `bank_conflict`，见 [LoadUnit.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1672)。

## 8. 性能和 bubble 观察

| 项目 | 波形结果 | 解释 |
|---|---:|---|
| 原始三路同时到达 BDA | 13068 | 读读冲突内部仲裁，read ready 没有下降 |
| winner c0 从冲突到 S3 writeback | 13068 -> 13070 | 主要是 BDA slow feedback 的流水线延迟 |
| c4 从第一次冲突到 writeback | 13069 -> 13073 | 一次 fast replay 后成功 |
| c8 从第一次冲突到最终 writeback | 13069 -> 13079 | 第二次冲突、LRQ 入队/调度/重放 |
| DCache miss request | 0（目标窗口及扫描区间） | 没有 refill/MSHR 参与 |
| 每轮控制流恢复 | 约每 50 个 cycle 一次 | 循环分支预测行为，非 bank conflict 本身 |

针对 Wavekit 技能要求的 `valid/ready/fire` 检查，代表性窗口的边界结果如下：

| 边界 | `valid` | `ready` | `fire=valid&ready` | 是否出现目标相关 `valid&&!ready` |
|---|---:|---:|---:|---|
| Decode、Rename、Dispatch（13061--13064） | 1 | 1 | 1 | 否 |
| BDA 三个 read port（13068） | 1 | 1 | 1 | 否；读读 conflict 在内部仲裁 |
| DCache response（13069、13072、13078） | 1 | 1 | 1 | 否 |
| LoadUnit fast replay out/in（13070） | 1 | 1 | 1 | 否 |
| LRQ enqueue（13073） | 1 | 1 | 1 | 否；源码固定 `enq.ready=true` |
| LRQ replay dequeue（13076） | 1 | 1 | 1 | 否 |

因此冲突造成的是“重新执行的延迟”，不是把原请求卡在 ready/valid 边界上。Wavekit 在 `13067..13085` 对 BDA read、DCache response、fast replay 和 LRQ 目标 transfer 查询到的 `valid&&!ready` 为 0；`ready&&!valid` 的空闲采样点属于相邻轮次之间的正常空窗，不能归因给 bank conflict。目标 c0/c4/c8 的可量化额外延迟分别约为 2、4 和 10 个 cycle（从首次冲突反馈到对应 S3 writeback）。

`fence` 让每轮的触发窗口彼此分离，代价是程序总 cycle 数增加；它的作用是提高波形归因的确定性，不是 DCache bank conflict 的必要条件。若去掉 fence，仍可能看到冲突，但前一轮 loser、下一轮原始请求和循环重定向会重叠，教学波形会难以按单轮解释。

## 9. 架构状态与数据正确性

ROB 的 difftest commit 信号仍由硬件产生；`--no-diff` 只关闭与参考模型的比较，不会关闭这些 commit/debug 信号。提交记录显示：

| PC | 指令 | load/store | 目标寄存器 | 提交次数 |
|---|---|---|---|---:|
| `0x800002c0` | `ld t0,0(a0)` | load=1/store=0 | x5 | 64 |
| `0x800002c4` | `ld t1,64(a0)` | load=1/store=0 | x6 | 64 |
| `0x800002c8` | `ld t2,128(a0)` | load=1/store=0 | x7 | 64 |

对应 LoadEvent 的 PAddr、操作类型和 atomic 标志也保持一致。Difftest commit bundle 本身不提供可直接用于本报告的整数写回数据字段；因此数据值正确性由程序的 warmup/checksum 检查和 PASS 输出确认，而不是臆造一个不存在的波形 wdata 信号。

## 10. 关键 Chisel 源码与波形信号对应

下面摘录的是产生本次波形行为的关键实现。每个代码块前都给出昆明湖源文件路径，便于与波形信号交叉阅读。

### 10.1 读读冲突矩阵、oldest 仲裁和 slow 延迟

源码：[BankedDataArray.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala:725)

```scala
val rr_bank_conflict = Seq.tabulate(LoadPipelineWidth)(x => Seq.tabulate(LoadPipelineWidth)(y => {
  if (x == y) {
    false.B
  } else {
    io.read(x).valid && io.read(y).valid &&
    div_addrs(x) === div_addrs(y) &&
    (io.read(x).bits.bankMask & io.read(y).bits.bankMask) =/= 0.U &&
    set_addrs(x) =/= set_addrs(y)
  }
}))

val load_req_with_bank_conflict = rr_bank_conflict.map(_.reduce(_ || _))
val load_req_lqIdx = io.read.map(_.bits.lqIdx)
val load_req_index = (0 until LoadPipelineWidth).map(_.asUInt)
val load_req_bank_conflict_selcet =
  selcetOldestPort(load_req_with_bank_conflict, load_req_lqIdx, load_req_index)
val load_req_bank_select_port =
  UIntToOH(load_req_bank_conflict_selcet._2).asBools
val rr_bank_conflict_oldest = (0 until LoadPipelineWidth).map(i =>
  !load_req_bank_select_port(i) && load_req_with_bank_conflict(i)
)
```

源码：[BankedDataArray.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala:758)

```scala
io.readline.ready := !(wrl_bank_conflict)
io.read.zipWithIndex.map { case (x, i) =>
  x.ready := !(wr_bank_conflict(i) || rrhazard)
}

val real_other_bank_conflict_reg = RegNext(wr_bank_conflict(i) || rrl_bank_conflict(i))
val real_rr_bank_conflict_reg = RegNext(rr_bank_conflict_oldest(i))
io.bank_conflict_slow(i) :=
  real_other_bank_conflict_reg || real_rr_bank_conflict_reg
```

波形中的 `ready=1` 和“原始冲突后下一拍 slow”分别对应这两段逻辑。

### 10.2 loser 屏蔽与 SRAM bank enable

源码：[BankedDataArray.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala:826)

```scala
val bank_addr_matchs = WireInit(VecInit(List.tabulate(LoadPipelineWidth)(i => {
  io.read(i).valid && div_addrs(i) === div_index.U &&
    (bank_addrs(i)(0) === bank_index.U ||
      bank_addrs(i)(1) === bank_index.U && io.is128Req(i)) &&
    !rr_bank_conflict_oldest(i)
})))

val bank_set_addr = Mux(
  readline_match,
  line_set_addr,
  PriorityMux(Seq.tabulate(LoadPipelineWidth)(i =>
    bank_addr_matchs(i) -> set_addrs(i)))
)
val read_enable = bank_addr_matchs.asUInt.orR || readline_match
val data_bank = data_banks(div_index)(bank_index)
data_bank.io.r.en := read_enable
```

当本实验的 bank 0 被访问时，winner c0 使 `bank_addr_matchs` 为真；c4/c8 因 `rr_bank_conflict_oldest=1` 被排除，产生 `10000000` 的 read-enable 向量。

### 10.3 LoadPipe 将 conflict 标记为 replay

源码：[DCacheWrapper.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1332)

```scala
(0 until LoadPipelineWidth).map(i => {
  bankedDataArray.io.read(i) <> ldu(i).io.banked_data_read
  ldu(i).io.banked_data_resp := bankedDataArray.io.read_resp(i)
  ldu(i).io.bank_conflict_slow := bankedDataArray.io.bank_conflict_slow(i)
})
```

源码：[LoadPipe.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala:452)

```scala
val real_miss = !s2_real_way_en.orR
resp.bits.real_miss := real_miss
resp.bits.miss := real_miss
resp.bits.data := s2_resp_data
resp.bits.replay :=
  (resp.bits.miss && (s2_nack || io.miss_req.bits.cancel)) ||
  io.bank_conflict_slow || s2_wpu_pred_fail || s2_btot_occupy_fail
```

源码：[LoadPipe.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala:433)

```scala
io.miss_req.valid := s2_miss_req_valid
io.miss_req.bits.addr := get_block_addr(s2_paddr)
```

由于本实验已经 warmup，`real_miss=0`；因此波形同时看到 `resp.replay=1`、`resp.miss=0` 和 `io_miss_req_valid=0`。

### 10.4 LoadUnit 的过滤、C_BC 和 fast replay

源码：[LoadUnit.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1260)

```scala
val s2_dcache_miss = io.dcache.resp.bits.miss &&
  !s2_fwd_frm_d_chan_or_mshr && !s2_full_fwd && !s2_in.nc
val s2_bank_conflict = io.dcache.s2_bank_conflict &&
  !s2_fwd_frm_d_chan_or_mshr && !s2_full_fwd && !s2_in.nc

val s2_dcache_fast_rep =
  s2_mq_nack || !s2_dcache_miss && (s2_bank_conflict || s2_wpu_pred_fail)
val s2_fast_rep = !s2_in.isFastReplay &&
  !s2_mem_amb && !s2_tlb_miss && !s2_fwd_fail &&
  (s2_dcache_fast_rep || s2_nuke_fast_rep) && s2_troublem
```

源码：[LoadUnit.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1429)

```scala
s2_out.rep_info.dcache_rep    := s2_mq_nack && s2_troublem
s2_out.rep_info.dcache_miss   := s2_dcache_miss && s2_troublem
s2_out.rep_info.bank_conflict := s2_bank_conflict && s2_troublem
```

源码：[LoadUnit.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1813)

```scala
io.fast_rep_out.valid := s3_valid && s3_fast_rep
io.fast_rep_out.bits := s3_in
```

源码：[MemBlock.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:855)

```scala
loadUnits(i).io.fast_rep_in <> loadUnits(i).io.fast_rep_out
loadUnits(i).io.dcache <> dcache.io.lsu.load(i)
```

因此 cycle 13070 的 FO/FI 是硬件 fast replay 回环，而不是软件重新调用函数。

### 10.5 再冲突时进入 LRQ，C_BC 下一周期可调度

源码：[LoadQueueReplay.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:37)

```scala
val C_DM = 4
val C_WF = 5
val C_BC = 6
val C_RAR = 7
val C_RAW = 8
```

源码：[LoadQueueReplay.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:607)

```scala
// LoadQueueReplay can't backpressure.
assert(freeList.io.canAllocate.reduce(_ || _) ||
  !io.enq.map(_.valid).reduce(_ || _), s"LoadQueueReplay Overflow")
enq.ready := true.B

when (needEnqueue(w) && enq.ready) {
  allocated(enqIndex) := true.B
  scheduled(enqIndex) := false.B
  cause(enqIndex) := enq.bits.rep_info.cause.asUInt
  blocking(enqIndex) := true.B
  when (enq.bits.rep_info.cause(LoadReplayCauses.C_BC) ||
        enq.bits.rep_info.cause(LoadReplayCauses.C_NK) ||
        enq.bits.rep_info.cause(LoadReplayCauses.C_DR) ||
        enq.bits.rep_info.cause(LoadReplayCauses.C_WF)) {
    blocking(enqIndex) := false.B
  }
}
```

源码：[LoadQueueReplay.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:424)

```scala
val s0_loadLowerPriorityReplaySelMask = VecInit((0 until LoadQueueReplaySize).map(i => {
  val hasLowerPriority = !cause(i)(LoadReplayCauses.C_DM) &&
    !cause(i)(LoadReplayCauses.C_FF)
  allocated(i) && !scheduled(i) && !blocking(i) && hasLowerPriority
})).asUInt
```

`C_BC` 不需要等待 refill；它属于 lower-priority replay，实际发出时间还要经过 oldest/age 和 replay port 仲裁。这解释了 cycle 13073 入队到 13076 dequeue 的间隔。

### 10.6 replay loser 不触发普通 rollback

源码：[LoadUnit.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1356)

```scala
val s2_safe_wakeup = !s2_out.rep_info.need_rep &&
  !s2_mmio && (!s2_in.nc || s2_nc_with_data) &&
  !s2_mis_align && !s2_real_exception
val s2_safe_writeback = s2_real_exception ||
  s2_safe_wakeup || s2_vp_match_fail
```

源码：[LoadUnit.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1634)

```scala
s3_out.valid := s3_valid && s3_safe_writeback && !toMisalignBufferValid
```

源码：[LoadUnit.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1672)

```scala
io.rollback.valid := s3_valid &&
  (s3_rep_frm_fetch || s3_flushPipe || s3_frm_mis_flush) &&
  !s3_exception
```

这个 rollback 表达式没有 `bank_conflict`。而 `s2_safe_wakeup`/`s2_safe_writeback` 要求 `!s2_out.rep_info.need_rep`；因此 conflict loser 只能 replay，不能把错误的 data 送入唤醒和写回。

### 10.7 ROB commit 是最终架构锚点

源码：[Rob.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1533)

```scala
difftest.valid := io.commits.commitValid(i) && io.commits.isCommit
difftest.rfwen := io.commits.commitValid(i) &&
  commitInfo.rfWen && basicDebug.ldest =/= 0.U
difftest.pc := Mux(pcTransType.shouldBeSext,
  SignExt(uop.pc, XLEN), uop.pc)
difftest.instr := uop.instr
difftest.robIdx := ZeroExt(ptr, 10)
difftest.lqIdx := ZeroExt(uop.lqIdx.value, 7)
difftest.isLoad := io.commits.info(i).commitType === CommitType.LOAD
```

这段逻辑提供了本报告使用的 PC、指令、ROB/LQ、load/store 和写寄存器字段。它也说明 `--no-diff` 不会让 commit 信号消失。

## 11. 信号生产者、消费者和状态摘要

| 行为 | 生产者 | 观察到的信号 | 消费者/后续动作 |
|---|---|---|---|
| pairwise bank conflict | `bankedDataArray` | `rr_bank_conflict_0_1/0_2/1_2_probe` | oldest selector |
| winner/loser | `bankedDataArray` | `load_req_bank_conflict_selcet_idx`、`rr_bank_conflict_oldest_*` | `bank_addr_matchs`、SRAM read enable |
| slow feedback | `bankedDataArray` | `io_bank_conflict_slow_*` | `ldu.io.bank_conflict_slow` -> `LoadPipe` |
| hit replay | `LoadPipe` | `resp_bits_replay=1, resp_bits_miss=0` | `LoadUnit.s2_bank_conflict` |
| fast replay | `LoadUnit` | `io_fast_rep_out/in_*`、`s2_in_r_isFastReplay` | 直接回到 LoadUnit |
| LRQ fallback | `LoadUnit`/`LoadQueueReplay` | `io_enq_*_bits_rep_info_cause_6`、`io_replay_*` | `isLoadReplay=1` 再执行 |
| 正确完成 | `LoadUnit`/ROB | `s3_safe_wakeup/writeback`、`difftest_commit_*` | PRF、ROB commit |
| 异常/控制流区分 | `CtrlBlock` | `debugIsCtrl`、`debugIsMemVio`、`io_mem_memoryViolation_valid` | frontend redirect 或 memory replay |

源码里没有为这条路径提供一个单一的、可在 FST 中直接观测的“BankConflict FSM enum”。实际状态由流水寄存器 valid、`isFastReplay`、`rep_info.cause`、LRQ 的 `allocated/scheduled/blocking` 组合表示。本实验报告因此使用可见的组合状态，而没有把 `s2/s3` 或 LRQ slot 误称为不存在的显式 FSM 状态。

### 11.1 涉及模块的状态寄存器审计

| 模块 | 波形中可用的状态字段 | 代表性值/周期 | 对本指令的作用 | 源码依据 |
|---|---|---|---|---|
| BankedDataArray | `io_read_*_valid`、`rr_bank_conflict_oldest_*`、`io_bank_conflict_slow_*` | 13068 loser=`110`；13069 slow=`110` | 选择 winner、屏蔽 loser、延迟反馈 | [BankedDataArray.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala:746) |
| LoadPipe | `s2_valid`、`io_lsu_s2_bank_conflict`、`resp_bits_replay/miss` | 13069 loser `1/1/0` | 将命中冲突编码为 replay，不发 miss | [LoadPipe.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala:464) |
| LoadUnit | `s2_in_r_isFastReplay`、`s2_bank_conflict`、`s2_out_rep_info_cause_6`、`s3_valid` | 13069 `fast=0,c6=1`；13072 c8 `fast=1,c6=1`；13078 `loadReplay=1` | 首次 fast replay，重复冲突转 LRQ，成功后 S3 输出 | [LoadUnit.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1327) |
| LoadQueueReplay | `allocated[*]`、`blocking[*]`、`scheduled[*]`、`io_enq/io_replay` | 13073 allocated/C_BC；13076 dequeue | 保存 replay uop，按 age/priority 调度 | [LoadQueueReplay.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:635) |
| CtrlBlock/Frontend | redirect valid、`debugIsCtrl`、`debugIsMemVio` | conflict 窗口全 0；13169 ctrl=1/memvio=0 | 区分控制流恢复与 memory violation | [CtrlBlock.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:192) |

Waveform hierarchy 中没有能可靠映射到本目标 transaction 的 `state/stateReg/FSM` 枚举信号；因此状态列使用实际 dump 的 valid/cause/allocated 寄存器，并在源码中追溯其更新条件。这个“缺少显式 FSM dump”的事实本身也记录下来，避免将共享模块的 idle/active 编码误归因给某一条 load。

## 12. 源码链接与引用代码段

本报告中的源码摘录集中在第 10 节，均直接从本次运行所用的 XiangShan commit 复制，并在代码块前给出绝对路径链接。便于逐项复核的文件入口如下：

- [Parameters.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:167)
- [DCacheWrapper.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:126)
- [BankedDataArray.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala:725)
- [LoadPipe.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala:433)
- [LoadUnit.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1260)
- [LoadQueueReplay.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:37)
- [MemBlock.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:855)
- [CtrlBlock.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:192)
- [Rob.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1533)

这些摘录覆盖了冲突谓词、oldest 选择、SRAM 端口屏蔽、slow 延迟、DCache response、fast replay、LRQ 入队/调度、writeback 抑制、rollback 排除和 commit 记录；没有用仅凭波形命名的信号替代源码来源。

## 13. 代码依据

实验程序的可复现代码为 [main.c](/nfs/home/yanyusong/cache-conflict-env/nexus-am/apps/cache-scenario/main.c:1)、[cache_conflict.S](/nfs/home/yanyusong/cache-conflict-env/nexus-am/apps/cache-scenario/cache_conflict.S:1) 和 [Makefile](/nfs/home/yanyusong/cache-conflict-env/nexus-am/apps/cache-scenario/Makefile:1)。硬件语义的主要依据是：

1. [DCacheWrapper.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:126) 的 bank/set 地址切分和 [DCacheWrapper.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1017) 的 BankedDataArray 选择。
2. [BankedDataArray.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala:725) 的读读冲突与 [BankedDataArray.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala:826) 的物理读屏蔽。
3. [LoadPipe.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala:464) 和 [LoadUnit.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1327) 的 replay/miss 分流。
4. [LoadQueueReplay.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:635) 的 C_BC entry 生命周期，以及 [Rob.scala](/nfs/home/yanyusong/cache-conflict-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1547) 的架构提交锚点。

## 14. 异常/不一致

- 本次目标 conflict 窗口与源码预期一致，没有发现 miss、MSHR、memory violation 或 LoadUnit rollback 的不一致。
- 全局 redirect 计数非零，但通过 `debugIsCtrl=1`、`debugIsMemVio=0` 和分支 PC 已证明它们是测试循环的控制流预测恢复，不是 bank conflict；这也是最容易把波形读错的地方。
- FST 没有稳定暴露完整 FTQ/IBuffer 预测字段、scheduler issue handshake、内部 FSM enum 或整数写回 data 总线。报告对这些字段明确标为“未 dump/未能证明”，只使用可观测的 decode/rename/dispatch、LoadUnit、LRQ、commit 和 CSR 信号，不以缺失信号推断额外行为。
- `--no-diff` 关闭参考模型比较，因此 PASS 和 commit/load-event 是本实验的架构正确性证据；它不等同于一次开启 NEMU 对比的 difftest。

## 15. 最终判断

测试程序和波形均达到目标。它覆盖的不是“真实 miss 恰好伴随 bank conflict”，而是更清晰、可重复的 **warmed L1 hit + three-way read-read bank conflict**：

1. 三个 load 在不同 set 上映射到同一个 bank。
2. BankedDataArray 选择 LQ 最老的 c0，屏蔽 c4/c8 的物理 SRAM 读。
3. slow 信号延迟一拍到达，两个 loser 被标记为 `C_BC`，没有 miss request。
4. c4 第一次 fast replay 后成功；c8 再次失败，进入 LRQ。
5. LRQ 重放 c8 后无冲突完成，三条 load 各自按程序顺序提交。
6. 冲突路径没有 memory violation 或 LoadUnit rollback；波形中的 redirect 来自循环分支预测恢复。

这与 [analysis.md](/nfs/home/yanyusong/XiangShanLab/xiangshan-course/docs/5-xiangshan-scenarios-analysis/scenarios-analysis/conflict/cache-conflict/analysis.md:1) 中的源码推导和预期行为逐项吻合。
