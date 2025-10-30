package ReForm.backend.user.controller;

import ReForm.backend.s3.AwsS3Service;
import ReForm.backend.user.User;
import ReForm.backend.user.repository.UserRepository;
import ReForm.backend.user.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AwsS3Service awsS3Service;

    /**
     * 최초 추가정보 단계에서 프로필 이미지 등록(선택)
     * - Path: POST /user/profile
     * - Form: file (optional)
     * - 동작: 파일이 있으면 업로드 후 URL 저장, 없으면 기본 이미지 URL 저장
     */
    @PostMapping("/profile")
    public ResponseEntity<?> uploadOptionalProfile(@RequestParam(value = "file", required = false) MultipartFile file,
                                                   HttpServletRequest request) {
        Optional<String> userIdOpt = currentUserId(request);
        if (userIdOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String userId = userIdOpt.get();

        User u = userRepository.findById(userId).orElseThrow();
        String url;
        if (file != null && !file.isEmpty()) {
            // 프로필 전용 경로에 업로드
            url = awsS3Service.store(file, ReForm.backend.s3.AwsS3Service.Category.PROFILE);
        } else {
            // 기본 이미지 경로 설정 (정적 리소스: src/main/resources/static/basicProfile/basicUSerImage.png)
            url = "/basicProfile/basicUSerImage.png";
        }

        User updated = User.builder()
                .userId(u.getUserId())
                .email(u.getEmail())
                .userName(u.getUserName())
                .password(u.getPassword())
                .socialType(u.getSocialType())
                .socialId(u.getSocialId())
                .role(u.getRole())
                .nickname(u.getNickname())
                .address(u.getAddress())
                .phoneNumber(u.getPhoneNumber())
                .createdAt(u.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .profileImageUrl(url)
                .build();
        userRepository.save(updated);

        boolean skipped = (file == null || file.isEmpty());
        return ResponseEntity.ok(Map.of(
                "profileImageUrl", url,
                "skipped", skipped,
                "message", skipped ? "기본 프로필 이미지가 설정되었습니다." : "프로필 이미지가 등록되었습니다."
        ));
    }

    private Optional<String> currentUserId(HttpServletRequest request) {
        return jwtService.extractAccessToken(request)
                .flatMap(jwtService::extractUserId)
                .or(() -> jwtService.extractAccessToken(request)
                        .flatMap(jwtService::extractEmail)
                        .flatMap(email -> userRepository.findFirstByEmailOrderByCreatedAtDesc(email).map(User::getUserId)));
    }
}


