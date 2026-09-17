import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RateForge {
    private static final Scanner scanner = new Scanner(System.in);
    private static final long DEFAULT_WINDOW_MS = 1000;
    private static final int DEFAULT_LIMIT = 100;
    interface RateLimiter {
        boolean allowRequest(long timestamp);
        String getName();
        String getStatus();
        void reset();
    }

    /*Fixed window*/

    static class FixedWindowLimiter implements RateLimiter {
        private final int limit;
        private final long windowSize;
        private long windowStart;
        private int requestCount;
        public FixedWindowLimiter(int limit, long windowSize) {
            this.limit = limit;
            this.windowSize = windowSize;
            reset();
        }
        @Override
        public boolean allowRequest(long timestamp) {
            if (windowStart < 0) {
                windowStart = timestamp;
            }
            if (timestamp >= windowStart + windowSize) {
                long windowsPassed =
                        (timestamp - windowStart) / windowSize;
                windowStart += windowsPassed * windowSize;
                requestCount = 0;
            }
            if (requestCount < limit) {
                requestCount++;
                return true;
            }
            return false;
        }

        @Override
        public String getName() {
            return "Fixed Window";
        }

        @Override
        public String getStatus() {
            return "Requests in window: "
                    + requestCount + "/" + limit;
        }

        @Override
        public void reset() {
            windowStart = -1;
            requestCount = 0;
        }
    }

    /*Sliding window*/

    static class SlidingWindowLimiter implements RateLimiter {
        private final int limit;
        private final long windowSize;
        private final Deque<Long> timestamps =
                new ArrayDeque<>();

        public SlidingWindowLimiter(int limit, long windowSize) {
            this.limit = limit;
            this.windowSize = windowSize;
        }

        @Override
        public boolean allowRequest(long timestamp) {
            removeExpired(timestamp);
            if (timestamps.size() < limit) {
                timestamps.addLast(timestamp);
                return true;
            }
            return false;
        }

        private void removeExpired(long timestamp) {
            while (!timestamps.isEmpty()
                    && timestamps.peekFirst()
                    <= timestamp - windowSize) {
                timestamps.removeFirst();
            }
        }
        @Override
        public String getName() {
            return "Sliding Window";
        }

        @Override
        public String getStatus() {
            return "Requests in rolling window: "
                    + timestamps.size() + "/" + limit;
        }

        @Override
        public void reset() {
            timestamps.clear();
        }
    }

    /*Token bucket*/
    
    static class TokenBucketLimiter implements RateLimiter {
        private final double capacity;
        private final double refillRatePerMs;
        private double tokens;
        private long lastRefillTime;
        public TokenBucketLimiter(
                int capacity,
                double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerMs =
                    refillRatePerSecond / 1000.0;
            reset();
        }

        @Override
        public boolean allowRequest(long timestamp) {
            refill(timestamp);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
        private void refill(long timestamp) {
            if (lastRefillTime < 0) {
                lastRefillTime = timestamp;
                return;
            }
            long elapsed =
                    timestamp - lastRefillTime;
            if (elapsed > 0) {
                tokens +=
                        elapsed * refillRatePerMs;
                tokens = Math.min(tokens, capacity);
                lastRefillTime = timestamp;
            }
        }

        @Override
        public String getName() {
            return "Token Bucket";
        }

        @Override
        public String getStatus() {
            return String.format(
                    "Tokens available: %.2f/%.0f",
                    tokens,
                    capacity
            );
        }

        @Override
        public void reset() {
            tokens = capacity;
            lastRefillTime = -1;
        }
    }

    /*Lweaky bucket*/
    

    static class LeakyBucketLimiter implements RateLimiter {
        private final double capacity;
        private final double leakRatePerMs;
        private double bucketLevel;
        private long lastUpdateTime;
        public LeakyBucketLimiter(
                int capacity,
                double leakRatePerSecond) {
            this.capacity = capacity;
            this.leakRatePerMs =
                    leakRatePerSecond / 1000.0;
            reset();
        }

        @Override
        public boolean allowRequest(long timestamp) {
            leak(timestamp);
            if (bucketLevel < capacity) {
                bucketLevel += 1.0;
                return true;
            }
            return false;
        }
        private void leak(long timestamp) {
            if (lastUpdateTime < 0) {
                lastUpdateTime = timestamp;
                return;
            }
            long elapsed =
                    timestamp - lastUpdateTime;
            if (elapsed > 0) {
                bucketLevel -=
                        elapsed * leakRatePerMs;
                bucketLevel =
                        Math.max(0, bucketLevel);
                lastUpdateTime = timestamp;
            }
        }
        @Override
        public String getName() {
            return "Leaky Bucket";
        }
        @Override
        public String getStatus() {
            return String.format(
                    "Bucket level: %.2f/%.0f",
                    bucketLevel,
                    capacity
            );
        }
        @Override
        public void reset() {
            bucketLevel = 0;
            lastUpdateTime = -1;
        }
    }

    /*traffic patterns*/
    

    enum TrafficPattern {
        NORMAL,
        OVERLOAD,
        BURST,
        PERIODIC,
        RANDOM
    }

    /*Simulated requests*/
    
    static class SimulatedRequest {
        private final int id;
        private final long timestamp;
        public SimulatedRequest(
                int id,
                long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }
        public int getId() {
            return id;
        }
        public long getTimestamp() {
            return timestamp;
        }
    }

    /*trafic generater*/
    
    static class TrafficGenerator {

        private final Random random =
                new Random();
        public List<SimulatedRequest> generate(
                TrafficPattern pattern,
                int durationSeconds,
                int baseRate,
                int peakRate) {
            List<SimulatedRequest> requests =
                    new ArrayList<>();
            long currentTime = 0;
            int requestId = 1;
            switch (pattern) {
                case NORMAL:
                    currentTime = generateConstantRate(
                            requests,
                            requestId,
                            durationSeconds,
                            baseRate,
                            currentTime
                    );
                    break;
                case OVERLOAD:
                    currentTime = generateConstantRate(
                            requests,
                            requestId,
                            durationSeconds,
                            peakRate,
                            currentTime
                    );
                    break;
                case BURST:
                    currentTime = generateBurstTraffic(
                            requests,
                            requestId,
                            durationSeconds,
                            baseRate,
                            peakRate,
                            currentTime
                    );
                    break;
                case PERIODIC:
                    currentTime = generatePeriodicTraffic(
                            requests,
                            requestId,
                            durationSeconds,
                            baseRate,
                            peakRate,
                            currentTime
                    );
                    break;
                case RANDOM:
                    currentTime = generateRandomTraffic(
                            requests,
                            requestId,
                            durationSeconds,
                            baseRate,
                            peakRate,
                            currentTime
                    );
                    break;
            }
            return requests;
        }

        private long generateConstantRate(
                List<SimulatedRequest> requests,
                int requestId,
                int durationSeconds,
                int rate,
                long currentTime) {
            if (rate <= 0) {
                return currentTime;
            }
            double interval =
                    1000.0 / rate;
            long endTime =
                    durationSeconds * 1000L;
            while (currentTime < endTime) {
                requests.add(
                        new SimulatedRequest(
                                requestId++,
                                currentTime
                        )
                );
                currentTime =
                        Math.max(
                                currentTime + 1,
                                Math.round(
                                        currentTime + interval
                                )
                        );
            }
            return currentTime;
        }

    /*burst*/

        private long generateBurstTraffic(
                List<SimulatedRequest> requests,
                int requestId,
                int durationSeconds,
                int baseRate,
                int peakRate,
                long currentTime) {
            long duration =
                    durationSeconds * 1000L;
            long burstStart =
                    duration / 3;
            long burstEnd =
                    burstStart + 1000;
            while (currentTime < duration) {
                int rate;
                if (currentTime >= burstStart
                        && currentTime < burstEnd) {
                    rate = peakRate;
                } else {
                    rate = baseRate;
                }
                double interval =
                        1000.0 / Math.max(rate, 1);
                requests.add(
                        new SimulatedRequest(
                                requestId++,
                                currentTime
                        )
                );
                currentTime =
                        Math.max(
                                currentTime + 1,
                                Math.round(
                                        currentTime + interval
                                )
                        );
            }
            return currentTime;
        }

    /*periodic*/
    
        private long generatePeriodicTraffic(
                List<SimulatedRequest> requests,
                int requestId,
                int durationSeconds,
                int baseRate,
                int peakRate,
                long currentTime) {
            long duration =
                    durationSeconds * 1000L;
            while (currentTime < duration) {
                long cyclePosition =
                        currentTime % 2000;
                int rate;
                if (cyclePosition < 500) {
                    rate = peakRate;
                } else {
                    rate = baseRate;
                }
                double interval =
                        1000.0 / Math.max(rate, 1);
                requests.add(
                        new SimulatedRequest(
                                requestId++,
                                currentTime
                        )
                );
                currentTime =
                        Math.max(
                                currentTime + 1,
                                Math.round(
                                        currentTime + interval
                                )
                        );
            }
            return currentTime;
        }

    /*random traffic*/
    
        private long generateRandomTraffic(
                List<SimulatedRequest> requests,
                int requestId,
                int durationSeconds,
                int baseRate,
                int peakRate,
                long currentTime) {
            long duration =
                    durationSeconds * 1000L;
            double minimumInterval =
                    1000.0 / Math.max(peakRate, 1);
            double maximumInterval =
                    1000.0 / Math.max(baseRate, 1);
            while (currentTime < duration) {
                requests.add(
                        new SimulatedRequest(
                                requestId++,
                                currentTime
                        )
                );
                double interval =
                        minimumInterval
                                + random.nextDouble()
                                * (maximumInterval
                                - minimumInterval);
                currentTime =
                        Math.max(
                                currentTime + 1,
                                Math.round(
                                        currentTime + interval
                                )
                        );
            }
            return currentTime;
        }
    }

    /*Benchmark result*/
    
    static class BenchmarkResult {
        private final String algorithm;
        private final int totalRequests;
        private final int accepted;
        private final int rejected;
        private final double acceptanceRate;
        private final double rejectionRate;
        private final long processingTimeNanos;
        public BenchmarkResult(
                String algorithm,
                int totalRequests,
                int accepted,
                int rejected,
                long processingTimeNanos) {
            this.algorithm = algorithm;
            this.totalRequests = totalRequests;
            this.accepted = accepted;
            this.rejected = rejected;
            this.acceptanceRate =
                    totalRequests == 0
                            ? 0
                            : (accepted * 100.0)
                            / totalRequests;
            this.rejectionRate =
                    totalRequests == 0
                            ? 0
                            : (rejected * 100.0)
                            / totalRequests;
            this.processingTimeNanos =
                    processingTimeNanos;
        }
        public String getAlgorithm() {
            return algorithm;
        }
        public int getTotalRequests() {
            return totalRequests;
        }
        public int getAccepted() {
            return accepted;
        }
        public int getRejected() {
            return rejected;
        }
        public double getAcceptanceRate() {
            return acceptanceRate;
        }
        public double getRejectionRate() {
            return rejectionRate;
        }
        public long getProcessingTimeNanos() {
            return processingTimeNanos;
        }
        public double getProcessingEventsPerSecond() {
            if (processingTimeNanos <= 0) {
                return 0;
            }
            return totalRequests /
                    (processingTimeNanos / 1_000_000_000.0);
        }
    }

    /*benchmark engine*/
    
    static class BenchmarkEngine {
        public BenchmarkResult run(
                RateLimiter limiter,
                List<SimulatedRequest> requests) {
            limiter.reset();
            int accepted = 0;
            int rejected = 0;
            long start =
                    System.nanoTime();
            for (SimulatedRequest request : requests) {
                boolean allowed =
                        limiter.allowRequest(
                                request.getTimestamp()
                        );
                if (allowed) {
                    accepted++;
                } else {
                    rejected++;
                }
            }
            long end =
                    System.nanoTime();
            return new BenchmarkResult(
                    limiter.getName(),
                    requests.size(),
                    accepted,
                    rejected,
                    end - start
            );
        }

    /*live simulation*/
    
        public void liveSimulation(
                RateLimiter limiter,
                List<SimulatedRequest> requests) {
            limiter.reset();
            System.out.println();
            System.out.println(
                    "=================================================="
            );
            System.out.println(
                    "LIVE SIMULATION - " + limiter.getName()
            );
            System.out.println(
                    "=================================================="
            );
            System.out.printf(
                    "%-8s %-12s %-12s %s%n",
                    "Request",
                    "Time(ms)",
                    "Decision",
                    "State"
            );
            System.out.println(
                    "--------------------------------------------------"
            );
            int displayLimit =
                    Math.min(requests.size(), 100);
            for (int i = 0; i < displayLimit; i++) {
                SimulatedRequest request =
                        requests.get(i);
                boolean allowed =
                        limiter.allowRequest(
                                request.getTimestamp()
                        );
                System.out.printf(
                        "%-8d %-12d %-12s %s%n",
                        request.getId(),
                        request.getTimestamp(),
                        allowed
                                ? "ALLOWED"
                                : "REJECTED",
                        limiter.getStatus()
                );
            }
            if (requests.size() > displayLimit) {
                System.out.println();
                System.out.println(
                        "... showing first "
                                + displayLimit
                                + " requests ..."
                );
            }
        }
    }

    /*config*/
    

    static class Configuration {
        int rateLimit =
                DEFAULT_LIMIT;
        int durationSeconds =
                10;
        int baseRate =
                50;
        int peakRate =
                500;
        TrafficPattern trafficPattern =
                TrafficPattern.NORMAL;
        String algorithm =
                "all";
        List<BenchmarkResult> lastResults =
                new ArrayList<>();
    }

    /*create limiter*/

    static RateLimiter createLimiter(
            String algorithm,
            int limit,
            long window) {
        switch (algorithm.toLowerCase()) {
            case "fixed":
                return new FixedWindowLimiter(
                        limit,
                        window
                );
            case "sliding":
                return new SlidingWindowLimiter(
                        limit,
                        window
                );
            case "token":
                return new TokenBucketLimiter(
                        limit,
                        limit
                );
            case "leaky":
                return new LeakyBucketLimiter(
                        limit,
                        limit
                );
            default:
                throw new IllegalArgumentException(
                        "Unknown algorithm: "
                                + algorithm
                );
        }
    }

    static List<RateLimiter> createAllLimiters(
            int limit,
            long window) {
        List<RateLimiter> limiters =
                new ArrayList<>();
        limiters.add(
                createLimiter(
                        "fixed",
                        limit,
                        window
                )
        );
        limiters.add(
                createLimiter(
                        "sliding",
                        limit,
                        window
                )
        );
        limiters.add(
                createLimiter(
                        "token",
                        limit,
                        window
                )
        );
        limiters.add(
                createLimiter(
                        "leaky",
                        limit,
                        window
                )
        );
        return limiters;
    }

    /*main menu*/
    
    static void showMainMenu() {
        System.out.println();
        System.out.println(
                "=============================================================="
        );
        System.out.println(
                "                         RATEFORGE"
        );
        System.out.println(
                "              Rate Limiting Experimentation"
        );
        System.out.println(
                "=============================================================="
        );
        System.out.println();
        System.out.println("1. Run a single algorithm");
        System.out.println("2. Compare all algorithms");
        System.out.println("3. Live request simulation");
        System.out.println("4. Run experiment preset");
        System.out.println("5. Learn about algorithms");
        System.out.println("6. Export last results to CSV");
        System.out.println("7. Exit");
        System.out.println();
        System.out.print("Choose an option: ");
    }

    /*Single algo*/
    
    static void runSingleAlgorithm(
            Configuration config) {
        System.out.println();
        System.out.println(
                "Select Algorithm"
        );
        System.out.println("1. Fixed Window");
        System.out.println("2. Sliding Window");
        System.out.println("3. Token Bucket");
        System.out.println("4. Leaky Bucket");
        int choice =
                readInt(
                        "Choice: ",
                        1,
                        4
                );
        String algorithm;
        switch (choice) {
            case 1:
                algorithm = "fixed";
                break;
            case 2:
                algorithm = "sliding";
                break;
            case 3:
                algorithm = "token";
                break;
            default:
                algorithm = "leaky";
        }
        configureExperiment(config);
        RateLimiter limiter =
                createLimiter(
                        algorithm,
                        config.rateLimit,
                        DEFAULT_WINDOW_MS
                );
        List<SimulatedRequest> requests =
                generateTraffic(config);
        BenchmarkEngine engine =
                new BenchmarkEngine();
        BenchmarkResult result =
                engine.run(
                        limiter,
                        requests
                );
        config.lastResults =
                new ArrayList<>();
        config.lastResults.add(result);
        printExperimentHeader(
                config,
                requests
        );
        printResults(
                config.lastResults
        );
    }

    /* comparision*/

    static void compareAllAlgorithms(
            Configuration config) {
        configureExperiment(config);
        List<SimulatedRequest> requests =
                generateTraffic(config);
        List<RateLimiter> limiters =
                createAllLimiters(
                        config.rateLimit,
                        DEFAULT_WINDOW_MS
                );
        BenchmarkEngine engine =
                new BenchmarkEngine();
        config.lastResults =
                new ArrayList<>();
        printExperimentHeader(
                config,
                requests
        );
        System.out.println();
        System.out.println(
                "Running experiment..."
        );
        for (RateLimiter limiter : limiters) {
            System.out.println(
                    "  -> " + limiter.getName()
            );
            BenchmarkResult result =
                    engine.run(
                            limiter,
                            requests
                    );
            config.lastResults.add(
                    result
            );
        }
        System.out.println();
        printResults(
                config.lastResults
        );
        printAnalysis(
                config,
                config.lastResults
        );
    }


    /* live sim*/

    static void liveSimulation(
            Configuration config) {
        configureExperiment(config);
        List<SimulatedRequest> requests =
                generateTraffic(config);
        System.out.println();
        System.out.println(
                "Select Algorithm"
        );
        System.out.println("1. Fixed Window");
        System.out.println("2. Sliding Window");
        System.out.println("3. Token Bucket");
        System.out.println("4. Leaky Bucket");
        int choice =
                readInt(
                        "Choice: ",
                        1,
                        4
                );
        String algorithm;
        switch (choice) {
            case 1:
                algorithm = "fixed";
                break;
            case 2:
                algorithm = "sliding";
                break;
            case 3:
                algorithm = "token";
                break;
            default:
                algorithm = "leaky";
        }
        RateLimiter limiter =
                createLimiter(
                        algorithm,
                        config.rateLimit,
                        DEFAULT_WINDOW_MS
                );
        BenchmarkEngine engine =
                new BenchmarkEngine();
        engine.liveSimulation(
                limiter,
                requests
        );
        System.out.println();
        System.out.println(
                "Live simulation completed."
        );
    }

    /* config experiment*/

    static void configureExperiment(
            Configuration config) {
        System.out.println();
        System.out.println(
                "--------------------------------------------------------------"
        );
        System.out.println(
                "Experiment Configuration"
        );
        System.out.println(
                "--------------------------------------------------------------"
        );
        config.rateLimit =
                readPositiveInt(
                        "Rate limit (requests/sec) ["
                                + config.rateLimit
                                + "]: ",
                        config.rateLimit
                );
        config.durationSeconds =
                readPositiveInt(
                        "Duration (seconds) ["
                                + config.durationSeconds
                                + "]: ",
                        config.durationSeconds
                );
        config.baseRate =
                readPositiveInt(
                        "Base traffic rate (requests/sec) ["
                                + config.baseRate
                                + "]: ",
                        config.baseRate
                );
        config.peakRate =
                readPositiveInt(
                        "Peak traffic rate (requests/sec) ["
                                + config.peakRate
                                + "]: ",
                        config.peakRate
                );
        config.trafficPattern =
                selectTrafficPattern(
                        config.trafficPattern
                );
    }

    /* traffic pattern menu*/

    static TrafficPattern selectTrafficPattern(
            TrafficPattern current) {
        System.out.println();
        System.out.println(
                "Traffic Pattern"
        );
        System.out.println(
                "1. Normal      - steady traffic"
        );
        System.out.println(
                "2. Overload    - sustained high traffic"
        );
        System.out.println(
                "3. Burst       - normal traffic + sudden spike"
        );
        System.out.println(
                "4. Periodic    - recurring traffic spikes"
        );
        System.out.println(
                "5. Random      - variable traffic"
        );
        int choice =
                readInt(
                        "Choice: ",
                        1,
                        5
                );
        switch (choice) {
            case 1:
                return TrafficPattern.NORMAL;
            case 2:
                return TrafficPattern.OVERLOAD;
            case 3:
                return TrafficPattern.BURST;
            case 4:
                return TrafficPattern.PERIODIC;
            default:
                return TrafficPattern.RANDOM;
        }
    }

    /* traffic generation*/

    static List<SimulatedRequest> generateTraffic(
            Configuration config) {
        TrafficGenerator generator =
                new TrafficGenerator();
        System.out.println();
        System.out.println(
                "Generating traffic..."
        );
        List<SimulatedRequest> requests =
                generator.generate(
                        config.trafficPattern,
                        config.durationSeconds,
                        config.baseRate,
                        config.peakRate
                );
        System.out.println(
                "Generated "
                        + requests.size()
                        + " requests."
        );
        return requests;
    }


    static void runPreset(
            Configuration config) {
        System.out.println();
        System.out.println(
                "=============================================================="
        );
        System.out.println(
                "                    EXPERIMENT PRESETS"
        );
        System.out.println(
                "=============================================================="
        );

        System.out.println(
                "1. Light Traffic"
        );

        System.out.println(
                "2. Sustained Overload"
        );

        System.out.println(
                "3. Sudden Burst"
        );

        System.out.println(
                "4. Periodic Spikes"
        );

        System.out.println(
                "5. Extreme Overload"
        );

        System.out.println(
                "6. Custom"
        );

        int choice =
                readInt(
                        "Choice: ",
                        1,
                        6
                );
        switch (choice) {
            case 1:
                config.rateLimit = 100;
                config.durationSeconds = 10;
                config.baseRate = 50;
                config.peakRate = 50;
                config.trafficPattern =
                        TrafficPattern.NORMAL;
                break;
            case 2:
                config.rateLimit = 100;
                config.durationSeconds = 10;
                config.baseRate = 500;
                config.peakRate = 500;
                config.trafficPattern =
                        TrafficPattern.OVERLOAD;
                break;
            case 3:
                config.rateLimit = 100;
                config.durationSeconds = 10;
                config.baseRate = 20;
                config.peakRate = 500;
                config.trafficPattern =
                        TrafficPattern.BURST;
                break;
            case 4:
                config.rateLimit = 100;
                config.durationSeconds = 10;
                config.baseRate = 30;
                config.peakRate = 400;
                config.trafficPattern =
                        TrafficPattern.PERIODIC;
                break;
            case 5:
                config.rateLimit = 100;
                config.durationSeconds = 10;
                config.baseRate = 1000;
                config.peakRate = 1000;
                config.trafficPattern =
                        TrafficPattern.OVERLOAD;
                break;
            case 6:
                configureExperiment(config);
                break;
        }
        List<SimulatedRequest> requests =
                generateTraffic(config);
        List<RateLimiter> limiters =
                createAllLimiters(
                        config.rateLimit,
                        DEFAULT_WINDOW_MS
                );
        BenchmarkEngine engine =
                new BenchmarkEngine();
        config.lastResults =
                new ArrayList<>();
        printExperimentHeader(
                config,
                requests
        );
        for (RateLimiter limiter : limiters) {
            BenchmarkResult result =
                    engine.run(
                            limiter,
                            requests
                    );
            config.lastResults.add(
                    result
            );
        }
        printResults(
                config.lastResults
        );
        printAnalysis(
                config,
                config.lastResults
        );
    }

    static void printExperimentHeader(
            Configuration config,
            List<SimulatedRequest> requests) {
        System.out.println();
        System.out.println(
                "=============================================================="
        );
        System.out.println(
                "                    EXPERIMENT DETAILS"
        );
        System.out.println(
                "=============================================================="
        );
        System.out.println(
                "Rate Limit       : "
                        + config.rateLimit
                        + " requests/sec"
        );
        System.out.println(
                "Duration         : "
                        + config.durationSeconds
                        + " seconds"
        );
        System.out.println(
                "Base Traffic     : "
                        + config.baseRate
                        + " requests/sec"
        );
        System.out.println(
                "Peak Traffic     : "
                        + config.peakRate
                        + " requests/sec"
        );
        System.out.println(
                "Traffic Pattern  : "
                        + config.trafficPattern
        );
        System.out.println(
                "Total Requests   : "
                        + requests.size()
        );
        System.out.println(
                "Window Size      : "
                        + DEFAULT_WINDOW_MS
                        + " ms"
        );
        System.out.println(
                "=============================================================="
        );
    }

    /*results*/

    static void printResults(
            List<BenchmarkResult> results) {
        System.out.println();
        System.out.println(
                "Benchmark Results"
        );
        System.out.println(
                "----------------------------------------------------------------------------------------"
        );
        System.out.printf(
                "%-20s %-10s %-10s %-12s %-12s %-18s%n",
                "Algorithm",
                "Accepted",
                "Rejected",
                "Accept %",
                "Reject %",
                "Processing Events/sec"
        );
        System.out.println(
                "----------------------------------------------------------------------------------------"
        );
        for (BenchmarkResult result : results) {
            System.out.printf(
                    "%-20s %-10d %-10d %-12.2f %-12.2f %-18.2f%n",
                    result.getAlgorithm(),
                    result.getAccepted(),
                    result.getRejected(),
                    result.getAcceptanceRate(),
                    result.getRejectionRate(),
                    result.getProcessingEventsPerSecond()
            );
        }
        System.out.println(
                "----------------------------------------------------------------------------------------"
        );
    }

    //analysis

    static void printAnalysis(
            Configuration config,
            List<BenchmarkResult> results) {
        if (results.isEmpty()) {
            return;
        }
        System.out.println();
        System.out.println(
                "Experiment Analysis"
        );
        System.out.println(
                "--------------------------------------------------------------"
        );
        System.out.println(
                "Traffic pattern: "
                        + config.trafficPattern
        );
        if (config.trafficPattern
                == TrafficPattern.NORMAL) {
            System.out.println(
                    "Traffic is below the configured rate limit."
            );
            System.out.println(
                    "Most requests are expected to be accepted."
            );
        } else if (
                config.trafficPattern
                        == TrafficPattern.OVERLOAD) {
            System.out.println(
                    "Traffic continuously exceeds the configured limit."
            );
            System.out.println(
                    "The experiment demonstrates rejection behaviour"
                            + " under sustained load."
            );
        } else if (
                config.trafficPattern
                        == TrafficPattern.BURST) {

            System.out.println(
                    "Traffic contains a short high-intensity burst."
            );

            System.out.println(
                    "This helps demonstrate how algorithms respond"
                            + " to sudden traffic spikes."
            );

        } else if (
                config.trafficPattern
                        == TrafficPattern.PERIODIC) {

            System.out.println(
                    "Traffic repeatedly alternates between low"
                            + " and high intensity."
            );

        } else {
            System.out.println(
                    "Traffic varies randomly around the configured"
                            + " base and peak rates."
            );
        }
        System.out.println();
        System.out.println(
                "Interpretation:"
        );
        System.out.println(
                "- Accepted requests represent traffic allowed"
                        + " by the limiter."
        );
        System.out.println(
                "- Rejected requests represent traffic suppressed"
                        + " by the limiter."
        );
        System.out.println(
                "- Processing Events/sec measures how quickly"
                        + " the Java simulator processed events."
        );
        System.out.println(
                "- It is NOT network/API throughput."
        );
    }

    static void learnMode() {
        while (true) {
            System.out.println();
            System.out.println(
                    "=============================================================="
            );
            System.out.println(
                    "                    RATEFORGE KNOWLEDGE"
            );
            System.out.println(
                    "=============================================================="
            );
            System.out.println(
                    "1. Fixed Window"
            );
            System.out.println(
                    "2. Sliding Window"
            );
            System.out.println(
                    "3. Token Bucket"
            );
            System.out.println(
                    "4. Leaky Bucket"
            );
            System.out.println(
                    "5. Back"
            );
            int choice =
                    readInt(
                            "Choice: ",
                            1,
                            5
                    );
            if (choice == 5) {
                return;
            }
            switch (choice) {
                case 1:
                    printFixedWindowInfo();
                    break;
                case 2:
                    printSlidingWindowInfo();
                    break;
                case 3:
                    printTokenBucketInfo();
                    break;
                case 4:
                    printLeakyBucketInfo();
                    break;
            }
            System.out.println();
            System.out.println(
                    "Press ENTER to continue..."
            );

            scanner.nextLine();
        }
    }

    //fixed window infoo

    static void printFixedWindowInfo() {

        System.out.println();
        System.out.println(
                "FIXED WINDOW"
        );
        System.out.println(
                "--------------------------------------------------"
        );
        System.out.println(
                "Requests are counted inside fixed time intervals."
        );
        System.out.println(
                "Example: 100 requests every 1 second."
        );
        System.out.println(
                "Once the count reaches the limit, additional"
                        + " requests are rejected until the next window."
        );
        System.out.println(
                "Advantages:"
        );
        System.out.println(
                "- Simple"
        );
        System.out.println(
                "- Low memory usage"
        );
        System.out.println(
                "Limitation:"
        );
        System.out.println(
                "- Requests can cluster around window boundaries."
        );
        System.out.println(
                "Time complexity per request: O(1)"
        );
        System.out.println(
                "Space complexity: O(1)"
        );
    }

    // sliding window info

    static void printSlidingWindowInfo() {
        System.out.println();
        System.out.println(
                "SLIDING WINDOW"
        );
        System.out.println(
                "--------------------------------------------------"
        );
        System.out.println(
                "Maintains timestamps of recent requests."
        );
        System.out.println(
                "Expired timestamps are removed as time advances."
        );
        System.out.println(
                "This provides a rolling view of recent traffic."
        );
        System.out.println(
                "Advantages:"
        );
        System.out.println(
                "- More precise than fixed windows"
        );
        System.out.println(
                "- Avoids fixed-window boundary effects"
        );
        System.out.println(
                "Limitation:"
        );
        System.out.println(
                "- Requires memory proportional to stored requests."
        );
        System.out.println(
                "Typical time complexity per request: O(1) amortized"
        );
        System.out.println(
                "Space complexity: O(limit)"
        );
    }

    // token bucket info

    static void printTokenBucketInfo() {
        System.out.println();
        System.out.println(
                "TOKEN BUCKET"
        );
        System.out.println(
                "--------------------------------------------------"
        );
        System.out.println(
                "Tokens are added to a bucket at a fixed rate."
        );
        System.out.println(
                "Each accepted request consumes one token."
        );
        System.out.println(
                "The bucket has a maximum capacity."
        );
        System.out.println(
                "This allows controlled bursts while maintaining"
                        + " a long-term request rate."
        );
        System.out.println(
                "Advantages:"
        );
        System.out.println(
                "- Supports bursts"
        );
        System.out.println(
                "- Widely applicable to API throttling"
        );
        System.out.println(
                "Time complexity per request: O(1)"
        );
        System.out.println(
                "Space complexity: O(1)"
        );
    }

    // leaky bucket info

    static void printLeakyBucketInfo() {
        System.out.println();
        System.out.println(
                "LEAKY BUCKET"
        );
        System.out.println(
                "--------------------------------------------------"
        );
        System.out.println(
                "Requests enter a bucket with limited capacity."
        );
        System.out.println(
                "The bucket drains at a fixed rate."
        );
        System.out.println(
                "When the bucket is full, additional requests"
                        + " are rejected."
        );
        System.out.println(
                "This model is useful for smoothing traffic."
        );
        System.out.println(
                "Advantages:"
        );
        System.out.println(
                "- Smooths traffic"
        );
        System.out.println(
                "- Fixed processing rate"
        );
        System.out.println(
                "Limitation:"
        );
        System.out.println(
                "- Can reject requests when the bucket is full."
        );
        System.out.println(
                "Time complexity per request: O(1)"
        );
        System.out.println(
                "Space complexity: O(1)"
        );
    }

    // csv export

    static void exportCSV(
            Configuration config) {
        if (config.lastResults.isEmpty()) {
            System.out.println();
            System.out.println(
                    "No experiment results available."
            );
            return;
        }
        String filename =
                "rateforge_results.csv";
        try (
                FileWriter writer =
                        new FileWriter(filename)
        ) {
            writer.append(
                    "Algorithm,Total Requests,Accepted,"
                            + "Rejected,Acceptance Rate,"
                            + "Rejection Rate,"
                            + "Processing Time (ns),"
                            + "Processing Events/sec\n"
            );
            for (BenchmarkResult result :
                    config.lastResults) {
                writer.append(
                        result.getAlgorithm()
                ).append(",");

                writer.append(
                        String.valueOf(
                                result.getTotalRequests()
                        )
                ).append(",");

                writer.append(
                        String.valueOf(
                                result.getAccepted()
                        )
                ).append(",");

                writer.append(
                        String.valueOf(
                                result.getRejected()
                        )
                ).append(",");

                writer.append(
                        String.format(
                                Locale.US,
                                "%.2f",
                                result.getAcceptanceRate()
                        )
                ).append(",");

                writer.append(
                        String.format(
                                Locale.US,
                                "%.2f",
                                result.getRejectionRate()
                        )
                ).append(",");

                writer.append(
                        String.valueOf(
                                result.getProcessingTimeNanos()
                        )
                ).append(",");

                writer.append(
                        String.format(
                                Locale.US,
                                "%.2f",
                                result.getProcessingEventsPerSecond()
                        )
                ).append("\n");
            }

            System.out.println();
            System.out.println(
                    "Results exported successfully:"
            );

            System.out.println(
                    "  " + filename
            );

        } catch (IOException e) {

            System.out.println(
                    "Unable to export results: "
                            + e.getMessage()
            );
        }
    }

    // input helper

    static int readInt(
            String message,
            int min,
            int max) {
        while (true) {
            System.out.print(message);
            String input =
                    scanner.nextLine().trim();
            try {
                int value =
                        Integer.parseInt(input);
                if (value >= min
                        && value <= max) {
                    return value;
                }
                System.out.println(
                        "Enter a value between "
                                + min
                                + " and "
                                + max
                                + "."
                );
            } catch (NumberFormatException e) {
                System.out.println(
                        "Please enter a valid integer."
                );
            }
        }
    }
    static int readPositiveInt(
            String message,
            int defaultValue) {
        while (true) {
            System.out.print(message);
            String input =
                    scanner.nextLine().trim();
            if (input.isEmpty()) {
                return defaultValue;
            }
            try {
                int value =
                        Integer.parseInt(input);
                if (value > 0) {
                    return value;
                }
                System.out.println(
                        "Value must be greater than zero."
                );
            } catch (NumberFormatException e) {
                System.out.println(
                        "Please enter a valid integer."
                );
            }
        }
    }

    //demo mode

    static void runDemo(
            Configuration config) {
        System.out.println();
        System.out.println(
                "=============================================================="
        );
        System.out.println(
                "                     RATEFORGE DEMO"
        );
        System.out.println(
                "=============================================================="
        );
        System.out.println(
                "Scenario: 100 requests/sec limit"
        );
        System.out.println(
                "Normal traffic: 20 requests/sec"
        );
        System.out.println(
                "Burst traffic: 500 requests/sec"
        );
        System.out.println(
                "Duration: 10 seconds"
        );
        config.rateLimit = 100;
        config.durationSeconds = 10;
        config.baseRate = 20;
        config.peakRate = 500;
        config.trafficPattern =
                TrafficPattern.BURST;
        List<SimulatedRequest> requests =
                generateTraffic(config);
        List<RateLimiter> limiters =
                createAllLimiters(
                        config.rateLimit,
                        DEFAULT_WINDOW_MS
                );
        BenchmarkEngine engine =
                new BenchmarkEngine();
        config.lastResults =
                new ArrayList<>();
        printExperimentHeader(
                config,
                requests
        );
        for (RateLimiter limiter : limiters) {
            System.out.println(
                    "Testing " + limiter.getName()
            );
            BenchmarkResult result =
                    engine.run(
                            limiter,
                            requests
                    );
            config.lastResults.add(
                    result
            );
        }
        printResults(
                config.lastResults
        );
        printAnalysis(
                config,
                config.lastResults
        );
    }

    // cli mode
    static void commandLineMode(
            String[] args,
            Configuration config) {
        String algorithm = "all";
        int limit = 100;
        int duration = 10;
        int baseRate = 50;
        int peakRate = 500;
        TrafficPattern pattern =
                TrafficPattern.NORMAL;
        boolean demo = false;
        boolean live = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help":
                    printHelp();
                    return;
                case "--demo":
                    demo = true;
                    break;
                case "--live":
                    live = true;
                    break;
                case "--algorithm":
                    algorithm =
                            requireValue(
                                    args,
                                    ++i,
                                    "--algorithm"
                            );
                    break;
                case "--limit":
                    limit =
                            Integer.parseInt(
                                    requireValue(
                                            args,
                                            ++i,
                                            "--limit"
                                    )
                            );

                    break;
                case "--duration":
                    duration =
                            Integer.parseInt(
                                    requireValue(
                                            args,
                                            ++i,
                                            "--duration"
                                    )
                            );

                    break;
                case "--base-rate":
                    baseRate =
                            Integer.parseInt(
                                    requireValue(
                                            args,
                                            ++i,
                                            "--base-rate"
                                    )
                            );

                    break;
                case "--peak-rate":
                    peakRate =
                            Integer.parseInt(
                                    requireValue(
                                            args,
                                            ++i,
                                            "--peak-rate"
                                    )
                            );

                    break;
                case "--traffic":
                    String traffic =
                            requireValue(
                                    args,
                                    ++i,
                                    "--traffic"
                            );
                    pattern =
                            parseTrafficPattern(
                                    traffic
                            );
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unknown argument: "
                                    + arg
                    );
            }
        }
        if (demo) {
            runDemo(config);
            return;
        }
        if (limit <= 0
                || duration <= 0
                || baseRate <= 0
                || peakRate <= 0) {
            throw new IllegalArgumentException(
                    "All numeric parameters must be positive."
            );
        }
        config.rateLimit = limit;
        config.durationSeconds = duration;
        config.baseRate = baseRate;
        config.peakRate = peakRate;
        config.trafficPattern = pattern;
        List<SimulatedRequest> requests =
                generateTraffic(config);
        BenchmarkEngine engine =
                new BenchmarkEngine();
        config.lastResults =
                new ArrayList<>();
        if (live) {
            RateLimiter limiter;
            if (algorithm.equalsIgnoreCase("all")) {
                limiter =
                        createLimiter(
                                "token",
                                limit,
                                DEFAULT_WINDOW_MS
                        );
                System.out.println(
                        "Live mode defaults to Token Bucket"
                );
            } else {
                limiter =
                        createLimiter(
                                algorithm,
                                limit,
                                DEFAULT_WINDOW_MS
                        );
            }
            engine.liveSimulation(
                    limiter,
                    requests
            );
            return;
        }
        if (algorithm.equalsIgnoreCase("all")) {
            List<RateLimiter> limiters =
                    createAllLimiters(
                            limit,
                            DEFAULT_WINDOW_MS
                    );
            for (RateLimiter limiter :
                    limiters) {
                config.lastResults.add(
                        engine.run(
                                limiter,
                                requests
                        )
                );
            }
        } else {
            RateLimiter limiter =
                    createLimiter(
                            algorithm,
                            limit,
                            DEFAULT_WINDOW_MS
                    );
            config.lastResults.add(
                    engine.run(
                            limiter,
                            requests
                    )
            );
        }
        printExperimentHeader(
                config,
                requests
        );
        printResults(
                config.lastResults
        );
        printAnalysis(
                config,
                config.lastResults
        );
    }

    // cli value

    static String requireValue(
            String[] args,
            int index,
            String argument) {
        if (index >= args.length) {
            throw new IllegalArgumentException(
                    "Missing value for "
                            + argument
            );
        }
        return args[index];
    }

    // traffic parsing

    static TrafficPattern parseTrafficPattern(
            String value) {
        switch (value.toLowerCase()) {
            case "normal":
                return TrafficPattern.NORMAL;
            case "overload":
                return TrafficPattern.OVERLOAD;
            case "burst":
                return TrafficPattern.BURST;
            case "periodic":
                return TrafficPattern.PERIODIC;
            case "random":
                return TrafficPattern.RANDOM;
            default:
                throw new IllegalArgumentException(
                        "Unknown traffic pattern: "
                                + value
                );
        }
    }

    // help

    static void printHelp() {
        System.out.println();
        System.out.println(
                "=============================================================="
        );
        System.out.println(
                "                         RATEFORGE"
        );
        System.out.println(
                "=============================================================="
        );
        System.out.println(
                "Rate Limiting Experimentation Tool"
        );
        System.out.println();
        System.out.println(
                "Compile:"
        );
        System.out.println(
                "  javac RateForge.java"
        );
        System.out.println();
        System.out.println(
                "Run interactive mode:"
        );
        System.out.println(
                "  java RateForge"
        );
        System.out.println();
        System.out.println(
                "Run demo:"
        );
        System.out.println(
                "  java RateForge --demo"
        );
        System.out.println();
        System.out.println(
                "Run custom CLI experiment:"
        );
        System.out.println(
                "  java RateForge "
                        + "--algorithm token "
                        + "--limit 100 "
                        + "--duration 10 "
                        + "--base-rate 50 "
                        + "--peak-rate 500 "
                        + "--traffic burst"
        );
        System.out.println();
        System.out.println(
                "Algorithms:"
        );
        System.out.println(
                "  fixed"
        );
        System.out.println(
                "  sliding"
        );
        System.out.println(
                "  token"
        );
        System.out.println(
                "  leaky"
        );
        System.out.println(
                "  all"
        );
        System.out.println();
        System.out.println(
                "Traffic:"
        );
        System.out.println(
                "  normal"
        );
        System.out.println(
                "  overload"
        );
        System.out.println(
                "  burst"
        );
        System.out.println(
                "  periodic"
        );
        System.out.println(
                "  random"
        );
        System.out.println();
        System.out.println(
                "Live simulation:"
        );
        System.out.println(
                "  java RateForge --live "
                        + "--algorithm token "
                        + "--limit 10 "
                        + "--duration 2 "
                        + "--base-rate 20 "
                        + "--peak-rate 100 "
                        + "--traffic burst"
        );
    }

    // main

    public static void main(
            String[] args) {
        Configuration config =
                new Configuration();
        try {

            // cli mode
            if (args.length > 0) {
                commandLineMode(
                        args,
                        config
                );
                return;
            }

            // interactive
            while (true) {
                showMainMenu();
                String input =
                        scanner.nextLine().trim();
                int choice;
                try {
                    choice =
                            Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    System.out.println(
                            "Please enter a valid menu option."
                    );
                    continue;
                }
                switch (choice) {
                    case 1:
                        runSingleAlgorithm(
                                config
                        );
                        break;
                    case 2:
                        compareAllAlgorithms(
                                config
                        );
                        break;
                    case 3:
                        liveSimulation(
                                config
                        );
                        break;
                    case 4:
                        runPreset(
                                config
                        );
                        break;
                    case 5:
                        learnMode();
                        break;
                    case 6:
                        exportCSV(
                                config
                        );
                        break;
                    case 7:
                        System.out.println();
                        System.out.println(
                                "Thank you for using RateForge."
                        );
                        return;
                    default:
                        System.out.println(
                                "Choose an option from 1 to 7."
                        );
                }
            }
        } catch (NumberFormatException e) {
            System.out.println();
            System.out.println(
                    "Invalid numeric value."
            );
        } catch (IllegalArgumentException e) {
            System.out.println();
            System.out.println(
                    "Configuration error: "
                            + e.getMessage()
            );
        } catch (Exception e) {
            System.out.println();
            System.out.println(
                    "Unexpected error: "
                            + e.getMessage()
            );
        }
    }
}