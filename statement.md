# RateForge - Project Statement

## 1. Problem Statement

Modern applications and APIs can receive a large number of requests
within a short period of time. If these requests are not controlled,
they can overload the system, reduce performance, or make the service
unavailable for other users.

Rate limiting is one way of controlling this traffic. Different
rate-limiting algorithms handle requests in different ways, especially
when traffic suddenly increases.

The problem addressed by RateForge is to provide a simple command-line
environment where different rate-limiting algorithms can be implemented,
tested, and compared under different traffic conditions.

The project focuses on understanding the behaviour of Fixed Window,
Sliding Window, Token Bucket, and Leaky Bucket algorithms through
simulation rather than using a real production API.

## 2. Scope of the Project

The project covers the implementation and simulation of four common
rate-limiting algorithms in Java.

The application allows users to configure a rate limit, simulation
duration, traffic rates, and traffic pattern. It then generates
simulated requests and passes them through one or more rate limiters.

The project includes:

- Implementation of four rate-limiting algorithms
- Simulated request generation
- Different traffic patterns
- Single-algorithm testing
- Comparison of all algorithms
- Live request-level simulation
- Predefined experiment scenarios
- Basic result analysis
- Processing performance measurement
- CSV result export
- Command-line interaction
- Input validation and error handling

The current version does not include a real HTTP server, database,
Redis, authentication, distributed rate limiting, or a web interface.
These can be considered for future versions.

## 3. Target Users

### Students

Students can use RateForge to understand rate-limiting algorithms,
data structures, and their practical behaviour through experiments.

### Developers

Developers can use the project as a small reference for understanding
how basic rate-limiting algorithms can be implemented in Java.

### Teachers / Evaluators

The application provides an easy way to demonstrate and evaluate
different algorithms, their complexity, and their behaviour under
different traffic conditions.

### Beginners in Backend Development

Users who are learning about APIs and backend systems can use the
simulation to understand why rate limiting is needed and how different
strategies respond to traffic.

## 4. High-Level Features

### Algorithm Implementation

Implements:

- Fixed Window
- Sliding Window
- Token Bucket
- Leaky Bucket

### Traffic Simulation

Generates requests based on configurable traffic rates and duration.

Supported patterns include:

- Normal traffic
- Sustained overload
- Sudden bursts
- Periodic spikes
- Random traffic

### Algorithm Comparison

Runs the same generated traffic through multiple algorithms and shows
accepted requests, rejected requests, and acceptance/rejection rates.

### Live Simulation

Displays individual request decisions along with the current state of
the selected rate limiter.

### Experiment Presets

Provides ready-made scenarios such as light traffic, sustained
overload, sudden burst, periodic spikes, and extreme overload.

### Learning Mode

Provides basic information about each algorithm, including its working,
advantages, limitations, and complexity.

### Result Export

Allows the latest experiment results to be exported to a CSV file for
further analysis.

### Command-Line Interface

The complete application can be compiled and executed from a terminal
without requiring a graphical interface.
