  # mseccfg.MLPE = 1, mstatus.MPELP = 1, mstatus.MPP = 3, mstatus.MPIE = 1
  # mip.SSIP = 1, mie.SSIE = 1, mepc = tgt_insn
  mret

tgt_insn:
  sc.d     x27, x29, 0(x30)             # Expected:
                                        #   1st trap: Async interrupt (mcause = 0x8000000000000001)
                                        #   2nd trap: Software Check (mcause = 18, mtval = 2)
                                        # Actual on XiangShan:
                                        #   Only 1 trap (mcause = 18)! Pending interrupt is dropped!
  csrr     x10, mstatus                 # reads mstatus.MIE = 1 (0x8)
  csrr     x28, mip                     # reads mip.SSIP = 1 (0x2) — still pending!
