vsetivli x0, 16, e8, m1, ta, ma
vle8.v   v1, (a1)            # dividends, all 100
vle8.v   v2, (a2)            # divisors 5..33
ld       t0, flag            # flag = 0
beqz     t0, 1f              # taken, but predicted not-taken, and the shadow executes
vdivu.vv v4, v1, v2          # wrong path, squashed by the redirect
vdivu.vv v5, v2, v1
1:
vdivu.vv v3, v1, v2          # correct path: never writes back
vse8.v   v3, (a3)
