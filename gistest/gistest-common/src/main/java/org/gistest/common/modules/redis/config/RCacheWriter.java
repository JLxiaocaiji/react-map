package org.gistest.common.modules.redis.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
public class RCacheWriter implements RedisCacheWriter {

    private final RedisConnectionFactory connectionFactory;

    private final Duration defaultTtl;

    private ThreadLocal<String> currentLockValue = new ThreadLocal<>();

    // Lua 原子解锁脚本
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "   return redis.call('del', KEYS[1]); " +
                    "else " +
                    "   return 0; " +
                    "end";

    public RCacheWriter(RedisConnectionFactory connectionFactory) {
        this(connectionFactory, Duration.ZERO);
    }

    public RCacheWriter(RedisConnectionFactory connectionFactory, Duration defaultTtl) {
        Assert.notNull(connectionFactory, "connectionFactory must not be null");
        Assert.notNull(defaultTtl, "Default TTL must not be null");
        this.connectionFactory = connectionFactory;
        this.defaultTtl = defaultTtl;
    }

    /**
     * Redis 缓存写入
     * @param name 缓存的名称
     * @param key Redis 中实际存储的 key（byte[]
     * @param value
     * @param ttl
     */
    @Override
    public void put(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
        // 防止空值导致 Redis 请求失败
        Assert.notNull(name, "Cache name must not be null!");
        Assert.notNull(key, "Cache key must not be null!");
        Assert.notNull(value, "Cache value must not be null!");

        this.execute(name, connection -> {
            // 判断ttl（Time-To-Live，存活时间）是否有效（通常规则：ttl > 0或ttl != Duration.ZERO
            // 若有ttl, 对于key: value, 没有则新增，有则覆盖
            if (shouldExpireWithin(ttl)) {
                // Expiration.from: 将Duration类型的 TTL 转换为 Redis 支持的过期时间（毫秒级）
                // upsert = update or insert,没有则新增，有则覆盖
                connection.set(key, value, Expiration.from(ttl.toMillis(), TimeUnit.MILLISECONDS), RedisStringCommands.SetOption.upsert());
            } else {
                // 若无ttl, 对于key: value, 永不过期
                connection.set(key, value);
            }
            return "OK";
        });
    }

    @Override
    public byte[] get(String name, byte[] key) {
        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");
        return (byte[]) this.execute(name, (connection) -> {
            return connection.get(key);
        });
    }

    /**
     * 不存在则写入
     * @param name
     * @param key
     * @param value
     * @param ttl
     * @return
     */
    @Override
    public byte[] putIfAbsent(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(value, "Value must not be null!");
        return (byte[]) this.execute(name, (connection) -> {
            if (this.isLockingCacheWriter()) {
                this.doLock(name, connection);
            }
            try {
                boolean put;
                if (shouldExpireWithin(ttl)) {
                    // 带TTL的原子写入：SET key value PX ttl NX
                    put = connection.set(key, value, Expiration.from(ttl.toMillis(), TimeUnit.MILLISECONDS), RedisStringCommands.SetOption.ifAbsent());
                } else {
                    // 无TTL的原子写入：SETNX key value
                    put = connection.setNX(key, value);
                }

                if (!put) {
                    byte[] val1 = connection.get(key);
                    return val1;
                }

                return null;
            } finally {
                if (this.isLockingCacheWriter()) {
                    this.doUnlock(name, connection);
                }
            }
        });
    }


    @Override
    public void remove(String name, byte[] key) {
        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");
        String keyString = new String(key);
        log.info("redis remove key:" + keyString);
        String keyIsAll = "*";

        if (keyString != null && keyString.endsWith(keyIsAll)) {
            execute(name, connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(key).count(100000).build();
                Set<byte[]> keys = new HashSet<>();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(cursor.next());
                    }
                }
                int delNum = 0;
                for (byte[] keyByte : keys) {
                    delNum += connection.del(keyByte);
                }
                return delNum;
            });
        } else {
            this.execute(name, (connection) -> {
                return connection.del(new byte[][]{key});
            });
        }
    }

    /**
     *
     * @param name
     * @param pattern
     */
    @Override
    public void clean(String name, byte[] pattern) {
        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(pattern, "Pattern must not be null!");
        this.execute(name, (connection) -> {
            boolean wasLocked = false;

            try {
                if (this.isLockingCacheWriter()) {
                    this.doLock(name, connection);
                    wasLocked = true;
                }
                // 如果不设置count数，默认会返回10个
                // SCAN是渐进式迭代命令，分批返回匹配的键（非阻塞），适合大数据量场景
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100000).build();
                Set<byte[]> keys = new HashSet<>();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(cursor.next());
                    }
                }
//                for (byte[] keyByte : keys) {
//                    connection.del(keyByte);
//                }
                if (!keys.isEmpty()) {
                    connection.del(keys.toArray(new byte[0][])); // 批量删除，性能提升
                }
                if (keys.isEmpty()) {
                    return "OK";
                }
            } finally {
                if (wasLocked && this.isLockingCacheWriter()) {
                    this.doUnlock(name, connection);
                }
            }

            return "OK";
        });
    }

    private static boolean shouldExpireWithin(@Nullable Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    /**
     * 模板方法模式封装了 Redis 连接的获取、锁检查、回调执行和连接关闭等通用逻辑
     * @param name 缓存名称（用于分布式锁的 key 生成）
     * @param callback 函数式接口，接收 RedisConnection（Redis 连接），返回泛型结果，用于封装具体的 Redis 操作逻辑
     * @return
     * @param <T>
     */
    private <T> T execute(String name, Function<RedisConnection, T> callback) {
        RedisConnection connection = this.connectionFactory.getConnection();
        try {
            this.checkAndPotentiallyWaitUntilUnlocked(name, connection);
            return callback.apply(connection);
        } finally {
            connection.close();
        }
    }

    /**
     * 分布式锁的 “自旋等待”,让当前线程循环等待
     * @param name
     * @param connection
     */
    private void checkAndPotentiallyWaitUntilUnlocked(String name, RedisConnection connection) {
        if (this.isLockingCacheWriter()) {
            try {
                // 循环调用doCheckLock检测锁 key 是否存在（即其他线程是否持有锁），若存在则进入循环体
                while(this.doCheckLock(name, connection)) {
                    // 当前线程休眠defaultTtl对应的毫秒数，避免高频循环检测锁
                    Thread.sleep(this.defaultTtl.toMillis());
                }
            } catch ( InterruptedException var4) {
                // 恢复线程的中断状态（因为Thread.sleep()会清除中断标记），确保上层代码能感知到中断事件
                Thread.currentThread().interrupt();
                // 将中断异常包装为 Spring 数据访问层的标准并发异常
                throw new PessimisticLockingFailureException(String.format("Interrupted while waiting to unlock cache %s", name), var4);
            }
        }
    }

    /**
     * 判断当前缓存写入器是否启用分布式锁机制
     * 若defaultTtl非零且非负 → 返回true（启用锁）
     * 若defaultTtl为零或负数 → 返回false（禁用锁）
     * @return
     */
    private boolean isLockingCacheWriter() {
        return !this.defaultTtl.isZero() && !this.defaultTtl.isNegative();
    }

    /**
     * 检查指定缓存名称（name）对应的分布式锁是否存在
     * @param name
     * @param connection
     * @return
     */
    private boolean doCheckLock(String name, RedisConnection connection) {
        byte[] lockKey = createCacheLockKey(name);
        return connection.exists(lockKey);
    }

    /**
     * 锁 key（通常格式为{name}~lock，如缓存名userCache对应锁 keyuserCache~lock）
     * @param name
     * @return
     */
    private static byte[] createCacheLockKey(String name) {
        return (name + "~lock").getBytes(StandardCharsets.UTF_8);
    }

    /**
     *
     * @param name  锁的业务标识
     * @param connection
     * @return
     */
    private boolean doLock(String name, RedisConnection connection) {
        String lockValue = Thread.currentThread().getId() + ":" + UUID.randomUUID();

        currentLockValue.set(lockValue);
        // createCacheLockKey("order:123") → lock:order:123, 生成 Redis 中锁的实际 Key
        // new byte[0], Value 无实际业务意义，仅需占位,生产环境中建议用客户端唯一标识（如 UUID + 线程 ID）作为 Value
        // Expiration.seconds(180L):对应 Redis SET 命令的 EX 选项
        // RedisStringCommands.SetOption.SET_IF_ABSENT: 只有当 Key不存在时，才执行 SET 操作
//        return connection.set(createCacheLockKey(name), new byte[0], Expiration.seconds(180L), RedisStringCommands.SetOption.SET_IF_ABSENT)
        return connection.set(createCacheLockKey(name), lockValue.getBytes(StandardCharsets.UTF_8), Expiration.seconds(180L), RedisStringCommands.SetOption.SET_IF_ABSENT);
    }

    /**
     * 生成对应的锁 key 并执行 Redis 的DEL命令删除锁，返回删除成功的 key 数量，完成分布式锁的释放
     * @param name
     * @param connection
     * @return
     */
    private Long doUnlock(String name, RedisConnection connection) {
        byte[] lockKey = createCacheLockKey(name);
        String lockValue = currentLockValue.get();

        try {
            if (lockValue == null) {
                return 0L;
            }
            return (Long) connection.eval(
                UNLOCK_LUA.getBytes(StandardCharsets.UTF_8),
                ReturnType.INTEGER,
                1,
                lockKey,
                lockValue.getBytes(StandardCharsets.UTF_8)
            );
        } finally {
            currentLockValue.remove();
        }
    }

    /**
     * 统计收集器初始化
     */
    private final CacheStatisticsCollector statistics = CacheStatisticsCollector.create();

    /**
     * 按缓存名称返回对应的统计数据（如命中率、写入次数），用于监控缓存性能
     * @param cacheName
     * @return
     */
    @Override
    public CacheStatistics getCacheStatistics(String cacheName) {
        return statistics.getCacheStatistics(cacheName);
    }

    /**
     * 清空指定缓存名称的统计数据
     * @param name
     */
    @Override
    public void clearStatistics(String name) {
        ((CacheStatisticsCollector) statistics).reset(name);
    }

    /**
     * 返回一个携带指定统计收集器的新RedisCacheWriter实例
     * @param cacheStatisticsCollector
     * @return
     */
    @Override
    public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector cacheStatisticsCollector) {
        return null;
    }

    /**
     * 异步缓存操作方法，支持非阻塞的缓存读取
     * @param name
     * @param key
     * @param ttl
     * @return
     */
    @Override
    public CompletableFuture<byte[]> retrieve(String name, byte[] key, Duration ttl) {
        return null;
    }

    /**
     * 异步缓存操作方法，支持非阻塞的缓存写入
     * @param name
     * @param key
     * @param value
     * @param ttl
     * @return
     */
    @Override
    public CompletableFuture<Void> store(String name, byte[] key, byte[] value, Duration ttl) {
        return null;
    }
}
