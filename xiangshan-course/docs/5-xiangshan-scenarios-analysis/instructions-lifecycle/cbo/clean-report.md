# CBO Clean 演示与波形分析报告

## 结论摘要

本次演示**满足 CBO Clean 场景要求**：程序先对同一 64 B 缓存块执行可见的
写入和读取校验，再执行 `cbo.clean`，随后再次读取并写入该缓存块。仿真正常以
Good Trap 结束；波形中可见地址 `0x80001780`、opcode `0`（XiangShan 中为
`cbo.clean`）的 CMO 请求、TileLink A/D 通道往返、LSQ 响应和目标指令退休，且
`denied=0`、`corrupt=0`。

## RISC-V 指令语义与场景设计

在线查阅官方 *RISC-V ISA Reference, Cache Management Operations*（当前页面重定向至
ISA v20260120）的 CMO 章节后，采用 Zicbom 的定义：

- `cbo.clean offset(base)` 对 `rs1` 指定有效地址所在的一个缓存块执行 clean；可选
  offset 必须为零。
- clean 会在该块自上次 invalidate/clean/flush 后被 store 修改时，将修改后的副本写回
  到另一缓存或内存；该操作不是软件可见的“清零”。规范允许实现以效果不可区分的
  flush 替代 clean。

因此演示程序使用 64 B 对齐的 `volatile` 数组，避免编译器删除访存：

1. 用递增种子填满 8 个 64-bit word，并读取 word[0]、word[7] 与 checksum；这会在
   clean 前产生目标缓存块的 store 和 load。
2. 以数组首地址发射 `cbo.clean 0(%reg)`。
3. 再次读取相同 word/checksum，确认 clean 没有改变架构可见内容；随后覆盖两个 word
   并再次读取 checksum，形成 clean 后访存证据。

程序位于 [cbo_clean.c](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_clean/cbo_clean.c)，
构建参数位于 [Makefile](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_clean/Makefile)：
`MARCH` 包含 `zicbom`。

## 构建、运行与程序输出

构建命令：

```sh
source ~/cbo-kmhv2/env.sh
cd ~/cbo-kmhv2/nexus-am/apps/cbo_clean
make ARCH=riscv64-xs
```

生成镜像：

```text
/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_clean/build/cbo_clean-riscv64-xs.bin
```

按要求运行：

```sh
source ~/cbo-kmhv2/env.sh
cd ~/cbo-kmhv2/XiangShan
./build/emu --dump-wave-full --no-diff -i \
  /home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_clean/build/cbo_clean-riscv64-xs.bin
```

运行输出确认 clean 前后数据与 checksum 保持一致，随后 post-clean store 改写数据：

```text
target block: 0x80001780, bytes: 64
before cbo.clean: word[0]=0x1122334455667700 word[7]=0x1122334455667707 checksum=0x89119a22ab33b81c
after cbo.clean:  word[0]=0x1122334455667700 word[7]=0x1122334455667707 checksum=0x89119a22ab33b81c
after post-clean stores: word[0]=0xa500000000000000 word[7]=0xa500000000000007 checksum=0xb0c339a0066ca1c
Core 0: HIT GOOD TRAP at pc = 0x80000226
```

目标指令反汇编：

```text
800001ac: 0014200f  cbo.clean (s0)
```

## 波形文件与方法

- 波形：`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-12-11-26.fst`
- 大小：391 MiB。
- XiangShan 源码根：`/home/yanyusong/cbo-kmhv2/XiangShan`。
- 使用 `/home/yanyusong/wavekit` 开源库的 `FstReader` 解析 FST，并以 `TOP.clock`
  上升沿采样；顶层作用域为 `TOP`。
- 目标 PC：`0x800001ac`；目标缓存块：`0x80001780`。

## Wavekit 取证

XiangShan 将 `CBO_CLEAN` 译码为 `FuType.stu` 和 `LSUOpType.cbo_clean`，见
[DecodeUnit.scala:476](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:476)。
DCACHE 的 `CMOReq` 约定 `opcode=0` 为 clean，且请求携带 64-bit 地址，见
[DCacheWrapper.scala:619](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:619)。

在波形中查询
`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.missQueue.cmo_unit`
得到如下上升沿事件。`fire` 均按 `valid && ready` 判定。

| 周期 | 时间 | 证据 | 观测值 |
|---:|---:|---|---|
| 26792 | 53584 | `io_req.fire` | `opcode=0`，`address=0x80001780` |
| 26793 | 53586 | `io_req_chanA.fire` | TileLink A 请求有效且被接收；地址仍为 `0x80001780`，TL opcode 为 `12`（CBO Clean） |
| 26794–26863 | 53588–53726 | `state=2` | 等待 D 通道响应；此期间 CMOUnit 不接收第二个请求 |
| 26864 | 53728 | `io_resp_chanD.fire` | D 通道响应有效并被接收 |
| 26865 | 53730 | `io_resp_to_lsq.fire` | CMO 响应返回 LSQ，`denied=0`、`corrupt=0` |
| 26866 | 53732 | `state=0` | CMOUnit 回到 idle，`io_req_ready=1` |
| 26878 | 53756 | ROB/difftest commit | `PC=0x800001ac`、ROB index `66`、`skip=0`、`rfwen=0` |

CMOUnit 的四个状态编码和握手迁移由
[MissQueue.scala:308](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:308)
定义：`s_idle → s_sreq → s_wresp → s_lsq_resp → s_idle`。其关键实现为：

```scala
when (io.req.fire)       { state_next := s_sreq }
when (io.req_chanA.fire) { state_next := s_wresp }
when (io.resp_chanD.fire){ state_next := s_lsq_resp }
when (io.resp_to_lsq.fire) { state_next := s_idle }
```

对应的完整状态和 TileLink/LSQ 接口定义见
[MissQueue.scala:319](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:319)。
DCache 将 `io.cmoOpReq`/`io.cmoOpResp` 直连到 MissQueue，见
[DCacheWrapper.scala:1532](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1532)。

`cbo.clean` 从请求 fire 到 LSQ 响应 fire 相隔 73 个周期，并在响应后 13 个周期退休。
该指令没有整数目的寄存器写回（`rfwen=0`），符合 CBO Clean 的架构语义。

## 重定向检查与限制

在 CMO 事务窗口查询 CtrlBlock、dispatch 和 LSQ-enqueue 的
`io_redirect_valid` 时，周期 26872/26873 出现了 redirect。目标 `cbo.clean` 随后仍在
周期 26878 以 `skip=0` 正常退休，且 CMO 事务已在周期 26866 回到 idle，因此该 redirect
没有取消目标 CBO；但本次报告不将其归因于 CBO Clean 本身。若需要把该 redirect 精确关联到
另一条指令，应以其 ROB index/PC 再做独立追踪。

本演示验证的是**核心发出并完成 CMO clean 事务**以及 clean 前后同一缓存块的可见读写。
单核仿真没有引入非一致 DMA/外设观察者，因此不能仅凭该程序直接证明外部 agent 已看见
写回数据；该可见性是 CBO Clean 的规范目标，需在含非一致 agent 的系统级场景中继续验证。
