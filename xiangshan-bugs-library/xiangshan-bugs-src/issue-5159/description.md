- Add InternalBank wrapper for (NumWay\*sram + 1\*WriteBuffer)
- Add AlignBank wrapper for (NumInternalBanks\*InternalBank + 1\*Replacer)

Also:
- Fix internalBankIdx caculation logic:
  - `getInternalBankIdx(startVAddr)` -> `getInternalBankIdx(startVAddr + i * alignSize)`
  - MinimalConfig coremark-2-iter IPC 0.74 -> 0.99, mbtbAlloc ~2000 -> ~500
