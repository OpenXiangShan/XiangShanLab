# HFENCE.GVMA 演示程序与波形验证报告

## 结论摘要

本分析使用 wavekit 开源仓库中的 `FstReader` 解析 FST 波形，并按 `TOP.clock` 上升沿采样后按绝对 cycle 查询。香山源码根目录为 `/home/yanyusong/cbo-kmhv2/XiangShan`。

结论：场景满足。演示程序在 PC `0x80000132` 执行编码为 `0x62e78073` 的 HFENCE.GVMA；波形证明该指令进入 `hfence_g` 路径，并在 cycle `15150` 向 PTW/TLB 发出 `hfenceg_valid=1`、`hg=1`、`addr=0x80200000`、`id=0x5` 的刷新请求。该地址与 VMID 与程序输入、反汇编完全一致；仿真以 `HIT GOOD TRAP` 正常结束。

| 项目 | 值 |
| --- | --- |
| 应用目录 | `/home/yanyusong/cbo-kmhv2/nexus-am/apps/hfence_gvma` |
| 二进制 | `/home/yanyusong/cbo-kmhv2/nexus-am/apps/hfence_gvma/build/hfence_gvma-riscv64-xs.bin` |
| 波形 | `/tmp/hfence_gvma_i_wave.fst` |
| 波形格式/大小 | FST，148 MiB |
| 采样时钟/边沿 | `TOP.clock` / 上升沿 |
| 目标 PC | `0x80000132` |
| 指令字 | `0x62e78073` |
| rs1 | `0x80200000` |
| rs2 / VMID | `0x5` |
| ROB 标识 | `flag=1, value=0x2a` |

## 演示程序

程序文件为 [`hfence_gvma.c`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/hfence_gvma/hfence_gvma.c#L1)。它通过 `.insn r 0x73, 0, 0x31` 固定生成 Hypervisor 扩展的 HFENCE.GVMA 指令，并以非零 `rs1`、`rs2` 覆盖“指定 guest physical address、指定 VMID”的 G-stage 刷新分支。

```c
__attribute__((noinline)) static void hfence_gvma(uintptr_t guest_physical_address,
                                                  uintptr_t vmid) {
  asm volatile(".insn r 0x73, 0, 0x31, x0, %0, %1"
               :
               : "r"(guest_physical_address), "r"(vmid)
               : "memory");
}
```

使用以下构建配置完成构建：

```sh
source ~/cbo-kmhv2/env.sh
make -C ~/cbo-kmhv2/nexus-am/apps/hfence_gvma ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=1
```

反汇编显示操作数装入与指令字一致：

```text
8000012a: li   a5,1025
8000012e: slli a5,a5,0x15       # a5 = 0x80200000
80000130: li   a4,5
80000132: 62e78073             # hfence.gvma x15, x14
```

## 仿真结果

执行命令：

```sh
source ~/cbo-kmhv2/env.sh
cd ~/cbo-kmhv2/XiangShan
./build/emu --dump-wave-full --no-diff \
  -i ~/cbo-kmhv2/nexus-am/apps/hfence_gvma/build/hfence_gvma-riscv64-xs.bin \
  --wave-path /tmp/hfence_gvma_i_wave.fst
```

仿真串口输出：

```text
HFENCE.GVMA demo starts
rs1 guest physical address = 0x80200000
rs2 VMID = 0x5
PASS: hfence.gvma retired without an exception
```

仿真器最终输出 `Core 0: HIT GOOD TRAP at pc = 0x8000018a`，退出状态为 0；因此没有由目标指令导致的非法指令或异常终止。

## 波形时间线

| Cycle | Time | 位置/事件 | 波形证据 |
| ---: | ---: | --- | --- |
| 14817 | 29634 | Decode lane 3 | `pc=0x80000132`，`instr=0x62e78073`，`fuOpType=0x14` |
| 15149 | 30298 | ROB 串行化控制 | `rob.io_flushOut_valid=1` |
| 15150 | 30300 | PTW cache / G-stage TLB flush | `io_sfence_dup_0_valid=1`、`hfenceg_valid=1`、`hg=1`、`addr=0x80200000`、`id=0x5` |
| 15150 | 30300 | ROB commit lane 0 | `commitValid=1`、`debug_pc=0x80000132`、`debug_instr=0x62e78073`、`robIdx=0x2a` |

`io_commits_info_0_debug_pc` 与 `commitValid` 在随后的若干采样点仍保持该值；本报告把 cycle `15150` 作为该提交事件的起点，因为它是与目标 `hfenceg_valid`、操作数和 `flushOut` 直接对齐的首个 cycle，而非把保持的 debug 字段误计为多个独立提交。

## 场景判定

### 1. Decode 与权限

波形的目标指令字等于反汇编的 `0x62e78073`，且 Decode 的 `fuOpType=0x14`。源码将 `HFENCE_GVMA` 译码为 `FuType.fence`、`FenceOpType.hfence_g`，并设置 `noSpec`、`blockBack`、`flushPipe`；因此其为不可投机、需要串行化的 Fence 类指令。[`DecodeUnit.scala:490`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L490)

```scala
HFENCE_GVMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X,
  FuType.fence, FenceOpType.hfence_g, SelImm.X,
  noSpec = T, blockBack = T, flushPipe = T)
```

程序运行至 PASS 且模拟器为 Good Trap，结合目标 commit 的 `commitValid=1`，证明当前 M-mode 演示没有触发 Decode 中的非法/虚拟指令异常路径。

### 2. Fence FU、TLB 刷新与字段来源

Fence FU 在 `s_wait` 等待 store buffer 为空后，对 `hfence_g` 进入 `s_tlb`；在该状态仅保持一个 cycle 并拉高 `sfence.valid`。同一实现把 `hg` 设为 `func === FenceOpType.hfence_g`，把 `src(0)` 锁存为 `addr`，并因 `useVmid` 将 `src(1)` 的低 VMID 位锁存为 `id`。[`Fence.scala:47`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L47) [`Fence.scala:67`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L67)

```scala
sfence.valid := state === s_tlb &&
  (func === FenceOpType.sfence || func === FenceOpType.hfence_v || func === FenceOpType.hfence_g)
sfence.bits.hg := func === FenceOpType.hfence_g
sfence.bits.addr := RegEnable(io.in.bits.data.src(0), io.in.fire)
sfence.bits.id := RegEnable(Mux(useVmid, fenceVmid, fenceAsid), io.in.fire)
```

cycle `15150` 的波形正好给出 `valid=1`、`hg=1`、`addr=0x80200000`、`id=0x5`，逐项匹配本程序的 rs1/rs2。因此可证明信号路径为：程序寄存器操作数 → Fence FU 锁存 `SfenceBundle` → PTW cache 的 `io_sfence_dup_0` → `hfenceg_valid`。

### 3. G-stage TLB 条目失效

TLB storage 用 `sfence.valid && sfence.bits.hg` 生成 `hfenceg_valid`。当 `rs2` 非零时，代码清除所有二阶段翻译条目的 valid；当 rs2 为零时则按 `vmid === sfence.bits.id` 选择性清除。当前程序的 rs2 为 `0x5`，因此波形中的 `rs2_zero=0` 落在“按 VMID 精确失效”的分支。[`TLBStorage.scala:216`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala#L216) [`TLBStorage.scala:268`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala#L268)

```scala
val hfenceg_valid = sfence.valid && sfence.bits.hg
when (hfenceg_valid) {
  when(hfenceg.bits.rs2) {
    v.zipWithIndex.map { case (a, i) =>
      a := a && !(entries(i).s2xlate =/= noS2xlate)
    }
  }.otherwise {
    v.zipWithIndex.map { case (a, i) =>
      a := a && !(entries(i).s2xlate =/= noS2xlate && entries(i).vmid === sfence.bits.id)
    }
  }
}
```

这里 `sfence.bits.rs2` 是“rs2 是否为 x0”的编码标识，不是寄存器值本身：波形显示 `rs2_zero=0`，故选择特定 VMID 的 `otherwise` 分支；`id=0x5` 即该特定 VMID。`addr` 同样由波形保留为 `0x80200000`，但当前昆明湖实现的 HFENCE.GVMA 失效选择以 VMID/二阶段翻译属性为主，源码计算 `hfenceg_gvpn` 后并未用它进行条目匹配。[`TLBStorage.scala:269`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala#L269)

### 4. Flush、bubble 与边界

ROB 在 cycle `15149` 拉高 `io_flushOut_valid`，下一 cycle Fence 的 G-stage TLB 请求和目标 commit 同时可见。这是 `flushPipe=T` 的波形证据，说明该类指令会对年轻指令形成串行化边界。

目标从 Decode cycle `14817` 到 TLB/commit cycle `15150` 间隔 333 cycles；该时间包含前端/后端已有指令、串口输出相关工作及 Fence 等待阶段，不能仅据此归因于某一个单独的 ready 信号。FST 中 Decode 输出缺少完整的外露 Decoupled `valid/ready` 对，且 Issue Queue 中同 PC payload 在其 `valid=0` 时会被保留，因此未将这些保持值误报为多次 fire。能够严格证明的是目标 Decode、ROB flush、TLB `hfenceg_valid` 和 commit 的上述对齐关系。

## 最终判定

- 指令生成正确：通过反汇编和 Decode 波形确认 `0x62e78073`。
- 操作数传递正确：`0x80200000` 与 VMID `0x5` 从程序到 TLB 刷新端口一致。
- 功能路径正确：`hg=1` 且 `hfenceg_valid=1` 证明选择了 HFENCE.GVMA 的 G-stage TLB 刷新路径。
- 串行化与提交正确：`flushOut` 后目标指令在 ROB lane 0 以 `commitValid=1` 提交。
- 程序结果正确：打印 PASS，仿真以 Good Trap 结束。

因此，本次 HFENCE.GVMA 演示满足“执行目标指令、产生 G-stage 地址翻译缓存刷新请求、携带指定 VMID、无异常完成”的场景分析要求。
