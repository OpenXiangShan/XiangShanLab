* There were errors in the previous design
  
    * `writeback` generate wrong addr
        * `writeback`'s addr use `s3_tag` to generate , no need to use `s3_tag_error` to select. 
        
    * `error` generate wrong addr
        * `error` must use `s3_tag` to generate, not use `s3_req.addr`,
            *  because the enable condition of `s3_req.addr` is different from that of `s3_error`,
            * should use access cacheline corresponding address
