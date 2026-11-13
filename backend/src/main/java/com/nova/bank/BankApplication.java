package com.nova.bank;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/** Start the independent banking API. */
@SpringBootApplication
public class BankApplication {
 /** Bootstrap configuration, migrations and the HTTP service. */
 public static void main(String[] args) { SpringApplication.run(BankApplication.class, args); }
}
