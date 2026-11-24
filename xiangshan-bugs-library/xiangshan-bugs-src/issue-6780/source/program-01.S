li      t0, (3 << 9)
csrs    mstatus, t0
vsetivli zero, 0, e8, m1, ta, ma
la      a0, test_data
faulting_instruction:
  vlseg2e8.v v31, (a0)
