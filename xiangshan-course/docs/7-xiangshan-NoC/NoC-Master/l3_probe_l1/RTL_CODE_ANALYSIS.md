# l3_probe_l1 RTL 代码覆盖分析

## 1. 分析边界

本文描述波形确认的 LLC 到 L2/L1 一致性路径，不等同于工具生成的 line/branch
coverage。RTL 基线为 XiangShan commit
`53ace33fd6b9c7c4289bb4751e3bb6ac3348075f`。

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
  -> core 1 获得 GrantData
```

## 3. OpenLLC peer RN 命中与 snoop 决策

源码：
`openLLC/src/main/scala/openLLC/MainPipe.scala:269-344`。

OpenLLC 排除请求发起者后形成 peer RN vector：

```scala
val peerRNs_valids_vec_s4 = ... Mux(i.U === srcID_s4, false.B, valid)
```

shared read 在 LLC self miss 时触发：

```scala
val request_snoop_s4 = ... || sharedReq_s4 && !self_hit_s4 || ...
val need_snoop_s4 = replace_snoop_s4 || request_snoop_s4
snp_s4.valid := task_s4.valid && need_snoop_s4
```

对 `ReadNotSharedDirty`，opcode 映射在 `MainPipe.scala:321-335` 选择
`SnpNotSharedDirty`。波形实测 `peerRNs_hit_s4=1`、`request_snoop_s4=1`、
`snoop_vec=1/0`、opcode 4，证明覆盖该路径。

## 4. OpenLLC 避免访问内存的 peer-hit 分支

源码：`openLLC/MainPipe.scala:429-432`。

```scala
val memRead_s4 =
  (readNotSharedDirty_s4 || readUnique_s4) &&
  !self_hit_s4 && !peerRNs_hit_s4
```

本场景 `peerRNs_hit_s4=1`，所以普通内存 `ReadNoSnp` 条件为 false，而是通过 peer
snoop 取得一致性响应。这一分支由 peer hit 与后续 snoop 链共同佐证。

## 5. CoupledL2 将 CHI snoop 转换为 L1 Probe

源码：
`coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:258-293`。

`SnpNotSharedDirty` 属于 snoop-to-B。若 L2 directory hit、状态为 TRUNK 且存在上层
client，则：

```scala
val need_pprobe_s3_b_snpToB = req_s3.fromB &&
  isSnpToB(...) && dirResult_s3.hit &&
  meta_s3.state === TRUNK && meta_has_clients_s3
val need_mshr_s3_b = need_pprobe_s3_b || need_dct_s3_b
```

MSHR 的 SourceB task 构造位于
`coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:430-451`：

```scala
ob.opcode := Probe
ob.param := Mux(snpToB, toB, ...)
```

MSHRCtl 在 `MSHRCtl.scala:174-178` 仲裁 MSHR 的 `source_b` 并连接到 SourceB。波形中
core 0 L1 收到 opcode 6、param 1、目标地址的 B-channel 请求，证明 CHI snoop 到
TileLink Probe 的转换路径已覆盖。

## 6. DCache ProbeQueue 接收 Probe

源码：
`src/main/scala/xiangshan/cache/dcache/mainpipe/Probe.scala:128-225`。

ProbeQueue 从 TileLink B 接收请求，复制 source/opcode/address/param，并分配 ProbeEntry：

```scala
req.opcode := io.mem_probe.bits.opcode
req.addr := io.mem_probe.bits.address
req.param := io.mem_probe.bits.param
io.mem_probe.ready := allocate
```

ProbeEntry 在 `Probe.scala:93-112` 将请求转成 main pipe 请求：

```scala
pipe_req.probe := true.B
pipe_req.probe_param := req.param
pipe_req.addr := req.addr
```

DCacheWrapper 的连接位于
`src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1575-1585`，将 `bus.b`
接入 ProbeQueue 并将 `pipe_req` 接到 MainPipe。

波形中的 B-channel fire 证明 ProbeQueue 输入路径已覆盖。

## 7. DCache MainPipe 与 ProbeAck

MainPipe probe/writeback 路径位于
`src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:729-753` 和
`MainPipe.scala:984-1003`：

```scala
val probe_wb = s3_req.probe
val need_wb = miss_wb || probe_wb || replace_wb
io.wb.valid := ... s3_req.probe ... && need_wb
```

WritebackQueue 在
`src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala:140-147` 定义
ProbeAck 状态流，并在 `WritebackQueue.scala:226-241` 构造无数据和带数据两种
`ProbeAck`。

本场景实测 C opcode 4，即无数据 `ProbeAck`，没有走 opcode 5 `ProbeAckData`。这与
toB 降权且 L1 无需上传数据的行为一致。

## 8. CHI SnpResp 完成

core 0 L2 最终发出 opcode 1 `SnpResp`，txnID 与 LLC 发出的 snoop txnID 1041 一致。
该关联证明 ProbeAck 已被 CoupledL2 消费并完成 CHI snoop 响应，而非停留在 L1 接口。

## 9. 覆盖矩阵

| 模块 | 文件/行 | 分支或动作 | 状态 |
| --- | --- | --- | --- |
| OpenLLC MainPipe | `MainPipe.scala:269-344` | peer RN hit 生成 snoop | 已覆盖 |
| OpenLLC MainPipe | `MainPipe.scala:321-335` | 生成 `SnpNotSharedDirty` | 已覆盖 |
| OpenLLC MainPipe | `MainPipe.scala:429-432` | peer hit 阻止 memory read | 行为已佐证 |
| CoupledL2 MainPipe | `MainPipe.scala:258-293` | snoop-to-B 需要 pProbe | 已覆盖 |
| CoupledL2 MSHR | `MSHR.scala:430-451` | 构造 `Probe toB` | 已覆盖 |
| CoupledL2 MSHRCtl | `MSHRCtl.scala:174-178` | SourceB 仲裁 | 已覆盖 |
| DCache ProbeQueue | `Probe.scala:128-225` | 接收 B Probe 并入队 | 已覆盖 |
| DCache MainPipe | `MainPipe.scala:729-753,984-1003` | probe 进入 writeback | 已覆盖 |
| DCache WritebackQueue | `WritebackQueue.scala:226-241` | 生成 `ProbeAck` | 无数据分支已覆盖 |

## 10. 未覆盖范围

- `ProbeAckData`、toN invalidation、dirty owner 和 forwarded snoop 未覆盖。
- ProbeQueue 被 LR/SC 或 MissQueue 阻塞的路径未覆盖。
- 多 peer RN snoop vector、LLC replacement snoop 和内存回退路径未覆盖。
- 未提供工具生成的 RTL 覆盖率百分比。
