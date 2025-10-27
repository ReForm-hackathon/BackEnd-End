package ReForm.backend.s3;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder

@Table(name = "uploaded_image")
public class UploadedImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "category", nullable = false)
	private String category; // ai | community | market

	@Column(name = "file_name", nullable = false)
	private String fileName; // 최종 S3 파일명 (키의 마지막 segment)

	@Column(name = "s3_key", nullable = false, length = 512)
	private String s3Key; // prefix 포함 전체 키

	@Column(name = "url", nullable = false, length = 1024)
	private String url; // 공개 접근 가능한 URL

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;
}


