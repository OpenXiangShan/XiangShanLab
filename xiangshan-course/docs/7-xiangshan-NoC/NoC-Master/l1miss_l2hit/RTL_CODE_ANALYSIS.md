# l1miss_l2hit RTL 代码覆盖分析

## 1. 分析边界

本文记录波形能够证明该场景经过的 RTL 行为路径。它是基于事务、内部信号和源码条件的
路径分析，不等同于仿真器生成的 line/branch coverage 百分比。

RTL 基线：

- XiangShan：`kunminghu-v3`，commit `6705b63685d301f8c0960f7cbcc8b964660dc58e`
- XSCache 子模块：`e9488aed486358e3e5984b531aab601f9b91a9c0`

## 2. 覆盖路径总览

```text
第一次目标 load
  -> DCache miss queue 生成 AcquireBlock
  -> L2 目录 miss
  -> need_mshr_s3 = 1，分配 MSHR
  -> MSHR 将 AcquireBlock NtoB 转成 CHI ReadNotSharedDirty
  -> GrantData 返回

16 条同 L1 set 冲突访问
  -> DCache 对 16 条 line 分别走 miss queue AcquireBlock
  -> L2 目录均 miss，并各自分配 MSHR

第二次目标 load
  -> DCache miss queue 再次生成 AcquireBlock
  -> L2 目录 hit
  -> need_mshr_s3 = 0
  -> sink_resp_s3.valid = 1，直接响应 GrantData
  -> 不生成目标地址 CHI 请求
```

## 3. DCache miss 请求生成

源码：

- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:909-910`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:987-1003`

关键逻辑：

```scala
io.miss_req.valid := s2_valid && s2_can_go_to_mq
...
io.mem_acquire.valid := !s_acquire && ...
val acquireBlock = edge.AcquireBlock(...)
io.mem_acquire.bits := Mux(full_overwrite, acquirePerm, acquireBlock)
```

本场景是 load miss，不是 full-overwrite store，因此选择 `acquireBlock`。波形中
18 个目标/冲突地址的 L2 入口请求均为 A opcode 6、param 0，证明这些访问覆盖了
MissQueue 的 `AcquireBlock` 输出路径。

V3 波形在 L1 DCache 侧 A 端口没有观察到可用的目标请求记录，本文把 A 通道证据取在 L2 入口
`slices_0.io_in_a_*`，并用 DCache missQueue 的 `io_misstrack_alloc_*` 补充 L1 miss
证据。

## 4. 第一次访问覆盖 L2 miss 分支

源码：`coupledL2/src/main/scala/coupledL2/MainPipe.scala:245-266`。

关键条件：

```scala
val need_acquire_s3_a = req_s3.fromA && (Mux(
  dirResult_s3.hit,
  acquire_on_hit_s3,
  acquire_on_miss_s3
) || cmo_cbo_s3)
...
val need_mshr_s3_a = need_acquire_s3_a || need_probe_s3_a || cache_alias
```

第一次访问实测 `dir_hit=0`、`mshr_alloc_s3_valid=1`，对应
`dirResult_s3.hit=false` 时的 `acquire_on_miss_s3` 分支。

MSHR 分配位于 `MainPipe.scala:305-310`：

```scala
val need_mshr_s3 = need_mshr_s3_a || need_mshr_s3_b
io.toMSHRCtl.mshr_alloc_s3.valid := task_s3.valid && !mshr_req_s3 && need_mshr_s3
```

实测 `mshr_alloc_s3_valid=1`，证明第一次目标访问进入 MSHR，而不是 L2 直接响应。

## 5. Miss 向 CHI 请求数据

源码：`coupledL2/src/main/scala/coupledL2/MSHR.scala:386-393`。

协议映射：

```scala
oa.opcode := ParallelPriorityMux(Seq(
  ...
  req_needB /* Default */ -> ReadNotSharedDirty
))
```

第一次访问波形出现目标地址、opcode `0x26` 的 CHI TxReq（cycle 1126），证明覆盖了
该映射与 MSHR TXREQ 路径。

## 6. 第二次访问覆盖 L2 hit 直接响应分支

源码：`coupledL2/src/main/scala/coupledL2/MainPipe.scala:433-437`。

```scala
sink_resp_s3.valid := task_s3.valid && !mshr_req_s3 && !need_mshr_s3
...
sink_resp_s3.bits.opcode := odOpGen(req_s3.opcode)
```

第二次访问实测目录命中，且当前 BRANCH 权限可满足 `AcquireBlock NtoB`，因此
`need_mshr_s3=0`，最终：

- `dir_hit=1`
- `mshr_alloc_s3_valid=0`
- `sink_resp_s3.valid=1`
- D 通道 `GrantData`

## 7. 否定覆盖证据

第二次目标请求到目标 load 完成的窗口内，同地址 CHI TxReq 数量为 0。这与第一次访问
能够命中同一查询形成对照，证明第二次访问没有再次覆盖 MSHR 下行取数路径，而是停留在
L2 main pipe 的 hit 直接响应路径。

## 8. 覆盖矩阵

| 模块 | 文件/行 | 条件或动作 | 波形结果 |
| --- | --- | --- | --- |
| DCache MainPipe | `MainPipe.scala:909-910` | S2 miss 进入 MissQueue | 已覆盖 |
| DCache MissQueue | `MissQueue.scala:987-1003` | load 选择 `AcquireBlock` | 已覆盖 |
| CoupledL2 MainPipe | `MainPipe.scala:245-266` | directory miss 需要 acquire | 第一次及 16 条冲突访问已覆盖 |
| CoupledL2 MainPipe | `MainPipe.scala:305-310` | `need_mshr` 时分配 MSHR | 17 次 miss 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:386-393` | NtoB 映射 `ReadNotSharedDirty` | 已覆盖 |
| CoupledL2 MainPipe | `MainPipe.scala:433-437` | `!need_mshr` 直接响应 | 第二次访问已覆盖 |
| MSHR CHI 取数 | 同上 | 第二次访问再次向 CHI 请求 | 已证明未覆盖 |

## 9. 未覆盖范围

- 本场景没有覆盖真正的 L1 hit 返回路径。
- 没有覆盖 `AcquirePerm`、NtoT/ReadUnique、store miss、AMO、prefetch 和 CMO 分支。
- 没有通过 tag array 内部端口直接证明具体 L1 victim way。
- 当前 V3 构建未导出逐指令 commit 端。
- 本文不提供工具生成的 RTL line/branch coverage 数值。
