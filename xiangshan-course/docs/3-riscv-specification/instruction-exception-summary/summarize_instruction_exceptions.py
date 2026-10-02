#!/usr/bin/env python3
"""Generate a Markdown summary from arch-fuzz instruction YAML annotations."""
from __future__ import annotations
import argparse, re, unicodedata
from collections import Counter, defaultdict
from pathlib import Path

TRAP = ("精确陷阱：故障指令不提交目的寄存器/内存副作用；写入目标特权级的 "
        "`xepc/xcause/xtval`，更新 `xstatus`，PC 转到 `xtvec`。")

SEM = {
"IllegalInst": ("编码未实现/保留、扩展未使能，或当前特权级/状态不允许执行。", "取指后的译码、合法性与特权检查阶段。", "同步精确陷阱：Illegal instruction（cause=2）。", TRAP),
"IllegalCsr": ("CSR 不存在、权限不足或写只读 CSR（CSRRS/CSRRC 源为 0 时无写访问）。", "CSR 地址、权限和读写属性检查阶段。", "项目细分类；架构上并入 Illegal instruction（cause=2）。", TRAP+" CSR 与目的寄存器不修改。"),
"IllegalVtype": ("向量 `vtype` 包含不支持或保留组合。", "`vset*` 执行阶段的 vtype 合法性检查。", "项目内部标记；规范通常设置 `vtype.vill=1`，不必然陷阱。", "通常更新 `vtype.vill=1`、`vl=0`；是否升级为 Illegal instruction 以 RTL 为准。"),
"AddrMisalign_inst": ("取指 PC 或跳转/分支目标不满足 `IALIGN`。", "取指地址检查，或跳转执行后的目标检查。", "同步精确陷阱：Instruction address misaligned（cause=0）。", TRAP+" `xtval` 记录未对齐地址。"),
"PageFault_inst": ("分页取指翻译失败、PTE 无效或取指权限检查失败。", "ITLB/PTW 翻译与取指权限检查阶段。", "同步精确陷阱：Instruction page fault（cause=12）。", TRAP+" `xtval` 为故障虚拟地址。"),
"AddrMisalign_load": ("load 有效地址未按自然宽度对齐，且不支持非对齐访问。", "Load AGU/LSU 地址生成与对齐检查。", "同步精确陷阱：Load address misaligned（cause=4）。", TRAP+" `xtval` 为有效地址，目的寄存器不写回。"),
"AccessFault_load": ("load 的物理地址、PMP/PMA、缓存/总线或设备访问失败。", "Load LSU 保护检查或缓存/总线响应阶段。", "同步精确陷阱：Load access fault（cause=5）。", TRAP+" `xtval` 为故障地址，目的寄存器不写回。"),
"PageFault_load": ("分页 load 翻译失败、PTE 无效或 R/U/A 权限检查失败。", "DTLB/PTW 翻译与 load 权限检查。", "同步精确陷阱：Load page fault（cause=13）。", TRAP+" `xtval` 为故障虚拟地址。"),
"AddrMisalign_store": ("store 有效地址未按自然宽度对齐，且不支持非对齐访问。", "Store AGU/LSU 地址生成与对齐检查。", "同步精确陷阱：Store/AMO address misaligned（cause=6）。", TRAP+" `xtval` 为有效地址；内存不修改。"),
"AccessFault_store": ("store 的物理地址、PMP/PMA、缓存/总线或设备访问失败。", "Store LSU 保护检查或缓存/总线响应阶段。", "同步精确陷阱：Store/AMO access fault（cause=7）。", TRAP+" `xtval` 为故障地址；无架构可见写入。"),
"PageFault_store": ("分页 store 翻译失败、PTE 无效或 W/U/A/D 权限检查失败。", "DTLB/PTW 翻译与 store 权限检查。", "同步精确陷阱：Store/AMO page fault（cause=15）。", TRAP+" `xtval` 为故障虚拟地址；无架构可见写入。"),
"AddrMisalign_amo": ("LR/SC/AMO 地址未按访问宽度自然对齐。", "原子 LSU 地址生成与对齐检查。", "同步精确陷阱：Store/AMO address misaligned（cause=6）。", TRAP+" 原子读改写不发生，`xtval` 为有效地址。"),
"AccessFault_amo": ("LR/SC/AMO 的物理地址、PMP/PMA、原子能力或总线检查失败。", "原子 LSU 保护/一致性/总线响应阶段。", "同步精确陷阱：Store/AMO access fault（cause=7；LR 归类以实现为准）。", TRAP+" 原子内存更新不发生。"),
"PageFault_amo": ("分页 LR/SC/AMO 翻译失败，或页表读写/A/D 权限检查失败。", "原子 LSU 的 DTLB/PTW 翻译与权限检查。", "同步精确陷阱：通常 Store/AMO page fault（cause=15；LR 可按 load 处理）。", TRAP+" 原子内存更新不发生。"),
"AddrMisalign": ("向量访存某活动元素地址未按元素宽度对齐。", "Vector LSU 元素地址生成与对齐检查。", "项目通用标记；按方向对应 load cause=4 或 store/AMO cause=6。", TRAP+" `xtval` 为故障元素地址；`vstart` 支持重启，早先元素可能已完成。"),
"AccessFault": ("向量访存活动元素的 PMP/PMA、物理地址、缓存/总线检查失败。", "Vector LSU 保护检查及缓存/总线响应。", "项目通用标记；按方向对应 load cause=5 或 store/AMO cause=7。", TRAP+" `xtval` 为故障元素地址；`vstart` 标识恢复位置。"),
"AccessException": ("预取提示地址在翻译、权限、PMP/PMA 或存储访问时不可达。", "预取地址生成、翻译或存储请求阶段。", "项目内部标记；Zicbop 预取为 HINT，规范上通常忽略而不陷阱。", "通常不改寄存器、内存或陷阱 CSR；实现若升级为陷阱以 RTL 为准。"),
"CALL": ("执行 `ECALL`。", "译码/执行后提交点。", "同步精确陷阱：Environment call（cause=8/9/10/11，依来源特权级）。", TRAP+" `xtval=0`，通用寄存器不变。"),
"BREAK": ("执行 `EBREAK`（未被调试模块直接接管）。", "译码/执行后提交点或调试触发检查。", "同步精确陷阱：Breakpoint（cause=3），也可能进入 Debug Mode。", TRAP+" 常规陷阱 `xtval` 通常为 0；Debug Mode 改写 `dpc/dcsr`。"),
"NV": ("浮点无效操作，如 0/0、∞−∞、0×∞、负数开方或信号 NaN。", "浮点执行单元特殊值检测与结果形成。", "浮点累计标志：Invalid (`fflags.NV`)，不是同步陷阱。", "结果正常写回，`fflags.NV` sticky 置 1，`fcsr` 更新。"),
"DZ": ("有限非零数除以精确零。", "浮点除法的除数分类与结果形成。", "浮点累计标志：Divide by Zero (`fflags.DZ`)。", "结果正常写回，`fflags.DZ` sticky 置 1。"),
"OF": ("舍入结果超出目标格式有限范围。", "浮点规格化、舍入与打包。", "浮点累计标志：Overflow (`fflags.OF`)。", "写回无穷或最大有限数，`OF` 置 1，通常同时置 `NX`。"),
"UF": ("结果既微小又不精确。", "浮点规格化、舍入与打包。", "浮点累计标志：Underflow (`fflags.UF`)。", "写回次正规数/零，`UF` 置 1，通常同时置 `NX`。"),
"NX": ("精确结果无法表示而发生舍入。", "浮点最终舍入与打包。", "浮点累计标志：Inexact (`fflags.NX`)。", "结果正常写回，`NX` sticky 置 1，`fcsr` 更新。"),
"FP": ("向量浮点出现 NV/DZ/OF/UF/NX 条件；项目汇总标签。", "Vector FPU 特殊值检测、运算、规格化和舍入。", "浮点状态标志汇总，不是同步陷阱。", "活动元素按向量语义写回，对应 `fflags` sticky 位置 1。"),
}

def clean(s):
    s = s.split('#',1)[0].strip().strip("'\"")
    return ''.join(c for c in s if unicodedata.category(c) != 'Cf').strip()

def parse(path, root):
    ext = path.stem; active = False; cur = None; in_exc = False; out = []
    for line in path.read_text(encoding='utf-8').splitlines():
        m = re.match(r'^extension:\s*(.+?)\s*$', line)
        if m and not active: ext = clean(m.group(1))
        if re.match(r'^instructions:\s*$', line): active = True; continue
        if not active: continue
        # Instruction keys are exactly four spaces deep.  Excluding whitespace
        # here prevents eight-space properties (format, operands, ...) from
        # being mistaken for instruction names.
        m = re.match(r'^ {4}([^\s:#][^:]*):\s*$', line)
        if m:
            if cur: out.append(cur)
            cur = {'name':clean(m.group(1)), 'ext':ext, 'src':path.relative_to(root).as_posix(), 'exc':[]}; in_exc=False; continue
        if cur is None: continue
        if re.match(r'^ {8}exceptions:\s*$', line): in_exc=True; continue
        if in_exc:
            m = re.match(r'^ {8}-\s*(.+?)\s*$', line)
            if m: cur['exc'].append(clean(m.group(1))); continue
            if line.strip(): in_exc=False
    if cur: out.append(cur)
    return out

def cell(s): return s.replace('|','\\|').replace('\n','<br>')
def inst_cell(items):
    groups = defaultdict(list)
    for i in items: groups[(i['ext'],i['src'])].append(i['name'])
    return '<br>'.join(f"**{e}** (`{src}`): " + ', '.join(f'`{n}`' for n in dict.fromkeys(ns)) for (e,src),ns in sorted(groups.items()))

def generate(root):
    files = sorted(root.rglob('*.yaml')); all_i = [i for p in files for i in parse(p,root)]; annotated=[i for i in all_i if i['exc']]
    by = defaultdict(list)
    for i in annotated:
        for e in dict.fromkeys(i['exc']): by[e].append(i)
    unknown = sorted(set(by)-set(SEM))
    if unknown: raise SystemExit('未建立语义映射的异常类型: '+', '.join(unknown))
    counts=Counter({e:len(v) for e,v in by.items()}); assoc=sum(len(i['exc']) for i in annotated)
    L=['# 指令异常触发汇总','', '> 本文由脚本从 `arch-fuzz/instructions/specs/**/*.yaml` 自动生成。','', '## 口径说明','', '- `exceptions` 是候选标签；触发条件、阶段和架构状态按 RISC-V 语义补充。','- `NV/DZ/OF/UF/NX/FP` 通常只更新 `fflags`，不是 trap；`IllegalVtype`、`AccessException` 为项目内部标签，已单独注明。','- `xepc/xcause/xtval/xstatus/xtvec` 中的 `x` 表示承接陷阱的 M/S/VS 级别。','- 阶段名称是定位参考，不等同于 XiangShan RTL 的唯一模块边界。','', '## 覆盖统计','', f'- 规格文件：**{len(files)}** 个', f'- 指令定义：**{len(all_i)}** 条', f'- 含异常指令：**{len(annotated)}** 条', f'- 异常标签类型：**{len(by)}** 种', f'- 指令—异常关联：**{assoc}** 项','', '| 异常类型 | 关联指令数 |','|---|---:|']
    L += [f'| `{e}` | {counts[e]} |' for e in sorted(counts)] + ['', '## 异常触发明细','', '| 指令 | 触发条件 | 触发阶段 | 异常类型 | 影响架构状态 |','|---|---|---|---|---|']
    for e in SEM:
        if e not in by: continue
        cond,stage,kind,state=SEM[e]; L.append('| '+' | '.join([cell(inst_cell(by[e])),cell(cond),cell(stage),cell(f'`{e}`：{kind}'),cell(state)])+' |')
    L += ['', '## 使用与复现','', '```bash', 'python3 /nfs/home/lvzhichao/.codex/summarize_instruction_exceptions.py \\', '  --specs-dir /nfs/home/lvzhichao/XiangShanLab/arch-fuzz/instructions/specs \\', '  --output /nfs/home/lvzhichao/XiangShanLab/instruction-exception-summary.md', '```','', '遇到未映射的新标签时脚本会失败并报告，避免静默遗漏。','']
    return '\n'.join(L), (len(files),len(all_i),len(by),assoc)

def main():
    p=argparse.ArgumentParser(); p.add_argument('--specs-dir',type=Path,required=True); p.add_argument('--output',type=Path,required=True); a=p.parse_args()
    text,stats=generate(a.specs_dir.resolve()); a.output.resolve().write_text(text,encoding='utf-8'); print(f'Generated {a.output.resolve()} | files={stats[0]} instructions={stats[1]} types={stats[2]} associations={stats[3]}')
if __name__=='__main__': main()
