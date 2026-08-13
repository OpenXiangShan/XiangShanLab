```
[779] [warn] <redacted>/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala:156:40: inferred existential type x$7.io.Trace forSome { val x$7: xiangshan.frontend.bpu.mbtb.MainBtbAlignBank }, which cannot be expressed by wildcards, should be enabled
[779] [warn] by making the implicit value scala.language.existentials visible.
[779] [warn] This can be achieved by adding the import clause 'import scala.language.existentials'
[779] [warn] or by setting the compiler option -language:existentials.
[779] [warn] See the Scaladoc for value scala.language.existentials for a discussion
[779] [warn] why the feature should be explicitly enabled.
[779] [warn]   private val finalTrace        = Mux1H(t1_writeAlignBankMask, alignBankTraceVec)
[779] [warn]                                        ^

```
