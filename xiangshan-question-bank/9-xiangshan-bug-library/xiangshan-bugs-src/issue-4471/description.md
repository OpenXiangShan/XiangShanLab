Similar to https://github.com/OpenXiangShan/XiangShan/pull/4455, ppn_s1 has a width of 44 bits, ppn_s2 has a width of 38 bits, and both of them need to finally assign to a 36-bits signal `ppn(d)`.

The original code does not result in a functional error, but the designer should be aware of the bit-widths of these signals and whether they can be directly truncated during assignment. Therefore this PR was committed.
