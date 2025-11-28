package org.gistest.common.modules.redis.cache;

import org.springframework.util.Assert;

@FunctionalInterface
public interface CacheKeyPrefix {
    String compute(String cacheName);

    static org.springframework.data.redis.cache.CacheKeyPrefix simple() {
        return (name) -> name + "::";
    }

    static org.springframework.data.redis.cache.CacheKeyPrefix prefixed(String prefix) {
        Assert.notNull(prefix, "Prefix must not be null");
        return (name) -> prefix + name + "::";
    }
}
