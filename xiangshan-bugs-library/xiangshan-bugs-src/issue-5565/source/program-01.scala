val int_regcache_size = 48
val int_regcache_tag = RegInit(VecInit(Seq.fill(int_regcache_size)(0.U(intSchdParams.pregIdxWidth.W))))
val int_regcache_enqPtr = RegInit(0.U(log2Up(int_regcache_size).W))

int_regcache_enqPtr := int_regcache_enqPtr + PopCount(intRfWen)
for (i <- intRfWen.indices) {
  when (intRfWen(i)) {
    int_regcache_tag(int_regcache_enqPtr + PopCount(intRfWen.take(i))) := intRfWaddr(i)
  }
}
