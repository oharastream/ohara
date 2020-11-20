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

package oharastream.ohara.kafka.connector.csv.source;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import oharastream.ohara.common.data.Cell;
import oharastream.ohara.common.data.Column;
import oharastream.ohara.common.data.DataType;
import oharastream.ohara.common.data.Row;
import oharastream.ohara.common.rule.OharaTest;
import oharastream.ohara.common.setting.TopicKey;
import oharastream.ohara.kafka.connector.RowSourceContext;
import oharastream.ohara.kafka.connector.RowSourceRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestCsvRecordConverter extends OharaTest {
  private final Set<TopicKey> topicKeys =
      new HashSet<>(Arrays.asList(TopicKey.of("g", "t0"), TopicKey.of("g", "t1")));
  private final List<Column> schema =
      Arrays.asList(
          Column.builder().name("cf1").dataType(DataType.STRING).order(0).build(),
          Column.builder().name("cf2").dataType(DataType.STRING).order(1).build(),
          Column.builder().name("cf3").dataType(DataType.STRING).order(2).build());

  private String path;
  private File tempFile;
  private Map<Integer, List<Cell<String>>> data;

  @BeforeEach
  public void setup() throws IOException {
    tempFile = File.createTempFile("test", "csv");
    path = tempFile.getName();
  }

  private CsvRecordConverter createConverter() {
    return CsvRecordConverter.builder()
        .path(path)
        .topicKeys(topicKeys)
        .offsetCache(new FakeOffsetCache())
        .build();
  }

  private CsvRecordConverter createConverter(List<Column> schema) {
    return CsvRecordConverter.builder()
        .path(path)
        .topicKeys(topicKeys)
        .offsetCache(new FakeOffsetCache())
        .schema(schema)
        .build();
  }

  private BufferedReader createReader() throws IOException {
    return new BufferedReader(
        new InputStreamReader(new FileInputStream(tempFile), StandardCharsets.UTF_8));
  }

  private Map<Integer, List<Cell<String>>> setupInputData() {
    String[] header = new String[] {"cf1", "cf2", "cf3"};
    String[] line1 = new String[] {"a", "b", "c"};
    String[] line2 = new String[] {"a", "d", "c"};
    String[] line3 = new String[] {"a", "f", "c"};

    String lineSeparator = System.getProperty("line.separator");

    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(String.join(",", header) + lineSeparator);
      writer.write(String.join(",", line1) + lineSeparator);
      writer.write(String.join(",", line2) + lineSeparator);
      writer.write(String.join(",", line3) + lineSeparator);
    } catch (IOException e) {
      e.printStackTrace();
    }

    Map<Integer, List<Cell<String>>> data = new HashMap<>();
    data.put(
        1,
        IntStream.range(0, header.length)
            .mapToObj(index -> Cell.of(header[index], line1[index]))
            .collect(Collectors.toUnmodifiableList()));
    data.put(
        2,
        IntStream.range(0, header.length)
            .mapToObj(index -> Cell.of(header[index], line2[index]))
            .collect(Collectors.toUnmodifiableList()));
    data.put(
        3,
        IntStream.range(0, header.length)
            .mapToObj(index -> Cell.of(header[index], line3[index]))
            .collect(Collectors.toUnmodifiableList()));

    return data;
  }

  @Test
  public void testTransform() {
    var converter = createConverter();
    data = setupInputData();
    System.out.println(data);
    Map<Integer, Row> transformedData = converter.transform(data);
    System.out.println(transformedData);
    Assertions.assertEquals(mapToRow(data), converter.transform(data));
  }

  @Test
  public void testTransform_WithFullSchema() {
    var converter = createConverter(schema);
    data = setupInputData();
    Map<Integer, Row> transformedData = converter.transform(data);
    Assertions.assertEquals(data.size(), transformedData.size());
    Assertions.assertEquals(mapToRow(data), transformedData);
  }

  @Test
  public void testTransform_WithSingleColumn() {
    Column column = Column.builder().name("cf1").dataType(DataType.STRING).order(0).build();
    var converter = createConverter(List.of(column));
    data = setupInputData();
    Map<Integer, Row> transformedData = converter.transform(data);
    Assertions.assertEquals(data.size(), transformedData.size());
    transformedData
        .values()
        .forEach(
            row -> {
              Assertions.assertEquals(1, row.size());
              Assertions.assertEquals("a", row.cell(column.newName()).value());
            });
  }

  @SuppressWarnings({"rawtypes"})
  private Map<Integer, Row> mapToRow(Map<Integer, List<Cell<String>>> data) {
    return data.entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> Row.of(e.getValue().toArray(new Cell[0]))));
  }

  @Test
  public void testFindCellByName() {
    var converter = createConverter();
    data = setupInputData();
    List<Cell<String>> cells = data.get(3);
    Cell<String> cell = converter.findCellByName(cells, "cf3");
    Assertions.assertEquals("cf3", cell.name());
    Assertions.assertEquals("c", cell.value());
  }

  @Test
  public void testConvertByType() {
    var converter = createConverter();
    Assertions.assertTrue(converter.convertByType("true", DataType.BOOLEAN) instanceof Boolean);
    Assertions.assertTrue(converter.convertByType("127", DataType.BYTE) instanceof Byte);
    Assertions.assertTrue(converter.convertByType("1", DataType.SHORT) instanceof Short);
    Assertions.assertTrue(converter.convertByType("2", DataType.INT) instanceof Integer);
    Assertions.assertTrue(converter.convertByType("3", DataType.LONG) instanceof Long);
    Assertions.assertTrue(converter.convertByType("4", DataType.FLOAT) instanceof Float);
    Assertions.assertTrue(converter.convertByType("5", DataType.DOUBLE) instanceof Double);
    Assertions.assertTrue(converter.convertByType("str", DataType.STRING) instanceof String);
    Assertions.assertNotNull(converter.convertByType("obj", DataType.OBJECT));
  }

  @Test
  public void testConvertByType_ThrowNumberFormatException() {
    var converter = createConverter();
    Assertions.assertThrows(
        NumberFormatException.class, () -> converter.convertByType("128", DataType.BYTE));
  }

  @Test
  public void testConvertByType_ThrowIllegalArgumentException() {
    var converter = createConverter();
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> converter.convertByType("row", DataType.ROW));
  }

  @Test
  public void testToRecords_BySingleRow() {
    var converter = createConverter();
    data = setupInputData();
    Map<Integer, Row> rows = mapToRow(data);

    int index = 1;
    Row row = rows.get(index);
    List<RowSourceRecord> records = converter.toRecords(row, index);
    Assertions.assertEquals(records.size(), topicKeys.size());
    for (RowSourceRecord record : records) {
      Assertions.assertTrue(topicKeys.contains(record.topicKey()));
      Assertions.assertEquals(
          Map.of(CsvRecordConverter.CSV_PARTITION_KEY, path), record.sourcePartition());
      Assertions.assertEquals(
          Map.of(CsvRecordConverter.CSV_OFFSET_KEY, index), record.sourceOffset());
      Assertions.assertEquals(row, record.row());
    }
  }

  @Test
  public void testOrder() {
    var converter = createConverter();
    var row0 = Row.of(Cell.of("a", "b"));
    var row1 = Row.of(Cell.of("ccc", "ddd"));
    var records = converter.toRecords(Map.of(1, row0, 2, row1));
    Assertions.assertEquals(row0, records.get(0).row());
    // the order of records should be [0-t0][0-t1][1-t0][1-t1]
    Assertions.assertEquals(row1, records.get(topicKeys.size()).row());
  }

  @Test
  public void testToRecords_ByManyRows() {
    var converter = createConverter();
    data = setupInputData();
    Map<Integer, Row> rows = mapToRow(data);

    List<RowSourceRecord> records = converter.toRecords(rows);
    Assertions.assertEquals(topicKeys.size() * rows.size(), records.size());
  }

  @Test
  public void testToCells() throws IOException {
    var converter = createConverter();
    data = setupInputData();
    try (BufferedReader reader = createReader()) {
      Stream<String> lines = reader.lines();
      Assertions.assertEquals(data, converter.toCells(lines, Integer.MAX_VALUE));
    }
  }

  @Test
  public void testToCellsWithMaximumNumberOfLines() {
    var converter = createConverter();
    data = setupInputData();
    IntStream.range(0, data.size())
        .forEach(
            index -> {
              int expectedLines = index + 1;
              try (BufferedReader reader = createReader()) {
                Stream<String> lines = reader.lines();
                Map<Integer, List<Cell<String>>> converted =
                    converter.toCells(lines, expectedLines);
                Assertions.assertEquals(expectedLines, converted.size());
                IntStream.range(0, expectedLines)
                    .forEach(
                        cellsIndex ->
                            Assertions.assertEquals(
                                data.get(cellsIndex), converted.get(cellsIndex)));
              } catch (IOException e) {
                throw new AssertionError(e);
              }
            });
  }

  @Test
  public void testConvert() throws IOException {
    var converter = createConverter();
    data = setupInputData();
    try (BufferedReader reader = createReader()) {
      Stream<String> lines = reader.lines();
      List<RowSourceRecord> records = converter.convert(lines);
      Assertions.assertEquals(topicKeys.size() * data.size(), records.size());
    }
  }

  @Test
  public void testConvert_IfAllCached() throws IOException {
    var converter =
        CsvRecordConverter.builder()
            .path(path)
            .topicKeys(topicKeys)
            .offsetCache(
                new OffsetCache() {
                  @Override
                  public void loadIfNeed(RowSourceContext context, String path) {}

                  @Override
                  public void update(String path, int index) {}

                  @Override
                  public boolean predicate(String path, int index) {
                    return false;
                  }
                })
            .schema(schema)
            .build();
    setupInputData();
    try (BufferedReader reader = createReader()) {
      Stream<String> lines = reader.lines();
      List<RowSourceRecord> records = converter.convert(lines);
      Assertions.assertEquals(0, records.size());
    }
  }

  @Test
  public void testRegex() {
    String[] splits = "1,\"2,3,4\",5".split(CsvRecordConverter.CSV_REGEX);
    Assertions.assertEquals(3, splits.length);
    Assertions.assertEquals("1", splits[0]);
    Assertions.assertEquals("\"2,3,4\"", splits[1]);
    Assertions.assertEquals("5", splits[2]);

    String[] splits2 = "1,3,5".split(CsvRecordConverter.CSV_REGEX);
    Assertions.assertEquals(3, splits.length);
    Assertions.assertEquals("1", splits2[0]);
    Assertions.assertEquals("3", splits2[1]);
    Assertions.assertEquals("5", splits2[2]);
  }

  private static class FakeOffsetCache implements OffsetCache {
    @Override
    public void loadIfNeed(RowSourceContext context, String path) {
      // DO NOTHING
    }

    @Override
    public void update(String path, int index) {
      // DO NOTHING
    }

    @Override
    public boolean predicate(String path, int index) {
      return true;
    }
  }
}
