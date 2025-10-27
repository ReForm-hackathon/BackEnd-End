package ReForm.backend.community;

import ReForm.backend.user.User;
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

@Table(name = "community")
public class Community {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "community_id")
	private Integer communityId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "title")
	private String title;

	@Column(name = "content", columnDefinition = "TEXT")
	private String content;

	@Column(name = "image")
	private String image;

	@Column(name = "created_at")
	private LocalDateTime createdAt;
}


