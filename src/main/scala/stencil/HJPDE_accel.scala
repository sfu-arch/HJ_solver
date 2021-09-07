package stencil

import chisel3._
import chisel3.util._
import config._
import shell._
import dnn.memory.CoreParams

class HJPDE_core(bit_width: Int, mantissa_width: Int, x_size: Int, y_size: Int,
                   v_size: Int, theta_size: Int, max_accel: Double, max_w: Double)(implicit p: Parameters)
  extends Module {
  val io = IO(new Bundle {
    val vcr = new VCRClient
    val vme = new VMEMaster
  })

  val compute_core = Module(new HJSolver_4D(32, 28, 60, 60, 20, 36, 1,  1))


  val cycle_count = new Counter(100000000)

  /* ================================================================== *
     *                      VCR Connections                            *
     * ================================================================== */

  // Only need 1 ecnt back
  io.vcr.ecnt(0).bits := cycle_count.value

  /* ================================================================== *
    *                    VME Reads and writes                           *
    * ================================================================== */

  // need only 1 read port
  io.vme.rd(0) <> compute_core.io.vme_rd

  // need only 1 write port
  io.vme.wr(0) <> compute_core.io.vme_wr

  compute_core.io.start := false.B

  // DRAM read address dictated by DRAM
  compute_core.io.rdAddr := io.vcr.ptrs(0)
  // DRAM write address dictated by DRAM
  compute_core.io.wrAddr := io.vcr.ptrs(1)

  val PETime = RegInit(0.U)
  when(compute_core.io.PE_done) {
    PETime := cycle_count.value
  }
  io.vcr.ecnt(1).bits := PETime

  val sIdle :: sExec :: sFinish :: Nil = Enum(3)
  val state = RegInit(sIdle)

  switch(state) {
    is(sIdle) {
      when(io.vcr.launch) {
        compute_core.io.start := true.B
        state := sExec
      }
    }
    is(sExec) {
      when(compute_core.io.done) {
        state := sIdle
      }
    }
  }

  val last = state === sExec && compute_core.io.done
  io.vcr.finish := last
  io.vcr.ecnt(0).valid := last
  io.vcr.ecnt(1).valid := last

  when(state =/= sIdle) {
    cycle_count.inc()
  }
}
