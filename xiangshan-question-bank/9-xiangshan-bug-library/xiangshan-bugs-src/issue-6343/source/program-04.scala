when (state === s_invalid) {
  when (io.in.fire) {
    uop := io.in.bits.uop
    rs1 := io.in.bits.src_rs1
    state := s_tlb_and_flush_sbuffer_req
    have_sent_first_tlb_req := false.B
  }
}
