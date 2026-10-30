# l1miss_l2miss_replace RTL 代码覆盖分析

## 1. 分析边界

本文描述由波形确认的 L2 replacement 行为路径，不等同于工具生成的 RTL
line/branch coverage。

RTL 基线：

- XiangShan：`kunminghu-v3`，commit `6705b63685d301f8c0960f7cbcc8b964660dc58e`
- XSCache 子模块：`e9488aed486358e3e5984b531aab601f9b91a9c0`

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
  -> 目标 GrantData、GrantAck、程序返回 0
```

## 3. DCache 与 L2 miss 分支

DCache miss 进入 MissQueue：
`src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:909-910`。

DCache `AcquireBlock` 生成：
`src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:987-1003`。

CoupledL2 miss 和 MSHR allocation：
`coupledL2/src/main/scala/coupledL2/MainPipe.scala:245-266,305-310`。

第 9 条访问实测目标 `AcquireBlock`（cycle 2093）、`dir_hit=0`、
`mshr_alloc_s3_valid=1`（cycle 2095），证明覆盖普通 miss 到 MSHR allocation 路径。
V3 波形在 L1 侧 A 端口没有观察到可用的目标请求记录，因此 A 通道证据取在 L2 入口
`slices_0.io_in_a_*`，并用 DCache missQueue 的 `io_misstrack_alloc_1_*`
（cycle 2095，`paddr=0x80124000`）补充 L1 miss 证据。

## 4. Replacement response 路由

源码：`coupledL2/src/main/scala/coupledL2/MSHRCtl.scala:127-151`。

当 MSHR 需要 replacement 时，SinkC 匹配使用 `metaTag` 而不是 `reqTag`：

```scala
val tag = Mux(status.needsRepl, status.metaTag, status.reqTag)
```

directory 的 replacement response 按 `mshrId` 路由给对应 MSHR：

```scala
m.io.replResp.valid := io.replResp.valid && io.replResp.bits.mshrId === i.U
```

波形实测 MSHR 0 的 `metaTag=0x10004`、victim way 0，证明 replacement response
已路由到目标 MSHR。

## 5. MSHR 接收 victim 并建立 replacement 状态

源码：`coupledL2/src/main/scala/coupledL2/MSHR.scala:1258-1291`。

非 retry replacement response 会执行：

```scala
state.w_replResp := true.B
dirResult.tag := replResp.tag
dirResult.way := replResp.way
dirResult.meta := replResp.meta
```

若 victim state 有效，则清除 release 完成状态，准备执行 victim release。状态导出
位于 `MSHR.scala:1315-1322,1336-1364`：

```scala
io.status.bits.reqTag := req.tag
io.status.bits.metaTag := dirResult.tag
io.status.bits.needsRepl := releaseNotSent
...
io.msInfo.bits.w_replResp := state.w_replResp
```

实测 cycle 2095 的 replacement response 为 victim tag `0x10004`、way 0、
state `TRUNK`、`retry=0`；cycle 2137–2149 `needsRepl=1`、`w_replResp=1`，
cycle 2150 `needsRepl` 变为 0。

## 6. 目标取数与 victim eviction

目标 miss 的 CHI 映射位于 `MSHR.scala:386-393`，NtoB 生成
`ReadNotSharedDirty`。波形实测目标地址 `0x80124000`、opcode `0x26`
（cycle 2097）。

victim release opcode 选择位于 `MSHR.scala:511-516`：

```scala
mp_release.chiOpcode.get := ParallelPriorityMux(Seq(
  isWriteBackFull       -> WriteBackFull,
  isWriteEvictFull      -> WriteEvictFull,
  isWriteEvictOrEvict   -> afterIssueEbOrElse(WriteEvictOrEvict, DontCare),
  isEvict /* Default */ -> Evict
))
```

本场景 victim 为 clean line，波形实测对 `0x80024000` 发出 opcode `0x42`
`WriteEvictOrEvict`（cycle 2153），证明覆盖该 victim eviction 分支，而不是空 way
refill；`WriteBackFull`（`0x4b`）没有出现。

## 7. 覆盖矩阵

| 模块 | 文件/行 | 分支或动作 | 状态 |
| --- | --- | --- | --- |
| DCache MainPipe | `MainPipe.scala:909-910` | miss 进入 MissQueue | 已覆盖 |
| DCache MissQueue | `MissQueue.scala:987-1003` | 目标生成 `AcquireBlock` | 已覆盖 |
| CoupledL2 MainPipe | `MainPipe.scala:245-266,305-310` | miss 并分配 MSHR | 已覆盖 |
| CoupledL2 MSHRCtl | `MSHRCtl.scala:127-151` | replacement response 路由 | 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:1258-1291` | 接收 victim tag/way/meta | 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:1315-1322,1336-1364` | 导出 needsRepl/w_replResp | 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:386-393` | 目标 `ReadNotSharedDirty` | 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:511-516` | victim `WriteEvictOrEvict` | 已覆盖 |

## 8. 未覆盖范围

- victim 为 clean line，因此 dirty `WriteBackFull` 路径未覆盖。
- replacement retry、tag error、victim 带 L1 client 时的 rProbe 未覆盖。
- replacement policy 的算法内部决策没有由本波形逐语句覆盖；只确认其返回结果。
- 当前 V3 构建未导出逐指令 commit 端。
- 未提供工具生成的 RTL 覆盖率百分比。
