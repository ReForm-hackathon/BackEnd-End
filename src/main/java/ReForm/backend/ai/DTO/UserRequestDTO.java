package ReForm.backend.ai.DTO;

import ReForm.backend.user.User;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UserRequestDTO {

    private MultipartFile imageFile; // 사용자가 올리는 이미지 파일
    private User user_id; // 사용자 식별 사용자 id
    private String description; // 사용자가 추가로 작성하는 설명
    private String storedImagePath; // 저장된 이미지 상대 경로 (nullable)
}
