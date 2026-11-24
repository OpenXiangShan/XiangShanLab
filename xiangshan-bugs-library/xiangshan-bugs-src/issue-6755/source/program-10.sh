SIM_VFLAGS="--output-split 1000 --output-split-cfuncs 1000" \
make emu CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize" FIRTOOL=/path/to/firtool -j16
