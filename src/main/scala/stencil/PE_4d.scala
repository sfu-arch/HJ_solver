package stencil

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint

// generate verilog code
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

/*
  THIS IS FULL PIPELINED
 */


class SpatialDeriv_FirstOrder( delta_inv: Double, loop_bound: Int,
                               bit_width: Int, mantissa_width : Int)
  extends Module{
  val io = IO(new Bundle{
    val mid_point     = Input(FixedPoint(bit_width.W, mantissa_width.BP))
    val left_point    = Input(FixedPoint(bit_width.W, mantissa_width.BP))
    val right_point   = Input(FixedPoint(bit_width.W, mantissa_width.BP))
    val index_count   = Input(UInt(log2Ceil(loop_bound).W))
    val outDMA_ready  = Input(Bool())
    // Derivative at mid-point
    val result        = Output(FixedPoint(bit_width.W, mantissa_width.BP))
    // Dissipation constituded by this component
    val dissipate     = Output(FixedPoint(bit_width.W, mantissa_width.BP))
  })

  val Vx_abs_diffR  = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val Vx_abs_diffL  = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val V_x_L_ghost_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val V_x_R_ghost_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val V_x_L_ghost   = RegEnable(V_x_L_ghost_wire, io.outDMA_ready) // pipelining
  val V_x_R_ghost   = RegEnable(V_x_R_ghost_wire, io.outDMA_ready) // pipelining
  val m_point       = RegEnable(io.mid_point, io.outDMA_ready)

  /********** STAGE 1  : Calculate left, right ghost point *********/
  // abs(A[MID] - A[MID + 1])
  when(io.right_point > io.mid_point){
    Vx_abs_diffR := io.right_point - io.mid_point
  }.otherwise{
    Vx_abs_diffR:= io.mid_point - io.right_point
  }
  // abs(A[MID] - A[MID - 1])
  when(io.left_point > io.mid_point){
    Vx_abs_diffL := io.left_point - io.mid_point
  }.otherwise{
    Vx_abs_diffL := io.mid_point - io.left_point
  }
  // mid_point + abs(mid_point) * abs_diff
  when(io.mid_point > 0.F(bit_width.W, mantissa_width.BP)){
    V_x_L_ghost_wire   := io.mid_point + Vx_abs_diffR
    V_x_R_ghost_wire   := io.mid_point + Vx_abs_diffL
  }.elsewhen(io.mid_point < 0.F(bit_width.W, mantissa_width.BP)){
    V_x_L_ghost_wire   := io.mid_point - Vx_abs_diffR
    V_x_R_ghost_wire   := io.mid_point - Vx_abs_diffL
  }.otherwise{
    V_x_L_ghost_wire   := 0.F(bit_width.W, mantissa_width.BP)
    V_x_R_ghost_wire   := 0.F(bit_width.W, mantissa_width.BP)
  }

  //m_point         := io.mid_point

  // Propagates A[mid - 1], A[mid + 1] to stage 2
  val V_xminus1     = RegEnable(io.left_point, io.outDMA_ready)
  val V_xplus1      = RegEnable(io.right_point, io.outDMA_ready)

  //V_xminus1         := io.left_point
  //V_xplus1          := io.right_point

  // At boundary yet? if yes, which boundary?
  val index_is_0_wire       = Wire(Bool())
  val index_is_min_1_wire   = Wire(Bool())
  val index_is_0            = RegEnable(index_is_0_wire, io.outDMA_ready)
  val index_is_min_1        = RegEnable(index_is_min_1_wire, io.outDMA_ready)

  index_is_0_wire          := (io.index_count === 0.U)
  index_is_min_1_wire      := (io.index_count === (loop_bound-1).U)

  /***************************** STAGE 2 *****************************/

  val V_x_L         = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val V_x_R         = Wire(FixedPoint(bit_width.W, mantissa_width.BP))

  // Initially we don't care what these values are
  V_x_L             := 0.F(bit_width.W, mantissa_width.BP)
  V_x_R             := 0.F(bit_width.W, mantissa_width.BP)

  //
  when(!index_is_0 && !index_is_min_1) {
    V_x_L := V_xminus1
    V_x_R := V_xplus1
  }.elsewhen(index_is_min_1){
    V_x_L := V_xminus1
    V_x_R := V_x_R_ghost
  }.elsewhen(index_is_0){
    V_x_L := V_x_L_ghost
    V_x_R := V_xplus1
  }
  val dV_L_diff     = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val dV_R_diff     = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  dV_L_diff         := (m_point - V_x_L)
  dV_R_diff         := (V_x_R - m_point)

  //printf(p"\n V_x_L : ${V_x_L.asUInt}  V_x_R: ${V_x_R.asUInt} ")

  /***************************** STAGE 3 *****************************/
  val dV_dx_diff_wire    = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val dV_dx_diff         = RegEnable(dV_dx_diff_wire, io.outDMA_ready)
  dV_dx_diff_wire       := (V_x_R - V_x_L)

  val dV_RL_diff_wire    = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val dV_RL_diff         = RegEnable(dV_RL_diff_wire, io.outDMA_ready)
  dV_RL_diff_wire        := (dV_R_diff - dV_L_diff)


  /***************************** STAGE 4 *****************************/

  val dV_dx_stage5_wire  = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val dV_dx_stage5       = RegEnable(dV_dx_stage5_wire, io.outDMA_ready)
  dV_dx_stage5_wire     := dV_dx_diff * delta_inv.F(bit_width.W, mantissa_width.BP)  // stage 5

  val diss_stage5_wire   = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val diss_stage5        = RegEnable(diss_stage5_wire, io.outDMA_ready)
  diss_stage5_wire      := dV_RL_diff * delta_inv.F(bit_width.W, mantissa_width.BP)

  /***************************** STAGE 5 *****************************/
  val dV_dx_stage6       = RegEnable(dV_dx_stage5, io.outDMA_ready) // stage 6
  val diss_stage6        = RegEnable(diss_stage5, io.outDMA_ready)
  //diss_stage6       := diss_stage5

  /***************************** STAGE 6 *****************************/

  val dV_dx         = RegEnable(dV_dx_stage6, io.outDMA_ready)
  val diss_stage7   = RegEnable(diss_stage6, io.outDMA_ready)
  // diss_stage7       := diss_stage6

  io.result         := dV_dx
  io.dissipate      := diss_stage7
}

/* Dynamics of a Dubins Car 4D
  x_dot   = v * cos(theta)
  y_dot   = v * sin(theta)
  v_dot   = u1 (-1 <= u1 <= 1)
  theta_d = v * tan(u2)/ L
 */

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

/* Parameters to DubinsCar_PE:
  - x_size, y_size, v_size, theta_size
  - x_range, y_range, ...
  They determines:  - delta_sth_inverse
                    - Look up table (sin[theta_size] , v[v_size], ... )
*/
// This PE computes value function at every grid of a 4D array
class DubinsCar_PE( bit_width: Int, mantissa_width: Int, x_size: Int, y_size: Int,
                    v_size: Int, theta_size: Int, max_accel: Double, max_w: Double,
                    ID: Int, parallel_factor: Int, debug: Boolean = false)
  extends Module
{
  val io = IO(new Bundle{
    val in      = Flipped(Decoupled(Vec(9, FixedPoint(bit_width.W, mantissa_width.BP)))) // 4 dim * 2 points + 1 = 9
    val start   = Input(Bool())
    val outDMA_ready = Input(Bool())
    val out     = Decoupled(FixedPoint(bit_width.W, mantissa_width.BP))
    val done    = Output(Bool())
  })
  // x bound parameter
  val x_min: Double = -3.0
  val x_max: Double = 3.0
  val y_min: Double = -1.0
  val y_max: Double = 4.0
  val v_min: Double = 0.0
  val v_max: Double = 4.0
  val theta_min: Double = -math.Pi
  val theta_max: Double = math.Pi

  // Accel parameters
  val accel_max: Double = 1.5
  val accel_min: Double = -1.5

  // Grid inverse constants
  val delta_x_inverse: Double = 1/(2*(x_max-x_min)/(x_size-1))
  val delta_y_inverse: Double = 1/(2*(y_max-y_min)/(y_size-1))
  val delta_v_inverse: Double = 1/(2*(v_max-v_min)/(v_size-1))
  //val delta_theta_inverse: Double = 36/(2*math.Pi) //FixedPoint.fromDouble((18/(2*math.Pi)), bit_width.W, mantissa_width.BP)
  val delta_theta_inverse: Double = 1/(2*(theta_max - theta_min)/(theta_size - 1))

  val L: Double = 0.3
  //val max_alpha1 = alphas(0)
  //val max_alpha2 = alphas(1)
  //val max_alpha3 = alphas(2)
  //val max_alpha4 = alphas(3)
  val delta_t = 0.00749716

  def sinTable(n: Int) = {
    val times = (0 until n).map(i => (i*2*math.Pi)/(n.toDouble-1) - math.Pi)
    val inits = times.map(t => math.sin(t).F(bit_width.W, mantissa_width.BP))
    VecInit(inits)
  }

  def cosTable(n: Int) = {
    val times = (0 until n).map(i => (i*2*math.Pi)/(n.toDouble-1) - math.Pi)
    val inits = times.map(t => math.cos(t).F(bit_width.W, mantissa_width.BP))
    VecInit(inits)
  }

  def velTable(n: Int, vel_lbound: Double, vel_ubound: Double) = {
    val vels = (0 until n).map(i => i * (vel_ubound - vel_lbound)/(n.toDouble-1) + vel_lbound)
    val vel_bits = vels.map(v => v.F(bit_width.W, mantissa_width.BP))
    VecInit(vel_bits)
  }

  def theta_DotTable(n: Int, vel_lbound: Double, vel_ubound: Double, delta_control: Double) = {
    val vels = (0 until n).map(i => i * (vel_ubound - vel_lbound)/(n.toDouble-1) + vel_lbound)
    val theta_dot = vels.map(t => t*math.tan(delta_control)*1/L)
    val vel_bits = theta_dot.map(v => v.F(bit_width.W, mantissa_width.BP))
    VecInit(vel_bits)
  }

  // Lookup table
  val sin_table = sinTable(theta_size)
  val cos_table = cosTable(theta_size)
  val vel_table = velTable(v_size, v_min, v_max)
  val thetaDotTable = theta_DotTable(v_size, v_min, v_max, math.Pi/18)

  // Not counting by default
  val start_count_i = Wire(Bool())
  val start_count_j = Wire(Bool())
  val start_count_k = Wire(Bool())
  val start_count_l = Wire(Bool())

  start_count_i     := false.B
  start_count_j     := false.B
  start_count_k     := false.B
  start_count_l     := false.B

  // Counter components -- Note that these counter starts at 0
  val (state1_i_count, wrap_i) = Counter(start_count_i, x_size)
  val (state1_j_count, wrap_j) = Counter(start_count_j, y_size)
  val (state1_k_count, wrap_k) = Counter(start_count_k, v_size)
  val (state1_l_count, wrap_l) = offset_counter(start_count_l, theta_size, ID, parallel_factor)

  //printf(p"\n state1_l_count : ${state1_l_count}") //Predicate ${predicate} Left ${left_R} Right ${right_R} Output: ${data_R}")
  // Nested loop condition
  when(wrap_l && io.outDMA_ready) {
    start_count_k := true.B
  }
  when(wrap_k && io.outDMA_ready){
    start_count_j := true.B
  }
  when(wrap_j && io.outDMA_ready){
    start_count_i := true.B
  }

  // Valid signal propagated using Pipe
  /*val io_done = Module(new Pipe(Bool(), 14))
  io_done.io.enq.bits  := wrap_i && wrap_j && wrap_k && wrap_l
  io_done.io.enq.valid := true.B */

  val io_done_wire  = Wire(Bool())
  io_done_wire := wrap_i && wrap_j && wrap_k && wrap_l
  val state1_done   = RegEnable(io_done_wire, false.B, io.outDMA_ready)
  val state2_done   = RegEnable(state1_done,  false.B, io.outDMA_ready)
  val state3_done   = RegEnable(state2_done,  false.B, io.outDMA_ready)
  val state4_done   = RegEnable(state3_done,  false.B, io.outDMA_ready)
  val state5_done   = RegEnable(state4_done,  false.B, io.outDMA_ready)
  val state6_done   = RegEnable(state5_done,  false.B, io.outDMA_ready)
  val state7_done   = RegEnable(state6_done,  false.B, io.outDMA_ready)
  val state8_done   = RegEnable(state7_done,  false.B, io.outDMA_ready)
  val state9_done   = RegEnable(state8_done,  false.B, io.outDMA_ready)
  val state10_done  = RegEnable(state9_done,  false.B, io.outDMA_ready)
  val state11_done  = RegEnable(state10_done, false.B, io.outDMA_ready)
  val state12_done  = RegEnable(state11_done, false.B, io.outDMA_ready)
  val state13_done  = RegEnable(state12_done, false.B, io.outDMA_ready)
  val io_done       = RegEnable(state13_done, false.B, io.outDMA_ready)

  // Output of the PE
  io.done := false.B
  //io.done := io_done.io.deq.bits


  // These registers are used to propagate i, j, k, l values
  val state2_i_count   = RegEnable(state1_i_count, 0.U, io.outDMA_ready)
  val state2_j_count   = RegEnable(state1_j_count, 0.U, io.outDMA_ready)
  val state2_k_count   = RegEnable(state1_k_count, 0.U, io.outDMA_ready)
  val state2_l_count   = RegEnable(state1_l_count, 0.U, io.outDMA_ready)

  val i_count          = RegEnable(state2_i_count, 0.U, io.outDMA_ready)
  val j_count          = RegEnable(state2_j_count, 0.U, io.outDMA_ready)
  val k_count          = RegEnable(state2_k_count, 0.U, io.outDMA_ready)
  val l_count          = RegEnable(state2_l_count, 0.U, io.outDMA_ready)

//  state2_i_count      := state1_i_count
//  state2_j_count      := state1_j_count
//  state2_k_count      := state1_k_count
//  state2_l_count      := state1_l_count

//  i_count             := state2_i_count // stage 3
//  j_count             := state2_j_count // stage 3
//  k_count             := state2_k_count // stage 3
//  l_count             := state2_l_count // stage 3


  // Registers holding the input value
  val g_point = Reg(Vec(9, FixedPoint(bit_width.W, mantissa_width.BP)))
  val enables = Wire(Vec(9, Bool()))
  for (i <- 0 to 8){
    enables(i) := false.B // Not loading by default
    when(enables(i))
    {
      g_point(i) := io.in.bits(i)
    }
  }

  // Initial result
//  io.out    := DontCare
  io.out.valid := false.B
//  io.valid_result := false.B

  // Spatial derivatives
  val dV_dx = Module(new SpatialDeriv_FirstOrder(delta_x_inverse, x_size, bit_width, mantissa_width))
  val dV_dy = Module(new SpatialDeriv_FirstOrder(delta_y_inverse, y_size, bit_width, mantissa_width))
  val dV_dv = Module(new SpatialDeriv_FirstOrder(delta_v_inverse, v_size, bit_width, mantissa_width))
  val dV_dT = Module(new SpatialDeriv_FirstOrder(delta_theta_inverse, theta_size, bit_width, mantissa_width))

  // Connect the modules
  dV_dx.io.mid_point    := g_point(0)
  dV_dx.io.left_point   := g_point(1)
  dV_dx.io.right_point  := g_point(2)
  dV_dx.io.index_count  := state2_i_count
  dV_dx.io.outDMA_ready := io.outDMA_ready

  dV_dy.io.mid_point    := g_point(0)
  dV_dy.io.left_point   := g_point(3)
  dV_dy.io.right_point  := g_point(4)
  dV_dy.io.index_count  := state2_j_count
  dV_dy.io.outDMA_ready := io.outDMA_ready

  dV_dv.io.mid_point    := g_point(0)
  dV_dv.io.left_point   := g_point(5)
  dV_dv.io.right_point  := g_point(6)
  dV_dv.io.index_count  := state2_k_count
  dV_dv.io.outDMA_ready := io.outDMA_ready

  dV_dT.io.mid_point    := g_point(0)
  dV_dT.io.left_point   := g_point(7)
  dV_dT.io.right_point  := g_point(8)
  dV_dT.io.index_count  := state2_l_count
  dV_dT.io.outDMA_ready := io.outDMA_ready



  // Propagate k_count
  val state3_k_count = RegEnable(state2_k_count, 0.U, io.outDMA_ready)
  val state4_k_count = RegEnable(state3_k_count, 0.U, io.outDMA_ready)
  val state5_k_count = RegEnable(state4_k_count, 0.U, io.outDMA_ready)
  val state6_k_count = RegEnable(state5_k_count, 0.U, io.outDMA_ready)
  val state7_k_count = RegEnable(state6_k_count, 0.U, io.outDMA_ready)

//  state3_k_count := state2_k_count
//  state4_k_count := state3_k_count
//  state5_k_count := state4_k_count
//  state6_k_count := state5_k_count
//  state7_k_count := state6_k_count

  // Determine optimal control
  val accel   = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  accel       := 0.F(bit_width.W, mantissa_width.BP)
  val rotate  = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  rotate       := 0.F(bit_width.W, mantissa_width.BP)

  // Obstacle avoidance, so we will try to maximize
  when(dV_dv.io.result >0.F(bit_width.W, mantissa_width.BP)){
    accel     := accel_max.F(bit_width.W, mantissa_width.BP)    // stage 7
  }.otherwise{
    accel     := accel_min.F(bit_width.W, mantissa_width.BP) // stage 7
  }

  when(dV_dT.io.result >0.F(bit_width.W, mantissa_width.BP)){
    //rotate     := max_w.F(bit_width.W, mantissa_width.BP)     // stage 7
    rotate       := thetaDotTable(state7_k_count) // stage 7
  }.otherwise{
    //rotate     := (-max_w).F(bit_width.W, mantissa_width.BP)  // stage 7
    rotate       := -thetaDotTable(state7_k_count) // stage 7
  }

  // Calculate the rates of changes
  val x_dot_state5_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val x_dot_state5      = RegEnable(x_dot_state5_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val x_dot_state6      = RegEnable(x_dot_state5, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val x_dot             = RegEnable(x_dot_state6, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)

  val y_dot_state5_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val y_dot_state5      = RegEnable(y_dot_state5_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val y_dot_state6      = RegEnable(y_dot_state5, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val y_dot             = RegEnable(y_dot_state6, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)

  val vel_wire          = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val sin_val_wire      = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val cos_val_wire      = Wire(FixedPoint(bit_width.W, mantissa_width.BP))

  val vel               = RegEnable(vel_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready) // stage4
  val sin_val           = RegEnable(sin_val_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready) // stage4
  val cos_val           = RegEnable(cos_val_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready) // stage4

  sin_val_wire := sin_table(l_count)   // stage 4
  cos_val_wire := cos_table(l_count)   // stage 4
  vel_wire     := vel_table(k_count)   // stage 4

  x_dot_state5_wire := vel * cos_val   // STAGE 5
  //x_dot_state6 := x_dot_state5
  //x_dot        := x_dot_state6    // stage 7

  y_dot_state5_wire := vel * sin_val
  //y_dot_state6 := y_dot_state5
  //y_dot        := y_dot_state6 // stage 7

  // calculate hamiltonian
  val dV_dt_x8_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val dV_dt_x8 = RegEnable(dV_dt_x8_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val dV_dt_x9 = RegEnable(dV_dt_x8, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val dV_dt_x = RegEnable(dV_dt_x9, 0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready) // stage 10

  val dV_dt_y8_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val dV_dt_y8 = RegEnable(dV_dt_y8_wire, 0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val dV_dt_y9 = RegEnable(dV_dt_y8, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val dV_dt_y = RegEnable(dV_dt_y9, 0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready) // stage 10

  val dV_dt_a8_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val dV_dt_a8 = RegEnable(dV_dt_a8_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val dV_dt_a9 = RegEnable(dV_dt_a8, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val dV_dt_a = RegEnable(dV_dt_a9, 0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready) // stage 10

  val dV_dt_T8_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val dV_dt_T8 = RegEnable(dV_dt_T8_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val dV_dt_T9 = RegEnable(dV_dt_T8, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val dV_dt_T  = RegEnable(dV_dt_T9, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready) // stage 10

  // Pipelining multiplication
  dV_dt_x8_wire     := dV_dx.io.result * x_dot // stage 8
  dV_dt_y8_wire     := dV_dy.io.result * y_dot // stage 8
  dV_dt_a8_wire     := dV_dv.io.result * accel // stage 8
  dV_dt_T8_wire     := dV_dT.io.result * rotate // stage 8

//  dV_dt_x9     := dV_dt_x8 // stage 9
//  dV_dt_y9     := dV_dt_y8
//  dV_dt_a9     := dV_dt_a8
//  dV_dt_T9     := dV_dt_T8
//
//  dV_dt_x     := dV_dt_x9 // stage 10
//  dV_dt_y     := dV_dt_y9 // stage 10
//  dV_dt_a     := dV_dt_a9 // stage 10
//  dV_dt_T     := dV_dt_T9 // stage 10

  // Get alphas from absolute values of rates of changes
  val alpha1   = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val alpha2   = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val alpha3   = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val alpha4   = Wire(FixedPoint(bit_width.W, mantissa_width.BP))

  // Within stage 7
  when(x_dot < 0.F(bit_width.W, mantissa_width.BP)){
    alpha1 :=  (-x_dot)
  }.otherwise{
    alpha1 := x_dot
  }
  when(y_dot < 0.F(bit_width.W, mantissa_width.BP)){
    alpha2 :=  (-y_dot)
  }.otherwise{
    alpha2 := y_dot
  }
  when(accel < 0.F(bit_width.W, mantissa_width.BP)){
    alpha3 :=  (-accel)
  }.otherwise{
    alpha3 := accel
  }
  when(rotate < 0.F(bit_width.W, mantissa_width.BP)){
    alpha4 :=  (-rotate)
  }.otherwise{
    alpha4 := rotate
  }

  // Pipelining the dissipation computation
  val diss1_8_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val diss1_8 = RegEnable(diss1_8_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val diss1_9 = RegEnable(diss1_8, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val diss1   = RegEnable(diss1_9, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready) // stage 10

  val diss2_8_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val diss2_8 = RegEnable(diss2_8_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val diss2_9 = RegEnable(diss2_8, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val diss2   = RegEnable(diss2_9, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready) // stage 10

  val diss3_8_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val diss3_8 = RegEnable(diss3_8_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val diss3_9 = RegEnable(diss3_8, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val diss3   = RegEnable(diss3_9, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready) // stage 10

  val diss4_8_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val diss4_8 = RegEnable(diss4_8_wire, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val diss4_9 = RegEnable(diss4_8, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready)
  val diss4   = RegEnable(diss4_9, 0.F(bit_width.W, mantissa_width.BP),io.outDMA_ready) // stage 10

  diss1_8_wire      := dV_dx.io.dissipate * alpha1 // stage 8
  diss2_8_wire      := dV_dy.io.dissipate * alpha2 // stage 8
  diss3_8_wire      := dV_dv.io.dissipate * alpha3 // stage 8
  diss4_8_wire      := dV_dT.io.dissipate * alpha4 // stage 8

//  // Stage 9
//  diss1_9      := diss1_8
//  diss2_9      := diss2_8
//  diss3_9      := diss3_8
//  diss4_9      := diss4_8
//
//  // Stage 10
//  diss1        := diss1_9
//  diss2        := diss2_9
//  diss3        := diss3_9
//  diss4        := diss4_9

  //
  val sum1 = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val sum2 = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val dissipation_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val dissipation = RegEnable(dissipation_wire, io.outDMA_ready)
  sum1     := diss1 + diss2 // stage 10
  sum2     := diss3 + diss4 // stage 10
  dissipation_wire := sum1 + sum2 // stage 11

  // Intermediate values
  val sum_term1_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val sum_term1      = RegEnable(sum_term1_wire, io.outDMA_ready)
  val sum_term2_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val sum_term2      = RegEnable(sum_term2_wire, io.outDMA_ready)
  val Hamiltonian_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val Hamiltonian    = RegEnable(Hamiltonian_wire, io.outDMA_ready)

  val debug_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))

  sum_term1_wire   := dV_dt_x + dV_dt_y // stage 11
  sum_term2_wire   := dV_dt_a + dV_dt_T // stage 11
  //Hamiltonian_wire  := -(sum_term1 + sum_term2 - dissipation)// stage 12
  Hamiltonian_wire  := (sum_term1 + sum_term2 + dissipation)

  debug_wire := sum_term1 + sum_term2
  // Calculate V_new
  val dV_stage13_wire = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  val dV_stage13 = RegEnable(dV_stage13_wire, io.outDMA_ready)
  val dV_stage14 = RegEnable(dV_stage13, io.outDMA_ready)
  val dV         = RegEnable(dV_stage14, io.outDMA_ready) // stage 15

  val V_new = Wire(FixedPoint(bit_width.W, mantissa_width.BP))

  dV_stage13_wire    := Hamiltonian * delta_t.F(bit_width.W, mantissa_width.BP)
//  dV_stage14    := dV_stage13
//  dV            := dV_stage14 // stage 15

  // Center value V[k][l][j][i] propagated towards the end of pipeline
  val latency = 14 // this is based on number of stages in the pipelined datapath
  /*val center_point = Module(new Pipe(FixedPoint(bit_width.W, mantissa_width.BP), latency))
  center_point.io.enq.bits  := io.in.bits(0)
  center_point.io.enq.valid := io.in.valid */

  val cp_wire     = Wire(FixedPoint(bit_width.W, mantissa_width.BP))
  cp_wire        := io.in.bits(0)
  val state1_cp   = RegEnable(cp_wire, 0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state2_cp   = RegEnable(state1_cp,  0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state3_cp   = RegEnable(state2_cp,  0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state4_cp   = RegEnable(state3_cp,  0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state5_cp   = RegEnable(state4_cp,  0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state6_cp   = RegEnable(state5_cp,  0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state7_cp   = RegEnable(state6_cp,  0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state8_cp   = RegEnable(state7_cp,  0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state9_cp   = RegEnable(state8_cp,  0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state10_cp  = RegEnable(state9_cp,  0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state11_cp  = RegEnable(state10_cp, 0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state12_cp  = RegEnable(state11_cp, 0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val state13_cp  = RegEnable(state12_cp, 0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  val center_point  = RegEnable(state13_cp, 0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready)
  //val center_point  = RegEnable(state14_cp, 0.F(bit_width.W, mantissa_width.BP), io.outDMA_ready) // state 15
  //V_new           := dV + center_point.io.deq.bits

  /* Original code */
  //V_new           := dV + center_point

  // Mode VWithVInit optimized_dp
  when(dV > 0.F(bit_width.W, mantissa_width.BP)){
    V_new := center_point
  }.otherwise{
    V_new := dV + center_point
  }

  io.out.bits  := V_new
  io.in.ready := true.B
  //io.debugging_wire := dV_dx.io.result

  //printf(p"\n dV : ${dV_dx}") //Predicate ${predicate} Left ${left_R} Right ${right_R} Output: ${data_R}")

  // Valid signal propagated using Pipe
  val valid_Vnew = Module(new Pipe(Bool(), latency))
  valid_Vnew.io.enq.bits  := false.B
  //valid_Vnew.io.enq.bits  := io.in.valid
  valid_Vnew.io.enq.valid := true.B

  // Valid bit of the resul
//  io.valid_result         := valid_Vnew.io.deq.bits

  //io.out.bits := vel
  //io.out.valid := valid_Vnew.io.deq.bits
  io.out.valid  := false.B

  // States of the machine
  val wait_data :: compute :: stopCounters :: done :: Nil = Enum(4)
  val state = RegInit(wait_data)

  //val idk =  FixedPoint.toDouble(io.out.bits, mantissa_width)
  //println("Inside PE_4D. Scala output: " + idk)
//  printf(p"\n ID: ${ID} PE_4D State : ${state}  Input1: ${io.in.bits(0).asUInt} " +
//    p"Input2: ${io.in.bits(2).asUInt} Input3: ${io.in.bits(4).asUInt} Input4: ${io.in.bits(6).asUInt}" +
//    p"Input5: ${io.in.bits(8).asUInt} Valid_sig : ${io.in.valid}")
  //printf(p"\n ID: ${ID} PE_4D State : ${state}  Result: ${io.out.bits.asUInt} Valid_sig : ${io.out.valid}")
  // Output : ${center_point.io.deq.bits.asSInt}
  // Core computation
  switch(state)
  {
    is(wait_data){
      io.done := false.B
      when(io.start)
      {
        // in the next state, data starts flowing for computation
        state := compute
        for(i <- 0 to 8){
          //enables(i) := true.B
          enables(i) := io.outDMA_ready
          start_count_l := true.B
          // start propagate the valid signal
          valid_Vnew.io.enq.bits  := true.B
          io.out.valid := valid_Vnew.io.deq.bits
        }
      }.otherwise{
        state := wait_data
      }
    }
    is(compute){ // Data is in
      io.done := false.B
      // start counter l now
      for(i <- 0 to 8){
        //enables(i) := true.B
        enables(i) := io.outDMA_ready
      }
      when(io.outDMA_ready) {
        start_count_l := true.B
      }
      // Finished the nest loop
      when(wrap_j && wrap_i && wrap_k && wrap_l){
        state := done
      }
      valid_Vnew.io.enq.bits  := true.B
      io.out.valid := valid_Vnew.io.deq.bits
    }
    is(done)
    {
      // Reset counters back to 0
      start_count_i := false.B
      start_count_j := false.B
      start_count_k := false.B
      start_count_l := false.B
      //io.valid_result := false.B

      valid_Vnew.io.enq.bits  := false.B
      io.out.valid := valid_Vnew.io.deq.bits

      // Wait until PE_io done is true
      io.done := io_done
      //io.done   := io_done_pipe(13)
      when(io.done){
          state := wait_data
      }
    }
  }

  // Debug print
  /*if(ID == 0){
    printf(p"\n state1_l_count : ${state1_l_count} state1_k_count : ${state1_k_count} state1_j_count : ${state1_j_count} state1_i_count : ${state1_i_count} ")
    printf(p"\n dV_dx : ${dV_dx.io.result.asUInt} dV_dy : ${dV_dy.io.result.asUInt} dV_dv : ${dV_dv.io.result.asUInt} dV_dT : ${dV_dT.io.result.asUInt} ")
    printf(p"\n x_dot : ${x_dot.asUInt} y_dot : ${y_dot.asUInt} v_dot : ${accel.asUInt} theta_dot : ${rotate.asUInt} ")
    printf(p"\n center_pt : ${io.in.bits(0).asUInt}")
    printf(p"\n Ham : ${Hamiltonian.asUInt}")
    printf(p"\n PE_4D State : ${state} Output: ${io.out.bits.asUInt}  Valid: ${valid_Vnew.io.deq.bits}")
    printf(p"\n outDMA ready : ${io.outDMA_ready.asUInt} ")
  }*/

  /* if(ID == 0){
    //printf(p"\n state1_l_count : ${state1_l_count} state1_k_count : ${state1_k_count} state1_j_count : ${state1_j_count} state1_i_count : ${state1_i_count} ")
    //printf(p"\n center_pt : ${io.in.bits(0).asUInt}")
    //printf(p"\n dV_dx : ${dV_dx.io.result.asUInt} dV_dy : ${dV_dy.io.result.asUInt} dV_dv : ${dV_dv.io.result.asUInt} dV_dT : ${dV_dT.io.result.asUInt} ")
    //printf(p"\n dV_dt_x : ${dV_dt_x.asUInt} ")
    //printf(p"\n dV_dt_y : ${dV_dt_y.asUInt} ")

    //printf(p"\n x_left_pt : ${io.in.bits(1).asUInt} x_right_pt : ${io.in.bits(2).asUInt} ")
    //printf(p"\n y_left_pt : ${io.in.bits(3).asUInt} y_right_pt : ${io.in.bits(4).asUInt} ")
    //printf(p"\n v_left_pt : ${io.in.bits(5).asUInt} v_right_pt : ${io.in.bits(6).asUInt} ")
    //printf(p"\n T_left_pt : ${io.in.bits(7).asUInt} T_right_pt : ${io.in.bits(8).asUInt} ")
    //printf(p"\n PE_4D State : ${state} Output: ${io.out.bits.asUInt}  Valid: ${valid_Vnew.io.deq.bits}")
    //printf(p"\n outDMA ready : ${io.outDMA_ready.asUInt} ")
  } */


  if(debug){
    printf(p"\n PE_4D State : ${state} Output: ${io.out.bits.asUInt}  Valid: ${valid_Vnew.io.deq.bits}")
    printf(p"\n i : ${state1_i_count} j : ${state1_j_count} k : ${state1_k_count} l : ${state1_l_count}")
  }
}



/*object DubinsCarPE_wrapper extends App {
  chisel3.Driver.execute(args, () => new DubinsCarPE_wrapper(32, 27, 60, 60,
    20, 36, 1.0, 1.0))
}*/

/*class DubinsCarPE_wrapper(bit_width: Int, mantissa_width: Int, x_size: Int, y_size: Int,
                          v_size: Int, theta_size: Int, max_accel: Double, max_w: Double)
  extends Module
  {
    val io = IO(new Bundle {
      val start_wrapper = Input(Bool()) // Control signal - should I start?
      val my_result = Output(FixedPoint(bit_width.W, mantissa_width.BP))
      val done = Output(Bool())
    })

    // My PE module
    val my_PE = Module(new DubinsCar_PE(bit_width, mantissa_width, x_size, y_size,
      v_size, theta_size, max_accel, max_w))

    my_PE.io.start_PE := io.start_wrapper
    // Initialize data
    my_PE.io.grid_points(0) := 0.5.F(bit_width.W, mantissa_width.BP)
    my_PE.io.grid_points(1) := 0.0357.F(bit_width.W, mantissa_width.BP)
    my_PE.io.grid_points(2) := 0.317.F(bit_width.W, mantissa_width.BP)
    my_PE.io.grid_points(3) := 1.32.F(bit_width.W, mantissa_width.BP)
    my_PE.io.grid_points(4) := 1.42.F(bit_width.W, mantissa_width.BP)
    my_PE.io.grid_points(5) := 2.11.F(bit_width.W, mantissa_width.BP)
    my_PE.io.grid_points(6) := 2.56.F(bit_width.W, mantissa_width.BP)
    my_PE.io.grid_points(7) := 1.7823.F(bit_width.W, mantissa_width.BP)
    my_PE.io.grid_points(8) := 1.9276.F(bit_width.W, mantissa_width.BP)


    // Connect output signal
    io.my_result    := my_PE.io.my_result
    io.done         := my_PE.io.done

  }*/
