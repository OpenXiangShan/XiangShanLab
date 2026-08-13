Skip `m_waitLastCmt` & `m_waitCommit` state if `f3_itlb_pbmt === Pbmt.nc`, as these memory spaces are idempotent and in which we can do speculative inst fetch.
