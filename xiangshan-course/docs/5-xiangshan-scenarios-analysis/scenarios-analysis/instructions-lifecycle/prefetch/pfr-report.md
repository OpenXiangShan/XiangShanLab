# PREFETCH.R 演示程序与波形分析报告

## 1. 结论摘要

本次在昆明湖 V2 环境中完成了 `PREFETCH.R` 演示程序、构建、仿真和波形检查。波形使用本机开源仓库 `/home/yanyusong/wavekit` 中的 **wavekit**（`FstReader`）解析和按 `TOP.clock` 上升沿查询。

**场景分析结论：满足。** 程序在 `prefetch.r` 前初始化并读取目标缓存行，在指令后对同一缓存行执行读取和 `printf` 输出；波形证明该指令被识别为软预取读请求，并以 `M_PFR` 送入 DCache。仿真最终以 `GOOD TRAP` 正常退出。

| 项目 | 结果 |
| --- | --- |
| 演示源文件 | `~/cbo-kmhv2/nexus-am/apps/prefetch_r/prefetch_r.c` |
| 生成镜像 | `/home/yanyusong/cbo-kmhv2/nexus-am/apps/prefetch_r/build/prefetch_r-riscv64-xs.bin` |
| 目标指令 PC | `0x800001a6` |
| 指令字 | `0x0212e013`，即固定为 `prefetch.r 0x20(t0)` 的编码 |
| 指令基址 / 目标地址 | `t0 = 0x80001740`，目标虚拟地址 `0x80001760` |
| 波形文件 | `/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-16-39-21.fst`（285 MiB） |
| 仿真结果 | `Core 0: HIT GOOD TRAP at pc = 0x800001ea` |

## 2. 演示程序设计

程序将 `demo_block` 按 64 B 对齐，使其恰好为一条缓存行。它先写入并读取全部 8 个 `uint64_t`，再打印校验和与 `word[4]`；随后通过内联汇编将块首地址写入 `t0`，执行 `.word 0x0212e013`，即对 `base + 32 B` 发出 `PREFETCH.R`。最后读取 `word[4]` 和 `word[5]` 并打印和。

因此，目标地址 `0x80001760` 与预取前后的普通访存属于同一条从 `0x80001740` 开始的 64 B 缓存行。仿真 UART 输出为：

```text
PREFETCH.R demonstration starts
target cache line: 0x80001740, prefetch address: 0x80001760
before prefetch.r: checksum=0x89119a22ab33b81c word[4]=0x1122334455667704
prefetch.r issued for base + 32 bytes
after prefetch.r: word[4] + word[5] = 0x22446688aaccee09
PREFETCH.R demonstration ends
```

构建命令为：

```bash
source ~/cbo-kmhv2/env.sh
cd ~/cbo-kmhv2/nexus-am/apps/prefetch_r
make ARCH=riscv64-xs
```

反汇编在 `0x800001a6` 显示：

```text
800001a4: 82a6                 mv      t0,s1
800001a6: 0212e013             .word   0x0212e013
800001aa: ...                  # 后续 printf
800001b6: 708c                 ld      a1,32(s1)
800001b8: 749c                 ld      a5,40(s1)
```

## 3. 仿真与波形方法

使用的仿真命令如下：

```bash
source ~/cbo-kmhv2/env.sh
cd ~/cbo-kmhv2/XiangShan
./build/emu --dump-wave-full --no-diff \
  -i /home/yanyusong/cbo-kmhv2/nexus-am/apps/prefetch_r/build/prefetch_r-riscv64-xs.bin
```

波形时钟为 `TOP.clock`，下表中的 cycle/time 是 wavekit 以时钟上升沿采样得到的绝对周期和仿真时间。`fire = valid && ready`。

## 4. 波形证据与时间线

| Cycle | Time | 位置 / 信号 | 观察值 | 含义 |
| ---: | ---: | --- | --- | --- |
| 25663 | 51326 | `decode.decoders_4.io_deq_decodedInst_pc`、`isSoftPrefetch` | PC=`0x800001a6`，`isSoftPrefetch=1` | 指令被译码器识别为软预取。 |
| 25665 | 51330 | `io_toIssueBlock_memUops_6` | `valid=1`、`ready=1`、`instr=0x0212e013`、ROB=`12`、`fuOp=0x9` | 调度边界发生 `fire`，指令进入 memory issue 路径。 |
| 25671 | 51342 | `backend.io_mem_issueLda_1` | PC=`0x800001a6`、`valid=1` | 目标指令被选到 load-address 发射端口 1。 |
| 25671 | 51342 | `inner_LoadUnit_1.io_ldin` | `valid=1`、`ready=1`、PC=`0x800001a6`、ROB=`12`、`fuOp=0x9`、`src_0=0x80001740` | LoadUnit 接收同一个 ROB 项；没有入口反压。 |
| 25671 | 51342 | `inner_LoadUnit_1.io_tlb_req_bits_isPrefetch` | `1` | TLB 请求明确带有预取标记。 |
| 25671 | 51342 | `inner_LoadUnit_1.io_dcache_req` | `valid=1`、`ready=1`、`cmd=2`、`vaddr=0x80001760` | DCache 请求发生 `fire`；`cmd=2` 与 `M_PFR` 完全一致。 |
| 25660–25689 | 51320–51378 | `inner_LoadUnit_1.io_redirect_valid` | 目标请求窗口为 `0` | 未观察到该指令导致的 LoadUnit redirect。 |

`LoadUnit` 的目标周期同时满足输入和 DCache 输出握手，故没有由该 `PREFETCH.R` 引起的可见 `valid && !ready` 停顿。该程序并非性能基准：后续 `printf` 会引入大量无关访存，因此本次只证明请求被正确生成和发送，不以其周期数声称缓存命中率或加速比。

## 5. 源码与波形的因果对应

### 5.1 译码

[DecodeUnit.scala:1102](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L1102) 将 `opcode=0010011`、`funct3=110`、`rd=x0` 归为软预取；当 `RS2=1` 时判定为 `prefetch.r`，并将 `fuOpType` 设为 `LSUOpType.prefetch_r`。这解释了 cycle 25663 的 `isSoftPrefetch=1`，以及 cycle 25665/25671 观测到的 `fuOp=0x9`。

```scala
val isSoftPrefetch = inst.OPCODE === BitPat("b0010011") && inst.FUNCT3 === BitPat("b110") && inst.RD === 0.U
val isPreR = isSoftPrefetch && inst.RS2 === 1.U(5.W)
...
isPreR -> LSUOpType.prefetch_r
```

### 5.2 LoadUnit 地址与预取属性

[LoadUnit.scala:615](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L615) 对整数访存输入计算 `src(0) + sign-extended imm[11:0]`，并以 `fuOpType` 设置 `prf`、`prf_rd`、`prf_wr`。本次 `src_0=0x80001740`，指令立即数为 `0x20`，因此波形中的 DCache 地址应为并且实际为 `0x80001760`。

```scala
val addr = io.ldin.bits.src(0) + SignExt(io.ldin.bits.uop.imm(11, 0), VAddrBits)
out.prf    := LSUOpType.isPrefetch(src.uop.fuOpType)
out.prf_rd := src.uop.fuOpType === LSUOpType.prefetch_r
```

### 5.3 TLB 与 DCache 请求

[LoadUnit.scala:383](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L383) 将 `prf` 传给 `io.tlb.req.bits.isPrefetch`；[LoadUnit.scala:406](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L406) 在 `prf_rd` 时选择 `M_PFR`，并将 source 标为 DCache prefetch。

```scala
io.tlb.req.bits.isPrefetch := s0_sel_src.prf
io.dcache.req.valid := s0_valid && !s0_sel_src.prf_i && !s0_nc_with_data
io.dcache.req.bits.cmd := Mux(s0_sel_src.prf_rd,
  MemoryOpConstants.M_PFR,
  Mux(s0_sel_src.prf_wr, MemoryOpConstants.M_PFW, MemoryOpConstants.M_XRD))
io.dcache.req.bits.vaddr := s0_dcache_vaddr
io.dcache.req.bits.instrtype := Mux(s0_sel_src.prf, DCACHE_PREFETCH_SOURCE.U, LOAD_SOURCE.U)
```

这与 cycle 25671 的 `isPrefetch=1`、`cmd=2`、`vaddr=0x80001760` 一一对应。

### 5.4 DCache 命令值

[CacheConstants.scala:29](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/CacheConstants.scala#L29) 定义 `M_PFR = b00010`，即十进制 `2`，表示“有读意图的预取”。

```scala
def M_XRD = "b00000".U
def M_XWR = "b00001".U
def M_PFR = "b00010".U // prefetch with intent to read
def M_PFW = "b00011".U // prefetch with intent to write
```

## 6. 场景要求核对

| 要求 | 证据 | 结论 |
| --- | --- | --- |
| 在 `prefetch.r` 前进行内存访问 | 初始化循环对 8 个字执行 store+load，并打印 checksum/`word[4]`。 | 满足 |
| 插入 `PREFETCH.R` | 反汇编和波形均为 `0x0212e013`，PC=`0x800001a6`。 | 满足 |
| 对 cache 相关地址发起读预取 | wavekit 显示 DCache `cmd=2 (M_PFR)`、`vaddr=0x80001760`、`valid && ready=1`。 | 满足 |
| 在指令后继续内存访问或 `printf` | 后续 `printf` 后读取 `word[4]`、`word[5]`，输出结果正确。 | 满足 |
| 仿真能够正常完成 | `HIT GOOD TRAP`。 | 满足 |

## 7. 限制与后续建议

- 本报告验证的是 `PREFETCH.R` 的功能路径和请求握手，不等同于量化预取收益。
- 若需要测量收益，应将预取后的读取改成可控延迟的独立循环，避免 `printf` 主导缓存行为；再比较有/无 `prefetch.r` 的 load latency、DCache hit/miss、MSHR 和 PMU 计数。
- 本次已检查目标 LoadUnit 的 redirect 输出为 0；未发现本指令触发的 redirect、异常或入口/DCache 反压。
