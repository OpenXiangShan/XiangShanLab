// L2TLB.scala:757
// Before
ptw_resp.cf := cfs(ptw_resp.ppn(sectortlbwidth - 1, 0))   // = cfs(PPN[5:3]) — semantically incorrect
// After
ptw_resp.cf := cfs(ptw_resp.ppn_low)                      // = cfs(PPN[2:0])
