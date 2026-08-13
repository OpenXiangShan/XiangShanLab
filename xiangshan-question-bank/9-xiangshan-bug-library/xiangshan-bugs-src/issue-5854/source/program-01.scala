// L2TLB.scala:738-757
for (i <- 0 until tlbcontiguous) {
  ptw_resp.ppn     := pte_in.getPPN()(ptePPNLen - 1, sectortlbwidth) // line 741: HIGH bits of PPN (PPN[PPNLen-1:3])
  ptw_resp.ppn_low := pte_in.getPPN()(sectortlbwidth - 1, 0)         // line 742: LOW 3 bits = PPN[2:0]
  ...
  ptw_resp.cf := cfs(ptw_resp.ppn(sectortlbwidth - 1, 0))            // line 757: BUG
}
