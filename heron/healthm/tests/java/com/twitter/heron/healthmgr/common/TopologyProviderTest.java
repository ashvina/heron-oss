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

package com.twitter.heron.healthmgr.common;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.twitter.heron.api.Config;
import com.twitter.heron.api.generated.TopologyAPI.Topology;
import com.twitter.heron.spi.statemgr.SchedulerStateManagerAdaptor;
import com.twitter.heron.spi.utils.TopologyTests;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TopologyProviderTest {
  String topology = "topology";

  @Test
  public void fetchesAndCachesPackingFromStateMgr() {
    Topology testTopology
        = TopologyTests.createTopology(topology, new Config(), getSpouts(), getBolts());
    SchedulerStateManagerAdaptor adaptor = mock(SchedulerStateManagerAdaptor.class);
    when(adaptor.getTopology(topology)).thenReturn(testTopology);

    TopologyProvider provider = new TopologyProvider(adaptor, topology);
    Assert.assertEquals(2, provider.get().getBoltsCount());

    // once fetched it is cached
    provider.get();
    verify(adaptor, times(1)).getTopology(topology);
  }

  @Test
  public void refreshesPackingPlanOnUpdate() {
    Topology testTopology
        = TopologyTests.createTopology(topology, new Config(), getSpouts(), getBolts());
    SchedulerStateManagerAdaptor adaptor = mock(SchedulerStateManagerAdaptor.class);
    when(adaptor.getTopology(topology)).thenReturn(testTopology);

    TopologyProvider provider = new TopologyProvider(adaptor, topology);
    Assert.assertEquals(2, provider.get().getBoltsCount());

    // once fetched it is cached
    provider.onNext(new HealthManagerEvents.TOPOLOGY_UPDATE());
    provider.get();
    verify(adaptor, times(2)).getTopology(topology);
  }

  @Test
  public void providesBoltNames() {
    Map<String, Integer> bolts = getBolts();

    String topology = "topology";
    Topology testTopology =
        TopologyTests.createTopology(topology, new Config(), getSpouts(), bolts);
    SchedulerStateManagerAdaptor adaptor = mock(SchedulerStateManagerAdaptor.class);
    when(adaptor.getTopology(topology)).thenReturn(testTopology);

    TopologyProvider topologyProvider = new TopologyProvider(adaptor, topology);

    assertEquals(2, bolts.size());
    String[] boltNames = topologyProvider.getBoltNames();
    assertEquals(bolts.size(), boltNames.length);
    for (String boltName : boltNames) {
      bolts.remove(boltName);
    }
    assertEquals(0, bolts.size());
  }

  private Map<String, Integer> getBolts() {
    Map<String, Integer> bolts = new HashMap<>();
    bolts.put("bolt-1", 1);
    bolts.put("bolt-2", 1);
    return bolts;
  }

  private Map<String, Integer> getSpouts() {
    Map<String, Integer> spouts = new HashMap<>();
    spouts.put("spout", 1);
    return spouts;
  }
}
