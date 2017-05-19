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
package com.twitter.heron.spi.healthmgr;


import java.util.Set;

import com.twitter.heron.spi.metricsmgr.metrics.MetricsInfo;
import com.twitter.heron.spi.packing.PackingPlan.InstancePlan;

public class InstanceBottleneck extends Bottleneck {
  private InstanceInfo instanceData;

  public InstanceBottleneck(int containerId, InstancePlan instance, Set<MetricsInfo> metrics) {
    this.instanceData = new InstanceInfo(containerId, instance, metrics);
  }

  public InstanceInfo getInstanceData() {
    return instanceData;
  }

  public String toString() {
    return instanceData.toString();
  }

  public boolean contains(String metric, String value) {
    if (instanceData.contains(metric, value)) {
      return true;
    }
    return false;
  }

  public boolean containsBelow (String metric, String value){
    if (instanceData.containsBelow(metric, value)) {
      return true;
    }
    return false;
  }

  public String getDataPoint(String metric) {
    return instanceData.getMetricValue(metric);
  }
}