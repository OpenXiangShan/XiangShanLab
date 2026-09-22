"""Reproduce learnTrap evidence with the repository's wavekit VcdReader."""

import argparse
import csv
import hashlib
import json
import re
import sys
from pathlib import Path


CORE = 'TOP.SimTop.cpu.l_soc.core_with_l2.core.'
CTRL = 'backend.inner_ctrlBlock.'
CSR = 'backend.inner_intExuBlock.exus_7.csr.'
FTQ = 'frontend.inner_ftq.'
IQ = 'backend.inner_intScheduler.IssueQueueAluCsrFenceDiv.'
DEFAULT_WAVE = '/nfs/home/wanghao/doCIE/xs-env/nexus-am/apps/learnTrap/runs/run-20260922-150415-Hlo0qH/learnTrap.vcd'
PATTERNS = [
    r'backend.inner_ctrlBlock.(decode|rename).io_(in|out)_\d+_(valid|ready|bits_(pc|instr|robIdx.*|exceptionVec_(2|11)|ftq.*|pdest|psrc.*|fuType|fuOpType))',
    r'backend.inner_ctrlBlock.dispatch.io_fromRename_\d+_(valid|ready|bits_(pc|instr|robIdx.*|exceptionVec_(2|11)|ftq.*|pdest|hasException|waitForward|blockBackward))',
    r'backend.inner_ctrlBlock.dispatch.(blockedByWaitForward_.*|isBlockBackward_.*|io_enqRob_.*)',
    r'backend.inner_ctrlBlock.rob.(io_(flushOut.*|exception.*|enq.*)|state|deqHasException|deqPtr.*|difftest_commit.*|difftest_event.*|exceptionGen.io_(state|out).*)',
    r'backend.inner_ctrlBlock.io_(frontend_toFtq_redirect.*|robio_(exception.*|csr_trapTarget.*))',
    r'backend.inner_intExuBlock.exus_7.csr.(io_(in|out)_.*|csrMod.(state|io_fromRob_trap.*|io_out_bits_targetPc.*|io_status.*|diff.*|mretEvent.*|trap.*))',
    r'backend.inner_intScheduler.IssueQueueAluCsrFenceDiv.io_(enq|deqDelay)_.*',
    r'backend.inner_intExuBlock.exus_[1357].brh.io_out_.*',
    r'frontend.inner_ftq.io_(toIfu_req.*|fromBackend_redirect.*)',
    r'frontend.inner_ibuffer.io_out_.*',
]


def write_csv(path, header, rows):
    with path.open('w', newline='') as stream:
        writer = csv.writer(stream)
        writer.writerow(header)
        writer.writerows(rows)


def read_wave(path, output):
    from wavekit import VcdReader

    samples = {}
    unknown = {}
    with VcdReader(str(path)) as reader:
        names = reader.file_handle.signals
        selected = {}
        for full_name in names:
            if not full_name.startswith(CORE):
                continue
            name = re.sub(r'\[\d+:\d+\]$', '', full_name[len(CORE):])
            if any(re.fullmatch(pattern, name) for pattern in PATTERNS):
                selected[name] = full_name
        clock = reader.load_waveform('TOP.clock', 'TOP.clock', sample_on_posedge=True)
        times = [int(time) for time in clock.time]
        for name, full_name in selected.items():
            wave = reader.load_waveform(full_name, 'TOP.clock', sample_on_posedge=True)
            if list(wave.time) != times:
                raise ValueError(f'Clock alignment mismatch: {full_name}')
            samples[name] = [int(value) for value in wave.value]
            if any(part in name for part in ['io_flushOut', 'io_exception', 'io_fromRob_trap', 'diffCSRState', 'io_frontend_toFtq_redirect', 'exceptionGen.io_state']):
                mask = reader.load_unknown_mask(full_name, 'TOP.clock', sample_on_posedge=True, begin_cycle=4190, end_cycle=4457)
                if any(mask.value):
                    unknown[name] = [int(cycle) for cycle, value in zip(mask.clock, mask.value) if value]
        (output / 'unknown-masks.json').write_text(json.dumps(unknown, indent=2))
        (output / 'signal-map.json').write_text(json.dumps(selected, indent=2))
        metadata = {'reader': 'wavekit.VcdReader', 'clock': 'TOP.clock', 'sample_on_posedge': True, 'begin_time': reader.begin_time, 'end_time': reader.end_time, 'sample_count': len(times), 'selected_signals': len(samples), 'wave': str(path), 'wave_sha256': hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()}
        (output / 'metadata.json').write_text(json.dumps(metadata, indent=2))
    return samples, times


def export(samples, times, output):
    def values(name):
        return samples[name]

    columns = {
        'rob_flush': CTRL + 'rob.io_flushOut_valid',
        'flush_rob': CTRL + 'rob.io_flushOut_bits_robIdx_value',
        'flush_level': CTRL + 'rob.io_flushOut_bits_level',
        'rob_exception': CTRL + 'rob.io_exception_valid',
        'rob_state': CTRL + 'rob.state',
        'exception_selected': CTRL + 'rob.exceptionGen.io_state_valid',
        'selected_rob': CTRL + 'rob.exceptionGen.io_state_bits_robIdx_value',
        'selected_isEnqExcp': CTRL + 'rob.exceptionGen.io_state_bits_isEnqExcp',
        'csr_trap': CSR + 'csrMod.io_fromRob_trap_valid',
        'csr_trap_pc': CSR + 'csrMod.io_fromRob_trap_bits_pc',
        'csr_trap_vector': CSR + 'csrMod.io_fromRob_trap_bits_trapVec',
        'target_update': CSR + 'csrMod.io_out_bits_targetPcUpdate',
        'csr_target': CSR + 'csrMod.io_out_bits_targetPc_pc',
        'csr_state': CSR + 'csrMod.state',
        'csr_in_valid': CSR + 'io_in_valid',
        'csr_in_ready': CSR + 'io_in_ready',
        'csr_in_rob': CSR + 'io_in_bits_ctrl_robIdx_value',
        'csr_out_valid': CSR + 'io_out_valid',
        'csr_out_ready': CSR + 'io_out_ready',
        'csr_out_rob': CSR + 'io_out_bits_ctrl_robIdx_value',
        'csr_out_ex11': CSR + 'io_out_bits_ctrl_exceptionVec_11',
        'csr_out_ex2': CSR + 'io_out_bits_ctrl_exceptionVec_2',
        'xret_redirect': CSR + 'io_out_bits_res_redirect_valid',
        'frontend_redirect': CTRL + 'io_frontend_toFtq_redirect_valid',
        'frontend_target': CTRL + 'io_frontend_toFtq_redirect_bits_cfiUpdate_target',
        'frontend_ftq': CTRL + 'io_frontend_toFtq_redirect_bits_ftqIdx_value',
        'frontend_offset': CTRL + 'io_frontend_toFtq_redirect_bits_ftqOffset',
        'frontend_level': CTRL + 'io_frontend_toFtq_redirect_bits_level',
        'frontend_mispred': CTRL + 'io_frontend_toFtq_redirect_bits_cfiUpdate_isMisPred',
        'fetch_valid': FTQ + 'io_toIfu_req_valid',
        'fetch_ready': FTQ + 'io_toIfu_req_ready',
        'fetch_start': FTQ + 'io_toIfu_req_bits_startAddr',
        'mepc': CSR + 'csrMod.diffCSRState_csr_mepc',
        'mcause': CSR + 'csrMod.diffCSRState_csr_mcause',
        'mtval': CSR + 'csrMod.diffCSRState_csr_mtval',
        'mstatus': CSR + 'csrMod.diffCSRState_csr_mstatus',
    }
    (output / 'column-map.json').write_text(json.dumps({key: CORE + name for key, name in columns.items()}, indent=2))
    write_csv(output / 'cycle-by-cycle.csv', ['cycle', 'time_ps'] + list(columns), ([cycle, times[cycle]] + [hex(values(name)[cycle]) for name in columns.values()] for cycle in range(4190, len(times))))
    events = []
    for name in samples:
        if not re.fullmatch(r'backend.inner_ctrlBlock.(decode.io_(in|out)|rename.io_(in|out)|dispatch.io_fromRename)_\d+_bits_pc', name):
            continue
        prefix = name[:-8]
        for cycle in range(4190, len(times)):
            if samples[prefix + '_valid'][cycle] and samples[prefix + '_ready'][cycle]:
                fields = ['pc', 'instr', 'robIdx_flag', 'robIdx_value', 'exceptionVec_2', 'exceptionVec_11', 'pdest']
                events.append([cycle, times[cycle], CORE + prefix, 1, 1, 1] + [hex(samples[prefix + '_bits_' + field][cycle]) if prefix + '_bits_' + field in samples else 'not-on-interface' for field in fields])
    write_csv(output / 'stage-events.csv', ['cycle', 'time_ps', 'interface', 'valid', 'ready', 'fire', 'pc', 'instr', 'rob_flag', 'rob_value', 'ex2', 'ex11', 'pdest'], sorted(events))
    snapshots = [4273, 4276, 4277, 4326, 4334, 4339, 4365, 4368, 4369, 4418, 4426, 4431, 4450]
    write_csv(output / 'csr-snapshots.csv', ['signal'] + [f'C{cycle}_{times[cycle]}ps' for cycle in snapshots], ([CORE + name] + [hex(samples[name][cycle]) for cycle in snapshots] for name in sorted(samples) if name.startswith(CSR + 'csrMod.diff')))
    commits = []
    for lane in range(8):
        prefix = CTRL + 'rob.difftest_commit' + (f'_{lane}' if lane else '')
        for cycle, valid in enumerate(values(prefix + '_valid')):
            if valid:
                commits.append([cycle, times[cycle], lane, hex(values(prefix + '_pc')[cycle]), hex(values(prefix + '_instr')[cycle])])
    write_csv(output / 'commits.csv', ['cycle', 'time_ps', 'lane', 'pc', 'instr'], sorted(commits))
    interfaces = [CTRL + 'dispatch.io_fromRename_3', CTRL + 'dispatch.io_fromRename_2', IQ + 'io_deqDelay_1', CSR + 'io_in', CSR + 'io_out']
    interface_rows = []
    for prefix in interfaces:
        for cycle in range(4245, len(times)):
            valid = values(prefix + '_valid')[cycle]
            ready = values(prefix + '_ready')[cycle]
            identity = next((prefix + suffix for suffix in ['_bits_robIdx_value', '_bits_common_robIdx_value', '_bits_ctrl_robIdx_value'] if prefix + suffix in samples), None)
            interface_rows.append([cycle, times[cycle], CORE + prefix, valid, ready, valid & ready, values(identity)[cycle] if identity else 'not-on-interface'])
    write_csv(output / 'interface-cycles.csv', ['cycle', 'time_ps', 'interface', 'valid', 'ready', 'fire', 'rob_value'], interface_rows)
    review = ['# 连续逐拍 review 索引', '', '由 analyze_trap.py 从 wavekit 采样值生成。每行一拍；列出的事件只覆盖本表选定的 trap/返回接口，不代表处理器其他模块没有活动。', '', '完整路径见 evidence/column-map.json；正文的插图⑥/⑧/⑪/⑫解释这些信号。C 是文件绝对正沿采样序号，时间单位 ps。', '', '| C | ps | ROB 状态 | CSR 状态 | 本拍有效事件 |', '| --- | --- | --- | --- | --- |']
    for cycle in range(4250, 4445):
        messages = []
        if values(columns['rob_flush'])[cycle]:
            messages.append(f"ROB flush：ROB={values(columns['flush_rob'])[cycle]}")
        if values(columns['rob_exception'])[cycle]:
            messages.append('ROB exception.valid=1')
        if values(columns['csr_trap'])[cycle]:
            messages.append(f"CSR trap：PC={hex(values(columns['csr_trap_pc'])[cycle])}，向量={hex(values(columns['csr_trap_vector'])[cycle])}")
        if values(columns['target_update'])[cycle]:
            messages.append(f"目标更新={hex(values(columns['csr_target'])[cycle])}")
        if values(columns['frontend_redirect'])[cycle]:
            messages.append(f"前端 redirect={hex(values(columns['frontend_target'])[cycle])}，level={values(columns['frontend_level'])[cycle]}")
        if values(columns['csr_in_valid'])[cycle] and values(columns['csr_in_ready'])[cycle]:
            messages.append(f"CSR 输入 fire，ROB={values(columns['csr_in_rob'])[cycle]}")
        if values(columns['csr_out_valid'])[cycle] and values(columns['csr_out_ready'])[cycle]:
            messages.append(f"CSR 输出 fire，ROB={values(columns['csr_out_rob'])[cycle]}，EX11={values(columns['csr_out_ex11'])[cycle]}，xRET redirect={values(columns['xret_redirect'])[cycle]}")
        for field in ['mepc', 'mcause', 'mtval', 'mstatus']:
            if values(columns[field])[cycle] != values(columns[field])[cycle - 1]:
                messages.append(f'{field}→{hex(values(columns[field])[cycle])}')
        if not messages:
            messages.append('所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv')
        review.append(f"| {cycle} | {times[cycle]} | {values(columns['rob_state'])[cycle]} | {values(columns['csr_state'])[cycle]} | {'；'.join(messages)} |")
    (output.parent / 'cycle-review.md').write_text('\n'.join(review) + '\n')
    forbidden = {hex(pc) for pc in [0x80000044, 0x80000078, 0x80000048, 0x8000004c, 0x8000007c, 0x80000080, 0x800000a0, 0x800000a4]}
    assert not any(row[3] in forbidden for row in commits)
    assert [cycle for cycle, value in enumerate(values(columns['rob_flush'])) if value] == [4273, 4365]
    assert all(times[cycle] == 2 * cycle for cycle in range(len(times)))
    assert values(columns['mepc'])[4277] == 0x80000044
    assert values(columns['mepc'])[4369] == 0x80000078
    assert values(columns['mcause'])[4277] == 11
    assert values(columns['mcause'])[4369] == 2
    print(f'PASS: {len(times)} samples, two ROB traps, forbidden PCs absent from all commit lanes')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--wave', type=Path, default=Path(DEFAULT_WAVE))
    parser.add_argument('--output', type=Path, default=Path(__file__).parent / 'evidence')
    parser.add_argument('--wavekit-src', type=Path, required=True)
    args = parser.parse_args()
    sys.path.insert(0, str(args.wavekit_src))
    args.output.mkdir(parents=True, exist_ok=True)
    samples, times = read_wave(args.wave, args.output)
    export(samples, times, args.output)


if __name__ == '__main__':
    main()
