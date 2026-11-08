import argparse
import hashlib
import importlib.metadata
import json
from pathlib import Path
import sys

import numpy as np
import wavekit
from wavekit import FstReader


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--input', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    ap.add_argument('--expect-af', type=int, choices=(0, 1), default=0)
    args = ap.parse_args()
    path = args.input.resolve(strict=True)
    before = path.stat()
    digest = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            digest.update(chunk)
    b = 'TOP.SimTop.cpu.l_soc.core_with_l2.core'
    s = b + '.memBlock.inner_StoreUnit_0'
    names = {'reset': 'TOP.reset', 'core_reset': b + '.memBlock.reset'}
    for n in ['io_stin_valid', 'io_stin_ready', 'io_stin_bits_robIdx_flag',
              'io_stin_bits_robIdx_value', 's2.io_pipeIn_valid',
              's2.io_pipeIn_bits_uop_robIdx_value', 's2.io_pipeIn_bits_paddr',
              's2.io_pipeIn_bits_tlbHit', 's2.io_pmp_st', 's2.io_pmp_mmio',
              's2.hasException', 's3.io_pipeIn_valid',
              's3.io_pipeIn_bits_uop_robIdx_value', 's3.io_pipeIn_bits_uop_exceptionVec_7',
              's3.io_pipeIn_bits_hasException', 's3.io_pipeIn_bits_mmio',
              's3.io_pipeIn_bits_needRSReplay', 's3.io_pipeIn_bits_accessType_instrType',
              's3.kill', 's3.delayPipe.io_in_valid', 's3.io_stout_toRob_valid']:
        names[n] = s + '.' + n
    for n in ['io_req_valid', 'io_req_bits_addr', 'io_req_bits_cmd', 'io_resp_st']:
        names['pmp.' + n] = b + '.memBlock.inner_pmp_checkers_4.' + n
    for n in ['deqPtrVec_0_value', 'deqPtrVec_0_flag', 'io_exception_valid']:
        names['rob.' + n] = b + '.backend.inner_ctrlBlock.rob.' + n
    for lane in range(8):
        scope = 'TOP.SimTop.endpoint.commit' + (f'_{lane}' if lane else '')
        for n in ['io_bits_valid', 'io_bits_pc', 'io_bits_robIdx']:
            names[f'commit{lane}.{n}'] = scope + '.' + n
    names['trap.pc'] = 'TOP.SimTop.endpoint.trap.io_bits_pc'
    names['trap.cycle'] = 'TOP.SimTop.endpoint.trap.io_bits_cycleCnt'
    for scope, fields in [('inner_sqStout', ['io_in_valid', 'io_in_ready', 'io_out_valid', 'io_out_ready']),
                          ('inner_uncache', ['io_lsq_req_valid', 'io_lsq_req_ready', 'io_lsq_resp_valid'])]:
        for field in fields:
            names['mmio.' + scope + '.' + field] = b + '.memBlock.' + scope + '.' + field
    result = {'interpreter': sys.executable, 'version': importlib.metadata.version('wavekit'),
              'module': wavekit.__file__, 'path': str(path), 'size': before.st_size,
              'mtime_ns': before.st_mtime_ns, 'sha256': digest.hexdigest(), 'windows': []}
    with FstReader(str(path)) as r:
        result['bounds'] = [r.begin_time, r.end_time]
        result['top_scopes'] = [t.full_name for t in r.top_scopes]
        result['store_children'] = [c.full_name for t in r.get_matched_scopes(s).values() for c in t.children if hasattr(c, 'children')]
        for begin, end in [(7138, 7170), (7162, 22164)]:
            selected = names if begin == 7138 else {k: v for k, v in names.items() if k.startswith(('commit', 'rob.', 'trap.', 'mmio.')) or k in ('reset', 'core_reset')}
            waves = {}
            meta = {}
            for key, signal in selected.items():
                matches = r.get_matched_signals(signal)
                if len(matches) != 1:
                    raise ValueError(f'{signal}: expected one match, got {len(matches)}')
                sig = next(iter(matches.values()))
                kw = dict(clock='TOP.clock', sample_on_posedge=True, begin_cycle=begin, end_cycle=end)
                w = r.load_waveform(sig.full_name, **kw)
                m = r.load_unknown_mask(sig.full_name, **kw)
                if len(w.clock) != end - begin or not np.array_equal(w.clock, np.arange(begin, end)):
                    raise ValueError('Incomplete or empty window')
                if not np.array_equal(w.clock, m.clock) or not np.array_equal(w.time, m.time) or np.any(m.value):
                    raise ValueError(f'Unknown or misaligned evidence: {signal}')
                if waves:
                    ref = next(iter(waves.values()))
                    if not np.array_equal(w.clock, ref.clock) or not np.array_equal(w.time, ref.time):
                        raise ValueError('Misaligned signals')
                waves[key] = w
                meta[key] = {'path': sig.full_name, 'width': sig.width, 'unknown_samples': 0}
            if np.any(waves['reset'].value) or np.any(waves['core_reset'].value):
                raise ValueError('Reset in verification window')
            rows = [{ 'cycle': int(begin+i), 'time': int(next(iter(waves.values())).time[i]),
                      **{k: int(w.value[i]) for k, w in waves.items()}} for i in range(end-begin)]
            win = {'begin': begin, 'end': end, 'samples': len(rows), 'signals': meta,
                   'time_first': rows[0]['time'], 'time_last': rows[-1]['time']}
            if begin == 7138:
                win['rows'] = rows
                trigger = rows[7144-begin]
                if not (trigger['s2.io_pipeIn_valid'] and trigger['s2.io_pipeIn_bits_uop_robIdx_value'] == 0xbb and trigger['s2.io_pipeIn_bits_paddr'] == 0x2cc):
                    raise ValueError('Expected trigger absent')
                actual = trigger['s2.io_pmp_st']
                win['af_check'] = {'status': 'PASS' if actual == args.expect_af else 'FAIL',
                                   'expected': args.expect_af, 'actual': actual, 'evidence': trigger}
            else:
                win['nonzero_commits'] = [row for row in rows if any(row[f'commit{i}.io_bits_valid'] for i in range(8))]
                win['unique'] = {k: sorted({row[k] for row in rows}) for k in waves if not k.startswith('commit') and k != 'trap.cycle'}
                win['trap_cycle_bounds'] = [rows[0]['trap.cycle'], rows[-1]['trap.cycle']]
                win['status'] = 'PASS' if not win['nonzero_commits'] else 'FAIL'
            result['windows'].append(win)
    after = path.stat()
    result['metadata_unchanged'] = (before.st_size, before.st_mtime_ns, before.st_ino) == (after.st_size, after.st_mtime_ns, after.st_ino)
    if not result['metadata_unchanged']:
        raise ValueError('Waveform changed during extraction')
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'af': result['windows'][0]['af_check']['status'], 'no_commit': result['windows'][1]['status'], 'output': str(args.output)}))


if __name__ == '__main__':
    main()
