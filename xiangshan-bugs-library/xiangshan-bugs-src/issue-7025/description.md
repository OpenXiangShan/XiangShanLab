### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

After a vector store takes an exception, its remaining SQ flows can coexist with the trap handler's first scalar store. Trap recovery reuses the vector instruction's ROB tag for that new scalar instruction. The SQ's second dequeue port inherits the preceding entry's exception solely because their ROB tags match, even when the second entry is a normal scalar FSD with no exception.
The FSD has the correct address, data and byte mask, but is sent toward Sbuffer with `vecValid=0`. Its SQ entry completes and is freed without writing the data. A subsequent FENCE retires, yet a load returns the old value.

The expected FSD data is `0x123456789abcdef0`; the target initially contains `0x8877665544332211`. The original reproducer observes the old value after FSD and FENCE. An additional diagnostic observes the following sequence in S-mode, before any subsequent ECALL:

```asm
# sp = 0x80040000; f0 = 0x123456789abcdef0; FS enabled
# Observation CSR writes and the warmup-control branch are omitted here.
fsd   f0, 0(sp)
ld    a1, 0(sp)       # Observed: 0x123456789abcdef0
fence rw, rw
ld    a2, 0(sp)       # Observed: 0x8877665544332211
```

There is no store or AMO between these loads, and no other hart or device writes the target. The failing scalar access is naturally aligned, uses ordinary RAM and does not take an exception.

### Expected behavior

The scalar FSD must retain its normal architectural effect after the vector exception has been handled. Both subsequent loads must return `0x123456789abcdef0`.

The architectural basis is the target-recorded ISA manual revision `6ee57d8997021aaa8025e208164763fe12e03c19`:

- [D extension 2.2, Double-Precision Load and Store Instructions](https://github.com/riscv/riscv-isa-manual/blob/6ee57d8997021aaa8025e208164763fe12e03c19/src/d-st-ext.adoc): FSD stores the double-precision register value to memory without modifying the transferred bits. This case uses RV64 and an 8-byte-aligned address.
- [RVWMO 2.0, Load Value Axiom and Preserved Program Order](https://github.com/riscv/riscv-isa-manual/blob/6ee57d8997021aaa8025e208164763fe12e03c19/src/rvwmo.adoc): the same-hart FSD is the relevant latest store to these bytes. With no intervening target write, returning the earlier sentinel is not a permitted weak-memory result.

This expectation does not require FENCE to write data directly to DRAM, nor does it assume that the preceding vector store executes atomically.

### Environment

- XiangShan kunminghu-v2 revision: `1fd6f257`.
- Configuration: KunminghuV2Config, one active hart, CHI simulation, Verilator seed 1.
- Entry: `0x80000000`; success-loop PC: `0x80002534`; observed failure-loop PC: `0x80002578` (`FSD-commit-policy`, check 16).
- Original run bound: `+XS_TIMEOUT_CYCLES=10000`, host timeout 240 seconds.


### To Reproduce

Use the provided `SQ6976D01` ELF with one active hart and the reset vector at its entry. Its setup and trigger are:

1. In M-mode, enable NMIE and FS/VS, use Bare translation, delegate store-access fault cause 7 to S, and leave S ECALL cause 9 routed to M.
2. Initialize the 8-byte scratch at `0x80040000` to the sentinel. Set `f0` to the expected data. Call and warm the FSD handler in M-mode, verify its result, then restore the sentinel and execute FENCE.
3. In S-mode, execute an e32/m8, VL32 strided vector store with a 64-byte stride from `0x802ffc00`. Element 16 reaches the read-only PMP page at `0x80300000`, causing the delegated store-access fault with `vstart=16`.
4. Keep `FSD f0,0(sp)` as the **first instruction** at the S trap entry, PC `0x80000240`. FS remains enabled. The non-warm path then executes S ECALL to enter M.
5. After FENCE, load the scratch and compare it with the expected data. The program enters the `FSD-commit-policy` failure loop because it reads the sentinel.

The recorded CHI harness uses these simulator arguments; this is not a command-line claim for the upstream `emu` executable:

```sh
"$SIM" +XS_CASE_ID=SQ6976D01 \
  "+XS_ELF=$ELF" +XS_RESET_VECTOR=80000000 +XS_CORES=1 \
  +XS_PERF_MODE +XS_PERF_DONE_PC=80002534 +XS_TIMEOUT_CYCLES=10000 \
  +UVM_VERBOSITY=UVM_LOW +verilator+seed+1
```

The failure loop, rather than the simulator's process exit code, identifies the functional failure. The harness eventually reports that the success PC was not reached.

[kunminghu-v2-SQ6976D01-reproducer-20261121.zip](https://github.com/user-attachments/files/35165674/kunminghu-v2-SQ6976D01-reproducer-20261121.zip)

### Additional context

#### Root cause and observed internal state

At the tested revision, [StoreQueue.scala:1203–1205](https://github.com/OpenXiangShan/XiangShan/blob/1fd6f257469ba99a00004595c92a3e5d482e9619/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1203) computes the second port's exception condition as follows, with pointer names simplified:

```scala
hasException(ptr1) ||
  (hasException(ptr0) && robIdx(ptr1) === robIdx(ptr0))
```

The inherited term does not check whether the second entry belongs to the same vector instruction. [The output gate at line 1250](https://github.com/OpenXiangShan/XiangShan/blob/1fd6f257469ba99a00004595c92a3e5d482e9619/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1250) includes `!exceptionValid` in `toSbufferVecValid`.

The internal capture shows:

| State at 4.931us | SQ33 / first port | SQ34 / second port |
| --- | --- | --- |
| Instruction | Faulting vector's last flow | Newly allocated scalar FSD |
| `isVec` | 1 | 0 |
| Own `hasException` | 1 | 0 |
| Full ROB `(flag,value)` | `(0,86)` | `(0,86)` |
| Address | `0x803003c0` | `0x80040000` |

SQ34 was newly allocated at 4.491us. Its STD supplies the correct FP64 data, its byte-valid mask is `0x00ff`, and it is committed by 4.503us. At 4.931us the read-data pointers directly select SQ33 and SQ34.

The inherited exception evaluates to `0 || (1 && true)`, incorrectly invalidating the scalar FSD. At 4.933us, the second buffered output has valid=1, the correct FSD address/data and mask `0x00ff`, but `vecValid=0`. No corresponding target-line write occurs in Sbuffer. Both SQ entries become completed at 4.935us and are freed at 4.937us. The separate direct `vecExceptionFlag && isVec` suppression term is false for this scalar entry.

These states were collected in one additional passive observation of the original ELF on the unchanged simulator. Its complete retirement capture and all 19,955 selected CSR frames are byte-identical to the original failure. All 4,001 internal samples were aligned against the same run's clock and MSCRATCH waveform before interpretation. Sbuffer ready was not directly sampled; no Dcache writeback observation is claimed.

A correction needs to distinguish entries that actually belong to the same vector instruction across trap recovery and SQ draining. ROB-tag equality alone is insufficient. A scalar/vector type guard would avoid this observed scalar case, but a generally sufficient repair and its regressions have not been validated.
