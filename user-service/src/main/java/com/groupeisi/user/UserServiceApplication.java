package com.groupeisi.user;
import com.groupeisi.user.client.LoanClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;
import org.springframework.core.DecoratingProxy;

class LoanClientProxyHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.proxies().registerJdkProxy(
            LoanClient.class,
            SpringProxy.class,
            Advised.class,
            DecoratingProxy.class
        );
    }
}

@EnableCaching
@EnableFeignClients
@SpringBootApplication
@ImportRuntimeHints(LoanClientProxyHints.class)
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
