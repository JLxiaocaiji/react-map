package org.gistest.common.modules.redis.receiver;

import cn.hutool.core.util.ObjectUtil;
import lombok.Data;
import org.gistest.common.base.BaseMap;
import org.gistest.common.constant.GlobalConstants;
import org.gistest.common.modules.redis.listener.RedisListener;
import org.gistest.common.util.SpringContextHolder;
import org.springframework.stereotype.Component;

@Component
@Data
public class RedisReceiver {
    /**
     * 接受消息并调用业务逻辑处理器
     *
     * @param params
     */
    public void onMessage(BaseMap params) {
        Object handlerName = params.get(GlobalConstants.HANDLER_NAME);
        RedisListener messageListener = SpringContextHolder.getHandler(handlerName.toString(), RedisListener.class);
        if (ObjectUtil.isNotEmpty(messageListener)) {
            messageListener.onMessage(params);
        }
    }

}
