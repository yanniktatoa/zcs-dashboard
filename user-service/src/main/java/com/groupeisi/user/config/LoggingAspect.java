package com.groupeisi.user.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class LoggingAspect {
    @Before("execution(* com.groupeisi.user..*(..))")
    public void e(JoinPoint jp) {
        log.info("ENTER {}", jp.getSignature());
    }

    @AfterReturning(pointcut = "execution(* com.groupeisi.user..*(..))", returning = "r")
    public void s(JoinPoint jp, Object r) {
        log.info("EXIT {}", jp.getSignature());
    }

    @AfterThrowing(pointcut = "execution(* com.groupeisi.user..*(..))", throwing = "ex")
    public void x(JoinPoint jp, Throwable ex) {
        log.error("ERROR {}", jp.getSignature(), ex);
    }
}
