# l1miss_l2hit RTL 代码覆盖分析

## 1. 分析边界

本文记录波形能够证明该场景经过的 RTL 行为路径。它是基于事务、内部信号和源码条件的
路径分析，不等同于仿真器生成的 line/branch coverage 百分比。

RTL 基线：XiangShan commit `53ace33fd6b9c7c4289bb4751e3bb6ac3348075f`。

## 2. 覆盖路径总览

```text
第一次目标 load
  -> DCache miss queue 生成 AcquireBlock
  -> CoupledL2 目录 miss
  -> need_mshr_s3 = 1，分配 MSHR
  -> MSHR 将 AcquireBlock NtoB 转成 CHI ReadNotSharedDirty
  -> GrantData 返回

六条同 set 冲突访问
  -> DCache 对六条 line 分别走 miss queue AcquireBlock

第二次目标 load
  -> DCache miss queue 再次生成 AcquireBlock
  -> CoupledL2 目录 hit
  -> need_mshr_s3 = 0
  -> sink_resp_s3.valid = 1，直接响应 GrantData
  -> 不生成目标地址 CHI 请求
```

## 3. DCache miss 请求生成

源码：
`src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:829-845`。

关键逻辑：

```scala
io.mem_acquire.valid := !s_acquire && ...
val acquireBlock = edge.AcquireBlock(..., toAddress = req.addr, ...)
io.mem_acquire.bits := Mux(full_overwrite, acquirePerm, acquireBlock)
```

本场景是 load miss，不是 full-overwrite store，因此选择 `acquireBlock`。波形中目标地址
第一次和第二次均出现 A opcode 6，六条冲突地址也分别出现 A opcode 6，证明这些访问
覆盖了 MissQueue 的 `AcquireBlock` 输出路径。

## 4. 第一次访问覆盖 L2 miss 分支

源码：
`coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:235-256`。

关键条件：

```scala
val need_acquire_s3_a = req_s3.fromA && Mux(
  dirResult_s3.hit,
  acquire_on_hit_s3,
  acquire_on_miss_s3
)
val need_mshr_s3_a = need_acquire_s3_a || need_probe_s3_a || cache_alias
```

第一次访问实测 `dir_hit=0`、`need_mshr_s3_a=1`，对应 `dirResult_s3.hit=false` 时的
`acquire_on_miss_s3` 分支。

MSHR 分配位于
`MainPipe.scala:293-305`：

```scala
val need_mshr_s3 = need_mshr_s3_a || need_mshr_s3_b
io.toMSHRCtl.mshr_alloc_s3.valid :=
  task_s3.valid && !mshr_req_s3 && need_mshr_s3
```

实测 `need_mshr_s3=1`、`mshr_alloc_s3_valid=1`、`sink_resp_s3_valid=0`，证明第一次
目标访问进入 MSHR，而不是 L2 直接响应。

## 5. Miss 向 CHI 请求数据

源码：
`coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:352-395`。

其中协议映射明确规定：

```text
AcquireBlock NtoB -> ReadNotSharedDirty
```

选择逻辑在 `MSHR.scala:385-393`，`req_needB` 默认映射为
`ReadNotSharedDirty`。第一次访问波形出现目标地址、opcode `0x26` 的 CHI TxReq，证明
覆盖了该映射与 MSHR TXREQ 路径。

## 6. 第二次访问覆盖 L2 hit 直接响应分支

源码：
`coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:235-256` 和
`MainPipe.scala:424-433`。

第二次访问实测目录命中，且当前 BRANCH 权限可满足 `AcquireBlock NtoB`，因此
`acquire_on_hit_s3=false`，最终：

```scala
need_mshr_s3 = false
sink_resp_s3.valid := task_s3.valid && !mshr_req_s3 && !need_mshr_s3
sink_resp_s3.bits.opcode := odOpGen(req_s3.opcode)
```

波形同时满足：

- `dir_hit=1`
- `need_mshr_s3_a=0`
- `need_mshr_s3=0`
- `mshr_alloc_s3_valid=0`
- `sink_resp_s3_valid=1`
- `sink_resp_s3_bits_opcode=GrantData`

## 7. 否定覆盖证据

第二次目标请求到目标 load 提交的窗口内，同地址 CHI TxReq 数量为 0。这与第一次访问
能够命中同一查询形成对照，证明第二次访问没有再次覆盖 MSHR 下行取数路径，而是停留在
L2 main pipe 的 hit 直接响应路径。

## 8. 覆盖矩阵

| 模块 | 文件/行 | 条件或动作 | 波形结果 |
| --- | --- | --- | --- |
| DCache MissQueue | `MissQueue.scala:829-845` | load miss 生成 `AcquireBlock` | 已覆盖 |
| CoupledL2 MainPipe | `MainPipe.scala:235-256` | directory miss 需要 acquire | 第一次访问已覆盖 |
| CoupledL2 MainPipe | `MainPipe.scala:293-305` | `need_mshr` 时分配 MSHR | 第一次访问已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:352-395` | NtoB 映射 `ReadNotSharedDirty` | 第一次访问已覆盖 |
| CoupledL2 MainPipe | `MainPipe.scala:424-433` | `!need_mshr` 直接响应 | 第二次访问已覆盖 |
| MSHR CHI 取数 | 同上 | 第二次访问再次向 CHI 请求 | 已证明未覆盖 |

## 9. 未覆盖范围

- 本场景没有覆盖真正的 L1 hit 返回路径。
- 没有覆盖 `AcquirePerm`、NtoT/ReadUnique、store miss、AMO、prefetch 和 CMO 分支。
- 没有通过 tag array 内部端口直接证明具体 L1 victim way。
- 本文不提供工具生成的 RTL line/branch coverage 数值。
