import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinDemo {
    static final class Sum extends RecursiveTask<Long> {
        private static final long serialVersionUID = 1L;
        private final int from;
        private final int to;

        Sum(int from, int to) {
            this.from = from;
            this.to = to;
        }

        @Override
        protected Long compute() {
            if (to - from <= 10) {
                long sum = 0;
                for (int i = from; i <= to; i++) {
                    sum += i;
                }
                return sum;
            }
            int mid = from + (to - from) / 2;
            Sum left = new Sum(from, mid);
            Sum right = new Sum(mid + 1, to);
            left.fork();
            long rightResult = right.compute();
            return left.join() + rightResult;
        }
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            System.out.println(pool.invoke(new Sum(1, 100)));
        } finally {
            pool.shutdown();
        }
    }
}
