package com.idfc.ai.runbook;

import com.idfc.ai.runbook.config.RunbookProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RunbookProperties.class)
public class RunbookApplication { public static void main(String[] args) { SpringApplication.run(RunbookApplication.class, args); } }
