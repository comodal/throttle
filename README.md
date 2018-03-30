# Throttle [![Build Status](https://travis-ci.org/client-side/throttle.svg)](https://travis-ci.org/comodal/throttle) [ ![Download](https://api.bintray.com/packages/comodal/libraries/throttle/images/download.svg) ](https://bintray.com/comodal/libraries/throttle/_latestVersion) [![License](http://img.shields.io/badge/license-Apache--2-blue.svg?style=flat) ](LICENSE) [![codecov](https://codecov.io/gh/comodal/throttle/branch/master/graph/badge.svg)](https://codecov.io/gh/comodal/throttle)

>Provides a mechanism to limit the rate of access to a resource.

### Usage

A Throttle instance distributes permits at a desired rate, blocking if necessary until a permit is available.

###### Submit two tasks per second:

```java
Throttle throttle = Throttle.create(2.0); // 2 permits per second
// ...
void submitTasksBlocking(List<Runnable> tasks, Executor executor) {
  for (var task : tasks) {
    throttle.acquireUnchecked();
    executor.execute(task);
  }
}

void submitTasksNonBlocking(List<Runnable> tasks, Executor executor) {
  for (var task : tasks) {
    long delay = throttle.acquireDelayDuration();
    if (delay > 0) {
      CompletableFuture.runAsync(task,
        CompletableFuture.delayedExecutor(delay, NANOSECONDS, executor));
      continue;
    }
    CompletableFuture.runAsync(task, executor);
  }
}
```

###### Cap data stream to 5kb per second:

```java
Throttle throttle = Throttle.create(5000.0); // 5000 permits per second
// ...
void submitPacket(byte[] packet) {
  throttle.acquire(packet.length);
  networkService.send(packet);
}
```

### Changes From Guava Rate Limiter
* Nanosecond instead of microsecond accuracy.
* Uses a `ReentrantLock` instead of `synchronized` blocks to support optional fair acquisition ordering.
* Factoring out an interface class, [Throttle](systems.comodal.throttle/src/main/java/systems/comodal/throttle/Throttle.java#L83), from the base abstract class.
* Remove the need for any non-core-Java classes outside of the original [RateLimiter](https://github.com/google/guava/blob/master/guava/src/com/google/common/util/concurrent/RateLimiter.java) and [SmoothRateLimiter](https://github.com/google/guava/blob/master/guava/src/com/google/common/util/concurrent/SmoothRateLimiter.java) classes.
* Remove the need for a [SleepingStopwatch](https://github.com/google/guava/blob/master/guava/src/com/google/common/util/concurrent/RateLimiter.java#L395) or similar class instance.
* Guava provides rate limiters with either _bursty_ or _warm-up_ behavior. Throttle provides only a single strict rate limiter implementation that will never exceed the desired rate limit over a one second period.
* Throws checked InterruptedException's or unchecked CompletionException's with the cause set to the corresponding InterruptedException if interrupted.
