package com.gateflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GateFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(GateFlowApplication.class, args);
    }
}

