package uj.wmii.pwj.exec;

import org.junit.jupiter.api.Test;

import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class ExecServiceTest {

    @Test
    void testExecute() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.execute(r);
        doSleep(10);
        assertTrue(r.wasRun);
    }

    @Test
    void testScheduleRunnable() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.submit(r);
        doSleep(10);
        assertTrue(r.wasRun);
    }

    @Test
    void testScheduleRunnableWithResult() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        Object expected = new Object();
        Future<Object> f = s.submit(r, expected);
        doSleep(10);
        assertTrue(r.wasRun);
        assertTrue(f.isDone());
        assertEquals(expected, f.get());
    }

    @Test
    void testScheduleCallable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        StringCallable c = new StringCallable("X", 10);
        Future<String> f = s.submit(c);
        doSleep(30);
        assertTrue(f.isDone());
        assertEquals("X", f.get());
    }

    @Test
    void testShutdown() {
        ExecutorService s = MyExecService.newInstance();
        s.execute(new TestRunnable());
        doSleep(10);
        s.shutdown();
        assertThrows(
            RejectedExecutionException.class,
            () -> s.submit(new TestRunnable()));
    }

    @Test
    void testInvokeAll() throws InterruptedException, ExecutionException {
        int max = 4;
        Integer[] answers = new Integer[max];
        for (int i = 1; i <= max; i++) {
            answers[i - 1] = i * i;
        }
        ExecutorService s = MyExecService.newInstance();
        List<Callable<Integer>> list = getListCallableInteger(max);
        List<Future<Integer>> result = s.invokeAll(list);
        int i = 0;
        for (Future<Integer> f : result) {
            assertEquals(answers[i], f.get());
            i++;
        }
    }

    @Test
    void testInvokeAllTimeoutFail() throws InterruptedException {
        int max = 4;
        ExecutorService s = MyExecService.newInstance();
        List<Callable<Integer>> list = getListCallableInteger(max);
        long timeout = 10;
        List<Future<Integer>> result = s.invokeAll(list, timeout, TimeUnit.MILLISECONDS);
        assertEquals(max, result.size());
        for (Future<Integer> f : result) {
            assertTrue(f.isCancelled());
        }
    }

    @Test
    void testInvokeAllTimeoutSuccess() throws InterruptedException, ExecutionException {
        int max = 4;
        long timeout = 100;
        Integer[] answers = new Integer[max];
        for (int i = 1; i <= max; i++) {
            answers[i - 1] = i * i;
            timeout += 100 * i;
        }
        ExecutorService s = MyExecService.newInstance();
        List<Callable<Integer>> list = getListCallableInteger(max);
        List<Future<Integer>> result = s.invokeAll(list, timeout, TimeUnit.MILLISECONDS);
        int i = 0;
        for (Future<Integer> f : result) {
            assertEquals(answers[i], f.get());
            i++;
        }
    }

    @Test
    void testInvokeAny() throws InterruptedException, ExecutionException {
        int max = 4;
        Integer[] answers = new Integer[max];
        for (int i = 1; i <= max; i++) {
            answers[i - 1] = i * i;
        }
        ExecutorService s = MyExecService.newInstance();
        List<Callable<Integer>> list = getListCallableInteger(max);
        Integer result = s.invokeAny(list);
        boolean check = false;
        for (int i = 0; i < max; i++) {
            if (result.equals(answers[i])) {
                check = true;
                break;
            }
        }
        assertTrue(check);
    }

    @Test
    void testInvokeAnyTimeoutFail() throws InterruptedException {
        int max = 4;
        ExecutorService s = MyExecService.newInstance();
        List<Callable<Integer>> list = getListCallableInteger(max);
        long timeout = 10;
        assertThrows(TimeoutException.class, () -> s.invokeAny(list, timeout, TimeUnit.MILLISECONDS));
    }

    @Test
    void testInvokeAnyTimeoutSuccess() throws InterruptedException, ExecutionException, TimeoutException {
        int max = 4;
        long timeout = 200;
        Integer[] answers = new Integer[max];
        for (int i = 1; i <= max; i++) {
            answers[i - 1] = i * i;
        }
        ExecutorService s = MyExecService.newInstance();
        List<Callable<Integer>> list = getListCallableInteger(max);
        Integer result = s.invokeAny(list, timeout, TimeUnit.MILLISECONDS);
        boolean check = false;
        for (int i = 0; i < max; i++) {
            if (result.equals(answers[i])) {
                check = true;
                break;
            }
        }
        assertTrue(check);
    }

    @Test
    void testInvokeNullTasks() {
        ExecutorService s = MyExecService.newInstance();
        List<Callable<Integer>> list = null;
        long timeout = 1;
        List<Callable<Integer>> finalList = list;
        assertThrows(NullPointerException.class, () -> s.invokeAll(finalList));
        assertThrows(NullPointerException.class, () -> s.invokeAll(finalList, timeout, TimeUnit.NANOSECONDS));
        assertThrows(NullPointerException.class, () -> s.invokeAny(finalList));
        assertThrows(NullPointerException.class, () -> s.invokeAny(finalList, timeout, TimeUnit.NANOSECONDS));
        list = new ArrayList<>();
        list.add(null);
        List<Callable<Integer>> finalList2 = list;
        assertThrows(NullPointerException.class, () -> s.invokeAll(finalList2));
        assertThrows(NullPointerException.class, () -> s.invokeAll(finalList2, timeout, TimeUnit.NANOSECONDS));
        assertThrows(NullPointerException.class, () -> s.invokeAny(finalList2));
        assertThrows(NullPointerException.class, () -> s.invokeAny(finalList2, timeout, TimeUnit.NANOSECONDS));
    }

    @Test
    void testShutdownNow() throws InterruptedException {
        int max = 4;
        ExecutorService s = MyExecService.newInstance();
        List<Callable<Integer>> list = getListCallableInteger(max);
        for (Callable<Integer> task : list) {
            s.submit(task);
        }
        List<Runnable> result = s.shutdownNow();
        assertEquals(max - 1, result.size());
    }

    @Test
    void testAwaitTermination() throws InterruptedException {
        ExecutorService s = MyExecService.newInstance();
        long millis = 10;
        long start = System.nanoTime();
        assertFalse(s.awaitTermination(millis, TimeUnit.MILLISECONDS));
        long end = System.nanoTime();
        assertTrue(end - start >= 1_000_000 * millis);
        int max = 4;
        List<Callable<Integer>> list = getListCallableInteger(max);
        for (Callable<Integer> task : list) {
            s.submit(task);
        }
        s.shutdown();
        assertFalse(s.awaitTermination(100, TimeUnit.MILLISECONDS));
        assertTrue(s.awaitTermination((long) (max + 1) * max / 2 * 100, TimeUnit.MILLISECONDS));
    }


    static void doSleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static List<Callable<Integer>> getListCallableInteger(Integer max) {
        List<Callable<Integer>> result = new ArrayList<>();
        for (int i = 1; i <= max; i++) {
            result.add(new IntegerCallable(i));
        }
        return result;
    }

}

class StringCallable implements Callable<String> {

    private final String result;
    private final int millis;

    StringCallable(String result, int millis) {
        this.result = result;
        this.millis = millis;
    }

    @Override
    public String call() throws Exception {
        ExecServiceTest.doSleep(millis);
        return result;
    }
}
class TestRunnable implements Runnable {

    boolean wasRun;
    @Override
    public void run() {
        wasRun = true;
    }
}



class IntegerCallable implements Callable<Integer> {
    Integer val;
    public IntegerCallable(Integer val) {
        this.val = val;
    }

    @Override
    public Integer call() {
        try {
            Thread.sleep((long) 100 * val);
            return val * val;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
