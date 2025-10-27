package ReForm.backend.community;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ReForm.backend.s3.AwsS3Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityImageService {

	private final AwsS3Service awsS3Service;

	/**
	 * 커뮤니티 단일 이미지 업로드
	 * - 커뮤니티용 prefix(community/) 하위에 저장하고 공개 URL을 반환합니다.
	 */
	public String store(MultipartFile file) {
		return awsS3Service.store(file, AwsS3Service.Category.COMMUNITY);
	}

	/**
	 * 커뮤니티 다중 이미지 업로드
	 * - 각 파일을 community/ 하위에 업로드하고 공개 URL 리스트를 반환합니다.
	 */
	public List<String> storeAll(List<MultipartFile> files) {
		return awsS3Service.uploadFiles(files, AwsS3Service.Category.COMMUNITY);
	}
}


