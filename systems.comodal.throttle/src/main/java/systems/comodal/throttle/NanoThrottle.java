/*
 * Copyright (C) 2012 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package systems.comodal.throttle;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * {@inheritDoc}
 */
final class NanoThrottle implements Throttle {

  static final double ONE_SECOND_NANOS = 1_000_000_000.0;

  private final ReentrantLock lock;
  private final long nanoStart;
  private final double stableIntervalNanos;
  private volatile long nextFreeTicketNanos;
  private volatile double storedPermits;

  NanoThrottle(final double permitsPerSecond, final boolean fair) {
    if (permitsPerSecond <= 0.0 || !Double.isFinite(permitsPerSecond)) {
      throw new IllegalArgumentException("rate must be positive");
    }
    this.lock = new ReentrantLock(fair);
    this.nanoStart = System.nanoTime();
    this.stableIntervalNanos = ONE_SECOND_NANOS / permitsPerSecond;
  }

  private static void checkPermits(final int permits) {
    if (permits <= 0) {
      throw new IllegalArgumentException("Requested permits " + permits + " must be positive.");
    }
  }

  /**
   * Returns the sum of {@code val1} and {@code val2} unless it would overflow or underflow in which
   * case {@code Long.MAX_VALUE} or {@code Long.MIN_VALUE} is returned, respectively.
   */
  static long saturatedAdd(final long val1, final long val2) {
    final long naiveSum = val1 + val2;
    if ((val1 ^ val2) < 0 || (val1 ^ naiveSum) >= 0) {
      return naiveSum;
    }
    return Long.MAX_VALUE + ((naiveSum >>> (Long.SIZE - 1)) ^ 1);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double getRate() {
    return ONE_SECOND_NANOS / stableIntervalNanos;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void acquire(final int permits) throws InterruptedException {
    NANOSECONDS.sleep(acquireDelayDuration(permits));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long acquireDelayDuration(final int permits) {
    checkPermits(permits);
    long elapsedNanos;
    long momentAvailable;
    lock.lock();
    try {
      elapsedNanos = System.nanoTime() - nanoStart;
      momentAvailable = reserveEarliestAvailable(permits, elapsedNanos);
    } finally {
      lock.unlock();
    }
    return elapsedNanos >= momentAvailable ? 0 : momentAvailable - elapsedNanos;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean tryAcquire(final int permits, final long timeout, final TimeUnit unit) throws InterruptedException {
    final long waitDuration = tryAcquireDelayDuration(permits, timeout, unit);
    if (waitDuration < 0) {
      return false;
    }
    NANOSECONDS.sleep(waitDuration);
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long tryAcquireDelayDuration(final int permits, final long timeout, final TimeUnit unit) {
    if (timeout <= 0) {
      return tryAcquire(permits) ? 0 : -1;
    }
    final long waitDuration = unit.toNanos(timeout);
    checkPermits(permits);
    lock.lock();
    try {
      final long durationElapsed = System.nanoTime() - nanoStart;
      return nextFreeTicketNanos - waitDuration > durationElapsed
          ? -1 : reserveEarliestAvailable(permits, durationElapsed) - durationElapsed;
    } finally {
      lock.unlock();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean tryAcquire(final int permits) {
    checkPermits(permits);
    lock.lock();
    try {
      final long elapsedNanos = System.nanoTime() - nanoStart;
      if (nextFreeTicketNanos > elapsedNanos) {
        return false;
      }
      reserveEarliestAvailable(permits, elapsedNanos);
      return true;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Reserves the requested number of permits and returns the time that those permits can be used
   * (with one caveat).
   *
   * @return the time that the permits may be used, or, if the permits may be used immediately, an
   * arbitrary past or present time.
   */
  @SuppressWarnings("NonAtomicOperationOnVolatileField") // All calls synchronized by lock.
  private long reserveEarliestAvailable(final int requiredPermits, final long elapsedNanos) {
    long _freeTicketNanos = nextFreeTicketNanos;
    if (elapsedNanos > _freeTicketNanos) {
      nextFreeTicketNanos = _freeTicketNanos = elapsedNanos;
    }
    double _storedPermits = storedPermits;
    final double storedPermitsToSpend = requiredPermits < _storedPermits ? requiredPermits : _storedPermits;
    nextFreeTicketNanos = saturatedAdd(_freeTicketNanos, (long) ((requiredPermits - storedPermitsToSpend) * stableIntervalNanos));
    storedPermits = _storedPermits - storedPermitsToSpend;
    return _freeTicketNanos;
  }

  @Override
  public String toString() {
    return "Throttle{rate=" + getRate() + '}';
  }
}
