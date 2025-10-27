package ReForm.backend.market;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ReForm.backend.s3.AwsS3Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketImageService {

	private final AwsS3Service awsS3Service;

	/**
	 * 마켓 단일 이미지 업로드
	 * - 마켓용 prefix(market/) 하위에 저장하고 공개 URL을 반환합니다.
	 */
	public String store(MultipartFile file) {
		return awsS3Service.store(file, AwsS3Service.Category.MARKET);
	}

	/**
	 * 마켓 다중 이미지 업로드
	 * - 각 파일을 market/ 하위에 업로드하고 공개 URL 리스트를 반환합니다.
	 */
	public List<String> storeAll(List<MultipartFile> files) {
		return awsS3Service.uploadFiles(files, AwsS3Service.Category.MARKET);
	}
}


