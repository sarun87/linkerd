package io.buoyant.telemetry.istio

import com.timgroup.istio.{NoOpIstioClient, IstioClient}
import com.twitter.conversions.time._
import com.twitter.util.{Time, MockTimer}
import org.scalatest._

class IstioTelemeterTest extends FunSuite {

  class MockIstioStatsReceiver(istioClient: IstioClient, sampleRate: Double)
    extends IstioStatsReceiver(istioClient: IstioClient, sampleRate: Double) {

    var flushes = 0
    var closed = false

    override private[istio] def flush(): Unit = { flushes += 1 }
    override private[istio] def close(): Unit = { closed = true }
  }

  test("creates a telemeter") {
    val stats = new MockIstioStatsReceiver(new NoOpIstioClient, 1.0d)

    val telemeter = new IstioTelemeter(
      stats,
      10000,
      new MockTimer
    )

    assert(stats.flushes == 0)
    assert(!stats.closed)
  }

  test("stops on close") {
    val stats = new MockIstioStatsReceiver(new NoOpIstioClient, 1.0d)

    val telemeter = new IstioTelemeter(
      stats,
      10000,
      new MockTimer
    )

    val closable = telemeter.run()

    assert(!stats.closed)
    val _ = closable.close(0.millis)
    assert(stats.closed)
  }

  test("flushes gauges every period until close") {
    val gaugeIntervalMs = 10000
    val stats = new MockIstioStatsReceiver(new NoOpIstioClient, 1.0d)
    val timer = new MockTimer

    val telemeter = new IstioTelemeter(
      stats,
      gaugeIntervalMs,
      timer
    )

    Time.withCurrentTimeFrozen { time =>
      val closable = telemeter.run()

      assert(stats.flushes == 0)

      time.advance(gaugeIntervalMs.millis)
      timer.tick()
      assert(stats.flushes == 1)

      time.advance(gaugeIntervalMs.millis)
      timer.tick()
      assert(stats.flushes == 2)

      val _ = closable.close(0.millis)

      time.advance(gaugeIntervalMs.millis)
      timer.tick()
      assert(stats.flushes == 2)
    }
  }
}
