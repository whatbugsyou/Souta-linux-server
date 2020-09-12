package com.souta.linuxserver;

import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class LinuxServerApplicationTests {

    //@Test
    void contextLoads() {
    }

    private ArrayList<String> seperator;

    public static void main(String[] args) {
        LinuxServerApplicationTests linuxServerApplicationTests = new LinuxServerApplicationTests();
        ExecutorService executorService = Executors.newFixedThreadPool(10);
    }

}
