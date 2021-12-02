<H2> This is a simple benchmark for Apache Mina Core TCP Client</H2>

<H3>Intro</H3>

Current implementation of _IoServiceStatistics_ is blocking - it locks on _throughputCalculationLock_ for almost all operation.

However, _IoServiceStatistics_ is used by all threads which writes to _IoSession_ and by all _NioProcessor_ threads. 

There is a concern that _IoServiceStatistics_ slows down performance

<H3>Goals</H3>

The aim of the benchmark is to compare performance of: 
1. Mina TCP Client with **blocking** _IoServiceStatistics_ implementation (original)
2. Mina TCP Client with **non-blocking** _IoServiceStatistics_ implementation (patched)

<H3>Benchmark details</H3>

* There are _N_ _IoSession_'s between _Client_ and _Server_ 
* There are _N_ threads, each thread has its own _IoSession_ and they write messages simultaneously
* The measurements are taken between the time the message was written to _IoSession_ and the time when it was actually sent to the server by _NioProcessor_ 
* The rate of sending messages is regulated by a _Semaphore_:
  * acquire Semaphore before sening message
  * release Semaphore when the message was actually sent
* Java reflection is used for imitating non-blocking _IoServiceStatistics_ (_benchmark.Client.makeIoServiceStatisticsNonBlocking_)
* The only difference between 2 benchmarks is here _benchmark.ClientBenchmark.ClientBenchmark_:

    ```
  if (config.blockingClientIoServiceStats) {
        System.out.println("BLOCKING Client IoServiceStatistics implementation is used");
  } else {
        client.makeIoServiceStatisticsNonBlocking();
        System.out.println("NON-BLOCKING Client IoServiceStatistics implementation is used");
  }
    ```

* Benchmark parameters are taken from _benchmark.ClientBenchmark.Config_
* The results:
  * for each iteration: calculated percentiles for sent messages during the iteration
  * for summary: calculate mean for all percentiles for all iterations (warmup iterations excluded)
* By default, the configuration is:
  * 3 warmup iterations which are _not used_ in summary result
  * 10 real iterations which are _used_ in summary result
  * 50 threads (_N_=50), each thread sends 50000 messages for each iteration

<H3>How to run</H3>

1. Run benchmark with **blocking** _IoServiceStatistics_:
 
   ```
   mvn clean compile exec:exec@blocking
   ```

2. Run benchmark with **non-blocking** _IoServiceStatistics_:

   ```
   mvn clean compile exec:exec@non-blocking
   ```

<H3>Results</H3>

1. **blocking** _IoServiceStatistics_ (original):
```
================================================
SUMMARY RESULTS
================================================
Mean Result for 10 iterations: 
p25: 50 mcs, p50: 140 mcs, p75: 400 mcs, p90: 905 mcs, p95: 1418 mcs, p99: 11485 mcs, p100: 505313 mcs, 
================================================
```

2. **non-blocking** _IoServiceStatistics_ (patched):
```
================================================
SUMMARY RESULTS
================================================
Mean Result for 10 iterations: 
p25: 44 mcs, p50: 85 mcs, p75: 150 mcs, p90: 239 mcs, p95: 319 mcs, p99: 1311 mcs, p100: 1374162 mcs, 
================================================
```

<H3>Summary</H3>

The results show that _IoServiceStatistics_ introduces huge latencies for the benchmark condition:

```
# patched vs original:

p50: 85mcs vs 140mcs
p75: 150mcs vs 400mcs
p90: 239mcs vs 905mcs
p95: 319mcs vs 1418mcs
p99: 1311mcs vs 11485mcs
```
