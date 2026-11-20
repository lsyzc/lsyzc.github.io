import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BarrierDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger phase = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(2,
                () -> System.out.println("phase=" + phase.incrementAndGet()));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Void>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    barrier.await(5, TimeUnit.SECONDS);
                    return null;
                }));
            }
            for (Future<Void> f : results) {
                f.get(10, TimeUnit.SECONDS);
            }
            System.out.println("phases=" + phase.get());
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool still running");
            }
        }
    }
}
