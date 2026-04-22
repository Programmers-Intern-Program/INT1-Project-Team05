package backend.drawrace.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import backend.drawrace.domain.user.dto.UserInfoResponse;
import backend.drawrace.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserInfoResponse> findOne(@PathVariable Long userId) {
        UserInfoResponse response = userService.getUser(userId);
        return ResponseEntity.ok(response);
    }
}
