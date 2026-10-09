"""Copy focused excerpts from the matching local XiangShan source tree."""

import subprocess
from pathlib import Path


ROOT = Path('/nfs/home/wanghao/emuByYuan/stable-kmh-v2')
EXPECTED = 'abd0f867a86b66a92d4fc5d3c6d62944725c747f'
SOURCE = ROOT / 'src/main/scala/xiangshan'
SECTIONS = [
    ('S1', '译码：ECALL 分类与非法指令标记', [('backend/decode/DecodeUnit.scala', 219, 226), ('backend/decode/DecodeUnit.scala', 928, 936)]),
    ('S2', 'Rename：ROB 身份分配与回收', [('backend/rename/Rename.scala', 176, 184), ('backend/rename/Rename.scala', 342, 348), ('backend/rename/Rename.scala', 410, 416)]),
    ('S3', 'Dispatch：等待前方排空，不等于没有容量', [('backend/dispatch/NewDispatch.scala', 794, 801), ('backend/dispatch/NewDispatch.scala', 805, 830)]),
    ('S4', 'IssueQueue：注册后的发射接口', [('backend/issue/IssueQueue.scala', 808, 820)]),
    ('S5', 'CSR wrapper：ECALL 异常、trap 输入与 MRET 重定向', [('backend/fu/wrapper/CSR.scala', 128, 138), ('backend/fu/wrapper/CSR.scala', 255, 265), ('backend/fu/wrapper/CSR.scala', 307, 334)]),
    ('S6', 'ROB 和 ExceptionGen：发现异常与处理异常分离', [('backend/rob/ExceptionGen.scala', 152, 167), ('backend/rob/Rob.scala', 299, 300), ('backend/rob/Rob.scala', 579, 585), ('backend/rob/Rob.scala', 619, 645), ('backend/rob/Rob.scala', 844, 853), ('backend/rob/Rob.scala', 1171, 1179)]),
    ('S7', 'CtrlBlock：清空控制和前端目标对齐', [('backend/CtrlBlock.scala', 104, 123), ('backend/CtrlBlock.scala', 333, 349), ('backend/CtrlBlock.scala', 361, 387), ('backend/CtrlBlock.scala', 738, 745)]),
    ('S8', 'NewCSR：trap 选择、状态机和目标旁路', [('backend/fu/NewCSR/NewCSR.scala', 805, 813), ('backend/fu/NewCSR/NewCSR.scala', 1044, 1072), ('backend/fu/NewCSR/NewCSR.scala', 1097, 1112), ('backend/fu/NewCSR/NewCSR.scala', 1129, 1144)]),
    ('S9', 'Trap entry 保存现场，MRET 恢复现场', [('backend/fu/NewCSR/TrapHandleModule.scala', 96, 110), ('backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala', 93, 100), ('backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala', 120, 138), ('backend/fu/NewCSR/CSREvents/MretEvent.scala', 51, 81)]),
    ('S10', 'FTQ、IBuffer 和 Decode 的边界', [('frontend/NewFtq.scala', 901, 905), ('frontend/NewFtq.scala', 951, 962), ('frontend/IBuffer.scala', 279, 294), ('backend/CtrlBlock.scala', 487, 505)]),
    ('S11', '分支预测纠正与异常年龄优先级', [('backend/fu/wrapper/BranchUnit.scala', 30, 66), ('backend/ctrlblock/RedirectGenerator.scala', 39, 62), ('backend/CtrlBlock.scala', 190, 199)]),
    ('S12', '波形采样方法', []),
]


def main():
    actual = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], text=True).strip()
    if actual != EXPECTED:
        raise RuntimeError(f'Source commit mismatch: {actual}')
    output = ['# Trap 分析的源码摘录', '', f'主仓库 HEAD：`{actual}`。以下为本地文件摘录；工作树状态限制见正文。', '', '阅读方式：先读正文解释，再看对应 S 编号。代码片段保持原文，因此原注释中可能存在与实际波形不完全一致的时间标签；正文已指出 T3/T4 的差异。', '']
    for number, title, snippets in SECTIONS:
        output.extend([f'<a id="{number.lower()}"></a>', f'## {number} {title}', ''])
        for relative, first, last in snippets:
            path = SOURCE / relative
            lines = path.read_text().splitlines()
            output.extend([f'[{path.name}:{first}]({path}#L{first})，摘录 {first}～{last} 行。', '', '```scala', '\n'.join(lines[first - 1:last]), '```', ''])
    output.extend(['仓库 wavekit 的 `src/wavekit/readers/base.py` 提供 `load_waveform(..., sample_on_posedge=True)` 与 `load_unknown_mask`；`src/wavekit/readers/value_change.pyx` 采用 `value_time <= clock_time` 的末值规则。故本文以同时间戳最终记录值为准，不宣称恢复了 delta-cycle 内部变化。', ''])
    Path(__file__).with_name('source-excerpts.md').write_text('\n'.join(output))


if __name__ == '__main__':
    main()
