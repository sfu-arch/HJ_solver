package stencil

import chisel3.assert
import chisel3.iotesters.{Driver, PeekPokeTester}
import org.scalatest.{FlatSpec, Matchers}
import chisel3.experimental.FixedPoint

class ReuseBufferTester(c: shift_buffer4d) extends PeekPokeTester(c){
  // Create a 4D array
  val dim1 = 20
  val dim2 = 20
  val dim3 = 20
  val dim4 = 20

  val testArray = Array.ofDim[Double](dim1, dim2, dim3, dim4)
  // Initialize array and input into reuser buffer
  expect(c.io.in.ready,true)
  for(i <- 0 to 2; j <- 0 until dim2;
      k <- 0 until dim3; l <- 0 until dim4){
    // Enqueue data into queue
    testArray(i)(j)(k)(l)  = 1.0 + (l + k*dim4 + j*dim3*dim4 + i*dim2*dim3*dim4).toDouble/10000.toDouble
    poke(c.io.in.bits, FixedPoint.toBigInt(testArray(i)(j)(k)(l), 27))
    poke(c.io.in.valid, true) // enough to start

    // check the results
    if(i == 0){
      expect(c.io.out.valid, false)
    }
    // when
    if(i == 1 && j == 0 && k == 0 && l == 0){
      expect(c.io.out.bits(0), FixedPoint.toBigInt(testArray(1)(0)(0)(0), 27))
      expect(c.io.out.bits(1), FixedPoint.toBigInt(testArray(0)(1)(0)(0), 27))
      expect(c.io.out.bits(2), FixedPoint.toBigInt(testArray(0)(0)(1)(0), 27))
      expect(c.io.out.bits(3), FixedPoint.toBigInt(testArray(0)(0)(0)(1), 27))
      expect(c.io.out.bits(4), FixedPoint.toBigInt(testArray(0)(0)(0)(0), 27))
      expect(c.io.out.valid, true)
      println("Access tap 0 is " + FixedPoint.toDouble(peek(c.io.out.bits(0)), 27))
      println("Access tap 1 is " + FixedPoint.toDouble(peek(c.io.out.bits(1)), 27))
      println("Access tap 2 is " + FixedPoint.toDouble(peek(c.io.out.bits(2)), 27))
      println("Access tap 3 is " + FixedPoint.toDouble(peek(c.io.out.bits(3)), 27))
      println("Access tap 4 is " + FixedPoint.toDouble(peek(c.io.out.bits(4)), 27))
    }
    if(i == 2 && j == 0 && k == 0 && l == 0){
      expect(c.io.out.bits(0), FixedPoint.toBigInt(testArray(2)(0)(0)(0), 27))
      expect(c.io.out.bits(1), FixedPoint.toBigInt(testArray(1)(1)(0)(0), 27))
      expect(c.io.out.bits(2), FixedPoint.toBigInt(testArray(1)(0)(1)(0), 27))
      expect(c.io.out.bits(3), FixedPoint.toBigInt(testArray(1)(0)(0)(1), 27))
      expect(c.io.out.bits(4), FixedPoint.toBigInt(testArray(1)(0)(0)(0), 27))
      expect(c.io.out.bits(5), FixedPoint.toBigInt(testArray(0)(19)(19)(19), 27))
      expect(c.io.out.bits(6), FixedPoint.toBigInt(testArray(0)(19)(19)(0), 27))
      expect(c.io.out.bits(7), FixedPoint.toBigInt(testArray(0)(19)(0)(0), 27))
      expect(c.io.out.bits(8), FixedPoint.toBigInt(testArray(0)(0)(0)(0), 27))
      expect(c.io.out.valid, true)
      println("Access tap 0 is " + FixedPoint.toDouble(peek(c.io.out.bits(0)), 27))
      println("Access tap 1 is " + FixedPoint.toDouble(peek(c.io.out.bits(1)), 27))
      println("Access tap 2 is " + FixedPoint.toDouble(peek(c.io.out.bits(2)), 27))
      println("Access tap 3 is " + FixedPoint.toDouble(peek(c.io.out.bits(3)), 27))
      println("Access tap 4 is " + FixedPoint.toDouble(peek(c.io.out.bits(4)), 27))
    }
    //print()
    step(1)
  }
  //expect(c.io.out_data(0), 9)
}

class test_reuseBuffer extends FlatSpec with Matchers {
  //  implicit val p = config.Parameters.root((new Mat_VecConfig).toInstance)
  //  implicit val p = config.Parameters.root((new CoreConfig ++ new MiniConfig).toInstance)
  //  implicit val p = new shell.DefaultDe10Config ++ Parameters.root((new MiniConfig).toInstance)

  it should "Typ Compute Tester" in {
    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir"),
      () => new shift_buffer4d(32, 27, 20, 20, 20, 20)) {
      c => new ReuseBufferTester(c)
    } should be(true)
  }
}

