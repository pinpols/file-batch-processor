package com.example.filebatchprocessor.observability;

import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.MDC;

/** MDC 上下文工具，保证批处理 Job 和任务执行结束后清理链路字段。 */
public final class MdcContext {

    private MdcContext() {}

    /** 在指定 MDC 上下文中执行逻辑，结束后自动清理写入的键。 */
    public static <T> T withContext(Map<String, String> context, Supplier<T> action) {
        if (context == null || context.isEmpty()) {
            return action.get();
        }
        try {
            context.forEach(MDC::put);
            return action.get();
        } finally {
            context.keySet().forEach(MDC::remove);
        }
    }

    /** Runnable 版本的 MDC 包裹执行。 */
    public static void withContext(Map<String, String> context, Runnable action) {
        withContext(context, () -> {
            action.run();
            return null;
        });
    }

    /** 写入单个 MDC 键值。 */
    public static void put(String key, String value) {
        if (key != null && value != null) {
            MDC.put(key, value);
        }
    }

    /** 移除单个 MDC 键。 */
    public static void remove(String key) {
        if (key != null) {
            MDC.remove(key);
        }
    }

    /** 清空当前线程的 MDC。 */
    public static void clear() {
        MDC.clear();
    }
}
