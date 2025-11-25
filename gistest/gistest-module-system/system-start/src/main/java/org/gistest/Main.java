package org.gistest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

@Slf4j
@SpringBootApplication
public class Main extends SpringBootServletInitializer {
    public static void main(String[] args) throws UnknownHostException {
        SpringApplication app = new SpringApplication(Main.class);

        ConfigurableApplicationContext application = app.run(args);
        System.out.println("当前JDK版本：" + System.getProperty("java.version"));
        Environment env = application.getEnvironment();
        String ip = InetAddress.getLocalHost().getHostAddress();
        String port = env.getProperty("server.port");
        String path = Objects.toString(env.getProperty("server.servlet.content-path"), "");
        log.info(
                "\n----------------------------------------------------------\n\t" +
                "Application GisTest is running! Access URLs:\n\t" +
                "Local: \t\thttp://localhost:" + port + path + "\n\t" +
                "External: \thttp://" + ip + ":" + port + path + "/doc.html\n\t" +
                "文档: \thttp://" + ip + ":" + port + path + "/doc.html\n" +
                "----------------------------------------------------------"
        );
    }
}
