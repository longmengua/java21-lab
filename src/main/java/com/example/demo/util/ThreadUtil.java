package com.example.demo.util;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadUtil {

    // Basic Thread example
    public static void runBasicThread(Runnable task) {
        Thread thread = new Thread(task);
        thread.start();
    }

    // Concurrency example
    public static void runConcurrentTasks(Runnable... tasks) {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        for (Runnable task : tasks) {
            executor.submit(task);
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Parallelism example
    public static void runParallelTasks(Runnable... tasks) {
        try (ForkJoinPool forkJoinPool = new ForkJoinPool()) {
            for (Runnable task : tasks) {
                forkJoinPool.submit(task);
            }
            forkJoinPool.shutdown();
            try {
                if (!forkJoinPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    forkJoinPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                forkJoinPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // Thread Pool example
    public static void runWithThreadPool(Runnable task, int poolSize) {
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        executor.submit(task);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 1. CPU-intensive tasks (e.g., computation, compression, image processing)
     * 2. Low concurrency but requires fast response
     * 3. Needs to support legacy systems (using ThreadLocal)
     */
    public static void runWithCachedThread(Runnable task) {
        ExecutorService executor = Executors.newCachedThreadPool();
        executor.submit(() -> {
            task.run();
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Synchronization example
    public static void synchronizedMethod(Runnable task) {
        synchronized (ThreadUtil.class) {
            task.run();
        }
    }

    // ReentrantLock example
    public static void runWithReentrantLock(Runnable task) {
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try {
            task.run();
        } finally {
            lock.unlock();
        }
    }

    // CompletableFuture example
    public static CompletableFuture<Void> runWithCompletableFuture(Runnable task) {
        return CompletableFuture.runAsync(task);
    }

    /**
     * 1. High-concurrency I/O tasks (e.g., HTTP API, DB, MQ)
     * 2. No need for ThreadLocal and requires massive concurrency
     */
    public static void runWithVirtualThread(Runnable task) {
        // Executor service that manages virtual threads
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

        // Submit the task to the executor which will run the task on a virtual thread
        executorService.submit(task);

        // Optional: Shut down the executor service after task completion
        executorService.shutdown();
    }

    /**
     * 1. High-concurrency I/O tasks (e.g., HTTP API, DB, MQ)
     * 2. No need for ThreadLocal and requires massive concurrency
     */
    public static void runWithVirtualThreadV2(Runnable task) {
        // Create and start a virtual thread using Thread.ofVirtual()
        Thread virtualThread = Thread.ofVirtual().start(task);

        try {
            virtualThread.join(); // This blocks the main thread until the virtual thread finishes its execution.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
