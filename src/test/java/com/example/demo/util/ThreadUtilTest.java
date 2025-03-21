package com.example.demo.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ThreadUtilTest {

    // Test for runBasicThread method
    @Test
    void testRunBasicThread() throws InterruptedException {
        final boolean[] taskExecuted = { false };

        // Task that modifies the array when executed
        Runnable task = () -> taskExecuted[0] = true;

        // Run the basic thread
        ThreadUtil.runBasicThread(task);

        // Wait for a moment to ensure thread has finished
        Thread.sleep(100);

        // Assert that the task was executed
        assertTrue(taskExecuted[0], "Task should have been executed.");
    }

    // Test for runConcurrentTasks method
    @Test
    void testRunConcurrentTasks() throws InterruptedException {
        final boolean[] task1Executed = { false };
        final boolean[] task2Executed = { false };

        // Two simple tasks
        Runnable task1 = () -> task1Executed[0] = true;
        Runnable task2 = () -> task2Executed[0] = true;

        // Run concurrent tasks
        ThreadUtil.runConcurrentTasks(task1, task2);

        // Wait for tasks to complete
        Thread.sleep(100);

        // Assert that both tasks were executed
        assertTrue(task1Executed[0], "Task 1 should have been executed.");
        assertTrue(task2Executed[0], "Task 2 should have been executed.");
    }

    // Test for runWithThreadPool method
    @Test
    void testRunWithThreadPool() throws InterruptedException {
        final boolean[] taskExecuted = { false };

        // Task that modifies the array when executed
        Runnable task = () -> taskExecuted[0] = true;

        // Run with a thread pool of size 1
        ThreadUtil.runWithThreadPool(task, 1);

        // Wait for the task to complete
        Thread.sleep(100);

        // Assert that the task was executed
        assertTrue(taskExecuted[0], "Task should have been executed with thread pool.");
    }

    // Test for synchronizedMethod (should lock the method and execute the task)
    @Test
    void testSynchronizedMethod() throws InterruptedException {
        final boolean[] taskExecuted = { false };

        Runnable task = () -> taskExecuted[0] = true;

        // Run the synchronized method
        ThreadUtil.synchronizedMethod(task);

        // Assert that the task was executed
        assertTrue(taskExecuted[0], "Task should have been executed under synchronization.");
    }

    // Test for runWithReentrantLock method
    @Test
    void testRunWithReentrantLock() throws InterruptedException {
        final boolean[] taskExecuted = { false };

        Runnable task = () -> taskExecuted[0] = true;

        // Run with ReentrantLock
        ThreadUtil.runWithReentrantLock(task);

        // Assert that the task was executed
        assertTrue(taskExecuted[0], "Task should have been executed with ReentrantLock.");
    }

    // Test for runWithCompletableFuture method
    @Test
    void testRunWithCompletableFuture() throws ExecutionException, InterruptedException {
        final boolean[] taskExecuted = { false };

        Runnable task = () -> taskExecuted[0] = true;

        // Run the task asynchronously using CompletableFuture
        CompletableFuture<Void> future = ThreadUtil.runWithCompletableFuture(task);

        // Wait for the task to complete
        future.get(); // This will block until the task completes

        // Assert that the task was executed
        assertTrue(taskExecuted[0], "Task should have been executed with CompletableFuture.");
    }

    // Test for runWithVirtualThread method
    @Test
    void testRunWithVirtualThread() throws InterruptedException {
        final boolean[] taskExecuted = { false };

        // Task that modifies the array when executed
        Runnable task = () -> taskExecuted[0] = true;

        // Run the task using a virtual thread
        ThreadUtil.runWithVirtualThread(task);

        // Wait for the task to complete
        Thread.sleep(100);

        // Assert that the task was executed
        assertTrue(taskExecuted[0], "Task should have been executed with virtual thread.");
    }

    // Test for runWithVirtualThreadV2 method
    @Test
    void testRunWithVirtualThreadV2() throws InterruptedException {
        final boolean[] taskExecuted = { false };

        // Task that modifies the array when executed
        Runnable task = () -> taskExecuted[0] = true;

        // Run the task using virtual thread (v2)
        ThreadUtil.runWithVirtualThreadV2(task);

        // Assert that the task was executed
        assertTrue(taskExecuted[0], "Task should have been executed with virtual thread V2.");
    }
}
