# chiselAIA module bug summary

- Count: `13`
- Exception-triggered: `2`
- Interrupt-triggered: `12`
- Source: `issues.jsonl` and `pulls.jsonl`
- Rule: classified from labels, title, body, branch names, and referenced directory/component names.
- Trigger: `exception` and `interrupt` are highlighted from title/body/labels keywords.

| Number | Type | State | Updated | Branch | Commit | Trigger | Labels | Title |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [#6128](https://github.com/OpenXiangShan/XiangShan/pull/6128) | PR | closed | 2026-07-09T09:36:19Z | kunminghu-v2 |  | interrupt | module: backend, module: tool, topic: functionality, note: submodule bump | fix(aia): fix m/siprios mask |
| [#6131](https://github.com/OpenXiangShan/XiangShan/pull/6131) | PR | closed | 2026-07-09T07:51:23Z | kunminghu-v2 |  | interrupt | module: backend, topic: functionality | fix(vstopi): fix the mapping of vsei index |
| [#6125](https://github.com/OpenXiangShan/XiangShan/pull/6125) | PR | closed | 2026-07-09T07:41:52Z | kunminghu-v2 |  | interrupt | module: backend, module: tool, topic: functionality, note: submodule bump | fix(aia): mstateen0.AIA shouldn't have effect on attempts in VS-mode to access sireg |
| [#6091](https://github.com/OpenXiangShan/XiangShan/pull/6091) | PR | closed | 2026-07-07T03:07:23Z | kunminghu-v2 |  | interrupt | module: backend, module: tool, module: other, module: top, note: submodule bump | feat(aia): support chiselAIA(APLIC/IMSIC) for XSTop |
| [#6001](https://github.com/OpenXiangShan/XiangShan/issues/6001) | Issue | closed | 2026-06-25T09:08:20Z | kunminghu-v2 |  | interrupt | module: backend, type: bug/fixed | [BUG] SEI injected from M‑mode is encoded as priority 0 instead of 256 |
| [#6113](https://github.com/OpenXiangShan/XiangShan/issues/6113) | Issue | open | 2026-06-24T05:00:34Z | kunminghu-v3 | d8aebf95f8ef | exception, interrupt | type: bug/reported, module: unknown | [Bug] VS-mode wrongly raises EX_II instead of EX_VI when mstateen0.AIA=0 and vsiselect ∈ 0x30-0x3F |
| [#6086](https://github.com/OpenXiangShan/XiangShan/pull/6086) | PR | closed | 2026-06-18T06:39:01Z | kunminghu-v2 |  | interrupt | module: backend, topic: functionality | fix(vstopi): fix iid when Candidate3 and Candidate5 enable |
| [#6067](https://github.com/OpenXiangShan/XiangShan/pull/6067) | PR | closed | 2026-06-17T08:49:51Z | kunminghu-v2 |  | exception, interrupt | module: backend, topic: functionality | fix(CSR, vscause): gate VS hvictl interrupt cause by interrupt type |
| [#6031](https://github.com/OpenXiangShan/XiangShan/pull/6031) | PR | closed | 2026-06-17T08:41:21Z | kunminghu-v2 |  | interrupt | module: backend | fix(vstopi): fix vstopi Candidate3 enable conditation |
| [#6010](https://github.com/OpenXiangShan/XiangShan/pull/6010) | PR | closed | 2026-06-10T03:05:00Z | kunminghu-v2 |  | interrupt | module: backend, topic: functionality | fix(Intr): fix priority number of SEI when SEI is injected from M-level |
| [#6032](https://github.com/OpenXiangShan/XiangShan/issues/6032) | Issue | closed | 2026-06-05T13:07:08Z | kunminghu-v3 |  | interrupt | type: bug/invalid | [Bug] WFI does not resume execution when some interrupts become pending |
| [#5926](https://github.com/OpenXiangShan/XiangShan/pull/5926) | PR | closed | 2026-05-20T07:28:42Z | kunminghu-v2 |  | interrupt | module: backend, topic: functionality | fix(Interrupt): `stepie` should control hvictl inject interrupt |
| [#5864](https://github.com/OpenXiangShan/XiangShan/pull/5864) | PR | closed | 2026-04-27T09:26:22Z | kunminghu-v2 |  |  | module: other, note: submodule bump | bump(ChiselAIA): Fix backpress issue from customer |

## Biweekly Bug Cause Notes

- Count: `5`
- Source: official XiangShan English biweekly `Recent Developments` / `Bug fixes` entries.

| Biweekly | Date | Section | Issue/PR | Bug cause / description | Source |
| --- | --- | --- | --- | --- | --- |
| 105 | 2026-06-23 | Backend | [#6010](https://github.com/OpenXiangShan/XiangShan/pull/6010) | Fixed the SEI priority number when an SEI is injected from M-level, setting it to S-level priority 256 according to the AIA spec (#6010) | [#105](https://docs.xiangshan.cc/zh-cn/latest/blog/2026/06/23/biweekly-105-en/) |
| 105 | 2026-06-23 | Backend | [#6030](https://github.com/OpenXiangShan/XiangShan/pull/6030), [#6031](https://github.com/OpenXiangShan/XiangShan/pull/6031), [#6086](https://github.com/OpenXiangShan/XiangShan/pull/6086) | Fixed the vstopi Candidate3 enable condition and the iid selection when Candidate3 and Candidate5 are both enabled (#6030, #6031, #6086) | [#105](https://docs.xiangshan.cc/zh-cn/latest/blog/2026/06/23/biweekly-105-en/) |
| 105 | 2026-06-23 | Backend | [#6067](https://github.com/OpenXiangShan/XiangShan/pull/6067) | Gated VS hvictl interrupt-cause generation by trap type and refined pending-interrupt handling for WFI resume (#6067) | [#105](https://docs.xiangshan.cc/zh-cn/latest/blog/2026/06/23/biweekly-105-en/) |
| 103 | 2026-05-25 | Backend | [#5926](https://github.com/OpenXiangShan/XiangShan/pull/5926) | (V2) Stepie should control hvictl interrupt injection (#5926) | [#103](https://docs.xiangshan.cc/zh-cn/latest/blog/2026/05/25/biweekly-103-en/) |
| 76 | 2025-05-12 | Backend | [#4649](https://github.com/OpenXiangShan/XiangShan/pull/4649) | Fixed incorrect access control of sireg and vsireg by xstateen (#4649). | [#76](https://docs.xiangshan.cc/zh-cn/latest/blog/2025/05/12/biweekly-76-en/) |
