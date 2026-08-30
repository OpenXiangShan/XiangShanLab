Store MMIO will also mark the rob bit to prevent critical errors caused by commitStuckCycle timeouts.

The commitStuckCycle logic, shown below, excludes MMIO instructions. However, we previously only marked load instructions as MMIO:
https://github.com/OpenXiangShan/XiangShan/blob/bff1f48837e906a2d060c267ef4725a36bb9dec8/src/main/scala/xiangshan/backend/rob/Rob.scala#L1646-L1659
