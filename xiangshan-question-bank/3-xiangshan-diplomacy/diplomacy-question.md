# 香山编程篇Diplomacy题库
**参考链接：**
-   [https://github.com/OpenXiangShan/ChiselIOPMP](https://github.com/OpenXiangShan/ChiselIOPMP)
-   [教程手册文档链接](https://github.com/OpenXiangShan/XiangShanLab/tree/master/xiangshan-course/docs/%E8%AF%BE%E7%A8%8B%E4%BD%93%E7%B3%BB2%EF%BC%9A%E7%BC%96%E7%A8%8B%E7%AF%87-%E9%A6%99%E5%B1%B1%E5%BC%80%E5%8F%91%E7%BC%96%E7%A8%8B%E8%AF%AD%E8%A8%80%E7%AF%87/%E4%B8%8BDiplomacy)

## **题目1、2、3：系统题：** 围绕 IOPMP、DCache、Memory、DMAC 的 Diplomacy 设计（参考 Xiangshan SimMMIO.scala）。
1.  **单数据流通路**：DCache -> IOPMP (bypass, APB 悬空) -> Memory
2.  **2 对 1 Xbar，且带位宽转换和协议转换（最难）**，需要使用 TLXbar。
    a.  AXI_Master(64bit) -> Xbar -> IOPMP(64bit) (bypass, APB 悬空) -> Memory(64bit)
    b.  AXI_Master(64bit) -> Xbar -> APB_Master(32bit) -> IOPMP APB 配置口
3.  **2 对 1 Xbar 方向通路**
    a.  DMAC -> Xbar -> Memory
    b.  DCache -> Xbar -> Memory





## 以下为diplomacy基础题

## 题目 4：端口数量由连接决定的加法广播器

- 功能需求：实现 `SourceNode → NexusNode → SinkNode` 组合通路，将所有输入相加并广播到所有输出。使用自定义 `SimpleNodeImp` 定义 32-bit 数据边；加法模块通过 `node.in`、`node.out` 获取端口，不能写死端口数量，也不能向加法模块传入数量参数来代替节点推导。
- 输入需求：
  - 配置输入：顶层参数 `nInputs>0`、`nOutputs>0`，默认分别为 5、3，用于创建源端和汇端的端口参数序列。
  - 硬件输入：`in_0` 至 `in_(nInputs-1)`，每路为 32-bit 无符号数。
- 输出需求：`out_0` 至 `out_(nOutputs-1)`，每路为全部输入之和的低 32 位；组合输出，不插入寄存器。生成的端口数量须与连接一致，日志报告实际输入/输出边数。
- 难度级别：L1 初级。
- 预期工期：1～2 天。
- 验收关注点：覆盖 `(1,1)、(2,1)、(5,3)、(8,4)`；默认配置输入 `[1,2,3,4,5]` 时三个输出均为 15；检查最大值相加时的截断、随机输入和各输出一致性；端口数量为 0 时须在 elaboration 阶段拒绝。

## 题目 5：自动协商位宽的完整精度加法器

- 功能需求：改造题目 4，使源节点声明各自的数据位宽，汇节点声明最大接收位宽；加法节点根据上游参数自动推导完整求和位宽，并向下游传播。输入边保留源端位宽，输出边使用求和位宽；汇端容量不足时拒绝生成，不能静默截断，也不能在顶层算好结果位宽后传给加法模块。
- 输入需求：
  - 配置输入：非空正整数序列 `sourceWidths`、`sinkMaxWidths`，默认分别为 `[8,8,8]`、`[10,16]`；序列长度分别决定输入、输出数量。
  - 硬件输入：第 `i` 路 `in_i` 的位宽为 `sourceWidths[i]`。
- 输出需求：每路输出均为全部输入的完整算术和。令 `maxSum = Σ(2^sourceWidths[i]-1)`，则实际输出位宽 `sumWidth = ceil(log2(maxSum+1))`，各汇端必须满足 `sinkMaxWidths[j] >= sumWidth`。使用精确整数计算位宽；日志列出输入宽度、推导结果和汇端容量，失败时指出容量不足的汇端。
- 难度级别：L2 中级。
- 预期工期：2～3 天。
- 验收关注点：默认配置输入 `[255,255,255]` 时，两路输出均为 10-bit 的 765；`sourceWidths=[1,8]` 时输出为 9 位；检查单输入、混合位宽和超过 64 位的配置；默认输入配置接 9-bit 容量的汇端必须失败。输出容量为 16 不意味着实际输出端口应扩成 16 位。

## 题目 6：可串联的位宽适配节点

- 功能需求：实现一入一出的 `WidthAdapter`，构建 `SourceNode → AdapterNode → SinkNode`。源端声明输入位宽，汇端声明所需输出位宽；适配节点通过双向参数变换确定两侧边的宽度，在实现层完成零扩展或截断。普通直连边要求两端声明一致，只有适配节点允许两侧宽度不同。
- 输入需求：
  - 配置输入：适配节点参数为正整数 `inWidth`、`outWidth`；源端声明 `inWidth`，汇端声明 `outWidth`。串联测试中，两个适配节点分别配置为 `(8,32)`、`(32,16)`。
  - 硬件输入：`in: UInt(inWidth.W)`，无 valid/ready 握手。
- 输出需求：`out: UInt(outWidth.W)`，拓宽时高位补 0，缩窄时保留低位，等宽时透传；均为组合逻辑。日志及 GraphML 标出适配节点两侧的实际位宽。
- 难度级别：L2 中级。
- 预期工期：2～3 天。
- 验收关注点：`8→32` 时 `0x80→0x00000080`，`32→8` 时 `0x12345678→0x78`；覆盖等宽和 1-bit 边界。复用同一个适配模块搭建 `8→32→16` 通路，检查中间边确为 32 位且最终值不变；非法宽度、宽度不匹配的普通直连必须在 elaboration 阶段报错。

## 题目 7：带缓冲与独立背压的可靠广播节点

- 功能需求：定义承载 `Decoupled(UInt(width.W))` 的自定义节点接口，使用 `NexusNode` 实现单输入、多输出广播。内部仅缓存一笔事务，每个输出独立消费；每笔已接收数据必须向每个输出恰好发送一次。输出数量从实际连接推导，不能因快端已经接收而丢弃慢端副本。
- 输入需求：
  - 配置输入：正整数 `width` 和顶层输出数量 `nOutputs`，默认分别为 32、3；所有数据边同宽。
  - 硬件输入：`clock`、`reset`、上游 `in.valid/in.bits`，以及各路 `out_j.ready`。上游受阻时保持 valid/bits 稳定，握手在时钟上升沿发生。
- 输出需求：`in.ready` 和各路 `out_j.valid/out_j.bits`。仅在缓冲空闲时接收输入，数据经过寄存器后再提供给输出；已消费端不再重复拉高 valid，未消费端保持 valid/bits 稳定。最后一路消费后的下一周期才允许接收新事务，不要求同拍替换；复位清空缓冲及消费状态，复位期间不计握手，复位前未完成的事务不补发。
- 难度级别：L2 中级。
- 预期工期：3～4 天。
- 验收关注点：覆盖 1、3、5 路输出，以及同时消费、错开消费、连续输入和广播中途复位。三路测试分别设为始终 ready、隔拍 ready、连续阻塞 10 拍，确认快端不重复消费、慢端不丢数据。各复位区间独立计分；停止输入并放开全部输出、排空后，各输出序列须与该区间已接收输入序列完全一致。
