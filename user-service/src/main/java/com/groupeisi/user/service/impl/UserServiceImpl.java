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
import com.groupeisi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    private final UserRepository repository;
    private final UserMapper mapper;
    private final LoanClient loanClient;
    private final UserBusinessMetrics metrics;

    @Override
    @Transactional
    @CachePut(value = "userDto", key = "#result.id")
    public UserResponseDto create(UserRequestDto dto) {
        if (repository.existsByEmail(dto.getEmail())) {
            log.warn("Attempt to create user with existing email={}", dto.getEmail());
            metrics.incrementDuplicateEmails();
            throw new UserAlreadyExistsException("User with email already exists: " + dto.getEmail());
        }
        UserEntity saved = repository.save(mapper.toEntity(dto));
        metrics.incrementUsersCreated();
        return mapper.toDto(saved);
    }

    @Override
    @Cacheable(value = "userDto", key = "#id")
    public UserResponseDto findById(Long id) {
        UserEntity e = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        return mapper.toDto(e);
    }

    @Override
    public List<UserResponseDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public Page<UserResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDto);
    }

    @Override
    @Transactional
    @CacheEvict(value = "userDto", key = "#id")
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("User not found with id: " + id);
        }
        if (loanClient.hasActiveLoans(id)) {
            log.warn("Cannot delete userId={} : active loans exist", id);
            metrics.incrementDeletionsRefused();
            throw new ActiveLoanExistsException("Cannot delete user with active loans");
        }
        repository.deleteById(id);
    }
}
