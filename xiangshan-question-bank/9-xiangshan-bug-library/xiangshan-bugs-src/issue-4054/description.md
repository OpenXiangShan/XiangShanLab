This pr fix write behavior of sstatus.sdt and sdt/sie interaction logic:
  - for the write alias of sstatus.sdt, write sdt 0 should also be blocked by DTE close
  - For the write behavior of sdt, it can be divided into two part
    - write `mstatus/vsstatus.sdt`, since it is directly written, the `sdt` field in `new_val` can be used to determine whether to write `sdt` and affect `sie`. 
    - For the write behavior of `sstatus.sdt`, due to changes in the write mask when `DTE close` is enabled, the original value of `sstatus.sdt` must be considered for its impact on `sie` when `new_val.sdt` cannot be written.
