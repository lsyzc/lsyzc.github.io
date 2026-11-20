public class PartialExamples {
static final class Inventory {
    private int stock = 1;

    synchronized boolean buy() {
        if (stock <= 0) {
            return false;
        }
        stock--;
        return true;
    }

    synchronized int remaining() {
        return stock;
    }
}

static final class ReportBox {
    private String report;

    synchronized String awaitReport() throws InterruptedException {
        while (report == null) {
            wait();
        }
        return report;
    }

    synchronized void publish(String value) {
        report = java.util.Objects.requireNonNull(value);
        notifyAll();
    }
}

static final class Message {
    private int payload;
    private volatile boolean ready;

    void publish() {
        payload = 42;
        ready = true;
    }

    int read() {
        if (!ready) {
            return -1;
        }
        return payload;
    }
}

static final class Singleton {
    private static volatile Singleton instance;
    private final int answer;

    private Singleton() {
        answer = 42;
    }

    static Singleton getInstance() {
        Singleton local = instance;
        if (local == null) {
            synchronized (Singleton.class) {
                local = instance;
                if (local == null) {
                    local = new Singleton();
                    instance = local;
                }
            }
        }
        return local;
    }
}

static void closePool(java.util.concurrent.ExecutorService pool)
        throws InterruptedException {
    pool.shutdown();
    try {
        if (!pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
            cancelQueued(pool.shutdownNow());
            if (!pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("任务未响应关闭请求");
            }
        }
    } catch (InterruptedException e) {
        cancelQueued(pool.shutdownNow());
        throw e; // 交还给上层处理中断
    }
}

static void cancelQueued(java.util.List<Runnable> queued) {
    for (Runnable task : queued) {
        if (task instanceof java.util.concurrent.Future<?>) {
            ((java.util.concurrent.Future<?>) task).cancel(false);
        }
    }
}

static boolean buyOne(java.util.concurrent.atomic.AtomicInteger stock) {
    for (;;) {
        int current = stock.get();
        if (current <= 0) {
            return false;
        }
        if (stock.compareAndSet(current, current - 1)) {
            return true;
        }
    }
}

static boolean changeWithTimeout(
        java.util.concurrent.locks.ReentrantLock lock,
        Runnable update) throws InterruptedException {
    if (!lock.tryLock(200, java.util.concurrent.TimeUnit.MILLISECONDS)) {
        return false;
    }
    try {
        update.run();
        return true;
    } finally {
        lock.unlock();
    }
}

static void showReentry(java.util.concurrent.locks.ReentrantLock lock) {
    lock.lock();
    try {
        System.out.println(lock.getHoldCount()); // 1
        lock.lock();
        try {
            System.out.println(lock.getHoldCount()); // 2
        } finally {
            lock.unlock();
        }
        System.out.println(lock.getHoldCount()); // 1
    } finally {
        lock.unlock();
    }
}

static final class LockedCache {
    private final java.util.Map<String, String> data = new java.util.HashMap<>();
    private final java.util.concurrent.locks.ReentrantReadWriteLock rw =
            new java.util.concurrent.locks.ReentrantReadWriteLock();

    String get(String key) {
        rw.readLock().lock();
        try {
            return data.get(key);
        } finally {
            rw.readLock().unlock();
        }
    }

    void put(String key, String value) {
        rw.writeLock().lock();
        try {
            data.put(key, value);
        } finally {
            rw.writeLock().unlock();
        }
    }
}

static final class Point {
    private double x;
    private double y;
    private final java.util.concurrent.locks.StampedLock lock =
            new java.util.concurrent.locks.StampedLock();

    void move(double newX, double newY) {
        long stamp = lock.writeLock();
        try {
            x = newX;
            y = newY;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    double distance() {
        long stamp = lock.tryOptimisticRead();
        double localX = x;
        double localY = y;
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                localX = x;
                localY = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return Math.hypot(localX, localY);
    }
}

static boolean awaitReady(
        java.util.concurrent.locks.ReentrantLock lock,
        java.util.concurrent.locks.Condition changed,
        java.util.function.BooleanSupplier ready,
        long timeoutNanos) throws InterruptedException {
    lock.lockInterruptibly();
    try {
        long remaining = timeoutNanos;
        while (!ready.getAsBoolean()) {
            if (remaining <= 0) {
                return false;
            }
            remaining = changed.awaitNanos(remaining);
        }
        return true;
    } finally {
        lock.unlock();
    }
}

static final class OneShotGate {
    private volatile boolean open;
    private volatile Thread waiter;

    // 仅允许一个等待者调用；不是通用多等待者门闩。
    boolean await() {
        waiter = Thread.currentThread();
        try {
            while (!open) {
                if (Thread.currentThread().isInterrupted()) {
                    return false;
                }
                java.util.concurrent.locks.LockSupport.park(this);
            }
            return true;
        } finally {
            waiter = null;
        }
    }

    void open() {
        open = true;
        java.util.concurrent.locks.LockSupport.unpark(waiter);
    }
}

static final ThreadLocal<String> USER = new ThreadLocal<>();

static void handleRequest(String userId, Runnable business) {
    USER.set(userId);
    try {
        business.run();
    } finally {
        USER.remove();
    }
}

static boolean callWithPermit(java.util.concurrent.Semaphore permits,
                              Runnable remoteCall) throws InterruptedException {
    if (!permits.tryAcquire(200, java.util.concurrent.TimeUnit.MILLISECONDS)) {
        return false;
    }
    try {
        remoteCall.run();
        return true;
    } finally {
        permits.release();
    }
}
}
