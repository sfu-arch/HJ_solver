package stencil

import chisel3.assert
import chisel3.iotesters.{Driver, PeekPokeTester}
import org.scalatest.{FlatSpec, Matchers}
import chisel3.experimental.FixedPoint

//class DubinsCar_Tester(c: DubinsCar_PE) extends PeekPokeTester(c) {
//  poke(c.io.start_PE, true)
//  poke(c.io.grid_points(0), FixedPoint.toBigInt(0.5, 27))
//  poke(c.io.grid_points(1), FixedPoint.toBigInt(0.0357, 27))
//  poke(c.io.grid_points(2), FixedPoint.toBigInt(0.317, 27))
//  poke(c.io.grid_points(3), FixedPoint.toBigInt(1.32, 27))
//  poke(c.io.grid_points(4), FixedPoint.toBigInt(1.42, 27))
//  poke(c.io.grid_points(5), FixedPoint.toBigInt(2.11, 27))
//  poke(c.io.grid_points(6), FixedPoint.toBigInt(2.56, 27))
//  poke(c.io.grid_points(7), FixedPoint.toBigInt(1.7823, 27))
//  poke(c.io.grid_points(8), FixedPoint.toBigInt(1.9276, 27))
//  println(" f_result is " + FixedPoint.toDouble(peek(c.io.f_result), 27))
//  println(" k_is_0 is " + peek(c.io.valid_result))
//  step(20)
//  //step(1)
//  println(" ")
//  println(" f_result is " + FixedPoint.toDouble(peek(c.io.f_result), 27))
//  println(" k_is_0 is " + peek(c.io.valid_result))
//
//  step(1)
//  println(" ")
//  println(" f_result is " + FixedPoint.toDouble(peek(c.io.f_result), 27))
//  println(" k_is_0 is " + peek(c.io.valid_result))
//
//  step(1)
//  println(" ")
//  println(" f_result is " + FixedPoint.toDouble(peek(c.io.f_result), 27))
//  println(" k_is_0 is " + peek(c.io.valid_result))
//
//  step(1)
//  println(" ")
//  println(" f_result is " + FixedPoint.toDouble(peek(c.io.f_result), 27))
//  println(" k_is_0 is " + peek(c.io.valid_result))
//
//  step(1)
//  println(" ")
//  println(" f_result is " + FixedPoint.toDouble(peek(c.io.f_result), 27))
//  println(" k_is_0 is " + peek(c.io.valid_result))
//
//  step(1)
//  println(" ")
//  println(" f_result is " + FixedPoint.toDouble(peek(c.io.f_result), 27))
//  println(" k_is_0 is " + peek(c.io.valid_result))
//
//  step(1)
//  println(" ")
//  println(" f_result is " + FixedPoint.toDouble(peek(c.io.f_result), 27))
//  println(" k_is_0 is " + peek(c.io.valid_result))
//}
class DubinsCar_Tester(c: DubinsCar_PE) extends PeekPokeTester(c) {
  // Create a 4D array
  val dim1 = 6
  val dim2 = 6
  val dim3 = 6
  val dim4 = 6

  val delta_x_inverse: Double = 7.5
  val delta_a_inverse: Double = 5.0
  val delta_theta_inverse: Double = 36/(2*math.Pi)
  val delta_t: Double = 0.02

  val testArray = Array.ofDim[Double](dim1, dim2, dim3, dim4)

  // Initialize x_array
  val x_array = (0 to dim1).map(i => ( i* 4)/(dim1.toDouble-1) - 2.0) // Assuming range = 4, and min = -2.0
  val y_array = (0 to dim2).map(i => ( i* 4)/(dim2.toDouble-1) - 2.0) // assuming range = 4, and min = -2.0
  val v_array = (0 to dim3).map(i => ( i* 2)/(dim3.toDouble - 1) - 1.0) // assuming range = 2, and min = -1.0
                                                                    // (-1 <= V <= 1)
  val theta_array = (0 to dim4).map(i => ( i* 2*math.Pi)/(dim4.toDouble-1) - math.Pi) // assuming range = 1, and min = 0.0

  val Init_V = Array.ofDim[Double](dim1, dim2, dim3, dim4)
  val radius = 0.02

  // Initialize the initial value function
  for(i <- 0 until dim1; j <- 0 until dim2;
      k <- 0 until dim3; l <- 0 until dim4){
        Init_V(i)(j)(k)(l) = math.sqrt(math.pow(x_array(i), 2) + math.pow(y_array(j), 2)) - radius
  }

  def spatialDeriv_1st(my_index: Array[Int], dim: Int): Double = {
    val i = my_index(0)
    val j = my_index(1)
    val k = my_index(2)
    val l = my_index(3)

    //
    //val flattened_array = Init_V.flatten
    val center_point = Init_V(i)(j)(k)(l)
    var left_point = 0.0
    var right_point = 0.0
    var delta_inverse = 1.0

    // if in x direction
    if (dim == 1){
      delta_inverse = 7.5
      if(i != 0 && i != dim1 -1){
        left_point = Init_V(i-1)(j)(k)(l)
        right_point = Init_V(i+1)(j)(k)(l)
      }
      if(i == 0){
        right_point = Init_V(i+1)(j)(k)(l)
        left_point = center_point + math.abs(right_point - center_point) * math.signum(center_point)
      }
      if(i == dim1 -1){
        left_point = Init_V(i-1)(j)(k)(l)
        right_point = center_point + math.abs(left_point - center_point) * math.signum(center_point)
      }
      //println("Left point is " + left_point)
      //println("Center point is " + center_point)
      //println("Right point is " + right_point)
    }

    if (dim == 2){
      delta_inverse = 7.5
      if(j != 0 && j != dim2 -1){
        left_point = Init_V(i)(j-1)(k)(l)
        right_point = Init_V(i)(j+1)(k)(l)
      }
      if(j == 0){
        right_point = Init_V(i)(j+1)(k)(l)
        left_point = center_point + math.abs(right_point - center_point) * math.signum(center_point)
      }
      if(j == dim2 -1){
        left_point = Init_V(i)(j-1)(k)(l)
        right_point = center_point + math.abs(left_point - center_point) * math.signum(center_point)
      }
    }

    if (dim == 3){
      delta_inverse = 5.0
      //println("centre_point = " + center_point )
      if(k != 0 && k != dim3 -1){
        left_point = Init_V(i)(j)(k-1)(l)
        right_point = Init_V(i)(j)(k+1)(l)
      }
      if(k == 0){
        right_point = Init_V(i)(j)(k+1)(l)
        left_point = center_point + math.abs(right_point - center_point) * math.signum(center_point)
      }
      if(k == dim3 -1){
        left_point = Init_V(i)(j)(k-1)(l)
        right_point = center_point + math.abs(left_point - center_point) * math.signum(center_point)
      }
      //println("right_point = " + right_point )
      //println("left_point = " + left_point )

    }

    if (dim == 4){
      delta_inverse = 36/(2*math.Pi)
      if(l != 0 && l != dim4 -1){
        left_point = Init_V(i)(j)(k)(l-1)
        right_point = Init_V(i)(j)(k)(l+1)
      }
      if(l == 0){
        right_point = Init_V(i)(j)(k)(l+1)
        left_point = center_point + math.abs(right_point - center_point) * math.signum(center_point)
      }
      if(l == dim4 -1){
        left_point = Init_V(i)(j)(k)(l-1)
        right_point = center_point + math.abs(left_point - center_point) * math.signum(center_point)
      }
    }

    (right_point - left_point)*delta_inverse
  }

  def cal_newV(my_index: Array[Int]): Double = {//(my_array: Array[Array[Array[Array[Double]]]], my_index: Array[Int]){
    val i = my_index(0)
    val j = my_index(1)
    val k = my_index(2)
    val l = my_index(3)

    val dV_dx = spatialDeriv_1st(my_index, 1)
    //println("dV_dx = " + dV_dx )
    val dV_dy = spatialDeriv_1st(my_index, 2)
    //println("dV_dy = " + dV_dy )
    val dV_dv = spatialDeriv_1st(my_index, 3)
    //println("dV_dv = " + dV_dv )
    val dV_dt = spatialDeriv_1st(my_index, 4)
    //println("dV_dt = " + dV_dt )

    var aOpt = 1.0
    var wOpt = 1.0
    // Determine optimal control (assuming range of 1.0 -> 1.0)
    if(dV_dv < 0.0){
      aOpt  = -1.0
    }
    if(dV_dt < 0.0){
      wOpt  = -1.0
    }
    // Calculate dynamics
    val x_dot = v_array(k) * math.cos(theta_array(l))
    //println("x_dot = " + x_dot )
    val y_dot = v_array(k) * math.sin(theta_array(l))
    //println("y_dot = " + y_dot )
    val v_dot = aOpt
    //println("v_dot = " + v_dot )
    val theta_dot = wOpt
    //println("theta_dot = " + theta_dot )

    // Calculate Hamiltonian
    val Ham = x_dot * dV_dx + y_dot * dV_dy + v_dot * dV_dv + theta_dot * dV_dt
    //println("Ham = " + Ham )
    val dV = Ham * delta_t

    //dV
    dV + Init_V(i)(j)(k)(l)
    //dV_dx
  }


  var cycle_count = 0
  /*poke(c.io.start, true)

  // Test signal done
  for(i <- 0 until dim1; j <- 0 until dim2;
      k <- 0 until dim3; l <- 0 until dim4){
    if(i == dim1 -1 && j == dim2 -1 && k == dim3 - 1 && l == dim4 -1){
      expect(c.io.done, true)
    }
    step(1)
  }
  //expect(c.io.done, true)
  step(1) // go back to idleState*/
  // Actual test
  poke(c.io.start, true)
  //step(1)
  var cont = false
  for(i <- 0 until dim1; j <- 0 until dim2;
      k <- 0 until dim3; l <- 0 until dim4){
      // Input V into the PE_array
    if(i == 0 && j == 0 && k == 0 && l == 0) {
      // set the input signal to valid
      poke(c.io.in.valid, true)
      // IMPORTANT: Follow the convention commented in PE_4d.scala
      poke(c.io.in.bits(0), FixedPoint.toBigInt(Init_V(0)(0)(0)(0), 27))
      poke(c.io.in.bits(1), FixedPoint.toBigInt(5, 27)) // just some garbage value
      poke(c.io.in.bits(2), FixedPoint.toBigInt(Init_V(1)(0)(0)(0), 27))
      poke(c.io.in.bits(3), FixedPoint.toBigInt(5, 27)) // just some garbage value
      poke(c.io.in.bits(4), FixedPoint.toBigInt(Init_V(0)(1)(0)(0), 27))
      poke(c.io.in.bits(5), FixedPoint.toBigInt(5.0, 27)) // just some garbage value
      poke(c.io.in.bits(6), FixedPoint.toBigInt(Init_V(0)(0)(1)(0), 27))
      poke(c.io.in.bits(7), FixedPoint.toBigInt(5.0, 27)) // just some garbage value
      poke(c.io.in.bits(8), FixedPoint.toBigInt(Init_V(0)(0)(0)(1), 27))

      // PE start _ always start
      poke(c.io.start, true)
      step(1)
      //expect(c.io.out.valid, false)
      poke(c.io.in.bits(0), FixedPoint.toBigInt(Init_V(0)(0)(0)(1), 27))
      poke(c.io.in.bits(1), FixedPoint.toBigInt(5, 27)) // just some garbage value
      poke(c.io.in.bits(2), FixedPoint.toBigInt(Init_V(1)(0)(0)(1), 27))
      poke(c.io.in.bits(3), FixedPoint.toBigInt(5, 27)) // just some garbage value
      poke(c.io.in.bits(4), FixedPoint.toBigInt(Init_V(0)(1)(0)(1), 27))
      poke(c.io.in.bits(5), FixedPoint.toBigInt(5.0, 27)) // just some garbage value
      poke(c.io.in.bits(6), FixedPoint.toBigInt(Init_V(0)(0)(1)(1), 27))
      poke(c.io.in.bits(7), FixedPoint.toBigInt(Init_V(0)(0)(0)(0), 27)) // just some garbage value
      poke(c.io.in.bits(8), FixedPoint.toBigInt(Init_V(0)(0)(0)(2), 27))

      // Calculate new V
      var V_new = cal_newV(Array(i, j, k, l))
      //expect(c.io.out.valid, true)
      step(12)
      expect(c.io.out.valid, false)
      println("Final result from PE is " + FixedPoint.toDouble(peek(c.io.out.bits), 27))
      step(1)


      // After 14 cycles, compare with the correct value
      //if (i == 0 && j == 0 && k == 0 && l == 14) {
      expect(c.io.out.valid, true)
      println("Final result from PE is " + FixedPoint.toDouble(peek(c.io.out.bits), 27))
      println("Should-be result is " + V_new)
      step(1)
      expect(c.io.out.valid, true)
      V_new = cal_newV(Array(i, j, k, l + 1))
      println("Final result from PE is " + FixedPoint.toDouble(peek(c.io.out.bits), 27))
      println("Should-be result is " + V_new)
      //}
    }
    /*if(i == 0 && j == 0 && k == 5 && l == 0){
      poke(c.io.in.valid, true)
      // IMPORTANT: Follow the convention commented in PE_4d.scala
      poke(c.io.in.bits(0), FixedPoint.toBigInt(Init_V(0)(0)(5)(0), 27)) // [l][k][j][i  ]
      poke(c.io.in.bits(1), FixedPoint.toBigInt(5, 27)) // just some garbage value
      poke(c.io.in.bits(2), FixedPoint.toBigInt(Init_V(1)(0)(5)(0), 27)) //[l][k][j][i+1]
      poke(c.io.in.bits(3), FixedPoint.toBigInt(5, 27)) // just some garbage value [l][k][j-1][i]
      poke(c.io.in.bits(4), FixedPoint.toBigInt(Init_V(0)(1)(5)(0), 27)) // [l][k][j+1][i]
      poke(c.io.in.bits(5), FixedPoint.toBigInt(Init_V(0)(0)(4)(0), 27)) // [l][k-1][j][i]
      poke(c.io.in.bits(6), FixedPoint.toBigInt(5.0, 27))// just some garbage value //[l][k+1][j][i]
      poke(c.io.in.bits(7), FixedPoint.toBigInt(5.0, 27)) // just some garbage value [l-1][k][j][i]
      poke(c.io.in.bits(8), FixedPoint.toBigInt(Init_V(0)(0)(5)(1), 27)) // [l+1][k][j][i]

      // PE start _ always start
      step(1)
      var V_new = cal_newV(Array(i, j, k, l))
      //expect(c.io.out.valid, true)
      step(13)
      println("Final result from PE is " + FixedPoint.toDouble(peek(c.io.out.bits), 27))
      println("Should-be result is " + V_new)
      cont = true
    }
    if (cont == false){
      step(1)
    }

    //println("Cont =  " + cont)
    println("i =  " + i + " j =  " + j + " k =  " + k + " l =  " + l)*/
  }
  //expect(c.io.done, true)
}

class test_DubinsCarPE extends FlatSpec with Matchers {
  //  implicit val p = config.Parameters.root((new Mat_VecConfig).toInstance)
  //  implicit val p = config.Parameters.root((new CoreConfig ++ new MiniConfig).toInstance)
  //  implicit val p = new shell.DefaultDe10Config ++ Parameters.root((new MiniConfig).toInstance)

  it should "Typ Compute Tester" in {
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir"),
      () => new DubinsCar_PE(32, 27, 6, 6, 20, 36,
        1,  1, 3, 4)) {
      c => new DubinsCar_Tester(c)
    } should be(true)
  }
}