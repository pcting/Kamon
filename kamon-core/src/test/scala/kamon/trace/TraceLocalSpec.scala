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

package kamon.trace

import akka.testkit.TestKit
import akka.actor.ActorSystem
import org.scalatest.{ OptionValues, Matchers, WordSpecLike }
import org.scalatest.concurrent.PatienceConfiguration

class TraceLocalSpec extends TestKit(ActorSystem("trace-local-spec")) with WordSpecLike with Matchers
    with PatienceConfiguration with OptionValues {

  object SampleTraceLocalKey extends TraceLocal.TraceLocalKey { type ValueType = String }

  "the TraceLocal storage" should {
    "allow storing and retrieving values" in {
      TraceRecorder.withNewTraceContext("store-and-retrieve-trace-local") {
        val testString = "Hello World"

        TraceLocal.store(SampleTraceLocalKey)(testString)
        TraceLocal.retrieve(SampleTraceLocalKey).value should equal(testString)
      }
    }

    "return None when retrieving a non existent key" in {
      TraceRecorder.withNewTraceContext("non-existent-key") {
        TraceLocal.retrieve(SampleTraceLocalKey) should equal(None)
      }
    }

    "return None when retrieving a key without a current TraceContext" in {
      TraceLocal.retrieve(SampleTraceLocalKey) should equal(None)
    }

    "be attached to the TraceContext when it is propagated" in {
      val testString = "Hello World"
      val testContext = TraceRecorder.withNewTraceContext("manually-propagated-trace-local") {
        TraceLocal.store(SampleTraceLocalKey)(testString)
        TraceLocal.retrieve(SampleTraceLocalKey).value should equal(testString)
        TraceRecorder.currentContext
      }

      /** No TraceLocal should be available here */
      TraceLocal.retrieve(SampleTraceLocalKey) should equal(None)

      TraceRecorder.withTraceContext(testContext) {
        TraceLocal.retrieve(SampleTraceLocalKey).value should equal(testString)
      }
    }
  }
}
