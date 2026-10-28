CROSS    ?= riscv64-unknown-elf-
CC       := $(CROSS)gcc
OBJCOPY  := $(CROSS)objcopy
OBJDUMP  := $(CROSS)objdump

TARGET   := csr_mtvec_write
SRCS     := csr_mtvec_write.c
CFLAGS   := -march=rv64imac_zicsr -mabi=lp64 \
            -nostdlib -nostartfiles -ffreestanding -O0 \
            -T link.ld

all: $(TARGET).bin $(TARGET).txt

$(TARGET).elf: $(SRCS) link.ld
	$(CC) $(CFLAGS) -o $@ $(SRCS)

$(TARGET).bin: $(TARGET).elf
	$(OBJCOPY) -O binary $< $@

$(TARGET).txt: $(TARGET).elf
	$(OBJDUMP) -d $< > $@

clean:
	rm -f $(TARGET).elf $(TARGET).bin $(TARGET).txt

.PHONY: all clean