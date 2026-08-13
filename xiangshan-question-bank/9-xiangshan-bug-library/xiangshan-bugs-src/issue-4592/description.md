The cbo instruction should check for violations at the granularity of cacheline.

Theoretically modifying the condition of this variable would allow checking at cacheline granularity in RAW and should not introduce any other side effects.

See:
https://github.com/OpenXiangShan/XiangShan/blob/57a8ca5e38b9245f78623b83e7b009df606585fb/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala#L293
