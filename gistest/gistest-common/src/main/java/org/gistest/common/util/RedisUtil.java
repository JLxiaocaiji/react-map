package org.gistest.common.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConfigurationProperties("gistest.redis")
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Set<String> keys(String keyPrefix) {
        String realKey = keyPrefix + "*";

        try {
            return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                Set<String> binaryKeys = new HashSet<String>();
                Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(realKey).count(Integer.MAX_VALUE).build());
                while (cursor.hasNext()) {
                    binaryKeys.add(new String(cursor.next()));
                }

                return binaryKeys;
            });
        }catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 删除指定前缀的一系列key
     * @param keyPrefix
     */
    public void removeAll(String keyPrefix) {
        try {
            Set<String> keys = keys(keyPrefix);
            redisTemplate.delete(keys);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
