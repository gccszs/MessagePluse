package com.messagepulse.core.repository;

import com.messagepulse.core.entity.SkillInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillInstanceRepository extends JpaRepository<SkillInstance, String> {

    Optional<SkillInstance> findByInstanceId(String instanceId);

    List<SkillInstance> findBySkillConfigIdAndStatus(String skillConfigId, String status);

    List<SkillInstance> findByStatus(String status);
}
