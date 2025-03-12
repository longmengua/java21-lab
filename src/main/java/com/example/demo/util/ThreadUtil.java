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
        ForkJoinPool forkJoinPool = new ForkJoinPool();
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

    // Virtual Threads example (Java 19)
    // public static void runWithVirtualThread(Runnable task) {
    // Thread.ofVirtual().start(task);
    // }

    /**
     * Executes a given task using a cached thread pool, simulating a virtual thread
     * in Java 17.
     * 
     * <p>
     * Note: Java 17 does not support true virtual threads natively.
     * This implementation uses a cached thread pool to simulate the behavior.
     * </p>
     *
     * @param task The runnable task to be executed.
     */
    public static void runWithVirtualThread(Runnable task) {
        ExecutorService executor = Executors.newCachedThreadPool();
        executor.submit(() -> {
            System.out.println("Simulating Virtual Thread in Java 17");
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
}
