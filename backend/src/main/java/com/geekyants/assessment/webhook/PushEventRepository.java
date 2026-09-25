package com.geekyants.assessment.webhook;

import com.geekyants.assessment.github.GithubRepo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushEventRepository extends JpaRepository<PushEvent, Long> {

    boolean existsByDeliveryId(String deliveryId);

    List<PushEvent> findTop20ByRepositoryOrderByReceivedAtDesc(GithubRepo repository);
}
