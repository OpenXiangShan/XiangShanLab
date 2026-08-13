This pull request implements **Zacas** extension for atomic Compare-and-Swap (CAS) instructions. For RV64, AMOCAS.W / AMOCAS.D / AMOCAS.Q atomically loads 32 / 64 / 128-bits of  a data value from **rs1**, compares the loaded value to register **rd** (for AMOCAS.Q, a register pair consisting of **rd** and **rd+1**), and if the comparison is bitwise equal, then stores the value held in **rs2** (for AMOCAS.Q, a register pair consisting of **rs2** and **rs2+1**) to the original address in **rs1**.

This pull request re-uses the existing AtomicsUnit design. However, an AMOCAS instruction may consume more than 1 std and even more than 1 sta in the new design. As far as stds,
* for AMOs (except AMOCAS) and LR/SC, 1 std uop is wanted: X(rs2) with uopIdx = 0
* for AMOCAS.W / AMOCAS.D, 2 std uops are wanted: X(rd), X(rs2) with uopIdx = 0, 1
* for AMOCAS.Q, 4 std uops are wanted: X(rd), X(rs2), X(rd+1), X(rs2+1) with uopIdx = 0, 1, 2, 3

As for stas, AMOCAS.Q has extra require for the number of sta uops, which is also the number of sta uops' write-back,
* for AMOs (except AMOCAS.Q) and LR/SC, 1 sta uop is wanted: X(rs1) with uopIdx = 0
* for AMOCAS.Q, 2 sta uops are wanted: X(rs1)*2 with uopIdx = 0, 2

Besides, this pr removes the write-back of atomic stds.
