state := Mux(sbuffer_empty, s_cache_req, s_wait_flush_sbuffer_resp)
...
when (state === s_wait_flush_sbuffer_resp) {
  when (sbuffer_empty) {
    state := s_cache_req
  }
}
