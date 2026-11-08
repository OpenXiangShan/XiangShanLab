package chi

/** Internal CHI subset: 64-byte lines, one whole line per DAT flit. */
case class ChiParams(nodeIdWidth: Int = 2, txnIdWidth: Int = 4, addrWidth: Int = 48) {
  require(nodeIdWidth > 0, "node IDs must have a positive width")
  require(txnIdWidth > 0, "transaction and DB IDs must have a positive width")
  require(addrWidth >= 6, "addresses must include the six-bit line offset")
}
