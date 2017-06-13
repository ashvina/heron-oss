// Copyright 2017 Twitter. All rights reserved.
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

package com.twitter.heron.scheduler.kubernetes;

import java.net.HttpURLConnection;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.twitter.heron.spi.utils.NetworkUtils;

import static org.mockito.Mockito.times;

@RunWith(PowerMockRunner.class)
@PrepareForTest(NetworkUtils.class)
public class KubernetesControllerTest {

  private static final String K8S_URI = "http://k8sapi.uri:8080";
  private static final String TOPOLOGY_NAME = "topology_name";
  private static final boolean IS_VERBOSE = true;

  private static KubernetesController controller;

  @Before
  public void setUp() throws Exception {
    controller = Mockito.spy(new KubernetesController(K8S_URI, TOPOLOGY_NAME, IS_VERBOSE));
  }

  @After
  public void after() throws Exception {
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
  }

  @AfterClass
  public static void afterClass() throws Exception {
  }

  /***
   * Test KubernetesController's killTopology method
   * @throws Exception
   */
  @Test
  public void testKillTopology() throws Exception {
    HttpURLConnection httpURLConnection = Mockito.mock(HttpURLConnection.class);

    // Failed to get connection
    PowerMockito.spy(NetworkUtils.class);
    PowerMockito.doReturn(null).when(NetworkUtils.class, "getHttpConnection", Mockito.anyString());
    Assert.assertFalse(controller.killTopology());
    PowerMockito.verifyStatic();
    NetworkUtils.getHttpConnection(Mockito.anyString());

    // Failed to send request
    PowerMockito.spy(NetworkUtils.class);
    PowerMockito.doReturn(httpURLConnection)
        .when(NetworkUtils.class, "getHttpConnection", Mockito.anyString());
    PowerMockito.doReturn(false)
        .when(NetworkUtils.class, "sendHttpDeleteRequest", Mockito.any(HttpURLConnection.class));
    Assert.assertFalse(controller.killTopology());
    PowerMockito.verifyStatic();
    NetworkUtils.getHttpConnection(Mockito.anyString());
    NetworkUtils.sendHttpDeleteRequest(Mockito.any(HttpURLConnection.class));

    // Failed to get response
    PowerMockito.spy(NetworkUtils.class);
    PowerMockito.doReturn(httpURLConnection)
        .when(NetworkUtils.class, "getHttpConnection", Mockito.anyString());
    PowerMockito.doReturn(true)
        .when(NetworkUtils.class, "sendHttpDeleteRequest", Mockito.any(HttpURLConnection.class));
    PowerMockito.doReturn(false)
        .when(NetworkUtils.class, "checkHttpResponseCode",
            Mockito.any(HttpURLConnection.class), Mockito.anyInt());
    Assert.assertFalse(controller.killTopology());
    PowerMockito.verifyStatic();
    NetworkUtils.getHttpConnection(Mockito.anyString());
    NetworkUtils.sendHttpDeleteRequest(Mockito.any(HttpURLConnection.class));
    NetworkUtils.checkHttpResponseCode(Mockito.any(HttpURLConnection.class), Mockito.anyInt());

    // Success
    PowerMockito.spy(NetworkUtils.class);
    PowerMockito.doReturn(httpURLConnection)
        .when(NetworkUtils.class, "getHttpConnection", Mockito.anyString());
    PowerMockito.doReturn(true)
        .when(NetworkUtils.class, "sendHttpDeleteRequest", Mockito.any(HttpURLConnection.class));
    PowerMockito.doReturn(true)
        .when(NetworkUtils.class, "checkHttpResponseCode",
            Mockito.any(HttpURLConnection.class), Mockito.anyInt());
    Assert.assertTrue(controller.killTopology());
    PowerMockito.verifyStatic();
    NetworkUtils.getHttpConnection(Mockito.anyString());
    NetworkUtils.sendHttpDeleteRequest(Mockito.any(HttpURLConnection.class));
    NetworkUtils.checkHttpResponseCode(Mockito.any(HttpURLConnection.class), Mockito.anyInt());
  }

  /***
   * Test KubernetesController's submitTopology method
   * @throws Exception
   */
  @Test
  public void testSubmitTopology() throws Exception {
    HttpURLConnection httpURLConnection = Mockito.mock(HttpURLConnection.class);
    final String[] appConf = {"{pod1: conf}", "{pod2: conf}"};

    // Failed to get connection
    PowerMockito.spy(NetworkUtils.class);
    PowerMockito.doReturn(null).when(NetworkUtils.class, "getHttpConnection", Mockito.anyString());
    Assert.assertFalse(controller.submitTopology(appConf));
    PowerMockito.verifyStatic();
    NetworkUtils.getHttpConnection(Mockito.anyString());

    // Failed to send request
    PowerMockito.spy(NetworkUtils.class);
    PowerMockito.doReturn(httpURLConnection)
        .when(NetworkUtils.class, "getHttpConnection", Mockito.anyString());
    PowerMockito.doReturn(false)
        .when(NetworkUtils.class, "sendHttpPostRequest",
            Mockito.any(HttpURLConnection.class),
            Mockito.anyString(),
            Mockito.any(byte[].class));
    Assert.assertFalse(controller.submitTopology(appConf));
    PowerMockito.verifyStatic();
    NetworkUtils.getHttpConnection(Mockito.anyString());
    NetworkUtils.sendHttpPostRequest(
        Mockito.any(HttpURLConnection.class),
        Mockito.anyString(),
        Mockito.any(byte[].class));

    // Failed to get response
    PowerMockito.spy(NetworkUtils.class);
    PowerMockito.doReturn(httpURLConnection)
        .when(NetworkUtils.class, "getHttpConnection", Mockito.anyString());
    PowerMockito.doReturn(true)
        .when(NetworkUtils.class, "sendHttpPostRequest",
            Mockito.any(HttpURLConnection.class),
            Mockito.anyString(),
            Mockito.any(byte[].class));
    PowerMockito.doReturn(false)
        .when(NetworkUtils.class, "checkHttpResponseCode",
            Mockito.any(HttpURLConnection.class), Mockito.anyInt());
    Assert.assertFalse(controller.submitTopology(appConf));
    PowerMockito.verifyStatic();
    NetworkUtils.getHttpConnection(Mockito.anyString());
    NetworkUtils.sendHttpPostRequest(
        Mockito.any(HttpURLConnection.class),
        Mockito.anyString(),
        Mockito.any(byte[].class));
    NetworkUtils.checkHttpResponseCode(Mockito.any(HttpURLConnection.class), Mockito.anyInt());

    // Success
    PowerMockito.spy(NetworkUtils.class);
    PowerMockito.doReturn(httpURLConnection)
        .when(NetworkUtils.class, "getHttpConnection", Mockito.anyString());
    PowerMockito.doReturn(true)
        .when(NetworkUtils.class, "sendHttpPostRequest",
            Mockito.any(HttpURLConnection.class),
            Mockito.anyString(),
            Mockito.any(byte[].class));
    PowerMockito.doReturn(true)
        .when(NetworkUtils.class, "checkHttpResponseCode",
            Mockito.any(HttpURLConnection.class), Mockito.anyInt());

    // Sample app conf with 2 pods -- verify it ran twice
    PowerMockito.spy(NetworkUtils.class);
    PowerMockito.doReturn(httpURLConnection)
        .when(NetworkUtils.class, "getHttpConnection", Mockito.anyString());
    PowerMockito.doReturn(true)
        .when(NetworkUtils.class, "sendHttpPostRequest",
            Mockito.any(HttpURLConnection.class),
            Mockito.anyString(),
            Mockito.any(byte[].class));
    PowerMockito.doReturn(true)
        .when(NetworkUtils.class, "checkHttpResponseCode",
            Mockito.any(HttpURLConnection.class), Mockito.anyInt());

    Assert.assertTrue(controller.submitTopology(appConf));

    // Verify 2 times
    PowerMockito.verifyStatic(times(2));
    NetworkUtils.getHttpConnection(Mockito.anyString());
    NetworkUtils.sendHttpPostRequest(
        Mockito.any(HttpURLConnection.class),
        Mockito.anyString(),
        Mockito.any(byte[].class));
    NetworkUtils.checkHttpResponseCode(Mockito.any(HttpURLConnection.class), Mockito.anyInt());
  }

}
