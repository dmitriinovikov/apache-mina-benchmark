package benchmark;

public class BenchmarkRunner {

    public static final String BLOCKING_CLIENT_IO_SERVICE_STATS_VM_OPTION = "blockingClientIoServiceStatistics";

    public static void main(String[] args) {
        ClientBenchmark.Config config = new ClientBenchmark.Config();

        boolean blockingClientStatistics = Boolean.parseBoolean(System.getProperty(BLOCKING_CLIENT_IO_SERVICE_STATS_VM_OPTION));
        config.setBlockingClientIoServiceStats(blockingClientStatistics);

        var benchmark = new ClientBenchmark(config);
        benchmark.run();
    }
}
