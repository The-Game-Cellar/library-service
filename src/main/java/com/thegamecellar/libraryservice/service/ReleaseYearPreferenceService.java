package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.UserReleaseYearPreferenceDTO;
import com.thegamecellar.libraryservice.model.entity.UserReleaseYearPreference;
import com.thegamecellar.libraryservice.repository.UserReleaseYearPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ReleaseYearPreferenceService {

    // Unknown labels silently dropped (not 400) so a future scheme tweak doesn't break saved rows mid-rollout.
    public static final Set<String> VALID_BUCKETS =
            Set.of("Pre-1990", "1990s", "2000s", "2010s", "2020s");

    private final UserReleaseYearPreferenceRepository repository;
    private final LibraryWritePublisher writePublisher;

    public List<UserReleaseYearPreferenceDTO> getPreferences(String userId) {
        return repository.findByUserId(userId).stream()
                .map(p -> new UserReleaseYearPreferenceDTO(p.getBucketLabel()))
                .toList();
    }

    @Transactional
    public List<UserReleaseYearPreferenceDTO> replacePreferences(String userId, List<String> buckets) {
        repository.deleteByUserId(userId);

        List<UserReleaseYearPreference> rows = (buckets == null ? Stream.<String>empty() : buckets.stream())
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .filter(VALID_BUCKETS::contains).distinct()
                .map(label -> UserReleaseYearPreference.builder().userId(userId).bucketLabel(label).build())
                .toList();

        writePublisher.publish(userId);
        if (rows.isEmpty()) return List.of();
        repository.saveAll(rows);
        return rows.stream().map(p -> new UserReleaseYearPreferenceDTO(p.getBucketLabel())).toList();
    }
}
