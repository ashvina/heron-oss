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


package com.twitter.heron.healthmgr.detectors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.microsoft.dhalion.api.IDetector;
import com.microsoft.dhalion.app.ComponentInfo;
import com.microsoft.dhalion.detector.Symptom;
import com.microsoft.dhalion.metrics.ComponentMetrics;

import com.twitter.heron.healthmgr.common.HealthMgrConstants;
import com.twitter.heron.healthmgr.sensors.BackPressureSensor;

public class BackPressureDetector implements IDetector {
  private final BackPressureSensor bpSensor;

  @Inject
  BackPressureDetector(BackPressureSensor bpSensor) {
    this.bpSensor = bpSensor;
  }

  /**
   * @return A collection of all components with any instance starting backpressure. Normally there
   * will be only one component
   */
  @Override
  public List<Symptom> detect() {
    ArrayList<Symptom> result = new ArrayList<>();

    Map<String, ComponentMetrics> backpressureMetrics = bpSensor.get();
    for (ComponentMetrics compMetrics : backpressureMetrics.values()) {
      if (compMetrics
          .anyInstanceAboveLimit(HealthMgrConstants.METRIC_INSTANCE_BACK_PRESSURE, 20)) {
        result.add(new Symptom(new ComponentInfo(compMetrics.getName()), compMetrics));
      }
    }

    return result;
  }

  @Override
  public void close() {
  }
}