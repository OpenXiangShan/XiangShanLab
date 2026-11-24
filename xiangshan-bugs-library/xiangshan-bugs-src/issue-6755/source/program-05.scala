// https://github.com/OpenXiangShan/XiangShan/blob/e516245046234ba958fd69cf95241e77278a1c43/src/main/scala/device/SYSCNT.scala#L178-L188
      timeOffset -> RegFieldGroup(
        "mtime",
        Some("Timer Register"),
        Seq(RWNotify(
          64,
          time_rpt_bus,
          time_sw,
          time_rpt_rd,
          time_sw_vld,
          Some(RegFieldDesc("timeswReq", "", reset = Some(0), volatile = true))
        ))
