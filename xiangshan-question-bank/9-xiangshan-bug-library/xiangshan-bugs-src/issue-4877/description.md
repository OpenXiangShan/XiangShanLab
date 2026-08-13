When loadUnit need to write back a vector request at the same time, misalignBuffer will handshake success, but not real writeback, which lead to the loss of vector misalign request.
