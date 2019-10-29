/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.island.ohara.configurator

import com.island.ohara.client.configurator.v0.WorkerApi.ConnectorDefinition
import com.island.ohara.common.rule.OharaTest
import com.island.ohara.configurator.fake.FakeWorkerClient
import org.junit.Test
import org.scalatest.Matchers

/**
  * the definitions of official connectors should define the "orderInGroup"
  */
class TestOrderInGroup extends OharaTest with Matchers {

  @Test
  def test(): Unit = {
    val illegalConnectors =
      FakeWorkerClient.localConnectorDefinitions
        .map(d => ConnectorDefinition(d.className, d.settingDefinitions.filter(_.orderInGroup() < 0)))
        .filter(_.settingDefinitions.nonEmpty)
    if (illegalConnectors.nonEmpty)
      throw new AssertionError(
        illegalConnectors
          .map(d =>
            s"the following definitions in ${d.className} have illegal orderInGroup. ${d.settingDefinitions
              .map(d => s"${d.key()} has orderInGroup:${d.orderInGroup()}")
              .mkString(",")}")
          .mkString(","))
  }
}
