when tl_d send data at load s2, the replay request will enter LoadQueueReplay and set blocking flag (at load s3). The above situation will lead to the load not to be wakeup. 

## example

* loadUnit s2 --> to.tl_d_channel.valid = true.B, io.tl_d_channel.mshrid = 0, replayInfo.mshr_id = 0 (mshr get data, but this load still on loadUnit s2)

* loadUnit s3 --> load enter LoadQueueReplay, and set `blocking` flag, but mshr had get data at loadUnit s2, it will not to be wakeup.  


This PR add `RegNext` to get the information of tl_d_channel at loadUnit s2 .
