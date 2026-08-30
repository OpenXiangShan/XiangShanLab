The cmo (mainly zero) instructions require the mask to be set to 0xFFFF (fully valid) in storeunit for forwarding and checking.
This has to do with the storequeue's forwarding mechanism, where the storequeue passes through the mask to pre-pass data after an address match.
For cmo instructions, the address will be matched by cacheline, so you need to set mask all to 1.


---
**Please delete the following words when merging: 
The pr does not solve the previously mentioned problem of cmo invalid's difftest, I probably(maybe, maybe.) already have a solution for it and will fix it as soon as I get back to Beijing.**
