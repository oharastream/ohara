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

import java.util.ArrayList;
import java.util.List;
import oharastream.ohara.common.data.Pair;

/** Assign a condition pair (left key and right key) list for the required operation */
public class Conditions {

  private List<Pair<String, String>> conditionsPairList;

  private Conditions() {
    this.conditionsPairList = new ArrayList<>();
  }

  public static Conditions create() {
    return new Conditions();
  }

  /**
   * Add new condition of leftTopic.key with rightTopic.key pair. you can add multiple key pairs for
   * different situations. Notes: the key should be contained in data header, i.e, the {@code
   * Row.names()}
   *
   * @param pair the conditions of key pair for join
   * @return the conditions
   */
  public Conditions add(List<Pair<String, String>> pair) {
    this.conditionsPairList.addAll(pair);
    return this;
  }

  List<Pair<String, String>> getConditionList() {
    return this.conditionsPairList;
  }
}
