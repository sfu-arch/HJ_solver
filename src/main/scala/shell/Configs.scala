package shell


import chisel3.Module
import config._


class VCRSimParams(val num_ptrs: Int = 42, val num_vals: Int = 15,
                   val num_event: Int = 1, val num_ctrl: Int = 1) extends VCRParams {
  override val nCtrl = num_ctrl
  override val nECnt = num_event
  override val nVals = num_vals
  override val nPtrs = num_ptrs
  override val regBits = 32
  val ptrBits = regBits
}

/** VME parameters.
  *
  * nRead =   numSegments * 6
  * nWrite =  numColMerger * 3
  */
class VMESimParams() extends VMEParams {
  override val nReadClients: Int = 1
  override val nWriteClients: Int = 1
  require(nReadClients > 0, s"\n\n [VMEParams] number of segments must be larger than 0\n\n")
  require(nWriteClients > 0, s"\n\n [VMEParams] number of column mergers must be larger than 0\n\n")
}

/**
  * vals =  numSegments * 3
  * ptrs =  numSegments * 6 + numColMerger * 3
  * ecnt =  numColMerger + 1
  */
/** De10Config. Shell configuration for De10 */
class De10Config () extends Config((site, here, up) => {
  case ShellKey => ShellParams(
    hostParams = AXIParams(
      addrBits = 16, dataBits = 32, idBits = 13, lenBits = 4),
    memParams = AXIParams(
      addrBits = 32, dataBits = 512, userBits = 5,
      lenBits = 4, // limit to 16 beats, instead of 256 beats in AXI4
      coherent = true),
    vcrParams = new VCRSimParams(num_ptrs = 2, num_vals = 1, num_event = 2, num_ctrl = 1),
    vmeParams = new VMESimParams())
})

/** AWSConfig. Shell configuration for AWS FPGAs */
class AWSConfig (numSegments: Int = 1, numSorter: Int = 1)extends Config((site, here, up) => {
  case ShellKey => ShellParams(
    hostParams = AXIParams(
      addrBits = 32, dataBits = 32, idBits = 13, lenBits = 8),
    memParams = AXIParams(
      addrBits = 64, dataBits = 512, userBits = 10,
      lenBits = 4,
      coherent = false),
    vcrParams = new VCRSimParams(num_ptrs = 2, num_vals = 0, num_event = 2, num_ctrl = 1),
    vmeParams = new VMESimParams())
})


/** PynqConfig. Shell configuration for Pynq */
class PynqConfig (numSegments: Int = 1, numSorter: Int = 1) extends Config((site, here, up) => {
  case ShellKey => ShellParams(
    hostParams = AXIParams(
      coherent = false,
      addrBits = 16,
      dataBits = 32,
      lenBits = 8,
      userBits = 1),
    memParams = AXIParams(
      coherent = true,
      addrBits = 32,
      dataBits = 64,
      lenBits = 8,
      userBits = 1),
    vcrParams = new VCRSimParams(num_ptrs = 2, num_vals = 0, num_event = 2, num_ctrl = 1),
    vmeParams = new VMESimParams())
})
