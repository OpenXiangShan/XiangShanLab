# Kunminghu V2 `cbo.inval` C9–C12 场景与波形分析报告

## 1. 结论

最终程序在 Kunminghu V2 上使用 Sv48 和 S-mode 虚拟地址，完整触发并验证了四个目标场景：

| 场景 | 最终判定 | 核心波形证据 |
|---|---|---|
| C9：DCache miss | 通过 | L2 仍记录 `clients=1` 并发 Probe；DCache MainPipe S1/S2 `tag_match=0`，S3 不写 meta，只回无数据 `NtoN ProbeAck`。 |
| C10：clean hit | 通过 | MainPipe S1 `tag_match=1, coh=Trunk(2)`；S3 把 coherence 写成 `Nothing(0)`，等价于 `meta.valid=false`，无数据写回。 |
| C11：dirty hit、无 alias set | 通过 | VA/PA 均为 color 0；S1 `Dirty(3)`，S3 写 `Nothing(0)`，两拍 `ProbeAckData`，随后 CHI `WriteBackFull`。 |
| C12：dirty hit、存在 alias set | 通过 | CBO operand 为 color 1，dirty line 位于 home VA color 0，PA natural color 为 3；L2 使用保存的 alias 0 Probe 到 DCache set 0，随后失效、`ProbeAckData` 和 CHI `WriteBackFull`。 |

最终仿真以 Good Trap 正常结束，无异常：DUT `cycleCnt=19,735`，`instrCnt=3,685`，seed 0。

本报告的波形结论由 `/home/yanyusong/wavekit` 开源 WaveKit 的 `FstReader` 直接解析 FST 得到，采样时钟为 `TOP.clock` 上升沿；FST time 等于本文 cycle 的两倍。

## 2. 交付物与最终制品

测试程序：

- [cbo-inval-scenario.c](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/cbo-inval-scenario.c:1)
- [Makefile](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/Makefile:1)
- [analyze_wave.py](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/analyze_wave.py:1)
- [final_evidence.py](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/final_evidence.py:1)

最终制品：

| 制品 | 路径/属性 |
|---|---|
| BIN | [cbo-inval-scenario-riscv64-xs.bin](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/build/cbo-inval-scenario-riscv64-xs.bin) |
| BIN 大小 | 9,376 bytes |
| BIN SHA-256 | `c1a892f6feb7b62ca1b32c9108778df0b2454759cbcc0af5dcb7ffee5ab89077` |
| ELF | [cbo-inval-scenario-riscv64-xs.elf](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/build/cbo-inval-scenario-riscv64-xs.elf) |
| 最终 FST | [2026-07-31-18-18-21.fst](/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-31-18-18-21.fst) |
| FST 大小 | 156,845,111 bytes |

## 3. 构建与仿真命令

最终源码使用用户指定的环境和构建命令：

```bash
cd /home/yanyusong/cbo-kmhv2
source env.sh && source /home/shared/shared-env.sh
cd /home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario
make ARCH=riscv64-xs
```

全波形仿真命令：

```bash
cd /home/yanyusong/cbo-kmhv2
./XiangShan/build/emu --no-diff --dump-wave-full \
  -i /home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/build/cbo-inval-scenario-riscv64-xs.bin
```

仿真结束摘要：

```text
PASS: C9-C12 functional postconditions
HIT GOOD TRAP at pc = 0x80000ba6
Core-0 instrCnt = 3,685, cycleCnt = 19,735
Seed=0 Guest cycle spent: 19,741
Host time spent: 126,306 ms
```

WaveKit 复现命令：

```bash
cd /home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario
PYTHONPATH=/home/yanyusong/wavekit/src \
  /home/yanyusong/wavekit/.venv/bin/python analyze_wave.py \
  /home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-31-18-18-21.fst

PYTHONPATH=/home/yanyusong/wavekit/src \
  /home/yanyusong/wavekit/.venv/bin/python final_evidence.py \
  /home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-31-18-18-21.fst
```

## 4. Sv48 稀疏页表

页表实现见 [build_page_tables()](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/cbo-inval-scenario.c:123)。`satp.MODE=9`，根页表物理地址为 `0x81000000`。

实现没有 `memset`，也没有遍历 512 项清零。它只写本测试实际会访问的 14 个 PTE：

- `root[0] -> shared_l2`；
- `shared_l2[1]`：`0x40000000` 的 1 GiB identity leaf，覆盖 UART；
- `shared_l2[2]`：`0x80000000` 的 1 GiB identity leaf，覆盖镜像、栈、页表和 C9 压力缓冲区；
- `shared_l2[36] -> test_l1`，`test_l1[0] -> test_l0`；
- `test_l0[0,4,8,12,13,256,260,264,268]`：C9–C12 和四个 C9 conflict page。

[map_test_page()](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/cbo-inval-scenario.c:55) 中的四次循环只写四个有效叶 PTE，不是清零逻辑。

程序只会取指或访存到上述已映射区域，页表遍历所经过的每一级表项都由测试显式写入；其余从不访问的表项是 don't-care。因此本实现不依赖页表页的其他内容，也不需要为了本测试清零未访问表项。

波形也直接确认了 Sv48，而不是只依据源码推断：cycle 6250 CSR 写入 `satp=0x9000000000081000`，cycle 6251 `satp.MODE` 从 0 变为 9、PPN 为 `0x81000`。在 C9–C12 的 accepted-decode 周期和最终 Good Trap 时，`satp.MODE=9`、TLB satp mode=9、privilege mode=1（S-mode）始终保持。

虚拟地址映射如下：

| 用途 | VA | PA | VA color `VA[13:12]` | PA natural color `PA[13:12]` |
|---|---:|---:|---:|---:|
| C9 | `0x900000000` | `0x87000000` | 0 | 0 |
| C10 | `0x900004000` | `0x85234000` | 0 | 0 |
| C11 | `0x900008000` | `0x83564000` | 0 | 0 |
| C12 home | `0x90000c000` | `0x819ab000` | 0 | 3 |
| C12 operand alias | `0x90000d000` | `0x819ab000` | 1 | 3 |

StoreUnit 的最终翻译证据：

| 场景 | TLB request cycle | TLB response cycle | 翻译结果 |
|---|---|---:|---|
| C9 | 13255、13261、13267 | 13268 | `0x900000000 -> 0x87000000` |
| C10 | 13609、13615、13621 | 13622 | `0x900004000 -> 0x85234000` |
| C11 | 13980 | 13981 | `0x900008000 -> 0x83564000` |
| C12 | 14371 | 14372 | `0x90000d000 -> 0x819ab000` |

C9/C10 的前三次 request 是首次 DTLB miss 后的正常 replay；最终均命中且 StoreUnit exception 为 0。

## 5. `cbo.inval` 与 CBIE 策略

程序中四个 marker 都是字面上的 `cbo.inval`，见 [C9 marker](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/cbo-inval-scenario.c:237) 和 [C10–C12 marker](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/cbo-inval-scenario.c:256)。

测试在 S-mode 使用虚拟地址。为满足 C11/C12 “dirty line 必须先 write back，再 invalidate”的要求，程序把 `menvcfg.CBIE` 设置为 `01`，见 [prepare_machine_state()](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/cbo-inval-scenario.c:97)。按照 RISC-V 特权架构规则，这会令较低特权级执行的 `cbo.inval` 使用 flush 语义；`CBIE=11` 才是可丢弃 dirty data 的 destructive invalidate。参考 [RISC-V CMO 规范](https://docs.riscv.org/reference/isa/unpriv/cmo.html) 和 [RISC-V 特权架构规范](https://docs.riscv.org/reference/isa/priv/machine.html)。

因此波形中预期看到：

- 提交指令仍为 `cbo.inval`；
- Decode 把内部 uop 转为 `cbo_flush`；
- StoreQueue CMO opcode 为 1；
- TileLink-A opcode 为 13；
- L2 `req_cbo_flush_s3=1`、`req_cbo_inval_s3=0`。

对应 RTL 是 [CSRDefines.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala:186)、[NewCSR.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala:1502) 和 [DecodeUnit.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1166)。

## 6. 指令、请求和提交总览

| 场景 | CBO PC / instruction | accepted Decode | SQ request | L2 CMO | SQ response | core precise commit / endpoint trace |
|---|---|---:|---:|---:|---:|---:|
| C9 | `0x8000097c / 0x0003200f` | 13249 | 13278 | 13292 | 13359 | 13372 / 13376 |
| C10 | `0x80000510 / 0x0007a00f` | 13595 | 13635 | 13640 | 13694 | 13707 / 13711 |
| C11 | `0x80000550 / 0x0007a00f` | 13966 | 13997 | 14002 | 14074 | 14087 / 14091 |
| C12 | `0x80000590 / 0x0007a00f` | 14357 | 14387 | 14392 | 14464 | 14477 / 14481 |

四次 accepted Decode 的 exception vector 都为 0，内部 `fuOpType=0xd`（`cbo_flush`）；四次 StoreUnit S2 exception 和 StoreQueue response 的 denied/corrupt 也均为 0。CMO 完成后出现的 pipeline redirect 是 [StoreQueue.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1055) 中 `flushPipe` 的预期排序行为，不是异常。

## 7. C9：DCache tag miss，NOP

C9 构造见 [c9_prefetch_and_invalidate()](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/cbo-inval-scenario.c:168)：

1. 先把 C9 target 和三个 conflict line 塞满同一四路 set；
2. 预先构造 16 个其他 set 的 dirty victim，并把最终替换行留在 L2；
3. 用 16 条 `prefetch.r` hint 异步产生两拍 dirty `ReleaseData`；
4. 再异步 prefetch 第四条 C9 conflict line，使 target 在本地被替换；
5. 紧邻执行 `cbo.inval target`。CBO 通过 TL-A 抢先到 L2，而 target 的 clean Release 仍堵在 TL-C，形成 L2 stale-client Probe 窗口。

关键事件：

| Cycle | 事件 | 结果 |
|---:|---|---|
| 13278 | StoreQueue/CMOUnit request | opcode 1，PA `0x87000000` |
| 13279 | DCache CMO TL-A | opcode 13 |
| 13292 | L2 CMO | dir hit，state 2，`clients=1`，`need_probe=1` |
| 13294 | L2 TL-B Probe | opcode 6，param 2 (toN) |
| 13296 | DCache ProbeQueue 接收 Probe | 同周期 conflict refill 将 target 本地替换，产生 clean voluntary WB |
| 13298 | target clean Release 发 TL-C | L2 到 cycle 13303 才处理，因此不影响已发出的 Probe |
| 13299 | ProbeQueue -> MainPipe | PA/VA `0x87000000` |
| 13300 | MainPipe S1 | `tag_match=0`，real-tag-eq=0，coh=0 |
| 13301 | MainPipe S2 | `tag_match=0` |
| 13314 | MainPipe S3 完成 | `probe_update_meta=0`，`io_meta_write_valid=0` |
| 13316 | DCache TL-C ProbeAck | opcode 4，param 5 (`NtoN`)，无数据 |
| 13355–13359 | L2 CBOAck -> SQ response | 正常完成 |
| 13372 / 13376 | core precise commit / endpoint trace | PC `0x8000097c` |

S3 在 13302–13313 因 WBQ ready 低而停顿，但整个停顿期间 tag miss 保持不变。最终没有对 target meta 或 data 做任何操作；只发送一致性协议要求的 `NtoN ProbeAck`，这就是要求的 DCache NOP。

## 8. C10：clean hit，清除 valid

关键事件：

| Cycle | 事件 | 结果 |
|---:|---|---|
| 13635 | SQ CMO request | PA `0x85234000` |
| 13640 | L2 CMO | dir hit，state 2，dirty 0，`clients=1`，`need_probe=1` |
| 13642 | L2 TL-B Probe | toN |
| 13644 | ProbeQueue 接收 Probe | PA `0x85234000` |
| 13647 | MainPipe S1 | `tag_match=1`，coh=`Trunk(2)`，即 clean |
| 13648 | MainPipe S2 | hit 保持 |
| 13649 | MainPipe S3/meta write | `probe_update_meta=1`，idx 0，wayOH 1，新 coh=`Nothing(0)` |
| 13649 | MainPipe -> WBQ | `dirty=0, hasData=0` |
| 13651 | TL-C ProbeAck | opcode 4，param 1 (`TtoN`)，无数据 |
| 13694 | SQ response | denied/corrupt 均为 0 |
| 13707 / 13711 | core precise commit / endpoint trace | PC `0x80000510` |

Kunminghu DCache 把 cache-line 有效性编码在 coherence meta 中；`Nothing(0)` 即 invalid。因此这里的 meta SRAM write valid 为 1，而写入 payload 的 line-valid 为 false，正对应用户要求的 `meta.valid = false`。

## 9. C11：dirty hit、无 alias，写回并失效

C11 选择 `VA[13:12]=PA[13:12]=0`，所以虚拟索引与 PA natural set 都是 set 0；L2 Probe 重建出的 virtual index 仍是 set 0，不需要转向另一个 alias set。Probe 波形里的 `vaddr=0x83564000` 高位来自 PA，只有 virtual-index/set 部分有意义。

| Cycle | 事件 | 结果 |
|---:|---|---|
| 13997 | SQ CMO request | PA `0x83564000` |
| 14002 | L2 CMO | dir hit，clients 1，alias 0，发 Probe |
| 14006 / 14008 | ProbeQueue / MainPipe request | paddr=`0x83564000`；probe vindex 为 set 0 |
| 14009 | MainPipe S1 | `tag_match=1`，coh=`Dirty(3)` |
| 14011 | MainPipe S3 | meta 写 `Nothing(0)`；`dirty=1, hasData=1` |
| 14013–14014 | TL-C | 两拍 opcode 5 `ProbeAckData` |
| 14021 | CHI TXREQ | opcode 27 `WriteBackFull`，addr `0x83564000` |
| 14033 | CHI RXRSP | opcode 5 `CompDBIDResp` |
| 14037 | CHI TXREQ | opcode 9 `CleanInvalid` |
| 14039–14040 | CHI TXDAT | 两拍 opcode 2 `CopyBackWrData` |
| 14065 | CHI RXRSP | opcode 4 `Comp` |
| 14073 / 14074 | TL-D CBOAck / SQ response | opcode 8，正常完成 |
| 14087 / 14091 | core 精确提交 / endpoint trace | PC `0x80000550` |

`ProbeAckData` 首拍低 64 bit 为测试写入值 `0xc110c110c110c110`。这条链同时证明 dirty 数据离开 L1、L1 meta 失效、数据经 CHI 写回，且系统级 CleanInvalid 完成。

## 10. C12：dirty hit、存在 alias set，写回、失效和 alias Probe

C12 先通过 `VA_C12_HOME=0x90000c000`（color 0）把 PA `0x819ab000` 写成 dirty，再通过从未提前访问过的 `VA_C12_ALIAS=0x90000d000`（color 1）执行 CBO，见 [run_c12()](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo-inval-scenario/cbo-inval-scenario.c:375)。

PA 的 natural color 是 3，而 dirty line 实际保存在 home VA color 0 的 DCache set 0。L2 directory 保存 alias 0，收到 CBO 后把 Probe 的 virtual index 重建到 set 0；波形显示的 probe `vaddr=0x819a8000` 中高位来自 PA、是 don't-care，只有 alias/index bits 有意义：

- reconstructed vaddr set：`(0x819a8000 >> 6) & 0xff = 0`；
- PA natural set：`(0x819ab000 >> 6) & 0xff = 0xc0`；
- CBO operand VA set：`(0x90000d000 >> 6) & 0xff = 0x40`。

因此 Probe 明确走的是“当前 cached alias set 0”，既不是 PA natural set `0xc0`，也不是 operand VA set `0x40`。C11 同样会因为 L2 `clients=1` 而收到 Probe；C12 的独特证据不是“出现 Probe”，也不能仅由两场景相同的 L2 `req_alias=0/meta.alias=0` 得出，而是由 home-store VA、CBO operand VA→PA 翻译和 Probe 重建出的 virtual set 联合证明。

C12 home store 的 StoreUnit 翻译在 cycle 14210 完成：`0x90000c000 -> 0x819ab000`；CBO operand 的翻译在 cycle 14372 完成：`0x90000d000 -> 0x819ab000`。两者同 PA、不同 virtual set。

| Cycle | 事件 | 结果 |
|---:|---|---|
| 14387 | SQ CMO request | operand VA 已翻译为 PA `0x819ab000` |
| 14392 | L2 CMO | dir hit，clients 1，保存的 alias 0，发 Probe |
| 14396 | ProbeQueue 接收 Probe | PA `0x819ab000` |
| 14398 | MainPipe request | paddr `0x819ab000`；probe `vaddr` signal=`0x819a8000`，vindex=set 0 |
| 14399 | MainPipe S1 | `tag_match=1`，coh=`Dirty(3)` |
| 14401 | MainPipe S3 | meta 写 `Nothing(0)`；`dirty=1, hasData=1` |
| 14403–14404 | TL-C | 两拍 opcode 5 `ProbeAckData` |
| 14411 | CHI TXREQ | opcode 27 `WriteBackFull`，addr `0x819ab000` |
| 14423 | CHI RXRSP | opcode 5 `CompDBIDResp` |
| 14427 | CHI TXREQ | opcode 9 `CleanInvalid` |
| 14429–14430 | CHI TXDAT | 两拍 opcode 2 `CopyBackWrData` |
| 14455 | CHI RXRSP | opcode 4 `Comp` |
| 14463 / 14464 | TL-D CBOAck / SQ response | opcode 8，正常完成 |
| 14477 / 14481 | core 精确提交 / endpoint trace | PC `0x80000590` |

`ProbeAckData` 首拍低 64 bit 为测试写入值 `0xc120c120c120c120`。

上述 CHI 表格周期取报告列出的 credit converter 本地接口握手（`Decoupled2LCredit_*.io_in` / `LCredit2Decoupled_*.io_out`）。实际 CHI link 上的对应握手为：C11 `WriteBackFull 14025 -> CompDBIDResp 14030 -> CleanInvalid 14041 -> CopyBackWrData 14043–14044 -> Comp 14062`；C12 为 `14415 -> 14420 -> 14431 -> 14433–14434 -> 14452`。两组观测点描述的是同一事务穿过 credit converter 前后的时序。

## 11. 关键波形信号

Sv48 / privilege：

```text
TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intExuBlock.exus_7.csr.csrMod.io_tlb_satp_MODE[3:0]
TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intExuBlock.exus_7.csr.csrMod.io_tlb_satp_PPN[43:0]
TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intExuBlock.exus_7.csr.csrMod.io_tlb_dmode[1:0]
TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intExuBlock.exus_7.csr.csrMod.diffCSRState_csr_satp[63:0]
TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intExuBlock.exus_7.csr.csrMod.diffCSRState_csr_privilegeMode[63:0]
```

StoreUnit / TLB / StoreQueue：

```text
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_StoreUnit_1.io_tlb_req_valid
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_StoreUnit_1.io_tlb_req_bits_fullva[63:0]
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_StoreUnit_1.io_tlb_resp_valid
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_StoreUnit_1.io_tlb_resp_bits_paddr_0[47:0]
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_lsq.storeQueue.io_cmoOpReq_valid
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_lsq.storeQueue.io_cmoOpReq_bits_opcode[2:0]
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_lsq.storeQueue.io_cmoOpReq_bits_address[63:0]
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_lsq.storeQueue.io_cmoOpResp_valid
```

L2 / SourceB：

```text
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mainPipe.task_s3_valid
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mainPipe.task_s3_bits_opcode[3:0]
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mainPipe.io_dirResp_s3_bits_hit
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mainPipe.io_dirResp_s3_bits_meta_clients
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mainPipe.io_dirResp_s3_bits_meta_alias[1:0]
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mainPipe.need_probe_s3_a
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mshrCtl.sourceB.io_sourceB_valid
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mshrCtl.sourceB.io_sourceB_bits_alias[1:0]
```

DCache Probe / MainPipe / WB：

```text
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.probeQueue.io_mem_probe_valid
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.probeQueue.io_pipe_req_bits_vaddr[49:0]
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.mainPipe.s1_tag_match
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.mainPipe.s1_has_real_tag_eq_way
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.mainPipe.s1_hit_coh_state[1:0]
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.mainPipe.probe_update_meta
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.mainPipe.io_meta_write_valid
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.mainPipe.io_meta_write_bits_meta_coh_state[1:0]
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.mainPipe.io_wb_bits_dirty
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.mainPipe.io_wb_bits_hasData
TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.auto_client_out_c_bits_opcode[2:0]
```

CHI / 完成：

```text
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.linkMonitor.Decoupled2LCredit_txreqLink.io_in_bits_opcode[6:0]
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.linkMonitor.Decoupled2LCredit_txreqLink.io_in_bits_addr[47:0]
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.linkMonitor.Decoupled2LCredit_txdatLink.io_in_bits_opcode[3:0]
TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.linkMonitor.LCredit2Decoupled_rxrsp.io_out_bits_opcode[4:0]
TOP.SimTop.endpoint.commit.io_bits_pc[63:0]
TOP.SimTop.endpoint.commit.io_bits_instr[31:0]
TOP.SimTop.endpoint.trap.io_bits_hasTrap
TOP.SimTop.endpoint.trap.io_bits_cycleCnt[63:0]
```

## 12. RTL 对照

- DCache S1 tag/meta-valid 匹配：[MainPipe.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:347)，lines 347–355。
- coherence `Nothing/Trunk/Dirty` 编码及 `isValid = state > Nothing`：[Metadata.scala](/home/yanyusong/cbo-kmhv2/XiangShan/rocket-chip/src/main/scala/tilelink/Metadata.scala:11)，lines 11–17、49–50。
- Decode 接受原始 `cbo.inval` 并依据 CBIE 转为内部 flush：[DecodeUnit.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:474)，lines 474–481、1175。
- StoreUnit 的 CBO TLB request/response、fault 和 S2 路径：[StoreUnit.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:215)，lines 215–234、302–323、397–417、463–545。
- StoreQueue 的 CMO request/response/WB/complete 状态机：[StoreQueue.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:824)，lines 824–850、987–1036、1055–1072。
- Sv48 mode 编码和 satp→TLB 输出：[CSRDefines.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala:165)，lines 165–173；[SupervisorLevel.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala:136)，lines 136–148、245–251；[NewCSR.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala:1388)，lines 1388–1396、1437–1442、1596–1603。
- Probe 只有 tag hit 且状态变化时才更新 meta：[MainPipe.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:560)，lines 560–568。
- dirty/needData 决定 Probe response 是否带数据：[MainPipe.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:729)，lines 729–742。
- meta SRAM 写入：[MainPipe.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:906)，lines 906–909。
- ProbeAck/ProbeAckData 进入 WBQ：[MainPipe.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:984)，lines 984–1005。
- ProbeQueue 用 L2 alias 重建虚拟 set：[Probe.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/Probe.scala:144)，lines 144–161。
- WBQ 选择 Release/ProbeAck 及数据形式：[WritebackQueue.scala](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala:226)，lines 226–270。
- L2 CBO 分类、client/alias 与 `need_probe`：[L2 MainPipe.scala](/home/yanyusong/cbo-kmhv2/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:153)，lines 153–256。
- L2 SourceB 携带 alias：[SourceB.scala](/home/yanyusong/cbo-kmhv2/XiangShan/coupledL2/src/main/scala/coupledL2/SourceB.scala:54)，lines 54–63。
- dirty CMO 选择 `WriteBackFull`：[MSHR.scala](/home/yanyusong/cbo-kmhv2/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:352)，lines 352–363。
- flush/invalidate 的 meta 和写回动作：[MSHR.scala](/home/yanyusong/cbo-kmhv2/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:525)，lines 525–556。
- CHI opcode 定义：[Opcode.scala](/home/yanyusong/cbo-kmhv2/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Opcode.scala:44)，包含 `CleanInvalid=9`、`WriteBackFull=27`、`CompDBIDResp=5`、`CopyBackWrData=2`。

## 13. 迭代记录与排除项

| 轮次 | 波形 | 结果/下一步 |
|---|---|---|
| attempt1 | `cbo-inval-scenario-attempt1.fst` | C10–C12 基本链路成立；C9 在 CBO 前已完成 clean Release，且 C11/C12 alias color 不可区分。 |
| attempt2 | `cbo-inval-scenario-attempt2.fst` | 改为固定稀疏 Sv48、C11 PA color 0；C10–C12 完整成立，C9 仍为 L2 `clients=0`。 |
| attempt3–5 | `cbo-inval-scenario-attempt3/4/5.fst` | 尝试 dirty WBQ 拥塞和调整 conflict load 位置；WBQ 峰值只有约 6/18，target Release 仍先到 L2。 |
| attempt6 | `2026-07-31-17-49-29.fst` | target conflict 放到最后，CBO 更近，但 clean Release 仍先清 clients。 |
| attempt7 | `2026-07-31-17-58-56.fst` | 引入异步 target `prefetch.r`；已产生本地 tag miss，但 target Release 比 CMO 到 L2 早约 10 cycles。 |
| attempt8 | `2026-07-31-18-07-06.fst` | demand-load dirty burst 确有多条 ReleaseData，但 CBO 必须等待更老 load，反而扩大窗口。 |
| attempt9 | `2026-07-31-18-18-21.fst` | 最终方案：用 software-prefetch dirty burst，使 refill/ReleaseData 异步继续而 ROB 不等待，四场景全部通过。 |

`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-31-17-46-32.fst` 是一次工具会话在仿真完成前被截断的 partial FST（无 CMO），未用于功能判断。最终结论只使用完整关闭并可由 WaveKit 解析的 attempt9 FST。
