package backend.drawrace.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.user.dto.UserInfoResponse;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.RefreshTokenRepository;
import backend.drawrace.domain.user.repository.UserRepository;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public UserInfoResponse getUser(Long userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 유저입니다. ID: " + userId));

        return UserInfoResponse.from(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ServiceException("404-1", "존재하지 않는 유저입니다. ID: " + userId);
        }
        refreshTokenRepository.deleteById(userId);
        userRepository.deleteById(userId);
    }
}
