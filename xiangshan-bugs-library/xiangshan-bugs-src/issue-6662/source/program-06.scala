package top

import org.chipsalliance.cde.config.Config
import xiangshan.XSTileKey

class P4Q3RG8SmmptFullCoreConfig(n: Int = 1) extends Config(
  new Config((site, here, up) => {
    case XSTileKey => up(XSTileKey).map { original =>
      val selected = original.copy(HasMptCheck = true, HasBitmapCheck = false)
      println(
        s"P4_Q3R_G8_CONFIG_CLOSURE base=MinimalConfig changed=HasBitmapCheck,HasMptCheck " +
          s"has_mpt_check=${selected.HasMptCheck} has_bitmap_check=${selected.HasBitmapCheck}"
      )
      selected
    }
  }) ++ new MinimalConfig(n)
)
