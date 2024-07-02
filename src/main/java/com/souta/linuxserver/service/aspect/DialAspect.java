package com.souta.linuxserver.service.aspect;


import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Aspect
@Component
@Slf4j
public class DialAspect {
    private static final int dialGapLimit = 10;
    private static final Timer timer = new Timer();
    private static final ReentrantLock reDialLock = new ReentrantLock();
    private static final ConcurrentHashMap<String, Condition> redialLimitedConditionMap = new ConcurrentHashMap<>();
    private static final Map<String, Condition> conditionList = new HashMap<>();

    @Pointcut(value = "execution(public String com.souta.linuxserver.service.PPPOEService.dialUp(..)) && args(pppoeId,adslUser,adslPassword,ethernetName,namespaceName))")
    public void dialUp(String pppoeId, String adslUser, String adslPassword, String ethernetName, String namespaceName) {
    }

    @Around(value = "dialUp(pppoeId,adslUser,adslPassword,ethernetName,namespaceName)", argNames = "joinPoint,pppoeId,adslUser,adslPassword,ethernetName,namespaceName")
    public Object aroundDialUp(ProceedingJoinPoint joinPoint, String pppoeId, String adslUser, String adslPassword, String ethernetName, String namespaceName) throws Throwable {
        reDialLock.lock();
        try {
            Condition condition = redialLimitedConditionMap.get(pppoeId);
            if (condition != null) {
                condition.await();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            reDialLock.unlock();
        }
        Object result = joinPoint.proceed();
        limitRedialTime(pppoeId);
        return result;
    }

    private void limitRedialTime(String id) {
        Condition condition = conditionList.get(id);
        if (condition == null) {
            condition = reDialLock.newCondition();
            conditionList.put(id, condition);
        }
        redialLimitedConditionMap.put(id, condition);
        Condition finalCondition = condition;
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                reDialLock.lock();
                try {
                    finalCondition.signalAll();
                    redialLimitedConditionMap.remove(id);
                } finally {
                    reDialLock.unlock();
                }
            }
        };
        timer.schedule(timerTask, TimeUnit.SECONDS.toMillis(dialGapLimit));
    }
}
