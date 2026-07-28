# ISA、MMU、PMP 与 RVV

## ISA 语义
- 分支、跳转、fence、CSR、异常和中断都要按 ISA 语义解释。
- RVV 要同时看 `vtype`、`vl`、`vstart`、`vxsat`、`vxrm`、mask 和 tail 规则。
- 反汇编与波形的 PC、指令宽度、立即数不一致时，先查抓取宽度和对齐。

## MMU
- 分清 `satp / vsatp / hgatp`、页表 walk、leaf / non-leaf、canonical 地址和 guest translation。
- Sv39 / Sv48 的 canonical 规则必须单独验证，不能默认页表命中就合法。
- 翻译异常、权限异常、访问异常、guest page fault 不是一回事。

## PMP / PMA
- PMP 是权限/区域控制，PMA 是物理属性。
- 它们和页表权限、canonical check、cacheability 是不同层次。
- 访存 bug 分析时要判断是翻译前失败还是翻译后失败。

## 异常与中断
- 常核对 `mcause`、`mepc`、`mtval`、`scause`、`sepc`、`stval`、`mstatus`、`sstatus`。
- 若 bug 在取指路径，优先区分 instruction page fault、instruction access fault、illegal instruction、misaligned 或 redirect fault。
