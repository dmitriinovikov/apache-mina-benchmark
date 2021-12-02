package benchmark;

import benchmark.ClientBenchmarkUnit.BenchmarkResult;
import benchmark.ClientBenchmarkUnit.BenchmarkRequest;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.util.AvailablePortFinder;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class ClientBenchmark extends AbstractBenchmark {

    private final Config config;
    private final Client client;
    private final Server server;
    private final IoSession[] clientSessions;

    private final List<BenchmarkResult> benchmarkResults = new LinkedList<>();

    public final BenchmarkRequest defaultBenchmarkReq = BenchmarkRequest.builder()
            .percentilesTimeUnit(TimeUnit.MICROSECONDS)
            .totalTimeUnit(TimeUnit.MILLISECONDS)
            .build();

    @SneakyThrows
    public ClientBenchmark(Config config) {
        super(config.warmupIterations, config.iterations);
        this.config = config;

        client = new Client();

        if (config.blockingClientIoServiceStats) {
            System.out.println("BLOCKING Client IoServiceStatistics implementation is used");
        } else {
            client.makeIoServiceStatisticsNonBlocking();
            System.out.println("NON-BLOCKING Client IoServiceStatistics implementation is used");
        }

        server = new Server();

        clientSessions = new IoSession[config.getSessionsCount()];
    }


    @Override
    @SneakyThrows
    protected void beforeRun() {
        int port = AvailablePortFinder.getNextAvailable();

        server.start(port);
        client.init();

        for (int i = 0; i < config.getSessionsCount(); i++) {
            clientSessions[i] = client.establishNewSession(port);
        }
    }

    private ClientBenchmarkUnit clientBenchmarkUnit;

    @Override
    protected void beforeIteration() {
        clientBenchmarkUnit = new ClientBenchmarkUnit(client, clientSessions, config);
        clientBenchmarkUnit.init();
    }

    @Override
    @SneakyThrows
    protected void warmupIterationImpl() {
        var result = clientBenchmarkUnit.run(defaultBenchmarkReq);
        printIterationResult(result);
    }

    @Override
    @SneakyThrows
    protected void iterationImpl() {
        var result = clientBenchmarkUnit.run(defaultBenchmarkReq);
        benchmarkResults.add(result);
        printIterationResult(result);
    }

    @Override
    protected void afterIteration() {
        clientBenchmarkUnit.destroy();
    }

    @Override
    protected void printAllResults() {

        if (benchmarkResults.isEmpty()) {
            System.out.println("There are no results!");
            return;
        }

        BenchmarkResult firstBenchmarkResult = benchmarkResults.get(0);

        // validate results format
        for (BenchmarkResult benchmarkResult : benchmarkResults) {
            assert benchmarkResult.getPercentilesTimeUnit() == firstBenchmarkResult.getPercentilesTimeUnit();
            assert benchmarkResult.getTotalTimeUnit() == firstBenchmarkResult.getTotalTimeUnit();
            assert benchmarkResult.getPercentiles().size() == firstBenchmarkResult.getPercentiles().size();

            for (Integer p : firstBenchmarkResult.getPercentiles().keySet()) {
                assert benchmarkResult.getPercentiles().containsKey(p);
            }
        }

        // compute single aggregated result for all iterations
        Map<Integer, Long> aggregatedPercentiles = new TreeMap<>();

        for (Integer currentPercentile : firstBenchmarkResult.getPercentiles().keySet()) {
            double[] currentPercentileValues = new double[benchmarkResults.size()];

            for (int i = 0; i < benchmarkResults.size(); i++) {
                currentPercentileValues[i] = benchmarkResults.get(i).getPercentiles().get(currentPercentile);
            }
            aggregatedPercentiles.put(currentPercentile, (long) StatUtils.mean(currentPercentileValues));
        }

        double[] totalTimes = new double[benchmarkResults.size()];

        for (int i = 0; i < benchmarkResults.size(); i++) {
            totalTimes[i] = benchmarkResults.get(i).getTotalTime();
        }

        System.out.println("================================================");
        System.out.println("SUMMARY RESULTS");
        System.out.println("================================================");
        System.out.printf("Mean Result for %d iterations: \n", benchmarkResults.size());
        printPercentiles(aggregatedPercentiles, firstBenchmarkResult.getPercentilesTimeUnit());
        System.out.println("================================================\n");
    }

    @Override
    @SneakyThrows
    protected void afterRun() {
        client.stop();
        server.stop();
    }

    private void printIterationResult(BenchmarkResult result) {
        System.out.println("Results: ");
        printPercentiles(result.getPercentiles(), result.getPercentilesTimeUnit());
        System.out.println((String.format("Total execution time: %d %s%n",
                result.getTotalTime(), convertTimeUnitToAcronym(result.getTotalTimeUnit()))));
    }

    private void printPercentiles(Map<Integer, Long> percentiles, TimeUnit timeUnit) {
        var builder = new StringBuilder();
        var percentilesTimeUnitAcronym = convertTimeUnitToAcronym(timeUnit);
        percentiles.forEach((k, v) -> builder.append(String.format("p%d: %d %s, ", k, v, percentilesTimeUnitAcronym)));
        System.out.println(builder);
    }

    private String convertTimeUnitToAcronym(TimeUnit timeUnit) {
        switch (timeUnit) {
            case NANOSECONDS:
                return "ns";
            case MICROSECONDS:
                return "mcs";
            case MILLISECONDS:
                return "ms";
            case SECONDS:
                return "sec";
            default:
                return timeUnit.name();
        }
    }

    @Data
    public static class Config {
        private int sessionsCount = 50;
        private int messagesPerSession = 50000;
        private int warmupIterations = 3;
        private int iterations = 10;
        private boolean blockingClientIoServiceStats = false;
    }


}
