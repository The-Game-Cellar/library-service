package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Propagates upstream derived-genre changes into existing libraries; per-read healStaleMetadata only fills NULL-marker rows.
@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryAdminService {

    private static final int PAGE_SIZE = 200;
    private static final int SAMPLE_SIZE = 30;

    private final UserGameRepository userGameRepository;
    private final GameServiceClient gameServiceClient;

    @Transactional
    public Map<String, Object> refreshGameInfo(String bearerToken) {
        long examined = 0, updated = 0;
        long genresChanged = 0, themesChanged = 0, tagsChanged = 0, releasedChanged = 0;
        long gameServiceMisses = 0;
        List<String> sampleUpdated = new ArrayList<>();

        int page = 0;
        while (true) {
            Page<UserGame> chunk = userGameRepository.findAll(PageRequest.of(page, PAGE_SIZE));
            if (chunk.isEmpty()) break;

            for (UserGame game : chunk.getContent()) {
                examined++;

                GameServiceClient.GameInfo info = gameServiceClient.getGameInfo(game.getIgdbGameId(), bearerToken);
                if (isEmpty(info)) {
                    // Game Service down or game not cached upstream; leave the row alone.
                    gameServiceMisses++;
                    continue;
                }

                String newReleased = info.released() == null ? "" : info.released();
                boolean changed = false;

                if (!setEquals(game.getGenres(), info.genres())) {
                    replaceCollection(game.getGenres(), info.genres());
                    genresChanged++;
                    changed = true;
                }
                if (!setEquals(game.getThemes(), info.themes())) {
                    replaceCollection(game.getThemes(), info.themes());
                    themesChanged++;
                    changed = true;
                }
                if (!setEquals(game.getTags(), info.tags())) {
                    replaceCollection(game.getTags(), info.tags());
                    tagsChanged++;
                    changed = true;
                }
                if (!java.util.Objects.equals(game.getReleased(), newReleased)) {
                    game.setReleased(newReleased);
                    releasedChanged++;
                    changed = true;
                }

                if (changed) {
                    game.setMetadataSyncedAt(LocalDateTime.now());
                    updated++;
                    userGameRepository.save(game);
                    if (sampleUpdated.size() < SAMPLE_SIZE) {
                        sampleUpdated.add(game.getGameName());
                    }
                }
            }

            if (!chunk.hasNext()) break;
            page++;
        }

        log.info("Library refresh complete: examined={} updated={} genresChanged={} themesChanged={} tagsChanged={} releasedChanged={} gameServiceMisses={}",
                examined, updated, genresChanged, themesChanged, tagsChanged, releasedChanged, gameServiceMisses);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examined", examined);
        result.put("updated", updated);
        result.put("genresChanged", genresChanged);
        result.put("themesChanged", themesChanged);
        result.put("tagsChanged", tagsChanged);
        result.put("releasedChanged", releasedChanged);
        result.put("gameServiceMisses", gameServiceMisses);
        result.put("sampleUpdated", sampleUpdated);
        return result;
    }

    // Preserves the Hibernate-managed collection reference. Clearing + addAll triggers orphan
    // removal + insert; replacing the field outright would detach the original collection and
    // cause "found shared references" exceptions on flush.
    private static void replaceCollection(List<String> existing, List<String> incoming) {
        existing.clear();
        if (incoming != null) {
            existing.addAll(incoming);
        }
    }

    private static boolean setEquals(List<String> a, List<String> b) {
        return toSet(a).equals(toSet(b));
    }

    private static Set<String> toSet(List<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        Set<String> result = new HashSet<>();
        for (String v : values) {
            if (v != null) {
                String trimmed = v.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private static boolean isEmpty(GameServiceClient.GameInfo info) {
        return info.name() == null
                && info.genres().isEmpty()
                && info.themes().isEmpty()
                && info.tags().isEmpty();
    }
}
