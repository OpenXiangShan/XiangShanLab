CROSS_COMPILE ?= riscv64-linux-gnu-
BUILD_DIR := build
ELF := $(BUILD_DIR)/main.elf
BIN := $(BUILD_DIR)/main.bin
DISASM := $(BUILD_DIR)/main.disasm

EMU ?=
CYCLES ?= 100000
WAVE ?= $(abspath $(BUILD_DIR)/stage2.vcd)
EMU_FLAGS ?= --no-diff

.PHONY: all run
all: $(BIN) $(DISASM)

$(ELF): main.S link.ld Makefile
	mkdir -p $(BUILD_DIR)
	$(CROSS_COMPILE)gcc -march=rv64im -mabi=lp64 -nostdlib -nostartfiles -static \
		-fno-pie -no-pie \
		-Wl,-T,link.ld -Wl,--build-id=none -Wl,--no-relax $< -o $@

$(BIN): $(ELF)
	$(CROSS_COMPILE)objcopy -O binary $< $@

$(DISASM): $(ELF)
	$(CROSS_COMPILE)objdump -d $< > $@

run: all
	@test -n "$(EMU)" || { echo 'Set EMU=/absolute/path/to/your/emu'; exit 1; }
	"$(EMU)" -i "$(abspath $(BIN))" -s 1 -C $(CYCLES) -b 0 -e $(CYCLES) \
		--dump-wave-full --wave-path "$(WAVE)" $(EMU_FLAGS)
