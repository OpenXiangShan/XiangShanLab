Re-split uops to avoid stalls caused by renaming when rd and rs2 are the same.

When rd and rs2 are the same, uop1'src will wait uop0'dest after rename, which cause stalls.

In this commit, we set src2 in uop0 and dest in uop1, avoiding stalls by renaming.
