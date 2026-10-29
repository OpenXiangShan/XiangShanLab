# Chapter 13 Reproduction

This directory contains the standalone reproductions from Chapter 13 of the
Diplomacy course. It is intentionally separate from the repository root,
which is reserved for question 5: the full-precision adder.

Compile and run the tutorial tests, then print both examples' SystemVerilog,
from any working directory:

```sh
/path/to/FullSum/tutorial/test.sh
```

From the repository root, generate the first example's SystemVerilog with:

```sh
./mill Tutorial.runMain chapter_13.BasicAdderMain
```

Generate the five-input, three-output nexus example and its Diplomacy graph:

```sh
./mill Tutorial.runMain chapter_13.MultiAdderMain
```

The graph is written to `out/tutorial/multi-adder.graphml`.

Generate the width-negotiation example and its Diplomacy graph:

```sh
./mill Tutorial.runMain chapter_13.NegotiatedAdderMain
```

It uses input widths `8, 16, 32, 64, 128` and output widths `16, 32, 64`.
The negotiated edge width is the smaller width requested by its two ends.
