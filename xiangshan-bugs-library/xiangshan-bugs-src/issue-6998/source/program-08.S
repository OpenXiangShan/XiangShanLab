  vsetivli x0, 0, e8, m2, tu, mu   # legal
  vsetivli x0, 0, 0x23             # illegal vtypei (vsew = 4)
  csrr     a1, vtype
