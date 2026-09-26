package bench;

import java.util.List;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.ConfigurableApplicationContext;

public final class StartupBenchmark {

    private static final String SCENARIO =
            System.getProperty("bench.scenario", "unknown");

    private StartupBenchmark() {
    }

    public static void main(String[] args) {

        if (args.length == 0) {
            throw new IllegalArgumentException(
                    "Expected mode: info, single, or repeat");
        }

        switch (args[0]) {
            case "info" -> printInfo();

            case "single" -> {
                long nanos = runOnce();

                printResult(
                        1,
                        nanos);
            }

            case "repeat" -> {
                if (args.length != 3) {
                    throw new IllegalArgumentException(
                            "repeat requires warmup and iteration counts");
                }

                int warmups =
                        Integer.parseInt(args[1]);

                int iterations =
                        Integer.parseInt(args[2]);

                runRepeated(
                        warmups,
                        iterations);
            }

            default -> throw new IllegalArgumentException(
                    "Unknown mode: " + args[0]);
        }
    }

    private static void printInfo() {

        ClassLoader classLoader =
                StartupBenchmark.class.getClassLoader();

        List<String> candidates =
                ImportCandidates
                        .load(
                                AutoConfiguration.class,
                                classLoader)
                        .getCandidates();

        System.out.printf(
                "INFO,%s,candidates,%d%n",
                SCENARIO,
                candidates.size());
    }

    private static void runRepeated(
            int warmups,
            int iterations) {

        for (int i = 1; i <= warmups; i++) {

            long nanos = runOnce();

            System.out.printf(
                    "WARMUP,%s,%d,%d,%.3f%n",
                    SCENARIO,
                    i,
                    nanos,
                    nanos / 1_000_000.0);
        }

        for (int i = 1; i <= iterations; i++) {

            long nanos = runOnce();

            printResult(
                    i,
                    nanos);
        }
    }

    private static long runOnce() {

        long start = System.nanoTime();

        ConfigurableApplicationContext context =
                new SpringApplicationBuilder(
                        BenchmarkApplication.class)
                        .web(WebApplicationType.NONE)
                        .bannerMode(Banner.Mode.OFF)
                        .logStartupInfo(false)
                        .registerShutdownHook(false)
                        .properties(
                                "spring.jmx.enabled=false")
                        .run();

        long elapsed =
                System.nanoTime() - start;

        context.close();

        return elapsed;
    }

    private static void printResult(
            int iteration,
            long nanos) {

        System.out.printf(
                "RESULT,%s,%d,%d,%.3f%n",
                SCENARIO,
                iteration,
                nanos,
                nanos / 1_000_000.0);
    }

}
