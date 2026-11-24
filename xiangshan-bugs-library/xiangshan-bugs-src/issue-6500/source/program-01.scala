// 2e148b4 L421 / L576 / L1146-1151; v2 HEAD 5d393413 L420 / L575 / L1162-1167; v3 HEAD L460 / L608 / L1182-1187
when(RegNext(RegNext(io.csr.wfiEvent)) || ...) { hasWFI := false.B }   // wake-up: only unblocks commit, no trap
val intrEnable = intrBitSetReg && !hasWaitForward && deqPtrEntry.interrupt_safe && !deqHasFlushed
val allow_interrupts = !CommitType.isLoadStore(..) && !FuType.isFence(..) && !FuType.isCsr(..) &&
                       !isVset && !FuType.isAMO(..)
robEntries(..).interrupt_safe := allow_interrupts
