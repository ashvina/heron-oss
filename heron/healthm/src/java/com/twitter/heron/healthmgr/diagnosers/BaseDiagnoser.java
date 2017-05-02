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

import com.microsoft.dhalion.api.IDiagnoser;
import com.microsoft.dhalion.metrics.ComponentMetricsData;
import com.microsoft.dhalion.metrics.InstanceMetricsData;
import com.microsoft.dhalion.symptom.ComponentSymptom;

import com.twitter.heron.healthmgr.common.HealthMgrConstants;

public abstract class BaseDiagnoser implements IDiagnoser<ComponentSymptom> {
  protected static final String EXE_COUNT = HealthMgrConstants.METRIC_EXE_COUNT;
  protected static final String BUFFER_SIZE = HealthMgrConstants.METRIC_BUFFER_SIZE;
  protected static final String BACK_PRESSURE =
      HealthMgrConstants.METRIC_INSTANCE_BACK_PRESSURE;

  @Override
  public void close() {
  }

  @Override
  public void initialize() {
  }

  /**
   * A helper class to compute and hold component stats
   */
  protected static class ComponentBackpressureStats {
    private final ComponentMetricsData componentMetrics;

    List<InstanceMetricsData> boltsWithBackpressure = new ArrayList<>();
    List<InstanceMetricsData> boltsWithoutBackpressure = new ArrayList<>();
    double exeCountMax;
    double exeCountMin;
    double bufferSizeMax;
    double bufferSizeMin;
    double totalBackpressure = 0;

    public ComponentBackpressureStats(ComponentMetricsData backPressureMetrics) {
      this.componentMetrics = backPressureMetrics;

      for (InstanceMetricsData mergedInstance : backPressureMetrics.getMetrics().values()) {
        int bpValue = mergedInstance.getMetricIntValue(BACK_PRESSURE);
        if (bpValue > 0) {
          boltsWithBackpressure.add(mergedInstance);
          totalBackpressure += bpValue;
        } else {
          boltsWithoutBackpressure.add(mergedInstance);
        }
      }
    }

    protected void computeBufferSizeStats() {
      bufferSizeMin = Double.MAX_VALUE;
      bufferSizeMax = Double.MIN_VALUE;

      for (InstanceMetricsData mergedInstance : componentMetrics.getMetrics().values()) {
        Double bufferSize = mergedInstance.getMetric(BUFFER_SIZE);
        if (bufferSize == null) {
          continue;
        }
        bufferSizeMax = bufferSizeMax < bufferSize ? bufferSize : bufferSizeMax;
        bufferSizeMin = bufferSizeMin > bufferSize ? bufferSize : bufferSizeMin;
      }
    }

    protected void computeExeCountStats() {
      for (InstanceMetricsData mergedInstance : componentMetrics.getMetrics().values()) {
        int exeCount = mergedInstance.getMetricIntValue(EXE_COUNT);
        exeCountMax = exeCountMax < exeCount ? exeCount : exeCountMax;
        exeCountMin = exeCountMin > exeCount ? exeCount : exeCountMin;
      }
    }
  }
}
