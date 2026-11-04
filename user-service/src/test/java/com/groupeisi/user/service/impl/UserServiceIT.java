package com.groupeisi.user.service.impl;

import com.groupeisi.user.client.LoanClient;
import com.groupeisi.user.dto.UserRequestDto;
import com.groupeisi.user.exception.UserAlreadyExistsException;
import com.groupeisi.user.repository.UserRepository;
import com.groupeisi.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceIT {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private LoanClient loanClient;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void create_shouldEnforceUniqueEmailInDatabase() {
        UserRequestDto request = new UserRequestDto();
        request.setName("Bob");
        request.setEmail("bob@example.com");
        request.setRole("MEMBER");

        userService.create(request);

        UserRequestDto duplicate = new UserRequestDto();
        duplicate.setName("Bob2");
        duplicate.setEmail("bob@example.com");
        duplicate.setRole("MEMBER");

        assertThatThrownBy(() -> userService.create(duplicate))
                .isInstanceOf(UserAlreadyExistsException.class);

        assertThat(userRepository.count()).isEqualTo(1);
    }
}
