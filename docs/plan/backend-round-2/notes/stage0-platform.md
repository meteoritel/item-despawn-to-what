# 阶段0-A：平台销毁入口与实体状态持久化核实

- 任务：task-1（阶段0-A），调研人 platform-scout，阶段0 只读调研，未修改任何 Java/JSON/构建文件。
- 上游依据：`docs/plan/backend-round-2/PLAN.md`（尤其 28、85-94、201-215、262-268 行）、ADR `0001-conversion-commitment.md`、`0002-shared-server-budget.md`、`CONTEXT.md`。
- 事实来源：本地反编译 sources jar（下表），全部结论附行号；不确定的一律标注"未核实"。

## 0. 语料基准（行号含义）

| 代号 | jar 路径 | 说明 |
| --- | --- | --- |
| VAN | `common/build/moddev/artifacts/vanilla-1.21.1-20240808.144430-minecraft-sources.jar` | 原版 1.21.1 反编译源码，行号基准 A |
| NEO | `neoforge/build/moddev/artifacts/neoforge-21.1.218-minecraft-sources.jar` | NeoForge 21.1.218 patched 反编译源码，行号基准 B |
| FAB-MC | `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-a5fe06f283/1.21.1-loom.mappings.1_21_1.layered+hash.730628366-v2/minecraft-merged-a5fe06f283-1.21.1-loom.mappings.1_21_1.layered+hash.730628366-v2-sources.jar` | Fabric(Loom) 端 Minecraft 源码，行号基准 C |
| FAB-ATT | `.gradle/loom-cache/remapped_mods/loom_mappings_1_21_1_layered_hash_288375958_v2/net/fabricmc/fabric-api/fabric-data-attachment-api-v1/1.4.0+da19b51a19/fabric-data-attachment-api-v1-1.4.0+da19b51a19-sources.jar` | Fabric API 0.109.0+1.21.1 attachment 源码，行号基准 D |

注意：vanilla merged jar 不含 `data/`（JSON 条目数为 0）；datapack JSON 需读 `D:\system_default\desktop\cc_project\MCP\mc-developing-mcp\sources\1.21.1\minecraft\vanilla-client\data\...` 或走 mc-developing-mcp `read_asset`。

---

## 1. Vanilla ItemEntity.hurt 致死分支精确结构

### 结论
1. 致死分支在 `hurt` 的最后一个 else 内，`health <= 0` 成立后**只执行两条语句**：先 `this.getItem().onDestroyed(this);` 再 `this.discard();`，中间没有任何其他调用；此刻 `ItemStack`（`getItem()`）与 `DamageSource`（参数 `source`）**同时在手**。
2. 致死分支之前有 4 道提前 return 闸门：`isInvulnerableTo`、下界之星+爆炸标签、`canBeHurtBy`、`level().isClientSide`。保护逻辑必须作用于第 1 道闸门（或等价地在其之前拦截），否则掉落物已被判定免疫/不可伤害时仍会误判。
3. 因此"真实致死才转化"的判定点 = `health <= 0` 分支内部（推荐注入 `onDestroyed` 调用点），而不是 `hurt` 的 HEAD，也不是 `discard()`。

### 证据（VAN `net/minecraft/world/entity/item/ItemEntity.java`）
- 字段：`39 private static final int LIFETIME = 6000;`、`40 INFINITE_PICKUP_DELAY = 32767`、`41 INFINITE_LIFETIME = -32768`、`42 private int age;`、`44 private int health = 5;`
- `276-296`：
```java
277: public boolean hurt(DamageSource source, float amount) {
278:     if (this.isInvulnerableTo(source)) {
279:         return false;
280:     } else if (!this.getItem().isEmpty() && this.getItem().is(Items.NETHER_STAR) && source.is(DamageTypeTags.IS_EXPLOSION)) {
281:         return false;
282:     } else if (!this.getItem().canBeHurtBy(source)) {
283:         return false;
284:     } else if (this.level().isClientSide) {
285:         return true;
286:     } else {
287:         this.markHurt();
288:         this.health = (int)((float)this.health - amount);
289:         this.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
290:         if (this.health <= 0) {
291:             this.getItem().onDestroyed(this);
292:             this.discard();
293:         }
294:
295:         return true;
296:     }
```
- 自然到期（tick 内唯一到期 discard）：`191-193 if (!this.level().isClientSide && this.age >= 6000) { this.discard(); }`；同方法内第一处 discard 是 `126-127 if (this.getItem().isEmpty()) { this.discard(); }`。
- 存档：`299-301 public void addAdditionalSaveData(CompoundTag compound)`（写 `"Health"`, `"Age"`, `"PickupDelay"`, Thrower/Owner/`"Item"`）、`320-322 public void readAdditionalSaveData(CompoundTag compound)`（`Item` 为空则 discard）。

### 阶段3 实现要点（精确注入点）
- Fabric：`@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;)V"))`，回调里可拿到 `ItemEntity` 与（局部变量）`source`，用于分类与转化请求。
- NeoForge：同一位置但 `onDestroyed` 描述符不同（见 3.3），**两平台不可共用未验证描述符**（与 PLAN 4.4 一致；Mixin 编译/应用期若描述符错误会直接报错，阶段3 以报错为准校正）。
- 保护判定不要注入 `discard()`（tick 内也有 discard，ordinal 易错）。

---

## 2. 火 / 岩浆 / 仙人掌致死伤害归因 API（1.21.1 确切 API）

### 结论
1. 三类伤害按**具体 DamageType 键**判定，不要用 `DamageTypeTags.IS_FIRE`：
   - 火：`DamageTypes.ON_FIRE`（持续燃烧掉血的真实来源；`DamageTypes.IN_FIRE` 同属 is_fire 标签，可作兼容判断）
   - 岩浆：`DamageTypes.LAVA`
   - 仙人掌：`DamageTypes.CACTUS`
2. `is_fire` 标签含 `fireball` / `unattributed_fireball` / `campfire` / `hot_floor` / `lava`，用标签判定会把爆炸火球也保护/转化成"火"，属于错误归因。
3. 岩浆与火在标签层面重叠（lava ∈ is_fire）→ 判定顺序必须**先 LAVA 再火**，满足 PLAN 4.4 "岩浆与火重叠时先分岩浆"。
4. "离开岩浆后持续燃烧"致死：伤害源为 `onFire`（`Entity.baseTick` 每 20 tick 一次，且 `!isInLava()`）→ 归为火类别，与 `PLAN.md:28` 的契约一致。
5. 站在火方块里本身**不给** `in_fire` 直接伤害，只点燃（`setRemainingFireTicks+1`/`igniteForSeconds(8)`）；后续掉血来自 `onFire`。因此"火"判据以 ON_FIRE 为主。

### 证据
- API 面（VAN `net/minecraft/world/damagesource/DamageSource.java`）：`129-131 public boolean is(TagKey<DamageType> damageTypeKey)`、`133-135 public boolean is(ResourceKey<DamageType> damageTypeKey)`、`137-139 public DamageType type()`、`95-97 public String getMsgId()`（调试用）。
- 伤害类型常量（VAN `net/minecraft/world/damagesource/DamageTypes.java`）：`9 IN_FIRE("in_fire")`、`10 CAMPFIRE`、`12 ON_FIRE("on_fire")`、`13 LAVA("lava")`、`14 HOT_FLOOR`、`19 CACTUS`；注册 `58-68`。
- tag 成员（本地数据）：`data/minecraft/tags/damage_type/is_fire.json` = `[minecraft:in_fire, minecraft:campfire, minecraft:on_fire, minecraft:lava, minecraft:hot_floor, minecraft:unattributed_fireball, minecraft:fireball]`；`is_explosion.json` = `[fireworks, explosion, player_explosion, bad_respawn_point]`。
- 实际触发点：
  - 燃烧：VAN `net/minecraft/world/entity/Entity.java` `460-466 if (this.remainingFireTicks % 20 == 0 && !this.isInLava()) { this.hurt(this.damageSources().onFire(), 1.0F); } this.setRemainingFireTicks(this.remainingFireTicks - 1);`
  - 岩浆：VAN `Entity.java` `474-477 if (this.isInLava()) { this.lavaHurt(); this.fallDistance *= 0.5F; }`；`524-530 public void lavaHurt() { if (!this.fireImmune()) { this.igniteForSeconds(15.0F); if (this.hurt(this.damageSources().lava(), 4.0F)) { playSound(...); } } }`
  - 仙人掌：VAN `net/minecraft/world/level/block/CactusBlock.java` `113-116 protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) { entity.hurt(level.damageSources().cactus(), 1.0F); }`
  - 火方块：VAN `net/minecraft/world/level/block/BaseFireBlock.java` `131-137`（仅点燃）。
- 另注：`ItemEntity.fireImmune()`（VAN `:269-271`）= `this.getItem().has(DataComponents.FIRE_RESISTANT) || super.fireImmune()` → 带火焰抗性组件的掉落物本来就不吃火/岩浆伤害，模组保护状态与之叠加时属于冗余，不冲突。

---

## 3. NeoForge 致死销毁事件调查

### 3.1 结论
1. **NeoForge 1.21.1 不存在 `ItemDestroyedEvent`**（NEO 语料中该路径不存在；全文 search `ItemDestroyed` 命中 0）→ "真实致死销毁"在 NeoForge 同样必须靠 Mixin 注入 `ItemEntity.hurt`（或退一步只做事后感知，但那拿不到 DamageSource）。
2. 自然消失有官方事件：`ItemExpireEvent`，且项目已使用；它只服务端触发，`addExtraLife` 最多把寿命延长到 `Short.MAX_VALUE - 1`。
3. 保护（仅三类伤害免疫）有官方入口：`EntityInvulnerabilityCheckEvent`，在 `Entity#isInvulnerableTo` 中被触发并可改写结果；`ItemEntity.hurt` 首行就是 `isInvulnerableTo(source)`，因此**该事件确实能阻止掉落物扣血/进入致死分支**。代价：事件对全部实体触发，监听器必须自行过滤 `instanceof ItemEntity` 与伤害类型。
4. `EntityJoinLevelEvent` 可取消，取消后实体不会加入世界；但只在 `Level#addFreshEntity` 与 `PersistentEntitySectionManager#addNewEntity` 路径触发，且官方 javadoc 警告可能在 LevelChunk 升到 FULL 前触发（需延迟世界交互以免死锁）→ 用它阻止新产物入世界风险高，**不建议**作为返还交付的主路径。

### 3.2 事件签名（NEO 语料）
- `net/neoforged/neoforge/event/entity/item/ItemExpireEvent.java`：`public class ItemExpireEvent extends ItemEvent`；构造 `ItemExpireEvent(ItemEntity)`；`public int getExtraLife()` / `public void setExtraLife(int)` / `public void addExtraLife(int)`。
- `net/neoforged/neoforge/event/entity/EntityInvulnerabilityCheckEvent.java`：`public class EntityInvulnerabilityCheckEvent extends EntityEvent`；构造 `(Entity entity, DamageSource source, boolean isVanillaInvulnerable)`；`public void setInvulnerable(boolean)` / `public boolean isInvulnerable()` / `public DamageSource getSource()` / `public boolean getOriginalInvulnerability()`；javadoc：在 `Entity#isInvulnerableTo(DamageSource)` 中两侧都会触发，并警告**某些重写 isInvulnerableTo 的实体无法被改变**（ItemEntity 未重写该方法 → 可用）。
- `net/neoforged/neoforge/event/entity/EntityJoinLevelEvent.java`：`extends EntityEvent implements ICancellableEvent`；构造 `(Entity, Level)` 与 `(Entity, Level, boolean loadedFromDisk)`；`public Level getLevel()` / `public boolean loadedFromDisk()`；取消后"the entity will not be added to the level"。

### 3.3 链路证据
- 自然消失：NEO `ItemEntity.java` `202-209`：`if (!this.level().isClientSide && this.age >= lifespan) { this.lifespan = Mth.clamp(lifespan + EventHooks.onItemExpire(this), 0, Short.MAX_VALUE - 1); if (this.age >= lifespan) { this.discard(); } }`；`EventHooks.java:530-534 public static int onItemExpire(ItemEntity entity)`（内部 post `ItemExpireEvent`）。NeoForge 用 `public int lifespan`（`:55`）替代原版 `6000` 常量，并多写 `"Lifespan"` NBT（`:323`/`:347-349`）。
- 保护：NEO `Entity.java` `2681-2687` 的 `isInvulnerableTo` 末尾 `return CommonHooks.isEntityInvulnerableTo(this, source, isVanillaInvulnerable);`；NEO `common/CommonHooks.java:281-283 public static boolean isEntityInvulnerableTo(Entity entity, DamageSource source, boolean isInvul) { return NeoForge.EVENT_BUS.post(new EntityInvulnerabilityCheckEvent(entity, source, isInvul)).isInvulnerable(); }`；NEO `ItemEntity.java:296-298` 首行 `if (this.isInvulnerableTo(source)) { return false; }`。
- 致死分支：NEO `ItemEntity.java` `305-315`，`309-312 if (this.health <= 0) { this.getItem().onDestroyed(this, source); this.discard(); }` → 与原版唯一差异是 `onDestroyed` 多带 DamageSource；调用点是 `net.neoforged.neoforge.common.extensions.IItemStackExtension#onDestroyed(ItemEntity, DamageSource)`（NEO `ItemStack.java:1108-1118` 保留了 @Deprecated 的原版签名重载并转发）。**INVOKE 的 owner 是接口还是 ItemStack 需阶段3 编译期确认**。

### 3.4 项目现状（`neoforge/src/main/java/com/meteorite/itemdespawntowhat/runtime/RuleRuntimeEvents.java`，122 行）
- `@EventBusSubscriber(modid = Constants.MOD_ID)`；已订阅：`ServerStartedEvent`→`RuleRuntimeHost.start`；`ServerStoppingEvent`→`DebugSessionManager.shutdown`+`RuleRuntimeHost.shutdown`；`OnDatapackSyncEvent`→reload；`EntityJoinLevelEvent`→`onItemAdded(ServerLevel, ItemEntity)`；`LivingDropsEvent`→`event.getDrops().forEach(item -> item.addTag(Constants.CHECK_LOCK_TAG))`；`EntityLeaveLevelEvent`→`onItemRemoved`；`ItemExpireEvent`→`deferNaturalExpiry` 成功则 `event.addExtraLife(1)`，并在 `item.getAge() >= 32765` 时 `item.setExtendedLifetime()`；`LevelTickEvent.Post`→`tickLevel`；`ServerTickEvent.Post`→DebugSessionManager.tick + 每 20 tick expireIdle；`LevelEvent.Load/Unload`。
- NeoForge 端**当前没有启用 Mixin**（`neoforge/src/main/resources` 下无 mixins.json，`ItemDespawnToWhat` 只有空 @Mod 构造）→ 若阶段3 需要在 NeoForge 做"禁止带状态合并"，必须先补 Mixin 配置（见末节决策项）。

---

## 4. Fabric 端 hurt 等价注入点与现有 Mixin 现状

### 4.1 结论
1. Fabric 侧 Minecraft 类与**原版结构一致**（Fabric Loader 不 patch 原版类，只通过 Mixin）；实测 FAB-MC 的 `ItemEntity.hurt` 致死分支与 VAN 完全对应。
2. Fabric API 0.109.0+1.21.1 **没有**物品销毁/到期事件（对其 sources jar 全文件扫描 `ItemExpire|ItemDestroyed|onDestroyed` 命中 0），也**没有** `EntityInvulnerabilityCheckEvent` 的等价物 → Fabric 端的"保护"与"致死入口"**都必须用 Mixin**，不存在纯事件方案。
3. Fabric 端 tick 内 `discard()` 有两处，第二处才是自然到期，现有 `ItemEntityMixin` 的 `ordinal = 1` 正确。

### 4.2 证据（FAB-MC `net/minecraft/world/entity/item/ItemEntity.java`，共 473 行）
- `119-122 public void tick() { if (this.getItem().isEmpty()) { this.discard(); } else { ... }`（第一处 discard）
- `184-185 if (!this.level().isClientSide && this.age >= 6000) { this.discard(); }`（第二处 = 自然到期）
- `272 public boolean hurt(DamageSource source, float amount)`；`285-287 if (this.health <= 0) { this.getItem().onDestroyed(this); this.discard(); }`
- 其余 discard 位置：`:262`（merge 内 originEntity.discard）、`:337`/`:350`（存档读写的异常路径）→ 说明 `@Redirect(discard ordinal=1)` 只落在 tick 内第二处，安全。

### 4.3 项目现有 Fabric Mixin（全部已读）
- `fabric/src/main/java/com/meteorite/itemdespawntowhat/mixin/ItemEntityMixin.java`（19 行）：`@Mixin(ItemEntity.class)`；`@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V", ordinal = 1))` → `if (!(item.level() instanceof ServerLevel level) || !RuleRuntimeHost.deferNaturalExpiry(level, item)) { item.discard(); }`。
- `fabric/src/main/java/com/meteorite/itemdespawntowhat/mixin/EntityMixin.java`（23 行）：`@Mixin(Entity.class)` + `@Redirect(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))` → `PlayerDeathDrops.mark(this, item)`。
- `fabric/src/main/java/com/meteorite/itemdespawntowhat/mixin/PlayerMixin.java`（21 行）：`@Mixin(Player.class)` + `@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"))` → `PlayerDeathDrops.mark`。
- 配置：仅 `fabric/src/main/resources/itemdespawntowhat.mixins.json`。

### 4.4 阶段3 建议（Fabric）
- 保护：`@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)`，当实体状态含（临时/永久）保护且伤害属于三类时 `ci.setReturnValue(false)`。语义与 NeoForge 的 `setInvulnerable(true)` 等价（都在扣血前返回 false：不 markHurt、不扣血、不进致死分支）。
- 致死：`@Inject(method = "hurt(...)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;)V"))`（此处可拿 `source`），把"真实致死"事件交给 common；注意不要重复调用 `discard()`。
- 判定必须"按最终致死伤害归因"：HEAD 里只看 `source` 分类，不得用"是否接触危险源/是否在燃烧"来判断（PLAN 4.4）。

---

## 5. 实体层自定义持久状态与合并逻辑

### 5.1 结论（状态存取）
1. **NeoForge 首选**：`Entity#getPersistentData()` 返回可写 `CompoundTag`，随实体存档序列化为 `"NeoForgeData"` → 平台层直接读写，**无需 Mixin**，也无需改 `ItemEntity` 的存档方法。
2. **Fabric 首选**：Fabric Data Attachment API v1（`AttachmentRegistry.createPersistent(ResourceLocation, Codec)` 或 `create(id, builder -> builder.persistent(codec))`）挂在 `Entity`（`AttachmentTarget`）上，由 fabric 的 `AttachmentTargetsMixin` 把 attachment 接入实体 NBT 读写 → 自动持久化，**无需自己写存档 Mixin**。
3. 通用兜底（两平台皆可但更重）：Mixin `ItemEntity.addAdditionalSaveData`/`readAdditionalSaveData`（VAN `:299-301`/`:320-322`；NEO `:318-320`/`:340-342`）自写 tag。
4. 纯布尔状态（永久保护 / 永久禁转）也可用实体 Tags 表达：`getTags()/addTag(String)/removeTag(String)`，随 `Entity.saveWithoutId` 的 `"Tags"` 保存/恢复，上限 1024 条。但 **Tags 无法表达"冷却到期刻"（long）** → 冷却必须用 persistentData/attachment；建议状态统一走 persistentData/attachment，避免两套机制。
5. 状态结构建议放 common（平台层负责"从实体读出 → 转 common 结构 / 写回"），因为 common 不得 import 平台类：需要在 `IPlatformHelper`（现有 27 行，仅 5 个实现方法 + 2 个 default）增加默认抛 `UnsupportedOperationException` 的方法，由两平台实现（与现有 `sendToPlayer` 模式一致）。
6. 项目现状：全仓 `*.java` grep `getPersistentData|SavedData|addAdditionalSaveData|readAdditionalSaveData|getDataStorage` → **0 命中**。当前唯一的实体级持久痕迹是物品/实体 tag 字符串常量 `Constants.CHECK_LOCK_TAG`、`Constants.CONVERTED_TAG`（`common/src/main/java/com/meteorite/itemdespawntowhat/Constants.java`，13 行）→ 全部状态能力都是新增。

### 5.2 证据
- NeoForge：NEO `Entity.java` `3709-3717 private CompoundTag persistentData;` + `public CompoundTag getPersistentData()`（javadoc：`Neo: Injected ability to store arbitrary nbt onto entities`）；`1797 if (persistentData != null) compound.put("NeoForgeData", persistentData.copy());`；`1882 if (compound.contains("NeoForgeData", 10)) persistentData = compound.getCompound("NeoForgeData");`；NeoForge 自家 attachment 序列化 `1795-1796 serializeAttachments(registryAccess())` / `1883 deserializeAttachments(...)`，类 `net/neoforged/neoforge/attachment/AttachmentType.java`（299 行）。
- Fabric：FAB-ATT `net/fabricmc/fabric/api/attachment/v1/AttachmentRegistry.java` `55-60 public static <A> AttachmentType<A> create(ResourceLocation id, Consumer<Builder<A>> consumer)`；javadoc 列出的便捷方法 `create(ResourceLocation)`（既不持久也不自动初始化）、`createDefaulted(ResourceLocation, Supplier)`、`createPersistent(ResourceLocation, Codec)`；`AttachmentTargetsMixin.java` `41-42 @Mixin({BlockEntity.class, Entity.class, Level.class, ChunkAccess.class}) abstract class AttachmentTargetsMixin implements AttachmentTargetImpl`，`87-89 fabric_writeAttachmentsToNbt(CompoundTag, HolderLookup.Provider)`、`92-94 fabric_readAttachmentsFromNbt(...)`。
- 实体 Tags：VAN/NEO `Entity.java` `321-323 getTags()`、`325-327 addTag(String)`、`329-331 removeTag(String)`；`saveWithoutId` `1785-1792`（`"Tags"` 写为 StringTag 列表）、load `1884-1891`（读 `"Tags"`，`Math.min(size, 1024)`）。

### 5.3 合并逻辑：精确签名与改动落点
两平台结构完全一致：
| 方法 | VAN 行 | NEO 行 | 签名 |
| --- | --- | --- | --- |
| 合并节流 | 175 | 186 | `if (this.tickCount % i == 0 && !this.level().isClientSide && this.isMergable()) { this.mergeWithNeighbours(); }`（i = flag ? 2 : 40） |
| `mergeWithNeighbours` | 212 | 231 | `private void mergeWithNeighbours()` |
| `isMergable` | 226-229 | 245-248 | `private boolean isMergable()`（`isAlive() && pickupDelay != 32767 && age != -32768 && age < 6000 && count < maxStackSize`） |
| `tryToMerge` | 231-236 | 250-255 | `private void tryToMerge(ItemEntity itemEntity)` ← **注意是 private void，不是 public boolean** |
| `areMergable` | 243-244 | 262-263 | `public static boolean areMergable(ItemStack destinationStack, ItemStack originStack)` |
| `merge`(3 参) | 254-257 | 273-276 | `private static void merge(ItemEntity destinationEntity, ItemStack destinationStack, ItemStack originStack)` |
| `merge`(4 参) | 259-262 | 278-285 | `private static void merge(ItemEntity destinationEntity, ItemStack destinationStack, ItemEntity originEntity, ItemStack originStack)`，体内 `destinationEntity.pickupDelay = Math.max(...)`、`destinationEntity.age = Math.min(...)`，NEO 另有 `if (originStack.isEmpty()) originEntity.discard();` |

结论：
1. **禁止带状态合并**的最小落点 = 注入 `private void tryToMerge(ItemEntity)` 的 `@At("HEAD")`，用 `@Inject(..., cancellable = true)` + `CallbackInfo` 直接 return（返回 void，无需返回值处理）；当双方任一含保护/冷却/禁转状态且不兼容时跳过合并。`isMergable()` 只描述"本实体可合并"，无法表达两实体状态兼容；`areMergable(ItemStack, ItemStack)` 是 static 且只有物品栈、没有实体上下文 → 都不可取。
2. **临时状态取较晚结束时间**的落点 = 注入 `private static void merge(ItemEntity, ItemStack, ItemEntity, ItemStack)` 的 `@At("TAIL")`（两平台），把目标实体的临时保护/冷却到期刻更新为 `max(dest, origin)`；原版已有 `pickupDelay=max / age=min` 的先例可参照。
3. Mixin 选择器需写成描述符形式（阶段3 直接用）：
   - `tryToMerge(Lnet/minecraft/world/entity/item/ItemEntity;)V`
   - `merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V`
4. 兼容性判定口径（"不兼容"的精确定义）需 lead 拍板，见末节。

---

## 6. 计时基准与服务端持久对象入口

### 6.1 结论
1. **计时基准用 `ServerLevel#getGameTime()`**（`Level.getGameTime()` → `levelData.getGameTime()`）：该值持久于 `level.dat` 的 `"Time"`，随 world tick 递增；区块卸载/实体不 tick 期间照常推进；停服即停；重启从存档继续（不归零）→ 完整满足 PLAN 4.5"卸载不暂停、停服不推进、重启恢复"。
2. **不要**用 `System.currentTimeMillis()`（不受停服/存档约束）、`ItemEntity.age`（会被 `setExtendedLifetime()/setUnlimitedLifetime()` 写成 -6000/-32768，且语义是"寿命剩余"）、`entity.tickCount`（只在实体存在时增长）。
3. 若未来需要跨维度统一计数器，用 SavedData（`ServerLevel#getDataStorage().computeIfAbsent(factory, id)`）；本需求用 `getGameTime()` 即可，无需新增 SavedData。

### 6.2 证据
- VAN `net/minecraft/world/level/Level.java` `916-918 public long getGameTime() { return this.levelData.getGameTime(); }`；`920-922 getDayTime()`。
- VAN `net/minecraft/server/level/ServerLevel.java` `205 private final boolean tickTime;`、`233 this.tickTime = tickTime;`、`235 this.serverLevelData = serverLevelData;`；`438-442 protected void tickTime() { if (this.tickTime) { long i = this.levelData.getGameTime() + 1L; this.serverLevelData.setGameTime(i); this.serverLevelData.getScheduledEvents().tick(this.server, i); ... } }`。
- VAN `net/minecraft/world/level/storage/PrimaryLevelData.java` `53-54`（gameTime/dayTime 字段）；读档 `171 long i = tag.get("Time").asLong(0L);`、`178 tag.get("DayTime").asLong(i)`；存档 `240 nbt.putLong("Time", this.gameTime);`、`241 nbt.putLong("DayTime", this.dayTime);`。
- 服务端持久对象入口：VAN `ServerLevel.java` `1326-1328 public DimensionDataStorage getDataStorage() { return this.getChunkSource().getDataStorage(); }`；用法 `:271 this.getDataStorage().computeIfAbsent(Raids.factory(this), ...)`、`:299 computeIfAbsent(RandomSequences.factory(i), "random_sequences")`、`:821 this.getChunkSource().getDataStorage().save()`；`net/minecraft/world/level/saveddata/SavedData.java` `15 public abstract class SavedData`、`19 public abstract CompoundTag save(CompoundTag tag, HolderLookup.Provider registries);`。
- 实体侧反例：VAN `ItemEntity.java` `428-430 setUnlimitedLifetime()`（`age = -32768`）、`432-434 setExtendedLifetime()`（`age = -6000`）；NeoForge 用 `lifespan` + `"Lifespan"` NBT。

---

## 7. 阶段3 实现速查（精确签名/描述符）

| 用途 | 平台 | 目标 |
| --- | --- | --- |
| 致死入口 | Fabric/Vanilla | `hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z` + `INVOKE Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;)V` |
| 致死入口 | NeoForge | `hurt(...)Z` + `onDestroyed(ItemEntity, DamageSource)`（owner 待编译期确认，见 8.4） |
| 保护 | NeoForge | `EntityInvulnerabilityCheckEvent#setInvulnerable(boolean)`，过滤 `instanceof ItemEntity` + 三类伤害 |
| 保护 | Fabric | `@Inject(method = "hurt(...)Z", at = @At("HEAD"), cancellable = true)` + `ci.setReturnValue(false)` |
| 自然消失 | NeoForge | `ItemExpireEvent`（已用；`addExtraLife` 上限 `Short.MAX_VALUE-1`） |
| 自然消失 | Fabric | tick 内第二处 `discard()`（`ordinal = 1`，已用） |
| 禁止带状态合并 | 两平台 | `tryToMerge(Lnet/minecraft/world/entity/item/ItemEntity;)V` HEAD cancellable |
| 临时状态并集 | 两平台 | `merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V` TAIL |
| 状态存取 | NeoForge | `Entity#getPersistentData()`（NBT 键 `"NeoForgeData"`） |
| 状态存取 | Fabric | `AttachmentRegistry.createPersistent(ResourceLocation, Codec)` |
| 计时 | 两平台 | `ServerLevel#getGameTime()`（持久于 `level.dat` `"Time"`） |

---

## 8. 未核实项

1. `DamageTypes.IN_FIRE` 在 1.21.1 的实际触发者：VAN 的 `FireBlock`/`BaseFireBlock`/`CampfireBlock`/`Entity` 中均未见 `damageSources().inFire()` 调用（火方块只点燃）→ "火"判据建议以 `ON_FIRE` 为准，`IN_FIRE` 是否需要兼容判断待运行时验证。
2. `ServerLevel` 构造参数 `tickTime` 的实参来源（未读 `MinecraftServer` 的建维度路径）→ 仅影响"是否存在不推进 gameTime 的维度"，不影响 `getGameTime()` 的持久语义。
3. Fabric `AttachmentRegistry.Builder` 上 `persistent(...)` 的精确方法签名（javadoc 只列出了 `createPersistent(ResourceLocation, Codec)`；`Builder` 接口成员未逐行读）→ 阶段3 直接用 `createPersistent` 可绕开。
4. NeoForge `onDestroyed(ItemEntity, DamageSource)` 调用点的 INVOKE owner（`ItemStack` vs `IItemStackExtension` 接口）未从字节码确认 → 阶段3 以 Mixin 应用报错为准校正描述符。
5. Fabric 端是否还有其他（Fabric API 之外）可拦截实体受伤/销毁的 loader 级钩子：未穷举，当前按"无"处理。
6. `ItemEntity.hurt` 之外的销毁路径（如 `ItemStack` 被直接清空、`/kill`、区块/实体移除）是否也应算"销毁入口"未做穷举；PLAN 4.4 只要求火/岩浆/仙人掌的致死路径 + 自然消失。
7. 原版 `canBeHurtBy`/`DataComponents.FIRE_RESISTANT` 与模组保护状态叠加的双重免疫语义未做交互分析（判断为冗余，不冲突）。
8. NeoForge `LivingDropsEvent` 写 `CHECK_LOCK_TAG` 与"返还物永久禁转"两套标记是否会互相干扰未核实。
9. `docs/plan/backend-round-2/PLAN.md` 3 节（124-130 行）声称的既有核实与实际代码行号存在少量出入（该节说 `hurt` 致死分支在 268-289、`onDestroyed` 在 283），本笔记以实际反编译源码为准（`276-296`，`onDestroyed` 在 291 行）→ 后续引用应以本笔记行号为准。

---

## 9. 需要 lead 决策项

1. **"火"类别的 DamageType 精确集合**：建议 `{ON_FIRE, IN_FIRE}`；是否把 `CAMPFIRE`/`HOT_FLOOR` 也归为火？（PLAN 只写"火/岩浆/仙人掌"，未枚举 DamageType。）
2. **保护状态的粒度**：是否需要在状态里存"保护掩码"（支持只防火/只防岩浆等），还是恒为"三种伤害全防"。状态结构一旦上线再扩展要考虑 NBT 迁移。
3. **合并兼容性口径**："不兼容"的判定规则需要明确：永久保护/永久禁转标志不同是否即不兼容；临时保护到期刻不同、临时冷却不同是否也算不兼容；以及兼容合并时"临时状态取较晚结束时间"是否对保护与冷却同时生效。
4. **拾取后再丢出的状态继承**：PLAN 2.6 说拾取后重丢不继承"永久"状态；临时冷却在拾取时是清空还是随物品保留？返还物拾取后原实体的永久禁转是否随之消失？
5. **状态结构归属**：建议 common 定义状态 record + 在 `IPlatformHelper` 增加存取方法（两平台各自实现）；是否接受为 common 增加平台接口方法（会改动 `common/.../platform/services/IPlatformHelper.java` 与两平台实现类）。
6. **NeoForge 是否启用 Mixin**：合并相关的两处注入（`tryToMerge`、`merge`）在两平台都是 Mixin；NeoForge 端当前无 mixins.json 与 build.gradle Mixin 配置 → 需要 lead 确认阶段3 是否新增（涉及构建文件改动，需在阶段3 计划中显式列出）。
7. **状态 NBT/Attachment 的 key 与版本号**：建议 `itemdespawntowhat:state` + 内部 `"v"` 版本字段，便于后续迁移；是否现在预留。
8. **永久保护是否豁免自然消失**：PLAN 说返还物仍可自然消失（不触发转化）→ 若不豁免，则 `ItemExpireEvent`/`deferNaturalExpiry` 无需为永久保护状态做例外；请确认按"不豁免"实现。
