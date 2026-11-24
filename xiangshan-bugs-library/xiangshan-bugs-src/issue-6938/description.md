## Before start

- Checked the default branch at submission time: `kunminghu-v3`, `182b2277c0e4115e39ae396f7d705bc4e20e0def`.
- Reproduced using freshly compiled main XiangShan sources and newly generated/built ORIG and FIX models. Unchanged pinned dependency artifacts were reused.
- Searched open/closed issues and PRs. **#3983 is directly related:** it fixes the same width adaptation requirement on other connections, but not the `error_xbar` connection reported here. Details below.
- AI assistance is disclosed below; no claim of independent human execution of these tests is made.

## Branch

kunminghu-v3

## Describe the bug

The Debug Module's system-bus master in `MemMisc` connects directly to the 8-byte `error_xbar`, without the `TLWidthWidget(1)` used on the standalone DM path:

```scala
debugModule.debug.dmInner.dmInner.sb2tlOpt.foreach { sb2tl =>
  error_xbar.get := sb2tl.node
}
```

[SoC.scala:492–494 at the tested commit](https://github.com/OpenXiangShan/XiangShan/blob/182b2277c0e4115e39ae396f7d705bc4e20e0def/src/main/scala/system/SoC.scala#L492-L494).

The pinned [SBToTL implementation](https://github.com/OpenXiangShan/rocket-chip/blob/a2df1a42399cfe2b343eeb5293796268dc2bc211/src/main/scala/devices/debug/SBA.scala#L304-L361) assembles/transmits accesses using byte-wise logic. Its negotiated edge is 8 bytes wide on this connection (measured from diplomacy). Adding `TLWidthWidget(1)` changes the SBA-side edge to one byte and resolves the failures below.

On the original design, 32- and 64-bit SBA reads and writes to the PLL control registers do not complete within the test timeout: `sbcs.sbbusy` remains set. With TileLink monitors enabled, writes also fail a `ready check` assertion. The DM advertises support for these access widths.

## Expected behavior

Supported 32/64-bit SBA accesses to these mapped registers complete, with correct data and no TileLink protocol assertion.

## Environment

- CPU: Intel Xeon Platinum 8163; server has approximately 629 GiB RAM, over 500 GiB available at the start. Builds limited to 8 jobs.
- Linux x86_64, kernel `5.15.0-164-generic`; GCC/G++ `11.4.0`; OpenJDK `21.0.12.1`.
- Chisel `7.13.0`, firtool `1.149.0`. Server Verilator installation reports `rev UNKNOWN.REV` (its version metadata is missing); original independent macOS runs used Verilator `5.052`.
- OpenOCD `0.11.0` for the JTAG follow-up.
- XiangShan: `182b2277c0e4115e39ae396f7d705bc4e20e0def`.
- rocket-chip: `a2df1a42399cfe2b343eeb5293796268dc2bc211`.
- DefaultConfig. Standalone harness disables MBIST/SRAM control and log/performance facilities. No NEMU/Spike comparison.

## To Reproduce

**Reproducer sources, runner, suggested patch, and separate DMI/OpenOCD logs:** https://gist.github.com/ttkxclr/607c16638dbcaa96bad295e7880ad1c9

### 1. Real MemMisc, direct DMI transport

`SbaRepro.scala` instantiates the real `system.MemMisc`, including the real Debug Module and SoC wiring. It exports DMI instead of JTAG, connects dummy external interrupt/MSI endpoints, and exposes the PLL register outputs for an independent data check. The CPU cores are not instantiated.

`tb.cpp` activates the DM and programs `sbcs`, `sbaddress0/1`, and `sbdata0/1`. It tests 32-bit and 64-bit accesses to the six 32-bit PLL RegFields at `0x3a000000`. The test resets between cases.

The tested runner path is mode B:

```sh
export XS_SRC=/path/to/XiangShan-at-182b2277c
export XS_CP='<freshly compiled XiangShan HEAD classes and dependency/resource classpath>'
export SCALAC_CP='<Scala 2.13.17 compiler classpath>'
export CHISEL_PLUGIN=/path/to/chisel-plugin_2.13.17-7.13.0.jar
export CHISEL_FIRTOOL_PATH=/path/to/firtool/bin
export WORK=/path/without/spaces/sba-repro-build
bash run.sh
```

The supplied runner generates ORIG and FIX RTL, then builds each with and without TileLink monitor layers. Its mode A (`XS_HOME=...`) was **not tested**. Legacy test programs return zero even for reported mismatches; inspect `RESULT bad` and the individual checks, not just the process exit code.

| Case | Original | With width widget |
|---|---|---|
| 32-bit write/read | Write remains busy; monitor assertion when enabled | Correct write and readback |
| 64-bit write/read | Write remains busy; monitor assertion when enabled | Correct write and readback |
| 32-bit read only | Remains busy | Pass |
| 64-bit read only | Remains busy | Pass |

Without monitors, ORIG reports four failed cases; FIX reports zero. FIX also checks the actual PLL register outputs and that unrelated bytes did not change. Full output is provided as a separate log file, not pasted into this issue.

### 2. Actual OpenOCD through the real JTAG DTM

A second harness retains the same real MemMisc but exports JTAG, including the real DTM. `jtag_bridge.cpp` exposes its TCK/TMS/TDI/TDO through a localhost remote_bitbang socket. `openocd_sba.cfg` uses OpenOCD `irscan`/`drscan` to issue DMI transactions; DMI response status is checked.

Each ORIG/FIX and 32/64-bit case starts from a fresh simulation process. For ORIG, the SBA busy bit remains set after the write. FIX completes and reads back `0x11223344` for 32-bit access and `0xaabbccdd11223344` for 64-bit access. The JTAG build generator and exact launch commands are included in the reproducer README.

**Scope:** this is actual OpenOCD JTAG transport driving SBA registers. It does not exercise OpenOCD's RISC-V target `mww/mdw`, GDB commands, CPU software, a complete XSTop, or a physical JTAG adapter. No conclusion about 8/16-bit accesses is drawn from the PLL target.

## Additional context

Suggested and tested change:

```diff
 debugModule.debug.dmInner.dmInner.sb2tlOpt.foreach { sb2tl =>
-  error_xbar.get := sb2tl.node
+  error_xbar.get := TLWidthWidget(1) := sb2tl.node
 }
```

### Relation to previous work

- #3983 added `TLWidthWidget(1)` to the non-CHI `l3_xbar` connection and the standalone Debug Module. Its SoC patch did not change this `error_xbar` connection. This report concerns that remaining connection, not a claim that the width-adaptation requirement is new.
- #3536 and #1426 are earlier SBA implementation changes. #6122 removed the old non-CHI path; this report does **not** attribute introduction of the missing widget to that removal.
- The standalone DM and rocket-chip debug periphery provide examples of the byte-side widget connection.

### AI assistance

The report and test harnesses were prepared with AI assistance (earlier Claude-assisted work; latest-source rebuild, OpenOCD/JTAG follow-up, and submission preparation using OpenCode / gpt-6-astra). The agent executed the builds and tests described above. Results are limited to the attached harnesses and logs; no independent human execution or silicon validation is claimed.
