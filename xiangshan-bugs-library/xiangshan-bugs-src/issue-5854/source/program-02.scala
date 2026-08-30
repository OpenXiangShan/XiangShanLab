BitmapCheck.scala:209-213
val ppnPart = req_real_ppn(log2Up(XLEN)-1, log2Up(8))                      // = PPN[5:3]
val selectedBits = bitmapdata(index).asTypeOf(Vec(8, UInt(8.W)))(ppnPart)  // byte selection using PPN[5:3]
for (j <- 0 until tlbcontiguous) {
  entries(enq_ptr).cfs(j) := selectedBits(j)                               // cfs(j) ≡ bitmap bit for the page with PPN[2:0]=j
}
