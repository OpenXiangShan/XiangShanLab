When use `-xprop=xmerge` policy, the un-initialized `stage1Hit` in Mux() will cause the X-state propagate to output port `io.resp.valid`. Fix it by moving `idle` check out of Mux(), which means the `io.resp.valid` only valid when `!idle`.

The generated RTL will changed from:

```
  wire             io_resp_valid_0 =
    stage1Hit
      ? ~idle & hptw_resp_stage2
      : ~idle & mem_addr_update & ~need_last_s2xlate
        & (guestFault | w_mem_resp & find_pte | s_pmp_check & accessFault | onlyS2xlate);
```

to:

```
  wire             io_resp_valid_0 =
    ~idle
    & (stage1Hit
         ? hptw_resp_stage2
         : mem_addr_update & ~need_last_s2xlate
           & (guestFault | w_mem_resp & find_pte | s_pmp_check & accessFault
              | onlyS2xlate));
```
