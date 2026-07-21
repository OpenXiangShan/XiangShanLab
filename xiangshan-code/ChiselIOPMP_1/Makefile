.PHONY: help init verilog clean bsp idea reformat checkformat

BUILD_DIR = ./build
RTL_DIR = $(BUILD_DIR)/rtl

TOP = ChiselIOPMP
TOP_V = $(RTL_DIR)/$(TOP).sv

SCALA_FILE = $(shell find ./src/main/scala -name '*.scala')

TIMELOG = $(BUILD_DIR)/time.log
TIME_CMD = time -avp -o $(TIMELOG)

help: ## Display this help message
	@echo "ChiselIOPMP Makefile Commands:"
	@echo ""
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""

init: ## initialize git submodules
	@printf "Check and init git submodules\n"
	@if [ ! -f .gitmodules ]; then \
		printf "Warning: No .gitmodules file found\n"; \
	else \
		git submodule status || true; \
		git submodule init; \
		git submodule update --init --recursive --progress; \
		printf "Submodules init ok\n"; \
		printf "Submodules status\n"; \
		git submodule status --recursive; \
	fi
	@printf "Project dependency initialization complete! You can now run 'make setup'\n"

$(TOP_V): $(SCALA_FILE)
	@printf "Generate SystemVerilog ...\n"
	mkdir -p $(BUILD_DIR)
	$(TIME_CMD) mill -i $(TOP).runMain iopmp.IOPMP \
		-td $(RTL_DIR)/
	@printf "Generate SystemVerilog is ok\n"

verilog: $(TOP_V) ## Generate SystemVerilog 

clean: ## clean all
	@printf "clean all start\n"
	@rm -rf ./$(BUILD_DIR) ./out ./gen_*
	@printf "clean all finished\n"

bsp: ## Generate BSP for VSCode
	mill -i mill.bsp.BSP/install

idea: ## Generate IntelliJ IDEA project files
	mill -i mill.scalalib.GenIdea/idea

reformat: ## Reformat code
	mill -i __.reformat

checkformat: ## Check code formatting
	mill -i __.checkFormat