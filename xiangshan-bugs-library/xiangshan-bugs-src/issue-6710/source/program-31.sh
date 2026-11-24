mill -i -Djvm-xmx=40G -Djvm-xss=256m xiangshan.test.runMain top.XiangShanSim --target-dir build/chirrtl --config DefaultConfig --issue E.b --num-cores 1 --target chirrtl --enable-difftest --full-stacktrace
firtool build/chirrtl/SimTop.fir -o build/rtl --split-verilog -O=release --disable-annotation-unknown --lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none --default-layer-specialization=enable
# then, as the Makefile's RTL rule does for this configuration: a header from git on SimTop.sv,
# $fatal replaced by xs_assert_v2(`__FILE__, `__LINE__), and $error( by $fwrite(32'h80000002,
SIM_VFLAGS="--output-split 1000 --output-split-cfuncs 1000" \
make emu CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize" -j16
