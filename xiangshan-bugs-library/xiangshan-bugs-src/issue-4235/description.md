The previous PMA configuration was as follows, we should only remove useless GPU address space.

>     addPMA(0x0L, range = 0x1000000000000L, a = 3)
>     addPMA(PMPPmemHighBounds(0), c = true, atomic = true, a = 1, x = true, w = true, r = true) 
>     addPMA(PMPPmemLowBounds(0), a = 1, w = true, r = true) 
>     addPMA(0x3A000000L, a = 1)
>     addPMA(0x39002000L, a = 1, w = true, r = true)
>     addPMA(0x39000000L, a = 1, w = true, r = true)
>     addPMA(0x38022000L, a = 1, w = true, r = true)
>     addPMA(0x38021000L, a = 1, x = true, w = true, r = true)
>     addPMA(0x38020000L, a = 1, w = true, r = true)
>     addPMA(0x30050000L, a = 1, w = true, r = true) // FIXME: GPU space is cacheable?
>     addPMA(0x30010000L, a = 1, w = true, r = true)
>     addPMA(0x20000000L, a = 1, x = true, w = true, r = true)
>     addPMA(0x10000000L, a = 1, w = true, r = true)
>     addPMA(0)


---

**Removing the `L3CacheCtrl` address space caused an error.**

**This pr adds the `L3CacheCtrl` address space back into the `PMA` configuration.**
