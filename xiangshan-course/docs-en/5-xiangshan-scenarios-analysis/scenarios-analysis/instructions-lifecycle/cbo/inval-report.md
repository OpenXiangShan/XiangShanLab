# CBO Inval 演示与波形分析报告

## 1. 结论

本场景满足 CBO Inval 演示要求：程序先读取一个已按 64 B 对齐的数据缓存块，执行
`cbo.inval`，再读取同一块并用校验和验证数据保持一致；前后均有 `printf` 输出。
XiangShan 波形证明该指令被解码为 `LSUOpType.cbo_inval`、以 CMO opcode `2` 向
DCache 发出地址为 `0x0000000080001780` 的请求、收到无错误响应并最终提交。

## 2. RISC-V CBO Inval 定义与场景设计

本报告阅读了 RISC-V 非特权 ISA 的 CMO/Zicbom 章节：
<https://docs.riscv.org/reference/isa/unpriv/cmo.html>。`cbo.inval` 使用 `rs1`
所给地址选择其所属的 cache block，并执行 invalidation；软件可传入块内任意地址，
硬件按缓存块粒度处理。因而演示程序特意向 `cbo.inval` 传入 `cache_block + 8`，
既验证块内非起始地址的语义，也便于从波形确认其被规整到块起始地址。

场景步骤如下：

1. `cache_block` 是一个 64 B 对齐、恰好 64 B 大小的静态 `volatile` 数据块，运行时地址为
   `0x80001780`。
2. `read_cache_block()` 在失效前读取全部八个 64-bit word 并计算校验和，形成缓存相关访问。
3. 执行 `cbo.inval(cache_block + 8)`；传入地址为 `0x80001788`，属于同一 cache block。
4. 再次读取该块并比较校验和；打印 `PASS` 表明失效操作没有改变架构可见的数据。

程序实现位于
[`/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_inval/cbo_inval.c:8`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_inval/cbo_inval.c#L8)，
内联汇编和前/后访问见
[`/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_inval/cbo_inval.c:29`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_inval/cbo_inval.c#L29)：

```c
static void cbo_inval(const void *address) {
  asm volatile("cbo.inval (%0)" : : "r"(address) : "memory");
}

before = read_cache_block();
cbo_inval((const void *)cache_block + sizeof(unsigned long));
after = read_cache_block();
```

## 3. 构建与仿真产物

先在 `~/cbo-kmhv2` 执行 `source env.sh`，然后构建：

```bash
make -C nexus-am/apps/cbo_inval ARCH=riscv64-xs \
  MARCH=rv64gc_zicbom LINUX_GNU_TOOLCHAIN=1
```

- 可执行镜像：`/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_inval/build/cbo_inval-riscv64-xs.bin`
- ELF：`/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_inval/build/cbo_inval-riscv64-xs.elf`
- 反汇编确认：PC `0x8000018a` 的指令字为 `0x0007a00f`，反汇编为 `cbo.inval (a5)`。

在 `~/cbo-kmhv2/XiangShan` 执行：

```bash
./build/emu --dump-wave-full --no-diff \
  -i /home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_inval/build/cbo_inval-riscv64-xs.bin
```

仿真结果为 `HIT GOOD TRAP`，程序输出：

```text
pre-inval reads complete, checksum = 0x866b486d0a6f4c61
cbo.inval completed for an address within the cached block
post-inval reads complete, checksum = 0x866b486d0a6f4c61
PASS: cache block was invalidated and data remained coherent
```

完整波形文件（FST，约 300 MiB）：

```text
/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-12-01-06.fst
```

## 4. Wavekit 分析方法

使用 `~/wavekit/.venv` 中的 Python 虚拟环境，并以
`PYTHONPATH=~/wavekit/src` 加载 Wavekit 的 `FstReader`。采样时钟为 `TOP.clock`，
选择上升沿（`sample_on_posedge=True`）；表中的 time 是 FST 的原始仿真时间值。
分析读取了前端/解码、分派、ROB 提交、StoreQueue CMO 请求/响应和重定向信号。

## 5. 目标指令周期证据

目标指令为 PC `0x8000018a`、bits `0x0007a00f`、`cbo.inval (a5)`；目标 ROB 为 `83`。

| 周期 | time | 边界/信号 | 波形证据 | 含义 |
|---:|---:|---|---|---|
| 21468 | 42936 | `decode.io_in_5` | `valid=1, ready=1, fire=1`；PC/bits 均匹配 | 解码输入完成握手 |
| 21468 | 42936 | `decode.io_out_5` | `valid=1, ready=1, fire=1` | 解码结果传向 rename/dispatch 路径 |
| 21470 | 42940 | `dispatch.io_fromRename_5` | `valid=1, ready=1, fire=1, rob=83, fuOp=0x0e` | 指令以稳定 ROB 身份进入分派 |
| 21470 | 42940 | `dispatch.io_enqRob_req_5` | `valid=1, rob=83, fuOp=0x0e` | ROB 接收该 CBO 指令 |
| 22370 | 44740 | `StoreQueue.io_cmoOpReq` | `valid=1, ready=1, fire=1, opcode=2, address=0x80001780` | 向 DCache 发出 CBO Inval 请求 |
| 22429 | 44858 | `StoreQueue.io_cmoOpResp` | `valid=1, ready=1, fire=1, denied=0, corrupt=0` | CMO 成功完成 |
| 22436 | 44872 | `backend.io_mem_redirect` | `valid=1, robIdx=83, level=0, isVlsException=0` | 与目标 ROB 对齐的内存完成/恢复重定向 |
| 22441 | 44882 | `io_frontend_toFtq_redirect` | `valid=1` | 前端收到恢复重定向 |
| 22442 | 44884 | `rob.difftest_commit` lane 0 | `valid=1, pc=0x8000018a, instr=0x0007a00f, rob=83, sq=19` | 架构提交 |

`decode.io_fromCSR_illegalInst_cboI`、`virtualInst_cboI`、`special_cboI2F` 在目标窗口
均为 `0`，因此该操作既未被拒绝为非法/虚拟化访问，也没有被降级为 `cbo.flush`。

## 6. 缓存操作与源码对应

解码表明确把 `CBO_INVAL` 映射到 store FU 与 `LSUOpType.cbo_inval`：
[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:476`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L476)。

```scala
CBO_INVAL -> XSDecode(SrcType.reg, SrcType.DC, SrcType.X,
  FuType.stu, LSUOpType.cbo_inval, SelImm.IMM_S)
```

StoreQueue 仅在可执行 CBO、StoreBuffer 已处理、且处于请求状态时令
`io.cmoOpReq.valid` 有效；地址来自 `cboMmioPAddr`：
[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1025`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1025)。

```scala
io.cmoOpReq.valid := deqCanDoCbo && cboFlushedSb &&
  (mmioState === s_req) && !io.wfi.wfiReq
io.cmoOpReq.bits.opcode  := cmoOpCode
io.cmoOpReq.bits.address := cboMmioPAddr
io.cmoOpResp.ready := deqCanDoCbo && (mmioState === s_resp)
```

DCache CMO interface 将 opcode `2` 定义为 `cbo.inval`：
[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:619`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L619)。

```scala
val opcode = UInt(3.W)   // 0-cbo.clean, 1-cbo.flush,
                         // 2-cbo.inval, 3-cbo.zero
val address = UInt(64.W)
```

因此波形中的 `opcode=2` 和规整后的 `address=0x80001780` 直接证明：程序传入的
`0x80001788` 作用于预期的 64 B 数据块，而不是其他 `printf` 访问的缓存行。

## 7. 停顿与正确性判定

- 从分派（21470）到 CMO request（22370）相隔 900 cycles；请求本身没有
  `valid && !ready` 反压。波形只能证明该指令在 StoreQueue/CMO 排序条件满足前等待；
  不能仅据源码把这 900 cycles 归因于某一个具体旧访存或 StoreBuffer 项。
- request 至 response 为 59 cycles，response 至 commit 为 13 cycles；响应无
  `denied` 或 `corrupt`，随后出现 ROB=83 对齐的内存和前端重定向并提交。
- 因使用 `--no-diff`，本次没有将外部参考模型的对拍结果作为证据；但 FST 中的
  ROB 提交 PC/指令、CMO 请求/响应及程序的前后校验和一致共同证明了本场景的行为。
- 该演示验证的是 **CBO Inval 请求、块地址规整、完成和架构数据不变**。若需要量化
  “失效后的首次读必然 DCache miss”，还应在后续场景中额外转储并关联该读的
  LoadQueue/DCache hit/miss/replay 信号。
