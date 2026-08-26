# FENCE.I 自修改代码演示报告

## 结论

**场景满足。** 演示程序先对数据内存读写并执行一段可执行的数据区代码，随后将该代码首条指令从 `addi a0, zero, 1` 改写为 `addi a0, zero, 42`，执行 `fence.i`，再进行后续内存访问并重新执行该代码。昆明湖 V2 仿真打印 `FENCE.I demonstration PASSED: fetched patched instruction`，且产生 `HIT GOOD TRAP`。

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| `fence.i` 位于代码写入之后 | 通过 | 反汇编在 `0x800001e8` 写入 `0x02a00513`，在 `0x800001fe` 执行 `fence.i`。 |
| Fence 前存在内存访问 | 通过 | 程序对 `data_before_fence[8]` 写入、求和并打印。 |
| Fence 后存在内存访问 | 通过 | 程序对 `data_after_fence[8]` 写入、求和并打印。 |
| 自修改代码对取指可见 | 通过 | 首次调用返回 `1`；改写并执行 `fence.i` 后再次调用返回 `42`。 |
| XiangShan 实现实际触发 ICache 处理 | 通过 | WaveKit 观测到 Fence 单元、Frontend、ICache 及 MissUnit 的 `fencei` 脉冲，随后 ICache `flush` 置位。 |

## 演示程序

源码为 [`~/cbo-kmhv2/nexus-am/apps/fence_i/fence_i.c`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/fence_i/fence_i.c)。`generated_code` 位于可读写的普通数据区，初始机器码为：

```text
0x00100513    addi a0, zero, 1
0x00008067    ret
```

其关键顺序是：

1. 对 `data_before_fence` 做八个 `volatile` 写入和八个读取求和。
2. 间接调用 `generated_code`，使旧指令已被执行，返回 `1`。
3. 向 `generated_code[0]` 写入 `0x02a00513`（`addi a0, zero, 42`）。
4. 执行内联汇编 `fence.i`。
5. 对 `data_after_fence` 做八个 `volatile` 写入和八个读取求和。
6. 再次间接调用同一地址；返回 `42` 才报告通过。

没有在写入后、`fence.i` 前执行该地址；这种取指与存储不同步的结果不应作为程序正确性依据。

反汇编文件为 [`~/cbo-kmhv2/nexus-am/apps/fence_i/build/fence_i-riscv64-xs.dis`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/fence_i/build/fence_i-riscv64-xs.dis)。关键指令如下：

```text
800001c2: auipc s1,...               # s1 = 0x80001940, generated_code
800001ce: jalr  s1                   # 第一次执行，返回 1
800001e0: lui   a5,0x2a00
800001e4: addi  a5,a5,1299           # a5 = 0x02a00513
800001e8: sw    a5,0(s1)             # 改写 generated_code[0]
800001fe: fence.i
8000021c: sw    a3,64(a4)            # Fence 后数据写入循环
```

## 构建与仿真

每个相关命令前均从 `~/cbo-kmhv2` 执行 `source env.sh`。构建命令：

```bash
cd ~/cbo-kmhv2/nexus-am/apps/fence_i
make ARCH=riscv64-xs
```

生成镜像：

```text
/home/yanyusong/cbo-kmhv2/nexus-am/apps/fence_i/build/fence_i-riscv64-xs.bin
```

仿真命令：

```bash
cd ~/cbo-kmhv2/XiangShan
./build/emu --dump-wave-full --no-diff \
  -i ../nexus-am/apps/fence_i/build/fence_i-riscv64-xs.bin
```

仿真结束状态为 `0`，结束信息包括：

```text
second execution returns 42
FENCE.I demonstration PASSED: fetched patched instruction
Core 0: HIT GOOD TRAP at pc = 0x800002b0
```

完整波形文件：

```text
/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-17-25-30.fst
```

## WaveKit 波形分析

本分析使用 WaveKit 的 `FstReader` 打开上述 `.fst`，以 `TOP.clock` 对信号进行正沿采样。以反汇编 PC `0x800001fe` 和指令字 `0x0000100f` 为锚点；结果显示该指令在 Decode lane 3 的第 `45916` 周期出现，在 ROB difftest commit lane 0 的第 `46899` 周期提交。

| 周期 | 波形证据 | 含义 |
| --- | --- | --- |
| 45916 | `decode.decoders_3.io_deq_decodedInst_pc=0x800001fe`，`instr=0x0000100f` | 目标 `fence.i` 在译码输出可见。 |
| 46889 | `backend.inner_intExuBlock.exus_7.Fence.io_fenceio_fencei=1`；backend 与 frontend 对应 `fencei=1` | Fence 功能单元生成并送出 ICache 刷新请求。 |
| 46890 | `frontend.inner_icache_io_fencei_REG=1`、`inner_icache.io_fencei=1`、`io_fencei_probe=1`、`missUnit.io_fencei=1` | 请求被寄存并进入 ICache 与 MissUnit。 |
| 46898 | `frontend.inner_icache.io_flush=1` | ICache flush 控制生效。 |
| 46899 | `rob.difftest_commit_valid=1` 且 `rob.difftest_commit_pc=0x800001fe` | 该 `fence.i` 在架构提交点退休。 |

在第 `46889` 至 `46910` 周期窗口内，以上信号之外的目标 `fencei` 脉冲均为 `0`；因此观测到的是单次、完整传递的 `Fence -> Frontend -> ICache/MissUnit` 事件，而不是持续高电平。该时间关系也说明 ICache 内部处理完成后才到第 `46899` 周期提交。

## RTL 对照

XiangShan 的 [`Fence.scala:47`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L47) 定义了 `s_icache` 状态，且 [`Fence.scala:65`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L65) 仅在该状态拉高 `fencei`；[`Fence.scala:79`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L79) 在 store buffer 为空时从 `s_wait` 转入该状态。

```scala
val s_idle :: s_wait :: s_tlb :: s_icache :: s_fence :: s_nofence :: Nil = Enum(6)
val state = RegInit(s_idle)
sbuffer := state === s_wait
fencei  := state === s_icache

when (state === s_wait && func === FenceOpType.fencei && sbEmpty) {
  state := s_icache
}
when (state =/= s_idle && state =/= s_wait) { state := s_idle }
```

核心顶层在 [`XSCore.scala:139`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/XSCore.scala#L139) 直接把 backend `fencei` 接至 frontend：

```scala
frontend.io.fencei <> backend.io.fenceio.fencei
```

ICache 的输入定义见 [`ICache.scala:562`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala#L562)，而 [`ICache.scala:635`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala#L635) 将其接到 MetaArray 的全表失效控制。实际失效逻辑见 [`ICache.scala:379`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala#L379)：每一路 `valid_array` 被清零。

```scala
metaArray.io.flushAll := io.fencei
missUnit.io.fencei := io.fencei

// flush all (e.g. fence.i)
when(io.flushAll) {
  (0 until nWays).foreach(w => valid_array(w) := 0.U)
}
```

这与波形中 `46890` 的 ICache/MissUnit `fencei` 以及其后 `46898` 的 `io_flush` 一致，验证了本自修改代码示例满足“写入新指令后由 `fence.i` 使后续取指观察到新内容”的场景要求。
