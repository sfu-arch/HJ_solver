package stencil

import chisel3._
import chisel3.util._
import chisel3.internal.naming.chiselName  // can't use chisel3_ version because of compile order

/** Used to generate an inline (logic directly in the containing Module, no internal Module is created)
 * hardware counter.
 *
 * Typically instantiated with apply methods in [[Counter$ object Counter]]
 *
 * Does not create a new Chisel Module
 *
 * @example {{{
 *   val countOn = true.B // increment counter every clock cycle
 *   val (counterValue, counterWrap) = Counter(countOn, 4)
 *   when (counterValue === 3.U) {
 *     ...
 *   }
 * }}}
 *
 * @param n number of counts before the counter resets (or one more than the
 * maximum output value of the counter), need not be a power of two
 */
class offset_counter(val n: Int, val start: Int, val offset: Int) {
  require(n >= 0, s"OffsetCounter value must be nonnegative, got: $n")
  val value = if (n > 1) RegInit(start.U(log2Ceil(n).W)) else 0.U

  /** Increment the counter
   *
   * @note The incremented value is registered and will be visible on the next clock cycle
   * @return whether the counter will wrap to zero on the next cycle
   */
  def inc(): Bool = {
    if (n > 1) {
      val wrap = value === (n - offset + start).U
      value := value + offset.U
      if (!isPow2(n)) {
        when (wrap) { value := start.U }
      }
      wrap
    } else {
      true.B
    }
  }
}

object offset_counter
{
  /** Instantiate a [[Counter! counter]] with the specified number of counts.
   */
  def apply(n: Int, start: Int, offset: Int): offset_counter = new offset_counter(n, start, offset)

  /** Instantiate a [[Counter! counter]] with the specified number of counts and a gate.
   *
   * @param cond condition that controls whether the counter increments this cycle
   * @param n number of counts before the counter resets
   * @return tuple of the counter value and whether the counter will wrap (the value is at
   * maximum and the condition is true).
   */
  def apply(cond: Bool, n: Int, start: Int, offset: Int): (UInt, Bool) = {
    val c = new offset_counter(n, start, offset)
    val wrap = WireInit(false.B)
    when (cond) { wrap := c.inc() }
    (c.value, wrap)
  }
}
