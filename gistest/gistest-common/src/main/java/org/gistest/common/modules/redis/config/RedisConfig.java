package org.gistest.common.modules.redis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.gistest.common.constant.CacheConstant;
import org.gistest.common.constant.GlobalConstants;
import org.gistest.common.modules.redis.receiver.RedisReceiver;
import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.cache.RedisCache;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@EnableCaching
@Configuration
public class RedisConfig extends CachingConfigurerSupport {

    @Resource
    private RedisCacheTtls redisCacheProperties;

    private static volatile Jackson2JsonRedisSerializer<Object> cachedJacksonSerializer;

    private static Jackson2JsonRedisSerializer<Object> getJacksonSerializer() {
        if ( cachedJacksonSerializer == null ) {
            synchronized (RedisConfig.class) {
                if (cachedJacksonSerializer == null) {
                    cachedJacksonSerializer = jacksonSerializer();
                }
            }
        }
        return cachedJacksonSerializer;
    }

    private static Jackson2JsonRedisSerializer<Object> jacksonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new JtsModule());
        // 忽略未知属性，避免反序列化异常
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
    }

    /**
     * RedisTemplate配置
     * @param factory
     * @return
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {

//        Jackson2JsonRedisSerializer<Object> jacksonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);

        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = getJacksonSerializer();
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key序列化
        template.setKeySerializer(new StringRedisSerializer());
        // value序列化
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();

        return template;
    }

    /**
     * 缓存配置管理器
     *
     * @param factory
     * @return
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = getJacksonSerializer();
        // 配置序列化（解决乱码的问题）,并且配置缓存默认有效期 6小时
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(6));
        RedisCacheConfiguration redisCacheConfiguration = config.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer))
                .disableCachingNullValues();

        RedisCacheWriter writer = new RCacheWriter(factory, Duration.ofMillis(50L));

        Map<String, RedisCacheConfiguration> initialCaches = new HashMap<>();
        initialCaches.put(CacheConstant.SYS_DICT_TABLE_CACHE, RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10)).disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer)));
        initialCaches.put(CacheConstant.TEST_DEMO_CACHE, RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)).disableCachingNullValues());
        initialCaches.put(CacheConstant.PLUGIN_MALL_RANKING, RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(24)).disableCachingNullValues());
        initialCaches.put(CacheConstant.PLUGIN_MALL_PAGE_LIST, RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(24)).disableCachingNullValues());


        Map<String, Long> cacheTtls = redisCacheProperties.getCacheTtls();
        if (cacheTtls != null && !cacheTtls.isEmpty()) {
            cacheTtls.forEach((cacheName, ttl) -> {
                log.debug("自定义缓存配置，cacheKey:{}, 缓存秒数:{}",cacheName,ttl);
                initialCaches.put(cacheName, RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(ttl))
                        .disableCachingNullValues()
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer)));
            });
        }

        RedisCacheManager cacheManager = new RedisConfigCacheManager(writer, redisCacheConfiguration, initialCaches);
        cacheManager.setTransactionAware(true);
        //update-end-author:taoyan date:20210316 for:注解CacheEvict根据key删除redis支持通配符*
        return cacheManager;
    }

    public static class RedisConfigCacheManager extends RedisCacheManager {
        public RedisConfigCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration, Map<String, RedisCacheConfiguration> initialCaches) {
            super(cacheWriter, defaultCacheConfiguration, initialCaches, true);
        }

        private static final RedisSerializationContext.SerializationPair<Object> DEFAULT_PAIR = RedisSerializationContext.SerializationPair
                .fromSerializer(jacksonSerializer());

        private static final CacheKeyPrefix DEFAULT_CACHE_KEY_PREFIX = cacheName -> cacheName + "::";

        @Override
        protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
            final int lastIndexOf = name.lastIndexOf( '#');
            if (lastIndexOf > -1) {
                final String ttl = name.substring(lastIndexOf + 1);
                final Duration duration = Duration.ofSeconds(Long.parseLong(ttl));
                cacheConfig = cacheConfig.entryTtl(duration);
                //修改缓存key和value值的序列化方式
                cacheConfig = cacheConfig.computePrefixWith(DEFAULT_CACHE_KEY_PREFIX)
                        .serializeValuesWith(DEFAULT_PAIR);
                final String cacheName = name.substring(0, lastIndexOf);
                return super.createRedisCache(cacheName, cacheConfig);
            }else{
                //修改缓存key和value值的序列化方式
                cacheConfig = cacheConfig.computePrefixWith(DEFAULT_CACHE_KEY_PREFIX)
                        .serializeValuesWith(DEFAULT_PAIR);
                return super.createRedisCache(name, cacheConfig);
            }
        }
    }

    /**
     * redis 监听配置
     * @param redisConnectionFactory
     * @param redisReceiver
     * @param commonListenerAdapter
     * @return
     */
    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory redisConnectionFactory, RedisReceiver redisReceiver, MessageListenerAdapter commonListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(commonListenerAdapter, new ChannelTopic(GlobalConstants.REDIS_TOPIC_NAME));
        return container;
    }
}
