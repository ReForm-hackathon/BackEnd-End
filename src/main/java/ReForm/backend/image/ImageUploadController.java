package ReForm.backend.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import ReForm.backend.s3.AwsS3Service;
import ReForm.backend.s3.AwsS3Service.Category;
import ReForm.backend.community.CommunityImageService;
import ReForm.backend.market.MarketImageService;
import ReForm.backend.s3.UploadedImage;
import ReForm.backend.s3.UploadedImageRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import ReForm.backend.ai.DTO.ImageAnalysisResult;
import ReForm.backend.ai.DTO.EvaluationResult;
import ReForm.backend.ai.Service.AIService;
import ReForm.backend.user.User;
import ReForm.backend.user.repository.UserRepository;

@RestController
@RequiredArgsConstructor
@RequestMapping("/image/upload")
@Slf4j
public class ImageUploadController {

    private final AwsS3Service awsS3Service;
	private final CommunityImageService communityImageService;
	private final MarketImageService marketImageService;
	private final UploadedImageRepository uploadedImageRepository;
    private final ObjectMapper objectMapper;
    private final AIService aiService;
    private final ApplicationContext applicationContext;
    private final UserRepository userRepository;

	/**
	 * 테스트/헬스체크용 엔드포인트
	 * - 경로: GET /image/upload
	 * - 사용 가능한 업로드 엔드포인트 안내
	 */
	@GetMapping
	public ResponseEntity<Map<String, Object>> info() {
		Map<String, Object> body = new HashMap<>();
		body.put("message", "이미지 업로드 엔드포인트입니다. /ai, /community, /market 로 POST 요청을 보내세요.");
		return ResponseEntity.ok(body);
	}

	/**
	 * AI 이미지 업로드 + 분석 + 평가 + 저장
	 * - 경로: POST /image/upload/ai
	 * 처리 순서:
	 *   1) 업로드된 이미지를 S3(ai/)에 저장하고, 공개 URL을 생성
	 *   2) 업로드 메타데이터(URL 등)를 MySQL(uploaded_image)에 저장
	 *   3) AI에 이미지 URL을 전달하여 분석(JSON: material, damageStatus, shape)
	 *   4) 분석 결과를 바탕으로 AI에 평가 요청(JSON: recommendation, difficulty, requiredTools, estimatedTime, tutorialLink)
	 *   5) 분석/평가 결과를 통합하여 도메인 엔티티(AIChatAnswer)로 DB 저장
	 *   6) 클라이언트에 업로드/분석/평가 결과를 JSON으로 응답
	 */
    @PostMapping("/ai")
    public ResponseEntity<Map<String, Object>> uploadAI(@RequestParam("file") MultipartFile file) {
        // 인증된 사용자 ID를 토큰에서 추출 (헤더 기반)
        String userId = userRepository.findByEmail(
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication() != null ?
                        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName() : null
        ).map(ReForm.backend.user.User::getUserId).orElse("anonymous");

        log.info("[/image/upload/ai] 요청 수신 - user_id={}, filename={}, size={}", userId, file.getOriginalFilename(), file.getSize());

        String url = awsS3Service.store(file, Category.AI);
        log.info("[/image/upload/ai] S3 업로드 완료 - url={}", url);

        // 1~2) S3 업로드 및 업로드 메타데이터 저장
        saveMetadata("ai", url);
        log.info("[/image/upload/ai] 업로드 메타데이터 저장 완료 - category=ai, url={}", url);

        // 3) AI 분석 호출 (이미지 URL을 프롬프트에 포함)
        String analysisPrompt = "Analyze this image URL and return JSON with keys material, damageStatus, shape. URL: " + url;
        String analysisJson = callModel(analysisPrompt);
        log.debug("[/image/upload/ai] AI 분석 응답(JSON)={}", analysisJson);

        ImageAnalysisResult analysis;
        try {
            analysis = objectMapper.readValue(analysisJson, ImageAnalysisResult.class);
        } catch (Exception e) {
            analysis = ImageAnalysisResult.builder()
                .material("unknown")
                .damageStatus("unknown")
                .shape("unknown")
                .build();
        }

        // 4) 평가 요청
        String evalPrompt = "Given material=" + safe(analysis.getMaterial())
            + ", damageStatus=" + safe(analysis.getDamageStatus())
            + ", shape=" + safe(analysis.getShape())
            + ", return JSON with keys recommendation, difficulty(easy|medium|hard), requiredTools, estimatedTime, tutorialLink.";
        String evalJson = callModel(evalPrompt);
        log.debug("[/image/upload/ai] AI 평가 응답(JSON)={}", evalJson);

        EvaluationResult evaluation;
        try {
            evaluation = objectMapper.readValue(evalJson, EvaluationResult.class);
        } catch (Exception e) {
            evaluation = EvaluationResult.builder()
                .recommendation("Unable to parse AI response. Try again.")
                .difficulty(EvaluationResult.Difficulty.easy)
                .requiredTools("")
                .estimatedTime("")
                .tutorialLink("")
                .build();
        }

        // 5) 분석/평가 결과를 도메인 엔티티로 저장 (기존 AIService를 재사용)
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));

        ReForm.backend.ai.DTO.UserRequestDTO req = new ReForm.backend.ai.DTO.UserRequestDTO();
        req.setUser_id(user);
        req.setImageFile(file);
        req.setStoredImagePath(url);

        ReForm.backend.ai.DTO.UserResponseDTO res = new ReForm.backend.ai.DTO.UserResponseDTO();
        res.setAnalysis_result(analysis);
        res.setEvaluation_result(evaluation);
        aiService.analyzeAndSave(req, res);
        log.info("[/image/upload/ai] 분석/평가 결과 저장 완료 - user_id={}, imageUrl={}", userId, url);

        // 6) 업로드/분석/평가 결과 응답
        Map<String, Object> body = new HashMap<>();
        body.put("message", "이미지가 성공적으로 등록되었습니다.");
        body.put("url", url);
        body.put("analysis", analysis); // material, damageStatus, shape
        body.put("evaluation", evaluation); // recommendation, difficulty, requiredTools, estimatedTime, tutorialLink
        log.info("[/image/upload/ai] 요청 처리 완료 - user_id={}, url={}", userId, url);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

	/**
	 * 커뮤니티 이미지 업로드 엔드포인트
	 * - 경로: POST /image/upload/community
	 */
	@PostMapping("/community")
	public ResponseEntity<Map<String, Object>> uploadCommunity(@RequestParam("file") MultipartFile file) {
		String url = communityImageService.store(file);
		saveMetadata("community", url);
		return success(url);
	}

	/**
	 * 마켓 이미지 업로드 엔드포인트
	 * - 경로: POST /image/upload/market
	 */
	@PostMapping("/market")
	public ResponseEntity<Map<String, Object>> uploadMarket(@RequestParam("file") MultipartFile file) {
		String url = marketImageService.store(file);
		saveMetadata("market", url);
		return success(url);
	}

	// 업로드 메타데이터 저장: 카테고리/키/URL/시간
	private void saveMetadata(String category, String url) {
		String key = extractKeyFromUrl(url);
		String fileName = key.substring(key.lastIndexOf('/') + 1);
		uploadedImageRepository.save(UploadedImage.builder()
			.category(category)
			.fileName(fileName)
			.s3Key(key)
			.url(url)
			.createdAt(LocalDateTime.now())
			.build());
	}

	// 공개 URL에서 키(prefix 포함)만 추출
	private String extractKeyFromUrl(String url) {
		int idx = url.indexOf(".amazonaws.com/");
		if (idx < 0) return url;
		return url.substring(idx + ".amazonaws.com/".length());
	}

	private ResponseEntity<Map<String, Object>> success(String url) {
		Map<String, Object> body = new HashMap<>();
		body.put("message", "이미지가 성공적으로 등록되었습니다.");
		body.put("url", url);
		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	private String callModel(String message) {
		try {
			Object model = applicationContext.getBean("openAiChatModel");
			java.lang.reflect.Method call = model.getClass().getMethod("call", String.class);
			Object result = call.invoke(model, message);
			return result == null ? "" : result.toString();
		} catch (Exception e) {
			return "AI model not available: " + e.getMessage();
		}
	}

	private String safe(String s) {
		return s == null ? "" : s;
	}
}


