package com.example.demo.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThreadUtilTest {

    @Test
    public void testRunBasicThread() {
        assertDoesNotThrow(() -> ThreadUtil.runBasicThread(() -> System.out.println("Basic Thread Running")));
    }

    @Test
    public void testRunConcurrentTasks() {
        Runnable task1 = () -> System.out.println("Concurrent Task 1 Running");
        Runnable task2 = () -> System.out.println("Concurrent Task 2 Running");
        assertDoesNotThrow(() -> ThreadUtil.runConcurrentTasks(task1, task2));
    }

    @Test
    public void testRunParallelTasks() {
        Runnable task1 = () -> System.out.println("Parallel Task 1 Running");
        Runnable task2 = () -> System.out.println("Parallel Task 2 Running");
        assertDoesNotThrow(() -> ThreadUtil.runParallelTasks(task1, task2));
    }

    @Test
    public void testRunWithThreadPool() {
        Runnable task = () -> System.out.println("Thread Pool Task Running");
        assertDoesNotThrow(() -> ThreadUtil.runWithThreadPool(task, 2));
    }

    @Test
    public void testRunWithVirtualThread() {
        Runnable task = () -> System.out.println("Virtual Thread Task Running");
        assertDoesNotThrow(() -> ThreadUtil.runWithVirtualThread(task));
    }

    @Test
    public void testSynchronizedMethod() {
        Runnable task = () -> System.out.println("Synchronized Task Running");
        assertDoesNotThrow(() -> ThreadUtil.synchronizedMethod(task));
    }

    @Test
    public void testRunWithReentrantLock() {
        Runnable task = () -> System.out.println("ReentrantLock Task Running");
        assertDoesNotThrow(() -> ThreadUtil.runWithReentrantLock(task));
    }

    @Test
    public void testRunWithCompletableFuture() throws ExecutionException, InterruptedException {
        Runnable task = () -> System.out.println("CompletableFuture Task Running");
        CompletableFuture<Void> future = ThreadUtil.runWithCompletableFuture(task);
        future.get(); // Wait for the task to complete
        assertTrue(future.isDone());
    }
}
