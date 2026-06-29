# 香山指令解析篇题库

1. **基础ALU指令（ADD）全流程分析**

以香山昆明湖后端执行的add x2, x30, x6（机器码0x006f0133，PC=0x80000122）为例分析：


- 译码阶段fuType=0x40/fuOpType=0x21/lsrc_0=30/lsrc_1=6/ldest=2分别对应什么含义？
- 重命名阶段读RAT时lsrc=30对应RAT表项值为0，但最终读出物理寄存器为10，差异由什么旁路逻辑导致？FreeList为该指令分配的物理寄存器、ROB表项分别是多少？
- 分发阶段读BusyTable两个源物理寄存器返回resp=0（未就绪）的原因？uopNum=1代表什么？
- 该ADD被分发到哪个IQ？无数据相关时从IQ发射到ROB提交的全周期行为是怎样的？

2. **乘除法唤醒机制与背靠背数据相关处理**

以mul x3,x2,x1+add x4,x3,x3（2周期乘+1周期加背靠背）、div x3,x2,x1+add x4,x3,x3（不定周期除+1周期加背靠背）两组用例为例分析：


- 单独乘法、单独除法的执行流程中，IQ停留时长、源操作数就绪方式（Bypass/前推）有何差异？
- 乘法为固定2周期延迟指令，依赖它的add为何无需等mul写回，就能在mul出IQ前一周期被唤醒？结合IssueBlockParams.scala中wakeUpQueues的lat参数（FuConfig.scala乘法延迟配置）说明原理。
- 除法为不定周期指令，为何无法复用wakeUpQueues？其依赖指令通过什么通道唤醒？


3. **Store指令后端执行与访存流水线交互**

以sd ra, 8(sp)（机器码0x00113423，PC=0x80000134`）为例分析：

- Store与ADD在重命名阶段有何差异（为何Store不需分配目标物理寄存器、不需写RAT）？重命名读RAT时lsrc=2（sp）对应RAT表项值为13，但最终传给分发阶段为20，差异由什么逻辑导致？Store与LSQ的交互逻辑（分配到SQ几号表项）？
- 为何Store拆分为STA（地址计算）、STD（数据准备）两个微操作？二者分别分发到哪类IQ？拆分后如何通过ROB/LSQ/SQ表项分配保证原子性？
- 分发读BusyTable时若STA依赖的sp（逻辑2）未就绪、STD依赖的ra（逻辑1）已就绪，IQ会如何处理两个微操作的发射？


4. AMO（原子操作，以amoswap.w为例）执行与原子性保证

以amoswap.w a5,a4,(a5)（PC=0x80000142）为例分析：
- AMO从译码到提交的全流程？对比普通Store，分发阶段为何AMO不需要分配LSQ表项？
- AMO同样拆分为STA/STD，与普通Store的STA/STD在ROB/LSQ/SQ表项分配上有何异同？为何这样设计能保证原子性？
- 执行阶段STA算完虚拟地址后未立即访DCache，延迟数个周期的原因（结合香山DCache VIPT特性说明）？为何访DCache前需先清空Store Buffer？和AMO的aq/rl语义有何关联？香山对aq/rl的实现策略是什么？

5. **阅读我们编写的文档, 配合波形图, 准备一个讲解香山昆明湖CPU如何执行一条add指令, 从后端译码开始讲解的 PPT 讲解时长最多15分钟**
   参考 [文档链接](https://github.com/OpenXiangShan/XiangShanLab/blob/master/xiangshan-course/docs/%E8%AF%BE%E7%A8%8B%E4%BD%93%E7%B3%BB5%EF%BC%9A%E8%A7%A3%E6%9E%90%E7%AF%87-%E6%8C%87%E4%BB%A4%E5%9C%A8%E9%A6%99%E5%B1%B1%E5%A4%84%E7%90%86%E5%99%A8%E7%9A%84%E5%8A%A8%E6%80%81%E6%89%A7%E8%A1%8C%E8%A7%A3%E6%9E%90/%E4%B8%80%E6%9D%A1ADD%E6%8C%87%E4%BB%A4%E7%9A%84%E7%AE%80%E5%8D%95%E5%88%86%E6%9E%90%E8%BF%87%E7%A8%8B.md)