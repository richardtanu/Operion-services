package com.example.operion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OperionApplication {

	public static void main(String[] args) {
		SpringApplication.run(OperionApplication.class, args);
	}

}
