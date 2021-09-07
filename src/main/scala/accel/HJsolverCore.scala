package accel

import FPU.FType
import chisel3.util._
import chisel3.{when, _}
import config._
import node.FPvecN
import shell._
import stencil.HJSolver_4D

/** SparseTensorCore.
  *
  * SparseTensorCore is able to perform the linear algebraic computations on sparse tensors.
  */
class HJsolverCore(bit_width: Int, mantissa_width: Int, x_size: Int, y_size: Int,
                   v_size: Int, theta_size: Int, max_accel: Int, max_w: Int)(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val vcr = new VCRClient
    val vme = new VMEMaster
  })

  val cycle_count = new Counter(100000000)

  val HJsolver = Module(new HJSolver_4D(bit_width= bit_width, mantissa_width= mantissa_width, x_size = x_size, y_size= y_size,
                        v_size= v_size, theta_size= theta_size, max_accel= max_accel, max_w= max_w))

  val sIdle :: sExec :: sFinish :: Nil = Enum(3)
  val state = RegInit(sIdle)

  /* ================================================================== *
     *                           Connections                            *
     * ================================================================== */

  HJsolver.io.start := false.B

  io.vme.rd(0) <> HJsolver.io.vme_rd
  io.vme.wr(0) <> HJsolver.io.vme_wr

  HJsolver.io.rdAddr := io.vcr.ptrs(0)
  HJsolver.io.wrAddr := io.vcr.ptrs(1)

  val PETime = RegInit(0.U)
  when(HJsolver.io.PE_done) {
    PETime := cycle_count.value
  }

  io.vcr.ecnt(0).bits := cycle_count.value
  io.vcr.ecnt(1).bits := PETime

  switch(state) {
    is(sIdle) {
      when(io.vcr.launch) {
        HJsolver.io.start := true.B
        state := sExec
      }
    }
    is(sExec) {
      when(HJsolver.io.done) {
        state := sIdle
      }
    }
  }

  val last = state === sExec && HJsolver.io.done
  io.vcr.finish := last
  io.vcr.ecnt.map(_.valid).foreach(a => a := last)

  when(state =/= sIdle) {
    cycle_count.inc()
  }
}
