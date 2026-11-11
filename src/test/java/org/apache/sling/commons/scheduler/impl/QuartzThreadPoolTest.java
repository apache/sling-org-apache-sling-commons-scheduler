/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.commons.scheduler.impl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sling.commons.threads.ModifiableThreadPoolConfig;
import org.apache.sling.commons.threads.ThreadPool;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Tests for {@link QuartzThreadPool}.
 */
public class QuartzThreadPoolTest {

    /**
     * An interrupted scheduler must release the pool lock so jobs and shutdown can proceed.
     */
    @Test(timeout = 5000)
    public void testInterruptedWaitReleasesPoolLock() throws InterruptedException {
        ModifiableThreadPoolConfig config = new ModifiableThreadPoolConfig();
        config.setMaxPoolSize(1);
        ThreadPool executor = Mockito.mock(ThreadPool.class);
        Mockito.when(executor.getConfiguration()).thenReturn(config);
        QuartzThreadPool pool = new QuartzThreadPool(executor);
        Assert.assertTrue(pool.runInThread(() -> {}));
        ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executor).execute(job.capture());

        AtomicInteger available = new AtomicInteger(-1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread scheduler = new Thread(() -> {
            Thread.currentThread().interrupt();
            available.set(pool.blockForAvailableThreads());
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        scheduler.setDaemon(true);
        scheduler.start();
        scheduler.join(1000);

        Assert.assertFalse("Interrupted scheduler must stop waiting", scheduler.isAlive());
        Assert.assertEquals(0, available.get());
        Assert.assertTrue("Interrupt status must be preserved", interrupted.get());
        job.getValue().run();
        Assert.assertEquals(1, pool.blockForAvailableThreads());
        pool.shutdown(false);
    }
}
