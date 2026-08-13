pushd $NOOP_HOME
make emu PGO_WORKLOAD=`realpath ./ready-to-run/coremark-2-iteration.bin` NUM_CORES=2 EMU_THREADS=8 EMU_TRACE=fst -j `nproc` CONFIG=MinimalConfig
popd
git clone git@github.com:cyyself/simple-sw-workbench.git -b xs-spinlock
make CROSS_COMPILE=riscv64-unknwon-linux-gnu-
# May replace with riscv64-linux-gnu-
$NOOP_HOME/build/emu -i start.bin --no-diff 2>/dev/null
