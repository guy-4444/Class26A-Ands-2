package com.guy.class26a_ands_2;

import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCT7 - Modern Cycle Timer
 *
 * A thread-safe task scheduler for Android that executes callbacks on the main thread.
 *
 * Key improvements over MCT6:
 * - Single shared thread pool (prevents thread leak)
 * - Atomic operations for thread safety
 * - Proper use of ScheduledFuture for cancellation
 * - WeakReference support to prevent Activity leaks
 * - Lifecycle-aware design
 * - No NPE catching hacks
 * - Uses scheduleWithFixedDelay (not scheduleAtFixedRate) to prevent
 *   burst executions when Android process transitions from cached state
 *
 * Usage:
 *   // Initialize once in Application.onCreate()
 *   MCT7.init();
 *
 *   // Create repeating task (5 times, every 1 second)
 *   MCT7.get().cycle(5, 1000, new MCT7.CycleTicker() {
 *       @Override public void onTick(int remaining) { Log.d("TAG", "Tick: " + remaining); }
 *       @Override public void onComplete() { Log.d("TAG", "Done!"); }
 *   });
 *
 *   // Create one-shot delayed task
 *   MCT7.get().delay(2000, () -> Log.d("TAG", "Executed after 2s"));
 *
 *   // With tags for group management
 *   MCT7.get().cycle(10, 500, "MainActivity", ticker);
 *   MCT7.get().cancelByTag("MainActivity"); // Cancel all tasks with this tag
 *
 *   // Clean up in Activity.onDestroy()
 *   MCT7.get().cancelByTag("ActivityName");
 *   // Or cancel all: MCT7.get().cancelAll();
 */
public class MCT7 {

    // ==================== Interfaces ====================

    /**
     * Callback for repeating cycle tasks.
     * All methods are called on the main thread.
     */
    public interface CycleTicker {
        /**
         * Called on each tick.
         * @param remainingTicks Number of ticks remaining (or INFINITE for continuous tasks)
         */
        void onTick(int remainingTicks);

        /**
         * Called when all ticks are complete.
         * Not called if task is cancelled or runs indefinitely.
         */
        default void onComplete() {}
    }

    /**
     * Simple callback for one-shot delayed tasks.
     */
    public interface DelayedTask {
        void onExecute();
    }

    /**
     * Adapter for CycleTicker when you only need onTick.
     */
    public static abstract class SimpleCycleTicker implements CycleTicker {
        @Override
        public void onComplete() {
            // Default empty implementation
        }
    }

    // ==================== Constants ====================

    /** Use this for tasks that should run indefinitely until cancelled */
    public static final int INFINITE = -1;

    private static final String DEFAULT_TAG = "";
    private static final int THREAD_POOL_SIZE = 2; // Sufficient for most apps

    // ==================== Singleton ====================

    private static volatile MCT7 instance;

    public static MCT7 get() {
        if (instance == null) {
            throw new IllegalStateException("MCT7 not initialized. Call MCT7.init() in Application.onCreate()");
        }
        return instance;
    }

    /**
     * Initialize MCT7. Call once in Application.onCreate().
     */
    public static synchronized void init() {
        if (instance == null) {
            instance = new MCT7();
        }
    }

    /**
     * Shutdown the scheduler completely.
     * Call in Application.onTerminate() if needed.
     */
    public static synchronized void shutdown() {
        if (instance != null) {
            instance.dispose();
            instance = null;
        }
    }

    // ==================== Task Class ====================

    private static class Task {
        final int id;
        final String tag;
        final ScheduledFuture<?> future;
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        // For cycle tasks
        final WeakReference<CycleTicker> cycleTickerRef;
        final AtomicInteger remainingTicks;
        final boolean isInfinite;

        // For delayed tasks
        final WeakReference<DelayedTask> delayedTaskRef;

        // Constructor for cycle tasks
        Task(int id, String tag, CycleTicker ticker, int ticks, ScheduledFuture<?> future) {
            this.id = id;
            this.tag = tag;
            this.cycleTickerRef = new WeakReference<>(ticker);
            this.remainingTicks = new AtomicInteger(ticks);
            this.isInfinite = (ticks == INFINITE);
            this.future = future;
            this.delayedTaskRef = null;
        }

        // Constructor for delayed tasks
        Task(int id, String tag, DelayedTask task, ScheduledFuture<?> future) {
            this.id = id;
            this.tag = tag;
            this.delayedTaskRef = new WeakReference<>(task);
            this.future = future;
            this.cycleTickerRef = null;
            this.remainingTicks = null;
            this.isInfinite = false;
        }

        void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                future.cancel(false); // Don't interrupt if running
            }
        }

        boolean isCancelled() {
            return cancelled.get();
        }
    }

    // ==================== Instance Fields ====================

    private final ScheduledExecutorService executor;
    private final Handler mainHandler;
    private final ConcurrentHashMap<Integer, Task> tasks;
    private final AtomicInteger taskIdGenerator;

    // ==================== Constructor ====================

    private MCT7() {
        this.executor = Executors.newScheduledThreadPool(THREAD_POOL_SIZE, r -> {
            Thread t = new Thread(r, "MCT7-Worker");
            t.setDaemon(true); // Won't prevent app termination
            return t;
        });
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.tasks = new ConcurrentHashMap<>();
        this.taskIdGenerator = new AtomicInteger(0);
    }

    private void dispose() {
        cancelAll();
        executor.shutdownNow();
    }

    // ==================== Public API ====================

    /**
     * Schedule a repeating task.
     *
     * Note: Uses fixed-delay scheduling (not fixed-rate). The interval is measured
     * from the END of each execution, not from start-to-start. This prevents
     * burst executions when Android processes transition from cached state.
     *
     * @param repeatCount Number of times to execute (use INFINITE for endless)
     * @param intervalMs Delay between end of one execution and start of next (milliseconds)
     * @param ticker Callback for each tick and completion
     * @return Task ID for later reference/cancellation
     */
    public int cycle(int repeatCount, long intervalMs, CycleTicker ticker) {
        return cycle(repeatCount, intervalMs, DEFAULT_TAG, ticker);
    }

    /**
     * Schedule a repeating task with a tag.
     * Note: Uses fixed-delay scheduling (not fixed-rate).
     * The interval is measured from the END of each execution, not from start-to-start.
     * This prevents burst executions when Android processes transition from cached state.
     *
     * @param repeatCount Number of times to execute (use INFINITE for endless)
     * @param intervalMs Delay between end of one execution and start of next (milliseconds)
     * @param tag Optional tag for grouping
     * @param ticker Callback for each tick and completion
     * @return Task ID for later reference/cancellation
     */
    public int cycle(int repeatCount, long intervalMs, String tag, CycleTicker ticker) {
        return cycle(repeatCount, intervalMs, 0, tag, ticker);
    }

    /**
     * Schedule a repeating task with initial delay.
     */
    public int cycle(int repeatCount, long intervalMs, long initialDelayMs, String tag, CycleTicker ticker) {
        if (ticker == null) {
            throw new IllegalArgumentException("Ticker cannot be null");
        }
        if (repeatCount == 0) {
            return -1; // No-op
        }
        if (repeatCount < INFINITE) {
            throw new IllegalArgumentException("Invalid repeat count: " + repeatCount);
        }

        final int taskId = taskIdGenerator.incrementAndGet();
        final String safeTag = (tag != null) ? tag : DEFAULT_TAG;

        // Create placeholder task first (future will be set after scheduling)
        final Task[] taskHolder = new Task[1];

        // Use scheduleWithFixedDelay instead of scheduleAtFixedRate!
        // scheduleAtFixedRate can cause hundreds of rapid executions when Android
        // process transitions from cached to uncached state (all "missed" ticks fire at once)
        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(() -> {
            Task task = taskHolder[0];
            if (task == null || task.isCancelled()) {
                return;
            }

            CycleTicker callback = task.cycleTickerRef.get();
            if (callback == null) {
                // Callback was garbage collected (Activity destroyed)
                removeTask(taskId);
                task.cancel();
                return;
            }

            int remaining = task.isInfinite ? INFINITE : task.remainingTicks.get();

            // Post tick to main thread
            mainHandler.post(() -> {
                if (!task.isCancelled()) {
                    callback.onTick(remaining);
                }
            });

            // Decrement and check completion
            if (!task.isInfinite) {
                int newRemaining = task.remainingTicks.decrementAndGet();
                if (newRemaining <= 0) {
                    removeTask(taskId);
                    task.cancel();

                    // Post completion to main thread
                    mainHandler.post(() -> {
                        CycleTicker cb = task.cycleTickerRef.get();
                        if (cb != null) {
                            cb.onComplete();
                        }
                    });
                }
            }
        }, initialDelayMs, intervalMs, TimeUnit.MILLISECONDS);

        Task task = new Task(taskId, safeTag, ticker, repeatCount, future);
        taskHolder[0] = task;
        tasks.put(taskId, task);

        return taskId;
    }

    /**
     * Schedule a one-shot delayed task.
     *
     * @param delayMs Delay before execution in milliseconds
     * @param task Callback to execute
     * @return Task ID for cancellation
     */
    public int delay(long delayMs, DelayedTask task) {
        return delay(delayMs, DEFAULT_TAG, task);
    }

    /**
     * Schedule a one-shot delayed task with a tag.
     */
    public int delay(long delayMs, String tag, DelayedTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }

        final int taskId = taskIdGenerator.incrementAndGet();
        final String safeTag = (tag != null) ? tag : DEFAULT_TAG;

        // Create placeholder
        final Task[] taskHolder = new Task[1];

        // Use schedule() for one-shot tasks, not scheduleAtFixedRate()
        ScheduledFuture<?> future = executor.schedule(() -> {
            Task t = taskHolder[0];
            if (t == null || t.isCancelled()) {
                return;
            }

            DelayedTask callback = t.delayedTaskRef.get();
            removeTask(taskId);

            if (callback != null) {
                mainHandler.post(callback::onExecute);
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        Task taskObj = new Task(taskId, safeTag, task, future);
        taskHolder[0] = taskObj;
        tasks.put(taskId, taskObj);

        return taskId;
    }

    /**
     * Cancel a specific task by ID.
     *
     * @param taskId The task ID returned by cycle() or delay()
     * @return true if task was found and cancelled
     */
    public boolean cancel(int taskId) {
        Task task = tasks.remove(taskId);
        if (task != null) {
            task.cancel();
            return true;
        }
        return false;
    }

    /**
     * Cancel a task by its callback reference.
     */
    public boolean cancel(CycleTicker ticker) {
        if (ticker == null) return false;

        boolean found = false;
        Iterator<Task> iterator = tasks.values().iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.cycleTickerRef != null && task.cycleTickerRef.get() == ticker) {
                iterator.remove();
                task.cancel();
                found = true;
                // Don't break - there might be duplicates
            }
        }
        return found;
    }

    /**
     * Cancel a task by its callback reference.
     */
    public boolean cancel(DelayedTask delayedTask) {
        if (delayedTask == null) return false;

        boolean found = false;
        Iterator<Task> iterator = tasks.values().iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.delayedTaskRef != null && task.delayedTaskRef.get() == delayedTask) {
                iterator.remove();
                task.cancel();
                found = true;
            }
        }
        return found;
    }

    /**
     * Cancel all tasks with a specific tag.
     * Useful for cleanup in Activity.onDestroy().
     */
    public int cancelByTag(String tag) {
        if (tag == null) {
            tag = DEFAULT_TAG;
        }

        int count = 0;
        Iterator<Task> iterator = tasks.values().iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (tag.equals(task.tag)) {
                iterator.remove();
                task.cancel();
                count++;
            }
        }
        return count;
    }

    /**
     * Cancel all active tasks.
     */
    public void cancelAll() {
        for (Task task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    /**
     * Get the number of currently active tasks.
     */
    public int getActiveTaskCount() {
        return tasks.size();
    }

    /**
     * Check if a specific task is still active.
     */
    public boolean isActive(int taskId) {
        Task task = tasks.get(taskId);
        return task != null && !task.isCancelled();
    }

    // ==================== Private Helpers ====================

    private void removeTask(int taskId) {
        tasks.remove(taskId);
    }
}