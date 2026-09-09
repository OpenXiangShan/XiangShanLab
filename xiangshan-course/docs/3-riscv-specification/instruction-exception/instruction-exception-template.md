# RISC-V Instruction Exception Template

> 本模板用于按 RISC-V 指令类别审计同步异常。基线采用 RISC-V
> Unprivileged ISA `v20260120` 与 Privileged ISA Version 1.13；规范核对日期为
> `2026-09-09`。
> “可能异常”表示架构上存在触发路径，不表示每次执行都会异常，也不表示
> 所有 EEI、profile 或实现都必须实现同一种可选行为。

## 1. 使用范围

本文只列 **同步 exception**。异步 interrupt 不属于某条指令的异常集合，不应
混入逐指令表。Debug Mode 进入、NMI、reset、浮点 `fflags` 和向量定点
`vxsat` 也必须与同步 exception 分开记录。

对任意指令，必须依次检查：

1. 指令是否成功取到：取指地址、执行权限、PMP/PMA、地址转换和 guest
   两阶段地址转换。
2. 指令编码及当前状态是否合法：扩展、XLEN、特权级、CSR 权限、`FS/VS`
   状态、虚拟化控制位和保留编码。
3. 指令执行是否产生其类别特有异常：控制流目标、数据访存、原子访问、
   `ECALL/EBREAK`、CFI 检查等。
4. trap 是否在处理过程中升级为 double trap，或由平台报告 hardware error。

## 2. 测试对象

| 字段 | 值 |
| --- | --- |
| 处理器/核名称 | TBD |
| 实现版本/提交号 | TBD |
| ISA 字符串 | 例如：`rv64imafdcv_zicsr_zifencei_zba_zbb_zicbom` |
| Privileged ISA 版本 | 1.13 / TBD |
| Profile | RVA23 / RVI20 / 无 / TBD |
| XLEN | 32 / 64 |
| IALIGN | 16 / 32 |
| FLEN / VLEN / ELEN | N/A 或 TBD |
| 支持模式 | M / HS / S / VS / U / VU |
| 地址转换 | Bare / Sv32 / Sv39 / Sv48 / Sv57 / 两阶段 |
| PMP/PMA 配置 | TBD |
| 测试环境 | RTL 仿真 / FPGA / 芯片 / ISA 模拟器 |
| 参考模型 | Spike / Sail / NEMU / 其他 |
| 测试日期 | YYYY-MM-DD |

## 3. 同步异常全集

下表是标准 cause 编码空间。`Interrupt=0` 时，`Exception Code` 写入
`mcause`、`scause` 或 `vscause`。保留和 custom 编码也列出，以免把平台
自定义异常误认为标准异常。

| Code | 标准名称 | 典型来源 | `tval`/附加状态要点 |
| ---: | --- | --- | --- |
| 0 | Instruction address misaligned | taken branch/jump/return 的目标不满足 `IALIGN` | 通常为错误目标地址 |
| 1 | Instruction access fault | 取指 PMP/PMA/物理访问错误，或平台规定的执行访问错误 | 通常为故障取指地址 |
| 2 | Illegal instruction | 不支持/禁用的扩展、非法操作数约束、无权访问 CSR、状态为 Off 等 | 可写入故障指令位或 0 |
| 3 | Breakpoint | `EBREAK` 或地址/数据 trigger 请求 breakpoint exception | 指令地址或命中的数据地址 |
| 4 | Load address misaligned | 标量、浮点、向量或隐式 load 地址未按要求对齐 | 故障虚拟地址 |
| 5 | Load access fault | load 的 PMP/PMA/物理访问或不可模拟未对齐错误 | 故障虚拟地址 |
| 6 | Store/AMO address misaligned | store、SC、AMO、部分 CMO 地址未按要求对齐 | 故障虚拟地址 |
| 7 | Store/AMO access fault | store/AMO 的 PMP/PMA/物理访问或不可模拟错误 | 故障虚拟地址 |
| 8 | Environment call from U-mode | U-mode 执行 `ECALL` | 通常为 0 |
| 9 | Environment call from S-mode | HS/S-mode 执行 `ECALL` | 通常为 0 |
| 10 | Environment call from VS-mode | VS-mode 执行 `ECALL` | 通常为 0 |
| 11 | Environment call from M-mode | M-mode 执行 `ECALL` | 通常为 0 |
| 12 | Instruction page fault | 第一阶段取指地址转换或页权限失败 | 故障虚拟地址 |
| 13 | Load page fault | 第一阶段 load 地址转换或页权限失败 | 故障虚拟地址 |
| 14 | Reserved | 不得当作标准异常使用 | 实现/平台定义 |
| 15 | Store/AMO page fault | 第一阶段 store/AMO 地址转换或页权限失败 | 故障虚拟地址 |
| 16 | Double trap | trap 处理关键阶段再次发生不可交付 trap | 配合 `mtval2` 等状态检查 |
| 17 | Reserved | 不得当作标准异常使用 | 实现/平台定义 |
| 18 | Software-check | Zicfilp landing-pad 或 Zicfiss shadow-stack 检查失败 | `tval` 携带软件检查子原因 |
| 19 | Hardware error | 实现检测到不可归入普通访问错误的硬件故障 | 平台定义 |
| 20 | Instruction guest-page fault | H 扩展第二阶段取指转换/权限失败 | `tval`、`htval`、`mtval2` |
| 21 | Load guest-page fault | H 扩展第二阶段 load 转换/权限失败 | `tval`、`htval`、`mtval2` |
| 22 | Virtual instruction | guest 中执行必须由 hypervisor 仿真/截获的指令 | 可写入故障指令位或 0 |
| 23 | Store/AMO guest-page fault | H 扩展第二阶段 store/AMO 转换/权限失败 | `tval`、`htval`、`mtval2` |
| 24--31 | Designated for custom use | 厂商/平台自定义同步异常 | 平台规范定义 |
| 32--47 | Reserved | 未来标准使用 | 不应私自占用 |
| 48--63 | Designated for custom use | 厂商/平台自定义同步异常 | 平台规范定义 |
| >=64 | Reserved | 未来标准使用 | 不应私自占用 |

注意：

- `0` 只在控制转移被执行并形成错误目标时报告；不跳转的条件分支不会因其
  立即数所表示的潜在目标而触发该异常。
- 实现 `C` 或其他 16-bit 指令扩展时 `IALIGN=16`，标准控制流目标至少
  2-byte 对齐，因此不会产生 instruction-address-misaligned exception。
- page fault、guest-page fault 和 access fault 的优先级及归类必须按当前
  特权状态、地址转换阶段、PMP/PMA 和平台 EEI 判断，不能只看指令助记符。
- trigger 可以让本来不产生异常的计算、load 或 store 以 code 3 结束。
- code 16、19 不是普通指令功能异常，但完整处理器异常验证必须覆盖。

## 4. 所有指令共有的前端与解码异常

以下集合适用于 **每一条指令**，后续分类表只列额外或特别重要的异常。

| 检查阶段 | 可能 cause | 条件 |
| --- | --- | --- |
| 取指 | 1, 12, 20 | 执行访问的物理、第一阶段或第二阶段检查失败 |
| 取指/执行 trigger | 3 | trigger 配置为产生 breakpoint exception |
| 解码 | 2 | 指令扩展未实现、编码不属于当前 XLEN，或编码确实定义为 illegal |
| 状态/权限 | 2 | 当前特权级、CSR 权限、`mstatus.FS/VS` 等不允许执行 |
| 虚拟化截获 | 22 | H 扩展明确规定该 guest 操作产生 virtual-instruction exception |
| trap 关键阶段 | 16 | 已处于不可嵌套 trap 处理状态时又发生 trap |
| 实现硬件检测 | 19 | 平台定义的硬件错误 |

**Reserved、custom、UNSPECIFIED 不是 illegal 的同义词。** 对 reserved
编码，只有规范明确要求 illegal，或目标平台另有约束时，测试才可固定期待
code 2。

## 5. 指令类别与可能异常矩阵

表中 `通用` 指第 4 节的共有集合。数据访问 trigger 还可产生 code 3。

| 指令类别 | 扩展/代表指令 | 类别特有或重点 cause | 规则 |
| --- | --- | --- | --- |
| U-type 与纯整数 ALU | I/E：`LUI`, `AUIPC`, `ADD`, `SUB`, logical, compare, shift, `*W` | 通用 | 整数溢出不 trap；E 对上半寄存器编码的行为按 E 规范处理 |
| 条件整数运算 | Zicond：`CZERO.EQZ/NEZ` | 通用 | 无数据访存异常 |
| 乘除法 | M/Zmmul：`MUL*`, `DIV*`, `REM*` | 通用 | 整数除零和有符号溢出返回规定结果，不产生除法异常 |
| 位操作 | Zba/Zbb/Zbc/Zbs、Zbkb/Zbkc/Zbkx | 通用 | 无数据访存异常 |
| 标量密码计算 | Zknd/Zkne/Zknh/Zksed/Zksh 等 | 通用 | 扩展或 XLEN 不匹配通常落入 code 2 |
| Hint、NOP、MOP | `FENCE` 空集合、Zihintntl、Zihintpause、Zimop、Zcmop | 通用 | 合法 hint/MOP 即使实现为空操作也不是 illegal |
| 内存排序 | `FENCE`, `FENCE.TSO` | 通用 | 栅栏本身不读取或写入被排序的数据地址 |
| 指令流同步 | Zifencei：`FENCE.I` | 通用 | 不因先前代码写入地址直接产生 load/store fault |
| 直接/间接跳转 | `JAL`, `JALR`, `C.J`, `C.JAL`, `C.JR`, `C.JALR` | 0 + 通用 | 仅实际采用的目标违反 `IALIGN` 时 code 0 |
| 条件分支 | `BEQ/BNE/BLT/BGE/BLTU/BGEU`, `C.BEQZ/BNEZ` | 0 + 通用 | 只有 taken 分支检查目标对齐 |
| 普通整数 load | `LB/LBU/LH/LHU/LW/LWU/LD` 及压缩形式 | 4, 5, 13, 21 + 通用 | 未对齐处理由 ISA/EEI 约束；H guest 可有 code 21 |
| 普通整数 store | `SB/SH/SW/SD` 及压缩形式 | 6, 7, 15, 23 + 通用 | 权限、页表、PMP/PMA 和设备属性决定具体 cause |
| 浮点 load | `FLH/FLW/FLD/FLQ` 及压缩形式 | 4, 5, 13, 21 + 通用 | 还需检查 `FS` 和对应浮点扩展是否启用 |
| 浮点 store | `FSH/FSW/FSD/FSQ` 及压缩形式 | 6, 7, 15, 23 + 通用 | `fflags` 与访存 trap 无关 |
| 整数 load-acquire | Zalasr：`LB/LH/LW/LD.AQ` 等 | 4, 5, 13, 21 + 通用 | 沿用 load 异常类别 |
| 整数 store-release | Zalasr：`SB/SH/SW/SD.RL` 等 | 6, 7, 15, 23 + 通用 | 沿用 store 异常类别 |
| LR | Zalrsc：`LR.W/D` | 4, 5, 13, 21 + 通用 | 必须自然对齐；异常归入 load 类 |
| SC | Zalrsc：`SC.W/D` | 6, 7, 15, 23 + 通用 | 即使最终返回失败，也可能执行写权限/PMP/PMA 检查 |
| AMO | Zaamo/Zabha：`AMOSWAP`, `AMOADD`, logical, min/max | 6, 7, 15, 23 + 通用 | 异常使用 Store/AMO cause；MAG PMA 可放宽部分 AMO 对齐要求 |
| Compare-and-swap | Zacas：`AMOCAS.B/H/W/D/Q` | 6, 7, 15, 23 + 通用 | 无论比较成功与否都按 AMO 类检查 |
| Reservation wait | Zawrs：`WRS.NTO`, `WRS.STO` | 2 或 22 + 通用 | 受 `mstatus.TW`、`hstatus.VTW` 和当前模式约束 |
| 标量浮点计算 | F/D/Q/Zfh/Zfa、BF16、Zfinx/Zdinx/Zhinx | 通用；保留 `rm` 可允许 code 2 | NV/DZ/OF/UF/NX 只置 `fflags`，基础 ISA 不因此 trap；Zfinx 类使用整数寄存器，不套用浮点寄存器 `FS` 门控 |
| 标量浮点 CSR | `FRCSR`, `FRRM`, `FRFLAGS` 及写形式 | 2/22 + 通用 | 本质为 CSR 访问；检查 `FS`、CSR 权限和虚拟化 |
| CSR 原子读改写 | Zicsr：`CSRRW/CSRRS/CSRRC[I]` | 2/22 + 通用 | 不存在、无权、只读写入或状态门控失败；纯读与写入判定按指令语义 |
| Counter 读取 | Zicntr/Zihpm：`cycle/time/instret/hpmcounter*` | 2/22 + 通用 | 受 `mcounteren/scounteren/hcounteren` 等控制 |
| Entropy CSR | Zkr：读取 `seed` | 2/22 + 通用 | 仍按 CSR 权限和规定的返回状态处理 |
| `ECALL` | I：`ECALL` | 8/9/10/11 | cause 由执行 `ECALL` 时的 U/S/VS/M 模式唯一决定 |
| `EBREAK` | I/C：`EBREAK`, `C.EBREAK` | 3 | Debug 配置也可能直接进入 Debug Mode，而非交付普通 breakpoint trap |
| CBO 管理 | Zicbom：`CBO.CLEAN/FLUSH/INVAL` | 7, 15, 23 + 通用 | 按规范以 Store/AMO 类异常报告；地址不要求 cache-block 对齐 |
| CBO 清零 | Zicboz：`CBO.ZERO` | 7, 15, 23 + 通用 | 具有写内存效果并按 Store/AMO 类报告；地址不要求 cache-block 对齐 |
| CBO 预取 hint | Zicbop：`PREFETCH.I/R/W` | 通用 | 合法 hint 不交付目标数据地址的访问异常 |
| 向量配置 | `VSETVLI`, `VSETIVLI`, `VSETVL` | 2 + 通用 | 检查 `VS`；不支持的 `vtype` 通常通过 `vill`/`vl=0` 表示，不能一概期待 trap |
| 向量整数/定点/浮点计算 | V/Zve/Zv* arithmetic, mask, reduction, permutation, crypto | 2 + 通用 | `VS=Off`、非法寄存器组/重叠、非法 `vstart` 等可产生 code 2；浮点仍以 flags 报告 IEEE 条件 |
| 向量 load | unit/strided/indexed/segment/mask/whole-register load | 4, 5, 13, 21 + 通用 | `vstart` 记录恢复元素；精确 trap 规则适用 |
| 向量 store | unit/strided/indexed/segment/mask/whole-register store | 6, 7, 15, 23 + 通用 | 非幂等内存对 fault 后已写元素有更严格限制 |
| Fault-only-first load | `VLE*FF.V` | 4, 5, 13, 21 + 通用 | element 0 同步异常正常 trap；后续元素故障通常缩短 `vl` 而不 trap |
| 压缩一对一指令 | C/Zca/Zcf/Zcd/Zcb | 对应展开指令的集合 + 通用 | 压缩编码不创建新的异常类别 |
| Zcmp 多寄存器栈操作 | `CM.PUSH`, `CM.POP`, `CM.POPRET[Z]` | load 或 store 对应集合 + 通用 | 可有多个内部访问与 trap；提交、重启和幂等内存规则必须单测 |
| Zcmt 表跳转 | `CM.JT`, `CM.JALT` | 1, 12, 20，必要时 0 + 通用 | jump-vector-table 读取使用 execute 权限；fault 归因于 table-jump 指令 |
| 成对 load | Zilsd/Zclsd load pair | 4, 5, 13, 21 + 通用 | 分别覆盖首/次访问故障和部分完成/重启规则 |
| 成对 store | Zilsd/Zclsd store pair | 6, 7, 15, 23 + 通用 | 分别覆盖首/次访问故障、幂等性和架构提交规则 |
| Landing-pad CFI | Zicfilp：`LPAD` 及间接控制转移检查 | 18 + 通用 | landing-pad 检查失败产生 software-check exception |
| Shadow-stack CFI | Zicfiss：shadow-stack load/store/check 指令 | 18；并可能有 load/store 访存集合 + 通用 | shadow-stack 检查失败使用 software-check；实际内存访问仍可 fault |
| M-mode 返回 | `MRET`，Smrnmi 的 `MNRET` | 0/2 + 通用 | 错误模式/状态可 illegal；恢复目标受 `IALIGN` 约束 |
| S/VS 返回 | `SRET` | 0/2/22 + 通用 | `TSR`、虚拟化控制和当前模式决定 illegal 或 virtual instruction |
| 等待中断 | `WFI` | 2/22 + 通用 | `TW/VTW` 与当前模式决定是否 trap；正常等待不是 exception |
| 地址转换 fence | `SFENCE.VMA`, `SINVAL.VMA`, `SFENCE.W.INVAL`, `SFENCE.INVAL.IR` | 2/22 + 通用 | 特权级、`TVM`、H 虚拟化控制决定 |
| Hypervisor fence | `HFENCE.VVMA`, `HFENCE.GVMA`, `HINVAL.VVMA`, `HINVAL.GVMA` | 2 + 通用 | 仅 H 扩展和允许的特权状态可执行 |
| Hypervisor virtual load | `HLV`, `HLVX` | 4, 5, 13, 21 + 2 + 通用 | 以 guest 地址转换和指定访问类型执行；权限/模式错误可 illegal |
| Hypervisor virtual store | `HSV` | 6, 7, 15, 23 + 2 + 通用 | 以 guest 两阶段地址转换执行 |
| 厂商自定义指令 | `X...` | 通用 + 24--31/48--63（若平台定义） | 必须引用厂商 ISA 与平台异常规范，不能套用标准语义 |

## 6. 逐指令异常记录模板

| 字段 | 内容 |
| --- | --- |
| 扩展与版本 | TBD |
| 指令/编码 | TBD |
| 语义类别 | ALU / control-flow / load / store / AMO / CSR / system / vector / CFI / other |
| 适用 XLEN/模式 | TBD |
| 前置状态 | `misa`、`FS/VS`、`vtype`、CSR 控制位、虚拟化状态等 |
| 正常架构效果 | TBD |
| 可能同步异常 | cause code + 名称 |
| 明确不会发生 | 例如整数除零不 trap、浮点 flags 不 trap |
| 优先级 | 与同条指令其他异常同时满足时的选择 |
| `xEPC` | 应指向哪条指令 |
| `xTVAL` | 指令位、虚拟地址、0 或平台定义 |
| `htval/mtval2` | guest-page fault 时的额外地址信息 |
| 部分完成状态 | 无 / `vstart` / `vl` / 多访存提交规则 |
| 可恢复性 | restart / emulate / fatal / EEI-defined |
| 规范依据 | 章节、版本、normative rule |
| RTL/验证点 | TBD |

可直接复制以下表格为某一指令组建立测试：

| Test ID | 指令 | 模式 | 前置条件 | 注入条件 | 期望 cause | 期望 EPC/TVAL | 架构状态提交 | 备注 |
| --- | --- | --- | --- | --- | ---: | --- | --- | --- |
| TBD | `instruction` | M/S/VS/U/VU | TBD | 正常执行 | N/A | N/A | 按指令语义 |  |
| TBD | `instruction` | TBD | 扩展关闭/状态 Off | 非法执行 | 2/22 | TBD | 指令无提交 |  |
| TBD | `instruction` | TBD | 地址转换开启 | 页权限失败 | TBD | TBD | 按精确异常规则 |  |
| TBD | `instruction` | TBD | PMP/PMA 限制 | 物理访问失败 | TBD | TBD | 按精确异常规则 |  |
| TBD | `instruction` | TBD | trigger 开启 | 地址/数据命中 | 3 | TBD | 按 trigger timing |  |

## 7. 异常优先级测试模板

同一条指令可能同时满足多个错误条件。只验证“每种异常单独出现”不够，还要
验证规范允许或要求的优先级。

| Test ID | 指令类别 | 同时满足的条件 | 期望最终 cause | 规范是否允许多种结果 | 实际结果 |
| --- | --- | --- | --- | --- | --- |
| TBD | instruction fetch | misaligned + access/page fault | TBD | Yes/No | TBD |
| TBD | load | misaligned + page/access fault | TBD | Yes/No | TBD |
| TBD | store/AMO | misaligned + page/access fault | TBD | Yes/No | TBD |
| TBD | illegal memory instruction | illegal encoding + bad data address | 2 | TBD | TBD |
| TBD | guest access | first-stage + guest-stage fault | TBD | Yes/No | TBD |
| TBD | vector memory | element N fault + interrupt pending | TBD | Yes/No | TBD |
| TBD | trap handler | original trap + nested critical trap | 16/TBD | TBD | TBD |

## 8. 容易混淆但不是普通同步异常的状态

| 状态 | 记录位置 | 是否自动 trap |
| --- | --- | --- |
| 浮点 NV/DZ/OF/UF/NX | `fcsr.fflags` | 否 |
| 向量定点饱和 | `vxsat` | 否 |
| 向量不精确/精确恢复位置 | `vstart` | 本身不是 cause |
| fault-only-first 后续元素故障 | `vl` 缩短 | 通常否；element 0 仍可 trap |
| `SC` reservation 失败 | `rd != 0` | 否 |
| 整数除零 | 规定的商和余数 | 否 |
| 有符号整数加减溢出 | 截断后的 XLEN 结果 | 否 |
| Hint/MOP 未实现具体优化 | 允许为空操作 | 否 |
| pending interrupt | `mip/sip/vsip` 等 | 是异步 interrupt，不是本表 exception |
| Debug trigger 进入 Debug Mode | `dcsr/dpc` 等 | 不一定形成 code 3 的普通 trap |

## 9. 完整性检查清单

- [ ] 每条指令先套用“所有指令共有”的取指、解码、权限和 trigger 检查。
- [ ] 控制流区分 taken/not-taken，并分别覆盖 `IALIGN=16` 与 `IALIGN=32`。
- [ ] load、store、LR、SC、AMO 使用正确的 cause 名称，不能都笼统写 memory fault。
- [ ] 分开覆盖 misaligned、access fault、page fault 和 guest-page fault。
- [ ] guest 测试区分 illegal-instruction 与 virtual-instruction exception。
- [ ] CSR 测试区分纯读、实际写入、只读 CSR、权限和 counter-enable 门控。
- [ ] 浮点测试把 IEEE flags 与 trap 分开。
- [ ] 向量测试覆盖 `VS=Off`、非法寄存器组、`vstart`、逐元素恢复和 `*ff` 特例。
- [ ] Zcmp、Zcmt、成对访存和 shadow stack 覆盖一条指令内的多次隐式访问。
- [ ] CMO 按 CMO 规范使用 Store/AMO cause；prefetch hint 不期待数据访问异常。
- [ ] CFI software-check 使用 code 18，并检查 `tval` 子原因。
- [ ] trap handler 覆盖 double-trap 控制和 code 16。
- [ ] 对 reserved 编码只断言规范保证的行为，不把“常见实现会 illegal”写成 ISA 必然。
- [ ] 对 custom cause 记录厂商文档，不与标准保留编码冲突。

## 10. 参考规范

- [RISC-V Unprivileged ISA Specification v20260120](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html)
- [RISC-V Privileged Architecture v1.13](https://docs.riscv.org/reference/isa/v20260120/priv/priv-index.html)
- [Machine-Level ISA: `mcause`, exception priority, PMP and PMA](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html)
- [Hypervisor Extension: virtual instruction and guest-page faults](https://docs.riscv.org/reference/isa/v20260120/priv/hypervisor.html)
- [RV32I/RV64I Base Integer ISA](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)
- [A Extension and Atomic PMA Rules](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)
- [F Extension: floating-point flags](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html)
- [V Extension: vector exception handling](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)
- [CMO Extensions](https://docs.riscv.org/reference/isa/v20260120/unpriv/cmo.html)
- [Zc Code-Size Reduction Extensions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html)
- [Zicfilp Landing Pads](https://docs.riscv.org/reference/isa/v20260120/unpriv/zicfilp.html)
- [Zicfiss Shadow Stack](https://docs.riscv.org/reference/isa/v20260120/unpriv/zicfiss.html)
