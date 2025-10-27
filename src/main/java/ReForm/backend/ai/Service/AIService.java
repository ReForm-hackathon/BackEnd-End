package ReForm.backend.ai.Service;

import ReForm.backend.ai.AIChatAnswer;
import ReForm.backend.ai.DTO.EvaluationResult;
import ReForm.backend.ai.DTO.ImageAnalysisResult;
import ReForm.backend.ai.DTO.UserRequestDTO;
import ReForm.backend.ai.DTO.UserResponseDTO;
import ReForm.backend.ai.repository.ImageAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AIService {

    public final ImageAnalysisRepository imageAnalysisRepository;

    public AIService(ImageAnalysisRepository imageAnalysisRepository) {
        this.imageAnalysisRepository = imageAnalysisRepository;
    }

    /**
     * 사용자가 업로드한 이미지 + AI 분석 결과를 받아서 DB에 저장하는 메서드
     *
     *  @param userResponseDTO AI가 분석 및 평가한 결과 (재질, 손상상태, 추천 등)
     * @return 저장 후 그대로 반환된 AI 응답 DTO@param userRequestDTO  사용자 요청 정보 (이미지, userId, 설명 등)
     *      *
     */

    @Transactional
    public UserResponseDTO analyzeAndSave(UserRequestDTO userRequestDTO, UserResponseDTO userResponseDTO) {

        // 반환값이 null이 아니어야 한다는 "전제 조건"을 확실하게 만드는 코드 (NULL 값이 들어갈 경우 NPE 발생)
        ImageAnalysisResult analysis = Objects.requireNonNull(userResponseDTO.getAnalysis_result(), "analysis_result is required");
        EvaluationResult evaluation = Objects.requireNonNull(userResponseDTO.getEvaluation_result(), "evaluation_result is required");

        AIChatAnswer entity = AIChatAnswer.builder()
                .user(userRequestDTO.getUser_id())
                .item(null)
                .recommendation(evaluation.getRecommendation())
                .difficulty(mapDifficulty(evaluation.getDifficulty()))
                .requiredTools(parseRequiredTools(evaluation.getRequiredTools()))
                .estimatedTime(evaluation.getEstimatedTime())
                .tutorialLink(evaluation.getTutorialLink())
                .material(analysis.getMaterial())
                .damageLevel(analysis.getDamageStatus())
                .shape(analysis.getShape())
                .build();

        imageAnalysisRepository.save(entity);
        return userResponseDTO;
    }

    private AIChatAnswer.Difficulty mapDifficulty(EvaluationResult.Difficulty difficulty) {
        if (difficulty == null) {
            return null;
        }
        switch (difficulty) {
            case easy:
                return AIChatAnswer.Difficulty.easy;
            case medium:
                return AIChatAnswer.Difficulty.medium;
            case hard:
                return AIChatAnswer.Difficulty.hard;
            default:
                return null;
        }
    }

    private List<String> parseRequiredTools(String tools) {
        if (tools == null || tools.isBlank()) {
            return null;
        }
        return Arrays.stream(tools.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
