package benchmark;

public abstract class AbstractBenchmark implements Benchmark {

    private final int warmupIterationsCount;
    private final int iterationsCount;

    public AbstractBenchmark(
            int warmupIterationsCount,
            int iterationsCount
    ) {
        this.warmupIterationsCount = warmupIterationsCount;
        this.iterationsCount = iterationsCount;
    }

    @Override
    public void run() {
        System.out.println("Running benchmark...");

        beforeRun();

        for (int i = 0; i < warmupIterationsCount; i++) {
            System.out.printf("Warmup iteration %d/%d%n", i + 1, warmupIterationsCount);
            iteration(true);
        }

        for (int i = 0; i < iterationsCount; i++) {
            System.out.printf("Iteration %d/%d%n", i + 1, iterationsCount);
            iteration(false);
        }

        printAllResults();

        afterRun();
    }

    private void iteration(boolean isWarmup) {
        beforeIteration();

        if (isWarmup) {
            warmupIterationImpl();
        } else {
            iterationImpl();
        }

        afterIteration();
    }

    protected abstract void beforeRun();

    protected abstract void beforeIteration();

    protected abstract void iterationImpl();

    protected abstract void warmupIterationImpl();

    protected abstract void afterIteration();

    protected abstract void printAllResults();

    protected abstract void afterRun();

}
