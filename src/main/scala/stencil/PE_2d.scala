package stencil

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

// Let's assume that we're writing this for 50 x 50 grid, assume it's 2D for now
// Reuse buffer would then be 2*50 + 1 = 101

class stencil_compute(bit_width: Int)
  extends Module {
  val io = IO(new Bundle{
    val valid_sig     = Input(Bool())
    val enq_data      = Input(UInt(bit_width.W))
    val input_read    = Input(Bool()) // start signal
    val buffers_ready = Output(Bool())
    val result_data   = Output(UInt(bit_width.W))
    val done          = Output(Bool())
    val result_valid  = Output(Bool())
  })

  // call the 2 main components
  val mem_buffer      = Module(new shift_buffer(bit_width, 4))
  val my_PE              = Module(new myPE(bit_width, 4, 4))

  // Connect mem_buffer output to PE, not using valid signals
  for(i <- 0 to 4){
    my_PE.io.grid_points(i) := mem_buffer.io.out_data(i)
  }
  // Signal to control the PE
  val PE_start       = Wire(Bool())
  PE_start          := false.B
  my_PE.io.start_PE    := PE_start

  // Results of this module is basically the PE's
  io.result_data         := my_PE.io.f_result
  io.result_valid        := my_PE.io.valid_result
  io.done                := my_PE.io.done

  mem_buffer.io.enq_val   := io.valid_sig
  mem_buffer.io.enq_data  := io.enq_data

  // Output signals
  io.buffers_ready        := mem_buffer.io.enq_ready

  // States of control
  val idle :: loadBuff1 :: finish :: Nil = Enum(3)
  val state = RegInit(idle)

  // Finite state machine description
  switch(state){
    is(idle){
      state := idle
      when(io.input_read){
        state := loadBuff1
      }
    }
    is(loadBuff1){
      //start_counter  := true.B
      when(mem_buffer.io.capacity_count(1) === 1.U){
        PE_start        := true.B
      }
      io.result_data         := my_PE.io.f_result
      state := loadBuff1
      when(my_PE.io.done){
        state := finish
      }
    }
    is(finish){
      state := finish
    }
  }

  // Now control the ready signals of mem buffer
}

// With this shift buffer we can access all the points in one cycle
class shift_buffer(bit_width: Int, dim_size: Int)
  extends Module{

  val io = IO(new Bundle{
    //val ready_sigs      = Input(Vec(5, Bool()))
    val enq_data        = Input(UInt(bit_width.W))
    val enq_val         = Input(Bool())
    // All the accessible data to PEs
    val out_data        = Output(Vec(5, UInt(bit_width.W)))
    val out_valids      = Output(Vec(5, Bool()))
    val capacity_count  = Output(Vec(4, UInt(log2Ceil(dim_size).W)))
    val enq_ready       = Output(Bool())
  })

  // Queues
  val store_101th_to_52         = Module(new Queue(UInt(bit_width.W), dim_size-1, true))
  val store_51                  = Module(new Queue(UInt(bit_width.W), 1, true))
  val store_50                  = Module(new Queue(UInt(bit_width.W), 1 , true))
  val store_49th_to_1           = Module(new Queue(UInt(bit_width.W), dim_size-1, true))

  /************* Connect the IOs to the queues ************/
  // Connect data bits and valid bits
  io.out_data(0)   := io.enq_data
  io.out_data(1)   := store_101th_to_52.io.deq.bits
  io.out_data(2)   := store_51.io.deq.bits
  io.out_data(3)   := store_50.io.deq.bits
  io.out_data(4)   := store_49th_to_1.io.deq.bits
  io.out_valids(0) := io.enq_val
  io.out_valids(1) := store_101th_to_52.io.deq.valid
  io.out_valids(2) := store_51.io.deq.valid
  io.out_valids(3) := store_50.io.deq.valid
  io.out_valids(4) := store_49th_to_1.io.deq.valid


  /************ Connect these queues together ***************/
  // Connect their valid signal and content data together
  store_101th_to_52.io.enq.bits   := io.enq_data
  store_101th_to_52.io.enq.valid  := io.enq_val

  store_101th_to_52.io.deq.ready := false.B
  store_51.io.enq.valid := false.B
  store_51.io.enq.bits  := DontCare
  store_51.io.deq.ready := false.B

  store_50.io.enq.valid := false.B
  store_50.io.enq.bits  := DontCare
  store_50.io.deq.ready := false.B

  store_49th_to_1.io.enq.bits := DontCare
  store_49th_to_1.io.enq.valid := false.B

  store_49th_to_1.io.deq.ready := false.B
  when(store_101th_to_52.io.count === 3.U){
    store_101th_to_52.io.deq <> store_51.io.enq
  }
  when(store_51.io.count === 1.U){
    store_51.io.deq <> store_50.io.enq
  }
  when(store_50.io.count === 1.U){
    store_50.io.deq <> store_49th_to_1.io.enq
  }
  when(store_49th_to_1.io.count === 3.U){
    store_49th_to_1.io.deq.ready := true.B
  }

  // Connect capacity count to the outside for full checking
  io.capacity_count(0) := store_101th_to_52.io.count
  io.capacity_count(1) := store_51.io.count
  io.capacity_count(2) := store_50.io.count
  io.capacity_count(3) := store_49th_to_1.io.count

  // Enqueue output
  //io.enq_ready := io.ready_sigs(0) && store_101th_to_52.io.enq.ready
  io.enq_ready := store_101th_to_52.io.enq.ready

}


// This PE computes sum of derivative at every grid of a 2D array
// also takes care of the halo (boundary)
// Assume unsigned integers for now
class myPE(bit_width: Int, x_size: Int, y_size: Int)
  extends Module
{
  val io = IO(new Bundle{
    val grid_points   = Input(Vec(5, UInt(bit_width.W)))
    val start_PE      = Input(Bool()) // Control signal - should I start?
    val f_result      = Output(UInt(bit_width.W))
    val valid_result  = Output(Bool())
    val done          = Output(Bool())
  })

  // Not counting by default
  val start_count_i = Wire(Bool())
  val start_count_j = Wire(Bool())
  start_count_i     := false.B
  start_count_j     := false.B

  // Counter components -- Note that these counter starts at 0
  val (i_count, wrap_i) = Counter(start_count_i, x_size)
  val (j_count, wrap_j) = Counter(start_count_j, y_size)

  // Output of the PE
  io.done := wrap_i && wrap_j

  // Registers holding the input value
  val g_point = Reg(Vec(5, UInt(bit_width.W)))
  val enables = Wire(Vec(5, Bool()))
  for (i <- 0 to 4){
    enables(i) := false.B // Not loading by default
    when(enables(i))
    {
      g_point(i) := io.grid_points(i)
    }
  }
  // States of the machine
  val wait_data :: compute :: done :: Nil = Enum(3)
  val state = RegInit(wait_data)

  // Nested loop condition
  when(wrap_i) // i_count == x_size - 1
  {
    start_count_j := true.B
  }

  // The index conditions, to check boundaries
  val i_is_0       = Wire(Bool())
  val i_is_X_min_1 = Wire(Bool())
  val j_is_0       = Wire(Bool())
  val j_is_Y_min_1 = Wire(Bool())

  i_is_0       :=  (i_count === 0.U)
  i_is_X_min_1 := (i_count === (x_size - 1).U)
  j_is_0       :=  (j_count === 0.U)
  j_is_Y_min_1 := (j_count === (y_size - 1).U)

  //Intermediate results
  val dV_dx       = Wire(UInt(bit_width.W))
  val dV_dy       = Wire(UInt(bit_width.W))
  val ghost_point = Wire(UInt(bit_width.W))
  val result_fuck = Wire(UInt(bit_width.W))

  ghost_point     := 5.U
  dV_dx           := DontCare
  dV_dy           := DontCare
  result_fuck     := DontCare
  io.valid_result := false.B
  io.f_result     := result_fuck


  // Core computation
  switch(state)
  {
    is(wait_data){
      when(io.start_PE)
      {
        // in the next state, data starts flowing for computation
        state := compute
        for(i <- 0 to 4){
          enables(i) := true.B
        }
      }.otherwise{
        state := wait_data
      }
    }
    is(compute){ // Data is in
      // start counter i now
      for(i <- 0 to 4){
        enables(i) := true.B
      }
      start_count_i := true.B
      // Output logic
      when(!i_is_0 && !j_is_0 && !i_is_X_min_1 && !j_is_Y_min_1) {
        //dV_dx          := g_point(1) - g_point(3)
        //dV_dy          := g_point(0) - g_point(4)
        dV_dx            := g_point(1) + g_point(3)
        dV_dy            := g_point(0) + g_point(4)
        //result         := dV_dy + dV_dx
      }.otherwise{
        //dV_dx          := g_point(2) + ghost_point
        //dV_dy          := g_point(2) - ghost_point
        dV_dx            := g_point(2)
        dV_dy            := g_point(2)
      }
      result_fuck         := dV_dx + dV_dy
      io.valid_result := true.B
      // Next state logic
      when(wrap_j && wrap_i){
        state := done
      }
    }
    is(done)
    {
      start_count_i := false.B
      start_count_j := false.B
      io.valid_result := false.B
      state := done
    }
  }
}

class myPE_float(bit_width: Int, x_size: Int, y_size: Int)
  extends Module
{
  val io = IO(new Bundle{
    val grid_points   = Input(Vec(5, UInt(bit_width.W)))
    val start_PE      = Input(Bool()) // Control signal - should I start?
    val f_result      = Output(UInt(bit_width.W))
    val i_val         = Output(UInt(10.W))
    val j_val         = Output(UInt(10.W))
    val valid_result  = Output(Bool())
    val done          = Output(Bool())
  })

  // Not counting by default
  val start_count_i = Wire(Bool())
  val start_count_j = Wire(Bool())
  start_count_i     := false.B
  start_count_j     := false.B

  // Counter components -- Note that these counter starts at 0
  val (i_count, wrap_i) = Counter(start_count_i, x_size)
  val (j_count, wrap_j) = Counter(start_count_j, y_size)

  io.i_val := i_count
  io.j_val := j_count
  // Output of the PE
  io.done := wrap_i && wrap_j

  // Registers holding the input value
  val g_point = Reg(Vec(5, UInt(bit_width.W)))
  val enables = Wire(Vec(5, Bool()))
  for (i <- 0 to 4){
    enables(i) := false.B // Not loading by default
    when(enables(i))
    {
      g_point(i) := io.grid_points(i)
    }
  }
  // States of the machine
  val wait_data :: compute :: done :: Nil = Enum(3)
  val state = RegInit(wait_data)

  // Nested loop condition
  when(wrap_i) // i_count == x_size - 1
  {
    start_count_j := true.B
  }

  // The index conditions, to check boundaries
  val i_is_0       = Wire(Bool())
  val i_is_X_min_1 = Wire(Bool())
  val j_is_0       = Wire(Bool())
  val j_is_Y_min_1 = Wire(Bool())

  i_is_0       :=  (i_count === 0.U)
  i_is_X_min_1 := (i_count === (x_size - 1).U)
  j_is_0       :=  (j_count === 0.U)
  j_is_Y_min_1 := (j_count === (y_size - 1).U)

  //Intermediate results
  val dV_dx       = Wire(UInt(bit_width.W))
  val dV_dy       = Wire(UInt(bit_width.W))
  val ghost_point = Wire(UInt(bit_width.W))
  val result_fuck = Wire(UInt(bit_width.W))

  ghost_point     := 5.U
  dV_dx           := DontCare
  dV_dy           := DontCare
  result_fuck     := DontCare
  io.valid_result := false.B
  io.f_result     := result_fuck


  // Core computation
  switch(state)
  {
    is(wait_data){
      when(io.start_PE)
      {
        // in the next state, data starts flowing for computation
        state := compute
        for(i <- 0 to 4){
          enables(i) := true.B
        }
      }.otherwise{
        state := wait_data
      }
    }
    is(compute){ // Data is in
      // start counter i now
      for(i <- 0 to 4){
        enables(i) := true.B
      }
      start_count_i := true.B
      // Output logic
      when(!i_is_0 && !j_is_0 && !i_is_X_min_1 && !j_is_Y_min_1) {
        //dV_dx          := g_point(1) - g_point(3)
        //dV_dy          := g_point(0) - g_point(4)
        dV_dx            := g_point(1) + g_point(3)
        dV_dy            := g_point(0) + g_point(4)
        //result         := dV_dy + dV_dx
      }.otherwise{
        //dV_dx          := g_point(2) + ghost_point
        //dV_dy          := g_point(2) - ghost_point
        dV_dx            := g_point(2)
        dV_dy            := g_point(2)
      }
      result_fuck         := dV_dx + dV_dy
      io.valid_result := true.B
      // Next state logic
      when(wrap_j && wrap_i){
        state := done
      }
    }
    is(done)
    {
      start_count_i := false.B
      start_count_j := false.B
      io.valid_result := false.B
      state := done
    }
  }
}








