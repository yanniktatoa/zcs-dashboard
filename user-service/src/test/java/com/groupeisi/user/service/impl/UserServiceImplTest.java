package com.groupeisi.user.service.impl;

import com.groupeisi.user.client.LoanClient;
import com.groupeisi.user.dto.UserRequestDto;
import com.groupeisi.user.dto.UserResponseDto;
import com.groupeisi.user.entities.UserEntity;
import com.groupeisi.user.exception.ActiveLoanExistsException;
import com.groupeisi.user.exception.EntityNotFoundException;
import com.groupeisi.user.exception.UserAlreadyExistsException;
import com.groupeisi.user.mapping.UserMapper;
import com.groupeisi.user.metrics.UserBusinessMetrics;
import com.groupeisi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository repository;

    @Mock
    private UserMapper mapper;

    @Mock
    private LoanClient loanClient;

    @Mock
    private UserBusinessMetrics metrics;

    @InjectMocks
    private UserServiceImpl userService;

    private UserRequestDto request;
    private UserEntity entity;
    private UserResponseDto response;

    @BeforeEach
    void setUp() {
        request = new UserRequestDto();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setRole("MEMBER");

        entity = UserEntity.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .role("MEMBER")
                .build();

        response = new UserResponseDto();
        response.setId(1L);
        response.setName("Alice");
        response.setEmail("alice@example.com");
        response.setRole("MEMBER");
    }

    @Test
    void create_shouldPersistUser_whenEmailIsUnique() {
        when(repository.existsByEmail("alice@example.com")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(response);

        UserResponseDto result = userService.create(request);

        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        verify(repository).save(entity);
    }

    @Test
    void create_shouldThrowUserAlreadyExistsException_whenEmailExists() {
        when(repository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("alice@example.com");

        verify(repository, never()).save(any(UserEntity.class));
    }

    @Test
    void delete_shouldRemoveUser_whenNoActiveLoans() {
        when(repository.existsById(1L)).thenReturn(true);
        when(loanClient.hasActiveLoans(1L)).thenReturn(false);

        userService.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void delete_shouldThrowActiveLoanExistsException_whenActiveLoansExist() {
        when(repository.existsById(1L)).thenReturn(true);
        when(loanClient.hasActiveLoans(1L)).thenReturn(true);

        assertThatThrownBy(() -> userService.delete(1L))
                .isInstanceOf(ActiveLoanExistsException.class);

        verify(repository, never()).deleteById(1L);
    }

    @Test
    void delete_shouldThrowEntityNotFoundException_whenUserMissing() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> userService.delete(99L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(loanClient, never()).hasActiveLoans(anyLong());
    }
}
