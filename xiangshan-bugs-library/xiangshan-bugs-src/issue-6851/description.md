### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [ ] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

Reproduced on kunminghu-v2 at e7bab53e66 (KunminghuV2Config). The commits on kunminghu-v2 since then (up to 7b16188f7) don't touch the files below. I haven't tried kunminghu-v3, whose vector unit has been rewritten.

### Branch

kunminghu-v2

### Describe the bug

A load or store address trigger (mcontrol6, action = 0) that matches an element after the first of a vector load or store writes the wrong address to mtval. The trap is taken, and mcause = 3 and vstart (the matching element's index) are right. But mtval is the address of the first active byte of the 16-byte flow that contains the element, not the address of the element that matched.

For example, `vle8.v` with vl = 9 and tdata2 = base + 8 gives mtval = base, vstart = 8. Stores (`vse8.v`) behave the same way. Scalar loads, and a trigger on element 0, are correct.

The program arms trigger 0 on `tdata2 = buf + OFF` and runs one access. The breakpoint handler records mcause, mtval and vstart. Every case traps with mcause = 3:

| Case | Access | tdata2 | Required mtval, vstart | Observed mtval, vstart |
|---|---|---|---|---|
| A | `vle8.v`, vl = 9 | buf+0x0 | buf+0x0, 0 | buf+0x0, 0 |
| B | `vle8.v`, vl = 9 | buf+0x8 | buf+0x8, 8 | **buf+0x0**, 8 |
| C | `vle8.v`, vl = 32 | buf+0x18 | buf+0x18, 24 | **buf+0x10**, 24 |
| D | `vle8.v v0.t`, vl = 9, elements 0-3 inactive | buf+0x8 | buf+0x8, 8 | **buf+0x4**, 8 |
| E | `vle32.v`, vl = 4 | buf+0x8 | buf+0x8, 2 | **buf+0x0**, 2 |
| F | `vse8.v`, vl = 9 | buf+0x8 | buf+0x8, 8 | **buf+0x0**, 8 |
| G | `lb` (scalar) | buf+0x8 | buf+0x8, 0 | buf+0x8, 0 |

C shows the address is the start of the second flow (buf+0x10), and D shows it is the first *active* byte (buf+0x4).

The trigger is tdata1 = 0x6000000000000041 (type 6, m = 1, load = 1, match = 0, size = 0, select = 0, chain = 0, action = 0) for loads, or 0x6000000000000042 (store = 1) for F. The program runs in M-mode with mstatus.MIE = 1.

### Expected behavior

mtval = tdata2, the address of the element whose access fired the trigger. The Sail model (0.16) gives that in every case.

- Sdtrig, Combined Accesses: "The Trigger Module should match such accesses as if they all happened individually. E.g. a vector load should be treated as if it performed multiple loads of size SEW."
- mcontrol6 (Sdtrig, `mcontrol6` action description): "The faulting virtual address for an mcontrol6 trigger with action=0 is the address being accessed and which caused that trigger to fire."
- Privileged spec, mtval: "If mtval is written with a nonzero value when a breakpoint, address-misaligned, access-fault, page-fault, or hardware-error exception occurs on an instruction fetch, load, or store, then mtval will contain the faulting virtual address."

XiangShan's own vstart = 8 in case B says the trap was taken on element 8 (vector spec: "the vstart CSR contains the element index on which the trap was taken"), so the element at buf+8 is the access that fired.

### Environment

- Hardware
  - CPU: 32 cores, x86_64
  - Memory (GB): 185
- Software
  - Operating system: AlmaLinux 9.7
  - gcc version: riscv64-unknown-elf-gcc 15.2.0 (for the test)
- Repo
  - XiangShan commit id: `e7bab53e66dfb3c4a1d11cf9519b0396f8576cae` (kunminghu-v2)
  - difftest commit id: `3729300ae233816d332d057f170472ebe35147b0`
- Build & Run
  - Build command: `make emu CONFIG=KunminghuV2Config` (Verilator 5.052; the default `--issue E.b`)
  - Run command: `./emu -i vtrig.elf --no-diff -C 2000000`

### To Reproduce

Source, prebuilt ELFs, a run script, the Sail config and the observed output are in https://github.com/davidharrishmc/xiangshan-repros/tree/main/xiangshan-vector-trigger-tval (tarball: https://github.com/davidharrishmc/xiangshan-repros/raw/main/xiangshan-vector-trigger-tval.tar.gz).

1. `make` (needs riscv64-unknown-elf-gcc), or use the prebuilt `vtrig.elf`.
2. `XIANGSHAN_EMU=/path/to/emu ./run.sh`

The program prints one line per case and ends with a bad trap if any case is wrong. On e7bab53e66 it prints the table above.

### Additional context

The trigger unit computes the right element. `LoadUnit.scala:1150-1154` sets `vecVaddrOffset = triggerVaddr - vecBaseVaddr` when a trigger fires, and `:1431` derives vstart from it, which is why vstart is right. `StoreUnit.scala:405-407` does the same for stores.

The exception address takes another path:
- `LoadUnit.scala:1864` sends `vecldout.vaddr := s3_in.fullva`, the flow's address.
- `VMergeBuffer.scala:285-287` and `:299` (`BaseVMergeBuffer`, shared by `VLMergeBufferImp` and `VSMergeBufferImp`) set `entry.vaddr := selVaddr + genVFirstUnmask(mask)`.
- `VMergeBuffer.scala:123` passes it on as the exception address.

When the exception is a trigger breakpoint, using `vecBaseVaddr + vecVaddrOffset` (equivalently, the firing trigger's tdata2) for vaddr would give the required value.

Found with the [riscv-arch-test](https://github.com/riscv/riscv-arch-test) SdtrigSm/S/U `vector_mask` and `vector_accesses` tests.
