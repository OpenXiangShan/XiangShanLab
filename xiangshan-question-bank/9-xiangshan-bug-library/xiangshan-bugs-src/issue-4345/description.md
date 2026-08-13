* `s2_fire` and `s2_can_to_s3` are different 
* `io.error.valid` uses `s2_fire`, but `s3_l2_error` uses `s2_can_to_s3`, causing `io.error.valid` to be updated, but `s3_l2_error` not to be updated.
