According to the current RISC-V specification, the Svnapot extension defines pages of size 64KB. Therefore, when performing TLB or bypass hit tag matching, the lower 4 bits of the VPN and tag do not need to be compared.

In MMU, the hit determination logic typically consists of two parts: tag matching and level matching. For pages of different levels—512GB, 1GB, 2MB, or 4KB—different bit fields of the tag need to be matched. In our implementation, the napot case is treated as a variant of the 4KB page. As such, we need to modify the 4KB tag matching logic (`tag_match(0)`) accordingly so that when napot is enabled, the lower 4 bits of the tag are ignored during comparison.

However, the MMU codebase contains numerous `def hit` definitions (which is also one of the reasons for the code complexity), and previously we did not account for all cases or modify all relevant `def hit` definitions appropriately. This commit fixes those bugs.

In addition, this commit also removes some redundant code and introduces a few code additions to slightly improve overall readability.
