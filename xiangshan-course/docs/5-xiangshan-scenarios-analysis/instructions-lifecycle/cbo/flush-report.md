# CBO Flush 演示程序与场景分析报告

## 1. 结论摘要

- **场景结论：满足。** 程序对同一个 64B 对齐缓存块先进行 8 次写入和读校验，再执行 `cbo.flush`，随后再次读取校验并继续写入；这覆盖了 Flush 前的脏块访问、Flush 请求和 Flush 后重新访问三个阶段。
- **目标指令：** `0x80000194: 0024200f  cbo.flush (s0)`；`s0` 指向演示块 `0x800016c0`。该地址是 64B 对齐的缓存块起始地址。
- **波形结论：** FST 中 DCache CMO 接口在绝对波形时间 `24432` 出现 `valid=1 && ready=1`，操作码为 `001`、地址为 `0x800016c0`。香山定义 `001` 为 `cbo.flush`，因此目标 Flush 已到达 DCache 并被接受。
- **仿真结果：** 仿真输出 `HIT GOOD TRAP at pc = 0x80000204`，退出码为 0；程序前、执行中、后阶段的 `printf` 均输出。

本次分析遵循了 `analyze-xiangshan-wavekit` 技能的信号追踪流程，并使用本机 wavekit 开源仓库 `/home/yanyusong/wavekit` 尝试解析/查询 FST。该仓库依赖的 `pylibfst` 无法解析 Verilator 5.048 产生的 `VerilatedFst` 层级索引（`get_scopes_signals2` 失败）；因此 CMO 波形值采用同一 Verilator 5.048 源码树中的 FST API 只读提取。该兼容性限制不影响下列握手、操作码和地址证据，但阻止了用 wavekit 形成完整的前端到 ROB 的逐级 PC/ROB trace。

## 2. RISC-V CBO Flush 语义与场景设计

`cbo.flush` 是 Zicbom 的按缓存块操作：操作数地址选择其所在缓存块；对脏数据执行写回，并使该缓存块在本 hart 的缓存层次中失效。它不写通用寄存器。演示选择一个单独、64B 对齐的 `volatile uint64_t[8]`，避免与无关对象共享缓存块。

场景顺序如下：

1. `fill_block(first_seed)` 对 8 个 64-bit word 进行写入，产生该块的缓存写访问和潜在脏状态。
2. `checksum_block()` 对相同块逐字读取，确认 Flush 前已有内存访问；随后输出前阶段提示。
3. 执行 `cbo.flush 0(%0)`，内联汇编带有 `memory` clobber，阻止编译器跨越该指令重排普通内存访问。
4. 再次执行 `checksum_block()`，强制对同一缓存块重新读取；随后再写一遍并读取校验，形成 Flush 后访存阶段。
5. 比较前后 checksum，程序只有在数据不一致时才返回非零；本次仿真命中 good trap，表明校验通过。

联网规范核对说明：已按用户提供的 `172.38.10.247:8970` SOCKS5 代理尝试访问 RISC-V 官方 GitHub 规范源；代理可连接，但尝试的历史仓库路径返回 HTTP 404，因而没有把不可验证的远端文本作为本报告的引用依据。

## 3. 程序与二进制证据

应用目录：`/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_flush`。

- [`cbo_flush.c:8`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_flush/cbo_flush.c#L8) 将目标数组按 64B 对齐。
- [`cbo_flush.c:11`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_flush/cbo_flush.c#L11) 发射 `cbo.flush 0(%0)`；内存 clobber 保证前后 C 级访存不被重排越过该汇编语句。
- [`cbo_flush.c:36`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_flush/cbo_flush.c#L36) 至 [`cbo_flush.c:45`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_flush/cbo_flush.c#L45) 是前访问、Flush 和后访问主体。
- [`Makefile:1`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_flush/Makefile#L1) 使用 `MARCH=..._zicbom`，使 GNU 工具链接受该扩展。

```c
static volatile uint64_t demo_block[WORD_COUNT]
    __attribute__((aligned(CBO_BLOCK_BYTES)));

static inline void cbo_flush(void *address) {
  __asm__ volatile("cbo.flush 0(%0)" : : "r"(address) : "memory");
}

fill_block(first_seed);
checksum_before_flush = checksum_block();
printf("CBO Flush demo: pre-accesses complete\n");
printf("CBO Flush: execute\n");
cbo_flush((void *)demo_block);
checksum_after_flush = checksum_block();
fill_block(second_seed);
(void)checksum_block();
```

`riscv64-linux-gnu-objdump -d` 的关键片段：

```text
80000190:  1a2010ef  jal       80001332 <printf_>
80000194:  0024200f  cbo.flush (s0)
80000198:  4481      li        s1,0
8000019e:  2087e733  sh3add    a4,a5,s0
800001a2:  6318      ld        a4,0(a4)
```

紧随 `cbo.flush` 的 `ld` 循环就是 Flush 后 checksum 的实际读访问。

## 4. 仿真执行记录

先在 `~/cbo-kmhv2` 执行 `source env.sh`，构建命令为：

```bash
make -C nexus-am/apps/cbo_flush ARCH=riscv64-xs
```

随后在 `~/cbo-kmhv2/XiangShan` 执行：

```bash
./build/emu --dump-wave-full --no-diff \
  -i /home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_flush/build/cbo_flush-riscv64-xs.bin
```

结果：

| 项目 | 值 |
|---|---|
| 输入镜像 | `/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_flush/build/cbo_flush-riscv64-xs.bin` |
| 完整波形 | `/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-12-23-35.fst` |
| 仿真结束 | `HIT GOOD TRAP at pc = 0x80000204` |
| 指令数 | 2,705 |
| 核心周期数 | 17,599 |
| 波形结束时间 | 35,309 |
| 程序输出 | `pre-accesses complete`、`execute`、`post-accesses complete` |

波形每个时钟边沿倾倒一次：绝对波形时间是边沿编号，近似核心周期为 `time / 2`。下文的 `24432` 对应约第 `12216` 个核心周期的高电平侧采样点。

## 5. CMO/DCache 波形分析

DCache 相关接口为 `TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.io_cmoOpReq`。FST 值变化如下：

| 波形时间 | 近似周期 | 信号变化 | 解释 |
|---:|---:|---|---|
| 24310 | 12155 | `bits.address=0x800016c0`，`bits.opcode=001` | 请求负载已准备：目标是演示数组所在缓存块，opcode 指向 Flush。|
| 24432 | 12216 | `valid=1`，`ready=1` | Decoupled 握手发生，`fire=1`；DCache 接受 CMO Flush 请求。|
| 24434 | 12217 | `valid=0`，`ready=0` | 请求被消费，接口进入忙/反压阶段。|
| 24588 | 12294 | `ready=1` | 接口重新可接受后续 CMO 请求。|

这段波形满足场景分析的核心要求：

- **操作类型正确：** `opcode=001`，而 [`DCacheWrapper.scala:619`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L619) 明确编码 `0-clean, 1-flush, 2-inval, 3-zero`。
- **地址正确：** 波形 `0x800016c0` 与 `demo_block` 的 64B 对齐目标块一致。
- **握手正确：** `valid && ready` 在时间 `24432` 同时为 1，不是只看见组合负载而未被缓存接收。
- **后续忙碌可见：** 接收后 `ready` 低至时间 `24588`，说明 CMO 接口没有把操作当作零延迟空操作处理。

由于当前 FST reader 兼容性问题，不能可靠地从波形给出该条指令的完整 FTQ/ROB 编号、DCache 内部 FSM 名称、写回完成时间或 post-flush load 的命中/缺失位；这些项目必须使用支持 Verilator 5.048 FST 的 wavekit/pylibfst 后重新导出。已检查的 CMO 请求接口足以证明目标指令进入并被 DCache 接收，但不应把 `ready` 的恢复时间误写成“DRAM 写回完成周期”。

## 6. 香山源码路径与因果映射

### Decode -> Store/CMO 通路

[`DecodeUnit.scala:476`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L476) 将 `CBO_FLUSH` 解码为 `FuType.stu` 和 `LSUOpType.cbo_flush`：

```scala
CBO_FLUSH -> XSDecode(SrcType.reg, SrcType.DC, SrcType.X,
  FuType.stu, LSUOpType.cbo_flush, SelImm.IMM_S)
```

[`package.scala:589`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/package.scala#L589) 将内部 CBO 子操作标识为 `cbo_flush = b1101`，并由 `isCboFlush` 识别。

### StoreQueue -> DCache CMO 请求

[`StoreQueue.scala:1025`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1025) 只有在 CBO 可出队、StoreBuffer 已经被 Flush、状态为 `s_req` 且非 WFI 时，才拉高 `io.cmoOpReq.valid`；同段把 `cmoOpCode` 和 `cboMmioPAddr` 赋给请求负载：

```scala
io.cmoOpReq.valid := deqCanDoCbo && cboFlushedSb &&
  (mmioState === s_req) && !io.wfi.wfiReq
io.cmoOpReq.bits.opcode  := cmoOpCode
io.cmoOpReq.bits.address := cboMmioPAddr
```

[`StoreQueue.scala:1033`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1033) 先向 StoreBuffer 发起 flush；[`StoreQueue.scala:1060`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1060) 给 CBO 的写回 uop 设置 `flushPipe`，用以维持 CMO 的顺序性。这解释了为何本程序在执行 CBO 前安排了写和读：它不仅触发目标块缓存访问，也让 StoreQueue/StoreBuffer 的 CMO 顺序路径具备实际工作负载。

## 7. Redirect、Bubble 与架构态

- **Redirect：** `cbo.flush` 不是控制流指令；在本次可解析的 CMO 证据中没有 target PC、异常或 redirect 信号。由于 wavekit 无法加载该 FST 的完整层级，不能将“未观察到”升级为对所有 ROB/Frontend redirect 信号的形式化证明。
- **Bubble/反压：** CMO 接口在 `24434..24587` 之间 `ready=0`，长度为 154 个边沿倾倒时间（约 77 核心周期）。这表明 DCache 的 CMO 接收端在请求消费后存在可见的接口级反压；具体归因到哪一个 DCache FSM 或下级写回资源，当前兼容性限制下不可断言。
- **架构态：** 该指令没有整数、浮点或向量目的寄存器。程序的 checksum 比较最终返回 0，结合 `HIT GOOD TRAP` 证明 Flush 后读取的数据与 Flush 前一致。`--no-diff` 关闭了参考模型对比，因此不存在可报告的 difftest 提交寄存器对照记录。

## 8. 后续建议

1. 将 `/home/yanyusong/wavekit` 的 `pylibfst` 升级为能读取 Verilator 5.048 FST 的版本，或用支持该格式的 GTKWave/FST reader 重新导出为 VCD；随后可按 wavekit 技能补齐 FTQ、ROB、LSQ、FSM 与 redirect 的逐周期追踪。
2. 若需要证明脏块“回写到外部存储”而不仅是 DCache 接收 Flush，可在重新可读的波形中追踪 DCache CMO response、L2/TileLink 请求和写回通道，并对照地址 `0x800016c0`。
3. 当前程序可直接作为课程场景入口；建议用本报告记录的波形路径复现并继续扩展分析。
