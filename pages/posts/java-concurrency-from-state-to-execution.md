---
title: Java 并发编程：从共享状态到任务执行，建立完整的思考框架
date: 2026-11-20T20:00:00+08:00
lang: zh
duration: 30min
type: blog
art: dots
description: 从线程与内存模型出发，串联锁、原子类、线程池、Future、线程协作与上下文传递，用一个可运行的库存示例理解并发边界。
---

写并发代码时，最容易出现的错觉是：线程启动了，任务跑完了，控制台输出也正常，程序就没有问题。实际上，一次正确输出既不能证明没有数据竞争，也不能证明系统在任务积压、请求超时或关闭时仍能正确工作。

本文根据《Java 并发编程教学视频知识点梳理》中第 1–35 节的主题重新组织，面向已经掌握 Java 基础、准备系统学习并发编程的读者。我们沿着四个问题展开：**共享状态如何保持一致，任务如何执行，线程如何协作，资源如何回收。**

示例以 Java 17 为基线，使用平台线程和 JDK 标准库。原始材料中第 36–40 节缺少字幕，本文不将其中的 CompletableFuture 任务编排扩写为已覆盖的课程内容；虚拟线程也不在本文讨论范围内。

[[toc]]

## 1. 先区分任务、线程和执行策略

进程为程序提供资源和地址空间，线程是进程内的执行流。同一进程中的线程可以访问共享对象，因此协作方便，也因此需要处理竞争。

并发强调多个任务在一段时间内交错推进；并行强调多个任务在同一时刻执行。单核也能实现并发，多核为并行提供条件。线程增多是否能缩短耗时，还要看 CPU、I/O、锁竞争以及任务之间的依赖。

“同步／异步”和“阻塞／非阻塞”描述的角度也不同。前者关注调用与结果的交付方式，后者关注等待期间调用线程能否继续执行。提交异步任务之后立刻调用 `Future.get()`，调用线程仍然可能阻塞。

### Runnable 和 Callable 描述要做什么

| 入口                       | 返回值     | 受检异常                     | 常见用途           |
| -------------------------- | ---------- | ---------------------------- | ------------------ |
| `Runnable.run()`           | 无         | 不能在接口方法签名中声明     | 无结果任务         |
| `Callable.call()`          | 泛型结果   | 可以声明                     | 查询、计算、聚合   |
| `Thread.start()`           | 无任务结果 | 启动失败可能抛异常           | 启动线程执行其任务 |
| `ExecutorService.submit()` | `Future`   | 任务异常通过结果获取路径暴露 | 提交并管理任务     |

直接调用 `run()` 是普通方法调用；调用 `start()` 才会启动新的线程执行流。同一个 `Thread` 对象不能重复启动。实现 `Runnable` 的价值在于把任务逻辑从线程对象中分离出来，但它本身不会让共享数据变得安全。

同样，`Executor` 只规定提交入口，具体实现可以在新线程、已有线程甚至调用者线程中执行任务。因此，不能仅凭一次 `execute()` 调用断言任务已经异步运行。[JDK 并发包说明](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html)

### 六种线程状态描述的是 JVM 视角

| 状态            | 典型含义                                    |
| --------------- | ------------------------------------------- |
| `NEW`           | 已创建，尚未启动                            |
| `RUNNABLE`      | JVM 中可运行，包含就绪与正在执行            |
| `BLOCKED`       | 等待进入或重新进入对象监视器                |
| `WAITING`       | 无期限等待，例如无超时的 `wait()`、`join()` |
| `TIMED_WAITING` | 有期限等待，例如 `sleep()`、带超时的等待    |
| `TERMINATED`    | 执行结束                                    |

`BLOCKED` 不是所有“暂时无法继续”的统称。等待 `ReentrantLock` 的线程通常通过底层停放机制等待，不能套用“等锁一定是 BLOCKED”的判断。`getState()` 也只是瞬时采样，不能拿它代替同步协议。

优先级和 `yield()` 都不能建立业务先后关系。守护线程也不适合承担必须完成的落盘或清理工作：当所有非守护线程结束时，不能指望守护线程继续完成这些操作。[Thread API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html)

## 2. 线程安全从业务不变量开始

假设库存只有一件，两个线程都执行“检查库存大于零，再扣减”。即使每一次读取和赋值都没有被拆开，这两个步骤仍然可能交错：

| 时刻 | 线程 A           | 线程 B           |
| ---- | ---------------- | ---------------- |
| 1    | 读到库存为 1     |                  |
| 2    |                  | 读到库存为 1     |
| 3    | 判定可以买，扣减 |                  |
| 4    |                  | 判定可以买，扣减 |

问题出在整个“检查并扣减”没有形成一个不可被竞争操作穿插的整体。此时应先写出不变量：库存不能小于零，成功销售数量加剩余库存必须等于初始库存。

围绕不变量再问三个问题：

1. 哪些对象是多个线程共同访问的？
2. 哪些连续操作必须作为一个整体完成？
3. 所有访问这些状态的路径，是否使用同一套同步规则？

给不同对象分别加锁，不能保护同一份共享库存。实例同步方法锁定 `this`，静态同步方法锁定对应的 `Class` 对象，二者也不会自动互斥。锁应当围绕资源和不变量设计，而不是围绕“这里看起来有多线程”设计。

## 3. JMM：可见性、原子性与有序性

Java 内存模型（JMM）规定线程间读写可以呈现怎样的结果。把它解释成“主内存与工作副本”有助于入门，但这只是抽象，不能直接当成 CPU 缓存刷新过程。

| 性质   | 要回答的问题                                 | 常见工具                 |
| ------ | -------------------------------------------- | ------------------------ |
| 原子性 | 操作能否被竞争操作穿插？                     | 锁、原子类               |
| 可见性 | 一个线程的修改，另一个线程何时能够可靠观察？ | `volatile`、锁、任务交接 |
| 有序性 | 哪些操作之间存在必须遵守的观察顺序？         | happens-before 规则      |

更可靠的推理方式是寻找 **happens-before** 关系：同一监视器的解锁与后续加锁、同一 volatile 变量的写与后续读、线程启动，以及成功等待线程结束，都能建立相应的可见性与顺序保证。这是语言规范层面的约束，不依赖“这台机器上看起来没问题”。[JLS 17.4.5](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5)

### volatile 不能把复合操作变成原子操作

`volatile boolean running` 可以用作循环任务的停止标志，但 `volatile int count` 上的 `count++` 仍然包含读取、计算、写回，两个线程可能基于相同旧值产生更新，最终丢失一次递增。

对于状态标记，需要可见性；对于单变量递增，需要原子更新；对于“检查库存并扣减”，需要保护整个业务条件。三类问题不能用同一个关键字一概处理。

### 双重检查锁解决的是安全发布

双重检查锁（DCL）需要在同步块外判空、块内再次判空，并把单例引用声明为 `volatile`。内部检查防止重复创建；volatile 发布使观察到该引用的线程能够看到构造阶段的相关写入。

用“分配内存、初始化对象、发布引用”解释风险是一种简化模型。真正的判断依据仍然是安全发布规则。一般业务代码也可以考虑静态初始化持有者等更容易推理的方案，避免无必要地手写 DCL。

## 4. 选择锁：先看需要什么等待能力

`synchronized` 适合结构清楚的临界区，正常返回或抛出异常时都会自动释放监视器。进入监视器的等待不能通过普通中断请求取消，也没有直接设置获取超时的入口。

`ReentrantLock` 提供显式控制，但必须在成功获取后，通过 `finally` 释放。

| 获取方法                 | 是否等待     | 等待能否响应中断     | 使用目的           |
| ------------------------ | ------------ | -------------------- | ------------------ |
| `lock()`                 | 可能无限等待 | 不因中断退出获取过程 | 必须取得锁         |
| `tryLock()`              | 立即返回     | 无阻塞等待           | 失败后采取其他路径 |
| `tryLock(timeout, unit)` | 限时等待     | 可以                 | 控制等待预算       |
| `lockInterruptibly()`    | 可能无限等待 | 可以                 | 支持取消等待       |

这里的“可中断”指等待获取锁的过程，并不表示线程会被强制终止。公平锁主要约束锁竞争的排队顺序，不能保证操作系统调度公平；无参 `tryLock()` 即使作用于公平锁，也可能插队。[ReentrantLock API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html)

可重入表示同一线程能够再次获取已经持有的锁，释放次数必须与成功获取次数对应。它不能消除多把锁之间的死锁。两个线程分别持有 A、B，再等待对方的锁，仍然可能永久等待。统一获取顺序、缩短持锁范围、避免持锁调用不可控的外部逻辑，都能降低这类风险。

### 读多写少也需要验证收益

`ReentrantReadWriteLock` 允许读读共享，读写、写写互斥。它适用于读操作足够多、临界区开销足以覆盖锁管理成本的场景。读锁内不能修改受保护状态，也不能因为叫“读多写少”就假定一定比互斥锁快。

`StampedLock` 的乐观读进一步减少读锁竞争：取得 stamp，把相关字段读到局部变量，再调用 `validate()`；验证失败就获取悲观读锁重新读取。在验证前，应避免基于可能不一致的数据执行不可撤销操作。

它不可重入，也不承诺统一的公平策略。因此，“支持乐观读”不能推导出“消除写线程饥饿”。使用者还需要处理校验与回退带来的复杂度。[StampedLock API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/StampedLock.html)

## 5. 原子类与 CAS：缩小同步粒度

CAS 可以理解为：只有当前值仍然等于预期值时，才把它替换为新值。基于 CAS 的更新通常在失败后重新读取并重试。原子类把这些单变量更新封装成了可用的 API。

| 工具                          | 适用问题                 | 注意边界                         |
| ----------------------------- | ------------------------ | -------------------------------- |
| `AtomicInteger`、`AtomicLong` | 原子递增、单变量条件更新 | 多个字段的业务约束仍需整体设计   |
| `AtomicReference`             | 原子替换对象引用         | 被引用对象的内部修改不会自动安全 |
| `AtomicStampedReference`      | 同时比较引用与版本       | 所有相关更新都必须遵守版本协议   |
| `LongAdder`                   | 多线程高频统计累计       | 并发更新时 `sum()` 不是原子快照  |

ABA 指状态经历 A→B→A 后，旧的 CAS 仍可能认为状态没有变化。它是否构成错误取决于业务是否关心中间历史；不能看到一次值恢复就断言一定发生了业务漏洞。版本号可以区分不同代次，但版本管理本身必须正确。

`LongAdder` 通过分散更新降低竞争，代价是额外空间和不同的读取语义。它适合请求次数等统计，不适合用 `sum()` 配合条件判断实现严格库存扣减，也不能提供原子递增后的唯一序号。所有写入停止并完成必要同步后，再汇总总数是另一种场景。[LongAdder API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html)

不要把课堂演示的耗时倍数当成性能承诺。可信的对比需要交代 JDK、硬件、预热、线程数、读写比例和多轮测量；本文不提供未经测量的性能结论。

## 6. 等待唤醒：等待的是条件

生产者—消费者模型中，消费者真正等待的是“缓冲区非空”，生产者等待的是“缓冲区未满”。通知只提示状态可能改变，线程必须重新检查条件。

| 机制                      | 等待范围           | 等待时是否释放关联锁 | 恢复执行前的要求   |
| ------------------------- | ------------------ | -------------------- | ------------------ |
| `wait/notifyAll`          | 同一对象的等待集合 | 释放该对象监视器     | 重新获得监视器     |
| `Condition.await/signal`  | 某个条件队列       | 释放关联锁           | 重新获得关联锁     |
| `LockSupport.park/unpark` | 指定线程的许可     | 不自动释放已持有的锁 | 自行检查状态与中断 |

调用 `wait()`、`notify()` 或 `notifyAll()` 必须持有相应监视器。`wait()` 只释放对应监视器，不会释放线程持有的其他锁；`sleep()` 不会替你释放锁。[JLS 等待与通知](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.2)

等待条件应使用 `while` 重检。即使排除虚假唤醒，线程重新取得锁时，资源也可能已经被另一个线程消费。`notifyAll()` 也不意味着所有线程马上继续业务操作，它们仍需竞争锁并检查各自条件。

一把 `ReentrantLock` 可以建立多个 `Condition`，分别表达“非空”和“未满”，减少无关等待者被唤醒的机会。“精准”指按条件分组，不是从条件队列里任意点名一个线程。[Condition API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Condition.html)

对于生产和消费批量大小不同的缓冲区，还要验证通知策略。若只唤醒一个当前仍无法满足条件的大批量消费者，其他可以继续的小批量消费者可能没有机会被唤醒。此时需要结合条件设计考虑 `signalAll()`，而不是机械地把全部通知改成单个通知。

`LockSupport` 为每个线程维护最多一个许可，连续多次 `unpark()` 不会累积多个许可；先发许可、后消费可以生效。`park()` 也可能因中断或其他原因返回，返回本身不能证明业务条件成立。业务开发通常优先使用阻塞队列等更高层抽象。[LockSupport API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/LockSupport.html)

## 7. 线程池管理的是执行容量

线程池除了复用线程，更重要的是给并发执行与排队设置边界。对 `ThreadPoolExecutor` 来说，核心参数包括核心线程数、最大线程数、存活时间及单位、任务队列、线程工厂、拒绝处理器。

在通常的运行情况下，新任务的处理过程可以概括为：

```text
工作线程数少于核心线程数 → 尝试创建核心工作线程
否则                    → 尝试入队
入队失败                → 尝试扩展至最大线程数
仍然无法接纳            → 执行拒绝处理器
```

实际实现还会检查关闭状态，并处理入队与关闭并发发生等情况。这幅流程用于理解容量关系，不能代替完整源码。[ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)

### 队列选择会改变最大线程数的作用

无界队列通常持续接纳任务，因此线程数达到核心值后，很少走到扩容分支。此时调大 `maximumPoolSize` 往往无法解决积压。有界队列能暴露过载，但容量也不能随意设置：队列太大可能让请求尚未开始执行就已超时。

`newFixedThreadPool()` 使用无界工作队列，`newCachedThreadPool()` 允许线程数大幅增长。它们是有明确语义的工具；在需要限制队列或线程资源的服务中，应显式选择满足容量要求的配置。[Executors API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executors.html)

线程数也不应只套用“CPU 核数乘某个常量”。CPU 密集任务受计算资源限制，I/O 密集任务还受下游连接数、响应时间和超时约束。以实际负载观察吞吐、延迟与资源占用，再调整配置，才能知道瓶颈在哪。

### 拒绝策略决定业务如何承受过载

| 策略                  | 行为                         | 需要考虑的业务后果             |
| --------------------- | ---------------------------- | ------------------------------ |
| `AbortPolicy`         | 抛出拒绝异常                 | 上层需要明确失败、降级或重试   |
| `CallerRunsPolicy`    | 未关闭时由提交线程运行       | 提交线程延迟增加，关闭后会丢弃 |
| `DiscardPolicy`       | 静默丢弃新任务               | 不能假定任务已执行             |
| `DiscardOldestPolicy` | 未关闭时移除队头，再尝试提交 | 队头未必是业务上最不重要的任务 |

尤其要留意 `submit()` 与静默丢弃的组合：如果任务对应的 Future 从未被执行或取消，调用者可能一直等不到结果。拒绝处理器不只是性能配置，它也是结果交付协议的一部分。

## 8. Future：结果、失败与取消是三条路径

`execute(Runnable)` 不返回任务结果凭据；`submit()` 返回 Future。`submit(Runnable)` 正常完成时 `get()` 返回 `null`，带预设结果的重载返回指定对象，`submit(Callable)` 返回计算结果。

对普通 `ThreadPoolExecutor`，通过 `submit()` 提交的任务异常通常保存在 Future 中，因此不能只依赖工作线程的未捕获异常处理器发现故障。调用方需要获取结果，或建立专门的异常观察机制。

| 获取结果时的情况        | 含义                           |
| ----------------------- | ------------------------------ |
| 返回结果                | 正常完成                       |
| `ExecutionException`    | 任务执行失败，应检查 cause     |
| `CancellationException` | Future 已被取消                |
| `TimeoutException`      | 本次限时等待结束，任务未必停止 |
| `InterruptedException`  | 等待结果的调用线程被中断       |

`isDone()` 包括正常完成、失败和取消；它不是“业务成功”的判断。`cancel(true)` 尝试取消并请求中断正在执行的任务，但底层代码是否停止取决于它是否配合。`get(timeout, unit)` 的超时也不会自动取消任务。[Future API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)

### 批量处理要先确定结果消费方式

| 方式                        | 适合的需求               | 边界                                 |
| --------------------------- | ------------------------ | ------------------------------------ |
| `invokeAll()`               | 等待一批任务，再逐项处理 | 返回列表保持输入顺序；失败需逐项检查 |
| `invokeAny()`               | 取一个成功结果           | 其余任务会被尝试取消                 |
| `ExecutorCompletionService` | 哪个先完成就先处理哪个   | 它依赖执行器，本身不是线程池         |

限时 `invokeAll()` 返回时，尚未完成的任务会被取消。逐个对 Future 使用相同超时时长，则可能多次消耗等待预算；如果业务规定“整批最多等待五秒”，应使用同一个截止时间计算剩余等待时间。[ExecutorService API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)

`ExecutorCompletionService` 还有一个容易误判的细节：自定义完成队列按无界队列使用，完成时采用 `add()` 放入结果。简单换成很小的有界队列，并不能得到可靠背压，放入失败的完成结果可能无法再从服务中取出。应限制在途任务数量、及时消费结果，而不是盲目缩小完成队列。[ExecutorCompletionService API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorCompletionService.html)

## 9. 一个完整示例：有限库存、有限执行容量与有限等待

下面用 20 个购买请求竞争 5 件库存，连接前面的概念：

| 约束                          | 实现                             |
| ----------------------------- | -------------------------------- |
| 检查和扣减必须原子完成        | `synchronized` 库存方法          |
| 最多 4 个工作线程             | 显式构造线程池                   |
| 待执行任务不能无限堆积        | 容量为 32 的工作队列             |
| 模拟下游最多同时处理 2 个请求 | `Semaphore(2)`                   |
| 尽早处理已完成结果            | `ExecutorCompletionService`      |
| 整批任务有等待期限            | 统一截止时间                     |
| 请求上下文不能残留在线程池里  | 每个任务 `finally` 中 `remove()` |
| 结束后回收工作线程            | 有序关闭，超时后请求中断         |

这里选择 32 个队列位置只是为了容纳本次演示，不代表服务容量建议。Semaphore 限制下游并发，库存锁保护共享状态，二者职责不同。

把代码保存为 `ConcurrencyDemo.java`，无需额外依赖：

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrencyDemo {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    static final class Inventory {
        private int remaining;

        Inventory(int initial) {
            remaining = initial;
        }

        synchronized boolean trySell() {
            if (remaining == 0) {
                return false;
            }
            remaining--;
            return true;
        }

        synchronized int remaining() {
            return remaining;
        }
    }

    public static void main(String[] args) throws Exception {
        Inventory inventory = new Inventory(5);
        Semaphore downstream = new Semaphore(2);
        AtomicInteger sequence = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                4, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32),
                task -> new Thread(task, "purchase-" + sequence.incrementAndGet()),
                new ThreadPoolExecutor.AbortPolicy());
        CompletionService<Boolean> completed = new ExecutorCompletionService<>(pool);
        List<Future<Boolean>> futures = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        try {
            for (int i = 0; i < 20; i++) {
                final String requestId = "request-" + i;
                futures.add(completed.submit(() -> {
                    REQUEST_ID.set(requestId);
                    try {
                        if (!downstream.tryAcquire(1, TimeUnit.SECONDS)) {
                            throw new TimeoutException("downstream capacity timeout");
                        }
                        try {
                            // 只模拟可中断的 I/O 等待，不用 sleep 建立执行顺序。
                            TimeUnit.MILLISECONDS.sleep(10);
                            if (!requestId.equals(REQUEST_ID.get())) {
                                throw new IllegalStateException("request context mismatch");
                            }
                            return inventory.trySell();
                        } finally {
                            // 只有成功取得许可才会进入此 try/finally。
                            downstream.release();
                        }
                    } finally {
                        REQUEST_ID.remove();
                    }
                }));
            }

            int sold = 0;
            for (int i = 0; i < futures.size(); i++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new TimeoutException("batch deadline exceeded");
                }
                Future<Boolean> result = completed.poll(remaining, TimeUnit.NANOSECONDS);
                if (result == null) {
                    throw new TimeoutException("batch deadline exceeded");
                }
                // 失败会通过 get() 向外传播，不当作“库存不足”。
                if (result.get()) {
                    sold++;
                }
            }

            if (sold != 5 || inventory.remaining() != 0
                    || downstream.availablePermits() != 2) {
                throw new IllegalStateException("invariant violated");
            }
            System.out.printf("sold=%d, rejectedByStock=%d, remaining=%d, permits=%d%n",
                    sold, futures.size() - sold, inventory.remaining(),
                    downstream.availablePermits());
        } finally {
            for (Future<Boolean> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
            shutdown(pool);
        }
    }

    private static void shutdown(ThreadPoolExecutor pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

运行：

```sh
javac --release 17 ConcurrencyDemo.java
java ConcurrencyDemo
```

正常完成时输出：

```text
sold=5, rejectedByStock=15, remaining=0, permits=2
```

这段代码验证的是库存不变量、许可配对和正常结果汇总，并不是性能测试。请求成功的具体编号没有确定性，线程调度不同也不影响总数。代码已在 OpenJDK 17 环境中编译运行。

示例对批次内首个执行异常采用失败退出策略，并取消剩余未完成任务。取消不能撤销已经成功扣减的库存；真实下单涉及持久化、幂等、事务与补偿，不能仅依靠 JVM 内的一把锁覆盖多个进程。批次截止时间约束结果等待流程，清理阶段另有等待预算，也不是任意外部 I/O 的强制终止期限。

## 10. 线程协作工具：先判断谁在等谁

很多协作关系无需自己实现等待唤醒。

| 工具             | 解决的问题                             | 生命周期             |
| ---------------- | -------------------------------------- | -------------------- |
| `Thread.join()`  | 等待某个线程结束                       | 跟随该线程           |
| `CountDownLatch` | 等待指定次数的操作完成                 | 一次性归零，不能重置 |
| `CyclicBarrier`  | 一组参与者互相等待，到齐后推进下一阶段 | 正常触发后进入下一代 |
| `Semaphore`      | 控制同时进入某个区域的数量             | 许可持续获取和归还   |

`CountDownLatch` 的计数代表操作，而不一定代表线程。若约定“每个任务无论成功失败都表示处理结束”，应在任务的 `finally` 中倒计数，同时通过另一条路径记录失败；归零本身不等于任务成功。

`CyclicBarrier` 的参与者需要实际到齐。在线程池中，如果屏障要求的参与数超过可同时运行的任务数，前一批任务可能占满工作线程等待，后一批任务却一直排队。屏障正常触发后可以循环使用；超时、中断或重置涉及屏障破坏及异常处理，不能简单理解成“每轮计数减到零”。[CyclicBarrier API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html)

Semaphore 允许多个线程同时进入，因此不会自动让 `count++` 安全。它还没有锁的所有者约束，多释放许可可能突破原先设定的并发上限。**只有成功获取的路径，才应归还对应许可。**[Semaphore API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html)

## 11. ThreadLocal：线程隔离不等于请求隔离

ThreadLocal 为每个线程关联独立值。它不自动深拷贝对象：如果两个线程分别存入同一个可变对象引用，对象本身仍然共享。

在 OpenJDK 17 实现中，值保存在 Thread 的 ThreadLocalMap 中，Entry 的 key 是弱引用，value 则是强引用。key 被回收后，存活的工作线程仍可能保留尚未清理的 value。更直接的风险是线程复用：上一个请求留下的数据可能被后一个请求读到。

所以应把清理放到与任务生命周期匹配的 `finally` 中。前面示例是在每次任务执行时设置上下文，并在离开任务时删除；仅在提交任务的主线程中设置普通 ThreadLocal，并不会把值自动传给工作线程。[ThreadLocal API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html)、[OpenJDK 17 实现](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/lang/ThreadLocal.java)

`InheritableThreadLocal` 在创建子线程时继承值，默认返回父线程值本身，不做任意对象的深拷贝。线程池在多次提交间复用线程，不会为每次任务重新执行这套继承流程，因此容易读到旧上下文。[InheritableThreadLocal API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InheritableThreadLocal.html)

需要跨任务边界传递上下文时，可以显式传入不可变的上下文对象，或者使用任务包装机制。TTL 提供任务／执行器包装等方式，在捕获、执行、恢复的边界处理上下文；仅替换变量类型并不足以完成线程池场景的接入。若传递的是可变对象，还必须另外考虑对象共享。[TTL 项目说明](https://github.com/alibaba/transmittable-thread-local)

## 12. 中断与关闭：停止需要合作

`interrupt()` 表达停止或取消的请求。循环任务需要检查中断状态，阻塞任务则要理解所调用 API 如何响应中断。

`sleep()`、`wait()`、`join()` 在抛出 `InterruptedException` 时会清除中断状态。如果当前层不能把异常继续向上抛，通常应恢复中断标志，并选择退出或交还控制。仅打印异常然后继续循环，可能使取消失效。

恢复标志后也不能不加判断地反复进入同一个可中断等待，否则可能形成快速抛异常的循环。`volatile` 停止标志则不会自动唤醒正在等待的线程；使用哪一种停止协议，要与阻塞方式配套。[Thread 中断 API](<https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#interrupt()>)

不要使用强制停止来代替业务协议。任务在什么位置允许退出，是否已经产生不可撤销的外部效果，退出时需要清理哪些资源，都需要明确设计。

线程池的有序关闭通常按以下顺序进行：

1. 停止接纳新任务，调用 `shutdown()`。
2. 用 `awaitTermination()` 在限定时间内等待。
3. 超时后调用 `shutdownNow()` 请求中断，并处理返回的未开始任务。
4. 再次观察是否终止，对不能退出的任务报告问题。

`shutdownNow()` 不是强制杀死线程，也不会替业务恢复中间状态。通过 `submit()` 创建的排队 Future，若对应任务被移出队列，也不应假定它已经自动完成取消；持有 Future 的一方应明确处理取消与结果交付。[ExecutorService 关闭协议](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)

## 13. 调度、分治与监控分别解决什么问题

### 周期调度：计划基准不同

`scheduleAtFixedRate()` 按计划时间线推进，`scheduleWithFixedDelay()` 从上一次执行结束后计算下一次延迟。同一个周期任务的前后执行不会重叠，但执行过慢会造成延后。

例如，周期为 5 秒、每轮耗时 2 秒且资源充足时，固定速率大致在第 0、5、10 秒开始；固定延迟大致在第 0、7、14 秒开始。这是说明语义的理想时间线，不是调度精度承诺。

周期执行抛出异常后，后续执行会被抑制。采集任务应明确哪些异常要记录后继续，哪些需要停止，不能认为调度器会一直替自己重试。[ScheduledExecutorService API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)

### Fork/Join：拆分成本也要计算

Fork/Join 面向可拆分并合并结果的任务。`RecursiveTask` 返回结果，`RecursiveAction` 不返回结果；`fork()` 提交子任务，`join()` 等待结果。这里的 `join()` 与等待整个 Thread 结束的 `Thread.join()` 含义不同。

拆分必须有终止条件，子区间边界必须正确。阈值太小会让调度成本占比上升，阈值太大又可能无法充分利用并行。对阻塞 I/O 较多的任务，也不能直接照搬 CPU 分治的配置。[ForkJoinPool API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html)

### 监控：把等待时间纳入视野

只看活跃线程数，很难解释请求为什么慢。至少应结合以下信息：

| 观察项                       | 想回答的问题                           |
| ---------------------------- | -------------------------------------- |
| 活跃线程数、池大小、队列长度 | 是否接近执行容量，是否持续积压？       |
| 完成量、拒绝量、失败量       | 有多少工作真正完成，多少被拒绝或失败？ |
| 排队时间、执行时间、整体延迟 | 时间花在等待资源，还是执行任务？       |
| 下游超时与取消数量           | 本地吞吐是否超过依赖服务承载能力？     |

线程池的部分统计是近似值，不适合作为严格的业务账本。`beforeExecute()`、`afterExecute()` 可用于埋点，但通过 FutureTask 包装的任务失败可能不会直接出现在 `afterExecute()` 的 Throwable 参数中，还需要结合 Future 观察异常。[ThreadPoolExecutor 扩展与统计](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)

## 14. 从一个并发需求开始练习

面对“把这批查询并发执行”“限制下游调用数量”或“修复库存竞争”这样的需求，可以按同一个顺序分析：先写出共享状态的不变量，再确定执行容量和排队边界，随后定义成功、失败、超时、取消的处理方式，最后验证关闭和清理。

练习时可以修改前面的示例：把工作队列缩小到 1，观察拒绝如何暴露；在一个任务中主动抛异常，观察结果获取与取消路径；把批次等待预算缩短，观察超时后许可是否仍然归还。不要依靠一次日志顺序判断正确性，应检查库存、许可与任务结果是否满足事先写出的约束。

Java 并发 API 很多，但每个工具都只承担部分职责。把业务不变量、任务生命周期和资源边界写清楚，才能知道应该在哪里加锁、在哪里等待，以及在哪里停止。
