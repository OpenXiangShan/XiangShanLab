If `flush.valid && valid` holds, but the currently stored `trapInstInfo` has not been flushed out by this `flush`, and at the same time, an older and not flushed out `newCSRInstValid` has arrived in this cycle, missing this CSR trap inst.

A typical scenario is: a young decode illegal is temporarily stored first, and later an older CSR inst execution phase discovers `EX_II/EX_VI`. There is also a younger `redirect/flush` in the same cycle.
