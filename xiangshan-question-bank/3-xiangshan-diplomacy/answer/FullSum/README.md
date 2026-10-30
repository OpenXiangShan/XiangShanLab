# FullSum

Question 5 implements an unsigned, combinational full-precision adder using
Diplomacy. `FullPrecisionAdderTop` accepts non-empty positive `sourceWidths`
and `sinkMaxWidths` sequences, defaulting to `Seq(8, 8, 8)` and `Seq(10, 16)`.
Each output has the minimum width required for the complete sum, not its sink's
maximum capacity. A sink that cannot accommodate the result fails elaboration.

```sh
./test.sh
./mill FullSum.runMain fullsum.FullPrecisionAdderMain
./mill FullSum.runMain fullsum.FullPrecisionAdderMain 1,8 9,16
```

The generator writes SystemVerilog to standard output and reports the input
widths, negotiated sum width and sink capacities on standard error. JDK 21 and
Verilator are needed for tests.

The required Diplomacy and CDE source snapshots are included under `third_party/`:
Diplomacy revision `fe5e131d4fc8adec14a3ce4a4935bb5c0a269871` and CDE revision
`52768c97a27b254c0cc0ac9401feb55b29e18c28`. Their licenses are included there.
