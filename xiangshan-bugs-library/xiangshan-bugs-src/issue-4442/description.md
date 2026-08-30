As the comment says, even if a `PF` is generated, an address is still generated for `PMP/PMA` checking, which can lead to some strange responses.
Since the previous(https://github.com/OpenXiangShan/XiangShan/pull/4426) modification removed `s2_exception`, this resulted in the incorrect generation of `s2_uncache`.

This is now represented using clearer semantics:
`s2_actually_uncache`: this real physical address is for uncache space.
The `s2_uncache` has been retained to distinguish if it's a request from prefetching, which may be handled in a subsequent change to **YQ senior sister**.

I synchronised the changes to StoreUnit in this pr(https://github.com/OpenXiangShan/XiangShan/pull/4441).
