/*
 * Copyright (C) 2012 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package systems.comodal.throttle;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.*;
import static org.junit.jupiter.api.Assertions.*;
import static systems.comodal.throttle.NanoThrottle.ONE_SECOND_NANOS;


/**
 * The following tests were adapted directly from com.google.common.util.concurrent.RateLimiterTest.
 * Changes were made to test the non-burst behavior of Throttle, to ensure the rate limit is not
 * exceeded over the period of one second.
 *
 * @author Dimitris Andreou - Original RateLimiterTest author
 * @author James P Edwards
 */
final class ThrottleTest {

  private static final double FIRST_DELTA = 0.005; // 5ms
  private static final double SECOND_DELTA = 0.006; // 6ms

  @BeforeAll
  static void warmup() {
    Throttle.create(100.0);
  }

  @Test
  void testAcquireDelayDuration() throws InterruptedException {
    final var throttle = Throttle.create(5.0);
    final long delay1 = throttle.acquireDelayDuration(1);
    final long delay2 = throttle.acquireDelayDuration(1);
    NANOSECONDS.sleep(delay2);
    final long delay3 = throttle.acquireDelayDuration(1);
    final long delay4 = throttle.acquireDelayDuration(1);
    assertEquals(0.0, delay1);
    assertEquals(0.20, delay2 / ONE_SECOND_NANOS, FIRST_DELTA);
    assertEquals(0.20, delay3 / ONE_SECOND_NANOS, SECOND_DELTA);
    assertEquals(0.40, delay4 / ONE_SECOND_NANOS, SECOND_DELTA);
  }

  private static double acquireAndSleep(final Throttle throttle) throws InterruptedException {
    return acquireAndSleep(throttle, 1);
  }

  private static double acquireAndSleep(final Throttle throttle, final int permits) throws InterruptedException {
    final long waitDuration = throttle.acquireDelayDuration(permits);
    NANOSECONDS.sleep(waitDuration);
    return waitDuration / ONE_SECOND_NANOS;
  }

//  @Test
//  void testBurstAcquire() throws InterruptedException {
//    final var throttle = Throttle.create(5.0, 1.0, true);
//    final var delay1 = acquireAndSleep(throttle);
//    final var delay2 = acquireAndSleep(throttle, 10);
//    final var delay3 = throttle.acquireDelayDuration() / ONE_SECOND_NANOS;
//    assertEquals(0.0, delay1);
//    assertEquals(0.20, delay2, FIRST_DELTA);
//    assertEquals(0.20, delay3, SECOND_DELTA);
//  }

  @Test
  void testAcquire() throws InterruptedException {
    final var throttle = Throttle.create(5.0);
    final var delay1 = acquireAndSleep(throttle);
    final var delay2 = acquireAndSleep(throttle);
    final var delay3 = throttle.acquireDelayDuration() / ONE_SECOND_NANOS;
    assertEquals(0.0, delay1);
    assertEquals(0.20, delay2, FIRST_DELTA);
    assertEquals(0.20, delay3, SECOND_DELTA);
  }

  @Test
  void testAcquireWeights() throws InterruptedException {
    final var throttle = Throttle.create(20.0);

    final var delay1 = acquireAndSleep(throttle);
    final var delay2 = acquireAndSleep(throttle);
    final var delay3 = acquireAndSleep(throttle, 2);
    final var delay4 = acquireAndSleep(throttle, 4);
    final var delay5 = acquireAndSleep(throttle, 8);
    final var delay6 = throttle.acquireDelayDuration() / ONE_SECOND_NANOS;

    assertEquals(0.00, delay1, FIRST_DELTA);
    assertEquals(0.05, delay2, SECOND_DELTA);
    assertEquals(0.05, delay3, SECOND_DELTA);
    assertEquals(0.10, delay4, SECOND_DELTA);
    assertEquals(0.20, delay5, SECOND_DELTA);
    assertEquals(0.40, delay6, SECOND_DELTA);
  }

  @Test
  void testAcquireWithWait() throws InterruptedException {
    final var throttle = Throttle.create(50.0);
    final var delay1 = acquireAndSleep(throttle);
    Thread.sleep(20);
    final var delay2 = acquireAndSleep(throttle);
    final var delay3 = throttle.acquireDelayDuration() / ONE_SECOND_NANOS;
    assertEquals(0.0, delay1);
    assertEquals(0.0, delay2);
    assertEquals(0.020, delay3, SECOND_DELTA);
  }

  @Test
  void testAcquireWithDoubleWait() throws InterruptedException {
    final var throttle = Throttle.create(50.0);
    final var delay1 = acquireAndSleep(throttle);
    Thread.sleep(40);
    final var delay2 = acquireAndSleep(throttle);
    final var delay3 = acquireAndSleep(throttle);
    final var delay4 = throttle.acquireDelayDuration() / ONE_SECOND_NANOS;
    assertEquals(0.0, delay1);
    assertEquals(0.0, delay2);
    assertEquals(0.020, delay3, SECOND_DELTA);
    assertEquals(0.020, delay4, SECOND_DELTA);
  }

  @Test
  void testManyPermits() throws InterruptedException {
    final var throttle = Throttle.create(50.0);
    final var delay1 = acquireAndSleep(throttle);
    final var delay2 = acquireAndSleep(throttle);
    final var delay3 = acquireAndSleep(throttle, 3);
    final var delay4 = acquireAndSleep(throttle);
    final var delay5 = throttle.acquireDelayDuration() / ONE_SECOND_NANOS;
    assertEquals(0.0, delay1);
    assertEquals(0.02, delay2, FIRST_DELTA);
    assertEquals(0.02, delay3, SECOND_DELTA);
    assertEquals(0.06, delay4, SECOND_DELTA);
    assertEquals(0.02, delay5, SECOND_DELTA);
  }

  @Test
  void testTryAcquire_noWaitAllowed() throws InterruptedException {
    final var throttle = Throttle.create(50.0);
    assertTrue(throttle.tryAcquire());
    assertFalse(throttle.tryAcquireUnchecked(0, SECONDS));
    assertFalse(throttle.tryAcquire(0, SECONDS));
    Thread.sleep(10);
    assertFalse(throttle.tryAcquire(0, SECONDS));
  }

  @Test
  void testTryAcquire_someWaitAllowed() throws InterruptedException {
    final var throttle = Throttle.create(50.0);
    assertTrue(throttle.tryAcquire(0, SECONDS));
    assertTrue(throttle.tryAcquire(20, MILLISECONDS));
    assertFalse(throttle.tryAcquire(10, MILLISECONDS));
    Thread.sleep(10);
    assertTrue(throttle.tryAcquire(10, MILLISECONDS));
  }

  @Test
  void testTryAcquire_overflow() throws InterruptedException {
    final var throttle = Throttle.create(50.0);
    assertTrue(throttle.tryAcquire(0, MICROSECONDS));
    Thread.sleep(10);
    assertTrue(throttle.tryAcquire(Long.MAX_VALUE, MICROSECONDS));
  }

  @Test
  void testTryAcquire_negative() throws InterruptedException {
    final var throttle = Throttle.create(50.0);
    assertTrue(throttle.tryAcquire(5, 0, SECONDS));
    Thread.sleep(90);
    assertFalse(throttle.tryAcquire(1, Long.MIN_VALUE, SECONDS));
    Thread.sleep(10);
    assertTrue(throttle.tryAcquire(1, -1, SECONDS));
  }

  @Test
  void testImmediateTryAcquire() {
    final var throttle = Throttle.create(1.0);
    assertTrue(throttle.tryAcquire(), "Unable to acquire initial permit");
    assertFalse(throttle.tryAcquire(), "Capable of acquiring secondary permit");
  }

  @Test
  void testDoubleMinValueCanAcquireExactlyOnce() throws InterruptedException {
    final var throttle = Throttle.create(Double.MIN_VALUE);
    assertTrue(throttle.tryAcquire(), "Unable to acquire initial permit");
    assertFalse(throttle.tryAcquire(), "Capable of acquiring an additional permit");
    Thread.sleep(10);
    assertFalse(throttle.tryAcquire(), "Capable of acquiring an additional permit after sleeping");
  }

  @Test
  void testAcquireParameterValidation() {
    final var throttle = Throttle.create(999);
    assertThrows(IllegalArgumentException.class, () -> throttle.acquire(0));
    assertThrows(IllegalArgumentException.class, () -> throttle.acquire(-1));
    assertThrows(IllegalArgumentException.class, () -> throttle.tryAcquire(0));
    assertThrows(IllegalArgumentException.class, () -> throttle.tryAcquire(-1));
    assertThrows(IllegalArgumentException.class, () -> throttle.tryAcquireUnchecked(0, 1, SECONDS));
    assertThrows(IllegalArgumentException.class, () -> throttle.tryAcquire(-1, 1, SECONDS));
  }

  @Test
  void testIllegalConstructorArgs() {
    assertThrows(IllegalArgumentException.class, () -> Throttle.create(Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> Throttle.create(Double.NEGATIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> Throttle.create(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> Throttle.create(-.0000001));
  }

  @Test
  void testInterruptUnchecked() throws InterruptedException {
    final var throttle = Throttle.create(1);
    throttle.acquireUnchecked(10);

    final var futureEx = new CompletableFuture<>();
    var thread = new Thread(() -> {
      try {
        throttle.acquireUnchecked();
        futureEx.complete(null);
      } catch (final CompletionException ex) {
        futureEx.complete(ex.getCause());
      }
    });
    thread.start();
    thread.interrupt();
    thread.join();
    assertFalse(throttle.tryAcquire());
    assertEquals(InterruptedException.class, futureEx.join().getClass());

    final var futureEx2 = new CompletableFuture<>();
    thread = new Thread(() -> {
      try {
        throttle.tryAcquireUnchecked(20, SECONDS);
        futureEx2.complete(null);
      } catch (final CompletionException ex) {
        futureEx2.complete(ex.getCause());
      }
    });
    thread.start();
    thread.interrupt();
    thread.join();
    assertFalse(throttle.tryAcquire());
    assertEquals(InterruptedException.class, futureEx2.join().getClass());
  }

  @Test
  void testMax() throws InterruptedException {
    final var throttle = Throttle.create(Double.MAX_VALUE);
    assertEquals(0.0, acquireAndSleep(throttle, Integer.MAX_VALUE / 4));
    assertEquals(0.0, acquireAndSleep(throttle, Integer.MAX_VALUE / 2));
    assertEquals(0.0, throttle.acquireDelayDuration(Integer.MAX_VALUE));
  }


  private static long measureTotalTimeMillis(final Throttle throttle, int permits) throws InterruptedException {
    final var random = ThreadLocalRandom.current();
    final long startTime = System.nanoTime();
    while (permits > 0) {
      final int nextPermitsToAcquire = Math.max(1, random.nextInt(permits));
      permits -= nextPermitsToAcquire;
      throttle.acquire(nextPermitsToAcquire);
    }
    throttle.acquireDelayDuration(); // to repay for any pending debt
    return NANOSECONDS.toMillis(System.nanoTime() - startTime);
  }

  @Test
  void testWeNeverGetABurstMoreThanOneSec() throws InterruptedException {
    final int[] rates = {10000, 100, 1000000, 1000, 100};
    for (final int oneSecWorthOfWork : rates) {
      final var throttle = Throttle.create(oneSecWorthOfWork);
      final int oneHundredMillisWorthOfWork = (int) (oneSecWorthOfWork / 10.0);
      long durationMillis = measureTotalTimeMillis(throttle, oneHundredMillisWorthOfWork);
      assertEquals(100.0, durationMillis, 15.0);
      durationMillis = measureTotalTimeMillis(throttle, oneHundredMillisWorthOfWork);
      assertEquals(100.0, durationMillis, 15.0);
    }
  }

  @Test
  void testToString() {
    final var throttle = Throttle.create(100.0);
    assertEquals("Throttle{rate=100.0}", throttle.toString());
  }

  @Test
  void testStream() {
    final int qps = 2_048;
    final var throttle = Throttle.create(qps);
    // warm-up
    IntStream.range(0, 32).parallel().forEach(index -> throttle.acquireUnchecked());

    var stream = IntStream.range(0, qps + 1).parallel();
    long start = System.nanoTime();
    stream.forEach(index -> throttle.acquireUnchecked());
    long duration = TimeUnit.MILLISECONDS
        .convert(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    assertTrue(duration >= 1000 && duration < 1050, "Expected duration between 1,000 and 1,050ms. Observed " + duration);

    final var fairThrottle = Throttle.create(qps, true);
    // warm-up
    IntStream.range(0, 32).parallel().forEach(index -> fairThrottle.acquireUnchecked());

    stream = IntStream.range(0, qps + 1).parallel();
    start = System.nanoTime();
    stream.forEach(index -> fairThrottle.tryAcquireUnchecked(200_000_000, TimeUnit.NANOSECONDS));
    duration = TimeUnit.MILLISECONDS
        .convert(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    assertTrue(duration >= 1000 && duration < 1050, "Expected duration between 1,000 and 1,050ms. Observed " + duration);
  }
}
