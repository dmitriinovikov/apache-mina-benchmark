package benchmark;

import benchmark.ClientBenchmark.Config;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IoSession;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientBenchmarkUnit implements Client.MessageSentListener {

    private static final AttributeKey SEMAPHORE_ATTR = new AttributeKey(Semaphore.class, "Semaphore");
    private static final int SEM_PERMITS_PER_SESSION = 10;

    private final Client client;

    private final double[] latencies;
    private final IoSession[] sessions;
    private final Config config;

    private final AtomicInteger sentMessagesCounter = new AtomicInteger();
    private final CountDownLatch messagesSentLatch;
    private final int totalMessagesCount;
    private final ExecutorService executor;

    public ClientBenchmarkUnit(Client client,
                               IoSession[] sessions,
                               Config config
    ) {
        this.client = client;
        this.config = config;
        this.sessions = sessions;

        // create executor with fixed number of threads == number of all sessions
        executor = Executors.newFixedThreadPool(sessions.length);

        totalMessagesCount = config.getSessionsCount() * config.getMessagesPerSession();
        latencies = new double[totalMessagesCount];
        messagesSentLatch = new CountDownLatch(totalMessagesCount);
    }

    @SneakyThrows
    @Override
    public void messageSent(IoSession session, Object message) {
        long receiveTime = System.nanoTime();

        // release semaphore to allow 'MessageSender' thread to write to the session
        Semaphore semaphore = (Semaphore) session.getAttribute(SEMAPHORE_ATTR);
        semaphore.release();

        long latency = receiveTime - Long.parseLong((String) message);
        latencies[sentMessagesCounter.getAndIncrement()] = latency;

        messagesSentLatch.countDown();
    }

    public void init() {
        client.registerListener(this);
    }

    public void destroy() {
        client.unregisterListener(this);
        executor.shutdownNow();
    }

    public BenchmarkResult run(BenchmarkRequest request) throws InterruptedException {
        long startTime = System.currentTimeMillis();

        for (IoSession session : sessions) {
            // create semaphore per each session to be able to write with the same pace as NioProcessors writes to server
            Semaphore semaphore = new Semaphore(SEM_PERMITS_PER_SESSION);
            session.setAttribute(SEMAPHORE_ATTR, semaphore);

            // start sending messages to server; each thread sends config.messagesPerSession messages
            executor.execute(new MessageSender(session, semaphore, config.getMessagesPerSession()));
        }

        messagesSentLatch.await();

        long endTime = System.currentTimeMillis();

        assert totalMessagesCount == sentMessagesCounter.get();

        return createResult(request, endTime - startTime);
    }

    private BenchmarkResult createResult(BenchmarkRequest request, long totalTimeInMillis) {

        // lets ignore some first elements - consider it as a warmup
        int startIndex = (int) Math.round(latencies.length * request.cutOffRatio);
        int length = latencies.length - startIndex;

        Map<Integer, Long> resPercentiles = new TreeMap<>();
        for (var p : request.getPercentiles()) {
            double percentileInNanos = StatUtils.percentile(latencies, startIndex, length, p);
            resPercentiles.put(p, request.percentilesTimeUnit.convert((long) percentileInNanos, TimeUnit.NANOSECONDS));
        }

        return BenchmarkResult.builder()
                .percentilesTimeUnit(request.percentilesTimeUnit)
                .totalTimeUnit(request.totalTimeUnit)
                .percentiles(resPercentiles)
                .totalTime(request.totalTimeUnit.convert(totalTimeInMillis, TimeUnit.MILLISECONDS))
                .build();
    }

    @Builder
    @Getter
    @ToString
    public static class BenchmarkRequest {
        private final TimeUnit totalTimeUnit;
        private final TimeUnit percentilesTimeUnit;

        @Builder.Default
        private final int[] percentiles = new int[]{25, 50, 75, 90, 95, 99, 100};
        @Builder.Default
        private final double cutOffRatio = 0.2;
    }

    @Getter
    @Builder
    @ToString
    public static class BenchmarkResult {
        private final TimeUnit totalTimeUnit;
        private final TimeUnit percentilesTimeUnit;
        private final long totalTime;
        private final Map<Integer, Long> percentiles;
    }

    private static class MessageSender implements Runnable {

        private final IoSession session;
        private final Semaphore semaphore;
        private final long messagesCount;

        public MessageSender(IoSession session, Semaphore semaphore, long messagesCount) {
            this.session = session;
            this.semaphore = semaphore;
            this.messagesCount = messagesCount;
        }

        @Override
        public void run() {
            for (int j = 0; j < messagesCount; j++) {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                session.write(String.valueOf(System.nanoTime()));
            }
        }
    }

}
