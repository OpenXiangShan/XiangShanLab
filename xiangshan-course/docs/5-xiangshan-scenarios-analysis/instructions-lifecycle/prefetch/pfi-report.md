# PREFETCH.I 演示与波形分析报告

## 方法与结论摘要

- **程序**：`/home/yanyusong/cbo-kmhv2/nexus-am/apps/prefetch_i/prefetch_i.c`。程序先读写 `before_data`，执行一次 `prefetch.i 0(a5)`，再读写 `after_data` 并调用被预取的 `prefetched_code()`。
- **目标指令**：PC `0x800001dc`，机器码 `0x0007e013`，反汇编为 `prefetch.i 0(a5)`；`a5=0x80000140`，即目标函数 `prefetched_code` 所在 ICache 行。
- **仿真命令**：在 `/home/yanyusong/cbo-kmhv2` 中先执行 `source env.sh`，再在 `XiangShan` 下运行：
  ```bash
  ./build/emu --dump-wave-full --no-diff -i /home/yanyusong/cbo-kmhv2/nexus-am/apps/prefetch_i/build/prefetch_i-riscv64-xs.bin
  ```
- **仿真结果**：输出 `HIT GOOD TRAP at pc = 0x80000234`，程序校验值为 `5a6e025a`。
- **波形**：`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-16-25-24.fst`（283 MiB）。
- **解析方法**：使用 wavekit 开源仓库 `/home/yanyusong/wavekit` 的 `FstReader` 解析 FST，并以 `TOP.clock` 上升沿采样、按 cycle 查询信号。
- **结论**：场景满足要求。`PREFETCH.I` 从 `LoadUnit_2` 向前端发出 `0x80000140` 软件指令预取，下一周期 ICache 以 `valid=ready=1` 接收。此次未产生 ICache MSHR 请求；计数器 `prefetch_req_receive_sw=1`、`prefetch_req_send_sw=0` 表明它走的是软件预取命中路径，无需下游 refill。

## 演示程序与构建

`prefetch_i.c` 使用 `volatile` 数组保证预取前后均有真实数据访存：

```c
sum = touch_data(before_data, 0x1000UL);
prefetch_instruction_line(prefetched_code);
sum += touch_data(after_data, 0x2000UL);
sum = prefetched_code(sum);
```

目标函数以 64 B 对齐并标记为 `noinline`，避免被内联；内联汇编固定生成 `prefetch.i 0(%reg)`。Makefile 为 `MARCH` 加入 `zicbop`。反汇编证据：

```text
800001d4: auipc a5,0x0
800001d8: addi  a5,a5,-148 # 80000140 <prefetched_code>
800001dc: prefetch.i 0(a5)
8000020e: jal   80000140 <prefetched_code>
```

## 全局时间线

| cycle | time | 事件与主要证据 |
|---:|---:|---|
| 22725 起 | 45450 起 | Decode lane 2 的 `pc=0x800001dc`，`isSoftPrefetch=1`、`isPreI=1`，与反汇编一致。|
| 22784 | 45568 | `memBlock.inner_LoadUnit_2.io_ifetchPrefetch.valid=1`，`vaddr=0x80000140`；同周期前端 `io_softPrefetch_2.valid=1`。|
| 22785 | 45570 | ICache `softPrefetchValid=1`；`prefetcher.io_req.valid=1`、`ready=1`、`isSoftPrefetch=1`，所以 `fire=1`。起始行 `0x80000140`，下一行 `0x80000180`。|
| 22786--22795 | 45572--45590 | 预取器内部 `s1_isSoftPrefetch`、`s2_isSoftPrefetch` 可见；`io_MSHRReq.valid=0`、`ready=1`。|
| 22780--22950 | 45560--45900 | `frontend_toFtq_redirect.valid=0`、`mem_redirect.valid=0`，没有该指令引起的 redirect。|
| 23656 | 47312 | ROB commit lane 3：`commitValid=1`、`pc=0x800001dc`、`instr=0x0007e013`、`robIdx=35`。|

## 逐级波形分析

### Decode、执行与前端交接

Decode lane 2 的 `isSoftPrefetch=1` 与 `isPreI=1` 证明该机器码被识别为 `PREFETCH.I`。LoadUnit 2 在 cycle 22784 将 `ifetchPrefetch` 输出置有效，并携带精确虚拟地址 `0x80000140`；同地址也出现在 `frontend.io_softPrefetch_2`：

```text
Decode (prefetch_i) -> LoadUnit_2.io_ifetchPrefetch
  -> Frontend.io_softPrefetch_2 -> ICache.softPrefetch
  -> ICache.prefetcher.io_req
```

该提示指令编码 `rd=x0`，无 GPR 写回；ROB 在 cycle 23656 正常提交。

### ICache / Prefetcher

cycle 22785 的 `req.valid=1 && req.ready=1` 是 ICache 接收软件预取的握手证据。`softPrefetchValid` 在该周期为 1、下周期清零，符合请求被消费的状态转换。

在 cycle 22780--22950 查询窗口中，`prefetcher.io_MSHRReq.valid` 始终为 0，而 `ready` 为 1；因此没有由 MSHR 资源阻塞导致的未发送请求。仿真末尾性能计数器为：

```text
prefetch_req_receive_sw = 1
prefetch_req_send_sw = 0
softPrefetch_drop_not_ready = 0
softPrefetch_drop_multi_req = 0
softPrefetch_block_ftq = 0
```

因此软件请求已进入 ICache 预取器，但无需生成下游 refill。该演示的目标函数和 `main` 位于相邻启动代码区域，目标行可能已由前端填入；本次验证的是“软件预取命中并被接收”的路径，而不是冷 ICache miss 路径。

## Redirect 与性能影响

波形证明该指令没有恢复流程：在发射、ICache 接收及后续窗口中，`frontend_toFtq_redirect.valid=0`、`mem_redirect.valid=0`。预取接口在 cycle 22785 立即完成 `fire`，没有 `valid && !ready` 反压；`io_MSHRReq.ready=1` 也表明下游可用。故未观测到可归因于该 `PREFETCH.I` 的 pipeline stall。

若需演示 miss/re-fill 路径，可将预取目标放到启动阶段从未取指的、更远的 64 B 对齐代码页，并在预取后插入独立计算窗口再调用该函数。

## 源码依据

### Decode：识别 `RS2=0` 的软件指令预取

[DecodeUnit.scala:1102](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1102>)：

```scala
val isSoftPrefetch = inst.OPCODE === BitPat("b0010011") && inst.FUNCT3 === BitPat("b110") && inst.RD === 0.U
val isPreW = isSoftPrefetch && inst.RS2 === 3.U(5.W)
val isPreR = isSoftPrefetch && inst.RS2 === 1.U(5.W)
val isPreI = isSoftPrefetch && inst.RS2 === 0.U(5.W)
```

[DecodeUnit.scala:1132](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1132>) 与 [DecodeUnit.scala:1170](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1170>) 将该指令送到 LDU，且赋为 `LSUOpType.prefetch_i`：

```scala
}.elsewhen (isPreW || isPreR || isPreI) {
  decodedInst.selImm := SelImm.IMM_S
  decodedInst.fuType := FuType.ldu.U
  decodedInst.canRobCompress := false.B
}
// ...
isPreI -> LSUOpType.prefetch_i,
```

### LoadUnit：产生 `ifetchPrefetch`

[LoadUnit.scala:639](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:639>) 与 [LoadUnit.scala:888](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:888>)：

```scala
out.prf_i := src.uop.fuOpType === LSUOpType.prefetch_i
// prefetch.i(Zicbop)
io.ifetchPrefetch.valid := RegNext(s0_src_select_vec(int_iss_idx) && s0_sel_src.prf_i)
io.ifetchPrefetch.bits.vaddr := RegEnable(
  s0_out.vaddr, 0.U, s0_src_select_vec(int_iss_idx) && s0_sel_src.prf_i)
```

这与 cycle 22784 的 `LoadUnit_2.io_ifetchPrefetch.valid=1, vaddr=0x80000140` 对应。

### ICache：缓存软件请求并赋予高优先级

[ICache.scala:665](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala:665>)：

```scala
when(io.softPrefetch.map(_.valid).reduce(_ || _)) {
  softPrefetchValid := true.B
  softPrefetch.fromSoftPrefetch(MuxCase(
    0.U.asTypeOf(new SoftIfetchPrefetchBundle),
    io.softPrefetch.map(req => req.valid -> req.bits)
  ))
}.elsewhen(prefetcher.io.req.fire) {
  softPrefetchValid := false.B
}
```

[ICache.scala:684](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala:684>)：

```scala
// software prefetch has higher priority
prefetcher.io.req.valid := softPrefetchValid || io.ftqPrefetch.req.valid
prefetcher.io.req.bits := Mux(softPrefetchValid, softPrefetch, ftqPrefetch)
io.ftqPrefetch.req.ready := prefetcher.io.req.ready && !softPrefetchValid
```

[ICache.scala:694](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala:694>) 将未命中的预取请求连接到 `missUnit.io.prefetch_req`；本次 `io_MSHRReq.valid=0`，没有走到该接口。

## 场景判定

| 检查项 | 结果 | 波形/运行证据 |
|---|---|---|
| 预取前存在缓存相关访存 | 通过 | `before_data` 的写后读循环和前置 `printf`。|
| 已插入 `PREFETCH.I` | 通过 | ELF 反汇编 `0x800001dc: prefetch.i 0(a5)`。|
| 预取目标地址正确 | 通过 | LDU 和 Frontend 均观测到 `0x80000140`。|
| ICache 接收软件预取 | 通过 | cycle 22785：`valid=1, ready=1, isSoftPrefetch=1`。|
| 预取后仍有访存/输出 | 通过 | `after_data` 循环、函数调用和最终 `printf`。|
| 无异常 / 正常完成 | 通过 | PREFETCH.I 在 ROB cycle 23656 提交；仿真最终 `HIT GOOD TRAP`。|

因此该演示程序和波形满足 PREFETCH.I 场景分析要求。
