NAME = cbo-inval-scenario
SRCS = cbo-inval-scenario.c

# The riscv64-xs platform default predates the ratified CMO extension, while
# this program intentionally uses the readable cbo.inval mnemonic.
export LINUX_GNU_TOOLCHAIN = 1
MARCH = rv64gc_zba_zbb_zbc_zbs_zbkb_zbkc_zbkx_zknd_zkne_zknh_zkr_zksed_zksh_zkt_zicbom

# Makefile.compile appends CC_OPT after the application CFLAGS.  Pin it here
# so the four marker/access sequences retain their source ordering.
CC_OPT := -O0

# Keep the four scenario markers and their surrounding accesses easy to find in
# the disassembly and waveform.
CFLAGS += -g -fno-inline -fno-reorder-functions -fno-reorder-blocks \
          -fno-optimize-sibling-calls

include $(AM_HOME)/Makefile.app
