>   }.elsewhen(last_fire_r) {
    // Clear corrupt_r when response it sent to mainPipe
    // This used to be io.fetch_resp.valid (last_fire_r && mshr_resp.valid) but when mshr is flushed by io.flush/fencei,
    // mshr_resp.valid is false.B and corrupt_r will never be cleared, that's not correct
    // so we remove mshr_resp.valid here, and the condition leftover is last_fire_r
    // or, actually, io.fetch_resp.valid || (last_fire_r && !mshr_resp.valid)
