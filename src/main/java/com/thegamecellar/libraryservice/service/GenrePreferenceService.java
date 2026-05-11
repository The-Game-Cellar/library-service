package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.UserGenrePreferenceDTO;
import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import com.thegamecellar.libraryservice.repository.UserGenrePreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GenrePreferenceService {

    private final UserGenrePreferenceRepository repository;

    public List<UserGenrePreferenceDTO> getPreferences(String userId) {
        return repository.findByUserId(userId).stream()
                .map(p -> new UserGenrePreferenceDTO(p.getGenreName()))
                .toList();
    }

    /**
     * Replace-all semantics: delete the user's current preferences and insert the supplied
     * set in a single transaction. Empty input is valid — clears all preferences. Duplicate
     * names in the input are deduped (case-insensitive on trim) before insert; blank entries
     * are dropped silently.
     */
    @Transactional
    public List<UserGenrePreferenceDTO> replacePreferences(String userId, List<String> genres) {
        repository.deleteByUserId(userId);

        Set<String> deduped = dedupeAndClean(genres);
        if (deduped.isEmpty()) {
            return List.of();
        }

        List<UserGenrePreference> rows = deduped.stream()
                .map(name -> UserGenrePreference.builder()
                        .userId(userId)
                        .genreName(name)
                        .build())
                .toList();
        repository.saveAll(rows);

        return rows.stream()
                .map(p -> new UserGenrePreferenceDTO(p.getGenreName()))
                .toList();
    }

    private static Set<String> dedupeAndClean(List<String> genres) {
        if (genres == null) return Set.of();
        Set<String> seen = new LinkedHashSet<>();
        for (String g : genres) {
            if (g == null) continue;
            String trimmed = g.trim();
            if (trimmed.isEmpty()) continue;
            seen.add(trimmed);
        }
        return seen;
    }
}
