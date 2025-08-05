package com.example.demo.application.util;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadUtil {

    // Basic Thread example for multiple tasks
    public static void runWithBasicThreads(Runnable... tasks) {
        for (Runnable task : tasks) {
            Thread thread = new Thread(task);
            thread.start();
        }
    }

    // Concurrency example for multiple tasks
    public static void runWithFixedThreadPool(Runnable... tasks) {
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

    // Parallelism example for multiple tasks
    public static void runWithForkJoinPool(Runnable... tasks) {
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

    /**
     * 1. CPU-intensive tasks (e.g., computation, compression, image processing)
     * 2. Low concurrency but requires fast response
     * 3. Needs to support legacy systems (using ThreadLocal)
     */
    public static void runWithCachedThreads(Runnable... tasks) {
        ExecutorService executor = Executors.newCachedThreadPool();
        for (Runnable task : tasks) {
            executor.submit(() -> {
                task.run();
            });
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

    /**
     * 1. High-concurrency I/O tasks (e.g., HTTP API, DB, MQ)
     * 2. No need for ThreadLocal and requires massive concurrency
     */
    public static void runWithVirtualThread(Runnable... tasks) {
        // Executor service that manages virtual threads
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

        // Submit the tasks to the executor which will run each task on a virtual thread
        for (Runnable task : tasks) {
            executorService.submit(task);
        }

        // Optional: Shut down the executor service after task completion
        executorService.shutdown();
    }

    /**
     * 1. High-concurrency I/O tasks (e.g., HTTP API, DB, MQ)
     * 2. No need for ThreadLocal and requires massive concurrency
     */
    public static void runWithThreadOfVirtual(Runnable... tasks) {
        for (Runnable task : tasks) {
            // Create and start a virtual thread using Thread.ofVirtual()
            Thread virtualThread = Thread.ofVirtual().start(task);

            try {
                virtualThread.join(); // This blocks the main thread until the virtual thread finishes its execution.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Synchronization example for multiple tasks
    public static void runWithSynchronized(List<String> lockAttemptLogs, Runnable... tasks) {
        boolean[] skippedTasks = new boolean[tasks.length];
        Arrays.fill(skippedTasks, false);

        for (int i = 0; i < tasks.length; i++) {
            Runnable task = tasks[i];
            String threadName = Thread.currentThread().getName();

            // First, check if the task was already skipped by another thread
            if (skippedTasks[i]) {
                lockAttemptLogs.add("Task " + (i + 1) + " is blocked, " + threadName);
                continue; // Skip the task if it was already executed or blocked
            }

            // Log execution of the task
            synchronized (task) {
                if (!skippedTasks[i]) {
                    lockAttemptLogs.add("Task " + (i + 1) + " executed, " + threadName);
                    task.run(); // Execute the task inside the synchronized block
                    skippedTasks[i] = true; // Mark the task as executed
                }
            }
        }
    }

    // ReentrantLock example for multiple tasks
    public static void runWithReentrantLock(ReentrantLock lock, Runnable... tasks) {
        try {
            if (lock.tryLock(1, TimeUnit.SECONDS)) { // 嘗試獲取鎖
                try {
                    for (Runnable task : tasks) {
                        task.run();
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                System.out.println(Thread.currentThread().getName() + " 獲取鎖失敗，無法執行任務");
            }
        } catch (Exception e) {

        }
    }

    // CompletableFuture example for multiple tasks
    public static CompletableFuture<Void> runWithCompletableFuture(Runnable... tasks) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (Runnable task : tasks) {
            future = future.thenRunAsync(task);
        }
        return future;
    }
}
