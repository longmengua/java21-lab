package com.example.demo.util;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ThreadUtilTest {

    // Test for testRunBasicThread method
    @Test
    void testRunBasicThread() throws InterruptedException {
        final boolean[] task1Executed = { false };
        final boolean[] task2Executed = { false };

        // Task 1 that modifies the array when executed
        Runnable task1 = () -> task1Executed[0] = true;

        // Task 2 that modifies the array when executed
        Runnable task2 = () -> task2Executed[0] = true;

        // Run the basic thread with both tasks
        ThreadUtil.runWithBasicThreads(task1, task2);

        // Wait for a moment to ensure threads have finished
        Thread.sleep(100);

        // Assert that both tasks were executed
        assertTrue(task1Executed[0], "Task 1 should have been executed.");
        assertTrue(task2Executed[0], "Task 2 should have been executed.");
    }

    // Test for testRunWithForkJoinPool method
    @Test
    void testRunWithForkJoinPool() throws InterruptedException {
        final boolean[] task1Executed = { false };
        final boolean[] task2Executed = { false };

        // Two simple tasks
        Runnable task1 = () -> task1Executed[0] = true;
        Runnable task2 = () -> task2Executed[0] = true;

        // Run concurrent tasks
        ThreadUtil.runWithForkJoinPool(task1, task2);

        // Wait for tasks to complete
        Thread.sleep(100);

        // Assert that both tasks were executed
        assertTrue(task1Executed[0], "Task 1 should have been executed.");
        assertTrue(task2Executed[0], "Task 2 should have been executed.");
    }

    // Test for runWithFixedThreadPool method
    @Test
    void testRunWithFixedThreadPool() throws InterruptedException {
        final boolean[] task1Executed = { false };
        final boolean[] task2Executed = { false };

        // Task 1 that modifies the array when executed
        Runnable task1 = () -> task1Executed[0] = true;

        // Task 2 that modifies the array when executed
        Runnable task2 = () -> task2Executed[0] = true;

        // Run with a thread pool of size 2
        ThreadUtil.runWithFixedThreadPool(task1, task2);

        // Wait for the task to complete
        Thread.sleep(100);

        // Assert that both tasks were executed
        assertTrue(task1Executed[0], "Task 1 should have been executed with thread pool.");
        assertTrue(task2Executed[0], "Task 2 should have been executed with thread pool.");
    }

    // Test for synchronizedMethod (should lock the method and execute the task)
    @Test
    void testRunWithSynchronized() throws InterruptedException {
        final boolean[] task1Executed = { false };
        final boolean[] task2Executed = { false };

        // Task 1
        Runnable task1 = () -> task1Executed[0] = true;

        // Task 2
        Runnable task2 = () -> task2Executed[0] = true;

        // Run the synchronized method
        ThreadUtil.runWithSynchronized(task1, task2);

        // Assert that both tasks were executed
        assertTrue(task1Executed[0], "Task 1 should have been executed under synchronization.");
        assertTrue(task2Executed[0], "Task 2 should have been executed under synchronization.");
    }

    // Test for runWithReentrantLock method
    @Test
    void testRunWithReentrantLock() throws InterruptedException {
        final boolean[] task1Executed = { false };
        final boolean[] task2Executed = { false };

        // Task 1
        Runnable task1 = () -> task1Executed[0] = true;

        // Task 2
        Runnable task2 = () -> task2Executed[0] = true;

        // Run with ReentrantLock
        ThreadUtil.runWithReentrantLock(task1, task2);

        // Assert that both tasks were executed
        assertTrue(task1Executed[0], "Task 1 should have been executed with ReentrantLock.");
        assertTrue(task2Executed[0], "Task 2 should have been executed with ReentrantLock.");
    }

    // Test for runWithCompletableFuture method
    @Test
    void testRunWithCompletableFuture() throws ExecutionException, InterruptedException {
        final boolean[] task1Executed = { false };
        final boolean[] task2Executed = { false };

        // Task 1
        Runnable task1 = () -> task1Executed[0] = true;

        // Task 2
        Runnable task2 = () -> task2Executed[0] = true;

        // Run the tasks asynchronously using CompletableFuture
        CompletableFuture<Void> future = ThreadUtil.runWithCompletableFuture(task1, task2);

        // Wait for the task to complete
        future.get(); // This will block until the tasks complete

        // Assert that both tasks were executed
        assertTrue(task1Executed[0], "Task 1 should have been executed with CompletableFuture.");
        assertTrue(task2Executed[0], "Task 2 should have been executed with CompletableFuture.");
    }

    // Test for runWithVirtualThread method
    @Test
    void testRunWithVirtualThread() throws InterruptedException {
        final boolean[] task1Executed = { false };
        final boolean[] task2Executed = { false };

        // Task 1
        Runnable task1 = () -> task1Executed[0] = true;

        // Task 2
        Runnable task2 = () -> task2Executed[0] = true;

        // Run the tasks using virtual threads
        ThreadUtil.runWithVirtualThread(task1, task2);

        // Wait for the tasks to complete
        Thread.sleep(100);

        // Assert that both tasks were executed
        assertTrue(task1Executed[0], "Task 1 should have been executed with virtual thread.");
        assertTrue(task2Executed[0], "Task 2 should have been executed with virtual thread.");
    }

    // Test for runWithVirtualThreadV2 method
    @Test
    void testRunWithVirtualThreadV2() throws InterruptedException {
        final boolean[] task1Executed = { false };
        final boolean[] task2Executed = { false };

        // Task 1
        Runnable task1 = () -> task1Executed[0] = true;

        // Task 2
        Runnable task2 = () -> task2Executed[0] = true;

        // Run the tasks using virtual thread (v2)
        ThreadUtil.runWithVirtualThreadV2(task1, task2);

        // Assert that both tasks were executed
        assertTrue(task1Executed[0], "Task 1 should have been executed with virtual thread V2.");
        assertTrue(task2Executed[0], "Task 2 should have been executed with virtual thread V2.");
    }
}
