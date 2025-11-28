package org.gistest.common.modules.redis.listener;

import org.gistest.common.base.BaseMap;

public interface RedisListener {
    /**
     * 接受消息
     *
     * @param message
     */
    void onMessage(BaseMap message);

}