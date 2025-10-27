package ReForm.backend.ai.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageAnalysisResult {
    private String material;       // 재질 (예: 천)
    private String damageStatus;   // 손상 상태 (예: 양호)
    private String shape;          // 형태 (예: 의류)
}