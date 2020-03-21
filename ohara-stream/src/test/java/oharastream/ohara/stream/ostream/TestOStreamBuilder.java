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

package oharastream.ohara.stream.ostream;

import oharastream.ohara.common.rule.OharaTest;
import oharastream.ohara.common.util.CommonUtils;
import org.junit.Test;

public class TestOStreamBuilder extends OharaTest {

  @Test(expected = NullPointerException.class)
  public void nullBootstrapServers() {
    OStreamBuilder.builder().bootstrapServers(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyBootstrapServers() {
    OStreamBuilder.builder().bootstrapServers("");
  }

  @Test(expected = NullPointerException.class)
  public void nullAppId() {
    OStreamBuilder.builder().appId(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyAppId() {
    OStreamBuilder.builder().appId("");
  }

  @Test(expected = NullPointerException.class)
  public void nullFromTopic() {
    OStreamBuilder.builder().fromTopic(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyFromTopic() {
    OStreamBuilder.builder().fromTopic("");
  }

  @Test(expected = NullPointerException.class)
  public void nullToTopic() {
    OStreamBuilder.builder().toTopic(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyToTopic() {
    OStreamBuilder.builder().toTopic("");
  }

  @Test
  public void minimumBuilder() {
    OStreamBuilder.builder()
        .bootstrapServers(CommonUtils.randomString())
        .appId(CommonUtils.randomString())
        .fromTopic(CommonUtils.randomString())
        .toTopic(CommonUtils.randomString())
        .build();
  }
}
