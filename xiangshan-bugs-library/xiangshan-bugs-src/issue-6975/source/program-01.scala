// cache/dcache/mainpipe/MissQueue.scala:894-896   (data_not_refilled = !w_grantfirst, :612)
def before_data_refill_can_merge(new_req: MissReqWoStoreData): Bool = {
  data_not_refilled && new_req.isFromLoad ||
  !io.main_pipe_refill_resp && !w_refill_resp && new_req.isFromStore && alloc_is_store
}
