s2_out.uop.exceptionVec(storeAddrMisaligned) := s2_actually_uncache && (s2_in.isMisalign || s2_in.isFrmMisAlignBuf) && !s2_un_misalign_exception
