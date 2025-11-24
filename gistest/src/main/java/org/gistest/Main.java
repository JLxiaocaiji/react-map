package org.gistest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class Main extends SpringBootServletInitializer {
    public static void main(String[] args) {
        System.out.println("当前JDK版本：" + System.getProperty("java.version"));
        SpringApplication.run(Main.class, args);
    }
}
