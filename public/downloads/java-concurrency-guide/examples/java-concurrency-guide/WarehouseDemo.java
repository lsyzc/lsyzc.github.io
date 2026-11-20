import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class WarehouseDemo {
    static final class Warehouse {
        private final int capacity;
        private int stock;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition enoughStock = lock.newCondition();

        Warehouse(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            this.capacity = capacity;
        }

        void produce(int n) throws InterruptedException {
            check(n);
            lock.lockInterruptibly();
            try {
                while (n > capacity - stock) {
                    notFull.await();
                }
                stock += n;
                enoughStock.signalAll();
            } finally {
                lock.unlock();
            }
        }

        void consume(int n) throws InterruptedException {
            check(n);
            lock.lockInterruptibly();
            try {
                while (stock < n) {
                    enoughStock.await();
                }
                stock -= n;
                notFull.signalAll();
            } finally {
                lock.unlock();
            }
        }

        int stock() {
            lock.lock();
            try {
                return stock;
            } finally {
                lock.unlock();
            }
        }

        private void check(int n) {
            if (n <= 0 || n > capacity) {
                throw new IllegalArgumentException("invalid batch size");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Warehouse warehouse = new Warehouse(5);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> producer = pool.submit(() -> {
                for (int i = 0; i < 6; i++) {
                    warehouse.produce(2);
                }
                return null;
            });
            Future<Void> consumer = pool.submit(() -> {
                for (int i = 0; i < 4; i++) {
                    warehouse.consume(3);
                }
                return null;
            });
            producer.get(5, TimeUnit.SECONDS);
            consumer.get(5, TimeUnit.SECONDS);
            System.out.println("stock=" + warehouse.stock());
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool still running");
            }
        }
    }
}
