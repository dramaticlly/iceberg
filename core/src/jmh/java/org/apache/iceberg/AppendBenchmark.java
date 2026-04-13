/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg;

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;

/**
 * A benchmark that evaluates the performance of appending files to the table across different
 * schema widths and flush modes (baseline vs dynamic auto-flush).
 *
 * <p>To run this benchmark: <code>
 *   ./gradlew :iceberg-core:jmh
 *       -PjmhIncludeRegex=AppendBenchmark
 *       -PjmhOutputPath=benchmark/append-benchmark.txt
 * </code>
 */
@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@BenchmarkMode(Mode.SingleShotTime)
@Timeout(time = 10, timeUnit = TimeUnit.MINUTES)
public class AppendBenchmark {

  private static final String TABLE_IDENT = "tbl";
  private static final PartitionSpec SPEC = PartitionSpec.unpartitioned();
  private static final HadoopTables TABLES = new HadoopTables();
  private static final int NUM_FILES = 100_000;

  // Narrow table: 5 columns
  private static final Schema NARROW_SCHEMA =
      new Schema(
          required(1, "id", Types.LongType.get()),
          required(2, "name", Types.StringType.get()),
          required(3, "value", Types.DoubleType.get()),
          required(4, "ts", Types.TimestampType.withZone()),
          optional(5, "tag", Types.StringType.get()));

  // Standard fact table: 30 columns
  private static final Schema FACT_TABLE_SCHEMA =
      new Schema(
          required(1, "event_id", Types.LongType.get()),
          required(2, "user_id", Types.LongType.get()),
          required(3, "session_id", Types.StringType.get()),
          required(4, "event_type", Types.StringType.get()),
          required(5, "event_ts", Types.TimestampType.withZone()),
          required(6, "date_col", Types.DateType.get()),
          optional(7, "amount", Types.DecimalType.of(18, 6)),
          optional(8, "quantity", Types.IntegerType.get()),
          optional(9, "price", Types.DecimalType.of(10, 2)),
          optional(10, "currency", Types.StringType.get()),
          optional(11, "country", Types.StringType.get()),
          optional(12, "region", Types.StringType.get()),
          optional(13, "city", Types.StringType.get()),
          optional(14, "device_type", Types.StringType.get()),
          optional(15, "os", Types.StringType.get()),
          optional(16, "browser", Types.StringType.get()),
          optional(17, "referrer", Types.StringType.get()),
          optional(18, "page_url", Types.StringType.get()),
          optional(19, "campaign_id", Types.StringType.get()),
          optional(20, "channel", Types.StringType.get()),
          optional(21, "is_mobile", Types.BooleanType.get()),
          optional(22, "is_converted", Types.BooleanType.get()),
          optional(23, "duration_ms", Types.LongType.get()),
          optional(24, "response_code", Types.IntegerType.get()),
          optional(25, "error_msg", Types.StringType.get()),
          optional(26, "payload_size", Types.LongType.get()),
          optional(27, "latitude", Types.DoubleType.get()),
          optional(28, "longitude", Types.DoubleType.get()),
          optional(29, "ip_address", Types.StringType.get()),
          optional(30, "user_agent", Types.StringType.get()));

  // ML features table: 150 columns
  private static final Schema ML_FEATURES_SCHEMA = buildMlFeaturesSchema();

  private static Schema buildMlFeaturesSchema() {
    List<Types.NestedField> fields = Lists.newArrayList();
    fields.add(required(1, "sample_id", Types.LongType.get()));
    fields.add(required(2, "label", Types.DoubleType.get()));
    fields.add(required(3, "timestamp", Types.TimestampType.withZone()));
    fields.add(required(4, "model_version", Types.StringType.get()));
    fields.add(required(5, "split", Types.StringType.get()));
    for (int i = 6; i <= 150; i++) {
      fields.add(optional(i, "feature_" + (i - 5), Types.DoubleType.get()));
    }
    return new Schema(fields);
  }

  private Table table;

  @Param({"narrow", "fact_table", "ml_features"})
  private String schemaType;

  @Param({"dynamic", "baseline"})
  private String flushMode;

  @AuxCounters(AuxCounters.Type.EVENTS)
  @State(Scope.Thread)
  @SuppressWarnings("checkstyle:VisibilityModifier")
  public static class ManifestCounters {
    public int manifestCount;

    @Setup(Level.Iteration)
    public void reset() {
      manifestCount = 0;
    }
  }

  @Setup
  public void setupBenchmark() {
    dropTable();
    initTable();
  }

  @TearDown
  public void tearDownBenchmark() {
    dropTable();
  }

  @Benchmark
  @Threads(1)
  public void appendFiles(ManifestCounters counters) {
    AppendFiles append;
    if ("baseline".equals(flushMode)) {
      TableOperations ops = ((HasTableOperations) table).operations();
      append = new MergeAppend(table.name(), ops, Integer.MAX_VALUE);
    } else {
      append = table.newAppend();
    }

    for (int ordinal = 0; ordinal < NUM_FILES; ordinal++) {
      append.appendFile(FileGenerationUtil.generateDataFile(table, null));
    }

    append.commit();
    long snapshotId = table.currentSnapshot().snapshotId();
    counters.manifestCount =
        (int)
            table.currentSnapshot().dataManifests(table.io()).stream()
                .filter(m -> m.snapshotId() == snapshotId)
                .count();
  }

  private Schema selectedSchema() {
    switch (schemaType) {
      case "narrow":
        return NARROW_SCHEMA;
      case "fact_table":
        return FACT_TABLE_SCHEMA;
      case "ml_features":
        return ML_FEATURES_SCHEMA;
      default:
        throw new IllegalArgumentException("Unknown schema type: " + schemaType);
    }
  }

  private void initTable() {
    this.table = TABLES.create(selectedSchema(), SPEC, TABLE_IDENT);
  }

  private void dropTable() {
    TABLES.dropTable(TABLE_IDENT);
  }
}
