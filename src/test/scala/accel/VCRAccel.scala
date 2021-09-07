/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package accel

import accel.HJsolverSimAccelMain.args
import accel.HJsolverAWSAccelMain.{args, bit_width, mantissa_width, max_accel, max_w, theta_size, v_size, x_size, y_size}
import chisel3._
import chisel3.MultiIOModule
import shell._
import vta.shell._
import shell.De10Config
import config._
import accel._
import chisel3.util._
import dnn.memory._



/** Test. This generates a testbench file for simulation */
class HJsolverAWSAccel(bit_width: Int, mantissa_width: Int, x_size: Int, y_size: Int, v_size: Int, theta_size: Int,
max_accel: Int, max_w: Int)(implicit p: Parameters) extends MultiIOModule {
  val sim_clock = IO(Input(Clock()))
  val sim_wait = IO(Output(Bool()))
  val sim_shell = Module(new AXISimShell)
  val vta_shell = Module(new HJAccel(bit_width= bit_width, mantissa_width= mantissa_width, x_size = x_size,
    y_size= y_size, v_size= v_size, theta_size= theta_size, max_accel= max_accel, max_w= max_w))
  sim_shell.sim_clock := sim_clock
  sim_wait := sim_shell.sim_wait

  sim_shell.mem.ar <> vta_shell.io.mem.ar
  sim_shell.mem.aw <> vta_shell.io.mem.aw
  vta_shell.io.mem.r <> sim_shell.mem.r
  vta_shell.io.mem.b <> sim_shell.mem.b
  sim_shell.mem.w <> vta_shell.io.mem.w



  vta_shell.io.host.ar <> sim_shell.host.ar
  vta_shell.io.host.aw <> sim_shell.host.aw
  sim_shell.host.r <> vta_shell.io.host.r
  sim_shell.host.b <> vta_shell.io.host.b
  vta_shell.io.host.w <> sim_shell.host.w

  // vta_shell.io.host <> sim_shell.host
}

class HJsolverSimAccel(bit_width: Int, mantissa_width: Int, x_size: Int, y_size: Int, v_size: Int, theta_size: Int,
                       max_accel: Int, max_w: Int)
                          (implicit val p: Parameters) extends MultiIOModule {
  val sim_clock = IO(Input(Clock()))
  val sim_wait = IO(Output(Bool()))
  val sim_shell = Module(new AXISimShell)
  val vta_shell = Module(new HJAccel(bit_width= bit_width, mantissa_width= mantissa_width, x_size = x_size,
    y_size= y_size, v_size= v_size, theta_size= theta_size, max_accel= max_accel, max_w= max_w))
  sim_shell.sim_clock := sim_clock
  sim_wait := sim_shell.sim_wait

  sim_shell.mem.ar <> vta_shell.io.mem.ar
  sim_shell.mem.aw <> vta_shell.io.mem.aw
  vta_shell.io.mem.r <> sim_shell.mem.r
  vta_shell.io.mem.b <> sim_shell.mem.b
  sim_shell.mem.w <> vta_shell.io.mem.w


  vta_shell.io.host.ar <> sim_shell.host.ar
  vta_shell.io.host.aw <> sim_shell.host.aw
  sim_shell.host.r <> vta_shell.io.host.r
  sim_shell.host.b <> vta_shell.io.host.b
  vta_shell.io.host.w <> sim_shell.host.w

}

/**
  * Configurations for various FPGA platforms
  */

class DefaultDe10Config()
  extends Config(new De10Config() ++
    new CoreConfig ++ new MiniConfig)

class DefaultPynqConfig()
  extends Config(new PynqConfig(numSegments = 1, numSorter = 1) ++
    new CoreConfig ++ new MiniConfig)

class DefaultAWSConfig()
  extends Config(new AWSConfig(numSegments = 1, numSorter = 1) ++
    new CoreConfig ++ new MiniConfig)



object HJsolverSimAccelMain extends App {
  var bit_width = 32
  var mantissa_width = 27
  var x_size = 60
  var y_size = 60
  var v_size = 20
  var theta_size = 36
  var max_accel = 1
  var max_w = 1

  args.sliding(2, 2).toList.collect {
    case Array("--bit_width", argCtrl: String) => bit_width = argCtrl.toInt
    case Array("--mantissa_width", argCtrl: String) => mantissa_width = argCtrl.toInt
    case Array("--x_size", argCtrl: String) => x_size = argCtrl.toInt
    case Array("--y_size", argCtrl: String) => y_size = argCtrl.toInt
    case Array("--v_size", argCtrl: String) => v_size = argCtrl.toInt
    case Array("--theta_size", argCtrl: String) => theta_size = argCtrl.toInt
    case Array("--max_accel", argCtrl: String) => max_accel = argCtrl.toInt
    case Array("--max_w", argCtrl: String) => max_w = argCtrl.toInt
  }

  implicit val p: Parameters = new DefaultDe10Config()
  chisel3.Driver.execute(args.take(4), () => new HJsolverSimAccel(bit_width= bit_width, mantissa_width= mantissa_width, x_size = x_size,
    y_size= y_size, v_size= v_size, theta_size= theta_size, max_accel= max_accel, max_w= max_w))
}


object TestXilinxShellMain extends App {
  implicit val p: Parameters = new DefaultPynqConfig
  chisel3.Driver.execute(args, () => new XilinxShell())
}


object HJsolverAWSAccelMain extends App {
  var bit_width = 32
  var mantissa_width = 27
  var x_size = 6
  var y_size = 6
  var v_size = 6
  var theta_size = 16
  var max_accel = 1
  var max_w = 1

  args.sliding(2, 2).toList.collect {
    case Array("--bit_width", argCtrl: String) => bit_width = argCtrl.toInt
    case Array("--mantissa_width", argCtrl: String) => mantissa_width = argCtrl.toInt
    case Array("--x_size", argCtrl: String) => x_size = argCtrl.toInt
    case Array("--y_size", argCtrl: String) => y_size = argCtrl.toInt
    case Array("--v_size", argCtrl: String) => v_size = argCtrl.toInt
    case Array("--theta_size", argCtrl: String) => theta_size = argCtrl.toInt
    case Array("--max_accel", argCtrl: String) => max_accel = argCtrl.toInt
    case Array("--max_w", argCtrl: String) => max_w = argCtrl.toInt
  }
  implicit val p: Parameters = new DefaultAWSConfig()
  chisel3.Driver.execute(args.take(4), () => new F1Shell(  bit_width= bit_width, mantissa_width= mantissa_width, x_size = x_size,
    y_size= y_size, v_size= v_size, theta_size= theta_size, max_accel= max_accel, max_w= max_w))

}

object HJsolverAccelMain extends App {
  var bit_width = 32
  var mantissa_width = 13
  var x_size = 10
  var y_size = 10
  var v_size = 10
  var theta_size = 10
  var max_accel = 10
  var max_w = 10

  args.sliding(2, 2).toList.collect {
    case Array("--bit_width", argCtrl: String) => bit_width = argCtrl.toInt
    case Array("--mantissa_width", argCtrl: String) => mantissa_width = argCtrl.toInt
    case Array("--x_size", argCtrl: String) => x_size = argCtrl.toInt
    case Array("--y_size", argCtrl: String) => y_size = argCtrl.toInt
    case Array("--v_size", argCtrl: String) => v_size = argCtrl.toInt
    case Array("--theta_size", argCtrl: String) => theta_size = argCtrl.toInt
    case Array("--max_accel", argCtrl: String) => max_accel = argCtrl.toInt
    case Array("--max_w", argCtrl: String) => max_w = argCtrl.toInt
  }
  implicit val p: Parameters = new DefaultDe10Config
  chisel3.Driver.execute(args.take(4), () => new HJAccel(bit_width= bit_width, mantissa_width= mantissa_width, x_size = x_size,
    y_size= y_size, v_size= v_size, theta_size= theta_size, max_accel= max_accel, max_w= max_w))
}

