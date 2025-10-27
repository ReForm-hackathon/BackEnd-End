package ReForm.backend.ai;

import ReForm.backend.item.MyItem;
import ReForm.backend.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder

@Table(name = "ai_chat_answer")
public class AIChatAnswer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "answer_id")
	private Integer answerId;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne
	@JoinColumn(name = "item_id", nullable = true)
	private MyItem item;

	@Column(name = "recommendation")
	private String recommendation;

	@Enumerated(EnumType.STRING)
	@Column(name = "difficulty")
	private Difficulty difficulty;

	@ElementCollection
	@Column(name = "required_tools")
	private List<String> requiredTools;

	@Column(name = "estimated_time")
	private String estimatedTime;

	@Column(name = "tutorial_link")
	private String tutorialLink;

	@Column(name = "material")
	private String material;

	@Column(name = "damage_level")
	private String damageLevel;

	@Column(name = "shape")
	private String shape;

	@Column(name = "image_path")
	private String imagePath;

	public enum Difficulty {
		easy, medium, hard
	}
}


