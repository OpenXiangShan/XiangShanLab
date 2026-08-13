Now it's marked on last instruction in current page, so ICache/Ifu will not fetch next page.

Before this change, CI can pass as Ifu has fixed instr range when fallthrough (!taken):
https://github.com/OpenXiangShan/XiangShan/blob/1bb82e92fdac39884038752f1a1dc81b4d3fa246/src/main/scala/xiangshan/frontend/ifu/Ifu.scala#L265-L278

After this change, maybe we can remove `s1_ftrRange` and simply relies on `s1_takenCfiOffset` to decide range. CC @my-mayfly
