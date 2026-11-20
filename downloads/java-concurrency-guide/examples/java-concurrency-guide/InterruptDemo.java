import java.util.concurrent.CountDownLatch;

public class InterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                entered.countDown();
                while (true) {
                    Thread.sleep(1000); // 阻塞循环通过中断异常退出
                }
            } catch (InterruptedException e) {
                // sleep 抛异常时已清除标志；本层不继续向上抛，因此恢复。
                Thread.currentThread().interrupt();
                System.out.println("caught, interrupted="
                        + Thread.currentThread().isInterrupted());
            } finally {
                System.out.println("cleanup");
            }
        }, "worker");
        worker.start();
        entered.await();
        worker.interrupt();
        worker.join();
        System.out.println("terminated=" + !worker.isAlive());
    }
}
