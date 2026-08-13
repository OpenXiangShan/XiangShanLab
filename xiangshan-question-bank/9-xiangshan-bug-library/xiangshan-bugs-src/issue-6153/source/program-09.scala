// src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
val killDCache = kill || tlbMiss || tlbException

val triggerAction = loadTrigger.io.toLoadStore.triggerAction
val bp = TriggerAction.isExp(triggerAction)

val exception = tlbException || bp

io.dcacheKill := killDCache
