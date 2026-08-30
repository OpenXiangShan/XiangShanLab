### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

XiangShan appears to lose the write-intent semantics of Zicbop `prefetch.w` in NewLoadUnit.

`DecodeUnit` correctly recognizes the instruction as `LSUOpType.prefetch_w`, but NewLoadUnit still sends the request as read-intent:

- the TLB request command is hardwired to `TlbCmd.read`
- the DCache request command for all prefetches is `M_PFR`
- no `M_PFW` request is observed for `prefetch.w`

This matters for permission and side-effect suppression. In the reproducer, the target address is covered by a locked read-only 4 KiB PMP NAPOT entry:

- `target_page = 0x80001000`
- `pmpaddr0` covers that 4 KiB page
- `pmpcfg0 = 0x99`, meaning locked, NAPOT, R=1, W=0, X=0

The test then executes:

```text
0x80000130: 00346013  prefetch.w 0(s0)
```

Observed monitor result:

```json
{
  "predicate_result": "reproduced",
  "target_addr": "0x80001000",
  "bad_issue_event_count": 1,
  "accepted_bad_event_count": 1,
  "s2_not_blocked_event_count": 1,
  "write_intent_event_count": 0
}
```

Key observed events:

```text
time 1227:
  ldin_fuop=10
  tlb_is_prefetch=1
  s0_req_cmd=2
  s0_req_vaddr=0x80001000

time 1227:
  top_req_valid=1
  top_req_ready=1
  top_req_cmd=2
  top_req_vaddr=0x80001000

time 1229:
  s2_pipein_fuop=10
  s2_pipein_vaddr=0x80001000
  s2_pmp_ld=0
  s2_pmp_unaccessable=0
  s2_dcache_kill=0
```

Here `s0_req_cmd=2` and `top_req_cmd=2` are `M_PFR`, a prefetch with intent to read. I did not observe any `M_PFW` event for the target `prefetch.w`.

I am not claiming that this hint instruction must raise an architectural exception. The issue is that `prefetch.w` should preserve write intent for TLB/PMP/PMA and DCache permission/attribute checks, so that a write-prefetch to a W-denied PMP page can be suppressed instead of being admitted as a read-prefetch side effect.

### Expected behavior

`prefetch.w` should be carried as write intent, not read intent:

- TLB/PMP/PMA permission checks should see write intent for `prefetch.w`.
- DCache should receive `M_PFW`, not `M_PFR`, for `prefetch.w`.
- A `prefetch.w` targeting a locked read-only PMP page should not be admitted as a read-prefetch request that can create a cache-side effect.

### Environment

- XiangShan branch: `kunminghu-v3`
- XiangShan commit: `3931c5112c528299a23c256bdd77fb90813afa6e`

### To Reproduce

Unpack the attached [prefetchw-pmp-write.zip](https://github.com/user-attachments/files/29404087/prefetchw-pmp-write.zip), then run:

```bash
cd attachment
XIANGSHAN_HOME=/path/to/XiangShan bash run_poc.sh
```

The script builds the small RISC-V program, runs the XiangShan emulator with VCD dumping enabled, and then runs the monitor over the generated VCD.

Expected local monitor result showing the bug:

```text
"predicate_result": "reproduced"
"bad_issue_event_count": 1
"accepted_bad_event_count": 1
"s2_not_blocked_event_count": 1
"write_intent_event_count": 0
```

### Additional context

_No response_
