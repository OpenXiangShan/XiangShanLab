# XiangShan #3844：非对齐 MMIO 存储的异常码不一致

## 1. 问题与结论

[Issue #3844](https://github.com/OpenXiangShan/XiangShan/issues/3844) 报告：执行 `fsh ft0,9(sp)` 或 `flh ft0,9(sp)` 时，XiangShan 与 NEMU 的 `mcause` 不一致。本地程序使用 `fsh`，失败结果为：

| 项目 | XiangShan | NEMU |
| --- | --- | --- |
| 故障指令 | `0x80000016: fsh ft0,9(sp)` | 相同 |
| 访问地址 | `0x9` | `0x9` |
| `mcause` | `6`，Store/AMO address misaligned | `7`，Store/AMO access fault |
| `mepc` / `mtval` | `0x80000016` / `0x9` | 相同 |

**根因是旧 NEMU 把“属于 MMIO 地址空间”和“存在已注册设备”混为同一个判断。** XiangShan 将地址 `0x9` 识别为 MMIO，并对该非对齐存储上报 cause 6；旧 NEMU 因该地址没有设备映射，没有执行 MMIO 非对齐检查，而是进入无效物理写分支，产生 cause 7。

以下按指令执行路径解释这一差异。DUT 的地址、MMIO 判定及异常写回均有 [replay.fst](replay.fst) 的实际采样支持；NEMU 的路径由 [replay.log](replay.log) 和对应版本源码共同确定。报告者也提到了 `flh`，但本地没有该指令的动态证据，本文不将 store 路径结论直接套到 load 路径。

## 2. 触发条件：地址为 0x9 的两字节存储

本地源码版本与 issue 给出的版本一致：XiangShan 为 `7af39ad2ddb1305b2c4ddf4c3a9663a7c3615fa6`，NEMU 为 `34ba2259558ed89f5a042179ef0a9131e53ce037`。复现脚本采用 `DefaultConfig`；原 issue 未注明硬件配置，该配置是本地重建选择。

[repro.S](case/repro.S) 来自 [NEMU #644](https://github.com/OpenXiangShan/NEMU/issues/644) 的最小程序，核心内容如下，末尾另加自循环：

```asm
li      t0, 0x8000000a00104a00
csrw    mstatus, t0
fsh     ft0, 9(sp)
```

[ELF 反汇编](case/repro.dump) 显示，`csrw mstatus,t0` 位于 `0x80000012`，`fsh` 位于 `0x80000016`，机器码为 `0x000114a7`。日志第 113 行虽然打印 `mcause different at pc=0x80000012`，但那是上一提交组的 PC；[日志第 40 行](replay.log#L40) 明确记录故障指令在 `0x80000016`。不能把这次失败归因于前一条 CSR 写指令。

程序没有显式初始化 `sp`，所以仅凭汇编不能确定有效地址。实际波形补足了这一条件：

> **E1，cycle 8344 / tick 16688：** StoreUnit 输入的 `io_stin_valid=1、io_stin_ready=1`，`io_stin_bits_src_0=0`，`io_stin_bits_uop_imm=9`，`io_stin_bits_uop_fuOpType=1`，`s0_vaddr=9`，ROB 标识为 `(flag=0,index=6)`。输入检查 C1 为 **PASS**。[REF 寄存器输出](replay.log#L43) 也显示 `sp=0`。

这里的 StoreUnit 为 `TOP.SimTop.l_soc.core_with_l2.core.memBlock.inner_StoreUnit_0`，下文缩写为 `ST`。`src_0` 宽 64 bit，`imm` 为 32 bit，`fuOpType` 为 9 bit，`vaddr` 为 50 bit，ROB flag/index 分别为 1/8 bit，valid/ready 为 1 bit。

[DecodeUnit.scala:392](xs-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L392) 将 `FSH` 解码为 `FuType.stu / LSUOpType.sh / Imm_S`。因此波形中的 `fuOpType=1` 是 halfword store，访问字节为 `0x9..0xa`；地址 bit 0 为 1，确实没有按两字节对齐。该触发条件不依赖浮点寄存器内容。

上述及后续事件均来自 Wavekit 对 `TOP.clock` 上升沿的采样，cycle 是从 FST 首个采样沿起算的绝对序号。主窗口 `[16500,16730)` ticks 包含 115 个样本，覆盖 cycle 8250..8364；事件期间 top、core、buffer 和 CSR reset 均为 0。61 条所选信号的值与独立 X/Z 掩码按相同窗口提取，时间轴一致，掩码全为零。FST 单位为 1 ps，但时间戳是仿真器按周期生成的 dump ticks，以下保留 tick 表示，不将其解释为物理运行频率。

## 3. XiangShan 为什么产生 cause 6

### 3.1 首次访问同时被识别为非对齐和 MMIO

[StoreUnit.scala:226](xs-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala#L226) 根据访问大小检查对齐。对 halfword，地址 bit 0 不为零就设置 `storeAddrMisaligned`，即 exceptionVec 的 bit 6。波形与这一判断一致：

| 证据 | cycle / tick | ST 中的有效状态 |
| --- | --- | --- |
| E2a | 8345 / 16690 | `s1_valid=1, s1_in_vaddr=9, s1_in_uop_exceptionVec_6=1, s1_in_uop_robIdx_value=6` |
| E8a | 8346 / 16692 | `s2_valid=1, s2_in_vaddr=9, io_pmp_mmio=1, io_pmp_st=0, s2_mmio=1, s2_out_mmio=0` |

表中地址宽 50 bit，ROB index 为 8 bit，其余为 1 bit。E2a 证明原始请求确实触发了非对齐检测；E8a 则说明它的内存属性是 **MMIO，但没有 PMP/PMA store access fault**。

这两个结果并不矛盾。[PMA.scala:143](xs-env/XiangShan/src/main/scala/xiangshan/backend/fu/PMA.scala#L143) 的默认 PMA 表对 `[0,0x10000000)` 设置读写许可，但不设置 cacheable；[214 行](xs-env/XiangShan/src/main/scala/xiangshan/backend/fu/PMA.scala#L214) 分别以写权限生成 `st`、以 `!cacheable` 生成 `mmio`。因此对地址 `0x9`，`mmio=1` 不意味着 `st=1`。

E8a 中 `s2_out_mmio=0` 也不能用于否定 MMIO 属性。[StoreUnit.scala:405](xs-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala#L405) 的关系是：

```text
s2_out.mmio = s2_mmio && !s2_exception
```

首次访问已经带有非对齐异常，所以输出 MMIO 标志被屏蔽，内部的 `s2_mmio` 仍为 1。此时硬件非对齐处理接管请求，并非立即把这次 S2 的结果直接作为最终异常提交。

### 3.2 非对齐缓冲区进行对齐探测

[StoreMisalignBuffer.scala:148](xs-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala#L148) 等待请求与 ROB 头部 pending store 匹配后处理。原始访问的两个字节 `0x9..0xa` 位于同一 16-byte 区域，故走 [262 行](xs-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala#L262) 的单请求路径：将探测地址对齐到 `0x0`，设置 `mask=0xffff、is128bit=1`，并清除探测请求中的非对齐位。

对应波形如下。`BUF` 表示 `TOP.SimTop.l_soc.core_with_l2.core.memBlock.inner_storeMisalignBuffer`。

| 证据 | cycle / tick | 实际值 |
| --- | --- | --- |
| EB | 8347..8348 / 16694..16696 | `BUF.req_valid=1, req_vaddr=9, req_uop_robIdx_flag=0, req_uop_robIdx_value=6`；`bufferState` 从 idle=0 到 split=1 |
| E3 | 8349 / 16698 | `BUF.bufferState=2`（req）；`io_splitStoreReq_valid=1, io_splitStoreReq_ready=1`；请求 `vaddr=0, mask=0xffff, is128bit=1, uop_fuOpType=1`，ROB 仍为 `(0,6)` |
| E2b | 8350 / 16700 | `ST.s1_valid=1, s1_in_vaddr=0, s1_in_uop_exceptionVec_6=0, s1_in_uop_robIdx_value=6`；`BUF.bufferState=3`（resp） |

E3 的请求字段前缀为 `BUF.io_splitStoreReq_bits_`；`vaddr/mask/is128bit/uop_fuOpType` 分别宽 50/16/1/9 bit，`bufferState` 为 3 bit。检查 C2 为 **PASS**。

[emu.cpp:623](xs-env/XiangShan/difftest/src/test/csrc/verilator/emu.cpp#L623) 在置高时钟并完成 `eval()` 后才 dump，因此表中是沿后状态。E3 的 valid/ready 同高表示请求可在下一上升沿接收，E2b 中 StoreUnit 的有效请求和 buffer 转入 resp 状态确认了这一接收，不能把沿后新出现的 valid 倒算成同一沿已发生的握手。

这里 `is128bit=1` 是内部探测形式，**不表示 `fsh` 被错误解码为实际的 16-byte 存储**。原始 uop 的 `fuOpType` 和 ROB 标识没有变化，最终异常仍对应地址 `0x9` 的 halfword store。

### 3.3 MMIO 响应使缓冲区重新生成非对齐异常

对齐探测清除了非对齐位，但没有改变地址所属的 MMIO 区域。两周期后的响应为：

> **E4，cycle 8351 / tick 16702：** `BUF.bufferState=3`，`io_splitStoreResp_valid=1`；响应 `vaddr=0、paddr=0、mmio=1、need_rep=0、uop_exceptionVec_6=0、uop_exceptionVec_7=0`。响应字段前缀为 `BUF.io_splitStoreResp_bits_`，地址宽 50/48 bit，其余为 1 bit。检查 C3 为 **PASS**。

同一周期的 ST S2 也显示 `io_pmp_mmio=1、io_pmp_st=0、s2_mmio=1`；因为此次探测已无非对齐异常，`s2_out_mmio=1`。两次 S2 的内存属性检查 C8 均为 **PASS**。这说明触发后续异常的条件是 MMIO 响应，而不是探测返回了 access fault。

该响应能与原始请求对应，不只是因为时间相邻：buffer 在整个有效区间始终保存 `(ROB flag=0,index=6,VA=9)`，仅发出一次 split request，并在 resp 状态收到唯一响应。响应接口本身是 Valid 接口，没有 ready 或独立 ROB tag，关联由该单 outstanding 状态机保证。

[StoreMisalignBuffer.scala:187](xs-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala#L187) 规定，收到 MMIO 响应后直接转入写回；[451 行](xs-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala#L451) 清除保存的响应 exceptionVec，并设置 `storeAddrMisaligned`。因此最终 cause 6 是缓冲区针对 MMIO 非对齐访问**重新生成**的异常，而不是对齐探测自身报告的异常。

> **E5，cycle 8352 / tick 16704：** `BUF.bufferState=6`（wb），`globalMMIO=1、globalException=0、io_writeBack_valid=1`，`io_writeBack_bits_uop_exceptionVec_6=1、..._7=0`，写回 ROB `(0,6)`。检查 C4 为 **PASS**。写回 ready 在 [MemBlock.scala:1254](xs-env/XiangShan/src/main/scala/xiangshan/backend/MemBlock.scala#L1254) 恒为 true，下一周期 buffer 进入 wait 状态。

buffer 没有转入向 store buffer 写数据的状态。在完整 `req_valid` 区间 cycle `[8347,8359)`，`BUF.io_sqControl_control_writeSb` 的 12 个样本均为 0，检查 C7 为 **PASS**。这支持本次内部探测以异常结束，而非完成拆分存储；它只说明该 buffer 的控制行为，不等价于检查了所有总线上的写事务。

### 3.4 异常最终写入机器态 CSR

[Rob.scala:546](xs-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L546) 从头部异常项生成 trap，[TrapHandleModule.scala:76](xs-env/XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala#L76) 编码异常号，再由 [TrapEntryMEvent.scala:120](xs-env/XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala#L120) 更新 CSR。

`CSR` 表示 `TOP.SimTop.l_soc.core_with_l2.core.backend.inner_intExuBlock.exus_7.csr.csrMod`。波形直接确认了 trap 输入与最终寄存器，而不只依赖 DiffTest 的异常打印：

| 证据 | cycle / tick | CSR 中的实际值 |
| --- | --- | --- |
| E6 | 8359 / 16718 | `io_fromRob_trap_valid=1, io_fromRob_trap_bits_pc=0x80000016, ..._bits_instr=0x000114a7, ..._bits_trapVec=0x40, io_fromMem_excpVA=9` |
| E7 | 8360..8364 / 16720..16728 | `mcause.rdata=6, mepc.rdata=0x80000016, mtval.rdata=9`，连续 5 个样本 |

trap valid/PC/instr/trapVec/excpVA 分别宽 1/50/32/64/64 bit，三个 CSR rdata 均为 64 bit。`trapVec=0x40` 表示只有 bit 6 被置位。检查 C5、C6 均为 **PASS**：异常指令是原始 `fsh`，故障地址仍为 `0x9`，没有误用内部探测地址 `0x0` 作为 `mtval`。

以上 E1..E8 及完整 buffer 状态保存在 [evidence.json](analysis-misaligned-store/evidence.json)，信号全名和位宽见其中 `signals`。提取使用 Wavekit 0.7.2，原 FST 为 93,889,689 字节，SHA-256 为 `8011de2f4791bd55c22195e4154c7a80932e0f886843aba0a4705d9715712eb9`，提取前后文件元数据未变；可在案例目录执行 `wavekit-python analysis-misaligned-store/verify.py --wave replay.fst --output analysis-misaligned-store/evidence.json` 重取。FST 的指令、地址和异常值与本地 ELF、日志相符，但缺少完整构建记录来严格证明现有可执行文件与当前源码树的对应关系。

## 4. NEMU 为什么产生 cause 7

旧 NEMU 的 [rvzfh/exec.h:9](xs-env/NEMU/src/isa/riscv64/instr/rvzfh/exec.h#L9) 同样将 `fsh` 实现为两字节 store。差异不在指令大小，而在地址分类之后是否执行非对齐检查。

### 4.1 一般非对齐检查没有抛出异常

[mmu.c:593](xs-env/NEMU/src/isa/riscv64/system/mmu.c#L593) 中一般 load/store 的软件非对齐检查受 `CONFIG_AC_SOFT` 控制。本地 [.config:150](xs-env/NEMU/.config#L150) 为：

```text
# CONFIG_AC_SOFT is not set
CONFIG_AC_NONE=y
CONFIG_MMIO_AC_SOFT=y
CONFIG_SHARE=y
```

因此，一般访存路径不会仅因 `0x9` 非对齐而立即抛异常。MMIO 专用检查虽然启用，但必须先进入 `is_in_mmio(addr)` 分支才会执行。

### 4.2 地址 0x9 被排除在 MMIO 检查之外

[mmio.c:28](xs-env/NEMU/src/device/io/mmio.c#L28) 的实现只查询已注册设备映射：

```c
bool is_in_mmio(paddr_t addr) {
  int mapid = find_mapid_by_addr(maps, nr_map, addr);
  return (mapid == -1 ? false : true);
}
```

本地 NEMU 的 RAM 从 `0x80000000` 开始，flash 设备从 `0x10000000` 开始，`0x9` 不在这些范围内。由于该地址没有设备映射，`is_in_mmio(0x9)` 返回 false。

于是 [paddr.c:373](xs-env/NEMU/src/memory/paddr.c#L373) 的共享 reference 路径跳过 `isa_mmio_misalign_data_addr_check`，进入下面的无效写分支：

```c
if (likely(is_in_mmio(addr))) {
  isa_mmio_misalign_data_addr_check(addr, vaddr, len,
                                   MEM_TYPE_WRITE, cross_page_store);
  mmio_write(addr, len, data);
} else {
  if (dynamic_config.ignore_illegal_mem_access)
    return;
  printf("ERROR: invalid mem write to paddr " FMT_PADDR
         ", NEMU raise access exception\n", addr);
  raise_access_fault(EX_SAF, vaddr);
  return;
}
```

[replay.log:9](replay.log#L9) 恰好记录了该分支的输出：

```text
ERROR: invalid mem write to paddr 0x0000000000000009, NEMU raise access exception
```

随后 [REF 状态](replay.log#L59) 为 `mcause=7、mepc=0x80000016`，[mtval](replay.log#L65) 为 `9`。这同时吻合错误分支、故障指令和地址，支持旧 NEMU 由 `EX_SAF` 产生 cause 7 的解释。NEMU 的 C 执行不在 DUT FST 中，这一侧的依据是日志与源码，而不是 reference 波形。

需要排除一个容易混淆的归因：**本次旧版本已经调用正确的 store access-fault helper `raise_access_fault(EX_SAF,...)`。** 后续 [NEMU #657](https://github.com/OpenXiangShan/NEMU/pull/657) 讨论的错误 read helper 属于另一个版本中新引入的分支，不是这里 cause 7 的来源。

## 5. 根因归纳

对同一笔 `VA=PA=0x9` 的 halfword store，两端在地址属性判断处分叉：

```text
XiangShan
  地址 0x9 非对齐，且 PMA 标为 MMIO
  -> 非对齐缓冲区进行对齐探测
  -> 探测返回 MMIO，无 access fault
  -> 为原始 store 生成 address-misaligned
  -> mcause = 6

旧 NEMU
  一般非对齐检查不抛异常
  -> 地址 0x9 没有注册设备，被判为非 MMIO
  -> 跳过 MMIO 专用非对齐检查
  -> 无效物理写，抛出 store access fault
  -> mcause = 7
```

因此，本例不是 XiangShan 在两个同时有效的异常中“选错优先级”，也不是 `fsh` 的大小、有效地址或异常 PC 计算错误。**直接问题是 NEMU 的 MMIO 分类范围小于它需要模拟的 XiangShan 平台 MMIO 范围，导致本应执行的非对齐检查被跳过。** 维护者在 [2024-11-13 的更正评论](https://github.com/OpenXiangShan/XiangShan/issues/3844#issuecomment-2472249439) 中也将问题归于 NEMU，而非此前怀疑的 XiangShan。

这一定性针对平台模型的一致性，不是声称 ISA 无条件要求 MMIO 非对齐优先。[RISC-V 特权规范](https://github.com/riscv/riscv-isa-manual/blob/6ee57d8997021aaa8025e208164763fe12e03c19/src/machine.adoc#L1813) 允许 address-misaligned 相对 access/page fault 有不同优先级；[非幂等区域说明](https://github.com/riscv/riscv-isa-manual/blob/6ee57d8997021aaa8025e208164763fe12e03c19/src/machine.adoc#L2823) 还建议用 access fault 避免软件拆分带来的副作用。这里判定旧 NEMU 存在问题的依据，是具体平台实现、实际波形和 reference 执行路径不一致，而不只是 DiffTest 将其值标成了 `right`。
