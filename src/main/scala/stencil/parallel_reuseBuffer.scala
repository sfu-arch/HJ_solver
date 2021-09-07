package stencil

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint

class parallel_reuseBuffer(bit_width: Int, mantissa_width: Int, dim1: Int, dim2: Int,
                            dim3: Int, dim4: Int, N: Int)
  extends Module{
  val io = IO(new Bundle{
    val in = Flipped(Decoupled(Vec(4, FixedPoint(bit_width.W, mantissa_width.BP))))
    val outDMA_ready = Input(Bool()) // stall or not
    // All the accessible data to PEs
    val out        = Decoupled(Vec(30, FixedPoint(bit_width.W, mantissa_width.BP)))
  })
  val buffer1 = Module(new top_q4d(bit_width, mantissa_width, dim1, dim2, dim3, dim4, N))
  val buffer2 = Module(new middle_q4d(bit_width, mantissa_width, dim1, dim2, dim3, dim4, N))
  val buffer3 = Module(new middle_q4d(bit_width, mantissa_width, dim1, dim2, dim3, dim4, N))
  val buffer4 = Module(new bottom_q4d(bit_width, mantissa_width, dim1, dim2, dim3, dim4, N))

  // Connect the Input Bits
  buffer1.io.in.bits := io.in.bits(0)
  buffer2.io.in.bits := io.in.bits(1)
  buffer3.io.in.bits := io.in.bits(2)
  buffer4.io.in.bits := io.in.bits(3)

  // Incoming valid signal is shared among the buffers
  buffer1.io.in.valid := io.in.valid
  buffer2.io.in.valid := io.in.valid
  buffer3.io.in.valid := io.in.valid
  buffer4.io.in.valid := io.in.valid

  // Ready signal -- only ready when each 4 component is ready
  io.in.ready   := buffer1.io.in.ready & buffer2.io.in.ready & buffer3.io.in.ready & buffer4.io.in.ready
  /*printf(p"\n buffer1 ready : ${buffer1.io.in.ready} ")
  printf(p"\n buffer2 ready : ${buffer2.io.in.ready} ")
  printf(p"\n buffer3 ready : ${buffer3.io.in.ready} ")
  printf(p"\n buffer4 ready : ${buffer4.io.in.ready} ") */

  buffer1.io.out.ready := io.out.ready
  buffer2.io.out.ready := io.out.ready
  buffer3.io.out.ready := io.out.ready
  buffer4.io.out.ready := io.out.ready

  // Connect  outDMA ready signal
  buffer1.io.outDMA_ready := io.outDMA_ready
  buffer2.io.outDMA_ready := io.outDMA_ready
  buffer3.io.outDMA_ready := io.outDMA_ready
  buffer4.io.outDMA_ready := io.outDMA_ready

  // Connect the output result
  var count = 0
  for(i <- 0 until 8){
    io.out.bits(count) := buffer1.io.out.bits(i)
    count = count + 1
  } // count = 8
  for(j <- 0 until 7) {
    io.out.bits(count)  := buffer2.io.out.bits(j)
    count = count + 1
  } // count = 15
  for(j <- 0 until 7) {
    io.out.bits(count)  := buffer3.io.out.bits(j)
    count = count + 1
  } // count = 22
  for(j <- 0 until 8) {
    io.out.bits(count)  := buffer4.io.out.bits(j)
    count = count + 1
  } // count  = 30

  io.out.valid := buffer1.io.out.valid & buffer2.io.out.valid & buffer3.io.out.valid & buffer4.io.out.valid
}

// N-PE
class middle_q4d(bit_width: Int, mantissa_width: Int, dim1: Int, dim2: Int,
                 dim3: Int, dim4: Int, N: Int)
  extends Module{

  val io = IO(new Bundle{
    val in = Flipped(Decoupled(FixedPoint(bit_width.W, mantissa_width.BP)))
    val outDMA_ready = Input(Bool())
    // All the accessible data to PEs
    val out        = Decoupled(Vec(7, FixedPoint(bit_width.W, mantissa_width.BP)))
  })

  // Queues
  val store_ip1_jp1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)/N-1, true)) //bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)-1, true))
  val first_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_jp1_kp1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim3-1)*dim4/N-1, true))
  val second_delay   = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_kp1_l    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), dim4/N - 1 , true))
  val third_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_l_km1    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), dim4/N - 1 , true))
  val fourth_delay   = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_km1_jm1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim3-1)*dim4/N - 1, true))
  val fifth_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_jm1_im1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)/N-1, true))
  val sixth_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))


  /************* Connect the IOs to the queues ************/
  // Connect data bits and valid bits
  //  io.out_data(0)   := io.enq_data
  io.out.bits(0)   := io.in.bits
  io.out.bits(1)   := first_delay.io.deq.bits
  io.out.bits(2)   := second_delay.io.deq.bits
  io.out.bits(3)   := third_delay.io.deq.bits
  io.out.bits(4)   := fourth_delay.io.deq.bits
  io.out.bits(5)   := fifth_delay.io.deq.bits
  io.out.bits(6)   := sixth_delay.io.deq.bits

  /************ Connect these queues together ***************/
  // Connect their valid signal and content data together
  //  io.buffer_ready             := false.B
  io.out.valid             := false.B

  //  store_lp1_kp1.io.enq.bits   := io.enq_data
  //  store_lp1_kp1.io.enq.valid  := io.enq_val
  store_ip1_jp1.io.enq <> io.in
  store_ip1_jp1.io.deq <> first_delay.io.enq
  first_delay.io.deq.ready  := false.B

  store_jp1_kp1.io.enq.valid := false.B
  store_jp1_kp1.io.enq.bits  := DontCare
  store_jp1_kp1.io.deq  <> second_delay.io.enq
  second_delay.io.deq.ready := false.B

  store_kp1_l.io.enq.valid := false.B
  store_kp1_l.io.enq.bits  := DontCare
  store_kp1_l.io.deq <> third_delay.io.enq
  third_delay.io.deq.ready := false.B

  store_l_km1.io.enq.valid := false.B
  store_l_km1.io.enq.bits  := DontCare
  store_l_km1.io.deq    <> fourth_delay.io.enq
  fourth_delay.io.deq.ready  := false.B

  store_km1_jm1.io.enq.bits := DontCare
  store_km1_jm1.io.enq.valid := false.B
  store_km1_jm1.io.deq    <> fifth_delay.io.enq
  fifth_delay.io.deq.ready   := false.B

  store_jm1_im1.io.enq.bits := DontCare
  store_jm1_im1.io.enq.valid := false.B
  store_jm1_im1.io.deq    <> sixth_delay.io.enq
  sixth_delay.io.deq.ready := false.B


  val num1 = dim4/N - 1
  val num2 = dim4 * (dim3 - 1)/N - 1
  val num3 = dim4 *  dim3 * (dim2 -1)/N - 1

  // When a buffer is full, start releasing data to the next one
  first_delay.io.deq <> store_jp1_kp1.io.enq
  second_delay.io.deq <> store_kp1_l.io.enq

  val fillBuff123 :: state1 :: Nil = Enum(2)
  val state = RegInit(fillBuff123)

  switch(state){
    is(fillBuff123){
        when(store_kp1_l.io.count === num1.U && store_jp1_kp1.io.count === num2.U && store_ip1_jp1.io.count === num3.U){
        third_delay.io.deq <> store_l_km1.io.enq
        // Enough data for PDE solver to start
        io.out.valid := true.B
        state := state1
      }
    }
    is(state1){
      third_delay.io.deq <> store_l_km1.io.enq
      io.out.valid := true.B

      when(store_l_km1.io.count === num1.U){
        fourth_delay.io.deq <> store_km1_jm1.io.enq
      }
      when(store_km1_jm1.io.count === num2.U) {
        fifth_delay.io.deq <> store_jm1_im1.io.enq
      }
      when(store_jm1_im1.io.count === num3.U) { // the whole buffer line is filled
        sixth_delay.io.deq.ready := true.B
      }
      /*when(store_kp1_l.io.count === 0.U){
        state := fillBuff123
      }*/
      when(io.outDMA_ready === false.B){
        // Stall all the shifting buffer
        third_delay.io.deq.ready := false.B
        store_l_km1.io.enq.valid := false.B // Really need to stall the subsequent component
        fourth_delay.io.deq.ready := false.B
        store_km1_jm1.io.enq.valid := false.B // Really need to stall the subsequent component
        fifth_delay.io.deq.ready := false.B
        store_jm1_im1.io.enq.valid := false.B
        sixth_delay.io.deq.ready := false.B
      }
      //printf(p"\n store_l_km1.count : ${store_l_jm1.io.count} ")
      //printf(p"\n FourthDeq sig : ${fourth_delay.io.deq.ready} ")
      //printf(p"\n store_km1_jm1.count : ${store_km1_jm1.io.count} ")
      //printf(p"\n Dequeue sig : ${fifth_delay.io.deq.ready} ")
      //printf(p"\n store_jm1_im1.count : ${store_jm1_im1.io.count} ")
      //printf(p"\n Sixth delay.count : ${sixth_delay.io.count} ")

      when(third_delay.io.count === 0.U){
        state := fillBuff123
      }
    }

  }


  //  io.enq_ready := store_lp1_kp1.io.enq.ready
}

class top_q4d(bit_width: Int, mantissa_width: Int, dim1: Int, dim2: Int,
              dim3: Int, dim4: Int, N: Int)
  extends Module{

  val io = IO(new Bundle{
    val in = Flipped(Decoupled(FixedPoint(bit_width.W, mantissa_width.BP)))
    val outDMA_ready = Input(Bool())
    // All the accessible data to PEs
    val out        = Decoupled(Vec(8, FixedPoint(bit_width.W, mantissa_width.BP)))
  })

  // Queues
  val store_ip1_jp1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)/N-1, true)) //bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)-1, true))
  val first_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_jp1_kp1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim3-1)*dim4/N-1, true))
  val second_delay   = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_kp1_lp4    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim4/N - 1)-1 , true))
  val third_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_lp4_l    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1 , true))

  val store_l_km1    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), dim4/N - 1 , true))
  val fourth_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_km1_jm1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim3-1)*dim4/N - 1, true))
  val fifth_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_jm1_im1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)/N-1, true))
  val sixth_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))


  /************* Connect the IOs to the queues ************/
  // Connect data bits and valid bits
  //  io.out_data(0)   := io.enq_data
  io.out.bits(0)   := io.in.bits
  io.out.bits(1)   := first_delay.io.deq.bits
  io.out.bits(2)   := second_delay.io.deq.bits
  io.out.bits(3)   := third_delay.io.deq.bits
  io.out.bits(4)   := store_lp4_l.io.deq.bits
  io.out.bits(5)   := fourth_delay.io.deq.bits
  io.out.bits(6)   := fifth_delay.io.deq.bits
  io.out.bits(7)   := sixth_delay.io.deq.bits

  /************ Connect these queues together ***************/
  // Connect their valid signal and content data together
  //  io.buffer_ready             := false.B
  io.out.valid             := false.B

  //  store_lp1_kp1.io.enq.bits   := io.enq_data
  //  store_lp1_kp1.io.enq.valid  := io.enq_val
  store_ip1_jp1.io.enq <> io.in
  store_ip1_jp1.io.deq <> first_delay.io.enq
  first_delay.io.deq.ready  := false.B

  store_jp1_kp1.io.enq.valid := false.B
  store_jp1_kp1.io.enq.bits  := DontCare
  store_jp1_kp1.io.deq  <> second_delay.io.enq
  second_delay.io.deq.ready := false.B

  store_kp1_lp4.io.enq.valid := false.B
  store_kp1_lp4.io.enq.bits  := DontCare
  store_kp1_lp4.io.deq <> third_delay.io.enq
  third_delay.io.deq.ready := false.B

  store_lp4_l.io.enq.valid := false.B
  store_lp4_l.io.enq.bits  := DontCare
  store_lp4_l.io.deq.ready := false.B

  store_l_km1.io.enq.valid := false.B
  store_l_km1.io.enq.bits  := DontCare
  store_l_km1.io.deq    <> fourth_delay.io.enq
  fourth_delay.io.deq.ready  := false.B

  store_km1_jm1.io.enq.bits := DontCare
  store_km1_jm1.io.enq.valid := false.B
  store_km1_jm1.io.deq    <> fifth_delay.io.enq
  fifth_delay.io.deq.ready   := false.B

  store_jm1_im1.io.enq.bits := DontCare
  store_jm1_im1.io.enq.valid := false.B
  store_jm1_im1.io.deq    <> sixth_delay.io.enq
  sixth_delay.io.deq.ready := false.B


  val num1 = dim4/N - 1
  val num2 = dim4 * (dim3 - 1)/N - 1
  val num3 = dim4 *  dim3 * (dim2 -1)/N - 1

  // When a buffer is full, start forwarding data to the next one
  first_delay.io.deq <> store_jp1_kp1.io.enq
  second_delay.io.deq <> store_kp1_lp4.io.enq
  //when(store_kp1_lp4.io.count === (num1-1).U){
  third_delay.io.deq <> store_lp4_l.io.enq
  //}

  val fillBuff1234 :: state1 :: Nil = Enum(2)
  val state = RegInit(fillBuff1234)

  switch(state){
    is(fillBuff1234){
        when(store_ip1_jp1.io.count === num3.U){
          store_lp4_l.io.deq <> store_l_km1.io.enq
        io.out.valid := true.B
        state := state1
      }
    }
    is(state1){
      store_lp4_l.io.deq <> store_l_km1.io.enq
      io.out.valid := true.B

      when(store_l_km1.io.count === num1.U){
        fourth_delay.io.deq <> store_km1_jm1.io.enq
      }
      when(store_km1_jm1.io.count === num2.U) {
        fifth_delay.io.deq <> store_jm1_im1.io.enq
      }
      when(store_jm1_im1.io.count === num3.U) {
        sixth_delay.io.deq.ready := true.B
      }
      /*when(third_delay.io.count === 0.U){
        state := fillBuff1234
      }*/
      when(io.outDMA_ready === false.B){
        // Stall all the shifting buffer
        store_lp4_l.io.deq.ready := false.B
        store_l_km1.io.enq.valid := false.B // Required to stall subseq component
        fourth_delay.io.deq.ready := false.B
        store_km1_jm1.io.enq.valid := false.B
        fifth_delay.io.deq.ready := false.B
        store_jm1_im1.io.enq.valid := false.B
        sixth_delay.io.deq.ready := false.B
      }

      when(store_lp4_l.io.count === 0.U) {
        state := fillBuff1234
      }
    }
  }


//  when(store_lp4_l.io.count === 1.U){
//    store_lp4_l.io.deq <> store_l_km1.io.enq
//    io.out.valid := true.B
//  }
//  when(store_l_km1.io.count === num1.U){
//    fourth_delay.io.deq <> store_km1_jm1.io.enq
//  }
//  when(store_km1_jm1.io.count === num2.U) {
//    fifth_delay.io.deq <> store_jm1_im1.io.enq
//  }
//  when(store_jm1_im1.io.count === num3.U) {
//    sixth_delay.io.deq.ready := true.B
//  }
  //  io.enq_ready := store_lp1_kp1.io.enq.ready
}

class bottom_q4d(bit_width: Int, mantissa_width: Int, dim1: Int, dim2: Int,
                 dim3: Int, dim4: Int, N: Int)
  extends Module{

  val io = IO(new Bundle{
    val in = Flipped(Decoupled(FixedPoint(bit_width.W, mantissa_width.BP)))
    val outDMA_ready = Input(Bool())
    // All the accessible data to PEs
    val out        = Decoupled(Vec(8, FixedPoint(bit_width.W, mantissa_width.BP)))
  })

  // Queues
  val store_ip1_jp1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)/N-1, true)) //bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)-1, true))
  val first_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_jp1_kp1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim3-1)*dim4/N-1, true))
  val second_delay   = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_kp1_l    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim4/N)-1 , true))
  val third_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_l_lm1    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1 , true))

  val store_lm1_km1    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim4/N -1) - 1 , true))
  val fourth_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_km1_jm1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), (dim3-1)*dim4/N - 1, true))
  val fifth_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))

  val store_jm1_im1  = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), dim4*dim3*(dim2-1)/N-1, true))
  val sixth_delay    = Module(new Queue(FixedPoint(bit_width.W, mantissa_width.BP), 1, true))


  /************* Connect the IOs to the queues ************/
  // Connect data bits and valid bits
  //  io.out_data(0)   := io.enq_data
  io.out.bits(0)   := io.in.bits
  io.out.bits(1)   := first_delay.io.deq.bits
  io.out.bits(2)   := second_delay.io.deq.bits
  io.out.bits(3)   := third_delay.io.deq.bits
  io.out.bits(4)   := store_l_lm1.io.deq.bits
  io.out.bits(5)   := fourth_delay.io.deq.bits
  io.out.bits(6)   := fifth_delay.io.deq.bits
  io.out.bits(7)   := sixth_delay.io.deq.bits

  /************ Connect these queues together ***************/
  // Connect their valid signal and content data together
  //  io.buffer_ready             := false.B
  io.out.valid             := false.B

  //  store_lp1_kp1.io.enq.bits   := io.enq_data
  //  store_lp1_kp1.io.enq.valid  := io.enq_val
  store_ip1_jp1.io.enq <> io.in
  store_ip1_jp1.io.deq <> first_delay.io.enq
  first_delay.io.deq.ready  := false.B

  store_jp1_kp1.io.enq.valid := false.B
  store_jp1_kp1.io.enq.bits  := DontCare
  store_jp1_kp1.io.deq  <> second_delay.io.enq
  second_delay.io.deq.ready := false.B

  store_kp1_l.io.enq.valid := false.B
  store_kp1_l.io.enq.bits  := DontCare
  store_kp1_l.io.deq <> third_delay.io.enq
  third_delay.io.deq.ready := false.B

  store_l_lm1.io.enq.valid := false.B
  store_l_lm1.io.enq.bits  := DontCare
  store_l_lm1.io.deq.ready := false.B

  store_lm1_km1.io.enq.valid := false.B
  store_lm1_km1.io.enq.bits  := DontCare
  store_lm1_km1.io.deq    <> fourth_delay.io.enq
  fourth_delay.io.deq.ready  := false.B

  store_km1_jm1.io.enq.bits := DontCare
  store_km1_jm1.io.enq.valid := false.B
  store_km1_jm1.io.deq    <> fifth_delay.io.enq
  fifth_delay.io.deq.ready   := false.B

  store_jm1_im1.io.enq.bits := DontCare
  store_jm1_im1.io.enq.valid := false.B
  store_jm1_im1.io.deq    <> sixth_delay.io.enq
  sixth_delay.io.deq.ready := false.B


  val num1 = (dim4/N -1) - 1
  val num2 = dim4 * (dim3 - 1)/N - 1
  val num3 = dim4 *  dim3 * (dim2 -1)/N - 1

  // When a buffer is full, let that buffer forward data to the next one
  //when(store_ip1_jp1.io.count === num3.U){
  first_delay.io.deq <> store_jp1_kp1.io.enq
  //}
  //when(store_jp1_kp1.io.count === num2.U){
  second_delay.io.deq <> store_kp1_l.io.enq
  val fillBuff1234 :: state1 :: Nil = Enum(2)
  val state = RegInit(fillBuff1234)

  switch(state) {
    is(fillBuff1234) {
      when(store_ip1_jp1.io.count === num3.U) {
        third_delay.io.deq <> store_l_lm1.io.enq
        io.out.valid := true.B
        state := state1
      }
    }
    is(state1){
      third_delay.io.deq <> store_l_lm1.io.enq
      io.out.valid := true.B

      when(store_l_lm1.io.count === 1.U){
        store_l_lm1.io.deq <> store_lm1_km1.io.enq
      }
      when(store_lm1_km1.io.count === num1.U){
        fourth_delay.io.deq <> store_km1_jm1.io.enq
      }
      when(store_km1_jm1.io.count === num2.U) {
        fifth_delay.io.deq <> store_jm1_im1.io.enq
      }
      when(store_jm1_im1.io.count === num3.U) {
        sixth_delay.io.deq.ready := true.B
      }
      /*when(store_kp1_l.io.count === 0.U){
        state := fillBuff1234
      }*/
      when(io.outDMA_ready === false.B){
        // Stall all the shifting buffer
        third_delay.io.deq.ready := false.B
        store_l_lm1.io.deq.ready := false.B
        store_lm1_km1.io.enq.valid := false.B
        fourth_delay.io.deq.ready := false.B
        store_km1_jm1.io.enq.valid := false.B
        fifth_delay.io.deq.ready := false.B
        store_jm1_im1.io.enq.valid := false.B
        sixth_delay.io.deq.ready := false.B
      }
      when(third_delay.io.count === 0.U){
        state := fillBuff1234
      }
    }
  }

  //  io.enq_ready := store_lp1_kp1.io.enq.ready
}