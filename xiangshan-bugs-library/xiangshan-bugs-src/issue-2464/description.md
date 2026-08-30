**Describe the bug**
```
slli x18, x29, 1
add x18, x18, x18
```

These instructions will be fused into `sh1add x18, x29, x18` in the current implementation, which is wrong.

**To Reproduce**

**Expected behavior**
Should not be fused because the actual semantic is `x18 = (x29 << 1) + (x29 <<1)`. Thus, the implementation should avoid fusing these instructions (`lsrc1 === lsrc2`) for fusion pairs with `lsrc2NeedMux = true`.

**Screenshots**
![d47c9956cd0b3a02af8f4783e3f3130](https://github.com/OpenXiangShan/XiangShan/assets/14199583/baebc73e-c930-4e7a-9094-d7b7aa7a8b6c)


**Environment (optional, if necessary):**

**Additional context**
Credit to @Siudya
