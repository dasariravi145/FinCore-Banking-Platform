package com.fincore.platform.user.service;

import com.fincore.platform.auth.dto.UserProfileResponse;
import com.fincore.platform.common.error.ResourceNotFoundException;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.entity.UserStatus;
import com.fincore.platform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserProfileResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(UserProfileResponse::fromUser);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        return UserProfileResponse.fromUser(user);
    }

    @Transactional
    public UserProfileResponse updateUserStatus(UUID id, UserStatus newStatus) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        user.setStatus(newStatus);
        User updated = userRepository.save(user);
        return UserProfileResponse.fromUser(updated);
    }
}

