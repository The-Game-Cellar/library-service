package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.UserTagPreferenceDTO;
import com.thegamecellar.libraryservice.model.entity.UserTagPreference;
import com.thegamecellar.libraryservice.repository.UserTagPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TagPreferenceService {

    private final UserTagPreferenceRepository repository;
    private final LibraryWritePublisher writePublisher;

    public List<UserTagPreferenceDTO> getPreferences(String userId) {
        return repository.findByUserId(userId).stream()
                .map(p -> new UserTagPreferenceDTO(p.getTagName()))
                .toList();
    }

    @Transactional
    public List<UserTagPreferenceDTO> replacePreferences(String userId, List<String> tags) {
        repository.deleteByUserId(userId);

        List<UserTagPreference> rows = (tags == null ? Stream.<String>empty() : tags.stream())
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct()
                .map(name -> UserTagPreference.builder().userId(userId).tagName(name).build())
                .toList();

        writePublisher.publish(userId);
        if (rows.isEmpty()) return List.of();
        repository.saveAll(rows);
        return rows.stream().map(p -> new UserTagPreferenceDTO(p.getTagName())).toList();
    }
}
