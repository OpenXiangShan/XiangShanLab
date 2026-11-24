// PseudoDecodeChannel.scala:333
def makeCSRRBitPat(csrno: Int): BitPat = {
  new BitPat(csrno, 0xFFF, 12) ## BitPat("b00000_010_?????_1110011")
}
