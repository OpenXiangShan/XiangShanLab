import argparse
import hashlib
import importlib.metadata
import json
import re
import sys
from pathlib import Path

import numpy as np
import wavekit
from wavekit import FstReader

ROOT = 'TOP.SimTop.cpu.l_soc.core_with_l2.core.'
P = ROOT + 'memBlock.inner_lsq.storeQueue.physicalQueue'
V = ROOT + 'memBlock.inner_lsq.storeQueue.virtualStoreQueue'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--input', default='replay.fst')
    ap.add_argument('--output', default='store-fault-analysis/head_audit.json')
    ap.add_argument('--begin', type=int, default=6000)
    ap.add_argument('--end', type=int, default=7180)
    ap.add_argument('--extra', action='append', default=[])
    ap.add_argument('--quiet', action='store_true')
    args = ap.parse_args()
    path = Path(args.input).resolve()
    before = path.stat()
    out = dict(input=str(path), size=before.st_size, mtime_ns=before.st_mtime_ns,
               interpreter=sys.executable, version=importlib.metadata.version('wavekit'),
               module=wavekit.__file__, window=[args.begin, args.end], signals={})
    selectors = {
        P: r'(deqPtrExt_[01]_.*|rdataPtrExt_[01]_.*|commitPtrExt_[0-3]_.*|addrReadyPtr_.*|dataReadyPtr_.*|validPtr_.*|validCount|redirectNeedClean|needCancel_[0-9]+|deqCancel_[0-9]+|.*[Rr]edirect.*|.*PreCommitPtr.*|.*RetiredPtr.*|io_fromVirtualStoreQueue_physicalQueueEnqPtr_.*|ctrlEntries_[04]_(allocated|addrValid|dataValid|hasException|committed|waitStoreS2|vecInactive|vecMbCommit)|dataEntries_[04]_(memoryType|vaddr|paddrHigh|debugPaddr|debugUop_(pc|instr|robIdx_.*|sqIdx_.*))|staSetValid_[04]|staReValid_[04]|hasExceptionSet_[04])',
        P + '.deqModule': r'(io_rdataCtrlEntries_0_.*|io_rdataDataEntries_0_(memoryType|debugPaddr|debugUop_.*)|uncacheState|uncacheCanHandle|io_toUncacheBuffer_req_valid_0|io_writeBack_valid|io_writeBack_bits_.*)',
        V: r'(.*[Rr]edirect.*|preCommit.*|deqPtrVec_[0-3]_.*|enqPtrVec_[0-3]_.*|physicalQueueEnqPtr_.*|io_toPhysicalQueue_headRobIdx_.*|io_fromRob_robHeadPtr_.*|dataEntries_[04]_(reqNum|robIdx_.*|uopIdx)|ctrlEntries_[04]_(allocated|vecMbCommit))',
    }
    with FstReader(str(path)) as r:
        out['top_scopes'] = [s.full_name for s in r.top_scopes]
        selected = {}
        for scope, pattern in selectors.items():
            node = next(iter(r.get_matched_scopes(scope).values()))
            assert node.children
            for sig in r.get_matched_signals(scope + '.*').values():
                if re.fullmatch(pattern, sig.base_name):
                    selected[sig.full_name] = sig
        for query in ['TOP.reset'] + args.extra:
            matches = r.get_matched_signals(query)
            assert matches, query
            selected.update({s.full_name: s for s in matches.values()})
        axes = None
        for name, sig in selected.items():
            kw = dict(clock='TOP.clock', sample_on_posedge=True,
                      begin_cycle=args.begin, end_cycle=args.end)
            w = r.load_waveform(name, signed=False, **kw)
            u = r.load_unknown_mask(name, **kw)
            assert len(w.clock) == args.end - args.begin, name
            assert not np.any(u.value), name
            assert np.array_equal(w.clock, u.clock) and np.array_equal(w.time, u.time)
            if axes is None:
                axes = w.clock, w.time
                out['samples'] = len(w.clock)
                out['times'] = [int(w.time[0]), int(w.time[-1])]
            assert np.array_equal(w.clock, axes[0]) and np.array_equal(w.time, axes[1])
            if name == 'TOP.reset':
                assert not np.any(w.value)
            idx = np.unique(np.r_[0, np.flatnonzero(w.value[1:] != w.value[:-1]) + 1, len(w.value)-1])
            out['signals'][name] = dict(width=sig.width, unknown=False,
                changes=[[int(w.clock[i]), int(w.time[i]), int(w.value[i])] for i in idx])
    after = path.stat()
    assert (before.st_ino, before.st_size, before.st_mtime_ns) == (after.st_ino, after.st_size, after.st_mtime_ns)
    with path.open('rb') as f:
        h = hashlib.sha256()
        for block in iter(lambda: f.read(1024*1024), b''):
            h.update(block)
    out['sha256'] = h.hexdigest()
    Path(args.output).write_text(json.dumps(out, indent=2) + '\n')
    for name, value in out['signals'].items():
        if not args.quiet:
            print(name.replace(ROOT, ''), value['width'], value['changes'])


if __name__ == '__main__':
    main()
