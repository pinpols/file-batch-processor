package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FeedDefinition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedDefinitionRepository extends JpaRepository<FeedDefinition, String> {

    Optional<FeedDefinition> findByFeedIdAndEnabledTrue(String feedId);
}
