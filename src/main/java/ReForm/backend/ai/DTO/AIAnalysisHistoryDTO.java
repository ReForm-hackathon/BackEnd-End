package ReForm.backend.ai.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysisHistoryDTO {

    private Long historyId;
    private String imageUrl;
    private String prompt;
    private String response;
    private LocalDateTime createdAt;

    // 응답에서 민감한 정보 제외 (사용자 ID는 제외)
    // 필요시 추가 필드 포함 가능
}
