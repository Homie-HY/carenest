package com.carenest.nursing.task;

import org.springframework.stereotype.Component;

@Component("helloTask")
public class HelloTask {
    public void myTask() {
        System.out.println("helloTask");
    }
}