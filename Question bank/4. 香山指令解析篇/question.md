# 香山指令解析篇题库
1. **基础ALU指令（以ADD为例）的全流程执行分析**
   请以香山昆明湖后端执行的add x2, x30, x6（机器码0x006f0133，PC=0x80000122）为例，结合译码、重命名、分发、发射、执行、写回/提交六级流水逻辑回答以下问题：
   • 基础要求：逐段梳理该指令在各阶段的核心行为与关键信号含义：

     1. 译码阶段：DecodeUnit输出的fuType=0x40、fuOpType=0x21、lsrc_0=30/lsrc_1=6/ldest=2分别对应什么含义？
     2. 重命名阶段：RAT表读取时，lsrc=30对应的RAT表项值为0，但最终读出的物理寄存器是10，该差异由什么旁路逻辑导致？FreeList为该指令分配了哪个物理寄存器？ROB表项分配值是多少？
     3. 分发阶段：该指令读BusyTable的两个源物理寄存器返回resp=0（未就绪），原因是什么？uopNum字段值为1代表什么含义？
     4. 发射与提交：该指令被分发到哪个IQ？无数据相关时从IQ发射到ROB提交的全周期行为是怎样的？
   • 进阶问题：

     1. 若ADD前一条指令是写x30的运算指令且未写回，此时BusyTable返回resp=1（就绪），IQ会如何处理该ADD的发射？
     2. 波形中观察到addi与该ADD被写入同一个ROB表项，该现象叫什么？触发条件是什么？
   • 【实验要求】结合香山昆明湖DecodeUnit.scala/RenameUnit.scala/BusyTable.scala代码定位对应信号逻辑，拉取波形验证结论。

2. **乘除法指令的唤醒机制与背靠背数据相关处理**
   香山昆明湖针对多周期乘除法指令设计了差异化的唤醒机制，以保证mul x3,x2,x1+add x4,x3,x3（2周期乘法+1周期加法背靠背）、div x3,x2,x1+add x4,x3,x3（不定周期除法+1周期加法背靠背）两组用例的正确性，请结合波形分析回答：
   • 基础要求：分别梳理单独乘法、单独除法的完整执行流程，对比二者在IQ停留时长、源操作数就绪方式（Bypass/前推）上的差异。

   • 核心考点：

     1. 乘法是固定2周期延迟指令，依赖乘法的add为何不需要等mul写回，就能在mul出IQ的前一个周期被唤醒？请结合IssueBlockParams.scala中wakeUpQueues组件的lat参数（FuConfig.scala中乘法的延迟配置）说明原理。
     2. 除法是不定周期指令，为何无法复用乘法的wakeUpQueues机制？其依赖指令通过什么通道完成唤醒？
   • 进阶问题：观察波形发现除法在IQ中被发射后又被取消，该取消机制是为了应对什么场景？请结合执行单元阻塞时前端流水级的处理逻辑说明。

   • 【实验要求】结合香山昆明湖IssueBlockParams.scala/FuConfig.scala代码定位唤醒延迟配置逻辑，拉取乘法/除法背靠背波形验证唤醒时序。

3. **Store指令的后端执行与访存流水线交互**
   请以sd ra, 8(sp)（机器码0x00113423，PC=0x80000134）这条存数指令为例，梳理香山昆明湖对Store指令的特殊处理逻辑，回答：
   • 基础要求：对比Store与ADD在重命名阶段的差异（为何Store不需要分配目标物理寄存器、不需要写RAT表）；重命名阶段读RAT时，lsrc=2（sp）读出的RAT表项值为13，但最终传给分发阶段的是20，该差异由什么逻辑导致？以及分发阶段Store与LSQ的交互逻辑（Store被分配到SQ的哪个表项？）。

   • 核心考点：

     1. 为何Store会被拆分为STA（地址计算）和STD（数据准备）两个微操作？二者分别被分发到哪类IQ？拆分后如何保证二者的原子性（ROB/LSQ/SQ表项分配上有何特点）？
     2. 分发阶段读BusyTable时，若STA依赖的sp（逻辑2）未就绪、STD依赖的ra（逻辑1）已就绪，后续IQ会如何处理两个微操作的发射？
   • 进阶问题：

     1. StoreUnit计算出物理地址后，如何将信息写回Store Queue？Sbuffer在什么条件下会将Store Queue中的数据刷入DCache？
     2. 若DCache处理Store请求时Miss，会触发什么流程（MissQueue、L2交互、回填、写回）？请结合DCache Tag对比逻辑说明Hit/Miss的判断依据。
   • 【实验要求】结合香山昆明湖LsqEnqCtrl.scala/StoreUnit.scala/DCacheMainPipe.scala代码定位对应逻辑，拉取Store全流程波形验证。

4. **AMO（原子内存操作，以amoswap.w为例）的执行流程与原子性保证**
   请以amoswap.w a5,a4,(a5)（PC=0x80000142）这条RISC-V A扩展原子交换指令为例，结合香山对原子操作的支持逻辑回答：
   • 基础要求：梳理AMO从译码到提交的全流程，对比其与普通Store在分发阶段LSQ交互的差异（为何AMO不需要分配LSQ表项？）。

   • 核心考点：

     1. AMO同样被拆分为STA和STD两个微操作，其与普通Store的STA/STD在ROB/LSQ/SQ表项分配上有何异同？为何这样设计能保证原子性？
     2. 执行阶段STA计算完虚拟地址后并未立即访问DCache，而是延迟了数个周期，结合香山DCache的VIPT特性说明原因；为何访问DCache前需要先清空Store Buffer？这和AMO的aq/rl语义有何关联？香山对aq/rl的实现策略是什么？
   • 进阶问题：AMO执行完成后，写回阶段为何需要引入RegCache？RegCache解决了香山后端的什么瓶颈？

   • 【实验要求】结合香山昆明湖AtomicUnit.scala/RegCache.scala代码定位对应逻辑，拉取AMO全流程波形验证原子性保证机制。