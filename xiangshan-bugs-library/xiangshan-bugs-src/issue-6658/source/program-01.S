vsetivli    x0, 16, e8, m1, ta, ma
vle8.v      v1, (a1)
vle8.v      v2, (a2)          # v2 = 01 02 .. 10
vle8.v      v3, (a0)          # v3 = 01 02 .. 10
vsetivli    x0, 16, e8, m1, ta, ma
vslideup.vi v3, v2, 4         # v3[4..15] = v2[0..11], v3[0..3] unchanged
vse8.v      v3, (a3)
