Previously, resolve updates wrote perfQueue directly per backend resolve, so multiple resolves for the same FTQ entry in one cycle could overwrite each other, and mispredict flushing was based on the old queue state.

This PR:
- Accumulate same-cycle resolve updates to perfQueue
- Keep commit branch stats limited to correct-path CFIs before the first mispredict
- Add cfiAttr so commit_branch_type counters can use correct branch attributes
