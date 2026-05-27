package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.UserGenrePreferenceDTO;
import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import com.thegamecellar.libraryservice.repository.UserGenrePreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class GenrePreferenceService {

    private final UserGenrePreferenceRepository repository;
    private final LibraryWritePublisher writePublisher;

    public List<UserGenrePreferenceDTO> getPreferences(String userId) {
        return repository.findByUserId(userId).stream()
                .map(p -> new UserGenrePreferenceDTO(p.getGenreName()))
                .toList();
    }

    @Transactional
    public List<UserGenrePreferenceDTO> replacePreferences(String userId, List<String> genres) {
        repository.deleteByUserId(userId);

        List<UserGenrePreference> rows = (genres == null ? Stream.<String>empty() : genres.stream())
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct()
                .map(name -> UserGenrePreference.builder().userId(userId).genreName(name).build())
                .toList();

        writePublisher.publish(userId);
        if (rows.isEmpty()) return List.of();
        repository.saveAll(rows);
        return rows.stream().map(p -> new UserGenrePreferenceDTO(p.getGenreName())).toList();
    }
}
