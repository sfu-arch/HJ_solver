package dnn.memory

import chisel3._
import chisel3.util._
import config._
import dnn.memory.ISA._
import dnnnode.{MIMOQueue, MInputStoreQueue}
import shell._


/** outDMA
 *
 * Load 1D and 2D tensors from main memory (DRAM) to input/weight
 * scratchpads (SRAM). Also, there is support for zero padding, while
 * doing the load. Zero-padding works on the y and x axis, and it is
 * managed by TensorPadCtrl. The TensorDataCtrl is in charge of
 * handling the way tensors are stored on the scratchpads.
 */
class MInputOutDMAIO(memTensorType: String = "none", num_Ins: Int)(implicit val p: Parameters)
  extends Module {
  val tp = new TensorParams(memTensorType)
  val mp = p(ShellKey).memParams
  val io = IO(new Bundle {
    val done = Output(Bool())
    val baddr = Input(UInt(mp.addrBits.W))
    val vme_wr = new VMEWriteMaster
    val in = Flipped(Decoupled(Vec(num_Ins, UInt(p(XLEN).W))))
    val last = Input(Bool())
    val outLen = Output(UInt(mp.addrBits.W))
  })
}

class MInputOutDMA(bufSize: Int, memTensorType: String = "none", numIns: Int)(implicit p: Parameters)
  extends MInputOutDMAIO(memTensorType, numIns)(p) {
  require(bufSize > tp.tensorWidth, "buffer size should be greater than the tensorFile width")

  val tensorStore = Module(new TensorStore(memTensorType))

  val storeQueue = Module(new MInputStoreQueue(UInt(p(XLEN).W), bufSize, numIns ,tp.tensorWidth))

  val popCnt = Counter(math.pow(2, p(XLEN)).toInt)
  val pushCnt = Counter(math.pow(2, p(XLEN)).toInt)
  val length = RegInit(init = 0.U)
  val sendingState = RegInit(false.B)
  val start = RegNext(io.last)

  when(storeQueue.io.enq.fire()){
    pushCnt.value := pushCnt.value + numIns.U
  }
  when (storeQueue.io.deq.fire()) {
    popCnt.inc()
  }


  when(io.last){
    length := pushCnt.value
    pushCnt.value := 0.U
    sendingState := true.B
  }

  val ts_Inst = Wire(new MemDecode)
  val memTensorRows = Mux(length % tp.tensorWidth.U === 0.U, length / tp.tensorWidth.U, (length /tp.tensorWidth.U) + 1.U)


  when (popCnt.value === memTensorRows && length > 0.U){
    popCnt.value := 0.U

  }

  storeQueue.io.last := io.last
  storeQueue.io.enq <> io.in
  io.in.ready := storeQueue.io.enq.ready && !sendingState

  storeQueue.io.deq.ready := true.B

  tensorStore.io.tensor.wr.valid := storeQueue.io.deq.valid
  tensorStore.io.tensor.wr.bits.data := VecInit(storeQueue.io.deq.bits.asUInt()).asTypeOf(tensorStore.io.tensor.wr.bits.data)
  tensorStore.io.tensor.wr.bits.idx := popCnt.value
  tensorStore.io.tensor.rd <> DontCare

  tensorStore.io.start := start
  tensorStore.io.baddr := io.baddr
  tensorStore.io.inst := ts_Inst.asTypeOf(UInt(INST_BITS.W))

  io.vme_wr <> tensorStore.io.vme_wr


  val done = RegInit(init = false.B)

  io.done := done
  when (done) {
    done := false.B
    sendingState := false.B
  }

  io.outLen := length

  when (tensorStore.io.done) {done := true.B}

  ts_Inst.xpad_0 := 0.U
  ts_Inst.xpad_1 := 0.U
  ts_Inst.ypad_0 := 0.U
  ts_Inst.ypad_1 := 0.U
  ts_Inst.xstride := memTensorRows
  ts_Inst.xsize := memTensorRows
  ts_Inst.ysize := 1.U
  ts_Inst.empty_0 := 0.U
  ts_Inst.dram_offset := 0.U
  ts_Inst.sram_offset := 0.U
  ts_Inst.id := 3.U
  ts_Inst.push_next := 0.U
  ts_Inst.push_prev := 0.U
  ts_Inst.pop_next := 0.U
  ts_Inst.pop_prev := 0.U
  ts_Inst.op := 0.U
}
