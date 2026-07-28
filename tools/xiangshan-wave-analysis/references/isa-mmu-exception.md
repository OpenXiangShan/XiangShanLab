# ISA、MMU、PMP 与 RVV

## ISA 语义
- 分支、跳转、fence、CSR、异常和中断都要按 ISA 语义解释。
- RVV 需要同时看 `vtype`、`vl`、`vstart`、`vxsat`、`vxrm`、mask 和 tail 规则。
- 反汇编和波形的指令宽度、立即数、PC 对不上时，先查抓取宽度与对齐，再查译码。

## MMU
- 分清 `satp/vsatp/hgatp`、页表 walk、leaf/non-leaf、canonical 地址、guest translation。
- Sv39 / Sv48 的 canonical 规则要单独验证；不要默认页表命中就一定合法。
- 翻译异常、权限异常、访问异常、guest page fault 不是一回事。

## PMP / PMA
- PMP 更像权限/区域控制，PMA 更像物理属性。
- 它们和页表权限、canonical check、cacheability 是不同层次。
- 分析访存 bug 时，要看是“翻译前”还是“翻译后”失败。

## 异常与中断
- 常核对：`mcause`、`mepc`、`mtval`、`scause`、`sepc`、`stval`、`mstatus`、`sstatus`。
- 如果 bug 发生在取指路径，先确认是不是 instruction page fault、instruction access fault、misaligned、illegal instruction，或 redirect target fault。

## 常见经验
- 只有当波形证明有投机路径时，才讨论 wrong-path 或时序侧信道。
- 只有当同一条指令在波形、源码、日志三者中都对齐时，才把它写成最终结论。
