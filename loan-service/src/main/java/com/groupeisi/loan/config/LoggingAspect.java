package com.groupeisi.loan.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class LoggingAspect {
    @Before("execution(* com.groupeisi.loan..*(..))")
    public void e(JoinPoint jp) {
        log.info("ENTER {}", jp.getSignature());
    }

    @AfterReturning(pointcut = "execution(* com.groupeisi.loan..*(..))", returning = "r")
    public void s(JoinPoint jp, Object r) {
        log.info("EXIT {}", jp.getSignature());
    }

    @AfterThrowing(pointcut = "execution(* com.groupeisi.loan..*(..))", throwing = "ex")
    public void x(JoinPoint jp, Throwable ex) {
        log.error("ERROR {}", jp.getSignature(), ex);
    }
}
