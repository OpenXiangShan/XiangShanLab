- add enable bit `KEYIDEN` in csr MBMC to determine whether the MSB of address are treated as KeyID or normal address
- Example:
PAddrBits=48 & KeyIDBits=1
```
[47,       46,45,..............,1,0]
{-KeyID-} {--------RealPAddr-------}
```
- when `KEYIDEN=0`, PMP/PMA/Bitmap check full `address[47:0]` and disable `keyid` check with `cmode`
- when `KEYIDEN=1`, PMP/PMA/Bitmap check `address[46:0] `without `keyid` and enable `keyid` check

<!-- This is an auto-generated comment: release notes by coderabbit.ai -->
## Summary by CodeRabbit

* **New Features**
  * Added KEYIDEN support in CSRs and memory permission checks to enable optional KeyID-based gating for address translation and PMP checks.

* **Improvements**
  * Tightened validation: corrected MEM encryption validation message and enforce that non-zero KeyIDBits require bitmap-check capability on the first tile to prevent misconfiguration.

* **Bug Fixes**
  * Fixed messaging to clarify MEM encryption/keyID configuration requirements.

<sub>✏️ Tip: You can customize this high-level summary in your review settings.</sub>
<!-- end of auto-generated comment: release notes by coderabbit.ai -->
