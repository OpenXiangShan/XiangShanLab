# XiangShan Issue #6668：`csrr x0, vl` / `csrr x0, vlenb` 错误地为 x0 分配物理寄存器

- Issue：[OpenXiangShan/XiangShan#6668](https://github.com/OpenXiangShan/XiangShan/issues/6668)（2026-10-05，`type: bug/WIP feature`，`version: kunminghu-v3`）
- 任务：OpenXiangShan/XiangShanLab#178
- 分析基线（缺陷）：`kunminghu-v3` `09453ff8f44e9d2b526e6149c60e1dc3f08c7192`（2026-09-29）
- 分析基线（修复）：`kunminghu-v3` `429980ea228c481384134abcd99a02f1ceae7440`（PR [#6779](https://github.com/OpenXiangShan/XiangShan/pull/6779)，2026-11-02）
- 最新 `kunminghu-v3` 复核：`b9638259e98c92b4c0d9e64a09d3c504faa699e6`（2026-11-10）
- 证据类型：源码 + 程序运行（构建/仿真 + Difftest 对照；本案例不含波形）

## 0. 结论速览

1. **缺陷已在 `kunminghu-v3` 上修复**。修复来自 PR #6779（merge commit `429980ea228c`），落点是 `PseudoDecodeChannel.scala` 的一行：
   `out.bits.gpWen := bundle(gpWenField) && instBitFields.RD =/= 0.U`。该 PR 的主题是把 CSRR 乱序执行重新加回译码器，这一行是顺带修掉的 `rd == x0` 写回使能问题，PR 描述与提交信息都没有提到 #6668。
2. **根因确认**：v3 的译码被拆成 vector / vset / simple / pseudo 四个通道，“`rfWen = 1 ⇒ ldest ≠ 0`” 这条不变量由各通道自己维护。`vl` / `vlenb` 走 pseudo 通道，而该通道直接取 `gpWen` 表项，没有像其他三个通道那样用 `RD =/= 0` 过滤；Rename 侧又按 `rfWen` 无条件分配物理寄存器并把 `ldest`（= 0）写进 RAT，于是 x0 被映射到一个装载 `vl` 值的物理寄存器。
3. **对照实验**（本文新增，不是 issue 原文的结论）：
   - 在最新 v3 `b9638259` 上，issue 原始 ELF 与自写的 rd=0 用例都能正常跑完，NEMU Difftest 逐指令一致；
   - 在最新 v3 上只回退那一行（`patch/0002`），issue 原始 ELF 立刻复现，且 **`instrCnt = 38, cycleCnt = 8,321, IPC = 0.004567`、`time=8325` 与 issue 附件日志逐位一致**；两条断言与 `pdest for $0 should be 0` 也一致；
   - `read_vlenb`（`rd = t0`）在回退构建上仍能正常跑完，说明缺陷只影响 `rd = x0`；
   - 需要在 3.4 单独说明的一点：`csrr x0, vtype` 在 `09453ff8f` 上不触发（issue 原文如此），但在“最新 v3 只回退一行”的构建上**会**触发——因为 PR #6779 把 pseudo 通道的匹配范围从 `vl`/`vlenb` 扩到了全 CSR 的 `csrrs/csrrc/csrrsi/csrrci` 纯读形式，那一行过滤在最新树上已经变成整个 CSR 读类的必要条件。两个基线的结论不能混用。
4. **触发条件**：任意一条 `vl` / `vlenb` 的纯读，只要 `rd = x0`（例如 `csrr zero, vl`、`csrr zero, vlenb`，以及 `csrrs/csrrc/csrrsi/csrrci` 的 `rs1 = x0, rd = x0` 形式），进入 Rename 就会 abort；关掉断言时则把 x0 的架构映射改坏。issue 评论区把该问题归为“向量单元仍在开发，后续解决”，但实际修复已经在 #6779 中合入。

## 1. 输入与基线

### 1.1 复现输入

| 项 | 值 |
| --- | --- |
| 原始复现 ELF | issue 附件 `xs_csrr_x0_vl.elf.zip` → `xs_csrr_x0_vl.elf` |
| 附件 SHA-256 | `e4038650bc4df8076d2a4b560006474f84cafce9e8e34f7bbbd1097ecac92996`（zip） |
| ELF SHA-256 | `ba12c99e4adffd1dec356bfa9bedebfde4edc123fffa732c8b35ea2ef92aad6c` |
| 程序内容 | 清空 x1–x31、置 `mstatus.VS = Initial`、`li a0,1`、`csrr zero, vl`（`csrrs x0, vl, x0`，编码 `0xc2002073`）、`li a0,2`、写 `tohost` 退出 |
| 复现命令（issue 原文） | `./build/emu -i xs_csrr_x0_vl.elf --diff ready-to-run/riscv64-nemu-interpreter-so -I 1000` |

### 1.2 基线版本

| 用途 | 分支 | commit | 日期 |
| --- | --- | --- | --- |
| 缺陷基线（issue 报告） | `kunminghu-v3` | `09453ff8f44e9d2b526e6149c60e1dc3f08c7192` | 2026-09-29 |
| 修复提交（PR #6779） | `kunminghu-v3` | `429980ea228c481384134abcd99a02f1ceae7440` | 2026-11-02 |
| 最新 v3（本轮复核） | `kunminghu-v3` | `b9638259e98c92b4c0d9e64a09d3c504faa699e6` | 2026-11-10 |
| V2 对照 | `kunminghu-v2` | `9c53db086a3b5bfe7a95a57e18de0e9662d64a38` | 2026-11-20 |

### 1.3 构建与运行配置

```bash
# 构建（DefaultConfig、单核、difftest 打开；g++ 不接受 difftest 默认的 -fno-slp-vectorize）
export NOOP_HOME=$PWD
make emu CONFIG=DefaultConfig NUM_CORES=1 \
    OPT_FAST="-O3 -fno-tree-slp-vectorize" -j64

# 运行（DUT alone / NEMU 对照）
./build/emu -i xs_csrr_x0_vl.elf --no-diff -C 20000 -I 60
./build/emu -i xs_csrr_x0_vl.elf --diff ready-to-run/riscv64-nemu-interpreter-so -C 20000 -I 60
```

V2 没有本案例涉及的 `PseudoDecodeChannel`：在 V2 里 `vl` 是普通 CSR，`csrr rd, vl` 走统一的 CSR 译码路径，并且在 `DecodeUnit.scala:1145`（`9c53db08`）有一道全局兜底
`io.deq.decodedInst.rfWen := (decodedInst.ldest =/= 0.U) && decodedInst.rfWen`。
V3 引入可重命名的 `vl` 与分通道译码后，这道全局兜底被拆散到各通道，pseudo 通道漏掉了 `rd != 0` 的判断，这就是本 bug 的来源。

## 2. 复现结果

构建耗时：修复树 `b9638259` 1874 s，回退树 `b9638259` + `patch/0002` 2126 s（同一台机器、同一套参数）。

所有运行统一用 `-I 60 -C 20000`：本机仿真速度约 60 sim-cycle/s，前 ~8k 周期是冷启动；issue 的 abort 发生在 `instrCnt = 38`，60 条指令足以越过触发点，同时避免在 `test_exit` 自循环里空跑。`COMPLETED` 表示达到指令上限、无断言、且 NEMU Difftest 逐指令一致；`ABORT` 表示 RTL 断言或 `XSError` 触发。

| 用例 | 构建 | `--no-diff` | `--diff NEMU` |
| --- | --- | --- | --- |
| `xs_csrr_x0_vl.elf`（issue 原样） | `b9638259`（修复后） | COMPLETED（60 / 8,379） | COMPLETED（60 / 8,379） |
| `xs_csrr_x0_vl.elf` | `b9638259` + 0002 回退 | **ABORT（38 / 8,321）** | **ABORT（38 / 8,321）** |
| `x0_readback.elf`（`csrr x0, vl` + `csrr x0, vlenb`） | `b9638259` | COMPLETED（60 / 8,337） | COMPLETED（60 / 8,337） |
| `x0_readback.elf` | 回退 | **ABORT（6 / 8,234）** | **ABORT（6 / 8,234）** |
| `read_vlenb.elf`（`csrr t0, vlenb`，rd ≠ 0） | `b9638259` | COMPLETED（60 / 8,338） | COMPLETED（60 / 8,338） |
| `read_vlenb.elf` | 回退 | COMPLETED | COMPLETED |
| `x0_vtype_control.elf`（`csrr x0, vtype`） | `b9638259` | COMPLETED（60 / 8,338） | COMPLETED |
| `x0_vtype_control.elf` | 回退 | **ABORT（6 / 8,233）** | **ABORT（6 / 8,233）** |

括号内为 `instrCnt / cycleCnt`。

回退构建上 issue 原始 ELF 的完整失败输出：

```text
Assertion failed at .../build/rtl/RenameTableWrapper.sv:947.
Assertion failed at .../build/rtl/Rename.sv:9216.
The simulation stopped. There might be some assertion failed.
Core 0: ABORT at pc = 0xfffef5a771d71ba2
Core-0 instrCnt = 38, cycleCnt = 8,321, IPC = 0.004567
Assertion failed: rfWen cannot be 1 when Int regfile ldest is 0
[ERROR][time=                8325] SimTop.cpu.l_soc.core_with_l2.core.backend.inner.ctrlBlock.rename.rat: pdest for $0 should be 0
```

`instrCnt = 38 / cycleCnt = 8,321 / IPC = 0.004567 / time = 8325` 与 issue 附件日志**逐位一致**；仅 RTL 行号不同（`09453ff8f` 为 `RenameTableWrapper.sv:930`、`Rename.sv:5458`），这是两个基线生成代码的差异。

补充说明：

- **Spike 对照不适用**。随仓库附带的 `ready-to-run/riscv64-spike-so` 复位 PC 是 `0x10000000`，而本镜像链接在 `0x80000000`，第一条指令即 ABORT；因此除 NEMU 外没有第二个可用的参考模型。NEMU 与 DUT 使用同一复位向量，逐指令一致。
- `x0_vtype_control` 在回退构建上失败，是 PR #6779 扩大 pseudo 匹配范围后的结果，不代表 `09453ff8f` 上的原始缺陷范围，原因见 3.4。

## 3. 根因分析

### 3.1 ISA 侧：`csrr x0, csr` 是合法的纯读

`csrr rd, csr` 是 `csrrs rd, csr, x0` 的伪指令：读 CSR，写入 `rd`。`rd = x0` 时写入被硬件常量 0 吞掉，但读与读副作用照常发生（RISC-V Unprivileged ISA，Zicsr）。RTL 侧的正确处理是**保留读、清掉 GPR 写回**，而不是把它判成非法或者给它分配目的寄存器。

`vl`（`0xC20`）与 `vlenb`（`0xC22`）是向量扩展的只读 CSR；`csrr zero, vl` = `0xc2002073`，`csrr zero, vlenb` = `0xc2202073`。

### 3.2 V3 的译码通道与 `gpWen`

`kunminghu-v3` 的后端译码按指令类别拆成四个通道（`DecodeChannels.scala`）：

| 通道 | 负责的指令 | 对 `rd == 0` 的处理 |
| --- | --- | --- |
| `VectorDecodeChannel` | 向量算术/访存等 | `gpWen && src.dest =/= 0`（`VectorDecodeChannel.scala:247`） |
| `VsetDecoder` | `vsetvli/vsetivli/vsetvl` | `gpWen && RD =/= 0`（`VsetDecoder.scala:116`） |
| `SimpleDecodeChannel` | 普通标量/CSR 指令（`09453ff8f` 上 `csrr x0, vtype` 走这里） | `gpWen && RD =/= 0`（`SimpleDecodeChannel.scala:152`） |
| `PseudoDecodeChannel` | `vl` / `vlenb` 读（最新树还包含全 CSR 的 `csrrs/csrrc/csrrsi/csrrci` 纯读），以及 `prefetch`、`j`、`jalr x0` 等 | **缺陷基线上没有过滤**（`09453ff8f`: `PseudoDecodeChannel.scala:93`） |

（表中行号为最新 v3 `b9638259`，pseudo 通道回退基线的行号单独标注。）

`PseudoDecodeChannel` 里 `gpWen` 来自 uop 表的 trait：

- `csrr rd, vl` → `VSetOpcodes.readvl`，定义是
  `Value(rdvl, notIll, dc(1), avlVl) + GpWen + VlRen`（`Opcode.scala:1025`），带 `GpWen`；
- `csrr rd, vlenb` → `AluOpcodes.add` 的变体，`add` 同样带 `GpWen`。

而 `vl` / `vlenb` 之所以落到 pseudo 通道，是因为它们在 V3 里的读法已经不再是“普通 CSR 读”：`vl` 有自己的重命名表与 free list（`vlRenamePorts` / `vlFreeList`），`vlenb` 被译成“立即数 `VLEN/8` 的 ALU 写回”，从而可以乱序执行而不必经过顺序 CSR 单元。因此它们需要单独的译码结果，被放进 pseudo 表，并且在通道选择时**伪通道优先**（`DecodeChannels.scala` 里 `psdChannelOut(i).valid` 先于 vec/vset/simple 被选中）。

缺陷基线上这一行是：

```scala
// 09453ff8f src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannel/PseudoDecodeChannel.scala:92-93
out.bits.ldest := instBitFields.RD
out.bits.gpWen := bundle(gpWenField)          // rd == 0 时仍然为 1
```

最新 v3 上是：

```scala
// b9638259 同文件:94-95
out.bits.ldest := instBitFields.RD
out.bits.gpWen := bundle(gpWenField) && instBitFields.RD =/= 0.U
```

`DecodeStage` 把 `gpWen` 直接转成 `rfWen`，中间没有第二道全局过滤：

```scala
// b9638259 .../vector/Decoder/DecodeStage.scala:177
bits.rfWen := uopInfo.gpWen
```

### 3.3 Rename 侧：`rfWen` 就是不变量本身

Rename 完全按 `rfWen` 分配目的寄存器，不检查 `ldest`：

```scala
// b9638259 Rename.scala
483:  needIntDest(i) := io.in(i).valid && needDestReg(Reg_I, io.in(i).bits)   // needDestReg(Reg_I, x) = x.rfWen
582:  uops(i).pdest := MuxCase(0.U, Seq(
583:    needIntDest(i)    ->  intFreeList.io.allocatePhyReg(i),
...
876:  intRenamePorts(i).wen  := intSpecWen(i)
877:  intRenamePorts(i).addr := inVec(i).ldest(...)                          // = 0
878:  intRenamePorts(i).data := io.out(i).bits.pdest                         // 新分配的物理寄存器
```

所以 `csrr x0, vl` 在 Rename 里做了三件不该做的事：从 free list 分配物理寄存器、把 `pdest` 写回 RAT 的 0 号表项、让后续所有 `x0` 读都读到这个物理寄存器。两处断言正是这条不变量的守门员：

```scala
// b9638259 Rename.scala:921-923
when(x.valid && x.bits.rfWen) {
  assert(x.bits.ldest =/= 0.U, "rfWen cannot be 1 when Int regfile ldest is 0")
}
// b9638259 RenameTable.scala:258
XSError(arch.wen && arch.addr === 0.U && arch.data =/= 0.U, "pdest for $0 should be 0\n")
```

（缺陷基线对应 `Rename.scala:847-848`、`RenameTable.scala:257`。`RenameTable.scala:264` 是 spec 写口的同一检查。）

### 3.4 为什么 issue 报告里 `csrr x0, vtype` 不受影响，而“最新树回退”会失败

`vtype` 本身不在 `09453ff8f` 的 pseudo 模式表里（该表只有 `CSRRVL` / `CSRRVLENB` 两个模式），因此 `csrr zero, vtype` 落到 `SimpleDecodeChannel`，而 simple 通道有 `RD =/= 0` 过滤——这解释了 issue 原文的分组：`vtype` 通过、`vl`/`vlenb` 失败。

但 PR #6779 同时把 pseudo 通道扩成了“全 CSR 的纯读形式”：

```scala
// b9638259 PseudoDecodeChannel.scala:160-174
def onlyBlockBackwardList = CSROoORead.blockBackwardCsrReadList.diff(CSROoORead.waitForwardCsrReadList)
...
val CSRR_SR_NO_BLOCK_WAIT  = noBlockAndWaitList.map(csr => PseudoInstPattern(makeCSRRSBitPat(csr)))
...
// 160-162 行：noBlockAndWaitList 从 fullCSRList 构造，只排除 vl/vlenb 和两个已有列表
def noBlockAndWaitList = CSROoORead.fullCSRList.diff(...).diff(List(CSRs.vl, CSRs.vlenb))
```

所以在 `b9638259` 上，`csrr zero, vtype` 也走 pseudo 通道。此时如果把那一行过滤单独回退，`vtype` 的 `rd = x0` 读同样会 abort（见第 2 节 `x0_vtype_control` 的回退结果）。这不是 issue 描述的原始缺陷范围，而是同一行过滤在更宽匹配表下的必要性；本文的 `x0_vtype_control` 因此只作为**最新树上的回归用例**使用，不用来推断 `09453ff8f` 的行为。

### 3.5 引入与消失时间

- `PseudoDecodeChannel.scala` 由 `29f48d8a683d`（2026-08-06，*feat(vector): new vector initial commit*）引入，引入时就没有 `rd != 0` 过滤，`09453ff8f` 原样保留。
- `429980ea228c`（PR #6779，2026-11-02）补上了过滤。PR 的目的是恢复 CSRR 乱序执行，同时把 pseudo 通道扩展到 `csrrs/csrrc/csrrsi/csrrci` 的纯读形式——这让 `RD =/= 0` 过滤成为这批新模式的必要条件：否则 `csrrci x0, fflags, 0` 之类的读也会给 x0 分配物理寄存器。
- 因此 #6668 在 `kunminghu-v3` 上的存活区间是 `29f48d8a68` … `429980ea22` 之前；V2 因为走统一的 CSR 译码路径 + 全局 `ldest != 0` 兜底，不受影响。

## 4. 修复

### 4.1 上游修复

PR #6779 的实质改动（与本 issue 相关的部分）：

```diff
-  out.bits.gpWen := bundle(gpWenField)
+  out.bits.gpWen := bundle(gpWenField) && instBitFields.RD =/= 0.U
```

语义上等价于把 simple/vset/vector 三个通道已有的 `rd != 0` 过滤补到 pseudo 通道：读照做，GPR 写回使能清零。`ldest` 仍为 0，但 `rfWen = 0`，Rename 不再分配物理寄存器，也不再写 RAT[0]，两条断言都不会触发。

### 4.2 缺陷基线上的最小修复

`patch/0001-fix-pseudo-decode-rd-nonzero.patch` 是同一行改动在 `09453ff8f` 上的最小补丁（1 文件 +1/−1），供直接复核缺陷基线时使用；本轮没有重建 `09453ff8f`，所以“仅这一行即可消除缺陷”的实证由 `patch/0002` 在最新树上的正反实验承担（回退即复现、恢复即消失）。`patch/0002-revert-prefix-behavior.patch` 就是它的反向补丁。

## 5. 修复前后对照

| 观察点 | 回退构建（等价缺陷） | 修复构建 `b9638259` |
| --- | --- | --- |
| `csrr x0, vl` 进入 Rename | `rfWen = 1`、`ldest = 0`，两条断言 + `pdest for $0 should be 0` | 不分配物理寄存器，无断言 |
| `csrr x0, vlenb` | 同左（`x0_readback` 第 6 条指令即 abort） | 正常完成 |
| `csrr t0, vlenb`（rd ≠ 0） | 正常完成 | 正常完成（修复没有误伤写回） |
| RAT[0] | 被写进新分配的 `pdest`（XSError 复现） | 保持 0 |
| issue 原始 ELF 的失败签名 | `instrCnt = 38, cycleCnt = 8,321`（与 issue 逐位一致） | COMPLETED，NEMU 逐指令一致 |

因此可以判定：`PseudoDecodeChannel` 那一行 `RD =/= 0` 过滤对该缺陷**既是必要的也是充分的**——只回退它就能复现原始失败签名，加上它就消失，且不影响 `rd != 0` 的正常读。

## 6. 边界与未覆盖

- **没有重建 `09453ff8f` 本体**。该基线的失败证据有三层：issue 附件日志、该 commit 的源码（`PseudoDecodeChannel.scala:93` 无过滤）、以及最新树“只回退一行”后**逐位相同**的失败签名（`instrCnt = 38 / cycleCnt = 8,321`）。时间允许时可以按第 7 节直接构建缺陷基线复核。
- 本案例的断言只在带断言的 emu 构建（`make emu`）里触发。issue 提到关掉断言后 x0 会读成 `vl` 的值；本文没有单独构建无断言版本去复现该现象，但 3.3 的 RAT 写回路径（`Rename.scala:876-878`）给出了同样的结论。
- `vl` 的**数据路径**不在本案例范围内：`vsetvli` 后立即 `csrr rd, vl` 可能读到旧值/0，是另一个仍然 Open 的 issue #6676（本案例的 `read_vlenb` 对照特意使用常量 `vlenb = 16`，避免依赖 `vl` 读数据）。
- 回退构建上的 `csrr x0, vtype` 也会 abort，这是 PR #6779 扩大 pseudo 匹配表导致的“同一行过滤的更大作用域”，不是 `09453ff8f` 的原始缺陷范围；不要把这条结论迁移回旧基线（见 3.4）。
- 涉及的 `vl` / `vlenb` 伪译码属于仍在开发中的 V3 向量后端；本文结论只对声明的 commit 成立，V2 只作为机制对照，不迁移结论。
- 本文没有导入波形：证据是 RTL 断言文本 + Difftest/程序运行结果 + 源码行号。复现脚本与完整日志随附，可离线重跑。

## 7. 复现步骤

```bash
# 0) 依赖：verilator 5.x、JDK 17+、mill、riscv64 工具链；TMPDIR 指到有空间的目录
git clone --recurse-submodules https://github.com/OpenXiangShan/XiangShan.git
cd XiangShan
git checkout b9638259e98c92b4c0d9e64a09d3c504faa699e6      # 最新 v3（含修复）

# 1) 修复构建：预期不 abort
export NOOP_HOME=$PWD
make emu CONFIG=DefaultConfig NUM_CORES=1 \
    OPT_FAST="-O3 -fno-tree-slp-vectorize" -j64
./build/emu -i replay/case/xs_csrr_x0_vl.elf --no-diff -I 60
#   -> EXCEEDING CYCLE/INSTR LIMIT，无断言

# 2) 受控回退：只去掉那一行过滤，预期复现 issue
git apply replay/patch/0002-revert-prefix-behavior.patch
make emu CONFIG=DefaultConfig NUM_CORES=1 \
    OPT_FAST="-O3 -fno-tree-slp-vectorize" -j64
./build/emu -i replay/case/xs_csrr_x0_vl.elf --no-diff -I 60
#   -> Assertion failed: rfWen cannot be 1 when Int regfile ldest is 0
#   -> Assertion failed: pdest for $0 should be 0
#   -> instrCnt = 38, cycleCnt = 8,321（与 issue 一致）

# 3) 全量矩阵（4 用例 × {no-diff, NEMU}）
bash replay/case/run_cases.sh ./build/emu replay/logs/out
```

若要直接复核缺陷基线，把 `git checkout` 换成 `09453ff8f44e9d2b526e6149c60e1dc3f08c7192`，并在构建前应用 `replay/patch/0001-fix-pseudo-decode-rd-nonzero.patch` 做修复对照。

## 8. 证据清单

| 文件 | 说明 |
| --- | --- |
| `replay/case/xs_csrr_x0_vl.elf` | issue 原始复现 ELF（SHA-256 `ba12c99e…`） |
| `replay/case/x0_readback.S/.elf` | `csrr x0, vl` + `csrr x0, vlenb` 后回读 x0（`448d06e3…` / `075d0240…`） |
| `replay/case/read_vlenb.S/.elf` | rd ≠ 0 正向对照（`vlenb = 16`，`863c82c7…` / `8a164273…`） |
| `replay/case/x0_vtype_control.S/.elf` | 最新树上的 rd=0 回归用例（`f9459713…` / `fadebab7…`） |
| `replay/case/link.ld` | 裸机链接脚本（0x80000000 / tohost=0x80001000） |
| `replay/case/run_cases.sh` | 4 用例 × {no-diff, NEMU} 一键脚本（`07369cd9…`） |
| `replay/patch/0001-*.patch` | `09453ff8f` 上的最小修复（1 文件 +1/−1） |
| `replay/patch/0002-*.patch` | 最新 v3 上的受控回退 |
| `replay/logs/head/` | `b9638259` 构建日志（gz）、8 次运行的精简日志、`summary.txt` |
| `replay/logs/revert/` | 回退构建的同一套日志；`xs_csrr_x0_vl.nodiff.full.log.gz` 为完整原始日志 |
| `replay/SHA256SUMS` | 用例与补丁的 SHA-256 |

完整（未裁剪）运行日志只对 `xs_csrr_x0_vl` 的两个运行保留为 `.full.log.gz`；其余精简日志保留了启动、断言/陷阱、`instrCnt` 与 `Host time` 行。
