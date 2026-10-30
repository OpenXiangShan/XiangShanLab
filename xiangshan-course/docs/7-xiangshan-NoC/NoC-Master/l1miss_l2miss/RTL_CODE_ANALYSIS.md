# l1miss_l2miss RTL 代码覆盖分析

## 1. 分析边界

本文给出波形能够确认的 RTL 行为路径，不等同于仿真工具输出的 line/branch coverage。
RTL 基线为 XiangShan commit `53ace33fd6b9c7c4289bb4751e3bb6ac3348075f`。

## 2. 覆盖路径

```text
目标 load
  -> DCache main pipe 识别 miss并送入 MissQueue
  -> MissQueue 生成 AcquireBlock NtoB
  -> CoupledL2 main pipe 目录 miss
  -> need_mshr_s3 = 1，分配 MSHR
  -> MSHR 生成 ReadNotSharedDirty
  -> CHI/L3 返回数据
  -> DCache 接收 GrantData 和 GrantAck
```

## 3. DCache miss 进入 MissQueue

源码：
`src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:832-852`。

`io.miss_req.valid := s2_valid && s2_can_go_to_mq` 将 DCache S2 miss 请求送入 MissQueue，
并携带物理地址、虚拟地址、访问命令和一致性状态。目标 load 随后在 L1/L2 接口出现
`AcquireBlock`，从外部事务上确认该 miss 请求进入了 MissQueue 路径。

## 4. MissQueue 生成 AcquireBlock

源码：
`src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:829-845`。

load 不是 full-overwrite store，因此：

```scala
io.mem_acquire.bits := Mux(full_overwrite, acquirePerm, acquireBlock)
```

选择 `acquireBlock`。波形实测 A opcode 6、param 0、size 6、地址
`0x80024000`，证明覆盖该分支。

## 5. CoupledL2 directory miss 与 MSHR allocation

源码：
`coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:235-256` 和
`MainPipe.scala:293-305`。

目录 miss 时 `acquire_on_miss_s3=true`，使 `need_acquire_s3_a` 和
`need_mshr_s3_a` 置位。随后：

```scala
io.toMSHRCtl.mshr_alloc_s3.valid :=
  task_s3.valid && !mshr_req_s3 && need_mshr_s3
```

波形实测 `dir_hit=0`、`need_mshr_s3_a=1`、`need_mshr_s3=1`、
`mshr_alloc_s3_valid=1`、`sink_resp_s3_valid=0`，完整覆盖 L2 miss allocation 分支。

## 6. MSHR 生成 CHI ReadNotSharedDirty

源码：
`coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:352-395`。

协议映射和优先选择逻辑将 `AcquireBlock NtoB` 映射到
`ReadNotSharedDirty`：

```scala
req_needB -> ReadNotSharedDirty
```

波形实测目标地址 CHI opcode `0x26`，证明 MSHR 下行请求路径已覆盖。

## 7. 返回与完成路径

波形出现两拍 D-channel `GrantData`，两拍均 `denied=0`、`corrupt=0`，随后 E-channel
`GrantAck` 和目标 load commit。它证明 MSHR 请求不是只发出未完成，而是最终返回到
DCache 并完成架构状态更新。

## 8. 覆盖矩阵

| 模块 | 文件/行 | 分支或动作 | 状态 |
| --- | --- | --- | --- |
| DCache MainPipe | `MainPipe.scala:832-852` | miss 请求送入 MissQueue | 已覆盖 |
| DCache MissQueue | `MissQueue.scala:829-845` | load 选择 `AcquireBlock` | 已覆盖 |
| CoupledL2 MainPipe | `MainPipe.scala:235-256` | directory miss acquire | 已覆盖 |
| CoupledL2 MainPipe | `MainPipe.scala:293-305` | 分配 MSHR | 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:352-395` | NtoB 生成 `ReadNotSharedDirty` | 已覆盖 |
| CoupledL2 hit 直返 | `MainPipe.scala:424-433` | `!need_mshr` direct response | 未覆盖 |

## 9. 未覆盖范围

- L1 hit、L2 hit 直接响应和 replacement 分支未覆盖。
- NtoT/ReadUnique、AcquirePerm、store/AMO/prefetch/CMO 未覆盖。
- 未提供工具生成的源码覆盖率百分比。
