// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/VecDecodeChannel/FuTypeField.scala#L88-L89
      case _: Opcode.VSha256msOpcodes.type => FuType.vsha256ms
      case _: Opcode.VSha256cOpcodes.type => FuType.vsha256c
