// Copyright 2016 Twitter. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.heron.healthmgr.diagnosers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;

import com.microsoft.dhalion.detector.Symptom;
import com.microsoft.dhalion.diagnoser.Diagnosis;
import com.microsoft.dhalion.metrics.ComponentMetrics;
import com.microsoft.dhalion.metrics.InstanceMetrics;

import com.twitter.heron.healthmgr.sensors.BufferSizeSensor;

public class SlowInstanceDiagnoser extends BaseDiagnoser {
  private static final Logger LOG = Logger.getLogger(SlowInstanceDiagnoser.class.getName());

  private final BufferSizeSensor bufferSizeSensor;
  private double limit = 25;

  @Inject
  SlowInstanceDiagnoser(BufferSizeSensor bufferSizeSensor) {
    this.bufferSizeSensor = bufferSizeSensor;
  }

  @Override
  public Diagnosis diagnose(List<Symptom> symptoms) {
    List<Symptom> bpSymptoms = getBackPressureSymptoms(symptoms);
    if (bpSymptoms.isEmpty()) {
      // Since there is no back pressure, any more capacity is not needed
      return null;
    }

    List<Symptom> resultSymptoms = new ArrayList<>();
    for (Symptom backPressureSymptom : bpSymptoms) {
      ComponentMetrics bpMetricsData = backPressureSymptom.getMetrics();
      if (bpMetricsData.getMetrics().size() <= 1) {
        // Need more than one instance for comparison
        continue;
      }

      Map<String, ComponentMetrics> result = bufferSizeSensor.get(bpMetricsData.getName());
      ComponentMetrics bufferSizeData = result.get(bpMetricsData.getName());
      ComponentMetrics mergedData = ComponentMetrics.merge(bpMetricsData, bufferSizeData);

      ComponentBackpressureStats compStats = new ComponentBackpressureStats(mergedData);
      compStats.computeBufferSizeStats();

      if (compStats.bufferSizeMax > limit * compStats.bufferSizeMin) {
        // there is wide gap between max and min bufferSize, potential slow instance if the
        // instances who are starting back pressure are also executing less tuples

        for (InstanceMetrics boltMetrics : compStats.boltsWithBackpressure) {
          double bpValue = boltMetrics.getMetricValue(BACK_PRESSURE);
          double bufferSize = boltMetrics.getMetricValue(BUFFER_SIZE);
          if (compStats.bufferSizeMax < bufferSize * 2) {
            LOG.info(String.format("SLOW: %s back-pressure(%s) and high buffer size: %s",
                boltMetrics.getName(), bpValue, bufferSize));
            resultSymptoms.add(Symptom.from(mergedData));
          }
        }
      }
    }

    return resultSymptoms.size() > 0 ? new Diagnosis(resultSymptoms) : null;
  }
}