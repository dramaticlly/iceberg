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
package org.apache.iceberg.metrics;

import java.util.concurrent.TimeUnit;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.immutables.value.Value;

/** Carries all metrics for a particular commit */
@Value.Immutable
public abstract class CommitMetrics {
  public static final String TOTAL_DURATION = "total-duration";
  public static final String ATTEMPTS = "attempts";
  public static final String TOTAL_DATA_MANIFEST_SIZE_BYTES = "total-data-manifest-size-bytes";
  public static final String TOTAL_DATA_MANIFEST_COUNT = "total-delete-manifest-count";

  public static CommitMetrics noop() {
    return CommitMetrics.of(MetricsContext.nullMetrics());
  }

  public abstract MetricsContext metricsContext();

  @Value.Derived
  public Timer totalDuration() {
    return metricsContext().timer(TOTAL_DURATION, TimeUnit.NANOSECONDS);
  }

  @Value.Derived
  public Counter attempts() {
    return metricsContext().counter(ATTEMPTS, Unit.COUNT);
  }

  @Value.Derived
  public Counter totalDataManifestCount() {
    return metricsContext().counter(TOTAL_DATA_MANIFEST_COUNT, Unit.COUNT);
  }

  @Value.Derived
  public Counter totalDataManifestSizeBytes() {
    return metricsContext().counter(TOTAL_DATA_MANIFEST_SIZE_BYTES, Unit.COUNT);
  }

  public static CommitMetrics of(MetricsContext metricsContext) {
    return ImmutableCommitMetrics.builder().metricsContext(metricsContext).build();
  }
}
