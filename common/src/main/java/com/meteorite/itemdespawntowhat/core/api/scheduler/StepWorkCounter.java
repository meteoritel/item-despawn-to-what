package com.meteorite.itemdespawntowhat.core.api.scheduler;

/**
 * 调度步骤的工作量回执窗口：调度器在执行任务步骤前后开窗与关窗，任务实现按本步真实完成的世界操作量上报。
 * 计价口径由窗口统一收集，因此不依赖任务的具体类型，同步执行与延迟执行两条路径都被同一份窗口覆盖。
 * 窗口按服务器线程隔离；窗口外的上报一律忽略，不产生任何副作用。
 */
public final class StepWorkCounter {

    // 窗口按线程隔离：调度步骤在服务器线程上串行执行，避免跨线程串账
    private static final ThreadLocal<Window> WINDOW = ThreadLocal.withInitial(Window::new);

    private StepWorkCounter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 打开本线程的工作量窗口，必须与 {@link #end()} 成对调用。
     * 用深度计数支持嵌套执行：只有最外层负责归零，内层上报仍计入最外层窗口，避免丢账或串账。
     */
    public static void begin() {
        Window window = WINDOW.get();
        window.depth++;
        if (window.depth == 1) {
            window.workUnits = 0;
        }
    }

    /**
     * 关闭本线程的工作量窗口。
     *
     * @return 最外层窗口关闭时返回本步累计的工作量；内层关闭或无窗口可关时返回 0
     */
    public static int end() {
        Window window = WINDOW.get();
        if (window.depth == 0) {
            return 0;
        }
        window.depth--;
        if (window.depth > 0) {
            return 0;
        }
        int total = window.workUnits;
        window.workUnits = 0;
        return total;
    }

    /**
     * 上报本步真正完成的世界操作量：每完成一次方块读写、一次实体写入，
     * 或一步完成整次世界操作的高代价动作时调用一次，按实际完成件数累计。
     * 不在窗口内时静默忽略，因此同步路径之外的零散调用不会影响计价。
     *
     * @param workUnits 本步完成的世界操作量，非正数忽略
     */
    public static void report(int workUnits) {
        if (workUnits <= 0) {
            return;
        }
        Window window = WINDOW.get();
        if (window.depth > 0) {
            window.workUnits += workUnits;
        }
    }

    // 单个服务器线程的窗口状态：depth 为 0 表示当前不在任何调度步骤内
    private static final class Window {
        private int depth;
        private int workUnits;
    }
}
