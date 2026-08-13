In commit `785e3bf`, when vleff instructions have exception but not raise, we will set tail undisturbed in uop which will change vl. But backend would not raise exception, and don't consider have trap, We retire as if it were a normal instruction. Such treatment is consistent with the spec, but in order to align with `REF`, we revert commit  `785e3bf`.

This PR modify:
assignment of vfofBuffer's  `io.mergeUopWriteback` , MergeBuffer.io.uopWriteback --> MergeBuffer.io.toLsq; 
writeback origin vl, modified vl --> origin vl.
