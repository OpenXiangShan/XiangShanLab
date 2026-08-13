#### Before start
PLEASE MAKE SURE you have done these: 
- [x] (Select what you have done like this)
- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question.
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest).
- [x] I have searched the previous issues and did not find anything relevant.
- [x] I have reviewed the commit messages from the relevant commit history.

#### Describe the bug

Difftest failed with workload: [https://github.com/cyyself/simple-sw-workbench/tree/xs-misaligned-bug-poc](https://github.com/cyyself/simple-sw-workbench/tree/xs-misaligned-bug-poc)

#### Screenshots

```console
➜  XiangShan git:(master) ✗ build/emu  -i../simple-sw-workbench/start.bin  2>/dev/null
emu compiled at May 27 2024, 16:58:55
Using simulated 32768B flash
Using simulated 8192MB RAM
The image is ../simple-sw-workbench/start.bin
The reference model is /mnt/data/xs/xs-env/NEMU/build/riscv64-nemu-interpreter-so
The first instruction of core 0 has commited. Difftest enabled. 
...
privilegeMode: 3
Mismatch for store commits 
  REF commits addr 0x90001150, data 0x0, mask 0xf
  DUT commits addr 0x90001050, data 0x0, mask 0xf
Core 0: ABORT at pc = 0x8002005c
Core-0 instrCnt = 433, cycleCnt = 9,275, IPC = 0.046685
Seed=0 Guest cycle spent: 9,278 (this will be different from cycleCnt if emu loads a snapshot)
Host time spent: 2,236ms
```

#### Expected behavior

Should hit good trap.

#### To Reproduce

```bash
cd XiangShan
git checkout f6458cc14f9e1c7ac8082fde150b988dd2defe04
make emu MFC=1 EMU_THREADS=16 NUM_CORES=1 -j 32
git clone https://github.com/cyyself/simple-sw-workbench.git -b xs-misaligned-bug-poc
pushd simple-sw-workbench
make CROSS_COMPILE=riscv64-unknown-linux-gnu-
popd
build/emu  -i./simple-sw-workbench/start.bin  2>/dev/null
```

#### Environment:

 - XiangShan branch:  master
 - XiangShan commit id: f6458cc14f9e1c7ac8082fde150b988dd2defe04
 - NEMU commit id: b966d2744ef6d2be3604920b5817ad8ab56003e9

#### Additional context


objdump of the workload:

```console
0000000080020040 <test_memcpy>:
    80020040:	86aa                	mv	a3,a0
    80020042:	8209                	srli	a2,a2,0x2
    80020044:	0d0672d7          	vsetvli	t0,a2,e32,m1,ta,ma

0000000080020048 <loop>:
    80020048:	0205e007          	vle32.v	v0,(a1)
    8002004c:	20b2c5b3          	sh2add	a1,t0,a1
    80020050:	40560633          	sub	a2,a2,t0
    80020054:	0206e027          	vse32.v	v0,(a3)
    80020058:	20d2c6b3          	sh2add	a3,t0,a3
    8002005c:	f675                	bnez	a2,80020048 <loop>
    8002005e:	8082                	ret
```

And also, modifying the workload in this way will hit good trap:

```diff
diff --git a/main.c b/main.c
index a101b50..7dc1f8a 100644
--- a/main.c
+++ b/main.c
@@ -50,7 +50,7 @@ int main(long hartid) {
     char *s = 0x90000000u;
     char *t = 0x90001000u;
     for (int i=0;i<100;i++) {
-        test_memcpy(t + 4, s + 4, 512);
+        test_memcpy(t + 0, s + 0, 512);
         // print_long(i);
         // print_s("\r\n");
     }
```

I also modified the workload in this way to get the `s` array always hit in d-cache, then it will hit good trap:

```diff
diff --git a/main.c b/main.c
index a101b50..545712d 100644
--- a/main.c
+++ b/main.c
@@ -49,6 +49,7 @@ int main(long hartid) {
      */
     char *s = 0x90000000u;
     char *t = 0x90001000u;
+    for (int i=0;i<512;i++) s[i] = 0;
     for (int i=0;i<100;i++) {
         test_memcpy(t + 4, s + 4, 512);
         // print_long(I);
```
