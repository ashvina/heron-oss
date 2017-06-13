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

package com.twitter.heron.healthmgr.sensors;

import com.microsoft.dhalion.api.ISensor;

import com.twitter.heron.healthmgr.common.HealthMgrConstants;

public abstract class BaseSensor implements ISensor {
  protected static final String EXE_COUNT = HealthMgrConstants.METRIC_EXE_COUNT;
  protected static final String BUFFER_SIZE = HealthMgrConstants.METRIC_BUFFER_SIZE;
  protected static final String BACK_PRESSURE = HealthMgrConstants.METRIC_BACK_PRESSURE;
  protected static final int METRIC_DURATION = HealthMgrConstants.DEFAULT_METRIC_DURATION;
  protected static final String BUFFER_SIZE_SUFFIX = HealthMgrConstants.METRIC_BUFFER_SIZE_SUFFIX;
}
