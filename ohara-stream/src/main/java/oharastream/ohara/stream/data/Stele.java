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

package oharastream.ohara.stream.data;

import java.io.Serializable;
import oharastream.ohara.common.data.BasicObject;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * The {@code Stele} represents a operation in the stream. This object contains the basic
 * information from {@code processor} of a {@code topology}.
 *
 * <p>The description of each fields are listed below:
 *
 * <ul>
 *   <li>{@link #kind}
 *   <li>{@link #key}
 *   <li>{@link #name}
 *   <li>{@link #from}
 *   <li>{@link #to}
 * </ul>
 *
 * @see org.apache.kafka.streams.Topology
 */
public final class Stele extends BasicObject implements Serializable {
  private static final long serialVersionUID = 1L;
  /**
   * The type of this {@code stele}. Should be one of the following : <b>Source</b>, <b>Sink</b>, or
   * <b>Processor</b>
   */
  private final String kind;
  /**
   * The origin name of format <i>KSTREAM-{processor}-{incremental number}</i> in {@code topology}
   */
  private final String key;
  /** The topic name if {@link #kind} is "Source" or "Sink", {@code empty} otherwise. */
  private final String name;
  // We don't use "generic types" (ex: List<E> or Collection<E>) here for generate json string
  // Since java uses type erasure, we cannot get the "actual" type during runtime
  // for example,
  // class Foo {
  //   List<String> list;
  //   public Foo(List<String> list){ this.list = list; }
  // }
  // String[] aa = {"a","b"};
  // Foo foo = new Foo(Arrays.asList(aa));
  // System.out.println(ReflectionToStringBuilder.toString(foo, ToStringStyle.JSON_STYLE));
  //
  // We expect value is : ["a", "b"], but will get the non-json array : [a, b]
  /**
   * The {@link #key} of source operations. Could be {@code empty} if this is a <i>source stele</i>.
   */
  private final String[] from;
  /**
   * The {@link #key} of target operations. Could be {@code empty} if this is a <i>target stele</i>.
   */
  private final String[] to;

  static {
    ToStringBuilder.setDefaultStyle(ToStringStyle.JSON_STYLE);
  }

  public Stele(String kind, String key, String name, String[] from, String[] to) {
    this.kind = kind;
    this.key = key;
    this.name = name;
    this.from = from;
    this.to = to;
  }

  public String getKind() {
    return kind;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public String[] getFrom() {
    return from;
  }

  public String[] getTo() {
    return to;
  }
}
