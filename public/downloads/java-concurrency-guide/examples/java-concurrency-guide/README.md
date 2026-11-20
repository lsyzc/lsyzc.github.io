# 并发讲义配套示例

正文：[Java并发编程逐章详解.md](../../Java并发编程逐章详解.md)。需要 JDK 17 或能以 Java 17 目标版本编译的 JDK，无第三方依赖。

在工作区根目录执行，编译产物存到临时目录：

```sh
mkdir -p /tmp/java-concurrency-guide-classes
javac --release 17 -Xlint:all -d /tmp/java-concurrency-guide-classes examples/java-concurrency-guide/*.java
java -cp /tmp/java-concurrency-guide-classes InterruptDemo
```

替换最后一行的类名，即可运行其他完整示例。

| 类名                 | 对应章节 | 正常输出重点                                        |
| -------------------- | -------- | --------------------------------------------------- |
| InterruptDemo        | 6        | 恢复中断、执行清理、线程结束                        |
| FutureCancelDemo     | 11       | Future 已取消时，任务主体尚未结束                   |
| CompletionDemo       | 15       | fast 在 slow 前返回                                 |
| ForkJoinDemo         | 17       | 5050                                                |
| AbaDemo              | 20       | plain=true，versioned=false                         |
| WarehouseDemo        | 30       | stock=0                                             |
| InheritedContextDemo | 33       | 两次都为 request-A                                  |
| BarrierDemo          | 34       | 两轮屏障完成                                        |
| PartialExamples      | 多节     | 正文 14 段局部示例的编译容器，无 main               |
| VerifyExamples       | 多节     | 检查局部示例关键不变量，末行 fragment checks passed |

代码从正文提取；VerifyExamples 是额外的运行检查程序。TTL 代码未纳入本目录，正文已说明其外部依赖和未运行状态。

本机使用 OpenJDK 17.0.20.1 编译无警告，8 个完整示例与 VerifyExamples 各运行 3 次通过。验证用于检查教学示例行为，不是性能测试，也不穷尽所有并发交错。
