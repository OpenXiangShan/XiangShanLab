* Tag ECC injection may cause a cache miss and raise a miss req, when mshr refill this miss req will see a cache hit after recovering the tag ecc injection.

Now fix it, fix `s2_can_go_to_s3` select priority for refill ecc inject lead to tag miss
* when req is refill and hit, it will wait for `refill_info_valid` otherwise req will replay
