package ReForm.backend.ai.DTO;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationResult { // AI가 평가한 문장

    private String recommendation;   // 재사용 권장 등 평가 문장

    private Difficulty difficulty;   // DIY 난이도(enum)

    private String requiredTools;    // 필요 도구 목록

    private String estimatedTime;    // 예상 소요 시간

    private String tutorialLink;     // 튜토리얼 링크

    public enum Difficulty {
        easy, medium, hard
    }
}
