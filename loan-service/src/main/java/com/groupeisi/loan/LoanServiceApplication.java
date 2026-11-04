package com.groupeisi.loan;
import com.groupeisi.loan.client.BookClient;
import com.groupeisi.loan.client.UserClient;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.DecoratingProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

class LoanClientProxyHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.proxies().registerJdkProxy(
            BookClient.class, SpringProxy.class, Advised.class, DecoratingProxy.class
        );
        hints.proxies().registerJdkProxy(
            UserClient.class, SpringProxy.class, Advised.class, DecoratingProxy.class
        );
    }
}

@EnableScheduling
@EnableFeignClients
@SpringBootApplication
@ImportRuntimeHints(LoanClientProxyHints.class)
public class LoanServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoanServiceApplication.class, args);
    }
}
