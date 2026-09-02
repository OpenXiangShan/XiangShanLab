# l1miss_l2miss_replace RTL 代码覆盖分析

## 1. 分析边界

本文描述由波形确认的 L2 replacement 行为路径，不等同于工具生成的 RTL
line/branch coverage。RTL 基线为 XiangShan commit
`53ace33fd6b9c7c4289bb4751e3bb6ac3348075f`。

## 2. 覆盖路径

```text
8 条同 set fill line
  -> L2 set 建立 8 个有效 tag

第 9 条目标 line
  -> DCache AcquireBlock
  -> CoupledL2 directory miss，分配 MSHR
  -> directory/replacer 返回 victim tag/way/meta
  -> MSHR 记录 w_replResp 和 needsRepl
  -> 目标 ReadNotSharedDirty
  -> victim WriteEvictOrEvict
  -> 目标 GrantData、GrantAck、commit
```

## 3. DCache 与 L2 miss 分支

DCache `AcquireBlock` 生成位于
`src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:829-845`。

CoupledL2 miss 和 MSHR allocation 位于
`coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:235-305`。

第九条访问实测目标 `AcquireBlock`、`dir_hit=0`、`need_mshr=1`、
`mshr_alloc_s3_valid=1`，证明覆盖普通 miss 到 MSHR allocation 路径。

## 4. Replacement response 路由

源码：
`coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:124-152`。

当 MSHR 需要 replacement 时，SinkC 匹配使用 `metaTag` 而不是 `reqTag`：

```scala
val tag = Mux(status.needsRepl, status.metaTag, status.reqTag)
```

directory 的 replacement response 按 `mshrId` 路由给对应 MSHR：

```scala
m.io.replResp.valid :=
  io.replResp.valid && io.replResp.bits.mshrId === i.U
```

波形实测 `mshrId=0`、victim tag `0x4001`、way 0，证明 replacement response 已路由到
目标 MSHR。

## 5. MSHR 接收 victim 并建立 replacement 状态

源码：
`coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1257-1297`。

非 retry replacement response 会执行：

```scala
state.w_replResp := true.B
dirResult.tag := replResp.tag
dirResult.way := replResp.way
dirResult.meta := replResp.meta
```

若 victim state 有效，则清除 release 完成状态，准备执行 victim release。状态导出位于
`MSHR.scala:1320-1368`：

```scala
io.status.bits.reqTag := req.tag
io.status.bits.metaTag := dirResult.tag
io.status.bits.needsRepl := !state.s_release
io.msInfo.bits.w_replResp := state.w_replResp
```

实测 `reqTag=0x4009`、`metaTag=0x4001`、`needsRepl=1`、`w_replResp=1`，与代码完全对应。

## 6. 目标取数与 victim eviction

目标 miss 的 CHI 映射位于 `MSHR.scala:352-395`，NtoB 生成
`ReadNotSharedDirty`。波形实测目标地址 opcode `0x26`。

victim release opcode 选择位于 `MSHR.scala:454-523`：

```scala
mp_release.chiOpcode := ParallelPriorityMux(Seq(
  isWriteBackFull       -> WriteBackFull,
  isWriteEvictFull      -> WriteEvictFull,
  isWriteEvictOrEvict   -> WriteEvictOrEvict,
  isEvict               -> Evict
))
```

本场景 victim 为 clean line，波形实测对 `0x80024000` 发出 opcode `0x42`
`WriteEvictOrEvict`，证明覆盖该 victim eviction 分支，而不是空 way refill。

## 7. 覆盖矩阵

| 模块 | 文件/行 | 分支或动作 | 状态 |
| --- | --- | --- | --- |
| DCache MissQueue | `MissQueue.scala:829-845` | 目标生成 `AcquireBlock` | 已覆盖 |
| CoupledL2 MainPipe | `MainPipe.scala:235-305` | miss 并分配 MSHR | 已覆盖 |
| CoupledL2 MSHRCtl | `MSHRCtl.scala:124-152` | replacement response 路由 | 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:1257-1297` | 接收 victim tag/way/meta | 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:1320-1368` | 导出 needsRepl/w_replResp | 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:352-395` | 目标 `ReadNotSharedDirty` | 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:454-523` | victim `WriteEvictOrEvict` | 已覆盖 |

## 8. 未覆盖范围

- victim 为 clean line，因此 dirty `WriteBackFull` 路径未覆盖。
- replacement retry、tag error、victim 带 L1 client 时的 rProbe 未覆盖。
- replacement policy 的算法内部决策没有由本波形逐语句覆盖；只确认其返回结果。
- 未提供工具生成的 RTL 覆盖率百分比。
