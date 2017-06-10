package io.buoyant.telemetry.istio

import com.google.protobuf.CodedOutputStream
import com.twitter.conversions.time._
import com.twitter.finagle.buoyant.{h2, H2}
import com.twitter.finagle.{Address, Name, Service}
import com.twitter.finagle.http.Method
import com.twitter.finagle.service.RetryBudget
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer
import com.twitter.io.Buf
import com.twitter.logging.Logger
import com.twitter.util.{Awaitable, Closable, CloseAwaitably, Future, Time, Timer}
import io.buoyant.telemetry.{MetricsTree, Telemeter}
import istio.mixer.v1.{Attributes, Mixer, ReportRequest, ReportResponse, StringMap}
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

private[telemetry] object IstioTelemeter {
  val DefaultPeriod = 1.hour
  val ContentType = "application/octet-stream"

  case class Client(
    service: Service[h2.Request, h2.Response]
  ) {

    def apply(istioHost: String): Future[ReportResponse] = {
      val reportRequest = mkReportRequest()
      log.debug(reportRequest.toString)

      // val sz = ReportRequest.codec.sizeOf(msg)
      // val bb0 = ByteBuffer.allocate(sz)
      // val bb = bb0.duplicate()
      // ReportRequest.codec.encode(msg, CodedOutputStream.newInstance(bb))

      val stream = h2.Stream()
      val req = h2.Request("http", h2.Method.Post, "", "/", stream)
      // req.host = istioHost
      // req.content = Buf.ByteBuffer.Owned(bb0)
      // req.contentType = ContentType
      // Monitor.using(blackholeMonitor) {
      //   service(req).map(r => log.debug(r.contentString)).unit
      // }

      // service(req)

      val client = new Mixer.Client(service)
      client.report(reportRequest)
    }
  }

  // source: source.labels["app"] | "unknown" // MAP
  // target: target.service | "unknown" // dns_name
  // service: target.labels["app"] | "unknown" // map
  // method: request.path | "unknown" // STRING
  // version: target.labels["version"] | "unknown" // MAP
  // response_code: response.code | 200 // int64

  //map<sint32, sint32> strings = 2;
  //map<sint32, int64> int64s = 3;
  //map<sint32, double> doubles = 4;
  //map<sint32, bool> bools = 5;
  //map<sint32, google.protobuf.Timestamp> timestamps = 6 [(gogoproto.nullable) = false, (gogoproto.stdtime) = true];
  //map<sint32, google.protobuf.Duration> durations = 7 [(gogoproto.nullable) = false, (gogoproto.stdduration) = true];
  //map<sint32, bytes> bytes = 8;
  //map<sint32, StringMap> string_maps = 9 [(gogoproto.nullable) = false];

  def mkReportRequest(): ReportRequest =
    ReportRequest(
      attributes = Seq[Attributes](
        Attributes(
          words = Seq[String](
            "REQUEST_PATH",
            "buoyant.svc.cluster.local",
            "app",
            "SOURCE_LABELS_APP",
            "TARGET_LABELS_APP",
            "version",
            "TARGET_LABELS_VERSION"
          ),
          strings = Map[Int, Int](
            17 -> -1, // request.path
            9 -> -2 // target.service
          ),
          int64s = Map[Int, Long](
            30 -> 200 // response.code
          ),
          stringMaps = Map[Int, istio.mixer.v1.StringMap](
            5 -> // source.labels
              StringMap(
                entries = Map[Int, Int](
                  -3 -> -4 // "app" => "SOURCE_LABELS_APP"
                )
              ),
            13 -> // target.labels
              StringMap(
                entries = Map[Int, Int](
                  -3 -> -5, // "app" => "TARGET_LABELS_APP"
                  -6 -> -7 // "version" => "TARGET_LABELS_APP"
                )
              )
          )
        )
      ),
      // from https://istio.io/docs/reference/config/mixer/attribute-vocabulary.html
      defaultWords = Seq[String](
        "source.ip",
        "source.port",
        "source.name",
        "source.uid",
        "source.namespace",
        "source.labels",
        "source.user",
        "target.ip",
        "target.port",
        "target.service",
        "target.name",
        "target.uid",
        "target.namespace",
        "target.labels",
        "target.user",
        "request.headers",
        "request.id",
        "request.path",
        "request.host",
        "request.method",
        "request.reason",
        "request.referer",
        "request.scheme",
        "request.size",
        "request.time",
        "request.useragent",
        "response.headers",
        "response.size",
        "response.time",
        "response.duration",
        "response.code"
      ),
      globalWordCount = Some(31)
    )

  private[telemetry] val log = Logger.get(getClass.getName)
}

private[telemetry] class IstioTelemeter(
  val istioHost: String,
  val istioPort: Int,
  val istioInterval: Int,
  val stats: IstioStatsReceiver,
  gaugeIntervalMs: Int,
  timer: Timer
) extends Telemeter {
  import IstioTelemeter._

  // no tracer with istio
  val tracer = NullTracer

  val metricsDst = Name.bound(Address(istioHost, istioPort))

  // private[this] val baseHttpClient = Http.client
  //   .withStatsReceiver(NullStatsReceiver)
  //   .withSessionQualifier.noFailFast
  //   .withSessionQualifier.noFailureAccrual
  //   .withRetryBudget(RetryBudget.Empty)
  // private[this] val httpClient = if (withTls) baseHttpClient.withTls(istioHost) else baseHttpClient
  // private[this] val httpClient = baseHttpClient
  // private[this] val metricsService = httpClient.newService(metricsDst, "istioTelemeter")
  private[this] val metricsService = H2.client
    .withParams(H2.client.params)
    .newService(metricsDst, "istioTelemeter")

  private[this] val started = new AtomicBoolean(false)
  private[this] val pid = java.util.UUID.randomUUID().toString
  log.info(s"connecting to istio mixer at $metricsDst")
  val client = Client(metricsService)

  // only run at most once
  def run(): Closable with Awaitable[Unit] =
    if (started.compareAndSet(false, true)) run0()
    else Telemeter.nopRun

  private[this] def run0() = {
    val task = timer.schedule(gaugeIntervalMs.millis) {
      stats.flush

      val _ = client(istioHost)
    }

    val closer = Closable.all(
      task,
      Closable.make(_ => Future.value(stats.close()))
    )

    new Closable with CloseAwaitably {
      def close(deadline: Time) = closeAwaitably {
        closer.close(deadline)
      }
    }
  }
}
