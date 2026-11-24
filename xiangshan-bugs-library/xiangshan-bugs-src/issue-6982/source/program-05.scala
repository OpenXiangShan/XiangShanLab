val all_empty = cmo_empty && io.sqempty
io.flush.empty := GatedValidRegNext(all_empty)
...
val stIsEmpty = sbuffer.io.flush.empty && uncache.io.flush.empty
atomicsUnit.io.flush_sbuffer.empty := stIsEmpty
