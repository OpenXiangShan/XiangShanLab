// src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
io.replace_access.valid := s3_valid && s3_hit
io.access_flag_write.valid := s3_valid && s3_hit && !s3_is_prefetch
