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

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javassist.*;
import javassist.bytecode.ClassFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DataUtils {

  private static final Logger log = LoggerFactory.getLogger(DataUtils.class);

  /**
   * Dynamic generate class from csv file and get all data (note : you should give the header in
   * file) The result class name will be {@code CSV_{filename.toUpper}}
   *
   * @param filename the csv file name
   * @return the data collection of this file
   */
  static List<?> readData(String filename) throws Exception {
    String prefix = "src/test/data";
    Path p = Paths.get(prefix, File.separator, filename);
    if (!p.toFile().exists()) throw new FileNotFoundException(filename + " not exists.");
    Optional<String> header = Files.lines(p).findFirst();
    String trueHeaders =
        header.orElseThrow(
            () -> new RuntimeException("the file : " + filename + " has no header."));
    String className = filename.replace(".csv", "").toUpperCase();

    Class<?> rowClass = buildClass(className, trueHeaders);

    return Files.lines(p)
        .skip(1)
        .filter(s -> s != null && !s.isEmpty())
        .map(line -> line.split(","))
        .map(
            values -> {
              try {
                Object rowObject = rowClass.getDeclaredConstructor().newInstance();
                Field[] fields = rowClass.getDeclaredFields();
                for (int i = 0; i < fields.length; i++) {
                  fields[i].setAccessible(true);
                  fields[i].set(rowObject, values[i]);
                }
                return rowObject;
              } catch (Exception e) {
                log.error(e.getMessage());
                return null;
              }
            })
        .collect(Collectors.toList());
  }

  private static Class<?> buildClass(String className, String headers)
      throws CannotCompileException, NotFoundException {
    String[] fields = headers.split(",");
    ClassPool pool = ClassPool.getDefault();
    CtClass ctc = pool.makeClass("CSV_" + className);
    ClassFile classFile = ctc.getClassFile();

    classFile.setSuperclass(Object.class.getName());
    for (String fieldName : fields) {
      CtField field = new CtField(pool.get(String.class.getName()), fieldName, ctc);
      ctc.addField(field);
    }
    return ctc.toClass();
  }
}
