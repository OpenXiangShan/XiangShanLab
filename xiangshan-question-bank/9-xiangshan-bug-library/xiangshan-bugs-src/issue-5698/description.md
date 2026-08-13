Currently critial REG2REG timing path is :

[1]. commitPtr generate.

[2]. forwardModule stage 1, generate byteSelectOffset.

This PR optimized the timing paths above, but did not fix them fully, we will fix this in the future.
