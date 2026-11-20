import java.util.concurrent.*;

public class InheritedContextDemo {
    private static final InheritableThreadLocal<String> CONTEXT =
            new InheritableThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CONTEXT.set("request-A");
            System.out.println(pool.submit(CONTEXT::get).get());
            CONTEXT.set("request-B");
            System.out.println(pool.submit(CONTEXT::get).get());
        } finally {
            CONTEXT.remove(); // 清主线程；池中线程随关闭退出
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool still running");
            }
        }
    }
}
