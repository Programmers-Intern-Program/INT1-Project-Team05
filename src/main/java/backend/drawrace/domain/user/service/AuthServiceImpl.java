package backend.drawrace.domain.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.user.dto.CreateUserRequest;
import backend.drawrace.domain.user.dto.LoginRequest;
import backend.drawrace.domain.user.dto.LoginResponse;
import backend.drawrace.domain.user.dto.TokenRequest;
import backend.drawrace.domain.user.entity.RefreshToken;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.RefreshTokenRepository;
import backend.drawrace.domain.user.repository.UserRepository;
import backend.drawrace.global.exception.ServiceException;
import backend.drawrace.global.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public Long signup(CreateUserRequest dto) {
        if (userRepository.existsByEmail(dto.email())) {
            throw new ServiceException("409-1", "이미 존재하는 이메일입니다.");
        }
        if (userRepository.existsByNickname(dto.nickname())) {
            throw new ServiceException("409-2", "이미 존재하는 닉네임입니다.");
        }

        User user = User.builder()
                .email(dto.email())
                .password(passwordEncoder.encode(dto.password()))
                .nickname(dto.nickname())
                .isAi(false)
                .build();

        return userRepository.save(user).getId();
    }

    @Override
    public LoginResponse login(LoginRequest dto) {
        User user = userRepository
                .findByEmail(dto.email())
                .orElseThrow(() -> new ServiceException("401-1", "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (user.isAi()) {
            throw new ServiceException("403-1", "AI 유저는 로그인할 수 없습니다.");
        }

        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw new ServiceException("401-1", "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(new RefreshToken(user.getId(), refreshToken));

        return new LoginResponse(accessToken, refreshToken);
    }

    @Override
    @Transactional
    public LoginResponse reissue(TokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            throw new ServiceException("401-2", "리프레시 토큰이 유효하지 않거나 만료되었습니다.");
        }

        Long userId = Long.valueOf(jwtTokenProvider.getSubject(request.refreshToken()));

        RefreshToken savedToken = refreshTokenRepository
                .findById(userId)
                .orElseThrow(() -> new ServiceException("401-3", "로그아웃되었거나 유효하지 않은 세션입니다."));

        if (!savedToken.getTokenValue().equals(request.refreshToken())) {
            throw new ServiceException("401-4", "토큰 정보가 일치하지 않습니다. 다시 로그인해주세요.");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new ServiceException("404-1", "사용자를 찾을 수 없습니다."));

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(new RefreshToken(user.getId(), newRefreshToken));

        return new LoginResponse(newAccessToken, newRefreshToken);
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteById(userId);
    }
}
