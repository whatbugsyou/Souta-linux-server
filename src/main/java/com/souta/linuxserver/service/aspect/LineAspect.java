package com.souta.linuxserver.service.aspect;


import com.souta.linuxserver.line.Line;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import static com.souta.linuxserver.monitor.LineMonitor.*;

@Aspect
@Component
@Slf4j
public class LineAspect {

    @Pointcut(value = "execution( public * com.souta.linuxserver.service.LineService.createLine(..)) && args(lineId))")
    public void create(String lineId) {
    }

    @Around(value = "create(lineId)", argNames = "joinPoint,lineId")
    public Object aroundCreate(ProceedingJoinPoint joinPoint, String lineId) throws Throwable {
        Line line = (Line) joinPoint.proceed();
        if (line == null) {
            return line;
        }
        if (line.getOutIpAddr() != null) {
            dialFalseTimesMap.remove(lineId);
            deadLineIdSet.remove(lineId);
            deadLineToSend.remove(line);
        } else {
            dialFalseTimesMap.merge(lineId, 1, Integer::sum);
            if (dialFalseTimesMap.get(lineId) == checkingTimesOfDefineDeadLine) {
                deadLineIdSet.add(lineId);
                deadLineToSend.add(line);
            }
        }
        return line;
    }

    @Pointcut(value = "execution( public * com.souta.linuxserver.service.LineService.refresh(..)) && args(lineId))")
    public void refresh(String lineId) {
    }

    @Around(value = "refresh(lineId)", argNames = "joinPoint,lineId")
    public Object aroundRefresh(ProceedingJoinPoint joinPoint, String lineId) throws Throwable {
        dialFalseTimesMap.remove(lineId);
        deadLineIdSet.remove(lineId);
        Line line = (Line) joinPoint.proceed();
        if (line == null) {
            return line;
        }
        if (line.getOutIpAddr() != null) {
            dialFalseTimesMap.remove(lineId);
            deadLineIdSet.remove(lineId);
            deadLineToSend.remove(line);
        } else {
            dialFalseTimesMap.merge(lineId, 1, Integer::sum);
            if (dialFalseTimesMap.get(lineId) == checkingTimesOfDefineDeadLine) {
                deadLineIdSet.add(lineId);
                deadLineToSend.add(line);
            }
        }
        return line;
    }
}
