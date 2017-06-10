package io.buoyant.telemetry.istio

import com.twitter.finagle.stats.{Counter => FCounter, Stat => FStat}

private[istio] object Metric {

  // stats (timing/histograms) only send when Math.random() <= sampleRate
  class Counter(name: String, sampleRate: Double) extends FCounter {
    def incr(delta: Int): Unit = {}
  }

  // gauges simply evaluate on send
  class Gauge(name: String, f: => Float) {
    def send: Unit = {}
  }

  // stats (timing/histograms) only send when Math.random() <= sampleRate
  class Stat(name: String, sampleRate: Double) extends FStat {
    def add(value: Float): Unit = {}
  }
}
