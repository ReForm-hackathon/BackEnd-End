package ReForm.backend.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AwsS3Service {

	@Value("${aws.s3.bucket-name}")
	private String bucketName;

	@Value("${aws.region}")
	private String region;

	// 카테고리별로 S3 내에 저장될 하위 경로(prefix)를 주입받아 사용 (기본값 제공)
	@Value("${storage.subdirs.ai:ai}")
	private String aiPrefix;

	@Value("${storage.subdirs.community:community}")
	private String communityPrefix;

	@Value("${storage.subdirs.market:market}")
	private String marketPrefix;

	@Value("${storage.subdirs.profile:profile}")
	private String profilePrefix;

	private final S3Client s3Client;

	/**
	 * 업로드 카테고리 (S3 버킷 내 폴더 구분용)
	 */
	public enum Category { AI, COMMUNITY, MARKET, PROFILE }

	/**
	 * 단일 파일 업로드
	 * - S3에 파일을 업로드하고 공개 URL을 반환합니다.
	 * - 컨트롤러에서 사용하는 메서드 시그니처를 유지하기 위해 메서드명을 store로 제공
	 */
	public String store(MultipartFile multipartFile, Category category) {
		if (multipartFile == null || multipartFile.isEmpty()) {
			throw new IllegalArgumentException("업로드할 파일이 비어 있습니다.");
		}

		String originalName = multipartFile.getOriginalFilename();
		String uniqueName = createUniqueFileName(originalName);
		String key = buildObjectKey(category, uniqueName);

		// 업로드 시작 로그 (버킷/리전/키/파일 크기/컨텐츠타입)
		log.info("[S3] 업로드 시작 - bucket={}, region={}, key={}, size={}, contentType={}",
				bucketName, region, key, multipartFile.getSize(), multipartFile.getContentType());

		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucketName)
				.key(key)
				.contentType(multipartFile.getContentType())
				.acl(ObjectCannedACL.PUBLIC_READ) // 업로드 즉시 공개 읽기 권한 부여
				.build();

		try {
			s3Client.putObject(request, RequestBody.fromInputStream(multipartFile.getInputStream(), multipartFile.getSize()));
		} catch (Exception e) {
			log.error("[S3] 업로드 실패 - key={}, 원인={}", key, e.getMessage(), e);
			throw new IllegalStateException("S3 업로드 실패: " + e.getMessage(), e);
		}

		String url = buildPublicUrl(key);
		log.info("[S3] 업로드 성공 - key={}, url={}", key, url);
		return url;
	}

	/**
	 * 바이트 배열 업로드 (기본 이미지 등 리소스를 직접 업로드할 때 사용)
	 * - originalFilename과 contentType을 전달받아 S3에 저장하고 공개 URL을 반환합니다.
	 */
	public String store(byte[] bytes, String originalFilename, String contentType, Category category) {
		if (bytes == null || bytes.length == 0) {
			throw new IllegalArgumentException("업로드할 데이터가 비어 있습니다.");
		}

		String uniqueName = createUniqueFileName(originalFilename);
		String key = buildObjectKey(category, uniqueName);

		log.info("[S3] 업로드(바이트) 시작 - bucket={}, region={}, key={}, size={}, contentType={}",
				bucketName, region, key, bytes.length, contentType);

		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucketName)
				.key(key)
				.contentType(contentType)
				.acl(ObjectCannedACL.PUBLIC_READ)
				.build();

		try {
			s3Client.putObject(request, RequestBody.fromBytes(bytes));
		} catch (Exception e) {
			log.error("[S3] 업로드(바이트) 실패 - key={}, 원인={}", key, e.getMessage(), e);
			throw new IllegalStateException("S3 업로드 실패: " + e.getMessage(), e);
		}

		String url = buildPublicUrl(key);
		log.info("[S3] 업로드(바이트) 성공 - key={}, url={}", key, url);
		return url;
	}

	/**
	 * 다중 파일 업로드
	 * - 각 파일마다 store를 호출하여 업로드하고, 업로드된 공개 URL 목록을 반환합니다.
	 */
	public List<String> uploadFiles(List<MultipartFile> multipartFiles, Category category) {
		if (multipartFiles == null || multipartFiles.isEmpty()) {
			return List.of();
		}
		List<String> urls = new ArrayList<>();
		for (MultipartFile file : multipartFiles) {
			urls.add(store(file, category));
		}
		return urls;
	}

	/**
	 * (기존 시그니처 유지) 리스트 인자만 받는 업로드 메서드
	 * - 기본 카테고리를 AI로 가정하여 업로드합니다.
	 */
	public List<String> uploadFile(List<MultipartFile> multipartFiles) {
		return uploadFiles(multipartFiles, Category.AI);
	}

	/**
	 * AI 전용 단일 업로드 헬퍼
	 */
	public String storeForAI(MultipartFile multipartFile) {
		return store(multipartFile, Category.AI);
	}

	/**
	 * AI 전용 다중 업로드 헬퍼
	 */
	public List<String> storeAllForAI(List<MultipartFile> multipartFiles) {
		return uploadFiles(multipartFiles, Category.AI);
	}

	/**
	 * 원본 파일명에서 확장자를 유지한 채 UUID 기반 유니크 파일명 생성
	 */
	private String createUniqueFileName(String originalFilename) {
		String ext = getExtension(originalFilename);
		return UUID.randomUUID().toString().replace("-", "") + ext;
	}

	/**
	 * 확장자 추출 (없으면 빈 문자열)
	 */
	private String getExtension(String originalFilename) {
		if (originalFilename == null) return "";
		int dot = originalFilename.lastIndexOf('.');
		return dot >= 0 ? originalFilename.substring(dot) : "";
	}

	/**
	 * 카테고리와 파일명을 결합해 S3 오브젝트 키(prefix/filename) 생성
	 */
	private String buildObjectKey(Category category, String fileName) {
		String prefix;
		switch (category) {
			case COMMUNITY -> prefix = communityPrefix;
			case MARKET -> prefix = marketPrefix;
			case AI -> {
				prefix = aiPrefix;
			}
			case PROFILE -> {
				prefix = profilePrefix;
			}
			default -> {
				prefix = aiPrefix;
			}
		}
		if (prefix.endsWith("/")) {
			return prefix + fileName;
		}
		return prefix + "/" + fileName;
	}

	/**
	 * S3 공개 URL 생성 (버킷이 public-read 가정)
	 */
	private String buildPublicUrl(String key) {
		return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
	}
}


