package org.springframework.boot.autoconfigure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * Isolated benchmark for AutoConfigurationSorter.
 *
 * The benchmark deliberately supplies AutoConfigurationMetadata for every
 * synthetic class so that metadata/class reading is not included in the
 * measured operation. This isolates the graph sorting algorithm itself.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 8, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Thread)
public class AutoConfigurationSorterBenchmark {

    @Param({
            "NONE",
            "CHAIN",
            "FAN_OUT",
            "FAN_IN",
            "MIXED"
    })
    public String shape;

    @Param({
            "64",
            "128",
            "256",
            "512",
            "1024",
            "2048"
    })
    public int size;

    private List<String> names;

    private Map<String, Set<String>> before;

    private Map<String, Set<String>> after;

    private AutoConfigurationSorter sorter;

    @Setup(Level.Trial)
    public void setup() {
        this.names = createNames(this.size);
        this.before = createEmptyGraph(this.names);
        this.after = createEmptyGraph(this.names);

        populateGraph();

        AutoConfigurationMetadata metadata =
                new SyntheticAutoConfigurationMetadata(
                        new HashSet<>(this.names),
                        this.before,
                        this.after);

        this.sorter = new AutoConfigurationSorter(
                new FailingMetadataReaderFactory(),
                metadata,
                null);

        verifyResult(this.sorter.getInPriorityOrder(this.names));
    }

    @Benchmark
    public List<String> sort() {
        return this.sorter.getInPriorityOrder(this.names);
    }

    private void populateGraph() {
        switch (this.shape) {
            case "NONE" -> populateNone();
            case "CHAIN" -> populateChain();
            case "FAN_OUT" -> populateFanOut();
            case "FAN_IN" -> populateFanIn();
            case "MIXED" -> populateMixed();
            default -> throw new IllegalStateException(
                    "Unknown graph shape: " + this.shape);
        }
    }

    private void populateNone() {
        // Deliberately empty.
    }

    /**
     * Every node except the first is configured after its predecessor:
     *
     * 0 -> 1 -> 2 -> 3 ...
     */
    private void populateChain() {
        for (int i = 1; i < this.names.size(); i++) {
            this.after.get(name(i)).add(name(i - 1));
        }
    }

    /**
     * The first node declares that it must be before every other node.
     *
     * This exercises reverse lookup of AutoConfigureBefore relationships:
     *
     *       -> 1
     *      /
     * 0 --+--> 2
     *      \
     *       -> 3 ...
     */
    private void populateFanOut() {
        String root = name(0);

        for (int i = 1; i < this.names.size(); i++) {
            this.before.get(root).add(name(i));
        }
    }

    /**
     * The final node is configured after every earlier node.
     *
     * This gives getClassesRequestedAfter a large direct dependency set
     * and stresses the index-based TreeSet comparator.
     *
     * 0 --\
     * 1 ---+--> N
     * 2 --/
     */
    private void populateFanIn() {
        String sink = name(this.names.size() - 1);

        for (int i = 0; i < this.names.size() - 1; i++) {
            this.after.get(sink).add(name(i));
        }
    }

    /**
     * Deterministic sparse DAG containing both Before and After edges.
     * Every edge points from a lower index to a higher index, preventing
     * accidental cycles.
     */
    private void populateMixed() {
        for (int i = 1; i < this.names.size(); i++) {
            if ((i & 1) == 0) {
                this.after.get(name(i)).add(name(i - 1));
            }
            else {
                this.before.get(name(i - 1)).add(name(i));
            }

            if (i >= 4 && i % 4 == 0) {
                this.after.get(name(i)).add(name(i - 4));
            }

            if (i % 8 == 0 && i + 7 < this.names.size()) {
                this.before.get(name(i)).add(name(i + 7));
            }
        }
    }

    private String name(int index) {
        return this.names.get(index);
    }

    private List<String> createNames(int size) {
        List<String> result = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            result.add("example.AutoConfiguration%05d".formatted(i));
        }

        return Collections.unmodifiableList(result);
    }

    private Map<String, Set<String>> createEmptyGraph(
            List<String> names) {

        Map<String, Set<String>> result =
                new LinkedHashMap<>(names.size());

        for (String name : names) {
            result.put(name, new LinkedHashSet<>());
        }

        return result;
    }

    /**
     * Verify that the sorter produced exactly the requested classes and that
     * every synthetic Before/After constraint is respected.
     *
     * This runs in trial setup and is therefore outside the measured operation.
     */
    private void verifyResult(List<String> result) {
        if (result.size() != this.names.size()) {
            throw new AssertionError(
                    "Expected " + this.names.size()
                            + " classes but got " + result.size());
        }

        if (!new HashSet<>(result).equals(new HashSet<>(this.names))) {
            throw new AssertionError(
                    "Sorted result does not contain exactly the input classes");
        }

        Map<String, Integer> positions =
                new HashMap<>(result.size());

        for (int i = 0; i < result.size(); i++) {
            positions.put(result.get(i), i);
        }

        for (String configuration : this.names) {
            int configurationPosition = positions.get(configuration);

            for (String dependency : this.after.get(configuration)) {
                int dependencyPosition = positions.get(dependency);

                if (dependencyPosition >= configurationPosition) {
                    throw new AssertionError(
                            configuration
                                    + " must be after "
                                    + dependency);
                }
            }

            for (String target : this.before.get(configuration)) {
                int targetPosition = positions.get(target);

                if (configurationPosition >= targetPosition) {
                    throw new AssertionError(
                            configuration
                                    + " must be before "
                                    + target);
                }
            }
        }
    }

    private static final class SyntheticAutoConfigurationMetadata
            implements AutoConfigurationMetadata {

        private final Set<String> classes;

        private final Map<String, Set<String>> before;

        private final Map<String, Set<String>> after;

        private SyntheticAutoConfigurationMetadata(
                Set<String> classes,
                Map<String, Set<String>> before,
                Map<String, Set<String>> after) {

            this.classes = classes;
            this.before = before;
            this.after = after;
        }

        @Override
        public boolean wasProcessed(String className) {
            return this.classes.contains(className);
        }

        @Override
        public Integer getInteger(String className, String key) {
            return null;
        }

        @Override
        public Integer getInteger(
                String className,
                String key,
                Integer defaultValue) {

            return defaultValue;
        }

        @Override
        public Set<String> getSet(
                String className,
                String key) {

            return getSet(className, key, null);
        }

        @Override
        public Set<String> getSet(
                String className,
                String key,
                Set<String> defaultValue) {

            Set<String> value = switch (key) {
                case "AutoConfigureBefore" ->
                        this.before.get(className);
                case "AutoConfigureAfter" ->
                        this.after.get(className);
                default -> null;
            };

            return (value != null) ? value : defaultValue;
        }

        @Override
        public String get(String className, String key) {
            return null;
        }

        @Override
        public String get(
                String className,
                String key,
                String defaultValue) {

            return defaultValue;
        }

    }

    /**
     * Metadata reading must not happen during this benchmark. If it does,
     * the benchmark is no longer isolating the sorting algorithm.
     */
    private static final class FailingMetadataReaderFactory
            implements MetadataReaderFactory {

        @Override
        public MetadataReader getMetadataReader(
                String className) {

            throw new AssertionError(
                    "Unexpected metadata read for " + className);
        }

        @Override
        public MetadataReader getMetadataReader(
                Resource resource) {

            throw new AssertionError(
                    "Unexpected metadata read for " + resource);
        }

        @Override
        public ResourceLoader getResourceLoader() {
            throw new AssertionError(
                    "Unexpected resource loader access");
        }

    }

}
