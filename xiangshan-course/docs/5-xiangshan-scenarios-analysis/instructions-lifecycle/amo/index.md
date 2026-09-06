# 一条 AMO 指令的简单分析过程

本文我们分析一个最简单的 AMO (Atomic Memory Operation, 原子内存操作) 指令 (amoswap.w), 在单核昆明湖中的运行过程.

RISC-V A 扩展（Atomic，原子扩展）是 RISC-V 指令集的标准非特权扩展，专为多核 / 多硬件线程的并发场景设计，提供硬件级不可分割的原子内存操作能力，核心包含原子读 - 修改 - 写（AMO）系列指令、加载保留 / 条件存储（lr/sc）指令对，同时配套 aq/rl 后缀实现轻量级内存序约束以适配 RISC-V 弱内存序模型，是操作系统实现自旋锁、信号量、无锁数据结构，解决多核并发竞态问题的核心硬件基础。

:::info
Q: 在单核且不考虑中断的情况下, 使用一条 AMO 指令对内存进行读-修改-写和使用一组 load-arithmetic-store 指令有没有区别? 如果有, 区别在哪里?

A: 肯定是有区别的. 区别在于作为一个乱序执行的处理器, 如果我们把一条 AMO 指令拆分成了三条非原子的指令, 在某些情况下, 会出现访问内存 (缓存) 的操作次序和程序语义不一致的情况 (遇到这种情况后, 会触发内存模块的 replay 重放机制, 我们会在后续的文章中分析这个过程), 以下是一个很好的例子: 考虑程序 A: AMO; STORE2, 再考虑程序 B: LOAD; ALU; STORE1; STORE2. 程序 B 的 LOAD-ALU-STORE1 的语义和程序 A 的 AMO 指令一致 (都是操作同一个地址, 且进行同样的修改. 比如说如果程序 A 的 AMO 指令是一个 AMOADD, 那么程序 B 就应该是 LOAD, 根据 LOAD 进来的值做加法, 再 STORE 到原来的地址). 这时候如果 STORE2 和 AMO/LOAD-ALU-STORE1 的地址一致, 且 STORE2 的源操作数更早的就位了, 可能会出现程序B 的 STORE2 比 LOAD, ALU, 或 STORE1 更先进入发射队列并被发射到执行单元. 但是程序 A 就不会, 因为 AMO 指令有内存序的要求, 后续的指令就算是操作数已经准备好很久了也不能进入到发射队列中.

:::

我们研究一个包括一条原子内存交换指令的 C 程序:

```bash
#include <klib.h>

int main() {
  int value = 5;
  printf("Old=%d ", value);
  int old_val;
  asm volatile(                 // C 语言内联汇编, volatile 关键字强制编译器包括这条指令
    "amoswap.w %0, %2, (%1)"    // 汇编指令模版, 用 %0, %1, %2 占位操作数
    : "=r"(old_val)             // 输出操作数列表, 汇编指令执行后, 把值写回 C 的变量
    : "r"(&value), "r"(7)       // 输入操作数列表, 把 C 变量/常量 传入汇编为操作数
    : "memory"                  // 破坏说明列表, 告知编译器这段汇编指令会修改那些资源
  );                            // memory 破坏符告知编译器汇编执行后必须重新从内存读取值
  printf("New=%d\n", value);
  return 0;
}
```

以下是 `main`函数的汇编代码:

```bash
000000008000012a <main>:
    8000012a:   1101                    addi    sp,sp,-32
    8000012c:   4595                    li      a1,5
    8000012e:   00001517                auipc   a0,0x1  
    80000132:   1a250513                addi    a0,a0,418 # 800012d0 <printf_+0x32>
    80000136:   ec06                    sd      ra,24(sp)
    80000138:   c62e                    sw      a1,12(sp)
    8000013a:   164010ef                jal     8000129e <printf_>
    8000013e:   007c                    addi    a5,sp,12
    80000140:   471d                    li      a4,7
    80000142:   08e7a7af                amoswap.w       a5,a4,(a5)
    80000146:   45b2                    lw      a1,12(sp)
    80000148:   00001517                auipc   a0,0x1
    8000014c:   19050513                addi    a0,a0,400 # 800012d8 <printf_+0x3a>
    80000150:   14e010ef                jal     8000129e <printf_>
    80000154:   60e2                    ld      ra,24(sp)
    80000156:   4501                    li      a0,0
    80000158:   6105                    addi    sp,sp,32
    8000015a:   8082                    ret
```

上述程序中 `PC=0x8000142`处出现了一条原子内存操作 (AMO) 指令, 该条指令为 `0x08e7a7af`, 也就是二进制`00001_00_01110_01111_010_01111_0101111`. 阅读 RISC-V 指令集手册, 对其进行手动译码, 可知本条指令的 `rs2 = 0b01110 = 14 (a4)`, `rs1 = 0b01111 = 15 (a5)`, `rd = 0b01111 = 15 (a5)`, 此外, 这条指令的 `aq`和`rl`位都是0, 表示程序没有特定的一致性约束. 从手册的指令语意描述可得, 这条指令将寄存器 `a4`中的值原子性的交换寄存器`a5`所指向的内存值, 并将原内存中的值存入到`a5`寄存器中.

使用 `build/emu`进行软件仿真, 执行该程序, 获得如下输出, 以及波形图 [附件: amo\_simple\_wave.zip](./attachments/4nSy-filnKivqKuf/amo_simple_wave.zip):

```bash
emu compiled at Mar 20 2026, 22:18:56
Using simulated 32768B flash
Using simulated 8386560MB RAM
The image is /home/yanyusong/xs-env/nexus-am/apps/rva-demo/build/demo-riscv64-xs.bin
Old=5 New=7
Core 0: HIT GOOD TRAP at pc = 0x80000166
Core-0 instrCnt = 894, cycleCnt = 10,234, IPC = 0.087356
Seed=0 Guest cycle spent: 10,238 (this will be different from cycleCnt if emu loads a snapshot)
Host time spent: 36,817ms
```

或使用 `build/simv`使用 VCS 进行软件仿真, 执行该程序, 可以获得如下输出, 以及波形图[附件: amo\_simv.fsdb.zip](./attachments/4nSy-filnKivqKuf/amo_simv.fsdb.zip):

```bash
yanyusong@eda01:~/xs-eda-compile/xs-env/XiangShan$ ./build/simv +workload=../nexus-am/apps/amotest/build/amotest-riscv64-xs.bin +no-diff +dump-wave=fsdb
Chronologic VCS simulator copyright 1991-2020
Contains Synopsys proprietary information.
Compiler version Q-2020.03-SP2_Full64; Runtime version Q-2020.03-SP2_Full64;  Jun 30 14:57 2026
ram image:../nexus-am/apps/amotest/build/amotest-riscv64-xs.bin
disable diff-test
Core  x's Commit SHA is: 2acbf327cf, dirty: 0
*Verdi* Loading libsscore_vcs202003.so
FSDB Dumper for VCS, Release Verdi_R-2020.12-SP1, Linux x86_64/64bit, 03/02/2021
(C) 1996 - 2021 by Synopsys, Inc.
*Verdi* : Create FSDB file 'simv.fsdb'
*Verdi* : Begin traversing the scopes, layer (0).
*Verdi* : Enable +mda dumping.
*Verdi* : End of traversing.
*Verdi* FSDB: For performance reasons, the Memory Size Limit has been increased to 128M.
*Verdi* FSDB: For performance reasons, the Memory Size Limit has been increased to 256M.
*Verdi* FSDB: For performance reasons, the Memory Size Limit has been increased to 512M.
simv compiled at Jun 26 2026, 16:00:42
Using simulated 8386560MB RAM
The image is ../nexus-am/apps/amotest/build/amotest-riscv64-xs.bin
Using simulated 32768B flash
*Verdi* FSDB: For performance reasons, the Memory Size Limit has been increased to 1024M.
Old=5 New=7
Core 0: HIT GOOD TRAP at pc = 0x80000166
Core-0 instrCnt = 898, cycleCnt = 10,304, IPC = 0.087151
DIFFTEST WORKLOAD DONE at cycle                10311
$finish called from file "/nfs/home/yanyusong/xs-eda-compile/xs-dual-env/XiangShan/difftest/src/test/vsrc/vcs/DifftestEndpoint.sv", line 390.
$finish at simulation time                20723
           V C S   S i m u l a t i o n   R e p o r t
Time: 20723 ns
CPU Time:    109.090 seconds;       Data structure size: 107.8Mb
Tue Jun 30 14:59:17 2026
```

我们主要关注该条指令在后端 (backend) 模块中的行为. 根据香山官方文档, 后端主要包括译码(Decode), 重命名 (Rename), 分派 (Dispatch), 调度 (Schedule), 发射 (Issue), 执行 (Execute), 写回 (Writeback), 和退休 (Retire) 几个阶段, 我们将依次对该条指令的执行过程进行分析. 本文将使用 VCS 进行软件仿真, 并使用 Verdi 查看波形图, 和使用 Verilator 以及 GTKWave (或 surfer) 查看波形的流程类似.

<details class="lake-collapse"><summary id="u1e0008ff"><span class="ne-text">译码 (Decode) 阶段</span></summary><p id="ub9004451" class="ne-p"><span class="ne-text">香山昆明湖架构默认配置了6个译码器 (也就是说, 单个周期内可以从前端同时获取 6 条指令的信息并进行译码), 我们关注每一个译码器的 </span><code class="ne-code"><span class="ne-text">io_deq_decidedUbst_pc</span></code><span class="ne-text">, 表示当前周期内, 当前的译码器所译码的指令的 PC 值 (可以参考类 </span><code class="ne-code"><span class="ne-text">DecodeUnitIO</span></code><span class="ne-text">, </span><code class="ne-code"><span class="ne-text">DecodeUnitEnqIO</span></code><span class="ne-text">, 以及 </span><code class="ne-code"><span class="ne-text">StaticInst</span></code><span class="ne-text">的定义). 通过阅读波形图, 我们可以发现在第 19121ps, </span><code class="ne-code"><span class="ne-text">decoder_2</span></code><span class="ne-text">(也就是第三个译码器) 对 </span><code class="ne-code"><span class="ne-text">PC=000008000142</span></code><span class="ne-text">的指令进行了译码 (可以通过查看 </span><code class="ne-code"><span class="ne-text">io_enq_ctrlFlow_instr</span></code><span class="ne-text">来确认和汇编代码的指令一致), 这条指令的 </span><code class="ne-code"><span class="ne-text">foldPC</span></code><span class="ne-text">值为 </span><code class="ne-code"><span class="ne-text">0A0</span></code><span class="ne-text">:</span></p><div id="o7iI9" class="ne-text-diagram"><img src="https://cdn.nlark.com/yuque/__mermaid_v3/397a967529342e62d2bf43f2f44ad263.svg"></div><p id="u86b0a9f1" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1782870993851-b56ae4e6-5d2a-4004-9ddc-a5b89b5669c1.png" width="2560" id="u8a0c0a67" class="ne-image"></p><p id="u3fca358f" class="ne-p"><span class="ne-text">通过阅读 DecodeUnit 的代码, 可以发现译码阶段的译码结果输出主要出现在 deq 的 decodedInst 中, 通过查阅 decodedInst 的定义 (在backend/bundle.scala中) 可以找到所有译码结果的输出信号:</span></p><p id="ueef34dec" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1774733345940-5f73e5b9-4c6a-48ba-a88d-64e8d9604805.png" width="714" id="u286d0c07" class="ne-image"></p><p id="ud8b93cea" class="ne-p"><span class="ne-text">DecodedInst分为两类, 第一类是直接从 StaticInst (也就是从前端模块的直接输入) 进行连通, 第二类是真正的译码结果 (图中 decoded 注释下的信号). 阅读 </span><code class="ne-code"><span class="ne-text">decodeDefault</span></code><span class="ne-text">, 我们可以大致了解译码阶段译码器都提取了指令的那些操作信息. </span><code class="ne-code"><span class="ne-text">decodeDefault</span></code><span class="ne-text">还作为发现非法指令的兜底行为, 如果从前端中取得的一条指令没有匹配上任何合法指令的比特位模式, 那么就会匹配这个默认结果, 该结果保证了不去写任何寄存器 (rfWen, fpWen, vecWen 都是 N), 通过 SelImm 是 INVALID_INSTR 表示这是一条非法指令, 从而产生非法指令异常.</span></p><pre data-language="scala" id="g7f0W" class="ne-codeblock language-scala"><code>/**
 * Abstract trait giving defaults and other relevant values to different Decode constants/
 */
trait DecodeConstants {
  // This X should be used only in 1-bit signal. Otherwise, use BitPat(&quot;b???&quot;) to align with the width of UInt.
  def X = BitPat(&quot;b0&quot;)
  def N = BitPat(&quot;b0&quot;)
  def Y = BitPat(&quot;b1&quot;)
  def T = true
  def F = false

def decodeDefault: List\[BitPat] = // illegal instruction
//   srcType(0) srcType(1) srcType(2) fuType    fuOpType    rfWen
//   |          |          |          |         |           |  fpWen
//   |          |          |          |         |           |  |  vecWen
//   |          |          |          |         |           |  |  |  isXSTrap
//   |          |          |          |         |           |  |  |  |  noSpecExec
//   |          |          |          |         |           |  |  |  |  |  blockBackward
//   |          |          |          |         |           |  |  |  |  |  |  flushPipe
//   |          |          |          |         |           |  |  |  |  |  |  |  canRobCompress
//   |          |          |          |         |           |  |  |  |  |  |  |  |  uopSplitType
//   |          |          |          |         |           |  |  |  |  |  |  |  |  |             selImm
List(SrcType.X, SrcType.X, SrcType.X, FuType.X, FuOpType.X, N, N, N, N, N, N, N, N, UopSplitType.X, SelImm.INVALID\_INSTR) // Use SelImm to indicate invalid instr

val decodeArray: Array\[(BitPat, XSDecodeBase)]
final def table: Array\[(BitPat, List\[BitPat])] = decodeArray.map(x => (x.\_1, x.\_2.generate()))
}</code></pre><p id="ud3ef7da3" class="ne-p"><span class="ne-text">拉取相关的信号, 并和手动译码的结果进行比较:</span></p><p id="u4099f20f" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1782886261176-3505f29b-fcdc-4fee-977f-b313cc0b2e39.png" width="2560" id="ufdd239c2" class="ne-image"></p><p id="ufa8eb1e3" class="ne-p"><span class="ne-text">可以从波形图中看出, 这条指令的 </span><code class="ne-code"><span class="ne-text">waitForward</span></code><span class="ne-text">信号为高电平. 本信号经过 Decode 阶段打一拍后进入 Rename 阶段, Rename 阶段会对该指令进行进一步分析 (对于一些 CSR 指令, RISC-V 手册要求不能乱序执行, 但是因为性能原因, 昆明湖选择了乱序执行, 需要对这些指令的 waitForward 信号进行消除), Rename 模块会把该信号打一拍后送往 Dispatch 模块, 之后会进入 IssueQueue, 告知 IssueQueue 需要保证在发射本条指令之前, 其他指令已经被发射到对应的执行单元.</span></p><p id="u38fd6918" class="ne-p"><span class="ne-text">此外, 这条指令的 </span><code class="ne-code"><span class="ne-text">blockBackward</span></code><span class="ne-text">信号为高电平. 和 </span><code class="ne-code"><span class="ne-text">waitForward</span></code><span class="ne-text">类似, 本信号需要告知 IssueQueue 不能在发射这条指令之前, 将其他指令发射到对应的执行单元. 译码过程中并没有 </span><code class="ne-code"><span class="ne-text">waitForward = T</span></code><span class="ne-text">或类似的字段, 其实, 这个信号是由译码过程中的 </span><code class="ne-code"><span class="ne-text">noSpec</span></code><span class="ne-text">信号转化过来, noSpecExec 的意思是不进行推测执行, 实际上就是等待前方的指令都提交完成了再进行执行, 符合 waitForward 的意思, 以下代码展示了其转换的过程:</span></p><pre data-language="scala" id="mM5xw" class="ne-codeblock language-scala"><code>case class FDecode(
src1: BitPat, src2: BitPat, src3: BitPat,
fu: FuType.OHType, fuOp: BitPat, selImm: BitPat = SelImm.X,
uopSplitType: BitPat = UopSplitType.X,
xWen: Boolean = false,
fWen: Boolean = false,
vWen: Boolean = false,
mWen: Boolean = false,
xsTrap: Boolean = false,
noSpec: Boolean = false,
blockBack: Boolean = false,
flushPipe: Boolean = false,
canRobCompress: Boolean = false,
) extends XSDecodeBase {
def generate() : List\[BitPat] = {
XSDecode(src1, src2, src3, fu, fuOp, selImm, uopSplitType, xWen, fWen, vWen, mWen, xsTrap, noSpec, blockBack, flushPipe, canRobCompress).generate()
}
}</code></pre><pre data-language="scala" id="r6reT" class="ne-codeblock language-scala"><code>class DecodedInst(implicit p: Parameters) extends XSBundle {
def numSrc = backendParams.numSrc
// passed from StaticInst
val instr           = UInt(32.W)
val pc              = UInt(VAddrBits.W)
val foldpc          = UInt(MemPredPCWidth.W)
val exceptionVec    = ExceptionVec()
val isFetchMalAddr  = Bool()
val trigger         = TriggerAction()
val preDecodeInfo   = new PreDecodeInfo
val pred\_taken      = Bool()
val crossPageIPFFix = Bool()
val ftqPtr          = new FtqPtr
val ftqOffset       = UInt(log2Up(PredictWidth).W)
val satpFlushFirstFetchFault = Bool()
// decoded
val srcType         = Vec(numSrc, SrcType())
val lsrc            = Vec(numSrc, UInt(LogicRegsWidth.W))
val ldest           = UInt(LogicRegsWidth.W)
val fuType          = FuType()
val fuOpType        = FuOpType()
val rfWen           = Bool()
val fpWen           = Bool()
val vecWen          = Bool()
val v0Wen           = Bool()
val vlWen           = Bool()
val isXSTrap        = Bool()
val waitForward     = Bool() // no speculate execution
val blockBackward   = Bool()
val flushPipe       = Bool() // This inst will flush all the pipe when commit, like exception but can commit
val canRobCompress  = Bool()
val selImm          = SelImm()
val imm             = UInt(ImmUnion.maxLen.W)
val fpu             = new FPUCtrlSignals
val vpu             = new VPUCtrlSignals
val vlsInstr        = Bool()
val wfflags         = Bool()
val isMove          = Bool()
val uopIdx          = UopIdx()
val uopSplitType    = UopSplitType()
val isVset          = Bool()
val firstUop        = Bool()
val lastUop         = Bool()
val numUops         = UInt(log2Up(MaxUopSize).W) // rob need this
val numWB           = UInt(log2Up(MaxUopSize).W) // rob need this
val commitType      = CommitType() // Todo: remove it
val needFrm         = new NeedFrmBundle

```
val debug_fuType    = OptionWrapper(backendParams.debugEn, FuType())
val debug_seqNum    = InstSeqNum()

private def allSignals = srcType.take(3) ++ Seq(fuType, fuOpType, rfWen, fpWen, vecWen,
  isXSTrap, waitForward, blockBackward, flushPipe, canRobCompress, uopSplitType, selImm)
```

}</code></pre><p id="u9e9aaf56" class="ne-p"><span class="ne-text">可以看到, XSDecode 的 generate 顺序中, noSpec 对应了 DecodedInst allSignals 中 waitForward 的位置, 所以说 noSpec 在这里隐式的转换为了 waitForward 信号.</span></p><p id="ud4909098" class="ne-p"><span class="ne-text">紧接着, 这条指令的 </span><code class="ne-code"><span class="ne-text">canRobCompress</span></code><span class="ne-text">信号为低电平. 表示这条指令不可以和其他经过译码的指令共享同一个 ROB 表项. (通过共享 ROB 表项, 可以节省对 ROB 的使用, 效果是可以增加 ROB 一共可以存放的指令数量, 可以帮助处理器提升发射/执行指令的效率)</span></p><div data-type="info" class="ne-alert"><p id="ucb78f002" class="ne-p"><span class="ne-text">香山昆明湖架构的设计文档指出, 目前的所有 AMO 指令实现均默认了指令中的 aq/lr 置位. 也就表示, 默认所有 AMO 指令之前所进行的内存写操作必须在这条 AMO 指令执行前对其他核心全局可见, 且所有 AMO 指令之后的所有内存读操作必须在这条 AMO 指令执行完成后才能执行.</span></p><p id="ua2694aa5" class="ne-p"><span class="ne-text">因此, 译码器必须要将 </span><code class="ne-code"><span class="ne-text">waitForward</span></code><span class="ne-text">信号拉高, 来保证这条指令的 RL 属性. 如果 </span><code class="ne-code"><span class="ne-text">waitForward</span></code><span class="ne-text">信号没有被拉高, 考虑以下几条指令: </span><code class="ne-code"><span class="ne-text">sd x2, 0(x1); amoswap.w.aq.lr x1, x3, (x1)</span></code><span class="ne-text">, </span><code class="ne-code"><span class="ne-text">x2</span></code><span class="ne-text">寄存器的值比</span><code class="ne-code"><span class="ne-text">x3</span></code><span class="ne-text">寄存器的值更晚就绪, 这时如果没有</span><code class="ne-code"><span class="ne-text">waitForward</span></code><span class="ne-text">, </span><code class="ne-code"><span class="ne-text">sd</span></code><span class="ne-text">指令可能比 </span><code class="ne-code"><span class="ne-text">amoswap</span></code><span class="ne-text">指令更晚被送到执行单元, 导致 AMO 指令的 RL 属性被破坏.</span></p><p id="ub3046247" class="ne-p"><span class="ne-text">同理, 译码器必须要将 </span><code class="ne-code"><span class="ne-text">blockBackward</span></code><span class="ne-text">信号拉高, 来保证这条 AMO 指令的 AQ 属性. 如果 </span><code class="ne-code"><span class="ne-text">blockBackward</span></code><span class="ne-text">信号没有被拉高, 考虑以下几条指令: </span><code class="ne-code"><span class="ne-text">amoswap.w.aq.lr x1, x2, (x1); ld x3, 0(x1)</span></code><span class="ne-text">, </span><code class="ne-code"><span class="ne-text">x2</span></code><span class="ne-text">寄存器的值还没有就绪, 这时如果没有 </span><code class="ne-code"><span class="ne-text">waitForward</span></code><span class="ne-text">, </span><code class="ne-code"><span class="ne-text">ld</span></code><span class="ne-text">指令可能比 </span><code class="ne-code"><span class="ne-text">amoswap</span></code><span class="ne-text">指令提前被送到执行单元, 导致 AMO 指令的 AQ 属性被破坏.</span></p><p id="uaf786133" class="ne-p"><span class="ne-text">最后, 译码器必须要将 </span><code class="ne-code"><span class="ne-text">canRobCompress</span></code><span class="ne-text">信号拉低, 来保证这条 AMO 指令不和其他指令共享一个 ROB 表项. 其实只要是 AMO, 访存, 分支, 跳转, 特权指令等可能会导致 PC 值跳转的指令, 都不能和相邻的指令共享一个 ROB表项. 否则考虑有两条指令共享了一个 ROB 表项, 这两条指令分别是一条肯定不会发生异常/跳转的加法指令, 和一条可能出现各种异常 (缺页异常, 非对其内存访问, 权限错误等) 的 AMO 指令. 如果这条 AMO 指令确实出现了异常 (因为出现了异常, 所以不能够提交这条指令, 并且 PC 会跳转到预先设定好的 trap vector), 需要冲刷掉这个 ROB 表项以及后续指令对应的 ROB 表项. 发现在冲刷当前表项的时候, 前一条正常的指令也被冲刷了, 效果相当于这条指令凭空消失了, 会造成处理器的状态转移和指令集定义的状态转移不一致, 这是绝对不可接受的.</span></p></div><p id="uc2ea538a" class="ne-p"><span class="ne-text">这条指令的 </span><code class="ne-code"><span class="ne-text">uopSplitType</span></code><span class="ne-text">为 0, 对应了在译码器代码中没有明确写明如何拆分成多个微操作的默认值, 表示这条 AMO 指令不需要被拆分成多个微操作. 与之不同的是, amocas 指令由于其语义过于复杂, 需要拆分成多个微操作:</span></p><pre data-language="scala" id="FRUFm" class="ne-codeblock language-scala"><code>// XSDecode 构造函数, 前面的几个参数没有默认值, 所有的指令定义都需要手动提供
// 其他的参数有默认值, 如果构造 XSDecode 时候没有给参数, 就会用默认值
case class XSDecode(
src1: BitPat, src2: BitPat, src3: BitPat,
fu: FuType.OHType, fuOp: BitPat, selImm: BitPat,
uopSplitType: BitPat = UopSplitType.X,
xWen: Boolean = false,
fWen: Boolean = false,
vWen: Boolean = false,
mWen: Boolean = false,
xsTrap: Boolean = false,
noSpec: Boolean = false,
blockBack: Boolean = false,
flushPipe: Boolean = false,
canRobCompress: Boolean = false,
) extends XSDecodeBase {
def generate() : List\[BitPat] = {
List (src1, src2, src3, BitPat(fu.U(FuType.num.W)), fuOp, xWen.B, fWen.B, (vWen || mWen).B, xsTrap.B, noSpec.B, blockBack.B, flushPipe.B, canRobCompress.B, uopSplitType, selImm)
}
}

// ...

// RV64A
// 一般的 RV64A AMO 指令都没有手动设置 uopSplitType, 从上面 XSDecode 的定义可以看出, 其默认值为 UopSplitType.X, 也就是不拆分
AMOADD\_W  -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amoadd\_w , SelImm.X, xWen = T, noSpec = T, blockBack = T),
AMOXOR\_W  -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amoxor\_w , SelImm.X, xWen = T, noSpec = T, blockBack = T),
AMOSWAP\_W -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amoswap\_w, SelImm.X, xWen = T, noSpec = T, blockBack = T),
AMOAND\_W  -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amoand\_w , SelImm.X, xWen = T, noSpec = T, blockBack = T),
AMOOR\_W   -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amoor\_w  , SelImm.X, xWen = T, noSpec = T, blockBack = T),
AMOMIN\_W  -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amomin\_w , SelImm.X, xWen = T, noSpec = T, blockBack = T),
AMOMINU\_W -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amominu\_w, SelImm.X, xWen = T, noSpec = T, blockBack = T),
AMOMAX\_W  -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amomax\_w , SelImm.X, xWen = T, noSpec = T, blockBack = T),
AMOMAXU\_W -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amomaxu\_w, SelImm.X, xWen = T, noSpec = T, blockBack = T),
// 下面三个 AMOCAS 指令, 手动定义了 uopSplitType 的值, 会覆盖默认的 X
AMOCAS\_W  -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amocas\_w, SelImm.X, uopSplitType = UopSplitType.AMO\_CAS\_W, xWen = T, noSpec = T, blockBack = T),
AMOCAS\_D  -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amocas\_d, SelImm.X, uopSplitType = UopSplitType.AMO\_CAS\_D, xWen = T, noSpec = T, blockBack = T),
AMOCAS\_Q  -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amocas\_q, SelImm.X, uopSplitType = UopSplitType.AMO\_CAS\_Q, xWen = T, noSpec = T, blockBack = T),</code></pre><p id="u9e534e99" class="ne-p"><span class="ne-text">我们发现 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_srcType\_0</span></code><span class="ne-text">和 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_srcType\_1</span></code><span class="ne-text">都是1, 查阅 </span><code class="ne-code"><span class="ne-text">package.scala</span></code><span class="ne-text">可以找到对srcType的定义:</span></p><pre data-language="bash" id="dkH8s" class="ne-codeblock language-bash"><code>package object xiangshan {
object SrcType {
def imm = "b0000".U
def pc  = "b0000".U
def xp  = "b0001".U
def fp  = "b0010".U
def vp  = "b0100".U
def v0  = "b1000".U
def no  = "b0000".U // this src read no reg but cannot be Any value</code></pre><p id="uf1f62743" class="ne-p"><span class="ne-text">所以波形图中的1表示, 这个源操作数来自xp, 也就是定点寄存器堆. 同时我们也发现 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_lsrc\_0</span></code><span class="ne-text">和</span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_lsrc\_1</span></code><span class="ne-text">也都和我们手动译码的结果一致.</span></p><div data-type="info" class="ne-alert"><p id="u11813ae0" class="ne-p"><span class="ne-text">在香山昆明湖中, 因为我们使用了重命名技术来提高指令的并行性, 所以处理器中就有了「逻辑寄存器」和「物理寄存器」两种寄存器表示方法, 译码器得到的是程序/汇编视角的寄存器编号, 对应「逻辑寄存器」,这也就是为什么信号名称为</span><code class="ne-code"><span class="ne-text">lsrc</span></code><span class="ne-text">. 在重命名阶段, 这些逻辑寄存器将会被分配到处理器中实际存在的「物理寄存器」并记录下和「逻辑寄存器」的对应关系.</span></p></div><p id="ufb894d66" class="ne-p"><span class="ne-text">在信号列表中, 除了 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_srcType\_{0, 1}</span></code><span class="ne-text">以外, 我们还能发现 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_srcType{2, 3, 4}</span></code><span class="ne-text">三路信号. 其中, </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_srcType\_2</span></code><span class="ne-text">是 0, </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_srcType\_3</span></code><span class="ne-text"> 和 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_srcType\_4</span></code><span class="ne-text">都是 4. 说明一共有五路 src, 这是因为, 正常的 RISC-V 指令只有两个源操作数寄存器, 但是对于一些 3R 类型的浮点指令 (例如 fmadd 指令), 就会有三个源操作数寄存器, 所以就有了 </span><code class="ne-code"><span class="ne-text">srcType\_2</span></code><span class="ne-text">, 对于这条 AMO 指令, 译码给出的 </span><code class="ne-code"><span class="ne-text">srcType\_2</span></code><span class="ne-text">是 </span><code class="ne-code"><span class="ne-text">srcType.X</span></code><span class="ne-text">也就对应了波形图中的 0. 对于第 3 个和第 4 个源操作数, 则是给向量指令 (向量 CSR 指令) 而准备的, 译码过程中根据指令的语义进行类型分配, 但是因为给出了默认值, 所以会看到波形图中的两个 4:</span></p><pre data-language="scala" id="TxGLo" class="ne-codeblock language-scala"><code>// 这是默认的 srcType 3 和 4
decodedInst.srcType(3) := Mux(inst.VM === 0.U, SrcType.vp, SrcType.DC) // mask src
decodedInst.srcType(4) := SrcType.vp // vconfig

// ...

when (isCsrrVl) {
// convert to vsetvl instruction
decodedInst.srcType(0) := SrcType.no
decodedInst.srcType(1) := SrcType.no
decodedInst.srcType(2) := SrcType.no
decodedInst.srcType(3) := SrcType.no
decodedInst.srcType(4) := SrcType.vp
// ...
}.elsewhen (isCsrrVlenb) {
// convert to addi instruction
decodedInst.srcType(0) := SrcType.reg
decodedInst.srcType(1) := SrcType.imm
decodedInst.srcType(2) := SrcType.no
decodedInst.srcType(3) := SrcType.no
decodedInst.srcType(4) := SrcType.no
// ...
} // ...</code></pre><p id="u51e7b741" class="ne-p"><span class="ne-text">接下来我们分析译码器计算出的操作码 (Opcode), 波形图显示这是一个独热的值, 查阅</span><code class="ne-code"><span class="ne-text">backend/fu/FuType.scala</span></code><span class="ne-text">发现</span><code class="ne-code"><span class="ne-text">FuType extends OHEnumeration</span></code><span class="ne-text">, 所以会输出一个独热的值 (具体哪一位是高电平取决于chisel如何生成system verilog代码).</span></p><p id="u508d2b4d" class="ne-p"><span class="ne-text">从波形图中可以发现, 这条指令的 </span><code class="ne-code"><span class="ne-text">fuOpType</span></code><span class="ne-text">为 </span><code class="ne-code"><span class="ne-text">0x0A0</span></code><span class="ne-text">, 查阅 </span><code class="ne-code"><span class="ne-text">package.scala</span></code><span class="ne-text">可以找到定义 </span><code class="ne-code"><span class="ne-text">def amoswap\_w = "b001010".U</span></code><span class="ne-text">对应 </span><code class="ne-code"><span class="ne-text">0x0A0</span></code><span class="ne-text">(宽度不是6位, 因为要考虑到其他的功能单元可能需要更长的编码宽度).</span></p><p id="u99ca25b1" class="ne-p"><span class="ne-text">同理, 可以从 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_rfWen = 1</span></code><span class="ne-text">得出这条指令会写入定点寄存器堆, 而 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_fpWen = 0</span></code><span class="ne-text">, </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_vecWen = 0</span></code><span class="ne-text">则表示这条指令不会导致浮点寄存器堆和向量寄存器堆的写入.</span></p><p id="u800492c2" class="ne-p"><span class="ne-text">综上, 译码器的行为匹配在 DecodeUnit 中对这条指令行为的编码:</span></p><pre data-language="bash" id="gMpH0" class="ne-codeblock language-bash"><code>AMOSWAP\_W -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amoswap\_w, SelImm.X, xWen = T, noSpec = T, blockBack = T),</code></pre></details>

<details class="lake-collapse"><summary id="ue577c471"><span class="ne-text">重命名 (Rename) 阶段</span></summary><p id="u893e2151" class="ne-p"><span class="ne-text">指令完成译码后, 将会进入到重命名阶段. 我们发现在译码阶段可以得到当前指令的逻辑源操作数寄存器编号以及逻辑目的地寄存器编号. 但是在香山昆明湖架构中, 我们使用了寄存器重命名技术来消除指令间的伪相关性 (WAW以及WAR相关性), 所以需要在译码阶段结束之后, 把逻辑寄存器 (在这里我们只关注定点寄存器, 所以编号是 0-31) 转换为物理寄存器 (在这里我们只关注定点寄存器, 在香山昆明湖架构中, 每个 CPU 共有 224 个物理定点寄存器).</span></p><div data-type="info" class="ne-alert"><p id="u2e62b07d" class="ne-p"><span class="ne-text">指令间间常见的相关性有RAW (Read After Write, 写后读), WAW (Write After Write, 写后写), 以及 WAR (Write After Read, 读后写) 相关性. 其中 RAW 相关性为「真实的相关性」无法通过重命名技术解决, 但 WAW 和 WAR 相关性被认为是「虚假的相关性」可以通过重命名技术解决.</span></p><p id="u75c3e52a" class="ne-p"><span class="ne-text">RAW 相关性指在程序的指令流中, 更年轻的指令需要读取更年长的指令所写入的寄存器值, 这时候, 更年轻的指令不能比更年长的指令更早地被执行, 因为所需要的操作数还没有被计算出来, 这两条指令的</span><span class="ne-text" style="color: rgb(0, 0, 0); background-color: rgba(0, 0, 0, 0); font-size: 16px">执行与写回顺序不能完全颠倒</span><span class="ne-text">，年轻指令可提前进入发射队列，但必须等待年长指令的结果生成后才能执行. 示例: 程序中包括以下两条连续的指令 </span><code class="ne-code"><span class="ne-text">add x1, x2, x3</span></code><span class="ne-text">以及 </span><code class="ne-code"><span class="ne-text">sub x4, x1, x2</span></code><span class="ne-text">减法指令需要读取寄存器 </span><code class="ne-code"><span class="ne-text">x1</span></code><span class="ne-text">这个寄存器是加法指令所写入的, 所以如果加法指令的结果没有计算出来, 那么减法指令就不能够执行.</span></p><p id="ucde67f47" class="ne-p"><span class="ne-text">WAW 相关性指在程序的指令流中, 更年轻的指令会写入一个寄存器, 这个寄存器被一个更年长的指令所写入. 在这种情况下, 更年轻的指令可以比更年长的指令提前执行 (只是逻辑寄存器的命名存在冲突, 完全可以乱序执行), 但是需要注意的是, 在最后的提交阶段, 必须保证这个逻辑寄存器的值是更年轻的指令所写入的 (否则乱序执行在提交后的状态和原来顺序执行的状态将无法保持一致). 示例: 程序中包括以下两条连续的指令</span><code class="ne-code"><span class="ne-text">add x1, x2, x3</span></code><span class="ne-text">以及</span><code class="ne-code"><span class="ne-text">add x1, x4, x5</span></code><span class="ne-text">此时我们完全可以先执行第二条加法指令, 但是在最后提交的时候, 务必要保证逻辑寄存器</span><code class="ne-code"><span class="ne-text">x1</span></code><span class="ne-text">的值是第二条加法指令的计算结果 (即使第二条指令比第一条指令更早地被执行). </span></p><p id="uedc2c0df" class="ne-p"><span class="ne-text">WAR 相关性指在程序的指令流中, 更年轻的指令会写入一个寄存器, 这个寄存器被一个更年长的指令所读取. 在这种情况下, 更年轻的指令可以比更年长的指令提前执行 (只是逻辑寄存器的命名存在冲突, 完全可以乱序执行). 示例: 程序中包括以下两条连续的指令</span><code class="ne-code"><span class="ne-text">add x2, x1, x3</span></code><span class="ne-text">以及</span><code class="ne-code"><span class="ne-text">add x1, x4, x5</span></code><span class="ne-text">此时我们完全可以先执行第二条指令, 再执行第一条指令 (因为重命名期间会给两条指令的 </span><code class="ne-code"><span class="ne-text">x1</span></code><span class="ne-text">逻辑寄存器分配不同的物理寄存器, 不用担心第二条指令写入后原来的数据被篡改).</span></p></div><div id="jTkzk" class="ne-text-diagram"><img src="https://cdn.nlark.com/yuque/__mermaid_v3/e2513aa2eadd055ea38bd574531cd111.svg"></div><p id="u29f2515c" class="ne-p"><span class="ne-text">在香山昆明湖架构中, 重命名阶段还会对当前的微操作 (uop) 分配 ROB (Re-Order Buffer) 表项, 并维护物理寄存器的空闲列表 (Free List), 我们将通过对照波形图和 Chisel 代码对重命名阶段的行为逐一进行分析.</span></p><p id="u85581b5e" class="ne-p"><span class="ne-text">当指令完成译码阶段, 会被送往</span><code class="ne-code"><span class="ne-text">decodePipeRenameModule</span></code><span class="ne-text">中, 这个模块是和重命名阶段之间的桥梁, 负责接偶缓冲和预处理. 这个模块通过 </span><code class="ne-code"><span class="ne-text">PipelineConnect</span></code><span class="ne-text">进行流水线寄存器打拍, 降低处理器的关键路径长度 (是取得很好的时序的关键). 接下来, 我们把注意力集中到波形图和代码的</span><code class="ne-code"><span class="ne-text">rename</span></code><span class="ne-text">模块.</span></p><p id="u429deb02" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1774994709934-a35dfff8-bab9-475a-b306-9cb27f61d777.png" width="197" id="uaa5c221d" class="ne-image"></p><p id="ue795365c" class="ne-p"><span class="ne-text">通过查看模块之间的关系, 我们可以发现重命名阶段维护了各类物理寄存器堆的空闲列表 (Free List) 以及压缩单元 </span><code class="ne-code"><span class="ne-text">compressUnit</span></code><span class="ne-text">, 空闲列表将用于分配物理寄存器, 压缩单元用于决定哪些指令可以共用一个 ROB 表项. 空闲列表和压缩单元在 </span><code class="ne-code"><span class="ne-text">backend/Rename.scala</span></code><span class="ne-text">中被实例化:</span></p><pre data-language="scala" id="MVYGX" class="ne-codeblock language-scala"><code>val compressUnit = Module(new CompressUnit())
// create free list and rat
val intFreeList = Module(new MEFreeList(IntPhyRegs))
val fpFreeList = Module(new StdFreeList(FpPhyRegs - FpLogicRegs, FpLogicRegs, Reg_F))
val vecFreeList = Module(new StdFreeList(VfPhyRegs - VecLogicRegs, VecLogicRegs, Reg_V, 31))
val v0FreeList = Module(new StdFreeList(V0PhyRegs - V0LogicRegs, V0LogicRegs, Reg_V0, 1))
val vlFreeList = Module(new StdFreeList(VlPhyRegs - VlLogicRegs, VlLogicRegs, Reg_Vl, 1))</code></pre><p id="uc9e8950a" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1782975855437-0ae85ddf-e01c-49b5-80e6-4736998c511d.png" width="2560" id="JV1Ki" class="ne-image"></p><p id="ub8d8ad53" class="ne-p"><span class="ne-text">因为这条指令是由第三个译码单元完成的译码 (在波形图中为</span><code class="ne-code"><span class="ne-text">decoder_2</span></code><span class="ne-text">), 所以拉去这个译码器到重命名阶段的输入, 其前缀应该为 </span><code class="ne-code"><span class="ne-text">io_in_2</span></code><span class="ne-text">表示输入来自第二个译码器.</span></p><p id="u9429b459" class="ne-p"><span class="ne-text">在第 19123ps, 也就是译码单元完成了这条指令的译码工作的下一个周期, 重命名模块的</span><code class="ne-code"><span class="ne-text">ready</span></code><span class="ne-text">输出为高电平, 且</span><code class="ne-code"><span class="ne-text">valid</span></code><span class="ne-text">输入为低电平, 表示该译码器和重命名单元的该输入通道成功进行了握手. 验证其 PC 和译码阶段的 PC 一致, 且逻辑寄存器</span><code class="ne-code"><span class="ne-text">lsrc</span></code><span class="ne-text">和逻辑目的寄存器</span><code class="ne-code"><span class="ne-text">ldest</span></code><span class="ne-text">也都一致,</span><code class="ne-code"><span class="ne-text">lastUop</span></code><span class="ne-text">为高电平表示这条微指令是一条 RISC-V 指令的最后一个微指令 (这条 AMO 指令足够简单, 所以只需要一个微指令就可以完成操作), 所以需要分配 ROB 表项. </span></p><p id="u8a01718f" class="ne-p"><span class="ne-text">在重命名整数指令时, 重命名模块主要需要完成以下工作:</span></p><ol class="ne-ol"><li id="u76e47b09" data-lake-index-type="0"><span class="ne-text">根据指令的语义, 决定是否需要分配新的物理寄存器 (如果是 move 指令或者该指令不写回寄存器堆, 重命名模块就不会发起物理寄存器分配请求)</span></li><li id="uf04d14b7" data-lake-index-type="0"><span class="ne-text">如果需要分配新的物理寄存器, 通知 intFreeList 进行物理寄存器分配并记录分配到的物理寄存器编号</span></li><li id="u368ef4ef" data-lake-index-type="0"><span class="ne-text">如果需要分配新的物理寄存器, 将第 2 步分配到的物理寄存器编号记录到 RAT (Register Alias Table, 也称为 Rename Table)</span></li><li id="u31f04607" data-lake-index-type="0"><span class="ne-text">根据指令的语义, 将指令中给出的逻辑寄存器 (如果有) 映射到物理寄存器</span></li><li id="u959aa373" data-lake-index-type="0"><span class="ne-text">给这条指令分配 ROB 表项</span></li></ol><p id="u689434c6" class="ne-p"><span class="ne-text">译码器给出, 这条指令需要写回寄存器, 而且不是 move 指令, 所以在重命名阶段会发起物理寄存器的分配请求. 可以从上图的 Rename - Physical Register Allocation &amp; Query 部分看到, 在重命名的那个周期, </span><code class="ne-code"><span class="ne-text">io_allocateReq_2</span></code><span class="ne-text">和 </span><code class="ne-code"><span class="ne-text">io_canAllocate</span></code><span class="ne-text">均为高电平, 表示重命名模块对这条指令发起了分配物理寄存器的请求, 该请求会被送往整数物理寄存器的空闲列表 (intFreeList).</span></p><pre data-language="scala" id="x86jb" class="ne-codeblock language-scala"><code>intFreeList.io.allocateReq(i) := needIntDest(i) &amp;&amp; !isMove(i)</code></pre><div data-type="info" class="ne-alert"><p id="ud21a9521" class="ne-p"><span class="ne-text">昆明湖架构支持整数 move 指令消除. 这是一个可以降低物理寄存器使用和整数单元发射队列压力的小优化. 如果一条指令是整数 move 指令, 那他的语义其实就是把源操作数寄存器的值写入了目的地寄存器, 作为一条整数指令其实根本没有用到 ALU, 又因为目的地寄存器所存入的值和源操作数寄存器的值一致, 所以我们其实也没有必要浪费这个物理寄存器 (对于带有重命名功能的处理器, 我们可以节省一个物理寄存器), 直接让逻辑源操作数寄存器和逻辑目的地寄存器都映射到同一个物理寄存器就好了, 这条指令也没有必要进入发射队列, 可以一定程度的减轻发射队列的压力.</span></p></div><p id="u6db52629" class="ne-p"><span class="ne-text">昆明湖的 intFreeList 是一个 </span><code class="ne-code"><span class="ne-text">MEFreeList</span></code><span class="ne-text">类的 FreeList, 继承了抽象类 </span><code class="ne-code"><span class="ne-text">BaseFreeList</span></code><span class="ne-text">的属性 (另有 </span><code class="ne-code"><span class="ne-text">StdFreeList</span></code><span class="ne-text">用于浮点和向量寄存器). 我们可以通过阅读相关的代码了解 intFreeList 的工作原理:</span></p><pre data-language="scala" id="mTmCu" class="ne-codeblock language-scala"><code>abstract class BaseFreeList(size: Int, numLogicRegs:Int = 32)(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelper {
  val io = IO(new Bundle {
    val redirect = Input(Bool())
    val walk = Input(Bool())
    val allocateReq = Input(Vec(RenameWidth, Bool()))
    val walkReq = Input(Vec(RabCommitWidth, Bool()))
    val allocatePhyReg = Output(Vec(RenameWidth, UInt(PhyRegIdxWidth.W)))
    val canAllocate = Output(Bool())
    val doAllocate = Input(Bool())
    val freeReq = Input(Vec(RabCommitWidth, Bool()))
    val freePhyReg = Input(Vec(RabCommitWidth, UInt(PhyRegIdxWidth.W)))
    val commit = Input(new RabCommitIO)
    val snpt = Input(new SnapshotPort)
    val debug_rat = if(backendParams.debugEn) Some(Vec(numLogicRegs, Input(UInt(PhyRegIdxWidth.W)))) else None
  })
  // ...
  val headPtr = RegInit(FreeListPtr(false, 0))
  val headPtrOH = RegInit(1.U(size.W))
  val archHeadPtr = RegInit(FreeListPtr(false, 0))
  // ...
}</code></pre><pre data-language="scala" id="VrGQR" class="ne-codeblock language-scala"><code>class MEFreeList(size: Int)(implicit p: Parameters) extends BaseFreeList(size) with HasPerfEvents {
  val freeList = RegInit(VecInit(
    // originally {1, 2, ..., size - 1} are free. Register 0-31 are mapped to x0.
    Seq.tabulate(size - 1)(i =&gt; (i + 1).U(PhyRegIdxWidth.W)) :+ 0.U(PhyRegIdxWidth.W)))

val tailPtr = RegInit(FreeListPtr(false, size - 1))

val doWalkRename = io.walk && io.doAllocate && !io.redirect
val doNormalRename = io.canAllocate && io.doAllocate && !io.redirect
val doRename = doWalkRename || doNormalRename
val doCommit = io.commit.isCommit

/\*\*
\* Allocation: from freelist (same as StdFreelist)
\*/
val phyRegCandidates = VecInit(headPtrOHVec.map(sel => Mux1H(sel, freeList)))
for (i <- 0 until RenameWidth) {
// enqueue instr, is move elimination
io.allocatePhyReg(i) := phyRegCandidates(PopCount(io.allocateReq.take(i)))
}

// ...

// update head pointer
val numAllocate = Mux(io.walk, PopCount(io.walkReq), PopCount(io.allocateReq))
val headPtrNew   = Mux(lastCycleRedirect, redirectedHeadPtr, headPtr + numAllocate)
val headPtrOHNew = Mux(lastCycleRedirect, redirectedHeadPtrOH, headPtrOHVec(numAllocate))
val headPtrNext   = Mux(doRename, headPtrNew, headPtr)
val headPtrOHNext = Mux(doRename, headPtrOHNew, headPtrOH)
headPtr   := headPtrNext
headPtrOH := headPtrOHNext

// ...
val freeRegCnt = Mux(doWalkRename && !lastCycleRedirect, distanceBetween(tailPtrNext, headPtr) - PopCount(io.walkReq),
Mux(doNormalRename,                     distanceBetween(tailPtrNext, headPtr) - PopCount(io.allocateReq),
distanceBetween(tailPtrNext, headPtr)))
val freeRegCntReg = RegNext(freeRegCnt)
io.canAllocate := freeRegCntReg >= RenameWidth.U
// ...
}</code></pre><p id="u945e1b36" class="ne-p"><span class="ne-text">可以看到, intFreeList 继承了父类的 headPtr, headPtrOH, archHeadPtr 等寄存器. 又定义了自己的 freeList, 这个 freeList 有 224 个寄存器, 每个寄存器又有 8 位宽度. 还定义了自己的 tailPtr, 分配逻辑, 头指针更新逻辑, 释放逻辑, 以及空闲的物理寄存器的计数逻辑. 这个 freeList 会被初始化成 {1, 2, ....., 223, 0}, headPtr 会被初始化成0, 表示在初始化后, 可以从第 0 个表项开始读出空闲物理寄存器的编号. 其 freeRegCnt 逻辑用来记录当前有多少空闲的物理寄存器, 如果当前空闲的物理寄存器的个数小于重命名的宽度 (因为对于整数指令来说, 每一个微操作最多写入一个物理寄存器), 那么当前可能无法完成所有来自重命名的微操作的物理寄存器申请需求, 所以我们就把 canAllocate 拉低, 等待物理寄存器的释放. intFreeList 会根据输入信号, 通过 PopCount 来计算需要分配的物理寄存器的数量, 并根据这个值来更新物理寄存器空闲列表的头指针 (如果出现了重定向, 就要回到重定向时候的头指针, 释放造成重定向的指令之后的指令所作出的物理寄存器分配), 同时会计算其读热码, 存入 headPtrOH.</span></p><p id="ue9556460" class="ne-p"><span class="ne-text">从波形图中可以看出, 在重命名该指令的那个周期, 一共收到了 6 个分配物理寄存器的请求 (也就是说, 每一个微操作都需要申请一个物理寄存器), </span><code class="ne-code"><span class="ne-text">freeRegCntReg = c0</span></code><span class="ne-text">则告诉我们, 目前有充足的物理寄存器供分配, 所以 </span><code class="ne-code"><span class="ne-text">io\_canAllocate</span></code><span class="ne-text">信号被拉高, 和 </span><code class="ne-code"><span class="ne-text">io\_allocateReg\_2</span></code><span class="ne-text">信号成功握手. 可以从波形图中 </span><code class="ne-code"><span class="ne-text">headPtrNew\_new\_value</span></code><span class="ne-text">的值的变化看出, headPtr 从 c1 变成了 c7, 表示当前周期确实分配了 6 个物理寄存器, 所以要读取 freeList 从 c1 到 c7 的 6 个表项. 我们这条 AMO 指令对应的微操作被分配到了 c7 号物理寄存器.</span></p><p id="ufc549a1a" class="ne-p"><span class="ne-text">在分配完物理寄存器后, 还需要把分配信息写入重命名映射表, 告知后续指令最新的寄存器映射关系. 我们通过阅读相关代码了解 RenameTable 的工作原理:</span></p><pre data-language="scala" id="Yu5Lv" class="ne-codeblock language-scala"><code>// 定义重命名表读取端口的输入输出, 用来查询映射关系
class RatReadPort(ratAddrWidth: Int)(implicit p: Parameters) extends XSBundle {
val hold = Input(Bool())
val addr = Input(UInt(ratAddrWidth.W))
val data = Output(UInt(PhyRegIdxWidth.W))
}

// 定义重命名表写入端口的输入输出, 用来写入映射关系
class RatWritePort(ratAddrWidth: Int)(implicit p: Parameters) extends XSBundle {
val wen = Bool()
val addr = UInt(ratAddrWidth.W)
val data = UInt(PhyRegIdxWidth.W)
}

// 重命名表的实现, 所有寄存器类型都基于这个表, 文中只保留了整数部分
class RenameTable(reg\_t: RegType)(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelper {
// ...
val readPortsNum = reg\_t match {
case Reg\_I => 2
}
val rdataNums = reg\_t match {
case Reg\_I => 32
}
val renameTableWidth = reg\_t match {
case Reg\_I => log2Ceil(IntLogicRegs)
}

val io = IO(new Bundle {
val redirect = Input(Bool())
val readPorts = Vec(readPortsNum \* RenameWidth, new RatReadPort(renameTableWidth))
val specWritePorts = Vec(RabCommitWidth, Input(new RatWritePort(renameTableWidth)))
val archWritePorts = Vec(RabCommitWidth, Input(new RatWritePort(renameTableWidth)))
val old\_pdest = Vec(RabCommitWidth, Output(UInt(PhyRegIdxWidth.W)))
val need\_free = Vec(RabCommitWidth, Output(Bool()))
val snpt = Input(new SnapshotPort)
// ...
})

// speculative rename table
val rename\_table\_init = reg\_t match {
case Reg\_I => VecInit.fill    (IntLogicRegs)(0.U(PhyRegIdxWidth.W))
}
val spec\_table = RegInit(rename\_table\_init)
val spec\_table\_next = WireInit(spec\_table)
// arch state rename table
val arch\_table = RegInit(rename\_table\_init)
val arch\_table\_next = WireDefault(arch\_table)
// old\_pdest
val old\_pdest = RegInit(VecInit.fill(RabCommitWidth)(0.U(PhyRegIdxWidth.W)))
val need\_free = RegInit(VecInit.fill(RabCommitWidth)(false.B))
// ... 暂时不研究实现的逻辑
}

// 完整的重命名表, 真实代码中初始化了各类寄存器的重命名表, 暂时不研究实现逻辑
class RenameTableWrapper(implicit p: Parameters) extends XSModule {

// params alias
private val numVecRegSrc = backendParams.numVecRegSrc
private val numVecRatPorts = numVecRegSrc

val io = IO(new Bundle() {
val hartId = Input(UInt(8.W))
val redirect = Input(Bool())
val rabCommits = Input(new RabCommitIO)
val diffCommits = if (backendParams.basicDebugEn) Some(Input(new DiffCommitIO)) else None
val intReadPorts = Vec(RenameWidth, Vec(2, new RatReadPort(IntLogicRegs)))
val intRenamePorts = Vec(RenameWidth, Input(new RatWritePort(IntLogicRegs)))
val int\_old\_pdest = Vec(RabCommitWidth, Output(UInt(PhyRegIdxWidth.W)))
val int\_need\_free = Vec(RabCommitWidth, Output(Bool()))
val snpt = Input(new SnapshotPort)
// ...
})

val intRat = Module(new RenameTable(Reg\_I))
// ...
intRat.io.readPorts <> io.intReadPorts.flatten
// ...
}</code></pre><p id="ue0784c60" class="ne-p"><span class="ne-text">可以看出, 写口定义了写使能, 写地址 (也就是逻辑寄存器的编号), 和写数据 (也就是产生联系的物理寄存器的编号). 在波形图中, 这些值分别是高电平, f, c4. 表示重命名阶段通知映射表记录最新的 15 号 RISC-V 逻辑寄存器对应架构实现的 C4 号寄存器. 因为目前是推测执行指令 (进行了分支预测, 所以这条指令可能不应该被执行, 因为分支预测出现错误才被意外执行), 所以会先写入重命名映射表的 </span><code class="ne-code"><span class="ne-text">spec\_table</span></code><span class="ne-text">(推测执行状态, 顾名思义, 允许出现推测失误), 等到这条指令顺利的提交并离开流水线后, 再更新到 </span><code class="ne-code"><span class="ne-text">arch\_table</span></code><span class="ne-text">(体系结构状态, 需要完全符合指令集的定义). 我们通过拉去 </span><code class="ne-code"><span class="ne-text">spec\_table\_15</span></code><span class="ne-text">的值, 发现在两个周期后被更新成了 C4, 表示映射关系成功被更新.</span></p><p id="u4cd63869" class="ne-p"><span class="ne-text">本条指令有两个整数寄存器类型的源操作数, 所以在重命名阶段需要根据其逻辑寄存器编号, 在重命名表中读出对应的物理寄存器编号. 从 Rename Result 中可以看出, 这条指令的两个源操作数寄存器对应 C2 和 C3 号物理寄存器.</span></p><p id="u353d1d22" class="ne-p"><span class="ne-text">最后, 重命名阶段还需要为这条微操作分配一个 ROB 表项, 这也是整个过程中最关键最复杂的一步, 先阅读相关代码, 后对照波形图分析:</span></p><pre data-language="scala" id="gEzPt" class="ne-codeblock language-scala"><code>class CompressUnit(implicit p: Parameters) extends XSModule{
val io = IO(new Bundle {
val in = Vec(RenameWidth, Flipped(Valid(new DecodedInst)))
val out = new Bundle {
val needRobFlags = Vec(RenameWidth, Output(Bool()))
val instrSizes = Vec(RenameWidth, Output(UInt(log2Ceil(RenameWidth + 1).W)))
val masks = Vec(RenameWidth, Output(UInt(RenameWidth.W)))
val canCompressVec = Vec(RenameWidth, Output(Bool()))
}
})

val noExc = io.in.map(in => !in.bits.exceptionVec.asUInt.orR && !TriggerAction.isDmode(in.bits.trigger))
val uopCanCompress = io.in.map(\_.bits.canRobCompress)
val canCompress = io.in.zip(noExc).zip(uopCanCompress).map { case ((in, noExc), canComp) =>
in.valid && !CommitType.isFused(in.bits.commitType) && in.bits.lastUop && noExc && canComp
}
// ...
}</code></pre><pre data-language="scala" id="GXbku" class="ne-codeblock language-scala"><code>class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelper with HasPerfEvents {

// params alias
private val numRegSrc = backendParams.numRegSrc
private val numVecRegSrc = backendParams.numVecRegSrc
private val numVecRatPorts = numVecRegSrc

println(s"\[Rename] numRegSrc: $numRegSrc")

val io = IO(new Bundle() {
// ...
})
// ...
val compressUnit = Module(new CompressUnit())
// 表示当前状态下, 是否可以输出重命名结果
val canOut = dispatchCanAcc && fpFreeList.io.canAllocate && intFreeList.io.canAllocate && vecFreeList.io.canAllocate && v0FreeList.io.canAllocate && vlFreeList.io.canAllocate && !io.rabCommits.isWalk

compressUnit.io.in.zip(io.in).foreach{ case(sink, source) =>
sink.valid := source.valid && !io.singleStep
sink.bits := source.bits
}
val needRobFlags = compressUnit.io.out.needRobFlags
val instrSizesVec = compressUnit.io.out.instrSizes
val compressMasksVec = compressUnit.io.out.masks

// speculatively assign the instruction with an robIdx
val validCount = PopCount(io.in.zip(needRobFlags).map{ case(in, needRobFlag) => in.valid && in.bits.lastUop && needRobFlag}) // number of instructions waiting to enter rob (from decode)
val robIdxHead = RegInit(0.U.asTypeOf(new RobPtr))
val lastCycleMisprediction = GatedValidRegNext(io.redirect.valid && !io.redirect.bits.flushItself())
val robIdxHeadNext = Mux(io.redirect.valid, io.redirect.bits.robIdx, // redirect: move ptr to given rob index
Mux(lastCycleMisprediction, robIdxHead + 1.U, // mis-predict: not flush robIdx itself
Mux(canOut, robIdxHead + validCount, // instructions successfully entered next stage: increase robIdx
/\* default \*/  robIdxHead))) // no instructions passed by this cycle: stick to old value
robIdxHead := robIdxHeadNext

// ...
for (i <- 0 until RenameWidth) {
// ...
uops(i).robIdx := robIdxHead + PopCount(io.in.zip(needRobFlags).take(i).map{ case(in, needRobFlag) => in.valid && in.bits.lastUop && needRobFlag})
uops(i).instrSize := instrSizesVec(i)
val hasExceptionExceptFlushPipe = Cat(selectFrontend(uops(i).exceptionVec) :+ uops(i).exceptionVec(illegalInstr) :+ uops(i).exceptionVec(virtualInstr)).orR || TriggerAction.isDmode(uops(i).trigger)
when(isMove(i) || hasExceptionExceptFlushPipe) {
uops(i).numUops := 0.U
uops(i).numWB := 0.U
}
if (i > 0) {
when(!needRobFlags(i - 1)) {
uops(i).firstUop := false.B
uops(i).ftqPtr := uops(i - 1).ftqPtr
uops(i).ftqOffset := uops(i - 1).ftqOffset
uops(i).numUops := instrSizesVec(i) - PopCount(compressMasksVec(i) & Cat(isMove.reverse))
uops(i).numWB := instrSizesVec(i) - PopCount(compressMasksVec(i) & Cat(isMove.reverse))
}
}
when(!needRobFlags(i)) {
uops(i).lastUop := false.B
uops(i).numUops := instrSizesVec(i) - PopCount(compressMasksVec(i) & Cat(isMove.reverse))
uops(i).numWB := instrSizesVec(i) - PopCount(compressMasksVec(i) & Cat(isMove.reverse))
}
// ...
}
// ...
}</code></pre><p id="u0685aabc" class="ne-p"><span class="ne-text">从代码片段中可以看出 CompressUnit 是重命名阶段中负责决定是否进行 ROB 压缩的模块, 这个模块并不会对 ROB 发起请求, 也不会直接分配 ROB 表项. 这个模块只根据本拍从重命名模块下发的译码结果, 判断哪些相邻的指令可以共享同一个 ROB 表项. 如果一条微操作想要被 ROB 压缩, 这条微操作首先必须是经过译码单元的合法译码结果, 其次, 必须不是 fused 类型的微操作, 并且必须得是对应指令的最后一条微操作, 这个指令还必须保证在任何情况下都不可以触发异常 (中断), 并且在译码单元已经初步给出了 canRobCompress 的信号. 该模块会对每一个输入微操作, 输出其是否满足 ROB 压缩的条件, 以及是否需要一个 ROB 表项.</span></p><p id="ud08eb5c3" class="ne-p"><span class="ne-text">从重命名模块的代码片段中可以看出, 该模块首先会用 PopCount 来计算有多少个合法的微操作需要分配 ROB 表项. 该模块内部还维护了一个 ROB 表项头的寄存器和相关逻辑. 其意义是: 跟踪并计算接下来第一个空闲的 ROB 表项的编号, 如果发生了重定向, 就更新为重定向的 ROB 表项号; 如果没有发生重定向, 但发生了分支预测失误, 那就更新为分支指令对应的下一个 ROB 表项; 如果没有发生重定向, 且没有发生分支预测失误, 并且当前状态下可以完成重命名, 那就更新成当前的 ROB 表项号 + 这一拍分配的 ROB 表项数量; 否则, 就不更新 ROB 空闲表项头的值.</span></p><p id="u56387807" class="ne-p"><span class="ne-text">重命名模块会对进入重命名的每一个微操作, 通过公式 </span><code class="ne-code"><span class="ne-text">uops(i).robIdx = robIdxHead + count(previous lanes that allocate ROB)</span></code><span class="ne-text">也就是不包括自己, 前面有多少个微操作被分配了 ROB 表项, 来计算当前微操作所对应的 ROB 表项号.</span></p><p id="ud8ec4fde" class="ne-p"><span class="ne-text">从波形图的 Rename - ROB Index Allocation 部分中可以看出, 当前周期的 ROB 空闲表项头为 37 (十六进制), 根据 CompressUnit 的输出, 第一个微操作并不需要占用一个 ROB 表项, 但是其他指令需要, 所以一共需要分配 5 个 ROB 表项. 波形图中的控制信号指示, 当前没有发生重定向, 也没有发生分支预测失败, 并且重命名模块可以当拍完成重命名 (有空闲的物理寄存器供使用). 所以 robIdxHead 被更新为 3B (增加了 5), 重命名模块处理的 6 个微操作分别使用 36, 36, 37, 38, 39, 3A 号 ROB 表项, 这条指令使用 37 号 ROB 表项. 到这一步, 这条指令正式完成了其重命名的过程, 接下来就要对这条指令进行分派和执行后续的流程.</span></p></details>

<details class="lake-collapse"><summary id="ua6f40883"><span class="ne-text">分派 (Dispatch) 阶段</span></summary><p id="u1b45a391" class="ne-p"><span class="ne-text">分派 Dispatch 阶段在重命名 Rename 阶段之后, 进入 ROB, IssueQueue, LSQ 等队列之前, 也是昆明湖控制模块 ControlBlock 的最后一个流水阶段. 该阶段主要完成 3 个任务: 判断来自重命名模块的微操作 (uop) 能不能离开分派模块, 进入调度和发射阶段; 控制 ROB 入队; 以及控制对应的 Issue Queue 入队, 并准备 srcState, LSQ 以及指令相关的信息.</span></p><div id="zpXFw" class="ne-text-diagram"><img src="https://cdn.nlark.com/yuque/__mermaid_v3/7e03874ef8364b494ee2b4c44a8e4d77.svg"></div><p id="ua8279503" class="ne-p"><span class="ne-text">可以从 Control Block 的代码中看出其在控制块流水线中的位置以及大致的接线情况:</span></p><pre data-language="scala" id="QsIFU" class="ne-codeblock language-scala"><code>class CtrlBlockImp( override val wrapper: CtrlBlock)(
  implicit p: Parameters, params: BackendParams) extends
  LazyModuleImp(wrapper) with HasXSParameter with HasCircularQueuePtrHelper
  with HasPerfEvents with HasCriticalErrors 
{
  // ...
  // pipeline between rename and dispatch
  PipeGroupConnect(renameOut, dispatch.io.fromRename, s1_s3_redirect.valid, dispatch.io.toRenameAllFire, &quot;renamePipeDispatch&quot;)
  // ...
  dispatch.io.enqRob.canAccept := enqRob.canAcceptForDispatch &amp;&amp; !enqRob.req.map(x =&gt; x.valid &amp;&amp; x.bits.blockBackward &amp;&amp; enqRob.canAccept).reduce(_ || _)
  dispatch.io.enqRob.canAcceptForDispatch := enqRob.canAcceptForDispatch
  dispatch.io.enqRob.isEmpty := enqRob.isEmpty &amp;&amp; !enqRob.req.map(_.valid).reduce(_ || _)
  dispatch.io.enqRob.resp := enqRob.resp
  rob.io.enq.needAlloc := enqRob.needAlloc
  rob.io.enq.req := enqRob.req
  dispatch.io.robHead := rob.io.debugRobHead
  dispatch.io.stallReason &lt;&gt; rename.io.stallReason.out
  dispatch.io.lqCanAccept := io.lqCanAccept
  dispatch.io.sqCanAccept := io.sqCanAccept
  dispatch.io.fromMem.lcommit := io.fromMemToDispatch.lcommit
  dispatch.io.fromMem.scommit := io.fromMemToDispatch.scommit
  dispatch.io.fromMem.lqDeqPtr := io.fromMemToDispatch.lqDeqPtr
  dispatch.io.fromMem.sqDeqPtr := io.fromMemToDispatch.sqDeqPtr
  dispatch.io.fromMem.lqCancelCnt := io.fromMemToDispatch.lqCancelCnt
  dispatch.io.fromMem.sqCancelCnt := io.fromMemToDispatch.sqCancelCnt
  io.toMem.lsqEnqIO &lt;&gt; dispatch.io.toMem.lsqEnqIO
  dispatch.io.wakeUpAll.wakeUpInt := io.toDispatch.wakeUpInt
  dispatch.io.wakeUpAll.wakeUpFp  := io.toDispatch.wakeUpFp
  dispatch.io.wakeUpAll.wakeUpVec := io.toDispatch.wakeUpVec
  dispatch.io.wakeUpAll.wakeUpMem := io.toDispatch.wakeUpMem
  dispatch.io.IQValidNumVec := io.toDispatch.IQValidNumVec
  dispatch.io.ldCancel := io.toDispatch.ldCancel
  dispatch.io.og0Cancel := io.toDispatch.og0Cancel
  dispatch.io.wbPregsInt := io.toDispatch.wbPregsInt
  dispatch.io.wbPregsFp := io.toDispatch.wbPregsFp
  dispatch.io.wbPregsVec := io.toDispatch.wbPregsVec
  dispatch.io.wbPregsV0 := io.toDispatch.wbPregsV0
  dispatch.io.wbPregsVl := io.toDispatch.wbPregsVl
  dispatch.io.vlWriteBackInfo := io.toDispatch.vlWriteBackInfo
  dispatch.io.robHeadNotReady := rob.io.headNotReady
  dispatch.io.robFull := rob.io.robFull
  dispatch.io.singleStep := GatedValidRegNext(io.csrCtrl.singlestep)
  // ...
}</code></pre><p id="u5d2f3617" class="ne-p"><span class="ne-text">首先分析分派阶段的第一步: 判断 rename 过来的 uop 能不能离开 Dispatch. Dispatch 允许一个 uop 离开的条件是 fromRename(i).fire, 根据 DecoupledIO 的定义, fire 指的是上下游的 ready (在这里是分派模块计算的该模块能不能接收这些信息, 并把这些信息经过处理后送往后续的流水单元中) 和 valid (在这里表示 Rename 模块送来了合法的数据) 都为高电平, 在流水线中表示这握手成功, 握手成功后, Rename 就可以扯下握手成功前送往 Dispatch 的数据, 并传递后续的微操作信息. 通过阅读分派模块 NewDispatch 的代码, 可以找到其 ready 信号的计算逻辑:</span></p><pre data-language="scala" id="tJHpn" class="ne-codeblock language-scala"><code>class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents with HasVLSUParameters {
  // ...
  val io = IO(new Bundle {
    // from rename
    val renameIn = Vec(RenameWidth, Flipped(ValidIO(new DecodedInst)))
    val fromRename = Vec(RenameWidth, Flipped(DecoupledIO(new DynInst)))
    val toRenameAllFire = Output(Bool())
    // enq Rob
    val enqRob = Flipped(new RobEnqIO)
    // IssueQueues
    val IQValidNumVec = Vec(exuNum, Input(UInt(maxIQSize.U.getWidth.W)))
    val toIssueQueues = Vec(IQEnqSum, DecoupledIO(new DynInst))
    // ...
    // from MemBlock
    val fromMem = new Bundle {
      val lcommit = Input(UInt(log2Up(CommitWidth + 1).W))
      val scommit = Input(UInt(log2Ceil(EnsbufferWidth + 1).W)) // connected to `memBlock.io.sqDeq` instead of ROB
      val lqDeqPtr = Input(new LqPtr)
      val sqDeqPtr = Input(new SqPtr)
      // from lsq
      val lqCancelCnt = Input(UInt(log2Up(VirtualLoadQueueSize + 1).W))
      val sqCancelCnt = Input(UInt(log2Up(StoreQueueSize + 1).W))
    }
    //toMem
    val toMem = new Bundle {
      val lsqEnqIO = Flipped(new LsqEnqIO)
    }
    // ...
  })
  // ...
  val isBlockBackward  = VecInit(fromRename.map(x =&gt; x.valid &amp;&amp; x.bits.blockBackward))
  val isWaitForward    = VecInit(fromRename.map(x =&gt; x.valid &amp;&amp; x.bits.waitForward))
  // ...
  for (i &lt;- 0 until RenameWidth){
    // update valid logic
    fromRenameUpdate(i).valid := fromRename(i).valid &amp;&amp; allowDispatch(i) &amp;&amp; !uopBlockByIQ(i) &amp;&amp; thisCanActualOut(i) &amp;&amp;
      lsqCanAccept &amp;&amp; !fromRename(i).bits.eliminatedMove &amp;&amp; !fromRename(i).bits.hasException &amp;&amp; !fromRenameUpdate(i).bits.singleStep
    fromRename(i).ready := allowDispatch(i) &amp;&amp; !uopBlockByIQ(i) &amp;&amp; thisCanActualOut(i) &amp;&amp; lsqCanAccept
    // update src type if eliminate old vd
    fromRenameUpdate(i).bits.srcType(numRegSrcVf - 1) := Mux(ignoreOldVdVec(i), SrcType.no, fromRename(i).bits.srcType(numRegSrcVf - 1))
  }
  // ...

// 以下代码和第 2 个判定条件有关
val uopBlockMatrix = Wire(Vec(renameWidth, Vec(issueQueueNum, Bool())))
val uopBlockMatrixForAssign = allIssueParams.zipWithIndex.map { case (issue, iqidx) => {
val result = uopSelIQMatrix.map(\_(iqidx)).map(x => Mux(io.toIssueQueues(temp).ready, x > issue.numEnq.U, x.orR))
temp = temp + issue.numEnq
result
}}.transpose
uopBlockMatrix.zip(uopBlockMatrixForAssign).map(x => x.*1 := VecInit(x.*2))
uopBlockByIQ := uopBlockMatrix.map(*.reduce(* || \_))
io.toIssueQueues.zip(IQSelUop).map(x => {
x.\_1.valid := x.\_2.valid
x.\_1.bits := x.\_2.bits
})
//...

// 以下代码和第 4 个判定条件有关
val lsqEnqCtrl = Module(new LsqEnqCtrl)
// ...
private val enqLsqIO = lsqEnqCtrl.io.enq
// ...
lsqCanAccept := enqLsqIO.canAccept

// 以下代码和第 1 个判定条件有关
private val conserveFlowTotal = Reg(Vec(RenameWidth, UInt(flowTotalWidth.W)))
when(io.toRenameAllFire){
conserveFlowTotal := conserveFlowTotalRename
}.otherwise(
conserveFlowTotal := conserveFlowTotalDispatch
)
// A conservative allocation strategy is adopted here.
// Vector 'unit-stride' instructions and scalar instructions can be issued from all six ports,
// while other vector instructions can only be issued from the first port
// if is segment instruction, need disptch it to Vldst\_RS0, so, except port 0, stall other.
// The allocation needs to meet a few conditions:
//  1) The lsq has enough entris.
//  2) The number of flows accumulated does not exceed VecMemDispatchMaxNumber.
//  3) Vector instructions other than 'unit-stride' can only be issued on the first port.
for (index <- allowDispatch.indices) {
val flowTotal = conserveFlowTotal(index)
val allowDispatchPrevious = if (index == 0) true.B else allowDispatch(index - 1)
when(isStoreVec(index) || isVStoreVec(index)) {
allowDispatch(index) := (sqFreeCount > flowTotal) && allowDispatchPrevious
}.elsewhen(isLoadVec(index) || isVLoadVec(index)) {
allowDispatch(index) := (lqFreeCount > flowTotal) && allowDispatchPrevious
}.elsewhen(isAMOVec(index)) {
allowDispatch(index) := allowDispatchPrevious
}.otherwise {
allowDispatch(index) := allowDispatchPrevious
}
}
// ...

// 以下代码和第 3 个判定条件有关
private val blockedByWaitForward = Wire(Vec(RenameWidth, Bool()))
blockedByWaitForward(0) := !io.enqRob.isEmpty && isWaitForward(0)
for (i <- 1 until RenameWidth) {
blockedByWaitForward(i) := blockedByWaitForward(i - 1) || (!io.enqRob.isEmpty || Cat(fromRename.take(i).map(*.valid)).orR) && isWaitForward(i)
}
if(backendParams.debugEn){
dontTouch(blockedByWaitForward)
dontTouch(conserveFlows)
}
// Only the uop with block backward flag will block the next uop
val nextCanOut = VecInit((0 until RenameWidth).map(i =>
!isBlockBackward(i)
))
val notBlockedByPrevious = VecInit((0 until RenameWidth).map(i =>
if (i == 0) true.B
else Cat((0 until i).map(j => nextCanOut(j))).andR
))
// for noSpecExec: (robEmpty || !this.noSpecExec) && !previous.noSpecExec
// For blockBackward:
// this instruction can actually dequeue: 3 conditions
// (1) resources are ready
// (2) previous instructions are ready
thisCanActualOut := VecInit((0 until RenameWidth).map(i => !blockedByWaitForward(i) && notBlockedByPrevious(i) && io.enqRob.canAccept))
val thisActualOut = (0 until RenameWidth).map(i => io.enqRob.req(i).valid && io.enqRob.canAccept)
// ...
}</code></pre><p id="ub4e44b04" class="ne-p"><span class="ne-text">从代码中可以看出 </span><code class="ne-code"><span class="ne-text">fromRename(i).ready := allowDispatch(i) && !uopBlockByIQ(i) && thisCanActualOut(i) && lsqCanAccept</span></code><span class="ne-text">定义了 ready 信号的发送逻辑.</span></p><p id="u9647d76e" class="ne-p"><span class="ne-text">首先是 </span><code class="ne-code"><span class="ne-text">allowDispatch(i)</span></code><span class="ne-text">, 主要负责 LSQ 和访存的流量保守控制, 其目的主要是保证 (向量) 访存指令类型的微操作不会超过 LSQ 的可分配的能力 (体现为如果 Load Queue 或者 Store Queue 剩余的表项数超过了可能分配的数量, 就把 </span><code class="ne-code"><span class="ne-text">allowDispatch(i)</span></code><span class="ne-text">信号拉低, 相应的</span><code class="ne-code"><span class="ne-text">fromRename(i).ready</span></code><span class="ne-text">信号也会被拉低). 对于我们要分析的 AMO 指令, 可以看到这种指令有专属的条件分支, AMO 指令在这里不需要检查 Load Queue 或者 Store Queue 的 Free Count 是否小于 flowTotal, 但是仍然受到前面一个 slot (allowDispatchPrevious) 的制约.</span></p><p id="u030ce4ba" class="ne-p"><span class="ne-text">其次是 </span><code class="ne-code"><span class="ne-text">!uopBlockByIQ(i)</span></code><span class="ne-text">, 主要负责保证这条指令所对应的 Issue Queue 能够接收这条指令入队. 从上文代码中可以看出, 由 uopSelIQMatrix (顾名思义, 这是一个为每个微操作来匹配合适的发射队列的矩阵, 记录了何种微操作应该进入何种发射队列) 给当前的 uop 分配发射队列. 如果目标发射队列的 ready 数量不够, 或者同周期分配到这个发射队列的微操作数量超过了 numEnq (也就是这个发射队列一个周期能够接受的入队微操作数量). </span><code class="ne-code"><span class="ne-text">uopBlockByIQ(i)</span></code><span class="ne-text">表示这个微操作因为其对应的发射队列因为资源不足原因, 不能离开分发模块, 反之则可以. 对于我们分析的 AMO 指令来说, 它的功能单元种类是 MOU, 会被分派到支持 AMO 的发射队列中, 如果对应的发射队列已经满了, 那么这个信号就会被拉高电平.</span></p><p id="uf9782595" class="ne-p"><span class="ne-text">然后是 </span><code class="ne-code"><span class="ne-text">thisCanActualOut(i)</span></code><span class="ne-text">, 主要负责保证 ROB 可以接受微操作入队以及满足程序顺序执行的语义约束. 从上述代码中可以看到, </span><code class="ne-code"><span class="ne-text">thisCanActualOut := VecInit((0 until RenameWidth).map(i => !blockedByWaitForward(i) && notBlockedByPrevious(i) && io.enqRob.canAccept))</span></code><span class="ne-text">, 也就是说, 一个微操作 CanActualOut 的条件是: </span><code class="ne-code"><span class="ne-text">!blockedByWaitForward(i)</span></code><span class="ne-text">, </span><code class="ne-code"><span class="ne-text">notBlockedByPrevious(i)</span></code><span class="ne-text">, 和 </span><code class="ne-code"><span class="ne-text">io.enqRob.canAccept</span></code><span class="ne-text">. </span><code class="ne-code"><span class="ne-text">io.enqRob.canAccept</span></code><span class="ne-text">来自 ROB, 表示 ROB 当前能不能接受 Dispatch 入队 (相关定义在 ROB 的实现中, 包括允许入队, 没有 BlockBackward, 可以入队寄存器重命名表等); </span><code class="ne-code"><span class="ne-text">notBlockedByPrevious(i)</span></code><span class="ne-text">处理的是前序微操作的 BlockBackward 信号, 如果前面的微操作要求阻塞后续的指令, 后面的微操作就不能够在同一个周期离开分发模块 (对于本条 AMO 指令, 译码单元拉高了 BlockBackward 信号, 所以这条指令自己是可以离开 Dispatch 的, 但是后面的同组微操作会被阻塞住); </span><code class="ne-code"><span class="ne-text">!blockedByWaitForward(i)</span></code><span class="ne-text">保证带有 waitForward 标记的微操作不能在他前面还有更老的微操作时离开分发模块.</span></p><p id="u96f38db7" class="ne-p"><span class="ne-text">最后是</span><code class="ne-code"><span class="ne-text">lsqCanAccept</span></code><span class="ne-text">, 主要负责保证 LSQ enqueue 模块处于一个可以接受入队请求的状态 (在 LSQWrapper 中定义为 </span><code class="ne-code"><span class="ne-text">io.enq.canAccept := RegNext(ldCanAccept && sqCanAccept && !t2\_update)</span></code><span class="ne-text">也就是说, 至少需要保证 Load Queue 和 Store Queue 都可以接受入队请求). 对于这条 AMO 指令, Dispatch 模块并不会发起 LSQ 入队请求, 但是如果 lsqCanAccept 信号不是高电平, 仍然会导致 ready 为低电平, 阻塞流水线.</span></p><p id="ua4f48a6b" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1783306850342-0fe66d3a-8f42-4fa3-9b39-10cbf35edf8d.png" width="2560" id="u2b1c63fb" class="ne-image"></p><p id="u17270b13" class="ne-p"><span class="ne-text">结合波形图 Dispatch - Check Status 部分分析, 可以看到在第 19125ps 时 (重命名结束后打一拍), 分派单元接收到了重命名单元发来的六条微操作的信息, 包括我们要分析的 AMO 指令. 可以看到所有槽位的 allowDispatch 信号都为高电平, 表示目前仍有可分配的 Load Queue 以及 Store Queue 资源供微操作使用. 接下来我们可以发现, 目前所有的 uopBlockedByIQ 都为低电平, 表示当前重命名阶段传来的六条指令所对应的发射队列都可以接受新的微操作入队. 重点要分析 thisCanActualOut 信号, 可以从波形图中看出, 前两个槽位再进入分派模块的那个周期就已经是高电平了, 说明满足离开分派模块的条件, 但是第三个槽位 (对应本文进行分析的 AMO 指令) 却为低电平, 后面的三个槽位也是低电平. 对于第三个槽位来说, 因为它是一条 AMO 指令, 在译码阶段被标注了 isWaitForward 和 blockBackward, 在刚进入分派阶段的那个时刻, 因为 ROB 非空 (有前序指令还没有离开 ROB, 又入队了两条新的指令), 所以被标记为 isWaitForward 的 AMO 指令不满足没有 blockedByWaitForward 的要求 (在波形图中, blockedByWaitForward 信号为高电平), 所以他的 thisCanActualOut 就是低电平了. 对于后续的三条指令, 因为 AMO 指令有 blockBackward 要求 (可以从波形图中看出, isBlockBackward 为高电平), 所以这三条指令的 </span><code class="ne-code"><span class="ne-text">notBlockedByPrevious</span></code><span class="ne-text">为假, 不能离开分派模块. 经过数个时钟周期后, 可以从波形图中看出信号</span><code class="ne-code"><span class="ne-text">io\_enqRob\_isEmpty</span></code><span class="ne-text">已经变成了高电平, 表示这个时刻 ROB 已经为空了, 那么对于积压在分派模块中的 AMO 指令, </span><code class="ne-code"><span class="ne-text">blockedByWaitForward</span></code><span class="ne-text">为假, 它就可以离开分派模块了. 但是在这个时刻, 因为这条 AMO 指令有 blockBackward 要求, 所以后续的微操作还是不能离开分派模块. 又过了数个始终周期, 可以从波形图中看出, ROB 此刻又为空了 (即这条 AMO 指令已经离开了 ROB, 正式的提交了), 所以后续的三条指令的 thisCanActualOut 被拉高, 被允许离开分派模块了. 在整个过程中, </span><code class="ne-code"><span class="ne-text">*lsqEnqCtrl\_io\_enq\_canAccept</span></code><span class="ne-text">信号始终为高电平, 标识 LSQ enqueue 模块一直可以接受入队请求, 这也是这几条微操作可以离开分派模块的原因之一.</span></p><p id="u4f7cee3f" class="ne-p"><span class="ne-text">然后我们分析分派阶段的第二步: 给 ROB 入队. 在重命名阶段, 我们只对微操作进行 ROB 表项的预分配 (体现为重命名模块会告诉这个微操作其对应的 ROB 表项号是多少, 但是不会真正对 ROB 表项进行写入操作, 因为目前微操作还是顺序的进入流水线, 所以可以保证最终微操作入队的顺序和 ROB 表项分配的顺序一致), 在分派阶段, 我们虽然也不会直接写入 ROB 表项, 但是我们会向 ROB 发起入队请求, 由 ROB 控制模块来写入 ROB 表项 (会在一个周期之后写入进 ROB 表项). 首先分派模块会吧这条指令的信息发送到 ROB 的入队端口, 在 Dispatch 模块的代码中体现如下:</span></p><pre data-language="scala" id="LIpN5" class="ne-codeblock language-scala"><code>class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents with HasVLSUParameters {
// ...
// input for ROB, LSQ
for (i <- 0 until RenameWidth) {
// needAlloc no use, need deleted
io.enqRob.needAlloc(i) := fromRename(i).valid
io.enqRob.req(i).valid := fromRename(i).fire
io.enqRob.req(i).bits := updatedUop(i)
io.enqRob.req(i).bits.hasException := updatedUop(i).hasException || updatedUop(i).singleStep
io.enqRob.req(i).bits.numWB := Mux(updatedUop(i).singleStep, 0.U, updatedUop(i).numWB)
}
// ...
}</code></pre><p id="ucee199ca" class="ne-p"><span class="ne-text">可以从代码中看出, 重命名阶段与分派阶段每一个微操作握手是否成功的信号会被发送到 ROB 的入队端口, 同时也会告知 ROB 是否需要入队 ROB 表项 (有些微操作在重命名阶段被判断为不需要为其分配 ROB 表项, 这是该信号就会被拉低, 就算重命名分派握手成功也不会分配 ROB 表项), 分派模块会吧这条指令的信息打包发送给 ROB 入队端口 (包括对一些来自译码/重命名阶段传来信号的修改). 接下来, ROB 侧会判断能否进行入队操作:</span></p><pre data-language="scala" id="DdSa6" class="ne-codeblock language-scala"><code>class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendParams) extends LazyModuleImp(wrapper)
with HasXSParameter with HasCircularQueuePtrHelper with HasPerfEvents with HasCriticalErrors {
// ...
io.enq.canAccept := allowEnqueue && !hasBlockBackward && rab.io.canEnq && vtypeBuffer.io.canEnq && !io.fromVecExcpMod.busy
io.enq.canAcceptForDispatch := allowEnqueueForDispatch && !hasBlockBackward && rab.io.canEnqForDispatch && vtypeBuffer.io.canEnqForDispatch && !io.fromVecExcpMod.busy
io.enq.resp := allocatePtrVec
val canEnqueue = VecInit(io.enq.req.map(req => req.valid && req.bits.firstUop && io.enq.canAccept))
// ...
}</code></pre><p id="u85a40248" class="ne-p"><span class="ne-text">可以看到, ROB 会保证这个请求是合法的, 并且这个请求来自其对应指令的第一个微操作 (如果不是第一个微操作, 表示这条指令已经发起过 ROB 请求了), 还需要保证目前 ROB 模块的状态允许新的指令入队 (这里重点看 </span><code class="ne-code"><span class="ne-text">!hasBlockBackward</span></code><span class="ne-text">, 这是一个寄存器, 初始化为否, 表示如果目前有 blockBackward 的指令入队了 ROB, 就要停止接受新的指令入队, 对于 AMO 指令来说, 这样做可以严格保证内存序不被打乱). 如果上述条件全部满足, 就会进行 ROB 表项的写入操作:</span></p><pre data-language="scala" id="SZCjm" class="ne-codeblock language-scala"><code>class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendParams) extends LazyModuleImp(wrapper)
with HasXSParameter with HasCircularQueuePtrHelper with HasPerfEvents with HasCriticalErrors {
// ...
// robEntries enqueue
for (i <- 0 until RobSize) {
val enqOH = VecInit(canEnqueue.zip(allocatePtrVec.map(*.value === i.U)).map(x => x.*1 && x.*2))
assert(PopCount(enqOH) < 2.U, s"robEntries$i enqOH is not one hot")
when(enqOH.asUInt.orR && !io.redirect.valid){
connectEnq(robEntries(i), Mux1H(enqOH, io.enq.req.map(*.bits)))
}
}
// ...
// update robEntries valid
for (i <- 0 until RobSize) {
val enqOH = VecInit(canEnqueue.zip(allocatePtrVec.map(*.value === i.U)).map(x => x.*1 && x.*2))
val commitCond = io.commits.isCommit && io.commits.commitValid.zip(deqPtrVec.map(*.value === i.U)).map(x => x.*1 && x.*2).reduce(* || *)
assert(PopCount(enqOH) < 2.U, s"robEntries$i enqOH is not one hot")
val needFlush = redirectValidReg && (Mux(
redirectEnd > redirectBegin,
(i.U > redirectBegin) && (i.U < redirectEnd),
(i.U > redirectBegin) || (i.U < redirectEnd)
) || redirectAll)
when(commitCond) {
robEntries(i).valid := false.B
}.elsewhen(enqOH.asUInt.orR && !io.redirect.valid) {
robEntries(i).valid := true.B
}.elsewhen(needFlush){
robEntries(i).valid := false.B
}
}
// ...
}</code></pre><pre data-language="scala" id="MLEEf" class="ne-codeblock language-scala"><code>object RobBundles extends HasCircularQueuePtrHelper {
// ...
def connectEnq(robEntry: RobEntryBundle, robEnq: DynInst): Unit = {
robEntry.wflags := robEnq.wfflags
robEntry.commitType := robEnq.commitType
robEntry.ftqIdx := robEnq.ftqPtr
robEntry.ftqOffset := robEnq.ftqOffset
robEntry.isRVC := robEnq.preDecodeInfo.isRVC
robEntry.isVset := robEnq.isVset
robEntry.isHls := robEnq.isHls
robEntry.instrSize := robEnq.instrSize
robEntry.rfWen := robEnq.rfWen
robEntry.fpWen := robEnq.dirtyFs
robEntry.dirtyVs := robEnq.dirtyVs
// flushPipe needFlush but not exception
robEntry.needFlush := robEnq.hasException || robEnq.flushPipe
// trace
robEntry.traceBlockInPipe := robEnq.traceBlockInPipe
robEntry.debug\_pc.foreach(* := robEnq.pc)
robEntry.debug\_instr.foreach(* := robEnq.instr)
robEntry.debug\_ldest.foreach(* := robEnq.ldest)
robEntry.debug\_pdest.foreach(* := robEnq.pdest)
robEntry.debug\_fuType.foreach(\_ := robEnq.fuType)
}
// ...
}</code></pre><p id="uaf8783c8" class="ne-p"><span class="ne-text">上述代码将指令的数据写入对应的 ROB entry, 并将该 ROB 表项的有效位置高. 完成入队操作后, AMO 指令还会让 ROB 模块标记这条指令不是 interrupt safe (中断安全) 的, 也就是说, 为了保证 AMO 指令执行的正确性, 步云熙 ROB 在这条指令随便接受外部中断打断:</span></p><pre data-language="scala" id="QQwtq" class="ne-codeblock language-scala"><code>class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendParams) extends LazyModuleImp(wrapper)
with HasXSParameter with HasCircularQueuePtrHelper with HasPerfEvents with HasCriticalErrors {
// ...
// interrupt\_safe
for (i <- 0 until RenameWidth) {
when(canEnqueue(i)) {
// For now, we allow non-load-store instructions to trigger interrupts
// For MMIO instructions, they should not trigger interrupts since they may
// be sent to lower level before it writes back.
// However, we cannot determine whether a load/store instruction is MMIO.
// Thus, we don't allow load/store instructions to trigger an interrupt.
// TODO: support non-MMIO load-store instructions to trigger interrupts
val allow\_interrupts = !CommitType.isLoadStore(io.enq.req(i).bits.commitType) &&
!FuType.isFence(io.enq.req(i).bits.fuType) &&
!FuType.isCsr(io.enq.req(i).bits.fuType) &&
!io.enq.req(i).bits.isVset &&
!FuType.isAMO(io.enq.req(i).bits.fuType)
robEntries(allocatePtrVec(i).value).interrupt\_safe := allow\_interrupts
}
}
// ...
}</code></pre><p id="ud7739846" class="ne-p"><span class="ne-text">不难发现, 因为我们分析的这条指令是 AMO 指令, 所以 </span><code class="ne-code"><span class="ne-text">!FuType.isAMO(io.enq.req(i).bits.fuType)</span></code><span class="ne-text">为假, 也就标志着整个表达式 </span><code class="ne-code"><span class="ne-text">allow\_interrupts</span></code><span class="ne-text">为假, 并将该信息记录到对应的 ROB 表项中. 接下来这条 AMO 指令还会更新 ROB 控制模块的全局状态:</span></p><pre data-language="scala" id="P5vIy" class="ne-codeblock language-scala"><code>class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendParams) extends LazyModuleImp(wrapper)
with HasXSParameter with HasCircularQueuePtrHelper with HasPerfEvents with HasCriticalErrors {
// ...
for (i <- 0 until RenameWidth) {
// we don't check whether io.redirect is valid here since redirect has higher priority
when(canEnqueue(i)) {
val enqUop = io.enq.req(i).bits
val enqIndex = allocatePtrVec(i).value
// store uop in data module and debug\_microOp Vec
debug\_microOp(enqIndex) := enqUop
debug\_microOp(enqIndex).debugInfo.dispatchTime := timer
debug\_microOp(enqIndex).debugInfo.enqRsTime := timer
debug\_microOp(enqIndex).debugInfo.selectTime := timer
debug\_microOp(enqIndex).debugInfo.issueTime := timer
debug\_microOp(enqIndex).debugInfo.writebackTime := timer
debug\_microOp(enqIndex).debugInfo.tlbFirstReqTime := timer
debug\_microOp(enqIndex).debugInfo.tlbRespTime := timer
debug\_lsInfo(enqIndex) := DebugLsInfo.init
debug\_lsTopdownInfo(enqIndex) := LsTopdownInfo.init
debug\_lqIdxValid(enqIndex) := false.B
debug\_lsIssued(enqIndex) := false.B
when (enqUop.waitForward) {
hasWaitForward := true.B
}
val enqTriggerActionIsDebugMode = TriggerAction.isDmode(io.enq.req(i).bits.trigger)
val enqHasException = ExceptionNO.selectFrontend(enqUop.exceptionVec).asUInt.orR
when(enqUop.isWFI && !enqHasException && !enqTriggerActionIsDebugMode) {
hasWFI := true.B
}

```
  robEntries(enqIndex).mmio := false.B
  robEntries(enqIndex).vls := enqUop.vlsInstr
}
```

}

for (i <- 0 until RenameWidth) {
val enqUop = io.enq.req(i)
when(enqUop.valid && enqUop.bits.blockBackward && io.enq.canAccept) {
hasBlockBackward := true.B
}
}
// ...
}</code></pre><p id="u39c58bee" class="ne-p"><span class="ne-text">表现为这条 AMO 指令的 ROB 入队会拉高 ROB 控制模块的 </span><code class="ne-code"><span class="ne-text">hasWaitForward</span></code><span class="ne-text">以及 </span><code class="ne-code"><span class="ne-text">hasBlockBackward</span></code><span class="ne-text">信号, 用来阻止后续的指令进入 ROB (也就无法被调度到发射队列), 用来保证原子指令内存序的正确性.</span></p><p id="uc05d5038" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1783308476510-49e07357-b3cd-471e-9646-99980042b379.png" width="2560" id="u83b76a10" class="ne-image"></p><p id="u6dd78754" class="ne-p"><span class="ne-text">结合波形图的 Dispatch - Enqueue ROB 单元进行分析, 可以从波形图中看出, 分派模块首先吧当前周期的前两条微操作 (指令, 即 AMO 指令之前的 addi 和 li 指令, 这两条指令共用一个 ROB 表项) 送往了 ROB 的入队端口, 这里只吧前两条微操作对应的 valid 置高, 因为后面一个微操作是 AMO 指令, 分派单元会等到整个 ROB 都为空后再进行入队 (可以从波形图中看出, 第 54 个 ROB 表项对应 AMO 前面的 addi 和 li 指令, 在经过数个周期, 这个 ROB 表项的 valid 位被清零之后的几个周期分派模块才对 AMO 指令发起了 ROB 入队操作). 在 AMO 指令的微操作入队 ROB 后, 可以看到 ROB 单元的 hasWaitForward 和 hasBlockBackward 信号都被拉高, 拉高后 ROB 的 canAccept 信号被拉低, 意味着在这条 AMO 指令离开 ROB 之前, ROB 不能再接受其他指令的 ROB 入队操作. 在若干个周期后 (第 19197ps) 这条 AMO 指令对应的 ROB 表项 valid 位被拉低, 表示这条指令离开了 ROB, 在这之后 AMO 的 canAccept 才被拉高, 同时拉低了 hasWaitForward 和 hasBlockBackward 寄存器 (因为这条 AMO 指令成功地离开了流水线), 接下来, 分派阶段的剩下三个微操作便进入了 ROB. 分派模块便可以开始处理下一批微操作了.</span></p><p id="u4a115168" class="ne-p"><span class="ne-text">最后我们来分析分派阶段的最后一步: 给 Issue Queue 入队. 本条 AMO 指令会被识别成使用 MOU 单元的内存操作指令 (在译码过程中, 这条指令的 FuType 被给出为 Mou). 我们可以在 Parameters 模块中找到和内存操作有关的发射队列的信息:</span></p><pre data-language="scala" id="AjI71" class="ne-codeblock language-scala"><code>  val memSchdParams = {
implicit val schdType: SchedulerType = MemScheduler()
val rfDataWidth = 64

```
SchdBlockParams(Seq(
  IssueBlockParams(Seq(
    ExeUnitParams(&quot;STA0&quot;, Seq(StaCfg, MouCfg), Seq(FakeIntWB()), Seq(Seq(IntRD(7, 2)))),
  ), numEntries = 16, numEnq = 2, numComp = 12),
  IssueBlockParams(Seq(
    ExeUnitParams(&quot;STA1&quot;, Seq(StaCfg, MouCfg), Seq(FakeIntWB()), Seq(Seq(IntRD(6, 2)))),
  ), numEntries = 16, numEnq = 2, numComp = 12),
  IssueBlockParams(Seq(
    ExeUnitParams(&quot;LDU0&quot;, Seq(LduCfg), Seq(IntWB(5, 0), FpWB(3, 0)), Seq(Seq(IntRD(8, 0))), true, 2),
  ), numEntries = 16, numEnq = 2, numComp = 12),
  IssueBlockParams(Seq(
    ExeUnitParams(&quot;LDU1&quot;, Seq(LduCfg), Seq(IntWB(6, 0), FpWB(4, 0)), Seq(Seq(IntRD(9, 0))), true, 2),
  ), numEntries = 16, numEnq = 2, numComp = 12),
  IssueBlockParams(Seq(
    ExeUnitParams(&quot;LDU2&quot;, Seq(LduCfg), Seq(IntWB(7, 0), FpWB(5, 0)), Seq(Seq(IntRD(10, 0))), true, 2),
  ), numEntries = 16, numEnq = 2, numComp = 12),
  IssueBlockParams(Seq(
    ExeUnitParams(&quot;VLSU0&quot;, Seq(VlduCfg, VstuCfg, VseglduSeg, VsegstuCfg), Seq(VfWB(4, 0), V0WB(4, 0), VlWB(port = 2, 0)), Seq(Seq(VfRD(6, 0)), Seq(VfRD(7, 0)), Seq(VfRD(8, 0)), Seq(V0RD(2, 0)), Seq(VlRD(2, 0)))),
  ), numEntries = 16, numEnq = 2, numComp = 12),
  IssueBlockParams(Seq(
    ExeUnitParams(&quot;VLSU1&quot;, Seq(VlduCfg, VstuCfg), Seq(VfWB(5, 0), V0WB(5, 0), VlWB(port = 3, 0)), Seq(Seq(VfRD(9, 0)), Seq(VfRD(10, 0)), Seq(VfRD(11, 0)), Seq(V0RD(3, 0)), Seq(VlRD(3, 0)))),
  ), numEntries = 16, numEnq = 2, numComp = 12),
  IssueBlockParams(Seq(
    ExeUnitParams(&quot;STD0&quot;, Seq(StdCfg, MoudCfg), Seq(), Seq(Seq(IntRD(5, 2), FpRD(9, 0)))),
  ), numEntries = 16, numEnq = 2, numComp = 12),
  IssueBlockParams(Seq(
    ExeUnitParams(&quot;STD1&quot;, Seq(StdCfg, MoudCfg), Seq(), Seq(Seq(IntRD(3, 2), FpRD(10, 0)))),
  ), numEntries = 16, numEnq = 2, numComp = 12),
),
  numPregs = intPreg.numEntries max vfPreg.numEntries,
  numDeqOutside = 0,
  schdType = schdType,
  rfDataWidth = rfDataWidth,
)
```

}</code></pre><p id="u17d77268" class="ne-p"><span class="ne-text">可以看出, 目前的昆明湖架构一共有 9 个内存操作的发射队列, 每个发射队列能储存最多 16 条微操作, 每个周期最多可以支持 2 个操作的入队. 从参数中可以看到, AMO 地址侧和数据测分别对应 </span><code class="ne-code"><span class="ne-text">STA</span></code><span class="ne-text">和 </span><code class="ne-code"><span class="ne-text">STD</span></code><span class="ne-text">. 其中 STA 表示 Store Address 用来进行地址侧计算, STD 表示 Store Data 用来进行数据侧计算. 对于 AMO 类型的指令, 地址侧走 MouCfg, 数据侧走 MoudCfg. 虽然说 AMO 指令的执行需要 STA 执行单元和 STD 执行单元, 但是这不意味着分派阶段会吧这一个微操作送入 STA 和 STD 的两个发射队列, NewDispatch 在一开始就过滤掉了 STD 的 Issue Queue, 所以分派阶段并不会给 STD 的发射队列发送微操作的信息, STD 发射队列的微操作信息是由后面的 Mem Scheduler 从 STA 的发射队列中复制过来的, 我们可以从分派模块的代码中找到线索:</span></p><pre data-language="scala" id="rz5B5" class="ne-codeblock language-scala"><code>class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents with HasVLSUParameters {
// std IQ donot need dispatch, only copy sta IQ, but need sta IQ's ready && std IQ's ready
val allIssueParams = backendParams.allIssueParams.filter(*.StdCnt == 0)
// ...
}</code></pre><p id="u84cef11d" class="ne-p"><span class="ne-text">接下来, 分派模块会根据译码阶段给出的功能单元类型选择入队的发射队列目标, 对于本条 AMO 指令, 会选择 STA 的发射队列. 下面的这段代码就是在对每个重命名阶段的输入计算与其匹配的执行单元类型:</span></p><pre data-language="scala" id="kYrVY" class="ne-codeblock language-scala"><code>class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents with HasVLSUParameters {
// ...
val fuTypeOH = Wire(Vec(renameWidth, Vec(needMultiExu.size, Bool())))
fuTypeOH.zip(renameIn).map{ case(oh, in) => {
oh := fuConfigSeq.map(x => x.map(xx => in.bits.fuType(xx.fuType.id)).reduce(* || *) && in.valid)
}
}
// ...
}</code></pre><p id="ub3bbcb30" class="ne-p"><span class="ne-text">这段代码首先定义了一个二维 Chisel Bool 类型的矩阵, 第一维对应重命名模块输入的槽位, 第二维对应第几类可选的执行单元, 如果这个二维数组的某一项为高电平, 意味着该槽位的微操作可以通过该类型的执行单元执行. 接下来的代码会对每个重命名阶段的输入遍历 fuConfigSeq 里的每一个 FU 配置, 如果这一个 FU 配置支持多个 fuType, 只要当前微操作的 fuType 命中了其中一种, 就认为这个微操作匹配这个功能单元, 该结果会与输入的 valid 信号进行与计算, 防止将无效的微操作送进发射队列中. 对于本条 AMO 指令, 其 fuType 是 mou, 因此会命中包含 MouCfg 的发射队列, 也就是 Parameters 中定义的 STA0 和 STA1 发射队列. 有了这个信息, 接下来会生成给各个发射队列的入队信号 (微操作信息):</span></p><pre data-language="scala" id="E4Vcc" class="ne-codeblock language-scala"><code>class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents with HasVLSUParameters {
// ...
allIssueParams.zipWithIndex.map{ case(issue, iqidx) => {
for (i <- 0 until issue.numEnq){
val oh = Wire(Vec(renameWidth, Bool())).suggestName(s"oh\_IQSelUop*$temp")
oh := uopSelIQMatrix.map(*(iqidx)).map(* === (i+1).U)
IQSelUop(temp) := PriorityMux(oh, fromRenameUpdate)
// there only assign valid not use PriorityMuxDefalut for better timing
IQSelUop(temp).valid := PriorityMuxDefault(oh.zip(fromRenameUpdate.map(*.valid)), false.B)
val allFuThisIQ = issue.exuBlockParams.map(*.fuConfigs).flatten.toSet.toSeq
val hasStaFu = !allFuThisIQ.filter(*.name == "sta").isEmpty
for (j <- 0 until numRegSrc){
val maskForStd = hasStaFu && (j == 1)
val thisSrcHasInt = allFuThisIQ.map(x => {x.srcData.map(xx => {if (j < xx.size) IntRegSrcDataSet.contains(xx(j)) else false}).reduce(* || *)}).reduce(* || *)
val thisSrcHasFp  = allFuThisIQ.map(x => {x.srcData.map(xx => {if (j < xx.size) FpRegSrcDataSet.contains(xx(j))  else false}).reduce(* || *)}).reduce(* || *)
val thisSrcHasVec = allFuThisIQ.map(x => {x.srcData.map(xx => {if (j < xx.size) VecRegSrcDataSet.contains(xx(j)) else false}).reduce(* || *)}).reduce(* || *)
val thisSrcHasV0  = allFuThisIQ.map(x => {x.srcData.map(xx => {if (j < xx.size) V0RegSrcDataSet.contains(xx(j))  else false}).reduce(* || *)}).reduce(* || *)
val thisSrcHasVl  = allFuThisIQ.map(x => {x.srcData.map(xx => {if (j < xx.size) VlRegSrcDataSet.contains(xx(j))  else false}).reduce(* || *)}).reduce(* || *)
val selSrcState = Seq(thisSrcHasInt || maskForStd, thisSrcHasFp || maskForStd, thisSrcHasVec, thisSrcHasV0, thisSrcHasVl)
IQSelUop(temp).bits.srcState(j) := PriorityMux(oh, allSrcState)(j).zip(selSrcState).filter(*.*2 == true).map(*.*1).foldLeft(false.B)(* || \_).asUInt
}
}}
// ...
}</code></pre><p id="ucf501751" class="ne-p"><span class="ne-text">这段代码将重命名阶段输出的多个微操作, 根据 uopSelIQMatrix (根据上文的 fuTypeOH 计算而得) 分发到各个发射队列的入队端口, 并发送源操作数的状态. 在分派阶段, 分派模块会读取每一个寄存器的状态, 并保存到 allSrcState 中, 以备发送到发射队列中:</span></p><pre data-language="scala" id="g5Pt4" class="ne-codeblock language-scala"><code>class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents with HasVLSUParameters {
// ...
// RegCacheTagTable Module
val rcTagTable = Module(new RegCacheTagTable(numRegSrcInt \* renameWidth))
// BusyTable Modules
val intBusyTable = Module(new BusyTable(numRegSrcInt \* renameWidth, backendParams.numPregWb(IntData()), IntPhyRegs, IntWB()))
val fpBusyTable = Module(new BusyTable(numRegSrcFp \* renameWidth, backendParams.numPregWb(FpData()), FpPhyRegs, FpWB()))
val vecBusyTable = Module(new BusyTable(numRegSrcVf \* renameWidth, backendParams.numPregWb(VecData()), VfPhyRegs, VfWB()))
val v0BusyTable = Module(new BusyTable(numRegSrcV0 \* renameWidth, backendParams.numPregWb(V0Data()), V0PhyRegs, V0WB()))
val vlBusyTable = Module(new VlBusyTable(numRegSrcVl \* renameWidth, backendParams.numPregWb(VlData()), VlPhyRegs, VlWB()))
vlBusyTable.io\_vl\_Wb.vlWriteBackInfo := io.vlWriteBackInfo
val busyTables = Seq(intBusyTable, fpBusyTable, vecBusyTable, v0BusyTable, vlBusyTable)
// ...
val allSrcState = Wire(Vec(renameWidth, Vec(numRegSrc, Vec(numRegType, Bool()))))
for (i <- 0 until renameWidth){
for (j <- 0 until numRegSrc){
for (k <- 0 until numRegType){
if (!idxRegType(k).contains(j)) {
allSrcState(i)(j)(k) := false.B
}
else {
val readidx = i \* idxRegType(k).size + idxRegType(k).indexOf(j)
val readEn = k match {
case 0 => SrcType.isXp(fromRename(i).bits.srcType(j))
case 1 => SrcType.isFp(fromRename(i).bits.srcType(j))
case 2 => SrcType.isVp(fromRename(i).bits.srcType(j))
case 3 => SrcType.isV0(fromRename(i).bits.srcType(j))
case 4 => true.B
}
allSrcState(i)(j)(k) := readEn && busyTables(k).io.read(readidx).resp || SrcType.isImm(fromRename(i).bits.srcType(j))
}
}
}
}
// ...
}</code></pre><p id="uecf430ff" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1783319687733-a9995e02-27dd-40d8-9787-8cd6be6564b5.png" width="2560" id="uc99515ff" class="ne-image"></p><p id="u5510eaa2" class="ne-p"><span class="ne-text">在波形图中可以看出, 这条 AMO 指令被选择分配到了第 11 号发射队列, 第一个源操作数寄存器已经就绪, 但是第二个源操作数寄存器还是繁忙状态.</span></p><p id="ua5b83a4f" class="ne-p"><span class="ne-text">到此, 这条指令就完成了分派的过程, 离开了控制块, 即将进入调度和执行阶段.</span></p></details>

<details class="lake-collapse"><summary id="u7dde2452"><span class="ne-text">调度 (Schedule) 和发射 (Issue) 阶段</span></summary><p id="u3cd39de9" class="ne-p"><span class="ne-text">在分派阶段结束后, 调度阶段会将该指令的信息发送到对应的发射队列, 指令会在发射阶段等待被发射到对应的执行功能单元. 对于一条 AMO 指令, 会被拆分成两个微操作 (uop), 一个是 Sta, 用于计算该 AMO 指令的内存地址; 另一个是 Std, 用于计算该 AMO 指令的内存写入数据. 这两条微操作被分配了同样的 ROB 表项号, 同样的 load queue 表项号, 以及同样的 store queue 表项号, 用来保证该 AMO 指令的原子性 (如果使用了不一样的 ROB 表项号, 那么可能会发生前面一条微指令被提交, 后面一条微指令没有被提交的情况, 打破原子性; 如果使用了不一样的load store 表项号, 那么在这之间可能会有其他的内存操作, 如果内存操作地址和这条 AMO 指令的地址发生了碰撞, 也会打破内存操作的原子性).</span></p><p id="ub8f03c02" class="ne-p"><span class="ne-text">我们可以从波形图中看出, 本条 AMO 原子指令流入了第 1 个 (从 0 开始数) Mou 发射队列, 即 IssueQueueStaMou_1 和 IssueQueueStdMoud_1. 在第 19038ps, enq valid 信号被拉高, 表示这两条微指令同时进入了 Mou 的发射队列. 我们可以发现, Sta Mou 的 psrc 是 C6, 对应重命名阶段的 a5 寄存器, 也就是存储内存地址的寄存器; Std Mou 的 psrc 是 C7, 对应重命名阶段的 a4 寄存器, 也就是存储待交换的数据的寄存器. 我们也可以看到, 这两条微指令都被分配了 2F 号 ROB 表项、 34 号 Store Queue 表项、以及 36 号 Load Queue 表项:</span></p><p id="u52434587" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1782203616159-302226e4-1548-4430-8546-f0723d78f4f0.png" width="1152" id="u3fcf2fb1" class="ne-image"></p><p id="u14928894" class="ne-p"><span class="ne-text">在 IssueQueue enqueue 信号被拉高后的下一个周期, 我们可以看到 Sta 和 Std 两个 IssueQueue 的 enqEntries_0 的 entryReg 都反映出了这条 AMO 指令的数据 (如下图), 新的 entryReg 对应 ROB 表项号 2F, 也有对应的物理寄存器编号:</span></p><p id="u9cacf934" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1782266423755-b38d4ed8-c89b-4fda-a6b0-f65602bdd54a.png" width="1152" id="u4877f9a0" class="ne-image"></p><p id="uab5a9f72" class="ne-p"><span class="ne-text">在这条 AMO 指令对应的微操作入队 Sta 和 Std Issue Queue 的下一个周期, 其对应的 enteryReg 的 issued 信号被拉高, 表示两个微操作成功被发射到执行单元:</span></p><p id="u4f1fefa5" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1782270723714-e864ef0e-a1d9-430e-b22d-e11a1b7b844f.png" width="1353.6" id="u0dc88a1f" class="ne-image"></p></details>
<details class="lake-collapse"><summary id="u344cd254"><span class="ne-text">执行 (Execute) 阶段</span></summary><p id="u7b3048c8" class="ne-p"><span class="ne-text">在前文所描述的「发射」阶段结束后 (AMO 指令的 Sta 和 Std 微操作进入发射队列后, issued 信号被置高), 便进入了这条指令的执行阶段. 回顾一下这条 AMOSWAP 指令的语义: 将寄存器 </span><code class="ne-code"><span class="ne-text">a4</span></code><span class="ne-text">中的值原子性的交换寄存器</span><code class="ne-code"><span class="ne-text">a5</span></code><span class="ne-text">所指向的内存值, 并将原内存中的值存入到</span><code class="ne-code"><span class="ne-text">a5</span></code><span class="ne-text">寄存器中.</span></p><p id="u3b2bb355" class="ne-p"><span class="ne-text">为了执行这条指令, 需要获取 (计算) 内存地址和需要写入内存的值. 从前文的分析可以看出, 这条指令被拆分成了两个微操作, 一个 Sta 用来获取内存地址, 一个 Std 用来获取内存待写入的数据. 在指令被发射的一个时钟周期后 (也就是波形图的第 19044ps), 内部数据通路 </span><code class="ne-code"><span class="ne-text">backend.inner_dataPath</span></code><span class="ne-text">的 toMemExu 1 和 8 分别收到了这条指令的两个微操作 (见下图中 </span><span class="ne-text" style="color: #DF2A3F">红色</span><span class="ne-text"> 方框标出的信号).</span></p><p id="u434e9103" class="ne-p"><span class="ne-text">其中 toMemExu_1 的 rfWen 被置高, 该路信号中包括了待写入的物理寄存器编号 (C8 号物理寄存器, 和重命名阶段分配的物理寄存器一致), 且该路信号的 src0 值为 </span><code class="ne-code"><span class="ne-text">0x0000000080009fdc</span></code><span class="ne-text">对应了这条 AMO 指令所修改的内存地址, 因此可以判断出, 该 Exu 负责执行 Sta 微操作 (计算 AMO 的内存操作地址, 因为 AMO 指令会吧该内存地址的源内存数据写会地址寄存器, 所以该数据通路还包括了物理目的地寄存器编号).</span></p><p id="u848ddea3" class="ne-p"><span class="ne-text">另外一个数据通路: toMemExu_8 则更加简单一些, 没有物理目的地寄存器的编号, 该路信号的 src0 值为 </span><code class="ne-code"><span class="ne-text">0x0000000000000007</span></code><span class="ne-text">对应了这条 AMO 指令所要给内存带来的新值, 因此可以判断出, 该 Exu 负责执行 Std 微操作 (计算 AMO 指令所要更新内存的数据值). 数据通路中包含了握手信号, 只有在 ready 和 valid 同时被双方置高后, 表示下游模块对执行微操作准备就绪, 并且成功收到了源操作数和其他相关的信息.</span></p><p id="u50ec32d2" class="ne-p"><span class="ne-text">一个时钟周期后 (第 19046ps), 可以从波形图 (见下图 </span><span class="ne-text" style="color: #FBDE28">黄色</span><span class="ne-text"> 方框标出的信号) 中看出, memblock 的 atomicUnit 已经收到了这条指令的操作数 (Store Queue, Load Queue, 内存地址). 但是我们可以发现, 对 dcache 的访问地址计算却延迟了数个周期, 原因是香山处理器是支持虚拟地址的, 而且香山昆明湖架构的 L1 DCache (数据缓存的) 是 VIPT (Virtual Index Physical Tagged, 意思是缓存的 index 是根据虚拟地址来的, 但是 tag 是根据物理地址来的), 所以说在访问缓存前需要进行地址翻译 (Address Translation, 即将这条内存指令的虚拟访问地址翻译成物理访问地址), 虽然本程序作为裸机程序 (没有设置 satp 寄存器), 并不需要进行地址翻译, 内存单元的状态机仍然需要花费始终周期进行这一步.</span></p><p id="u2364d0b8" class="ne-p"><span class="ne-text">在第 19052ps, 内存单元“完成”了地址翻译, 并将物理地址 (也就是 a5 逻辑寄存器中的值) 送到了 dcache 的访问请求地址上, 比 AMO 所要更新的数据 (0x5) 晚了几个周期.</span></p><p id="u3d870db1" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1782294146480-2a15bf24-d358-456c-bc66-00fb8a1488c7.png" width="1440" id="u9726bc7e" class="ne-image"></p><p id="u4785ab06" class="ne-p"><span class="ne-text">计算出当前 AMO 指令的内存地址之后, 我们不能直接进行 dcache 的访问. 根据香山昆明湖架构的文档, 目前所有的原子指令的执行一律按照 aq/lr 置位处理 (在 RISC-V 指令集中, RL - release 表示该原子指令之前的左右内存写操作必须在原子指令执行前对其他核心可见, AQ - acquire 表示该原子指令之后的所有内存操作必须在原子指令执行完成后才能执行), 香山昆明湖架构的内存模块 (memblock) 设有 Store Buffer (作为内存写操作相关指令被 ROB 提交后, 写入 L1-DCache 之前的缓冲, 用来进行合并/解耦提交与缓存写入), 为了避免出现数据竞争, 在开始执行这条 AMO 指令之前需要清空 Store Buffer (以防 Store Buffer 中有对这个地址或临近重合地址的写入操作没有反馈到 DCache 中, 这时候进行 DCache 访问将不会得到当前访问内存地址的最新值, 也就破坏了 LR 语义), 只有将 Store Buffer 中的表项全部清空 (将修改写入 DCache), 后续 DCache 的读写才能基于最新的正确值操作.</span></p><p id="u521bea86" class="ne-p"><span class="ne-text">观察波形图可以看到, 在 memblock 的 atomicUnit 开始处理这条 AMO 指令的时候 (可以从第 19046ps 开始观察下图中 </span><span class="ne-text" style="color: #FBDE28">黄色</span><span class="ne-text"> 方框), 这时候的 Store Queue (一共有 16 个表项, 每个表项有 </span><code class="ne-code"><span class="ne-text">stateVec_{N}_state_valid</span></code><span class="ne-text">来表示其分配情况, 可以看到目前 0, 1, 2, 3, 4, 7 六个表项都被分配了. 在第 19050ps (也就是 atomicUnit 收到 AMO 操作后的下一个周期), Store Buffer 的 flush_valid 信号被置高, 意为 atomicUnit 希望清空 Store Buffer. 在这之后的数个周期内, 可以看到 之前被占用的 Store Buffer 表项从小到大逐渐被释放了 (也就表示这些修改反映到了 DCache 中). 待全部清空后 flush_empty 信号被置高, 表示 Store Buffer 没有占用的表项了. 从这个时刻开始, 这条 AMO 指令可以被执行了.</span></p><p id="u315476fb" class="ne-p"><span class="ne-text">一个时钟周期后 (第 19076ps 下图中蓝色箭头所指的时刻), 也就是 flush_empty 被置高的下一个周期, 可以从 atomicUnit 对 DCache 的输出中发现, 这条 AMO 指令的内存访问地址和待写入的内存数据 (包括写掩码) 都已经提前准备好了. 这时 </span><code class="ne-code"><span class="ne-text">io_dcache_req_valid</span></code><span class="ne-text">被置高, 且与 </span><code class="ne-code"><span class="ne-text">io_dcache_req_ready</span></code><span class="ne-text">成功握手, 标识这条 AMO 指令的执行已经进入了 DCache, 接下来等待 DCache 的返回 (resp) 信号被置高, 即可收到内存中的旧值. 另外观察, 握手成功后, DCache 的 Request Ready 信号被置低, 表示着从现在开始, 到缓存更新结束, DCache 不能接受其他来自 atomicUnit 的操作指令.</span></p><p id="u488036f9" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1782294918489-4444ca54-8a81-4b2d-9e00-bbb06e0b7087.png" width="1440" id="uee690f09" class="ne-image"></p><p id="u1faccbb8" class="ne-p"><span class="ne-text">等待四个周期后 (下图中第 19086ps 或红色箭头所指向的时刻), </span><code class="ne-code"><span class="ne-text">io_dcache_resp_valid</span></code><span class="ne-text">信号被拉高, 表明 DCache 已经完成了这项原子内存操作, 并且通过 </span><code class="ne-code"><span class="ne-text">io_dcache_resp_bits_data</span></code><span class="ne-text">返回了内存中的旧数据. 因为 DCache 成功地完成了这项原子内存操作, 所以现在 DCache 重新进入了空闲状态, 其 ready 握手信号再次被置高, 等待下一次内存操作握手.</span></p><p id="u955f6e84" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1782294960827-820bcc86-f314-4345-871f-0fdb783e8149.png" width="1440" id="u4f6e3ad6" class="ne-image"></p><p id="uf3f423ea" class="ne-p"><span class="ne-text">进行到这一步, 这条指令便成功的被 atomicUnit 执行, 得到了这条指令的结果 (内存中的旧值, 也是要写入目的地寄存器的数据), 接下来就由旁路网络将数据提前转发给后续可能要用到这个物理寄存器的指令, 并在下一个周期进入写回 (Writeback) 阶段.</span></p></details>
<details class="lake-collapse"><summary id="u569e4a86"><span class="ne-text">写回 (Writeback) 阶段</span></summary><p id="uab5d5560" class="ne-p"><span class="ne-text">指令完成执行后, 需要吧执行的结果写入物理寄存器堆, 所以我们就进入了写回 (Writeback) 阶段. 在 atomicUnit 将 </span><code class="ne-code"><span class="ne-text">io_lsu_atomics_resp_valid</span></code><span class="ne-text">置高两个周期后 (波形图中的第 18500ps), 这条原子指令的执行结果终于流入了写回单元中. 可以从 backend 的 wbDataPath 的 </span><code class="ne-code"><span class="ne-text">io_fromMemExu_2_0_bits_robIdx_value</span></code><span class="ne-text">中找到这条指令所对应的 ROB 表项号 (2F), 其待写入的数据 (</span><code class="ne-code"><span class="ne-text">io_fromMemExu_2_0_bits_data_0</span></code><span class="ne-text">), 物理寄存器写使能, 以及 LoadQueue 表项号都与这条 AMO 指令相对应, 且该输入通道的 valid 信号为高, 表明这条指令流入了写回阶段.</span></p><p id="u2a6fe01c" class="ne-p"><span class="ne-text">接下来, 由于香山昆明湖架构中的存在非常多的执行单元, 所以要处理很多路写回信号 (尤其是整数类型的操作), 如果直接让每个执行单元都能直接写会物理寄存器堆, 那么我们的物理寄存器堆将会有很多的写口, 但是这些写口一般不会都需要真正的写入 (很多时候只有部分写口 valid 为高电平, 需要进行写回操作). 过量的写口会加大后端布局布线以及时序收敛的难度, 导致处理器的面积和频率不理想. 因此, 香山昆明湖处理器在后端引入了仲裁单元 (arbiter), 仲裁单元负责决定在每个周期, 那个执行单元可以向物理寄存器堆中写入数据. 这样可以大幅度减少物理寄存器堆写口的数量, 帮助控制面积, 优化时序.</span></p><p id="u2026d6ad" class="ne-p"><span class="ne-text">我们可以从下面的波形图中看出, 来自内存执行单元的 </span><code class="ne-code"><span class="ne-text">io_fromMemExu_2_*</span></code><span class="ne-text">信号被路由到了</span><code class="ne-code"><span class="ne-text">intWbArbiter.io_in_12_*</span></code><span class="ne-text">, 经过了内部的写回仲裁器, 再被路由到</span><code class="ne-code"><span class="ne-text">intWbAribiter.io_out_5_*</span></code><span class="ne-text">, 接下来再被路由到</span><code class="ne-code"><span class="ne-text">wbDataPath.io_toCtrlBlock_writeback_20_*</span></code><span class="ne-text">, 最后进入控制模块 CtrlBlock 的 </span><code class="ne-code"><span class="ne-text">io_fromWb_wbData_20_*</span></code><span class="ne-text">通路.</span></p><p id="ud354658a" class="ne-p"><span class="ne-text">在接下来, </span><code class="ne-code"><span class="ne-text">inner_dataPath.regCache.io_writePorts_4_data</span></code><span class="ne-text">出现了这条指令所要写回的数据, 说明写回单元在写入物理寄存器堆, 完成写回操作.</span></p><p id="ubbf0aae9" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1782377342679-e3531a96-7594-4ade-94f0-4a04bc5e3630.png" width="1440" id="u7cd90e57" class="ne-image"></p><div data-type="info" class="ne-alert"><h6 id="bMFEF"><span class="ne-text">RegCache 是什么? 为什么香山需要 RegCache? 使用 RegCache 能带来什么好处?</span></h6><p id="ub2116c9b" class="ne-p"><span class="ne-text">RegCache (Register Cache，寄存器堆缓存) </span><span class="ne-text" style="color: rgb(0, 0, 0); background-color: rgba(0, 0, 0, 0)">不是传统意义上的内存层级 Cache</span><span class="ne-text">，它是香山昆明湖 V2 针对</span><span class="ne-text" style="color: rgb(0, 0, 0); background-color: rgba(0, 0, 0, 0)">整数物理寄存器堆 (Int PRF) 设计的高速子集缓存</span><span class="ne-text">, 属于后端乱序执行数据通路的核心优化部件. 它是一块小容量、多读端口的高速存储, 用来缓存近期刚写回的物理寄存器值, 让大部分寄存器读请求可以直接从这个小缓存读取, 无需访问大容量的主物理寄存器堆.</span></p><p id="u2c44f381" class="ne-p"><span class="ne-text">引入 RegCache 的核心驱动力, 是解决</span><span class="ne-text" style="color: rgb(0, 0, 0); background-color: rgba(0, 0, 0, 0)">高性能乱序超标量处理器的物理寄存器堆读端口瓶颈</span><span class="ne-text">, 这是所有宽发射乱序核都会遇到的经典硬件约束. 在多发射乱序架构中, 每周期要同时发射多条指令到不同执行单元, 每条指令又需要读取多个源寄存器, 这就要求主物理寄存器堆提供极多的并发读端口. 但硬件层面, 多端口 SRAM 的</span><span class="ne-text" style="color: rgb(0, 0, 0); background-color: rgba(0, 0, 0, 0)">面积、延迟、功耗都随端口数量呈超线性增长</span><span class="ne-text">: 读端口越多, 寄存器堆的访问延迟越高, 会直接拉长后端关键路径, 成为处理器主频提升的主要瓶颈, 也会导致寄存器堆面积急剧膨胀, 功耗占比大幅升高. 和内存访问一样, 寄存器访问也存在极强的时间局部性: 刚被写入的物理寄存器, 大概率很快就会被后续依赖指令读取. 这就给了 RegCache 发挥空间: 用一块极小的缓存承接大部分高频率寄存器的请求, 主寄存器堆只需要处理少部分低概率寄存器的请求, 从而可以安全地缩减主寄存器堆的读端口数量.</span></p><p id="u7a5be4ac" class="ne-p"><span class="ne-text">使用了 RegCache 后, 可以缩减主寄存器堆读端口, 释放关键路径时序潜力, 还可以显著降低面积与功耗, 并且不会造成明显的性能损失.</span></p></div></details>
<details class="lake-collapse"><summary id="udf4f4d26"><span class="ne-text">退休 (Retire) 阶段</span></summary><p id="uffc48265" class="ne-p"><span class="ne-text">在 RegCache 被写入的下一个周期 (下面波形图的的第 19094ps), ROB 模块的 </span><code class="ne-code"><span class="ne-text">io_commits_info_0_commit_w</span></code><span class="ne-text">(这一路信号表示这条指令被提交, </span><code class="ne-code"><span class="ne-text">io_commits_info_0_commit_v</span></code><span class="ne-text">表示当前的 ROB entry 是存在有效的信息的)被置高, 表示这条指令已经正式被提交. 在指令被正式提交的下一个周期, 对应的 ROB 表项 (2F, 对应十进制的 47) 的有效位被拉低, 标识这条 ROB 表项被释放, 意味着这条指令正式的离开了重排序缓存, 对体系结构状态做出了修改, 并无法退回.</span></p><p id="uf156fa6a" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1782294995357-8ca8ca59-c776-4b04-9ee9-4a81036881e7.png" width="1440" id="ud1303db7" class="ne-image"></p></details>
在指令退休后, 这条指令也就正式的完成了它的执行过程. 如果一条指令执行到了退休阶段的结束, 那也就意味着这条指令被正式的成功执行了 (没有回退的余地了, 如果一条指令完成了写回, 但没有提交/退休, 那么这条指令是有可能被回滚的, 比如说前面的指令提交时出现分支预测失败或出现其他异常).


> 更新: 2026-07-08 16:55:03  
