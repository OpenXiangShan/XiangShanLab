mill -i -Djvm-xmx=40G -Djvm-xss=256m xiangshan.test.runMain top.XiangShanSim --target-dir build/chirrtl --config DefaultConfig --issue E.b --num-cores 1 --target chirrtl --enable-difftest --full-stacktrace
firtool build/chirrtl/SimTop.fir -o build/rtl --split-verilog -O=release --disable-annotation-unknown --lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none --default-layer-specialization=enable
# then the Makefile's RTL rule for this configuration (Makefile lines 341-342, 350 and 353); our build made the same three edits:
{ git log -n 1; git diff; } | sed 's/^/\/\// ' > ./build/rtl/.__diff__
cat ./build/rtl/.__diff__ ./build/rtl/SimTop.sv > ./build/rtl/.__out__ && mv ./build/rtl/.__out__ ./build/rtl/SimTop.sv
sed -i -e 's/$fatal/xs_assert_v2(`__FILE__, `__LINE__)/g' ./build/rtl/*.sv
sed -i -e "s/\$error(/\$fwrite(32\'h80000002, /g" ./build/rtl/*.sv
SIM_VFLAGS="--output-split 1000 --output-split-cfuncs 1000" \
make emu CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize" -j16
