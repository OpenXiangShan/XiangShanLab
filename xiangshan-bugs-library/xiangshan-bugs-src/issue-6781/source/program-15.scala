val iEmul = emul
val dEmul = lmul
val uopNum = (emul max lmul max 1.0).toInt * seg
if (emul >= 0.125 && uopNum <= 8) {
  // generate indexed-memory uops
} else {
  Seq()
}
