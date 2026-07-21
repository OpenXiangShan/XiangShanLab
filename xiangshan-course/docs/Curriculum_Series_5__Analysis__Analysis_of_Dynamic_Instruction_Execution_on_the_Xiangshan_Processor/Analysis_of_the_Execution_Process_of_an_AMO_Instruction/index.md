# 一条 AMO 指令的简单分析过程

本文我们首先分析一个最简单的 AMO (Atomic Memory Operation, 原子内存操作) 指令 (amoswap.w), 在单核昆明湖中的运行过程.

RISC-V A 扩展（Atomic，原子扩展）是 RISC-V 指令集的标准非特权扩展，专为多核 / 多硬件线程的并发场景设计，提供硬件级不可分割的原子内存操作能力，核心包含原子读 - 修改 - 写（AMO）系列指令、加载保留 / 条件存储（lr/sc）指令对，同时配套 aq/rl 后缀实现轻量级内存序约束以适配 RISC-V 弱内存序模型，是操作系统实现自旋锁、信号量、无锁数据结构，解决多核并发竞态问题的核心硬件基础。

我们研究一个包括一条原子内存交换指令的 C 程序:

```bash
#include <klib.h>

int main() {
  int value = 5;
  printf("Old=%d ", value);
  int old_val;
  asm volatile(
    "amoswap.w %0, %2, (%1)"
    : "=r"(old_val)
    : "r"(&value), "r"(7)
    : "memory"
  );
  printf("New=%d\n", value);
  return 0;
}
```

***TODO: 如果这个文档要公开的话 记得加一下内联汇编的用法 已经用豆包做了总结***

以下是 `main`函数的汇编代码:

```bash
000000008000012a <main>:
    8000012a:   1101                    addi    sp,sp,-32
    8000012c:   4595                    li      a1,5
    8000012e:   00001517                auipc   a0,0x1
    80000132:   22250513                addi    a0,a0,546 # 80001350 <printf_+0x36>
    80000136:   ec06                    sd      ra,24(sp)
    80000138:   c62e                    sw      a1,12(sp)
    8000013a:   1e0010ef                jal     8000131a <printf_>
    8000013e:   007c                    addi    a5,sp,12
    80000140:   471d                    li      a4,7
    80000142:   08e7a7af                amoswap.w       a5,a4,(a5)
    80000146:   45b2                    lw      a1,12(sp)
    80000148:   00001517                auipc   a0,0x1
    8000014c:   21050513                addi    a0,a0,528 # 80001358 <printf_+0x3e>
    80000150:   1ca010ef                jal     8000131a <printf_>
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

我们主要关注该条指令在后端 (backend) 模块中的行为. 根据香山官方文档, 后端主要包括译码(Decode), 重命名 (Rename), 分派 (Dispatch), 调度 (Schedule), 发射 (Issue), 执行 (Execute), 写回 (Writeback), 和退休 (Retire) 几个阶段, 我们将依次对该条指令的执行过程进行分析.

<details class="lake-collapse"><summary id="u1e0008ff"><span class="ne-text">译码 (Decode) 阶段</span></summary><p id="ub9004451" class="ne-p"><span class="ne-text">香山昆明湖架构默认配置了6个译码器 (也就是说, 单个周期内可以从前端同时获取 6 条指令的信息并进行译码), 我们关注每一个译码器的 </span><code class="ne-code"><span class="ne-text">io_enq_ctrlFlow_pc</span></code><span class="ne-text">, 表示当前周期内, 当前的译码器所译码的指令的 PC 值 (可以参考类 </span><code class="ne-code"><span class="ne-text">DecodeUnitIO</span></code><span class="ne-text">, </span><code class="ne-code"><span class="ne-text">DecodeUnitEnqIO</span></code><span class="ne-text">, 以及 </span><code class="ne-code"><span class="ne-text">StaticInst</span></code><span class="ne-text">的定义). 通过阅读波形图, 我们可以发现在第 18984 ps, </span><code class="ne-code"><span class="ne-text">decoder_2</span></code><span class="ne-text">(也就是第三个译码器) 对 </span><code class="ne-code"><span class="ne-text">PC=000008000142</span></code><span class="ne-text">的指令进行了译码 (可以通过查看 </span><code class="ne-code"><span class="ne-text">io_enq_ctrlFlow_instr</span></code><span class="ne-text">来确认和汇编代码的指令一致), 这条指令的 </span><code class="ne-code"><span class="ne-text">foldPC</span></code><span class="ne-text">值为 </span><code class="ne-code"><span class="ne-text">0A0</span></code><span class="ne-text">:</span></p><p id="ua47919dc" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1774731884035-563ed752-e9b9-4cbb-8638-a3ffcb5a4fab.png" width="1920" id="u732133aa" class="ne-image"></p><p id="u3fca358f" class="ne-p"><span class="ne-text">通过阅读 DecodeUnit 的代码, 可以发现译码阶段的译码结果输出主要出现在 deq 的 decodedInst 中, 通过查阅 decodedInst 的定义 (在backend/bundle.scala中) 可以找到所有译码结果的输出信号:</span></p><p id="ueef34dec" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1774733345940-5f73e5b9-4c6a-48ba-a88d-64e8d9604805.png" width="714" id="u286d0c07" class="ne-image"></p><p id="ud8b93cea" class="ne-p"><span class="ne-text">DecodedInst分为两类, 第一类是直接从 StaticInst (也就是从前端模块的直接输入) 进行连通, 第二类是真正的译码结果 (图中 decoded 注释下的信号). 阅读 </span><code class="ne-code"><span class="ne-text">decodeDefault</span></code><span class="ne-text">, 我们可以大致了解译码阶段译码器都提取了指令的那些操作信息. </span><code class="ne-code"><span class="ne-text">decodeDefault</span></code><span class="ne-text">还作为发现非法指令的兜底行为, 如果从前端中取得的一条指令没有匹配上任何合法指令的比特位模式, 那么就会匹配这个默认结果, 该结果保证了不去写任何寄存器 (rfWen, fpWen, vecWen 都是 N), 通过 SelImm 是 INVALID_INSTR 表示这是一条非法指令, 从而产生非法指令异常.</span></p><pre data-language="bash" id="g7f0W" class="ne-codeblock language-bash"><code>/**
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
}</code></pre><p id="ud3ef7da3" class="ne-p"><span class="ne-text">拉取相关的信号, 并和手动译码的结果进行比较:</span></p><p id="u4099f20f" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1774748352141-36684430-9f1d-4952-b356-b5ba3eb2166d.png" width="1728" id="u73211259" class="ne-image"></p><p id="u9e534e99" class="ne-p"><span class="ne-text">我们发现 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_srcType\_0</span></code><span class="ne-text">和 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_srcType\_1</span></code><span class="ne-text">都是1, 查阅 </span><code class="ne-code"><span class="ne-text">package.scala</span></code><span class="ne-text">可以找到对srcType的定义:</span></p><pre data-language="bash" id="dkH8s" class="ne-codeblock language-bash"><code>package object xiangshan {
object SrcType {
def imm = "b0000".U
def pc  = "b0000".U
def xp  = "b0001".U
def fp  = "b0010".U
def vp  = "b0100".U
def v0  = "b1000".U
def no  = "b0000".U // this src read no reg but cannot be Any value</code></pre><p id="uf1f62743" class="ne-p"><span class="ne-text">所以波形图中的1表示, 这个源操作数来自xp, 也就是定点寄存器堆. 同时我们也发现 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_lsrc\_0</span></code><span class="ne-text">和</span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_lsrc\_1</span></code><span class="ne-text">也都和我们手动译码的结果一致.</span></p><div data-type="info" class="ne-alert"><p id="u11813ae0" class="ne-p"><span class="ne-text">在香山昆明湖中, 因为我们使用了重命名技术来提高指令的并行性, 所以处理器中就有了「逻辑寄存器」和「物理寄存器」两种寄存器表示方法, 译码器得到的是程序/汇编视角的寄存器编号, 对应「逻辑寄存器」,这也就是为什么信号名称为</span><code class="ne-code"><span class="ne-text">lsrc</span></code><span class="ne-text">. 在重命名阶段, 这些逻辑寄存器将会被分配到处理器中实际存在的「物理寄存器」并记录下和「逻辑寄存器」的对应关系.</span></p></div><p id="u51e7b741" class="ne-p"><span class="ne-text">接下来我们分析译码器计算出的操作码 (Opcode), 波形图显示这是一个独热的值, 查阅</span><code class="ne-code"><span class="ne-text">backend/fu/FuType.scala</span></code><span class="ne-text">发现</span><code class="ne-code"><span class="ne-text">FuType extends OHEnumeration</span></code><span class="ne-text">, 所以会输出一个独热的值 (具体哪一位是高电平取决于chisel如何生成system verilog代码).</span></p><p id="u508d2b4d" class="ne-p"><span class="ne-text">从波形图中可以发现, 这条指令的 </span><code class="ne-code"><span class="ne-text">fuOpType</span></code><span class="ne-text">为 </span><code class="ne-code"><span class="ne-text">0x0A0</span></code><span class="ne-text">, 查阅 </span><code class="ne-code"><span class="ne-text">package.scala</span></code><span class="ne-text">可以找到定义 </span><code class="ne-code"><span class="ne-text">def amoswap\_w = "b001010".U</span></code><span class="ne-text">对应 </span><code class="ne-code"><span class="ne-text">0x0A0</span></code><span class="ne-text">(宽度不是6位, 因为要考虑到其他的功能单元可能需要更长的编码宽度).</span></p><p id="u99ca25b1" class="ne-p"><span class="ne-text">同理, 可以从 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_rfWen = 1</span></code><span class="ne-text">得出这条指令会写入定点寄存器堆, 而 </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_fpWen = 0</span></code><span class="ne-text">, </span><code class="ne-code"><span class="ne-text">io\_deq\_decodedInst\_vecWen = 0</span></code><span class="ne-text">则表示这条指令不会导致浮点寄存器堆和向量寄存器堆的写入.</span></p><p id="u800492c2" class="ne-p"><span class="ne-text">综上, 译码器的行为匹配在 DecodeUnit 中对这条指令行为的编码:</span></p><pre data-language="bash" id="gMpH0" class="ne-codeblock language-bash"><code>AMOSWAP\_W -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.mou, LSUOpType.amoswap\_w, SelImm.X, xWen = T, noSpec = T, blockBack = T),</code></pre></details>

<details class="lake-collapse"><summary id="ue577c471"><span class="ne-text">重命名 (Rename) 阶段</span></summary><p id="u893e2151" class="ne-p"><span class="ne-text">指令完成译码后, 将会进入到重命名阶段. 我们发现在译码阶段可以得到当前指令的逻辑源操作数寄存器编号以及逻辑目的地寄存器编号. 但是在香山昆明湖架构中, 我们使用了寄存器重命名技术来消除指令间的伪相关性 (WAW以及WAR相关性), 所以需要在译码阶段结束之后, 把逻辑寄存器 (在这里我们只关注定点寄存器, 所以编号是 0-31) 转换为物理寄存器 (在这里我们只关注定点寄存器, 在香山昆明湖架构中, 每个 CPU 共有 224 个物理定点寄存器).</span></p><div data-type="info" class="ne-alert"><p id="u2e62b07d" class="ne-p"><span class="ne-text">指令间间常见的相关性有RAW (Read After Write, 写后读), WAW (Write After Write, 写后写), 以及 WAR (Write After Read, 读后写) 相关性. 其中 RAW 相关性为「真实的相关性」无法通过重命名技术解决, 但 WAW 和 WAR 相关性被认为是「虚假的相关性」可以通过重命名技术解决.</span></p><p id="u75c3e52a" class="ne-p"><span class="ne-text">RAW 相关性指在程序的指令流中, 更年轻的指令需要读取更年长的指令所写入的寄存器值, 这时候, 更年轻的指令不能比更年长的指令更早地被执行, 因为所需要的操作数还没有被计算出来, 这两条指令的</span><span class="ne-text" style="color: rgb(0, 0, 0); background-color: rgba(0, 0, 0, 0); font-size: 16px">执行与写回顺序不能完全颠倒</span><span class="ne-text">，年轻指令可提前进入发射队列，但必须等待年长指令的结果生成后才能执行. 示例: 程序中包括以下两条连续的指令 </span><code class="ne-code"><span class="ne-text">add x1, x2, x3</span></code><span class="ne-text">以及 </span><code class="ne-code"><span class="ne-text">sub x4, x1, x2</span></code><span class="ne-text">减法指令需要读取寄存器 </span><code class="ne-code"><span class="ne-text">x1</span></code><span class="ne-text">这个寄存器是加法指令所写入的, 所以如果加法指令的结果没有计算出来, 那么减法指令就不能够执行.</span></p><p id="ucde67f47" class="ne-p"><span class="ne-text">WAW 相关性指在程序的指令流中, 更年轻的指令会写入一个寄存器, 这个寄存器被一个更年长的指令所写入. 在这种情况下, 更年轻的指令可以比更年长的指令提前执行 (只是逻辑寄存器的命名存在冲突, 完全可以乱序执行), 但是需要注意的是, 在最后的提交阶段, 必须保证这个逻辑寄存器的值是更年轻的指令所写入的 (否则乱序执行在提交后的状态和原来顺序执行的状态将无法保持一致). 示例: 程序中包括以下两条连续的指令</span><code class="ne-code"><span class="ne-text">add x1, x2, x3</span></code><span class="ne-text">以及</span><code class="ne-code"><span class="ne-text">add x1, x4, x5</span></code><span class="ne-text">此时我们完全可以先执行第二条加法指令, 但是在最后提交的时候, 务必要保证逻辑寄存器</span><code class="ne-code"><span class="ne-text">x1</span></code><span class="ne-text">的值是第二条加法指令的计算结果 (即使第二条指令比第一条指令更早地被执行). </span></p><p id="uedc2c0df" class="ne-p"><span class="ne-text">WAR 相关性指在程序的指令流中, 更年轻的指令会写入一个寄存器, 这个寄存器被一个更年长的指令所读取. 在这种情况下, 更年轻的指令可以比更年长的指令提前执行 (只是逻辑寄存器的命名存在冲突, 完全可以乱序执行). 示例: 程序中包括以下两条连续的指令</span><code class="ne-code"><span class="ne-text">add x2, x1, x3</span></code><span class="ne-text">以及</span><code class="ne-code"><span class="ne-text">add x1, x4, x5</span></code><span class="ne-text">此时我们完全可以先执行第二条指令, 再执行第一条指令 (因为重命名期间会给两条指令的 </span><code class="ne-code"><span class="ne-text">x1</span></code><span class="ne-text">逻辑寄存器分配不同的物理寄存器, 不用担心第二条指令写入后原来的数据被篡改).</span></p></div><p id="u29f2515c" class="ne-p"><span class="ne-text">在香山昆明湖架构中, 重命名阶段还会对当前的微操作 (uop) 分配 ROB (Re-Order Buffer) 表项, 并维护物理寄存器的空闲列表 (Free List), 我们将通过对照波形图和 Chisel 代码对重命名阶段的行为逐一进行分析.</span></p><p id="u85581b5e" class="ne-p"><span class="ne-text">当指令完成译码阶段, 会被送往</span><code class="ne-code"><span class="ne-text">decodePipeRenameModule</span></code><span class="ne-text">中, 这个模块是和重命名阶段之间的桥梁, 负责接偶缓冲和预处理. 这个模块通过 </span><code class="ne-code"><span class="ne-text">PipelineConnect</span></code><span class="ne-text">进行流水线寄存器打拍, 降低处理器的关键路径长度 (是取得很好的时序的关键). 接下来, 我们把注意力集中到波形图和代码的</span><code class="ne-code"><span class="ne-text">rename</span></code><span class="ne-text">模块.</span></p><p id="u429deb02" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1774994709934-a35dfff8-bab9-475a-b306-9cb27f61d777.png" width="197" id="uaa5c221d" class="ne-image"></p><p id="ue795365c" class="ne-p"><span class="ne-text">通过查看模块之间的关系, 我们可以发现重命名阶段维护了各类物理寄存器堆的空闲列表 (Free List) 以及压缩单元 </span><code class="ne-code"><span class="ne-text">compressUnit</span></code><span class="ne-text">, 空闲列表将用于分配物理寄存器, 压缩单元用于决定哪些指令可以共用一个 ROB 表项. 空闲列表和压缩单元在 </span><code class="ne-code"><span class="ne-text">backend/Rename.scala</span></code><span class="ne-text">中被示例化:</span></p><pre data-language="scala" id="MVYGX" class="ne-codeblock language-scala"><code>val compressUnit = Module(new CompressUnit())
// create free list and rat
val intFreeList = Module(new MEFreeList(IntPhyRegs))
val fpFreeList = Module(new StdFreeList(FpPhyRegs - FpLogicRegs, FpLogicRegs, Reg_F))
val vecFreeList = Module(new StdFreeList(VfPhyRegs - VecLogicRegs, VecLogicRegs, Reg_V, 31))
val v0FreeList = Module(new StdFreeList(V0PhyRegs - V0LogicRegs, V0LogicRegs, Reg_V0, 1))
val vlFreeList = Module(new StdFreeList(VlPhyRegs - VlLogicRegs, VlLogicRegs, Reg_Vl, 1))</code></pre><p id="ub8d8ad53" class="ne-p"><span class="ne-text">因为这条指令是由第三个译码单元完成的译码 (在波形图中为</span><code class="ne-code"><span class="ne-text">decoder_2</span></code><span class="ne-text">), 所以拉去这个译码器到重命名阶段的输入, 其前缀应该为 </span><code class="ne-code"><span class="ne-text">io_in_2</span></code><span class="ne-text">表示输入来自第二个译码器.</span></p><p id="uc0a4d03b" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1774995345785-69b434bd-3e04-4493-9998-0945c84f998e.png" width="960" id="ub116b056" class="ne-image"></p><p id="u9429b459" class="ne-p"><span class="ne-text">在第 18986ps, 也就是译码单元完成了这条指令的译码工作的下一个周期, 重命名模块的</span><code class="ne-code"><span class="ne-text">ready</span></code><span class="ne-text">输出为高电平, 且</span><code class="ne-code"><span class="ne-text">valid</span></code><span class="ne-text">输入为低电平, 表示该译码器和重命名单元的该输入通道成功进行了握手. 验证其 PC 和译码阶段的 PC 一致, 且逻辑寄存器</span><code class="ne-code"><span class="ne-text">lsrc</span></code><span class="ne-text">和逻辑目的寄存器</span><code class="ne-code"><span class="ne-text">ldest</span></code><span class="ne-text">也都一致,</span><code class="ne-code"><span class="ne-text">lastUop</span></code><span class="ne-text">为高电平表示这条微指令是一条 RISC-V 指令的最后一个微指令 (这条 AMO 指令足够简单, 所以只需要一个微指令就可以完成操作), 所以需要分配 ROB 表项. 接下来, 我们分析重命名模块分配物理寄存器的逻辑:</span></p><p id="u15f6869c" class="ne-p"><img src="https://cdn.nlark.com/yuque/0/2026/png/65238355/1774999841877-6b6e9bd0-89a4-4f04-81a2-9037f3f6b217.png" width="960" id="uc7ef2bec" class="ne-image"></p><p id="ud377a182" class="ne-p"><span class="ne-text">TODO: 分析分配ROB表项, 以及ROB内部free list的变化的逻辑</span></p><p id="u6324be4a" class="ne-p"><span class="ne-text">TODO: 分析输出数据</span></p></details>
<details class="lake-collapse"><summary id="u237d8d00"><span class="ne-text">分派 (Dispatch) 阶段</span></summary><p id="u6d136578" class="ne-p"><span class="ne-text">TODO</span></p></details>
<details class="lake-collapse"><summary id="u7dde2452"><span class="ne-text">调度 (Schedule) 阶段</span></summary><p id="ufb771803" class="ne-p"><span class="ne-text">TODO</span></p></details>
<details class="lake-collapse"><summary id="u5688eebf"><span class="ne-text">发射 (Issue) 阶段</span></summary><p id="u04fbc459" class="ne-p"><span class="ne-text">TODO</span></p></details>
<details class="lake-collapse"><summary id="u344cd254"><span class="ne-text">执行 (Execute) 阶段</span></summary><p id="uf3f423ea" class="ne-p"><span class="ne-text">TODO</span></p></details>
<details class="lake-collapse"><summary id="u569e4a86"><span class="ne-text">写回 (Writeback) 阶段</span></summary><p id="u1862f287" class="ne-p"><span class="ne-text">TODO</span></p></details>
<details class="lake-collapse"><summary id="udf4f4d26"><span class="ne-text">退休 (Retire) 阶段</span></summary><p id="uc4b2d867" class="ne-p"><span class="ne-text">TODO</span></p></details>


> 更新: 2026-04-01 07:30:50  
