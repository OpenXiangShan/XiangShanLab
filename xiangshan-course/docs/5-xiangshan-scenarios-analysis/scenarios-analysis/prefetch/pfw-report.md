# PREFETCH.W 演示程序与波形分析报告

## 1. 产物与执行方法

- 演示程序目录：`/home/yanyusong/cbo-kmhv2/nexus-am/apps/prefetch_w`
- 镜像：`/home/yanyusong/cbo-kmhv2/nexus-am/apps/prefetch_w/build/prefetch_w-riscv64-xs.bin`
- 最终波形：`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-17-23-02.fst`
- 反汇编目标：PC `0x8000017c`，指令字 `0x0236e013`；该工具链未为该扩展指令显示助记符，程序源码将其作为 `PREFETCH.W 32(a3)` 使用。

构建命令：

```bash
source ~/cbo-kmhv2/env.sh
cd ~/cbo-kmhv2/nexus-am/apps/prefetch_w
make ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=1
```

仿真命令：

```bash
source ~/cbo-kmhv2/env.sh
cd ~/cbo-kmhv2/XiangShan
./build/emu --dump-wave-full --no-diff \
  -i ~/cbo-kmhv2/nexus-am/apps/prefetch_w/build/prefetch_w-riscv64-xs.bin
```

最终仿真打印：

```text
before PREFETCH.W: observed=0x37c048bf, target=0000000080001720
after PREFETCH.W: target=0x9265ed1a
Core 0: HIT GOOD TRAP at pc = 0x800001d0
```

## 2. 程序设计

`cache_demo_data` 按 64 byte 对齐。预取前，程序写入并读取 `cache_demo_data[0]` 和 `[1]`，即 cache line `0x800016c0`；这满足“PREFETCH.W 前先进行内存访问”的要求。

预取使用的基址则为 `0x80001700`，即**下一条未被上述操作触及的 cache line**；`PREFETCH.W 32(a3)` 的目标地址为 `0x80001720`。在预取之后，程序打印提示信息，再执行：

```text
sw a1, 96(s0)    # PC 0x80000198，写入 0x80001720
lw a1, 96(s0)    # 随后读回校验
```

这样避免了“预取前访问与预取目标落在同一 cache line，导致预取直接 hit 而不产生可见事务”的伪场景。

## 3. 波形分析方法

本分析使用 wavekit 开源仓库 `/home/yanyusong/wavekit` 中的 `wavekit.FstReader` 解析 FST，并在 `TOP.clock` **上升沿**采样。FST 时间范围为 `0` 至 `57829`；相邻上升沿相差 2 个仿真时间单位。

PC 锚点使用 ROB 的 Difftest 提交通道：

```text
TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock.rob
  .difftest_commit_7_{valid,instr,pc}
```

DCache 预取请求锚点使用：

```text
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.mainPipe
  .main_pipe_req_arb.io_out_{valid,ready,bits_source,bits_cmd,bits_vaddr,bits_addr,bits_miss}
```

## 4. 全局时间线

| 周期 | 时间 | 观察点 | 关键值 | 结论 |
|---:|---:|---|---|---|
| 17888 | 35776 | MissQueue entry 2 | `req_addr=0x80001700`、`source=3`、`cmd=3` | 对目标 cache line 分配了预取 miss entry。 |
| 17934 | 35868 | DCache main-pipe arbiter | `valid=1`、`ready=1`、`fire=1`；`vaddr=0x80001720`、`addr=0x80001700`、`source=3`、`cmd=3`、`miss=1` | 写预取请求实际进入 DCache miss 路径；物理地址按 cache line 对齐。 |
| 17935 | 35870 | MissQueue entry 2 | `s_mainpipe_req=1`、`mainpipe_req_fired=1`、`io_mem_grant_valid=1` | entry 从等待主流水请求转入后续处理；本窗口未看到该请求被取消。 |
| 18757 | 37514 | ROB Difftest commit lane 7 | `valid=1`、`instr=0x0236e013`、`pc=0x8000017c` | `PREFETCH.W` 正常提交，无异常或 redirect 证据。 |
| 24179 | 48358 | StoreBuffer Difftest store lane 0 | `pc=0x80000198`、`addr=0x80001720`、`data=0x9265ed1a`、`mask=0xf` | 预取后的普通 `sw` 确实写入同一目标地址。 |
| 28859 | 57718 | 仿真结束 | GOOD TRAP，`pc=0x800001d0` | 程序校验通过。 |

`PREFETCH.W` 在周期 17934 已将存储预取发往 DCache，而在周期 18757 才提交；两者相距 823 周期。这符合软件预取的非阻塞用途：其 cache 请求可在 ROB 顺序提交之前发射，给后续使用留出隐藏 miss 延迟的时间。

## 5. DCache / MissQueue 证据

在周期 17934：

```text
main_pipe_req_arb.io_out.valid         = 1
main_pipe_req_arb.io_out.ready         = 1
main_pipe_req_arb.io_out.bits_source   = 3
main_pipe_req_arb.io_out.bits_cmd      = 3
main_pipe_req_arb.io_out.bits_vaddr    = 0x80001720
main_pipe_req_arb.io_out.bits_addr     = 0x80001700
main_pipe_req_arb.io_out.bits_miss     = 1
```

因此该周期存在明确的 `fire = valid && ready`，而不是仅有组合信号。`vaddr` 保留了 `32(a3)` 的字节地址；`addr` 被截断为 64-byte line 地址 `0x80001700`。MissQueue entry 2 从周期 17888 起保持同一 `req_addr/source/cmd`，并于周期 17935 标记 `mainpipe_req_fired=1`。

随后 StoreBuffer 的 Difftest 记录表明，程序的普通 store 的 PC、地址、数据和 byte mask 均与反汇编一致。程序随后读回该值并以 GOOD TRAP 退出，证明该场景没有引入异常或错误数据。

## 6. 源码依据

### 6.1 请求类型

[DCacheWrapper.scala:103](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L103) 将 DCache 软件/硬件预取的 source 编码定义为 3：

```scala
// prefetch source >= 3
def DCACHE_PREFETCH_SOURCE = 3
```

[StorePipe.scala:169](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala#L169) 对 miss 的 store-prefetch 直接构造 DCache 请求：

```scala
io.miss_req.valid := s2_valid && !s2_hit && s2_is_prefetch
io.miss_req.bits.source := DCACHE_PREFETCH_SOURCE.U
io.miss_req.bits.pf_source := L1_HW_PREFETCH_STORE
io.miss_req.bits.cmd := MemoryOpConstants.M_PFW
io.miss_req.bits.addr := get_block_addr(s2_paddr)
io.miss_req.bits.vaddr := s2_req.vaddr
```

这正解释了波形中的 `source=3`、`cmd=3`、目标虚拟地址 `0x80001720` 与对齐 line 地址 `0x80001700`。

### 6.2 MissQueue 判定

[MissQueue.scala:92](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L92) 明确区分预取来源和写预取命令：

```scala
def isFromPrefetch = source >= DCACHE_PREFETCH_SOURCE.U
def isPrefetchWrite = source === DCACHE_PREFETCH_SOURCE.U && cmd === MemoryOpConstants.M_PFW
def isPrefetchRead = source === DCACHE_PREFETCH_SOURCE.U && cmd === MemoryOpConstants.M_PFR
```

故波形中 `source=3 && cmd=3` 的 entry 2 是 `PREFETCH.W` 的预取写请求，而不是普通 store。

## 7. 场景结论

**满足要求。** 最终程序在 `PREFETCH.W` 前进行了与目标不同 cache line 的真实读写和 `printf`，在其后再次 `printf` 并对预取目标执行普通 store/load 校验。波形同时证明：

1. `0x8000017c` 的 `0x0236e013` 确实提交；
2. 其目标 `0x80001720` 形成了 `valid && ready` 的 DCache 事务；
3. 该事务为 `DCACHE_PREFETCH_SOURCE=3` 和 `M_PFW` 的写预取，且 `miss=1`；
4. MissQueue entry 2 接收该 line；
5. 后续普通 store 在 `0x80001720` 写入预期数据，并最终 GOOD TRAP。

本次波形足以验证“冷 line 的 `PREFETCH.W` 请求被送入 DCache miss 路径并由后续内存操作使用”的场景。未将该结果解释为定量性能提升：程序中 `printf` 引入了大量 UART 相关指令和长间隔，若要测量预取收益，应使用无 `printf` 的计时版本并对比有/无 `PREFETCH.W` 的访问延迟。
