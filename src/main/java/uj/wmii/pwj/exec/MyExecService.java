package uj.wmii.pwj.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class MyExecService implements ExecutorService {

    private static class EndOfQueue<V> extends FutureTask<V> {
        public EndOfQueue() {
            super(() -> {}, null);
        }
    }

    private final BlockingQueue<FutureTask<?>> queue = new LinkedBlockingQueue<>();
    private boolean shutdown = false;
    Thread thread = new Thread(() -> {
        while(true) {
            try {
                FutureTask<?> task = queue.take();
                if (task instanceof EndOfQueue<?>) {
                    break;
                }
                else {
                    task.run();
                }
            } catch (InterruptedException ignored) { }
        }
    });


    private <T> boolean isCollectionNull(Collection<T> collection) {
        for (T el : collection) {
            if (el == null) return true;
        }
        return false;
    }

    private void cancelAll(Collection<? extends Future<?>> collection) {
        for (Future<?> f : collection) {
            f.cancel(true);
        }
    }

    @SuppressWarnings("removal")
    private void checkSecurityManager() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("modifyThread"));
        }
    }

    public MyExecService() {
        thread.start();
    }

    static MyExecService newInstance() {
        return new MyExecService();
    }

    @Override
    public void shutdown() {
        checkSecurityManager();
        shutdown = true;
        queue.add(new EndOfQueue<>());
    }

    @Override
    public List<Runnable> shutdownNow() {
        checkSecurityManager();
        thread.interrupt();
        shutdown = true;
        return queue.stream().map(ft -> (Runnable) ft).toList();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return !thread.isAlive();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long millis = unit.toMillis(timeout);
        long nanos = unit.toNanos(timeout) % 1_000_000;
        thread.join(millis, (int) nanos);
        return isTerminated();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) {
            throw new NullPointerException("The executor doesn't accept null tasks.");
        }
        if (shutdown) {
            throw new RejectedExecutionException("The executor is shut down.");
        }
        FutureTask<T> ft = new FutureTask<>(task);
        queue.add(ft);
        return ft;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) {
            throw new NullPointerException("The executor doesn't accept null tasks.");
        }
        if (shutdown) {
            throw new RejectedExecutionException("The executor is shut down.");
        }
        FutureTask<T> ft = new FutureTask<>(task, result);
        queue.add(ft);
        return ft;
    }


    @Override
    public Future<?> submit(Runnable task) {
        if (task == null) {
            throw new NullPointerException("The executor doesn't accept null tasks.");
        }
        if (shutdown) {
            throw new RejectedExecutionException("The executor is shut down.");
        }
        FutureTask<?> ft = new FutureTask<>(task, null);
        queue.add(ft);
        return ft;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (tasks == null || isCollectionNull(tasks)) {
            throw new NullPointerException("The executor doesn't accept null tasks.");
        }
        if (shutdown) {
            throw new RejectedExecutionException("The executor is shut down.");
        }
        List<Future<T>> list = new ArrayList<>();
        for (Callable<T> task : tasks) {
            FutureTask<T> ft = new FutureTask<>(task);
            list.add(ft);
            submit(ft);
        }
        for (Future<T> f : list) {
            try {
                f.get();
            } catch (InterruptedException ie) {
                cancelAll(list);
                throw ie;
            } catch (ExecutionException ignored) { }
        }
        return list;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        if (tasks == null || isCollectionNull(tasks)) {
            throw new NullPointerException("The executor doesn't accept null tasks.");
        }
        if (shutdown) {
            throw new RejectedExecutionException("The executor is shut down.");
        }

        long timeoutNanos = unit.toNanos(timeout);
        long start = System.nanoTime();

        List<Future<T>> list = new ArrayList<>();
        for (Callable<T> task : tasks) {
            FutureTask<T> ft = new FutureTask<>(task);
            list.add(ft);
            submit(ft);
        }
        for (Future<T> f : list) {
            if (!f.isDone()) {
                long now = System.nanoTime();
                long timeRemaining = timeoutNanos - (now - start);
                if (timeRemaining < 0) {
                    cancelAll(list);
                    return list;
                }
                try {
                    f.get(timeRemaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException ie) {
                    cancelAll(list);
                    throw ie;
                } catch (TimeoutException e) {
                    cancelAll(list);
                    return list;
                } catch (ExecutionException ignored) { }
            }
        }
        return list;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (tasks == null || isCollectionNull(tasks)) {
            throw new NullPointerException("The executor doesn't accept null tasks.");
        }
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("Tasks may not be empty.");
        }
        if (shutdown) {
            throw new RejectedExecutionException("The executor is shut down.");
        }
        Throwable lastException = null;
        List<Future<T>> list = new ArrayList<>();
        for (Callable<T> task : tasks) {
            FutureTask<T> ft = new FutureTask<>(task);
            list.add(ft);
            submit(ft);
        }
        try {
            for (Future<T> f : list) {
                try {
                    return f.get();
                } catch (ExecutionException ee) {
                    lastException = ee.getCause();
                }
            }
        } finally {
            cancelAll(list);
        }

        throw new ExecutionException("No task completed successfully.", lastException);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null || isCollectionNull(tasks)) {
            throw new NullPointerException("The executor doesn't accept null tasks.");
        }
        if (shutdown) {
            throw new RejectedExecutionException("The executor is shut down.");
        }

        long timeoutNanos = unit.toNanos(timeout);
        long start = System.nanoTime();

        Throwable lastException = null;
        List<Future<T>> list = new ArrayList<>();
        for (Callable<T> task : tasks) {
            FutureTask<T> ft = new FutureTask<>(task);
            list.add(ft);
            submit(ft);
        }
        try {
            for (Future<T> f : list) {
                if (!f.isDone()) {
                    long now = System.nanoTime();
                    long timeRemaining = timeoutNanos - (now - start);
                    if (timeRemaining < 0) {
                        throw new TimeoutException("No task completed in required time");
                    }
                    try {
                        return f.get(timeRemaining, TimeUnit.NANOSECONDS);
                    } catch (ExecutionException ee) {
                        lastException = ee.getCause();
                    }
                }
            }
        } finally {
            cancelAll(list);
        }

        throw new ExecutionException("No task completed successfully.", lastException);
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("The executor doesn't accept null tasks.");
        }
        if (shutdown) {
            throw new RejectedExecutionException("The executor is shut down");
        }
        submit(command);
    }
}
