package org.gistest.common.util;

import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 作为 Spring 容器的 “全局访问入口”，解决非 Spring 管理类（如普通工具类、线程类、第三方框架回调类）无法直接依赖注入 Bean 的问题
 */
@Slf4j
@Lazy(false)    // 强制该 Bean 在 Spring 容器启动时立即初始化
@Component
public class SpringContextHolder implements ApplicationContextAware {
    private static ApplicationContext applicationContext;

    /**
     * 实现ApplicationContextAware接口的context注入函数, 将其存入静态变量.
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        // NOSONAR 忽略 SonarQube 等代码检查工具对 “静态变量赋值” 的警告
        SpringContextHolder.applicationContext = applicationContext;
    }

    /**
     * 取得存储在静态变量中的ApplicationContext.
     */
    public static ApplicationContext getApplicationContext() {
        checkApplicationContext();
        return applicationContext;
    }

    /**
     * 从静态变量ApplicationContext中取得Bean, 自动转型为所赋值对象的类型.
     */
    public static <T> T getBean(String name) {
        checkApplicationContext();
        return (T) applicationContext.getBean(name);
    }

    /**
     * 从静态变量ApplicationContext中取得Bean, 自动转型为所赋值对象的类型.
     */
    private static boolean isWarningLogged = false; // 标志变量
    public static <T> T getHandler(String name, Class<T> cls) {
        T t = null;
        if (ObjectUtil.isNotEmpty(name)) {
            checkApplicationContext();
            try {
                t = applicationContext.getBean(name, cls);
            } catch (Exception e) {
                if (!isWarningLogged) { // 检查标志变量
                    log.warn("Customize redis listener handle [ " + name + " ], does not exist！");
                    isWarningLogged = true; // 设置标志，确保只记录一次
                }
            }
        }
        return t;
    }


    /**
     * 从静态变量ApplicationContext中取得Bean, 自动转型为所赋值对象的类型.
     */
    public static <T> T getBean(Class<T> clazz) {
        checkApplicationContext();
        return applicationContext.getBean(clazz);
    }

    /**
     * 清除applicationContext静态变量.
     */
    public static void cleanApplicationContext() {
        applicationContext = null;
    }

    private static void checkApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("applicationContext未注入，请在applicationContext.xml中定义SpringContextHolder");
        }
    }

}
