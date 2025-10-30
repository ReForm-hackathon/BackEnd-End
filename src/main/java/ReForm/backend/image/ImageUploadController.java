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
import ReForm.backend.ai.entity.AIAnalysisHistory;
import ReForm.backend.ai.repository.AIAnalysisHistoryRepository;
import ReForm.backend.ai.DTO.AIAnalysisHistoryDTO;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import ReForm.backend.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/image/upload")
@Slf4j
public class ImageUploadController {

	private final AwsS3Service awsS3Service;
	private final CommunityImageService communityImageService;
	private final MarketImageService marketImageService;
	private final UploadedImageRepository uploadedImageRepository;
	private final AIAnalysisHistoryRepository aiAnalysisHistoryRepository;
	private final ObjectMapper objectMapper;
	private final UserRepository userRepository;
	private final ReForm.backend.config.OpenAiConfig openAiConfig;

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
	 * OpenAI 분석 히스토리 조회
	 * - 경로: GET /image/upload/history
	 * - 최신 10개 히스토리 반환
	 */
	@GetMapping("/history")
	public ResponseEntity<Map<String, Object>> getAnalysisHistory() {
		try {
			// 현재 인증된 사용자 ID 추출
			String userId = getCurrentUserId();
			if (userId == null) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.body(Map.of("error", "인증이 필요합니다."));
			}

			log.info("[/image/upload/history] 요청 수신 - userId={}", userId);

			// 최신 10개 히스토리 조회
			List<AIAnalysisHistory> histories = aiAnalysisHistoryRepository
					.findTop10ByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10));

			// DTO로 변환
			List<AIAnalysisHistoryDTO> historyDTOs = histories.stream()
					.map(this::convertToDTO)
					.collect(Collectors.toList());

			Map<String, Object> response = new HashMap<>();
			response.put("histories", historyDTOs);
			response.put("totalCount", historyDTOs.size());

			log.info("[/image/upload/history] 응답 완료 - userId={}, count={}", userId, historyDTOs.size());
			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("[/image/upload/history] 에러 발생", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", e.getMessage()));
		}
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

		// 3) 응답
		Map<String, Object> body = new HashMap<>();
		body.put("message", "이미지가 성공적으로 등록되었습니다.");
		body.put("url", url);
		log.info("[/image/upload/ai] 요청 처리 완료 - user_id={}, url={}", userId, url);
		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	/**
	 * 이미지 URL 기반 업사이클링 분석 API
	 * - 경로: POST /image/upload/analyze-by-url
	 * - 요청: JSON { "imageUrl": "...", "userPrompt": "..." }
	 * - 처리: DEFAULT_PROMPT + userPrompt + imageUrl → OpenAI Vision API
	 * - 응답: 업사이클링 분석 JSON
	 */
	@PostMapping("/analyze-by-url")
	public ResponseEntity<?> analyzeByUrl(@RequestBody Map<String, String> request) {
		try {
			String imageUrl = request.get("imageUrl");
			String userPrompt = request.getOrDefault("prompt", "");

			log.info("[/analyze-by-url] 요청 수신 - imageUrl={}, userPrompt={}", imageUrl, userPrompt);

			if (imageUrl == null || imageUrl.isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("error", "imageUrl is required"));
			}

			// DEFAULT_PROMPT + userPrompt 결합
			String DEFAULT_PROMPT = """
                    역할: 너는 업사이클링 제품을 추천해주는 AI야 사용자가 사진을 찍어 올리면 이미지를 확인하고 그 제품을 어떻게 업사이클링하면 좋을지 알려주는 AI야
                    기능: 너의 기능은 사용자의 사진을 정확히 분석해서 더 사용 가능한 제품인지, 업사이클링 가능한 제품인지, 버려야 할 제품인지를 판단해주는 AI로 DIY등급을 상, 중, 하 순으로 매기고 업사이클링이 가능한 제품이라고 판단되면 어떤 방식으로 업사이클링 해주면 좋을지, 또한 어떤 방식으로 업사이클링을 해야하는지, 어떤 재료가 필요한지, 어느 정도 금액이 드는지를 확실히 분석해주는 AI야
                    목표: 사용자가 너한테 물어보면 근거를 가지고 간단하고 정확하게 답변해줘
                    스타일 : 반말 금지, 존댓말 유지, 불확실하면 추정 금지.
                    출력형식 : 1. 물체 종류 : , 2. 재질 : , 3. DIY 등급 : , 4. 업사이클링 방안 추천 : 형식, 방안 추천은 3개정도 해줘
                """;

			String combinedPrompt = DEFAULT_PROMPT;
			if (!userPrompt.isEmpty()) {
				combinedPrompt += "\n\n사용자 질문: " + userPrompt;
			}

			// OpenAI Vision API 직접 호출
			String analysisJson = openAiConfig.callVisionAPI(combinedPrompt, imageUrl);
			log.info("[/analyze-by-url] OpenAI 응답 수신");

			// OpenAI 응답에서 업사이클링 방안 추출
			String upcyclingPlan = extractUpcyclingPlan(analysisJson);
			log.info("[/analyze-by-url] 추출된 업사이클링 방안 - 길이: {}, 내용: {}",
					upcyclingPlan != null ? upcyclingPlan.length() : 0,
					upcyclingPlan != null && upcyclingPlan.length() > 100 ?
							upcyclingPlan.substring(0, 100) + "..." : upcyclingPlan);

			// 업사이클링 방안에서 제목만 추출
			String upcyclingTitles = extractUpcyclingTitles(upcyclingPlan);
			log.info("[/analyze-by-url] 추출된 업사이클링 제목: {}", upcyclingTitles);

			// 업사이클링 방안이 있으면 Perplexity API에 전달
			String perplexityResponse = null;
			if (upcyclingTitles != null && !upcyclingTitles.isEmpty()) {
				String perplexityPrompt = String.format("""
                                업사이클링 방안과 관련된 url을 찾아주는데 영상정보가 담겨있는 url이면 좋아 업사이클링 제목을 유튜브나 블로그와 같은 자료를 보여줘
                                방안 하나당 url하나만 보여주고
                                업사이클링 방안1 : url1
                                업사이클링 방안2 : url2
                                업사이클링 방안3 : url3
                                url은 무조건 업사이클링과 관련된 자료여야 해
                                예를 들어 깨진 화분으로 만드는 정원 장식 만들기면 이거와 관련된 url을 줘야해 업사이클링 제목과 무조건 일치하게 일치하지 않는 주소면 null값을 줘
                                이 형태로만 값을 반환해줘
                                존재하지 않는 페이지는 반환하지 마
                                업사이클링 방안 제목들:
                                %s
                                """
						,
						upcyclingTitles
				);
				log.info("[/analyze-by-url] Perplexity API에 전달되는 프롬프트 - 길이: {}", perplexityPrompt.length());
				perplexityResponse = openAiConfig.callPerplexityAPI(perplexityPrompt);
				log.info("[/analyze-by-url] Perplexity API 응답 수신");

				// null 값 처리
				if (perplexityResponse != null &&
						(perplexityResponse.trim().equalsIgnoreCase("null") ||
								perplexityResponse.trim().equalsIgnoreCase("없음") ||
								perplexityResponse.trim().isEmpty())) {
					perplexityResponse = null;
					log.info("[/analyze-by-url] Perplexity API가 null을 반환했습니다.");
				}
			} else {
				log.warn("[/analyze-by-url] 업사이클링 방안이 추출되지 않아 Perplexity API 호출을 건너뜁니다.");
			}

			// JSON 파싱을 우선 시도하고, 실패 시 텍스트로 반환
			Object analysis;
			try {
				analysis = objectMapper.readValue(analysisJson, Object.class);
			} catch (Exception parseEx) {
				log.warn("[/analyze-by-url] JSON 파싱 실패 - 텍스트로 반환합니다.");
				analysis = analysisJson;
			}

			// 결과에 Perplexity 응답 추가
			Map<String, Object> responseMap = new HashMap<>();
			if (analysis instanceof Map) {
				responseMap.putAll((Map<String, Object>) analysis);
			} else {
				responseMap.put("openaiResponse", analysis);
			}

			if (perplexityResponse != null) {
				responseMap.put("perplexityDetails", perplexityResponse);
			}

			// 히스토리 저장 (사용자 프롬프트만 저장)
			saveAnalysisHistory(imageUrl, userPrompt, analysisJson);

			return ResponseEntity.ok(Map.of("analysis", responseMap));

		} catch (Exception e) {
			log.error("[/analyze-by-url] 에러 발생", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", e.getMessage()));
		}
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

	/**
	 * 현재 인증된 사용자 ID 추출
	 */
	private String getCurrentUserId() {
		try {
			String email = org.springframework.security.core.context.SecurityContextHolder.getContext()
					.getAuthentication() != null ?
					org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName() : null;

			if (email == null) {
				return null;
			}

            return userRepository.findFirstByEmailOrderByCreatedAtDesc(email)
					.map(ReForm.backend.user.User::getUserId)
					.orElse(null);
		} catch (Exception e) {
			log.error("사용자 ID 추출 실패", e);
			return null;
		}
	}

	/**
	 * AI 분석 히스토리 저장
	 */
	private void saveAnalysisHistory(String imageUrl, String prompt, String response) {
		try {
			String userId = getCurrentUserId();
			if (userId == null) {
				log.warn("사용자 ID가 없어 히스토리 저장을 건너뜁니다.");
				return;
			}

			ReForm.backend.user.User user = userRepository.findById(userId).orElse(null);
			if (user == null) {
				log.warn("사용자를 찾을 수 없어 히스토리 저장을 건너뜁니다. userId={}", userId);
				return;
			}

			AIAnalysisHistory history = AIAnalysisHistory.builder()
					.user(user)
					.imageUrl(imageUrl)
					.prompt(prompt)
					.response(response)
					.build();

			aiAnalysisHistoryRepository.save(history);
			log.info("AI 분석 히스토리 저장 완료 - userId={}, historyId={}", userId, history.getHistoryId());

		} catch (Exception e) {
			log.error("AI 분석 히스토리 저장 실패", e);
		}
	}

	/**
	 * Entity를 DTO로 변환
	 */
	private AIAnalysisHistoryDTO convertToDTO(AIAnalysisHistory history) {
		return AIAnalysisHistoryDTO.builder()
				.historyId(history.getHistoryId())
				.imageUrl(history.getImageUrl())
				.prompt(history.getPrompt())
				.response(history.getResponse())
				.createdAt(history.getCreatedAt())
				.build();
	}

	/**
	 * OpenAI 응답에서 업사이클링 방안 추천 부분 추출
	 * @param openAiResponse OpenAI 응답 텍스트
	 * @return 추출된 업사이클링 방안 텍스트
	 */
	private String extractUpcyclingPlan(String openAiResponse) {
		if (openAiResponse == null || openAiResponse.isEmpty()) {
			return null;
		}

		// "4. 업사이클링 방안 추천 :" 또는 "업사이클링 방안 추천" 패턴 찾기
		String[] patterns = {
				"4\\.\\s*업사이클링\\s*방안\\s*추천\\s*:",
				"업사이클링\\s*방안\\s*추천\\s*:",
				"4\\.\\s*업사이클링\\s*방안\\s*:"
		};

		for (String pattern : patterns) {
			java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern,
					java.util.regex.Pattern.CASE_INSENSITIVE);
			java.util.regex.Matcher matcher = regex.matcher(openAiResponse);

			if (matcher.find()) {
				int startIdx = matcher.end();

				// "5. 추천 자료" 또는 끝까지 추출
				int endIdx = openAiResponse.length();
				java.util.regex.Pattern endPattern = java.util.regex.Pattern.compile(
						"5\\.\\s*추천\\s*자료", java.util.regex.Pattern.CASE_INSENSITIVE);
				java.util.regex.Matcher endMatcher = endPattern.matcher(openAiResponse);
				if (endMatcher.find() && endMatcher.start() > startIdx) {
					endIdx = endMatcher.start();
				}

				String extracted = openAiResponse.substring(startIdx, endIdx).trim();
				if (!extracted.isEmpty()) {
					log.info("업사이클링 방안 추출 성공 - 길이: {}", extracted.length());
					return extracted;
				}
			}
		}

		log.warn("업사이클링 방안을 찾을 수 없습니다.");
		return null;
	}

	/**
	 * 업사이클링 방안에서 제목만 추출
	 * @param upcyclingPlan 전체 업사이클링 방안 텍스트
	 * @return 추출된 제목들 (줄바꿈으로 구분)
	 */
	private String extractUpcyclingTitles(String upcyclingPlan) {
		if (upcyclingPlan == null || upcyclingPlan.isEmpty()) {
			return null;
		}

		// 줄바꿈으로 분리
		String[] lines = upcyclingPlan.split("\n");
		StringBuilder titles = new StringBuilder();

		// 각 줄에서 제목 추출 (번호로 시작하는 패턴)
		java.util.regex.Pattern titlePattern = java.util.regex.Pattern.compile(
				"^\\s*(?:\\d+[.\\-]?|•|[-*])\\s*(.+?)(?:[:]|$)",
				java.util.regex.Pattern.MULTILINE
		);

		for (String line : lines) {
			line = line.trim();
			if (line.isEmpty()) continue;

			java.util.regex.Matcher matcher = titlePattern.matcher(line);
			if (matcher.find()) {
				String title = matcher.group(1).trim();
				// 설명 부분 제거 (첫 번째 줄바꿈이나 문장 끝까지)
				if (title.contains("\n")) {
					title = title.split("\n")[0];
				}
				// 너무 긴 경우 첫 문장만 추출
				if (title.length() > 100) {
					// 첫 문장만 추출
					int dotIdx = title.indexOf('.');
					int commaIdx = title.indexOf(',');
					int endIdx = Math.min(
							dotIdx > 0 ? dotIdx : title.length(),
							commaIdx > 0 ? commaIdx : title.length()
					);
					if (endIdx < title.length() && endIdx > 10) {
						title = title.substring(0, endIdx).trim();
					} else {
						title = title.substring(0, Math.min(100, title.length()));
					}
				}

				if (!title.isEmpty()) {
					if (titles.length() > 0) {
						titles.append("\n");
					}
					titles.append(title);
				}
			} else {
				// 패턴이 없으면 첫 줄만 사용 (제목으로 간주)
				if (titles.length() == 0 && line.length() < 100) {
					String firstLine = line.split("[:.]")[0].trim();
					if (!firstLine.isEmpty()) {
						titles.append(firstLine);
					}
				}
			}
		}

		String result = titles.toString().trim();
		if (result.isEmpty()) {
			// 제목 추출 실패 시 원본 반환 (첫 3줄)
			String[] firstLines = upcyclingPlan.split("\n");
			StringBuilder fallback = new StringBuilder();
			for (int i = 0; i < Math.min(3, firstLines.length); i++) {
				String line = firstLines[i].trim();
				if (!line.isEmpty()) {
					// 설명 부분 제거
					if (line.contains(":")) {
						line = line.split(":")[0] + ": " + line.split(":")[1].split("\\.")[0];
					}
					if (fallback.length() > 0) {
						fallback.append("\n");
					}
					fallback.append(line.substring(0, Math.min(100, line.length())));
				}
			}
			result = fallback.toString().trim();
		}

		return result.isEmpty() ? null : result;
	}

}


