package ReForm.backend.user.controller;

import ReForm.backend.s3.AwsS3Service;
import ReForm.backend.user.User;
import ReForm.backend.user.repository.UserRepository;
import ReForm.backend.user.service.JwtService;
import ReForm.backend.user.service.ProfileCompletionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDateTime;
import java.util.Optional;


@RestController
@RequiredArgsConstructor
@Slf4j
public class AdditionalInfoController {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AwsS3Service awsS3Service;
    private final ProfileCompletionService profileCompletionService;

    // 추가정보 상태 조회 (뷰 없이 JSON 반환)
    @GetMapping("/user/additional")
    public ResponseEntity<java.util.Map<String, Object>> additionalInfoPage(HttpServletRequest request) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        Optional<String> userIdOpt = currentUserId(request);
        if (userIdOpt.isEmpty()) {
            body.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(401).body(body);
        }
        User u = userRepository.findById(userIdOpt.get()).orElse(null);
        body.put("message", "추가 정보 입력이 필요합니다.");
        if (u != null) {
            body.put("nickname", u.getNickname());
            body.put("address", u.getAddress());
            body.put("profileImageUrl", u.getProfileImageUrl());
        }
        return ResponseEntity.ok(body);
    }

    // 추가정보 저장 (프로필 이미지 + 닉네임 + 주소)
    @PostMapping("/user/additional")
    public ResponseEntity<java.util.Map<String, Object>> submitAdditional(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("nickname") String nickname,
            @RequestParam("address") String address,
            HttpServletRequest request
    ) {
        Optional<String> userIdOpt = currentUserId(request);
        if (userIdOpt.isEmpty()) return ResponseEntity.status(401).body(java.util.Map.of("message", "로그인이 필요합니다."));
        String userId = userIdOpt.get();

        log.info("[ADDITIONAL] 제출 수신 - userId={}, filePresent={}, nickname='{}', address='{}'",
                userId, (file != null && !file.isEmpty()), nickname, address);

        User u = userRepository.findById(userId).orElseThrow();

        String url = u.getProfileImageUrl();
        if (file != null && !file.isEmpty()) {
            // 사용자가 업로드한 이미지를 S3 profile 경로에 저장
            url = awsS3Service.store(file, ReForm.backend.s3.AwsS3Service.Category.PROFILE);
            log.info("[ADDITIONAL] 사용자 업로드 이미지 S3 저장 완료 - url={}", url);
        } else {
            // 기본 프로필 이미지를 리소스에서 읽어 S3 profile 경로에 저장
            try {
                ClassPathResource resource = new ClassPathResource("basicProfile/basicUserImage.png");
                byte[] bytes = resource.getContentAsByteArray();
                url = awsS3Service.store(bytes, "basicUserImage.png", "image/png", ReForm.backend.s3.AwsS3Service.Category.PROFILE);
                log.info("[ADDITIONAL] 기본 프로필 이미지 S3 업로드 완료 - url={}", url);
            } catch (Exception e) {
                log.warn("기본 프로필 이미지 업로드 실패: {}. 로컬 기본 경로를 사용합니다.", e.getMessage());
                url = "/basicProfile/basicUserImage.png";
            }
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
                .nickname(nickname)
                .address(address)
                .phoneNumber(u.getPhoneNumber())
                .createdAt(u.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .profileImageUrl(url)
                .build();
        updated = userRepository.save(updated);

        log.info("[ADDITIONAL] DB 저장 완료 - userId={}, nickname='{}', address='{}', profileImageUrl={}",
                updated.getUserId(), updated.getNickname(), updated.getAddress(), updated.getProfileImageUrl());

        ProfileCompletionService.Result result = profileCompletionService.check(updated);
        if (!result.isComplete()) {
            String missing = String.join(", ", result.getMissingFields());
            log.info("[LOGIN-GATE] 누락된 값: {}", missing);
            java.util.Map<String, Object> err = new java.util.HashMap<>();
            err.put("message", "필수 누락: " + missing);
            err.put("nickname", updated.getNickname());
            err.put("address", updated.getAddress());
            err.put("profileImageUrl", updated.getProfileImageUrl());
            return ResponseEntity.badRequest().body(err);
        }

        log.info("회원의 모든 정보가 입력 완료되었습니다.");
        java.util.Map<String, Object> ok = new java.util.HashMap<>();
        ok.put("message", "등록이 완료되었습니다.");
        ok.put("nickname", updated.getNickname());
        ok.put("address", updated.getAddress());
        ok.put("profileImageUrl", updated.getProfileImageUrl());
        return ResponseEntity.ok(ok);
    }

    private Optional<String> currentUserId(HttpServletRequest request) {
        try {
            // 1) 세션 기반(SecurityContext) 우선: OAuth2 로그인 직후 폼 제출 시 헤더에 토큰이 없으므로 세션 사용자 사용
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String name = auth.getName(); // 보통 email
                if (name != null && !name.isBlank()) {
                    Optional<String> byEmail = userRepository.findByEmail(name).map(User::getUserId);
                    if (byEmail.isPresent()) return byEmail;
                }
            }

            // 2) JWT 헤더 기반 (로컬 토큰 보유 시)
            Optional<String> accessToken = jwtService.extractAccessToken(request);
            Optional<String> byUserId = accessToken.flatMap(jwtService::extractUserId);
            if (byUserId.isPresent()) return byUserId;
            return accessToken
                    .flatMap(jwtService::extractEmail)
                    .flatMap(email -> userRepository.findFirstByEmailOrderByCreatedAtDesc(email).map(User::getUserId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}



