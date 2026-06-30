# 香山编程篇Diplomacy题库
**参考链接：**
-   [https://github.com/OpenXiangShan/ChiselIOPMP](https://github.com/OpenXiangShan/ChiselIOPMP)
-   [教程手册文档链接](https://github.com/OpenXiangShan/XiangShanLab/tree/master/xiangshan-course/docs/%E8%AF%BE%E7%A8%8B%E4%BD%93%E7%B3%BB2%EF%BC%9A%E7%BC%96%E7%A8%8B%E7%AF%87-%E9%A6%99%E5%B1%B1%E5%BC%80%E5%8F%91%E7%BC%96%E7%A8%8B%E8%AF%AD%E8%A8%80%E7%AF%87/%E4%B8%8BDiplomacy)

**题目：** 围绕 IOPMP、DCache、Memory、DMAC 的 Diplomacy 设计（参考 Xiangshan SimMMIO.scala）。
1.  **单数据流通路**：DCache -> IOPMP (bypass, APB 悬空) -> Memory
2.  **2 对 1 Xbar，且带位宽转换和协议转换（最难）**，需要使用 TLXbar。
    a.  AXI_Master(64bit) -> Xbar -> IOPMP(64bit) (bypass, APB 悬空) -> Memory(64bit)
    b.  AXI_Master(64bit) -> Xbar -> APB_Master(32bit) -> IOPMP APB 配置口
3.  **2 对 1 Xbar 方向通路**
    a.  DMAC -> Xbar -> Memory
    b.  DCache -> Xbar -> Memory