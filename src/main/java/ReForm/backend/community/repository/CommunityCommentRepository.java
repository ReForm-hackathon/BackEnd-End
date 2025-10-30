package ReForm.backend.community.repository;

import ReForm.backend.community.CommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Integer> {

    List<CommunityComment> findByCommunity_CommunityIdOrderByCreatedAtDesc(Integer communityId);

    /**
     * 특정 게시글의 댓글 개수
     */
    long countByCommunity_CommunityId(Integer communityId);
}
