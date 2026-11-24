when(t1_hasWrite) {
  statusEntries(enqPtr.value).valid := true.B
  statusEntries(enqPtr.value).dirty := true.B
}

when(t1_hasWrite && t1_hasHit) {
  statusEntries(t1_cleanId).dirty := false.B
}
