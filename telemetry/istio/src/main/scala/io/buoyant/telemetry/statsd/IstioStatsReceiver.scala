package io.buoyant.telemetry.istio

import java.util.concurrent.ConcurrentHashMap
import com.twitter.finagle.stats.{Counter, Stat, StatsReceiverWithCumulativeGauges}
import scala.collection.JavaConverters._

private[telemetry] object IstioStatsReceiver {
  // from https://github.com/researchgate/diamond-linkerd-collector/
  private[istio] def mkName(name: Seq[String]): String = {
    name.mkString("/")
      .replaceAll("[^/A-Za-z0-9]", "_")
      .replace("//", "/")
      .replace("/", ".") // http://graphite.readthedocs.io/en/latest/feeding-carbon.html#step-1-plan-a-naming-hierarchy
  }
}

private[telemetry] class IstioStatsReceiver(
  sampleRate: Double
) extends StatsReceiverWithCumulativeGauges {
  import IstioStatsReceiver._

  val repr: AnyRef = this

  private[istio] def flush(): Unit = {
    gauges.values.foreach(_.send)
  }
  private[istio] def close(): Unit = {}

  private[this] val gauges = new ConcurrentHashMap[String, Metric.Gauge].asScala

  protected[this] def registerGauge(name: Seq[String], f: => Float): Unit = {
    val istioName = mkName(name)
    gauges(istioName) = new Metric.Gauge(istioName, f)
  }

  protected[this] def deregisterGauge(name: Seq[String]): Unit = {
    val _ = gauges.remove(mkName(name))
  }

  def counter(name: String*): Counter =
    new Metric.Counter(mkName(name), sampleRate)

  def stat(name: String*): Stat =
    new Metric.Stat(mkName(name), sampleRate)

}
