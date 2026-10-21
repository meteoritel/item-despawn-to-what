package com.meteorite.itemdespawntowhat.core.api;

import com.meteorite.itemdespawntowhat.core.api.scheduler.RunnableTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ScheduledTaskHandle;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.TaskQueue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 运行时观测接缝：运行时、调度与效果执行通过这些方法上报观测点与排期请求，不再直接依赖调试子系统。
 * 本接口只暴露生产侧真正需要的能力：是否启用观测、候选结果上报、输出物装饰、以及把任务排入调度队列；
 * 场景索引与排期追踪等调试语义全部由实现方（调试子系统）自行持有，接缝本身不保存任何状态。
 *
 * <p>未注入实现时使用 {@link #NONE}，语义等价于「未启用调试」：判定类方法返回安全默认值，
 * 上报类方法是空操作，排期方法直接入队而不做任何场景追踪，因此不会引入可观测的行为与开销差异。
 * 装配方（平台运行时）在服务端装配阶段调用 {@link #install(RuntimeObserver)} 注入实现。
 */
public interface RuntimeObserver {

    /** 未注入任何实现时的空观测器：等价于「未启用调试」。 */
    RuntimeObserver NONE = new RuntimeObserver() {
    };

    /**
     * 返回当前生效的观测实现；未注入时返回 {@link #NONE}。
     * 该查询在每次观测点都会发生，调用方据此在未启用时跳过参数构造，避免无谓的对象分配。
     *
     * @return 当前观测实现，永不为 {@code null}
     */
    static RuntimeObserver current() {
        return Holder.CURRENT;
    }

    /**
     * 注入观测实现；传入 {@code null} 等价于恢复为 {@link #NONE}。
     * 只应由装配方在服务端装配阶段调用一次，运行期不再替换。
     *
     * @param observer 观测实现，可为 {@code null}
     */
    static void install(RuntimeObserver observer) {
        Holder.CURRENT = observer == null ? NONE : observer;
    }

    /**
     * 观测是否处于启用状态。
     * 用于在构造观测参数之前做廉价短路：未启用时调用方不应构造任何额外对象。
     *
     * @return 启用观测返回 {@code true}
     */
    default boolean enabled() {
        return false;
    }

    /**
     * 判断服务端当前是否不存在任何观测场景：不存在场景时调用方可以提前放弃追踪。
     *
     * @param server 当前服务端
     * @return 无激活场景返回 {@code true}
     */
    default boolean inactive(MinecraftServer server) {
        return true;
    }

    /**
     * 上报一个观测事件。事件名与字段的含义由实现方解释，接缝不做校验也不做限流。
     *
     * @param source 观测来源实体
     * @param event 事件名
     * @param fields 事件字段，按「名、值」成对出现
     */
    default void observe(ItemEntity source, String event, Object... fields) {
    }

    /**
     * 判断该实体是否允许输出运行时日志。
     * 实现的典型语义是「基准测试场景下静默」，未注入实现时一律允许。
     *
     * @param source 观测来源实体
     * @return 允许输出日志返回 {@code true}
     */
    default boolean allowsRuntimeLogging(ItemEntity source) {
        return true;
    }

    /**
     * 上报一次转换已发生，供实现方更新场景进度。
     *
     * @param source 观测来源实体
     * @param ruleId 命中的规则标识
     * @param rounds 本次预计结算的组数
     */
    default void converted(ItemEntity source, String ruleId, int rounds) {
    }

    /**
     * 返回实现方为该实体指定的候选结果覆盖。
     * 返回值刻意声明为未知元素类型：候选结果属于模型层类型，契约层不得依赖模型层。
     * 调用方按实际语义把元素收窄为规则类型，且只在返回非 {@code null} 时覆盖正常候选计算。
     *
     * @param item 待检查的掉落物
     * @return 覆盖用的候选列表；返回 {@code null} 表示按正常索引计算
     */
    default @Nullable List<?> candidates(ItemEntity item) {
        return null;
    }

    /**
     * 通知实现方「即将产出物品」，由实现方决定是否把该产出物纳入场景追踪。
     *
     * @param context 产出该物品的效果上下文
     * @param entity 产出物实体
     */
    default void prepareOutput(EffectContext context, Entity entity) {
    }

    /**
     * 通知实现方「产出物已加入世界」，用于场景统计与追踪绑定。
     *
     * @param context 产出该物品的效果上下文
     * @param entity 产出物实体
     */
    default void outputAdded(EffectContext context, Entity entity) {
    }

    /**
     * 上报「规则重载进入应用阶段」。默认空实现；实现方只允许以抛出异常表达注入的重载失败，
     * 不得改变调用方对索引替换结果的处理。调用方在替换规则索引之后、回扫之前调用，
     * 并把异常与其他重载异常一样按失败收口。
     *
     * @param server 当前服务端
     */
    default void reloadApplying(MinecraftServer server) {
    }

    /**
     * 把一个延迟动作排入调度队列。
     * 默认实现直接入队；实现方可以包裹它以追踪场景创建的任务。
     *
     * @param source 触发该排期的实体
     * @param queue 目标调度队列
     * @param realmKey realm 标识，通常是维度键
     * @param kind 任务分类
     * @param dueTick 绝对到期游戏刻
     * @param name 任务追踪名
     * @param action 到期执行的动作
     * @return 已入队任务的句柄
     */
    default ScheduledTaskHandle schedule(ItemEntity source, TaskQueue queue, Object realmKey, ServerTaskKind kind,
                                        long dueTick, String name, Runnable action) {
        return queue.scheduleAt(realmKey, new RunnableTask(kind, name, action), dueTick);
    }

    /**
     * 把一个已构建的任务排入调度队列。
     * 默认实现直接入队；实现方可以包裹它以追踪场景创建的任务。
     *
     * @param source 触发该排期的实体
     * @param queue 目标调度队列
     * @param realmKey realm 标识，通常是维度键
     * @param dueTick 绝对到期游戏刻
     * @param task 待执行任务
     * @return 已入队任务的句柄
     */
    default ScheduledTaskHandle schedule(ItemEntity source, TaskQueue queue, Object realmKey, long dueTick,
                                        ServerTask task) {
        return queue.scheduleAt(realmKey, task, dueTick);
    }

    /** 当前观测实现的持有者：接口字段只能是常量，可变引用必须放在嵌套类中。 */
    final class Holder {

        // 装配期写入、运行期读取；volatile 保证替换对已启动的服务端线程立即可见
        static volatile RuntimeObserver CURRENT = NONE;

        private Holder() {
        }
    }
}
