Add an signal to indicate CMO flush when io.in.flush.valid of sbuffer is set. CMO flush needs to drain sbuffer and input valid requests.

State x_drain_sbuffer does not need mshr drain.
