// src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:654-663
val ColdDownCycles = 16
val coldCounter = RegInit(VecInit(List.fill(LoadPipelineWidth)(0.U(log2Up(ColdDownCycles).W))))
val ColdDownThreshold = Wire(UInt(log2Up(ColdDownCycles).W))
ColdDownThreshold := Constantin.createRecord(s"ColdDownThreshold_${p(XSCoreParamsKey).HartId}", initValue = 12)
assert(ColdDownCycles.U > ColdDownThreshold, "ColdDownCycles must great than ColdDownThreshold!")

def replayCanFire(i: Int) = coldCounter(i) >= 0.U && coldCounter(i) < ColdDownThreshold
def coldDownNow(i: Int)   = coldCounter(i) >= ColdDownThreshold
