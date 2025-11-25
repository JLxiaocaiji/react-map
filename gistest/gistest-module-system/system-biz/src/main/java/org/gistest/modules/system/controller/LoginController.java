package org.gistest.modules.system.controller;

import cn.hutool.core.util.RandomUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.gistest.common.api.vo.Result;
import org.gistest.config.BaseConfig;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.gistest.common.util.Md5Util;
import org.gistest.modules.system.system.util.RandImageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="用户登录")
@RestController
@Slf4j
public class LoginController {

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private BaseConfig baseConfig;

    private final String BASE_CHECK_CODES = "qwertyuiplkjhgfdsazxcvbnmQWERTYUPLKJHGFDSAZXCVBNM1234567890";


    @GetMapping(value = "/randomImage/{key}")
    public Result<String> randomImage(HttpServletResponse response, @PathVariable("key") String key) {
        Result<String> res = new Result<String>();
        try {
            String code = RandomUtil.randomString(BASE_CHECK_CODES, 4);
            //存到redis中
            String lowerCaseCode = code.toLowerCase();
            String keyPrefix = Md5Util.md5Encode(key + baseConfig.getSignatureSecret(), "utf-8");
            String realKey = keyPrefix + lowerCaseCode;

            redisUtil.removeAll(keyPrefix);
            redisUtil.set(realKey, lowerCaseCode, 60);
            log.info("获取验证码，Redis key = {}，checkCode = {}", realKey, code);
            String base64 = RandImageUtil.generate(code);
        }
    }
}
