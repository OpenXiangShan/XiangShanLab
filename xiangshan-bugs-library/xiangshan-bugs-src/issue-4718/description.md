Bug descriptions:
When `MissQueue` refills data, ECC error injection occurs. The way where the tag hits before the ECC error is injected (if any) should be selected for replacement. However, `s1_need_replacement` does not take this into account, resulting in the error tag reported to `BEU` being the one selected by the replacer, rather than the tag corresponding to the ECC error injection.

How to fix:
* In the `s1` stage of  `MainPipe`, add a check to determine whether it is in the ECC injection state. If so, it is necessary to determine whether to compare with the non-injected tag, determine whether there is a hit way, and select the hit way as the path corresponding to accessing `BankedDataArray`.

*  In the `s2` stage of `MainPipe`, by determining whether it is in the ECC injection state and whether there is a hit way with a tag error, add additional logic to indicate whether `replacement` or `eviction` really needs to be processed.
