* Only use hvictlIID for vscause.ExceptionCode when hvictl injects a virtual interrupt and the trap is actually an interrupt.
 * execution must resume from a WFI whenever an interrupt is pending at any privilege level (regardless of whether the interrupt privilege level is higher or lower than the hart's current privilege mode).
 * An interrupt is pending at machine level if register mtopi is not zero. If S-mode is implemented, an interrupt is pending at supervisor level if stopi is not zero. And if the H extension is implemented, an interrupt is pending at VS level if vstopi is not zero.
