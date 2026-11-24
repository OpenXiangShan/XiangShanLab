// src/main/scala/xiangshan/backend/rob/RobBundles.scala:210-212
def needFlush(redirect: Valid[Redirect]): Bool = {
  val flushItself = redirect.bits.flushItself() && this === redirect.bits.robIdx
  redirect.valid && (flushItself || isAfter(this, redirect.bits.robIdx))
