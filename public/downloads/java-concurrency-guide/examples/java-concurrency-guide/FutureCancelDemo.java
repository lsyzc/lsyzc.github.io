import java.util.concurrent.*;

public class FutureCancelDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch allowFinish = new CountDownLatch(1);
        CountDownLatch bodyFinished = new CountDownLatch(1);
        try {
            Future<Integer> f = pool.submit(() -> {
                started.countDown();
                try {
                    allowFinish.await();
                    return 42;
                } finally {
                    bodyFinished.countDown();
                }
            });
            started.await();
            System.out.println("cancelled=" + f.cancel(false));
            System.out.println("done=" + f.isDone());
            System.out.println("bodyFinished=" + (bodyFinished.getCount() == 0));
            try {
                f.get();
            } catch (CancellationException e) {
                System.out.println("get: CancellationException");
            }
            allowFinish.countDown();
            bodyFinished.await();
            System.out.println("body eventually finished");
        } finally {
            allowFinish.countDown();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool still running");
            }
        }
    }
}
