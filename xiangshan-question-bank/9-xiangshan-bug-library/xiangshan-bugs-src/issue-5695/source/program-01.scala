// Line 1312-1314
// We will generate misaligned exceptions at mmio.
val s2_real_exceptionVec = WireInit(s2_exception_vec)
s2_real_exceptionVec(loadAddrMisaligned) := (s2_out.isMisalign || s2_out.isFrmMisAlignBuf) && s2_uncache
