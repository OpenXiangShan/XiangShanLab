This PR aims to solve this issue:
https://github.com/OpenXiangShan/XiangShan/issues/6153

Summary:
- Gate DCache hit-side replace_access, access_flag, and prefetch metadata updates on s3_kill.
- Prevents exception-killed loads (e.g. load breakpoint trigger) from updating replacement state when S1 DCache lookup is not suppressed.

Problem to solve:
A load that matches a breakpoint trigger is killed in S2, but S1 does not assert dcacheKill, so the DCache lookup still proceeds. On a cache hit, S3 could still update PLRU/access metadata even though the load is architecturally squashed.
This can leak a secret through microarchitectural cache state:
- A secret-dependent, trigger-matched load updates replacement metadata before trap handling.
- After exception recovery, ordinary probe loads observe different hit/miss patterns for same-set lines.
- An attacker can recover which protected address was selected without reading the data directly.
