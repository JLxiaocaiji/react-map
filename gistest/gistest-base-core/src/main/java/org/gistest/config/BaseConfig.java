package org.gistest.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.stereotype.Component;

import java.security.Signature;

@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class BaseConfig {
    private String signatureSecret = "dd05f1c54d63749eda95f9fa6d49v442a";

    public String getSignatureSecret() {
        return signatureSecret;
    }
}
