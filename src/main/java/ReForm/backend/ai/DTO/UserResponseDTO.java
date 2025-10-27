package ReForm.backend.ai.DTO;

import lombok.Data;

@Data
public class UserResponseDTO {

    private ImageAnalysisResult analysis_result; // 이미지 분석한 결과
    private EvaluationResult evaluation_result; // AI 평가 결과
}
