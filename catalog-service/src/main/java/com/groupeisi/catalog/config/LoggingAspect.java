package com.groupeisi.catalog.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.AfterReturning;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class LoggingAspect {
    @Before("execution(* com.groupeisi.catalog..*(..))")
    public void logEntry(JoinPoint joinPoint) {
        log.info("ENTER {} args={}", joinPoint.getSignature(), joinPoint.getArgs());
    }

    @AfterReturning(pointcut = "execution(* com.groupeisi.catalog..*(..))", returning = "result")
    public void logExit(JoinPoint joinPoint, Object result) {
        log.info("EXIT {} result={}", joinPoint.getSignature(), result);
    }

    @AfterThrowing(pointcut = "execution(* com.groupeisi.catalog..*(..))", throwing = "ex")
    public void logError(JoinPoint joinPoint, Throwable ex) {
        log.error("ERROR {} msg={}", joinPoint.getSignature(), ex.getMessage(), ex);
    }
}
