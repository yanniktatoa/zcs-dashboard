package com.groupeisi.user.service;

import com.groupeisi.user.dto.*;
import org.springframework.data.domain.*;

import java.util.List;

public interface UserService {
    UserResponseDto create(UserRequestDto dto);

    UserResponseDto findById(Long id);

    List<UserResponseDto> findAll();

    Page<UserResponseDto> findAll(Pageable pageable);

    void delete(Long id);
}
