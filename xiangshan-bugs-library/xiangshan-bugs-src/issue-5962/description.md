Pbmt.pma does not have an explicit width, chisel inferred width is 1, so if we do `RegEnable(..., init = Pbmt.pma, ...)` (same for RegNext/DataHoldBypass), we'll get a 1-bit register.

Bug introduced in #4909, does not affect v2

Tested with https://github.com/OpenXiangShan/nexus-am/pull/68
