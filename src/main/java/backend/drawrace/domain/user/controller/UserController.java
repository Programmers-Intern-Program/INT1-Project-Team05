package backend.drawrace.domain.user.controller;

import jakarta.validation.constraints.NotBlank;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import backend.drawrace.domain.user.dto.UpdateUserRequest;
import backend.drawrace.domain.user.dto.UserInfoResponse;
import backend.drawrace.domain.user.dto.UserSearchResponse;
import backend.drawrace.domain.user.service.UserService;
import backend.drawrace.global.rsdata.RsData;
import backend.drawrace.global.security.SecurityUser;

import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public RsData<UserInfoResponse> getMyInfo(@AuthenticationPrincipal SecurityUser securityUser) {
        UserInfoResponse response = userService.getUser(securityUser.getUserId());
        return new RsData<>("200-1", "본인 정보를 성공적으로 조회했습니다.", response);
    }

    @GetMapping("/{userId}")
    public RsData<UserInfoResponse> findOne(@PathVariable Long userId) {
        UserInfoResponse response = userService.getUser(userId);
        return new RsData<>("200-2", "유저 정보를 성공적으로 조회했습니다.", response);
    }

    @GetMapping("/search")
    public RsData<UserSearchResponse> searchByNickname(@RequestParam @NotBlank String nickname) {
        UserSearchResponse response = userService.searchByNickname(nickname);
        return new RsData<>("200-5", "유저를 성공적으로 조회했습니다.", response);
    }

    @PatchMapping("/me")
    public RsData<UserInfoResponse> updateMyProfile(
            @AuthenticationPrincipal SecurityUser securityUser, @RequestBody @Validated UpdateUserRequest request) {
        UserInfoResponse response = userService.updateProfile(securityUser.getUserId(), request);
        return new RsData<>("200-4", "프로필이 성공적으로 수정되었습니다.", response);
    }

    @DeleteMapping("/me")
    public RsData<Void> deleteMyAccount(@AuthenticationPrincipal SecurityUser securityUser) {
        userService.deleteUser(securityUser.getUserId());
        return new RsData<>("200-3", "회원탈퇴가 완료되었습니다.");
    }
}
