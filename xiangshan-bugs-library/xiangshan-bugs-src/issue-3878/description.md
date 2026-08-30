I have tested it in **the newest version**, it works! No more `mstatus` different for now.
However, this test case raise `mcause` different:
```
sh      s2, 1259(s0)   # test case
-------------------
privilegeMode: 3
 mcause different at pc = 0x0080000030, right= 0x0000000000000007, wrong = 0x0000000000000006
```
I ran this test case, the `mcause` value is 6 in the official spike, which is the same as XiangShan. But in spike-diff, it is 7, nemu-diff reported nothing.

Thanks.

_Originally posted by @ha0lyu in https://github.com/OpenXiangShan/XiangShan/issues/3860#issuecomment-2476616185_

**NEMU HIT GOOD TRAP**:[mcause-nemu.log](https://github.com/user-attachments/files/17783458/mcause-nemu.log)
**spike-so raise different**:[mcause-spike.log](https://github.com/user-attachments/files/17783459/mcause-spike.log)


**To Reproduce**
```
sh      s2, 1259(s0
```

**Environment**
  XiangShan branch: master
  XiangShan commit id: [011f1eff](https://github.com/OpenXiangShan/XiangShan/commit/011f1effdbdc7c6fa84e1de650ccc95e54e360b6)
  NEMU commit id: b9507fbc
  SPIKE commit id:
