# RateForge V2.0
**Rate Limiting Experimentation & Analysis Tool**
RateForge is a Java command-line project for implementing, simulating, and comparing common rate-limiting algorithms.
The program generates simulated traffic and shows how different algorithms handle different traffic conditions.
## Features
- Interactive CLI
- Fixed Window, Sliding Window, Token Bucket and Leaky Bucket
- Custom traffic experiments
- Normal, overload, burst, periodic and random traffic
- Compare all algorithms using the same traffic
- Live request simulation
- Experiment presets
- Learning mode
- CSV export
- Input validation and error handling
- Command-line arguments
## Algorithms
### Fixed Window
Requests are counted inside fixed time windows. With a limit of 100 requests/sec, the first 100 requests in a one-second window are accepted.
**Time:** O(1) per request  
**Space:** O(1)
### Sliding Window
Recent request timestamps are stored and expired timestamps are removed as time moves forward. This gives a rolling view of traffic.
**Time:** O(1) amortized per request  
**Space:** O(limit)
### Token Bucket
Tokens are added to a bucket at a fixed rate. Each accepted request uses one token. The bucket capacity allows controlled bursts.
**Time:** O(1) per request  
**Space:** O(1)
### Leaky Bucket
Requests increase a limited bucket while it drains at a fixed rate. Requests are rejected when the bucket is full.
**Time:** O(1) per request  
**Space:** O(1)
> V2.0 uses a simplified bucket-occupancy model for Leaky Bucket.
## Traffic Simulation
Traffic is generated from configurable values rather than a hardcoded request list.
```text
Rate Limit       : 100 requests/sec
Duration         : 10 seconds
Base Traffic     : 20 requests/sec
Peak Traffic     : 500 requests/sec
Pattern          : BURST
```
The same generated request stream can be tested against all algorithms.
## Traffic Patterns
| Pattern | Description |
|---|---|
| Normal | Steady traffic around the base rate |
| Overload | Traffic continuously above the limit |
| Burst | Normal traffic with a temporary spike |
| Periodic | Repeated low and high traffic |
| Random | Traffic varying between configured rates |
## Main Menu
Running `java RateForge` opens:
```text
1. Run a single algorithm
2. Compare all algorithms
3. Live request simulation
4. Run experiment preset
5. Learn about algorithms
6. Export last results to CSV
7. Exit
```
### Single Algorithm
Select one algorithm and configure a custom experiment.
### Compare All Algorithms
Runs the same request stream through all four algorithms, making the results easier to compare.
### Live Simulation
Shows individual request decisions and the current algorithm state.
```text
Request  Time(ms)     Decision
-----------------------------------
1        0             ALLOWED
2        2             ALLOWED
3        4             REJECTED
```
The live display shows up to the first 100 requests.
### Experiment Presets
```text
1. Light Traffic
2. Sustained Overload
3. Sudden Burst
4. Periodic Spikes
5. Extreme Overload
6. Custom
```
Presets make it easy to demonstrate common traffic situations.
### Learning Mode
Provides a short explanation of each algorithm, including its working, advantages, limitations and complexity.
### CSV Export
The latest experiment can be exported as `rateforge_results.csv`.
## Requirements
- Java JDK 8 or newer
- Terminal / command prompt
- No external libraries
Check Java:
```bash
java -version
javac -version
```
## Installation
Clone the repository:
```bash
git clone https://github.com/YOUR-USERNAME/RateForge.git
cd RateForge
```
Compile and run:
```bash
javac RateForge.java
java RateForge
```
## Command-Line Usage
Show help:
```bash
java RateForge --help
```
Run the demo:
```bash
java RateForge --demo
```
Run a Token Bucket experiment:
```bash
java RateForge --algorithm token --limit 100 --duration 10 --base-rate 50 --peak-rate 500 --traffic burst
```
Compare all algorithms:
```bash
java RateForge --algorithm all --limit 100 --duration 10 --base-rate 20 --peak-rate 500 --traffic burst
```
Run live simulation:
```bash
java RateForge --live --algorithm token --limit 10 --duration 2 --base-rate 20 --peak-rate 100 --traffic burst
```
## Example Result
A burst experiment can produce results similar to:
```text
Algorithm            Accepted   Rejected   Accept %
----------------------------------------------------
Fixed Window         360        313        53.49
Sliding Window       280        393        41.60
Token Bucket         378        295        56.17
Leaky Bucket         379        294        56.32
```
Exact values depend on the generated traffic. The purpose is to observe behaviour under the same workload.
## Project Structure
V2.0 is currently kept in one Java file:
```text
RateForge.java
├── RateLimiter
├── FixedWindowLimiter
├── SlidingWindowLimiter
├── TokenBucketLimiter
├── LeakyBucketLimiter
├── TrafficPattern
├── SimulatedRequest
├── TrafficGenerator
├── BenchmarkResult
├── BenchmarkEngine
├── Configuration
└── CLI / application logic
```
The project demonstrates interfaces, classes, enums, collections, encapsulation, polymorphism and exception handling.
## Performance
RateForge measures how quickly the Java simulator processes generated request events.
The metric is **Processing Events/sec**. It is not HTTP throughput because V2.0 does not send real network requests.
## Error Handling
The program validates input and handles invalid menu choices, numeric values, algorithms, traffic patterns, missing command-line values and CSV export errors.
## Limitations
V2.0 is a simulation tool rather than a production API rate limiter.
It does not include real HTTP requests, Redis/database storage, distributed limiting, authentication, a web dashboard or network benchmarking.
## Future Scope
Possible future additions:
- Per-user rate limiting
- Multiple simulated users
- Concurrent request simulation
- Real HTTP API support
- Redis-based limiting
- Web dashboard
- More traffic models
- JUnit tests
- Separate Java files and packages
## Project Goal
The goal of RateForge is to understand rate limiting by implementing the algorithms and testing them under different traffic conditions.
It combines Java, data structures, algorithms and basic software engineering concepts into one practical project.
## Author
Developed as a Java course project.
**RateForge V2.0**
