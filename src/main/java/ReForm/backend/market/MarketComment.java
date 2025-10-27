package ReForm.backend.market;

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

@Table(name = "market_comment")
public class MarketComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "comment_id")
	private Integer commentId;

	@ManyToOne
	@JoinColumn(name = "market_id")
	private Market market;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "content")
	private String content;

	@Column(name = "created_at")
	private LocalDateTime createdAt;
}


