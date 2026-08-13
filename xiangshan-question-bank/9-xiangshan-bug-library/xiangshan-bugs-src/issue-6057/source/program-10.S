   fmv.x.d s2, fs7          # first illegal trap; handler skips it with mepc += 4
   rem s7, s2, t1
   subw s1, t1, t4
   csrrs a3, frm, zero      # PC 0x8000101c, faulting older CSR illegal instruction
   rolw t2, s1, t2
   fcvt.l.s a1, fs9, dyn    # PC 0x80001024, younger FP instruction incorrectly used as mtval
   csrrs s3, frm, zero
   beq s11, zero, . + 28
