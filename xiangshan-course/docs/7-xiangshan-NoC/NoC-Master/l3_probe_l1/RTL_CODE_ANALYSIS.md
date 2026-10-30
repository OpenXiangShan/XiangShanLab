# l3_probe_l1 RTL 代码覆盖分析

## 1. 分析边界

本文描述波形确认的 LLC 到 L2/L1 一致性路径，不等同于工具生成的 line/branch
coverage。

RTL 基线：

- XiangShan：`kunminghu-v3`，commit `6705b63685d301f8c0960f7cbcc8b964660dc58e`
- XSCache 子模块：`e9488aed486358e3e5984b531aab601f9b91a9c0`

## 2. 覆盖路径

```text
core 1 ReadNotSharedDirty
  -> OpenLLC client directory 检测 peer RN0 hit
  -> request_snoop_s4 = 1
  -> 生成指向 RN0 的 SnpNotSharedDirty
  -> core 0 CoupledL2 接收 snoop
  -> CoupledL2 判断需要 pProbe toB，分配 MSHR
  -> SourceB 向 core 0 L1 发出 TileLink Probe
  -> DCache ProbeQueue 接收并送入 MainPipe
  -> WritebackQueue 生成 ProbeAck
  -> core 0 L2 返回 CHI SnpResp
  -> OpenLLC ReadNoSnp 后向 core 1 返回 CompData
```

## 3. OpenLLC peer RN 命中与 snoop 决策

源码：`openLLC/src/main/scala/openLLC/MainPipe.scala:114`（peer RN hit）、
`MainPipe.scala:286-303`（peer RN vector）。

OpenLLC 排除请求发起者后形成 peer RN vector：

```scala
val peerRNs_valids_vec_s4 = VecInit(clients_valids_vec_s4.zipWithIndex.map { ... })
```

shared read 在 LLC self miss 时触发：

```scala
val request_snoop_s4 = (exclusiveReq_s4 || invalidReq_s4) && peerRNs_hit_s4 ||
  sharedReq_s4 && !self_hit_s4 || cleanReq_s4 && unique_peerRN_s4
val need_snoop_s4 = replace_snoop_s4 || request_snoop_s4
snp_s4.valid := task_s4.valid && need_snoop_s4
```

对 `ReadNotSharedDirty`，opcode 映射位于 `MainPipe.scala:340-355`，选择
`SnpNotSharedDirty`。波形实测 `snpVec=1/0`、`self_hit=0`、opcode 4、
txnID 81（cycle 1617），证明覆盖该路径。

## 4. OpenLLC 的 peer-hit 与内存读取

源码：`openLLC/MainPipe.scala:451-454`。

```scala
val memRead_s4 =
  (readNotSharedDirty_s4 || readUnique_s4) && !self_hit_s4 && !peerRNs_hit_s4 ||
  stashMissRead_s4
val mem_s4.valid := task_s4.valid && (memRead_s4 || memWrite_s4)
```

`memRead_s4` 的主条件要求 `!peerRNs_hit_s4`。本场景的 peer hit 由
`io_snoopTask_s4_bits_snpVec_0=1` 和 `self_hit=0` 证明。波形在 snoop/SnpResp
之后仍观察到一次目标地址 `0x80001680` 的 `ReadNoSnp`（cycle 1646，txnID 81）。
这说明本 V3 实现还在 snoop 完成后为 LLC 的目录/数据一致性发起了内存读取；
本文只把该事务记为实测事实，不把它归因到 peer-hit 的 `memRead_s4` 主条件。

## 5. CoupledL2 将 CHI snoop 转换为 L1 Probe

源码：`coupledL2/src/main/scala/coupledL2/MainPipe.scala:287-303`。

`SnpNotSharedDirty` 属于 snoop-to-B。若 L2 directory hit、状态为 TRUNK 且存在上层
client，则：

```scala
val need_pprobe_s3_b_snpToB = req_s3.fromB && (
  isSnpToB(req_s3.chiOpcode.get) ||
  req_s3.chiOpcode.get === SnpCleanShared
) && dirResult_s3.hit && metaOnHit_s3.state === TRUNK && metaOnHit_has_clients_s3
...
val need_mshr_s3_b = need_pprobe_s3_b || need_dct_s3_b
```

MSHR 的 SourceB task 构造位于
`coupledL2/src/main/scala/coupledL2/MSHR.scala:436-447`：

```scala
ob.opcode := Probe
ob.param := Mux(snpToB, toB, ...)
```

MSHRCtl 在 `MSHRCtl.scala:174-177` 仲裁 MSHR 的 `source_b` 并连接到 SourceB。
波形中 core 0 L1 收到 opcode 6、param 1、目标地址的 B-channel 请求（cycle 1628），
证明 CHI snoop 到 TileLink Probe 的转换路径已覆盖。

## 6. DCache ProbeQueue 接收 Probe

源码：`src/main/scala/xiangshan/cache/dcache/mainpipe/Probe.scala:144-164`。

ProbeQueue 从 TileLink B 接收请求，复制 source/opcode/address/param，并分配
ProbeEntry：

```scala
req.source := io.mem_probe.bits.source
req.opcode := io.mem_probe.bits.opcode
req.addr := io.mem_probe.bits.address
req.param := io.mem_probe.bits.param
io.mem_probe.ready := allocate
```

ProbeEntry 在 `Probe.scala:93-108` 将请求转成 main pipe 请求：

```scala
pipe_req.probe := true.B
pipe_req.probe_param := req.param
pipe_req.addr := req.addr
```

DCacheWrapper 的连接位于
`src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1134,1727-1728`，将
`bus.b` 接入 ProbeQueue 并将 `pipe_req` 接到 MainPipe。

波形中的 B-channel fire 证明 ProbeQueue 输入路径已覆盖。

## 7. DCache MainPipe 与 ProbeAck

MainPipe probe/writeback 路径位于
`src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:816-818` 和
`MainPipe.scala:1097-1108`：

```scala
val probe_wb = s3_req.probe
val need_wb = miss_wb || probe_wb || replace_wb
io.wb.valid := s3_valid && ... && need_wb
```

WritebackQueue 在
`src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala:226-270`
构造无数据和带数据两种 `ProbeAck`：

```scala
val probeResponse = edge.ProbeAck(..., reportPermissions = req.param)
val probeResponseData = edge.ProbeAck(..., data = beat_data(beat), ...)
io.mem_release.bits := Mux(req.voluntary,
  Mux(req.hasData, voluntaryReleaseData, voluntaryRelease),
  Mux(req.hasData, probeResponseData, probeResponse))
```

本场景实测 C opcode 4，即无数据 `ProbeAck`，没有走 opcode 5 `ProbeAckData`。
这与 toB 降权且 L1 无需上传数据的行为一致。

## 8. CHI SnpResp 完成

core 0 L2 最终发出 opcode 1 `SnpResp`（cycle 1641），txnID 81 与 LLC 发出的
snoop txnID 一致。该关联证明 ProbeAck 已被 CoupledL2 消费并完成 CHI snoop 响应，
而非停留在 L1 接口。core 1 随后收到 txnID 1024 的 `CompData` 和两拍
`GrantData`，数据为 `0x1122334455667788`。

## 9. 覆盖矩阵

| 模块 | 文件/行 | 分支或动作 | 状态 |
| --- | --- | --- | --- |
| OpenLLC MainPipe | `MainPipe.scala:114,286-303` | peer RN hit 形成 snoop vector | 已覆盖 |
| OpenLLC MainPipe | `MainPipe.scala:340-355` | 生成 `SnpNotSharedDirty` | 已覆盖 |
| OpenLLC MainPipe | `MainPipe.scala:451-454` | 内存读条件 | peer hit 分支已佐证；ReadNoSnp 为实测事务 |
| CoupledL2 MainPipe | `MainPipe.scala:287-303` | snoop-to-B 需要 pProbe | 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:436-447` | 构造 `Probe toB` | 已覆盖 |
| CoupledL2 MSHRCtl | `MSHRCtl.scala:174-177` | SourceB 仲裁 | 已覆盖 |
| DCache ProbeQueue | `Probe.scala:144-164` | 接收 B Probe 并入队 | 已覆盖 |
| DCache MainPipe | `MainPipe.scala:816-818,1097-1108` | probe 进入 writeback | 已覆盖 |
| DCache WritebackQueue | `WritebackQueue.scala:226-270` | 生成 `ProbeAck` | 无数据分支已覆盖 |

## 10. 未覆盖范围

- `ProbeAckData`、toN invalidation、dirty owner 和 forwarded snoop 未覆盖。
- ProbeQueue 被 LR/SC 或 MissQueue 阻塞的路径未覆盖。
- 多 peer RN snoop vector、LLC replacement snoop 未覆盖。
- LLC `ReadNoSnp` 与 snoop 完成之间的精确微架构原因未由波形逐信号证明；本文只记录
  事务级事实。
- 当前 V3 构建未导出逐指令 commit 端。
- 未提供工具生成的 RTL 覆盖率百分比。
