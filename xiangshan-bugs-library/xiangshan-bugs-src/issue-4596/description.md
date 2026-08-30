In the original design, the condition for `to_last_hptw_req` was: `dup_wait_resp && entries(io.mem.resp.bits.id).req_info.s2xlate === allStage`. As a result, when a newly entered LLPTW request dups with an entry currently returning from memory, and the new request is marked as allStage, the `to_last_hptw_req` signal would be true. This causes the state machine to transition to the `state_last_hptw_req` state and send a request to HPTW.

However, if the page table returned from memory contains a `vsStagePf` or `gStagePf`, it should directly go to `mem_out` or `bitmap_check` without performing a final HPTW translation. Therefore, this commit fixes the bug by adding a restriction to the original `to_last_hptw_req` condition to ensure that no exceptions are present; otherwise, the state machine will transition to either `mem_out` or `bitmap_check`.

Additionally, this PR also fixes a bug where `last_hptw_req_ppn` did not account for the napot case.
