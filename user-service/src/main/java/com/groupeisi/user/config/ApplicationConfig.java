package com.groupeisi.user.config;

import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration
public class ApplicationConfig {
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource s = new ReloadableResourceBundleMessageSource();
        s.setBasename("classpath:messages");
        s.setDefaultEncoding("UTF-8");
        return s;
    }

    @Bean
    public LocalValidatorFactoryBean validatorFactoryBean(MessageSource ms) {
        LocalValidatorFactoryBean b = new LocalValidatorFactoryBean();
        b.setValidationMessageSource(ms);
        return b;
    }
}
