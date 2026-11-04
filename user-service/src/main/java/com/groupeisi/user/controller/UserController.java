package com.groupeisi.user.controller;

import com.groupeisi.user.dto.*;
import com.groupeisi.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDto create(@Valid @RequestBody UserRequestDto dto) {
        return service.create(dto);
    }

    @GetMapping("/{id}")
    public UserResponseDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping
    public List<UserResponseDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/paginated")
    public Page<UserResponseDto> getPaginated(Pageable pageable) {
        return service.findAll(pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
