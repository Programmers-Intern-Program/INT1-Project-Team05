package backend.drawrace.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import backend.drawrace.domain.user.dto.UserInfoResponse;
import backend.drawrace.domain.user.service.UserService;
import backend.drawrace.global.security.SecurityUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getMyInfo(@AuthenticationPrincipal SecurityUser securityUser) {
        UserInfoResponse response = userService.getUser(securityUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserInfoResponse> findOne(@PathVariable Long userId) {
        UserInfoResponse response = userService.getUser(userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(@AuthenticationPrincipal SecurityUser securityUser) {
        userService.deleteUser(securityUser.getUserId());
        return ResponseEntity.noContent().build();
    }
}
