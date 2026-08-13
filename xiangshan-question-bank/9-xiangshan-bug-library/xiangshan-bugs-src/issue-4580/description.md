The prior design reassigns `io.lsq.ldin.bits.rep_info.need_rep` to 0 when source comes from MisalignBuffer, preventing cancellation of rar/raw enqueue requests during misaligned instruction reissuance. 

Thus, we must use `io.misalign_ldout.bits.rep_info.need_rep` to determine whether to revoke rar/raw enqueue requests when source is from MisalignBuffer.
