import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/** Checks for the local teaching fragments; not a performance benchmark. */
public class VerifyExamples {
    static void check(boolean condition) {
        if (!condition) throw new AssertionError("invariant failed");
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            PartialExamples.Inventory inventory = new PartialExamples.Inventory();
            List<Future<Boolean>> purchases = new ArrayList<>();
            for (int i = 0; i < 30; i++) purchases.add(pool.submit(inventory::buy));
            int sold = 0;
            for (Future<Boolean> f : purchases) if (f.get()) sold++;
            check(sold == 1 && inventory.remaining() == 0);

            PartialExamples.ReportBox reports = new PartialExamples.ReportBox();
            Future<String> first = pool.submit(reports::awaitReport);
            Future<String> second = pool.submit(reports::awaitReport);
            reports.publish("ready");
            check("ready".equals(first.get(2, TimeUnit.SECONDS)));
            check("ready".equals(second.get(2, TimeUnit.SECONDS)));

            PartialExamples.Message message = new PartialExamples.Message();
            check(message.read() == -1);
            pool.submit(message::publish).get();
            check(message.read() == 42);
            List<Future<PartialExamples.Singleton>> instances = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                instances.add(pool.submit(PartialExamples.Singleton::getInstance));
            }
            Object expected = instances.get(0).get();
            for (Future<?> f : instances) check(f.get() == expected);

            AtomicInteger stock = new AtomicInteger(20);
            List<Future<Boolean>> casPurchases = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                casPurchases.add(pool.submit(() -> PartialExamples.buyOne(stock)));
            }
            int casSold = 0;
            for (Future<Boolean> f : casPurchases) if (f.get()) casSold++;
            check(casSold == 20 && stock.get() == 0);

            ReentrantLock lock = new ReentrantLock();
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<?> holder = pool.submit(() -> {
                lock.lock();
                try {
                    locked.countDown();
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                }
            });
            locked.await();
            try {
                check(!PartialExamples.changeWithTimeout(lock,
                        () -> { throw new AssertionError("must not run"); }));
            } finally {
                release.countDown();
            }
            holder.get();
            try {
                PartialExamples.changeWithTimeout(lock,
                        () -> { throw new IllegalArgumentException("expected"); });
                throw new AssertionError("missing exception");
            } catch (IllegalArgumentException expectedFailure) {
                check(!lock.isLocked());
            }
            PartialExamples.showReentry(lock);
            check(!lock.isLocked());

            PartialExamples.LockedCache cache = new PartialExamples.LockedCache();
            cache.put("key", "value");
            check("value".equals(cache.get("key")));
            PartialExamples.Point point = new PartialExamples.Point();
            Future<?> writer = pool.submit(() -> {
                for (int i = 0; i < 10000; i++) {
                    point.move(3, 4);
                    point.move(0, 0);
                }
            });
            Future<?> reader = pool.submit(() -> {
                for (int i = 0; i < 10000; i++) {
                    double distance = point.distance();
                    check(distance == 0 || distance == 5);
                }
            });
            writer.get();
            reader.get();

            ReentrantLock conditionLock = new ReentrantLock();
            Condition changed = conditionLock.newCondition();
            AtomicBoolean ready = new AtomicBoolean(false);
            check(!PartialExamples.awaitReady(conditionLock, changed,
                    ready::get, TimeUnit.MILLISECONDS.toNanos(1)));
            Future<Boolean> waiting = pool.submit(() -> PartialExamples.awaitReady(
                    conditionLock, changed, ready::get, TimeUnit.SECONDS.toNanos(2)));
            conditionLock.lock();
            try {
                ready.set(true);
                changed.signalAll();
            } finally {
                conditionLock.unlock();
            }
            check(waiting.get());

            PartialExamples.OneShotGate gate = new PartialExamples.OneShotGate();
            Future<Boolean> gateResult = pool.submit(gate::await);
            gate.open();
            check(gateResult.get(2, TimeUnit.SECONDS));
            PartialExamples.OneShotGate preOpened = new PartialExamples.OneShotGate();
            preOpened.open();
            check(preOpened.await());
            Future<Boolean> interrupted = pool.submit(() -> {
                Thread.currentThread().interrupt();
                try {
                    return new PartialExamples.OneShotGate().await();
                } finally {
                    Thread.interrupted();
                }
            });
            check(!interrupted.get());

            PartialExamples.handleRequest("alice",
                    () -> check("alice".equals(PartialExamples.USER.get())));
            check(PartialExamples.USER.get() == null);
            try {
                PartialExamples.handleRequest("bob",
                        () -> { throw new IllegalArgumentException("expected"); });
            } catch (IllegalArgumentException expectedFailure) {
                check(PartialExamples.USER.get() == null);
            }
            PartialExamples.USER.remove();

            Semaphore permits = new Semaphore(1);
            check(PartialExamples.callWithPermit(permits, () -> {}));
            check(permits.availablePermits() == 1);
            permits.acquire();
            check(!PartialExamples.callWithPermit(permits,
                    () -> { throw new AssertionError("must not run"); }));
            check(permits.availablePermits() == 0);
            permits.release();
            try {
                PartialExamples.callWithPermit(permits,
                        () -> { throw new IllegalArgumentException("expected"); });
            } catch (IllegalArgumentException expectedFailure) {
                check(permits.availablePermits() == 1);
            }

            ExecutorService queued = Executors.newSingleThreadExecutor();
            CountDownLatch queuedStarted = new CountDownLatch(1);
            queued.submit(() -> {
                queuedStarted.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            queuedStarted.await();
            Future<?> neverStarted = queued.submit(() -> {});
            PartialExamples.cancelQueued(queued.shutdownNow());
            check(neverStarted.isCancelled());
            PartialExamples.closePool(queued);
            System.out.println("fragment checks passed");
        } finally {
            PartialExamples.closePool(pool);
        }
    }
}
