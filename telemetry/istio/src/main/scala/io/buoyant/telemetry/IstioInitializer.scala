package io.buoyant.telemetry

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.twitter.finagle.Stack
import com.twitter.finagle.util.DefaultTimer
import com.twitter.logging.Logger
import io.buoyant.telemetry.istio.{IstioStatsReceiver, IstioTelemeter}

class IstioInitializer extends TelemeterInitializer {
  type Config = IstioConfig
  val configClass = classOf[IstioConfig]
  override val configId = "io.l5d.istio"
}

private[telemetry] object IstioConfig {
  val DefaultPrefix = "linkerd"
  val DefaultHostname = "127.0.0.1"
  val DefaultPort = 8125
  val DefaultGaugeIntervalMs = 10000 // for gauges
  val DefaultSampleRate = 0.01d // for counters and timing/histograms

  val MaxQueueSize = 10000
}

case class IstioConfig(
  prefix: Option[String],
  hostname: Option[String],
  port: Option[Int],
  gaugeIntervalMs: Option[Int],
  @JsonDeserialize(contentAs = classOf[java.lang.Double]) sampleRate: Option[Double]
) extends TelemeterConfig {
  import IstioConfig._

  @JsonIgnore override val experimentalRequired = true

  @JsonIgnore private[this] val log = Logger.get("io.l5d.istio")

  @JsonIgnore private[this] val istioPrefix = prefix.getOrElse(DefaultPrefix)
  @JsonIgnore private[this] val istioHost = hostname.getOrElse(DefaultHostname)
  @JsonIgnore private[this] val istioPort = port.getOrElse(DefaultPort)
  @JsonIgnore private[this] val istioInterval = gaugeIntervalMs.getOrElse(DefaultGaugeIntervalMs)
  @JsonIgnore private[this] val istioSampleRate = sampleRate.getOrElse(DefaultSampleRate)

  @JsonIgnore
  def mk(params: Stack.Params): IstioTelemeter = {
    // initiate a UDP connection at startup time
    log.info(s"connecting to Istio at $istioHost:$istioPort as $istioPrefix")
    // val istioClient = new NonBlockingIstioClient(
    //   istioPrefix,
    //   istioHost,
    //   istioPort,
    //   MaxQueueSize
    // )

    new IstioTelemeter(
      istioHost,
      istioPort,
      istioInterval,
      new IstioStatsReceiver(istioSampleRate),
      istioInterval,
      DefaultTimer.twitter
    )
  }
}
