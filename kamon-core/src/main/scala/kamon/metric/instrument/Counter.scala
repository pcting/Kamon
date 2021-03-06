/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.metric.instrument

import kamon.jsr166.LongAdder
import kamon.metric.{ CollectionContext, MetricSnapshot, MetricRecorder }

trait Counter extends MetricRecorder {
  type SnapshotType = Counter.Snapshot

  def increment(): Unit
  def increment(times: Long): Unit
}

object Counter {

  def apply(): Counter = new LongAdderCounter

  trait Snapshot extends MetricSnapshot {
    type SnapshotType = Counter.Snapshot

    def count: Long
    def merge(that: Counter.Snapshot, context: CollectionContext): Counter.Snapshot
  }
}

class LongAdderCounter extends Counter {
  private val counter = new LongAdder

  def increment(): Unit = counter.increment()

  def increment(times: Long): Unit = {
    if (times < 0)
      throw new UnsupportedOperationException("Counters cannot be decremented")
    counter.add(times)
  }

  def collect(context: CollectionContext): Counter.Snapshot = CounterSnapshot(counter.sumThenReset())

  def cleanup: Unit = {}
}

case class CounterSnapshot(count: Long) extends Counter.Snapshot {
  def merge(that: Counter.Snapshot, context: CollectionContext): Counter.Snapshot = CounterSnapshot(count + that.count)
}