package dev.w0fv1.remotebrowser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RemoteBrowserApplication {
    public static void main(String[] args) {
        SpringApplication.run(RemoteBrowserApplication.class, args);
    }
}
