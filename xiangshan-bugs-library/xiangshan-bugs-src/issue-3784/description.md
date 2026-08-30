mainPipe.io.errors is not ensured to be at-most-one-hot, ECC errors may occur on both cachelines at the same time.

Note: DCache may have similar problems, as this code is actually copy-and-pasted from DCache 3 years ago.
https://github.com/OpenXiangShan/XiangShan/blob/0303f76a84fd705d32f6f0434c63c55e2ff02186/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L991-L997
