package stencil

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import shell._
import config._
import dnn.memory.{MInputOutDMA, MO_inStreamDMA, StreamStore, outStreamDMA}
import dnnnode.MIMOQueue

/*class HJSolver_4D(bit_width: Int, mantissa_width: Int, x_size: Int, y_size: Int,
                  v_size: Int, theta_size: Int, max_accel: Double, max_w: Double, debug: Boolean = false)(implicit val p: Parameters)
  extends Module {
  val mp = p(ShellKey).memParams
  val io = IO(new Bundle {
    val start    = Input(Bool()) // start signal
    val rdAddr = Input(UInt(mp.addrBits.W))
    val wrAddr = Input(UInt(mp.addrBits.W))

    val vme_rd = new VMEReadMaster
    val vme_wr = new VMEWriteMaster

    val done          = Output(Bool())
    val PE_done       = Output(Bool())
  })

  // Instantiate the mem_buffer and PE
  //val mem_buffer = Module(new shift_buffer4d(bit_width, mantissa_width, x_size, y_size, v_size, theta_size))
  val mem_buffer = Module(new parallel_reuseBuffer(bit_width, mantissa_width, x_size, y_size, v_size, theta_size, 4))
  val PE0 = Module(new DubinsCar_PE(bit_width, mantissa_width, x_size, y_size, v_size, theta_size, max_accel, max_w, ID=0, 4))
  val PE1 = Module(new DubinsCar_PE(bit_width, mantissa_width, x_size, y_size, v_size, theta_size, max_accel, max_w, ID=1, 4))
  val PE2 = Module(new DubinsCar_PE(bit_width, mantissa_width, x_size, y_size, v_size, theta_size, max_accel, max_w, ID=2, 4))
  val PE3 = Module(new DubinsCar_PE(bit_width, mantissa_width, x_size, y_size, v_size, theta_size, max_accel, max_w, ID=3, 4))

  val inDMA =  Module(new MO_inStreamDMA(bufSize = 5000, "inp", num_out = 4))
  val outDMA = Module(new MInputOutDMA(bufSize = 20, memTensorType = "out", numIns = 4))

  /***************************************************
                      MAIN DATAPATH
   ***************************************************/

  /******* Connect mem buffer taps to PE's input following PE_4D.scala convention *****/
  /*
* The following is convention for the input coming in
* 0: [l][k][j][i  ]
* 1: [l][k][j][i-1]
* 2: [l][k][j][i+1]
* 3: [l][k][j-1][i]
* 4: [l][k][j+1][i]
* 5: [l][k-1][j][i]
* 6: [l][k+1][j][i]
* 7: [l-1][k][j][i]
* 8: [l+1][k][j][i]
* */

  /************ PE0 Connections *************/
  PE0.io.in.bits(0) := mem_buffer.io.out.bits(4)   // PE0 works on V[i][j][k][l]
  PE0.io.in.bits(1) := mem_buffer.io.out.bits(7)   // [i-1][j][k][l]
  PE0.io.in.bits(2) := mem_buffer.io.out.bits(0)   // [i+1][j][k][l]
  PE0.io.in.bits(3) := mem_buffer.io.out.bits(6)   // [i][j-1][k][l]
  PE0.io.in.bits(4) := mem_buffer.io.out.bits(1)   // [i][j+1][k][l]
  PE0.io.in.bits(5) := mem_buffer.io.out.bits(5)   // [i][j][k-1][l]
  PE0.io.in.bits(6) := mem_buffer.io.out.bits(2)   // [i][j][k+1][l]
  PE0.io.in.bits(7) := mem_buffer.io.out.bits(26)  // [i][j][k][l-1]
  PE0.io.in.bits(8) := mem_buffer.io.out.bits(11)  // [i][j][k][l+1]

  /************ PE1 Connections *************/
  PE1.io.in.bits(0) := mem_buffer.io.out.bits(11)  // PE1 works on V[i][j][k][l + 1]
  PE1.io.in.bits(1) := mem_buffer.io.out.bits(14)  //[i-1][j][k][l+1]
  PE1.io.in.bits(2) := mem_buffer.io.out.bits(8)   //[i+1][j][k][l+1]
  PE1.io.in.bits(3) := mem_buffer.io.out.bits(13)  //[i][j-1][k][l+1]
  PE1.io.in.bits(4) := mem_buffer.io.out.bits(9)   //[i][j+1][k][l+1]
  PE1.io.in.bits(5) := mem_buffer.io.out.bits(12)  //[i][j][k-1][l+1]
  PE1.io.in.bits(6) := mem_buffer.io.out.bits(10)  //[i][j][k+1][l+1]
  PE1.io.in.bits(7) := mem_buffer.io.out.bits(4)   //[i][j][k][l]
  PE1.io.in.bits(8) := mem_buffer.io.out.bits(18)  //[i][j][k][l+2]

  /************ PE2 Connections *************/
  PE2.io.in.bits(0) := mem_buffer.io.out.bits(18) // PE2 works on V[i][j][k][l + 2]
  PE2.io.in.bits(1) := mem_buffer.io.out.bits(21) // V[i-1][j][k][l+2]
  PE2.io.in.bits(2) := mem_buffer.io.out.bits(15) // V[i+1][j][k][l+2]
  PE2.io.in.bits(3) := mem_buffer.io.out.bits(20) // V[i][j-1][k][l+2]
  PE2.io.in.bits(4) := mem_buffer.io.out.bits(16) // V[i][j+1][k][l+2]
  PE2.io.in.bits(5) := mem_buffer.io.out.bits(19) // V[i][j][k-1][l+2]
  PE2.io.in.bits(6) := mem_buffer.io.out.bits(17) // V[i][j][k+1][l+2]
  PE2.io.in.bits(7) := mem_buffer.io.out.bits(11) // V[i][j][k][l+1]
  PE2.io.in.bits(8) := mem_buffer.io.out.bits(25) // V[i][j][k][l+3]

  /************ PE3 Connections *************/
  PE3.io.in.bits(0) := mem_buffer.io.out.bits(25) // PE3 works on V[i][j][k][l+3]
  PE3.io.in.bits(1) := mem_buffer.io.out.bits(29) //V[i-1][j][k][l+3]
  PE3.io.in.bits(2) := mem_buffer.io.out.bits(22) //V[i+1][j][k][l+3]
  PE3.io.in.bits(3) := mem_buffer.io.out.bits(28) //V[i][j-1][k][l+3]
  PE3.io.in.bits(4) := mem_buffer.io.out.bits(23) //V[i][j+1][k][l+3]
  PE3.io.in.bits(5) := mem_buffer.io.out.bits(27) //V[i][j][k-1][l+3]
  PE3.io.in.bits(6) := mem_buffer.io.out.bits(24) //V[i][j][k+1][l+3]
  PE3.io.in.bits(7) := mem_buffer.io.out.bits(18) // V[i][j][k][l+2]
  PE3.io.in.bits(8) := mem_buffer.io.out.bits(3)  // V[i][j][k][l+4]


  /************************************************************************************/
  // Validate 4 PEs
  PE0.io.in.valid := mem_buffer.io.out.valid
  PE1.io.in.valid := mem_buffer.io.out.valid
  PE2.io.in.valid := mem_buffer.io.out.valid
  PE3.io.in.valid := mem_buffer.io.out.valid

  mem_buffer.io.out.ready := PE0.io.in.ready & PE1.io.in.ready & PE2.io.in.ready & PE3.io.in.ready

  // Signal to control the PE
  PE0.io.start := false.B // By default
  PE1.io.start := false.B
  PE2.io.start := false.B
  PE3.io.start := false.B

  // Results of this module is basically the PE's
  io.done := false.B
  io.PE_done := false.B

  // inDMA specifications
  inDMA.io.start := false.B
  io.vme_rd <> inDMA.io.vme_rd
  inDMA.io.baddr := io.rdAddr
  inDMA.io.len := (x_size * y_size * v_size * theta_size).U

  // inDMA <> membufferr
  mem_buffer.io.in.bits := inDMA.io.out.bits.asTypeOf(mem_buffer.io.in.bits)
  mem_buffer.io.in.valid := inDMA.io.out.valid
//  printf(p"\n Valid inputToMem : ${inDMA.io.out.valid} Input1 ${mem_buffer.io.in.bits(0).asUInt} Input2 ${mem_buffer.io.in.bits(1).asUInt} " +
//    p"Input3: ${mem_buffer.io.in.bits(2).asUInt} Input3: ${mem_buffer.io.in.bits(3).asUInt} ")

  inDMA.io.out.ready := mem_buffer.io.in.ready

  //  mem_buffer.io.in <> inDMA.io.out

  // outDMA <> PE result
  io.vme_wr <> outDMA.io.vme_wr
  outDMA.io.baddr := io.wrAddr
  //outDMA.io.in.bits := PE.io.out.bits.asTypeOf(outDMA.io.in.bits)
  outDMA.io.in.bits(0) := PE0.io.out.bits.asUInt()
  outDMA.io.in.bits(1) := PE1.io.out.bits.asUInt()
  outDMA.io.in.bits(2) := PE2.io.out.bits.asUInt()
  outDMA.io.in.bits(3) := PE3.io.out.bits.asUInt()

  outDMA.io.in.valid := PE0.io.out.valid & PE1.io.out.valid & PE2.io.out.valid & PE3.io.out.valid
  PE0.io.out.ready := outDMA.io.in.ready
  PE1.io.out.ready := outDMA.io.in.ready
  PE2.io.out.ready := outDMA.io.in.ready
  PE3.io.out.ready := outDMA.io.in.ready

  outDMA.io.last := false.B

  /***************************************************
                CONTROL STATE MACHINE
    ***************************************************/

  // States of control
  val idle :: loadBuff1 :: finish :: Nil = Enum(3)
  val state = RegInit(idle)


  // Finite state machine description

  switch(state) {
    is(idle) {
      state := idle
      when(io.start) {
        inDMA.io.start := true.B
        state := loadBuff1
      }
    }
    is(loadBuff1) {
      /*
        WHEN RE-USE BUFFER IS HALF-LY FILLED, THE PE STARTS
       */
      when(mem_buffer.io.out.valid) {
        PE0.io.start := true.B
        PE1.io.start := true.B
        PE2.io.start := true.B
        PE3.io.start := true.B
        printf(p"\n PE 0. Tap1: ${PE0.io.in.bits(0).asUInt} " +
            p"Tap2: ${PE0.io.in.bits(8).asUInt} Tap3: ${PE0.io.in.bits(6).asUInt} Tap4: ${PE0.io.in.bits(4).asUInt}" +
            p"Tap5: ${PE0.io.in.bits(2).asUInt} ")
      }
      when(PE0.io.done) {
        state := finish
        io.PE_done := true.B
        outDMA.io.last := true.B
      }
    }
    is(finish) {
      when(outDMA.io.done){
        state := idle
        io.done := true.B
      }
    }
  }

  if(debug){
      printf(p"\n HJ_4D State : ${state} Output: ${outDMA.io.in.bits.asUInt}  Valid: ${outDMA.io.in.valid} Ready: ${outDMA.io.in.ready}")
  }

//printf(p"\n State : ${state} Re-buffer input : ${mem_buffer.io.in.bits.asSInt} " +
//  p"Valid-signal : ${mem_buffer.io.in.valid}")

}*/

class HJSolver_4D(bit_width: Int, mantissa_width: Int, x_size: Int, y_size: Int,
                  v_size: Int, theta_size: Int, max_accel: Double, max_w: Double, debug: Boolean = false, iter_num: Int = 67)(implicit val p: Parameters)
  extends Module {
  val mp = p(ShellKey).memParams
  val io = IO(new Bundle {
    val start    = Input(Bool()) // start signal
    val rdAddr = Input(UInt(mp.addrBits.W))
    val wrAddr = Input(UInt(mp.addrBits.W))

    val vme_rd = new VMEReadMaster
    val vme_wr = new VMEWriteMaster

    val done          = Output(Bool())
    val PE_done       = Output(Bool())
  })

  // Instantiate the mem_buffer and PE
  //val mem_buffer = Module(new shift_buffer4d(bit_width, mantissa_width, x_size, y_size, v_size, theta_size))
  val mem_buffer = Module(new parallel_reuseBuffer(bit_width, mantissa_width, x_size, y_size, v_size, theta_size, 4))
  val PE0 = Module(new DubinsCar_PE(bit_width, mantissa_width, x_size, y_size, v_size, theta_size, max_accel, max_w, ID=0, 4))
  val PE1 = Module(new DubinsCar_PE(bit_width, mantissa_width, x_size, y_size, v_size, theta_size, max_accel, max_w, ID=1, 4))
  val PE2 = Module(new DubinsCar_PE(bit_width, mantissa_width, x_size, y_size, v_size, theta_size, max_accel, max_w, ID=2, 4))
  val PE3 = Module(new DubinsCar_PE(bit_width, mantissa_width, x_size, y_size, v_size, theta_size, max_accel, max_w, ID=3, 4))

  //val inDMA =  Module(new MO_inStreamDMA(bufSize = 512, "inp", num_out = 4))
  val inDMA =  Module(new MO_inStreamDMA(bufSize = 512, "inp", num_out = 4))
  // Holds everything inside scratchpad and flush in the end?
  //val outDMA = Module(new MInputOutDMA(bufSize = 20, memTensorType = "out", numIns = 4))
  //val outDMA = Module(new outStreamDMA(bufSize = 20, memTensorType = "out", numIns = 4))
  
  val outDMA = Module(new StreamStore(bufSize = 512, NumIns = 4))




  /***************************************************
                      MAIN DATAPATH
   ***************************************************/

  /******* Connect mem buffer taps to PE's input following PE_4D.scala convention *****/
  /*
* The following is convention for the input coming in
* 0: [l][k][j][i  ]
* 1: [l][k][j][i-1]
* 2: [l][k][j][i+1]
* 3: [l][k][j-1][i]
* 4: [l][k][j+1][i]
* 5: [l][k-1][j][i]
* 6: [l][k+1][j][i]
* 7: [l-1][k][j][i]
* 8: [l+1][k][j][i]
* */

  /************ PE0 Connections *************/
  PE0.io.in.bits(0) := mem_buffer.io.out.bits(4)   // PE0 works on V[i][j][k][l]
  PE0.io.in.bits(1) := mem_buffer.io.out.bits(7)   // [i-1][j][k][l]
  PE0.io.in.bits(2) := mem_buffer.io.out.bits(0)   // [i+1][j][k][l]
  PE0.io.in.bits(3) := mem_buffer.io.out.bits(6)   // [i][j-1][k][l]
  PE0.io.in.bits(4) := mem_buffer.io.out.bits(1)   // [i][j+1][k][l]
  PE0.io.in.bits(5) := mem_buffer.io.out.bits(5)   // [i][j][k-1][l]
  PE0.io.in.bits(6) := mem_buffer.io.out.bits(2)   // [i][j][k+1][l]
  PE0.io.in.bits(7) := mem_buffer.io.out.bits(26)  // [i][j][k][l-1]
  PE0.io.in.bits(8) := mem_buffer.io.out.bits(11)  // [i][j][k][l+1]

  /************ PE1 Connections *************/
  PE1.io.in.bits(0) := mem_buffer.io.out.bits(11)  // PE1 works on V[i][j][k][l + 1]
  PE1.io.in.bits(1) := mem_buffer.io.out.bits(14)  //[i-1][j][k][l+1]
  PE1.io.in.bits(2) := mem_buffer.io.out.bits(8)   //[i+1][j][k][l+1]
  PE1.io.in.bits(3) := mem_buffer.io.out.bits(13)  //[i][j-1][k][l+1]
  PE1.io.in.bits(4) := mem_buffer.io.out.bits(9)   //[i][j+1][k][l+1]
  PE1.io.in.bits(5) := mem_buffer.io.out.bits(12)  //[i][j][k-1][l+1]
  PE1.io.in.bits(6) := mem_buffer.io.out.bits(10)  //[i][j][k+1][l+1]
  PE1.io.in.bits(7) := mem_buffer.io.out.bits(4)   //[i][j][k][l]
  PE1.io.in.bits(8) := mem_buffer.io.out.bits(18)  //[i][j][k][l+2]

  /************ PE2 Connections *************/
  PE2.io.in.bits(0) := mem_buffer.io.out.bits(18) // PE2 works on V[i][j][k][l + 2]
  PE2.io.in.bits(1) := mem_buffer.io.out.bits(21) // V[i-1][j][k][l+2]
  PE2.io.in.bits(2) := mem_buffer.io.out.bits(15) // V[i+1][j][k][l+2]
  PE2.io.in.bits(3) := mem_buffer.io.out.bits(20) // V[i][j-1][k][l+2]
  PE2.io.in.bits(4) := mem_buffer.io.out.bits(16) // V[i][j+1][k][l+2]
  PE2.io.in.bits(5) := mem_buffer.io.out.bits(19) // V[i][j][k-1][l+2]
  PE2.io.in.bits(6) := mem_buffer.io.out.bits(17) // V[i][j][k+1][l+2]
  PE2.io.in.bits(7) := mem_buffer.io.out.bits(11) // V[i][j][k][l+1]
  PE2.io.in.bits(8) := mem_buffer.io.out.bits(25) // V[i][j][k][l+3]

  /************ PE3 Connections *************/
  PE3.io.in.bits(0) := mem_buffer.io.out.bits(25) // PE3 works on V[i][j][k][l+3]
  PE3.io.in.bits(1) := mem_buffer.io.out.bits(29) //V[i-1][j][k][l+3]
  PE3.io.in.bits(2) := mem_buffer.io.out.bits(22) //V[i+1][j][k][l+3]
  PE3.io.in.bits(3) := mem_buffer.io.out.bits(28) //V[i][j-1][k][l+3]
  PE3.io.in.bits(4) := mem_buffer.io.out.bits(23) //V[i][j+1][k][l+3]
  PE3.io.in.bits(5) := mem_buffer.io.out.bits(27) //V[i][j][k-1][l+3]
  PE3.io.in.bits(6) := mem_buffer.io.out.bits(24) //V[i][j][k+1][l+3]
  PE3.io.in.bits(7) := mem_buffer.io.out.bits(18) // V[i][j][k][l+2]
  PE3.io.in.bits(8) := mem_buffer.io.out.bits(3)  // V[i][j][k][l+4]


  /************************************************************************************/
  // Validate 4 PEs
  PE0.io.in.valid := mem_buffer.io.out.valid
  PE1.io.in.valid := mem_buffer.io.out.valid
  PE2.io.in.valid := mem_buffer.io.out.valid
  PE3.io.in.valid := mem_buffer.io.out.valid

  mem_buffer.io.out.ready := PE0.io.in.ready & PE1.io.in.ready & PE2.io.in.ready & PE3.io.in.ready

  // Signal to control the PE
  PE0.io.start := false.B // By default
  PE1.io.start := false.B
  PE2.io.start := false.B
  PE3.io.start := false.B

  // Results of this module is basically the PE's
  io.done := false.B
  io.PE_done := false.B

  // inDMA specifications
  inDMA.io.start := false.B
  io.vme_rd <> inDMA.io.vme_rd
  inDMA.io.baddr := io.rdAddr
  inDMA.io.len := (x_size * y_size * v_size * theta_size).U

  // inDMA <> membufferr
  mem_buffer.io.in.bits := inDMA.io.out.bits.asTypeOf(mem_buffer.io.in.bits)
  mem_buffer.io.in.valid := inDMA.io.out.valid
  inDMA.io.out.ready := mem_buffer.io.in.ready

  //  printf(p"\n Valid inputToMem : ${inDMA.io.out.valid} Input1 ${mem_buffer.io.in.bits(0).asUInt} Input2 ${mem_buffer.io.in.bits(1).asUInt} " +
//      p"Input3: ${mem_buffer.io.in.bits(2).asUInt} Input3: ${mem_buffer.io.in.bits(3).asUInt} ")
//  printf(p"\n inDMA done : ${inDMA.io.done}")

  //  mem_buffer.io.in <> inDMA.io.out

  // Read from inDMA states
  /* val noRead :: read :: readInDMAOnly :: Nil = Enum(3)
  val readState = RegInit(noRead)
  val read_ready = (readState === read && inDMA.io.out.valid) || (readState === readInDMAOnly)

  //printf(p"\n readState : ${readState}")
  //printf(p"\n read_ready : ${read_ready}")

  switch(readState){
    is(noRead){
      readState := noRead
      when(inDMA.io.start){
        readState := read
      }
    }
    is(read){
      readState := read
      when(inDMA.io.done){
        readState := readInDMAOnly
      }
    }
    is(readInDMAOnly){

      readState := readInDMAOnly
      when(PE0.io.done){
        readState := noRead
      }
    }
  }*/

  // outDMA <> PE result
  io.vme_wr <> outDMA.io.vme_wr
  outDMA.io.baddr := io.wrAddr
  //outDMA.io.in.bits := PE.io.out.bits.asTypeOf(outDMA.io.in.bits)
  outDMA.io.in.bits(0) := PE0.io.out.bits.asUInt()
  outDMA.io.in.bits(1) := PE1.io.out.bits.asUInt()
  outDMA.io.in.bits(2) := PE2.io.out.bits.asUInt()
  outDMA.io.in.bits(3) := PE3.io.out.bits.asUInt()
  //printf(p"\n outDMA(0): ${outDMA.io.in.bits(0).asUInt}, outDMA(1): ${outDMA.io.in.bits(1).asUInt}")

  outDMA.io.in.valid := PE0.io.out.valid & PE1.io.out.valid & PE2.io.out.valid & PE3.io.out.valid// & read_ready
  PE0.io.out.ready := outDMA.io.in.ready
  PE1.io.out.ready := outDMA.io.in.ready
  PE2.io.out.ready := outDMA.io.in.ready
  PE3.io.out.ready := outDMA.io.in.ready

  val my_ready = outDMA.io.in.ready //& read_ready
  // stall if outDMA or inDMA is not yet ready
  PE0.io.outDMA_ready := my_ready
  PE1.io.outDMA_ready := my_ready
  PE2.io.outDMA_ready := my_ready
  PE3.io.outDMA_ready := my_ready

  mem_buffer.io.outDMA_ready := my_ready

  outDMA.io.last := false.B
  outDMA.io.start := false.B
  //printf(p"\n read_state, inDMA.io.out.valid : ${readState}, ${inDMA.io.out.valid}")
//  printf(p"\n outDMA.io.in.valid : ${outDMA.io.in.valid}")
//  printf(p"\n read_ready : ${read_ready}")
//  printf(p"\n inDMA done : ${inDMA.io.done}")

  //  printf(p"\n inDMA done : ${inDMA.io.done}")
//  printf(p"\n inDMA.io.out.valid: ${inDMA.io.out.valid}")
//  printf(p"\n my_ready: ${my_ready}")

  /***************************************************
                CONTROL STATE MACHINE
   ***************************************************/

  // States of control
  val idle :: loadAndExecute :: keepContinue :: finish :: Nil = Enum(4)
  val state = RegInit(idle)

  // Iteration counter
  val start_count_iter = Wire(Bool())
  start_count_iter  := false.B
  val (iter_count, wrap_iter) = Counter(start_count_iter, iter_num)

  // Switch V_new and V_old read address
  when(iter_count === 0.U)
  {
    inDMA.io.baddr  := io.rdAddr
    outDMA.io.baddr := io.wrAddr
  }.otherwise{
    inDMA.io.baddr := io.wrAddr
    outDMA.io.baddr := io.wrAddr
  }

  //printf(p"\n state : ${state}")

  // Finite state machine description
  switch(state) {
    is(idle) {
      state := idle
      when(io.start) {
        printf(p"\n Starting")
        inDMA.io.start := true.B
        state := loadAndExecute
        // Start outDMA
        outDMA.io.start := true.B
      }
    }
    is(loadAndExecute) {
      //printf(p"\n outDMA ready : ${outDMA.io.in.ready}")
      // Set DMA read address based on iter_count
      /*
        WHEN RE-USE BUFFER IS FILLED, THE PE STARTS
       */
      when(mem_buffer.io.out.valid) {
        PE0.io.start := true.B
        PE1.io.start := true.B
        PE2.io.start := true.B
        PE3.io.start := true.B
//        printf(p"\n PE 0. Tap1: ${PE0.io.in.bits(0).asUInt} " +
//          p"Tap2: ${PE0.io.in.bits(8).asUInt} Tap3: ${PE0.io.in.bits(6).asUInt} Tap4: ${PE0.io.in.bits(4).asUInt}" +
//          p"Tap5: ${PE0.io.in.bits(2).asUInt} ")
      }

      when(PE0.io.done) { // Finished one iteration
        printf(p"\n Finished one iteration")
        state := loadAndExecute    //
        start_count_iter := true.B // iter_count++
        outDMA.io.last := true.B // Start sending back to DRAM
        when(wrap_iter){ // when iter_count = 70
          state := finish
          io.PE_done := true.B     // What is this?
        }
      }

      when(outDMA.io.done){ // happens later
          state := keepContinue
      }
    }
    is(keepContinue){
      printf(p"\n Restart reading from DMA")
      inDMA.io.start := true.B // Restart reading from DMA
      outDMA.io.start := true.B // restart writing to DMA
      state := loadAndExecute
    }
    is(finish) {
      //printf(p"\n I'm in finished")
      when(outDMA.io.done){
        //printf(p"\n When outDMA io is done")
        state := idle
        io.done := true.B
      }
    }
  }

  if(debug){
    printf(p"\n HJ_4D State : ${state} Output: ${outDMA.io.in.bits.asUInt}  Valid: ${outDMA.io.in.valid} Ready: ${outDMA.io.in.ready}")
  }

  //printf(p"\n State : ${state} Re-buffer input : ${mem_buffer.io.in.bits.asSInt} " +
  //  p"Valid-signal : ${mem_buffer.io.in.valid}")
}


