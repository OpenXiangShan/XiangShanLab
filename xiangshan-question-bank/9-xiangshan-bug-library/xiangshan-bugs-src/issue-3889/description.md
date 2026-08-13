* Add param `withClockGate` at SRAMTemplate
* when SRAM is single-port, use maskedClock for both array\.read\(\) and array\.write\(\) to ensure single-port SRAM access.
* when SRAM is multi-port, the read and write ports of the multi-port SRAM are gated using different clocks.
