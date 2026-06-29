# 香山编程篇Diplomacy题库
**参考链接：**
-   [https://github.com/OpenXiangShan/ChiselIOPMP](https://github.com/OpenXiangShan/ChiselIOPMP)
-   [https://zhuanlan.zhihu.com/p/659308008](https://zhuanlan.zhihu.com/p/659308008)
-   [https://zhuanlan.zhihu.com/p/633327505](https://zhuanlan.zhihu.com/p/633327505)

**题目：** 围绕 IOPMP、DCache、Memory、DMAC 的 Diplomacy 设计（参考 Xiangshan SimMMIO.scala）。
1.  **单数据流通路**：DCache -> IOPMP (bypass, APB 悬空) -> Memory
2.  **2 对 1 Xbar，且带位宽转换和协议转换（最难）**，需要使用 TLXbar。
    a.  AXI_Master(64bit) -> Xbar -> IOPMP(64bit) (bypass, APB 悬空) -> Memory(64bit)
    b.  AXI_Master(64bit) -> Xbar -> APB_Master(32bit) -> IOPMP APB 配置口
3.  **2 对 1 Xbar 方向通路**
    a.  DMAC -> Xbar -> Memory
    b.  DCache -> Xbar -> Memory