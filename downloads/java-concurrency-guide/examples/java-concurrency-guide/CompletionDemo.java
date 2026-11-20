import java.util.concurrent.*;

public class CompletionDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CompletionService<String> completed = new ExecutorCompletionService<>(pool);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        try {
            completed.submit(() -> {
                releaseSlow.await();
                return "slow";
            });
            completed.submit(() -> "fast");
            System.out.println(completed.take().get());
            releaseSlow.countDown();
            System.out.println(completed.take().get());
        } finally {
            releaseSlow.countDown();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool still running");
            }
        }
    }
}
