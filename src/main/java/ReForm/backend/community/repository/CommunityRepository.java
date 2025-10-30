package ReForm.backend.community.repository;

import ReForm.backend.community.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Integer> {

    /**
     * 전체 커뮤니티 게시글 최신순 조회
     */
    List<Community> findAllByOrderByCreatedAtDesc();

    /**
     * 사용자별 커뮤니티 게시글 조회
     * @param userId 사용자 ID
     * @return 해당 사용자의 커뮤니티 게시글 목록
     */
    List<Community> findByUser_UserIdOrderByCreatedAtDesc(String userId);
}
