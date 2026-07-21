MILL ?= mill
RTL_DIR ?= mmu-smmpt-rtl

.PHONY: all rtl sync-rtl clean

all: rtl

rtl:
	$(MILL) smmpt.rtl

sync-rtl:
	mkdir -p $(RTL_DIR)
	cp build/smmpt/smmpt.v $(RTL_DIR)/smmpt.v
	cp build/smmpt/smmpt.fir $(RTL_DIR)/smmpt.fir

clean:
	rm -rf $(RTL_DIR) build/smmpt
