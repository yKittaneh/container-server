package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

@SpringBootApplication
@RestController
public class ContainerServer {
    private static final Logger logger = LoggerFactory.getLogger(ContainerServer.class);

    private static final String DEFAULT_PORT = "5567";

    @RequestMapping("/")
    public String home() {
        logger.info("home called");

        return "Hello, this is ContainerServer home.";
    }

    public static void main(String[] args) {
        logger.info("starting spring boot application, args: {}", Arrays.toString(args));

        startApplication(args);

        logger.info("application started...");
    }

    @PostMapping("/stepInformation")
    public void receiveStepInformation(@RequestBody Map<String, Double> infoMap) {
        logger.info("received request on /stepInformation");

        if (!infoMap.containsKey("battery_power"))
            logger.warn("key [battery_power] not found in received infoMap");
        if (!infoMap.containsKey("pv_power"))
            logger.warn("key [pv_power] not found in received infoMap");

        logger.info("Key-value sets are:");

        for (Map.Entry<String, Double> entry : infoMap.entrySet()) {
            logger.info(entry.getKey() + " - " + entry.getValue());
        }
    }

    private static void startApplication(String[] args) {
        String port = DEFAULT_PORT;
        if (args != null && args.length > 0)
            port = args[0];

        logger.info("Using port " + port);

        SpringApplication app = new SpringApplication(ContainerServer.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", port));
        app.run(args);
    }
}
