package stencil

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint

// 1 PE re-user buffer structure
class shift_buffer4d(bit_width: Int, mantissa_width: Int, dim1: Int, dim2: Int,
                     dim3: Int, dim4: Int)
  extends Module{

  val io = IO(new Bundle{
    //val ready_sigs      = Input(Vec(5, Bool()))
//    val enq_data        = Input(FixedPoint(bit_width.W, mantissa_width.BP))
//    val enq_val         = Input(Bool())
    val in = Flipped(Decoupled(FixedPoint(bit_width.W, mantissa_width.BP)))
    // All the accessible data to PEs
    val out        = Decoupled(Vec(9, FixedPoint(bit_width.W, mantissa_width.BP)))
//    val buffer_ready    = Output(Bool())
//    val enq_ready       = Output(Bool())
  })

  // Queues
  val store_lp1_kp1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)-1, true)) //bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)-1, true))
  val first_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_kp1_jp1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim3-1)*dim4-1, true))
  val second_delay   = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_jp1_ip1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim4- 1) - 1 , true))
  val third_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_ip1_i    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1 , true))
  val store_i_im1    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1 , true))

  val store_im1_jm1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim4-1) -1, true))
  val fourth_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_jm1_km1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim3-1)*dim4 - 1, true))
  val fifth_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_km1_lm1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)-1, true))
  val sixth_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))


  /************* Connect the IOs to the queues ************/
  // Connect data bits and valid bits
//  io.out_data(0)   := io.enq_data
  io.out.bits(0)   := io.in.bits
  io.out.bits(1)   := first_delay.io.deq.bits
  io.out.bits(2)   := second_delay.io.deq.bits
  io.out.bits(3)   := third_delay.io.deq.bits
  io.out.bits(4)   := store_ip1_i.io.deq.bits
  io.out.bits(5)   := store_i_im1.io.deq.bits
  io.out.bits(6)   := fourth_delay.io.deq.bits
  io.out.bits(7)   := fifth_delay.io.deq.bits
  io.out.bits(8)   := sixth_delay.io.deq.bits

  /************ Connect these queues together ***************/
  // Connect their valid signal and content data together
//  io.buffer_ready             := false.B
  io.out.valid             := false.B

//  store_lp1_kp1.io.enq.bits   := io.enq_data
//  store_lp1_kp1.io.enq.valid  := io.enq_val
  store_lp1_kp1.io.enq <> io.in
  store_lp1_kp1.io.deq <> first_delay.io.enq
  first_delay.io.deq.ready  := false.B

  store_kp1_jp1.io.enq.valid := false.B
  store_kp1_jp1.io.enq.bits  := DontCare
  store_kp1_jp1.io.deq  <> second_delay.io.enq
  second_delay.io.deq.ready := false.B

  store_jp1_ip1.io.enq.valid := false.B
  store_jp1_ip1.io.enq.bits  := DontCare
  store_jp1_ip1.io.deq <> third_delay.io.enq
  third_delay.io.deq.ready := false.B

  store_ip1_i.io.enq.bits := DontCare
  store_ip1_i.io.enq.valid := false.B
  store_ip1_i.io.deq.ready := false.B

  store_i_im1.io.enq.valid := false.B
  store_i_im1.io.enq.bits  := DontCare
  store_i_im1.io.deq.ready := false.B

  store_im1_jm1.io.enq.valid := false.B
  store_im1_jm1.io.enq.bits  := DontCare
  store_im1_jm1.io.deq    <> fourth_delay.io.enq
  fourth_delay.io.deq.ready  := false.B

  store_jm1_km1.io.enq.bits := DontCare
  store_jm1_km1.io.enq.valid := false.B
  store_jm1_km1.io.deq    <> fifth_delay.io.enq
  fifth_delay.io.deq.ready   := false.B

  store_km1_lm1.io.enq.bits := DontCare
  store_km1_lm1.io.enq.valid := false.B
  store_km1_lm1.io.deq    <> sixth_delay.io.enq
  sixth_delay.io.deq.ready := false.B


  val num1 = (dim4 - 1) - 1
  val num2 = dim4 * (dim3 - 1) - 1
  val num3 = dim4 *  dim3 * (dim2 -1) - 1

  //println("Inside PE_4D. Scala output: " + idk)
  //printf(p"\n Buffer 1 size : ${store_lp1_kp1.io.count.asUInt} ")


  // When a buffer is full, start releasing data to the next one
  when(store_lp1_kp1.io.count === num3.U){
    first_delay.io.deq <> store_kp1_jp1.io.enq
  }
  when(store_kp1_jp1.io.count === num2.U){
    second_delay.io.deq <> store_jp1_ip1.io.enq
  }
  when(store_jp1_ip1.io.count === num1.U){
    third_delay.io.deq <> store_ip1_i.io.enq
  }
  when(store_ip1_i.io.count === 1.U){
    store_ip1_i.io.deq <> store_i_im1.io.enq
    first_delay.io.deq <> store_kp1_jp1.io.enq
    second_delay.io.deq <> store_jp1_ip1.io.enq
    third_delay.io.deq <> store_ip1_i.io.enq
    //    io.buffer_ready      :=  true.B
    io.out.valid      :=  true.B
  }
  when(store_i_im1.io.count === 1.U){
    store_i_im1.io.deq <> store_im1_jm1.io.enq
  }
  when(store_im1_jm1.io.count === num1.U){
    fourth_delay.io.deq <> store_jm1_km1.io.enq
  }
  when(store_jm1_km1.io.count === num2.U) {
    fifth_delay.io.deq <> store_km1_lm1.io.enq
  }
  when(store_km1_lm1.io.count === num3.U) {
    sixth_delay.io.deq.ready := true.B
  }
//  io.enq_ready := store_lp1_kp1.io.enq.ready
}

object shift_buffer4d extends App {
  chisel3.Driver.execute(args, () => new shift_buffer4d(32, 27, 60, 60,
    20, 36))
}