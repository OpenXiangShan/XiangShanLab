**// The change history contains a Chinese description**

### Problem Description:

For a store with `PBMT=NC` and `PMA=MM` (hereafter referred to as an **NC store**), difftest is performed when it enters the `ubuffer`.

For a store with `PBMT=PMA` and `PMA=MM` (hereafter referred to as a **PMA store**), difftest is performed when it enters the `sbuffer`.

However, once a PMA store enters the `databuffer`, the `rdataPtr` moves forward. If the `sbuffer` is not yet ready, the PMA store remains in the `databuffer`. At this point, if `rdataPtr` advances to an NC store and the `ubuffer` is ready, the NC store proceeds into the `ubuffer` as expected. In this scenario, the NC store performs difftest before the PMA store, resulting in a misalignment in the difftest order.

### Fix Description:

The timing of difftest for PMA stores is advanced to the moment they enter the `databuffer`—that is, when `rdataPtr` moves forward.

Considering the sources of different store types—`vSegment Store` (from the vector unit), `PMA store` (from the StoreQueue), and `NC store` (from the StoreQueue)—the **Store Difftest** logic is isolated into a separate file and integrated at the `MemBlock` level.
