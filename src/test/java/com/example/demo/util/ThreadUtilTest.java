package com.example.demo.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class ThreadUtilTest {

    // @Test
    // void testRunWithBasicThreads() throws InterruptedException {
    // AtomicInteger counter = new AtomicInteger(0);
    // Runnable task = () -> counter.addAndGet(2);

    // ThreadUtil.runWithBasicThreads(task, task, task);
    // Thread.sleep(500); // Wait for threads to finish
    // assertEquals(6, counter.get());
    // }

    // @Test
    // void testRunWithFixedThreadPool() {
    // AtomicInteger counter = new AtomicInteger(0);
    // Runnable task = () -> {
    // System.out.println("Task executed by: " + Thread.currentThread().getName());
    // counter.addAndGet(2);
    // };

    // ThreadUtil.runWithFixedThreadPool(task, task, task);
    // assertEquals(6, counter.get());
    // }

    @Test
    void testRunWithForkJoinPoolv1() {
        long startTime = System.currentTimeMillis(); // Capture start time
        int n = 30;
        try (ForkJoinPool forkJoinPool = new ForkJoinPool()) {
            FibonacciTask task = new FibonacciTask(n);
            Future<Long> result = forkJoinPool.submit(task);

            assertEquals(832040, result.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            fail("Test failed due to exception.");
        }
        long endTime = System.currentTimeMillis(); // Capture end time
        System.out.println("Test execution time: " + (endTime - startTime) + "milliseconds");

        startTime = System.currentTimeMillis(); // Capture start time
        long result = FibonacciTask.ComputeFibonacciV2(30);
        assertEquals(832040, result);
        endTime = System.currentTimeMillis(); // Capture end time
        System.out.println("Test execution time: " + (endTime - startTime) + "milliseconds");
    }

    // @Test
    // void testRunWithCachedThreads() {
    // AtomicInteger counter = new AtomicInteger(0);
    // Runnable task = () -> {
    // System.out.println("Task executed by: " + Thread.currentThread().getName());
    // counter.addAndGet(2);
    // };

    // ThreadUtil.runWithCachedThreads(task, task, task);
    // assertEquals(6, counter.get());
    // }

    // @Test
    // void testRunWithVirtualThread() {
    // AtomicInteger counter = new AtomicInteger(0);
    // Runnable task = counter::incrementAndGet;

    // ThreadUtil.runWithVirtualThread(task, task, task);
    // assertEquals(3, counter.get());
    // }

    // @Test
    // void testRunWithThreadOfVirtual() {
    // AtomicInteger counter = new AtomicInteger(0);
    // Runnable task = counter::incrementAndGet;

    // ThreadUtil.runWithThreadOfVirtual(task, task, task);
    // assertEquals(3, counter.get());
    // }

    // @Test
    // void testRunWithSynchronized() {
    // List<String> log = new ArrayList<>();
    // AtomicInteger counter = new AtomicInteger(0);
    // Runnable task = counter::incrementAndGet;

    // ThreadUtil.runWithSynchronized(log, task, task, task);
    // assertEquals(3, counter.get());
    // assertFalse(log.isEmpty());
    // }

    // @Test
    // void testRunWithReentrantLock() {
    // ReentrantLock lock = new ReentrantLock();
    // AtomicInteger counter = new AtomicInteger(0);
    // Runnable task = counter::incrementAndGet;

    // ThreadUtil.runWithReentrantLock(lock, task, task, task);
    // assertEquals(3, counter.get());
    // }

    // @Test
    // void testRunWithCompletableFuture() {
    // AtomicInteger counter = new AtomicInteger(0);
    // Runnable task = counter::incrementAndGet;

    // CompletableFuture<Void> future = ThreadUtil.runWithCompletableFuture(task,
    // task, task);
    // future.join();
    // assertEquals(3, counter.get());
    // }
}
