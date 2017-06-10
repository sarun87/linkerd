package io.buoyant.telemetry.istio

import org.scalatest._

class MetricTest extends FunSuite {

  test("Counter increments a istio counter") {
    val name = "foo"
    val value = 123
    val istioClient = new MockIstioClient
    val counter = new Metric.Counter(istioClient, name, 1.0d)
    counter.incr(value)

    assert(istioClient.lastName == name)
    assert(istioClient.lastValue == value.toString)
  }

  test("Stat records a istio execution time") {
    val name = "foo"
    val value = 123.4F
    val istioClient = new MockIstioClient
    val stat = new Metric.Stat(istioClient, name, 1.0d)
    stat.add(value)

    assert(istioClient.lastName == name)
    assert(istioClient.lastValue == value.toLong.toString)
  }

  test("Gauge records a istio gauge value on every send") {
    val name = "foo"
    var value = 123.4F
    def func(): Float = { value += value; value }
    val istioClient = new MockIstioClient
    val gauge = new Metric.Gauge(istioClient, name, func)

    gauge.send
    assert(istioClient.lastName == name)
    assert(istioClient.lastValue == value.toDouble.toString)

    gauge.send
    assert(istioClient.lastName == name)
    assert(istioClient.lastValue == value.toDouble.toString)
  }
}
