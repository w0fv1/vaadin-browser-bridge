package dev.w0fv1.browserbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BrowserBridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(BrowserBridgeApplication.class, args);
    }
}
