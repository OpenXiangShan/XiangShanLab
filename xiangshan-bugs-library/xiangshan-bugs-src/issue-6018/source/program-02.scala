def parseVaddr(x: UInt): (UInt, UInt) = {
  (x(x.getWidth - 1, tpTableSetBits),         // tag  = 50 - 10 = 40 bits
   x(tpTableSetBits - 1, 0))                  // set  = 10 bits
}
def parsePaddr(x: UInt): (UInt, UInt) = {
  (x(x.getWidth - 1, tpTableSetBits + blockOffBits),  // tag = 48 - 16 = 32 bits
   x(tpTableSetBits + blockOffBits - 1, blockOffBits))
}
