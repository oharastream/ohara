package com.island.ohara.integration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.island.ohara.common.rule.MediumTest;
import org.junit.Assert;
import org.junit.Test;

public class TestWorkers extends MediumTest {

  @Test(expected = IllegalArgumentException.class)
  public void testWorkersNull() {
    Workers.of(
        null,
        () -> {
          throw new IllegalArgumentException("you can't pass");
        },
        1);
  }

  @Test
  public void testHaveWorkers() {
    String connProps = "localhost:12345";

    try (Workers external =
        Workers.of(
            connProps,
            () -> {
              throw new IllegalArgumentException("you can't pass");
            },
            1)) {
      Assert.assertEquals(connProps, external.connectionProps());
      assertFalse(external.isLocal());
    }
  }

  @Test
  public void testLocalMethod() throws Exception {
    try (Zookeepers zk = Zookeepers.of();
        Brokers brokers = Brokers.of(() -> zk, 1)) {
      try (Workers local = Workers.of(() -> brokers, 1)) {
        assertTrue(local.isLocal());
      }
    }
  }

  @Test
  public void testRandomPort() throws Exception {
    int[] brokerPorts = {0};
    int[] workerPorts = {0};

    try (Zookeepers zk = Zookeepers.local(0);
        Brokers brokers = Brokers.local(zk, brokerPorts);
        Workers workers = Workers.local(brokers, workerPorts)) {
      Assert.assertNotEquals(
          0, Integer.parseInt(workers.connectionProps().split(",")[0].split(":")[1]));
    }
  }
}
