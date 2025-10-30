package ReForm.backend.user.controller;

import ReForm.backend.user.User;
import ReForm.backend.user.SocialType;
import ReForm.backend.s3.AwsS3Service;
import ReForm.backend.user.repository.UserRepository;
import ReForm.backend.user.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AwsS3Service awsS3Service;

    @GetMapping("/mypage")
    public ResponseEntity<MypageResponse> mypage(HttpServletRequest request) {
        Optional<String> userIdOpt = currentUserId(request);
        if (userIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userId = userIdOpt.get();
        User u = userRepository.findById(userId).orElseThrow();
        String phoneForResponse = (u.getSocialType() == SocialType.LOCAL) ? u.getPhoneNumber() : null; // 소셜 로그인은 phone 비공개
        MypageResponse resp = new MypageResponse(
                u.getUserId(), u.getEmail(), u.getUserName(), u.getNickname(), phoneForResponse, u.getAddress(), u.getCreatedAt()
        );
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/me/status")
    public ResponseEntity<MypageResponse> updateStatus(@RequestBody UpdateUserStatusRequest req, HttpServletRequest request) {
        Optional<String> userIdOpt = currentUserId(request);
        if (userIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userId = userIdOpt.get();

        User u = userRepository.findById(userId).orElseThrow();
        User updated = User.builder()
                .userId(u.getUserId())
                .email(u.getEmail())
                .userName(u.getUserName())
                .password(u.getPassword())
                .socialType(u.getSocialType())
                .socialId(u.getSocialId())
                .role(u.getRole())
                .refreshToken(u.getRefreshToken())
                .nickname(req.getNickname() != null ? req.getNickname() : u.getNickname())
                .address(req.getAddress() != null ? req.getAddress() : u.getAddress())
                .createdAt(u.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
        updated = userRepository.save(updated);

        MypageResponse resp = new MypageResponse(
                updated.getUserId(), updated.getEmail(), updated.getUserName(), updated.getNickname(), updated.getPhoneNumber(), updated.getAddress(), updated.getCreatedAt()
        );
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/add/status")
    public ResponseEntity<MypageResponse> addStatus(@RequestBody UpdateUserStatusRequest req, HttpServletRequest request) {
        Optional<String> userIdOpt = currentUserId(request);
        if (userIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userId = userIdOpt.get();

        User u = userRepository.findById(userId).orElseThrow();
        // 최초 추가 입력 전용: 닉네임/주소 둘 다 아직 미설정이어야 하며, 요청 본문에 둘 다 제공되어야 함
        boolean alreadyHasAdditional = (u.getNickname() != null && !u.getNickname().isBlank())
                || (u.getAddress() != null && !u.getAddress().isBlank());
        if (alreadyHasAdditional) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // 409: 이미 입력됨
        }
        if (req.getNickname() == null || req.getNickname().isBlank() || req.getAddress() == null || req.getAddress().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build(); // 400: 필수값 누락
        }

        User updated = User.builder()
                .userId(u.getUserId())
                .email(u.getEmail())
                .userName(u.getUserName())
                .password(u.getPassword())
                .socialType(u.getSocialType())
                .socialId(u.getSocialId())
                .role(u.getRole())
                .refreshToken(u.getRefreshToken())
                .nickname(req.getNickname())
                .address(req.getAddress())
                .createdAt(u.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
        updated = userRepository.save(updated);

        MypageResponse resp = new MypageResponse(
                updated.getUserId(), updated.getEmail(), updated.getUserName(), updated.getNickname(), updated.getPhoneNumber(), updated.getAddress(), updated.getCreatedAt()
        );
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Optional<String> userIdOpt = currentUserId(request);
        if (userIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userId = userIdOpt.get();
        jwtService.updateRefreshTokenByUserId(userId, null);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteMe(HttpServletRequest request) {
        Optional<String> userIdOpt = currentUserId(request);
        if (userIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        String userId = userIdOpt.get();
        userRepository.deleteById(userId);
        return ResponseEntity.ok("회원 탈퇴가 완료됐습니다.");
    }

    @PostMapping("/me/profile-image")
    public ResponseEntity<?> uploadProfileImage(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                                HttpServletRequest request) {
        Optional<String> userIdOpt = currentUserId(request);
        if (userIdOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String userId = userIdOpt.get();

        String url = awsS3Service.store(file, ReForm.backend.s3.AwsS3Service.Category.PROFILE);
        User u = userRepository.findById(userId).orElseThrow();
        User updated = User.builder()
                .userId(u.getUserId())
                .email(u.getEmail())
                .userName(u.getUserName())
                .password(u.getPassword())
                .socialType(u.getSocialType())
                .socialId(u.getSocialId())
                .role(u.getRole())
                .refreshToken(u.getRefreshToken())
                .nickname(u.getNickname())
                .address(u.getAddress())
                .phoneNumber(u.getPhoneNumber())
                .createdAt(u.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .profileImageUrl(url)
                .build();
        userRepository.save(updated);
        return ResponseEntity.ok(Map.of("profileImageUrl", url));
    }

    private Optional<String> currentUserId(HttpServletRequest request) {
        Optional<String> accessToken = jwtService.extractAccessToken(request);
        Optional<String> byUserId = accessToken.flatMap(jwtService::extractUserId);
        if (byUserId.isPresent()) return byUserId;
        return accessToken
                .flatMap(jwtService::extractEmail)
                .flatMap(email -> userRepository.findFirstByEmailOrderByCreatedAtDesc(email).map(User::getUserId));
    }

    @Getter
    @AllArgsConstructor
    public static class MypageResponse {
        private String userId;
        private String email;
        private String userName;
        private String nickname;
        private String phoneNumber;
        private String address;
        private LocalDateTime createdAt;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateUserStatusRequest {
        private String nickname;
        private String address;
    }
}


