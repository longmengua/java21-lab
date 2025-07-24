package com.example.demo.util;

import java.util.concurrent.RecursiveTask;

public class FibonacciTask extends RecursiveTask<Long> {
    private final int n;

    public FibonacciTask(int n) {
        this.n = n;
    }

    @Override
    protected Long compute() {
        if (n <= 1) {
            return (long) n;
        }

        FibonacciTask task1 = new FibonacciTask(n - 1);
        FibonacciTask task2 = new FibonacciTask(n - 2);
        task1.fork();
        task2.fork();

        return task1.join() + task2.join();
    }

    public static long ComputeFibonacciV2(int n) {
        if (n <= 1) {
            return n; // Base case: Fibonacci(0) = 0, Fibonacci(1) = 1
        }
        return ComputeFibonacciV2(n - 1) + ComputeFibonacciV2(n - 2); // Recursive step
    }
}
