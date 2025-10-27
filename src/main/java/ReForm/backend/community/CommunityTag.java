package ReForm.backend.community;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder

@Table(name = "community_tag")
public class CommunityTag {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "tag_id")
	private Integer tagId;

	@ManyToOne
	@JoinColumn(name = "community_id")
	private Community community;

	@Column(name = "tag_content")
	private String tagContent;
}


