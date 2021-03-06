package kamon.metric

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKitBase }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.TraceMetrics.TraceMetricsSnapshot
import kamon.trace.TraceContext.SegmentIdentity
import kamon.trace.TraceRecorder
import org.scalatest.{ Matchers, WordSpecLike }

class TraceMetricsSpec extends TestKitBase with WordSpecLike with Matchers with ImplicitSender {
  implicit lazy val system: ActorSystem = ActorSystem("trace-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  tick-interval = 1 hour
      |  default-collection-context-buffer-size = 10
      |
      |  filters = [
      |    {
      |      trace {
      |        includes = [ "*" ]
      |        excludes = [ "non-tracked-trace"]
      |      }
      |    }
      |  ]
      |  precision {
      |    default-histogram-precision {
      |      highest-trackable-value = 3600000000000
      |      significant-value-digits = 2
      |    }
      |
      |    default-min-max-counter-precision {
      |      refresh-interval = 1 second
      |      highest-trackable-value = 999999999
      |      significant-value-digits = 2
      |    }
      |  }
      |}
    """.stripMargin))

  "the TraceMetrics" should {
    "record the elapsed time between a trace creation and finish" in {
      for (repetitions ← 1 to 10) {
        TraceRecorder.withNewTraceContext("record-elapsed-time") {
          TraceRecorder.finish()
        }
      }

      val snapshot = takeSnapshotOf("record-elapsed-time")
      snapshot.elapsedTime.numberOfMeasurements should be(10)
      snapshot.segments shouldBe empty
    }

    "record the elapsed time for segments that occur inside a given trace" in {
      TraceRecorder.withNewTraceContext("trace-with-segments") {
        val segmentHandle = TraceRecorder.startSegment(TraceMetricsTestSegment("test-segment"))
        segmentHandle.get.finish()
        TraceRecorder.finish()
      }

      val snapshot = takeSnapshotOf("trace-with-segments")
      snapshot.elapsedTime.numberOfMeasurements should be(1)
      snapshot.segments.size should be(1)
      snapshot.segments(TraceMetricsTestSegment("test-segment")).numberOfMeasurements should be(1)
    }

    "record the elapsed time for segments that finish after their correspondent trace has finished" in {
      val segmentHandle = TraceRecorder.withNewTraceContext("closing-segment-after-trace") {
        val sh = TraceRecorder.startSegment(TraceMetricsTestSegment("test-segment"))
        TraceRecorder.finish()
        sh
      }

      val beforeFinishSegmentSnapshot = takeSnapshotOf("closing-segment-after-trace")
      beforeFinishSegmentSnapshot.elapsedTime.numberOfMeasurements should be(1)
      beforeFinishSegmentSnapshot.segments.size should be(0)

      segmentHandle.get.finish()

      val afterFinishSegmentSnapshot = takeSnapshotOf("closing-segment-after-trace")
      afterFinishSegmentSnapshot.elapsedTime.numberOfMeasurements should be(0)
      afterFinishSegmentSnapshot.segments.size should be(1)
      afterFinishSegmentSnapshot.segments(TraceMetricsTestSegment("test-segment")).numberOfMeasurements should be(1)
    }
  }

  case class TraceMetricsTestSegment(name: String) extends SegmentIdentity

  def takeSnapshotOf(traceName: String): TraceMetricsSnapshot = {
    val recorder = Kamon(Metrics).register(TraceMetrics(traceName), TraceMetrics.Factory)
    val collectionContext = Kamon(Metrics).buildDefaultCollectionContext
    recorder.get.collect(collectionContext)
  }
}
