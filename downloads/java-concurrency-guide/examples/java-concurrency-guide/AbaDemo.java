import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;

public class AbaDemo {
    public static void main(String[] args) throws InterruptedException {
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();
        AtomicReference<Object> plain = new AtomicReference<>(a);
        AtomicStampedReference<Object> versioned = new AtomicStampedReference<>(a, 0);
        Object oldPlain = plain.get();
        int[] stamp = new int[1];
        Object oldRef = versioned.get(stamp); // 原子地取得引用与版本

        Thread changer = new Thread(() -> {
            if (!plain.compareAndSet(a, b) || !plain.compareAndSet(b, a)
                    || !versioned.compareAndSet(a, b, 0, 1)
                    || !versioned.compareAndSet(b, a, 1, 2)) {
                throw new AssertionError("unexpected contention");
            }
        });
        changer.start();
        changer.join();

        System.out.println("plain=" + plain.compareAndSet(oldPlain, c));
        System.out.println("versioned="
                + versioned.compareAndSet(oldRef, c, stamp[0], stamp[0] + 1));
    }
}
