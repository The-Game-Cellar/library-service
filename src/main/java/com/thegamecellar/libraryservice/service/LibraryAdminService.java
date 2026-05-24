package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-shot admin operation that walks every {@code user_games} row, re-fetches game info
 * from Game Service, and updates the cached genres / themes / tags CSV columns whenever
 * the upstream set differs from the local copy. Drives propagation of derived-genre
 * additions (Game Service rule changes) into existing user libraries. The per-read
 * {@code healStaleMetadata} path only fixes NULL columns, so already-populated rows
 * never see new derived genres without this refresh.
 */
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
                    // Game Service down or game not cached upstream. Leave the row alone.
                    gameServiceMisses++;
                    continue;
                }

                String newGenres = joinOrEmpty(info.genres());
                String newThemes = joinOrEmpty(info.themes());
                String newTags = joinOrEmpty(info.tags());
                String newReleased = info.released() == null ? "" : info.released();

                boolean changed = false;
                if (!setEqualsCsv(game.getGenres(), newGenres)) {
                    game.setGenres(newGenres);
                    genresChanged++;
                    changed = true;
                }
                if (!setEqualsCsv(game.getThemes(), newThemes)) {
                    game.setThemes(newThemes);
                    themesChanged++;
                    changed = true;
                }
                if (!setEqualsCsv(game.getTags(), newTags)) {
                    game.setTags(newTags);
                    tagsChanged++;
                    changed = true;
                }
                if (!java.util.Objects.equals(game.getReleased(), newReleased)) {
                    game.setReleased(newReleased);
                    releasedChanged++;
                    changed = true;
                }

                if (changed) {
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

    /** Compares two CSV strings as sets (order-independent, blank-tolerant). */
    private static boolean setEqualsCsv(String a, String b) {
        return splitToSet(a).equals(splitToSet(b));
    }

    private static Set<String> splitToSet(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static String joinOrEmpty(List<String> values) {
        return (values == null || values.isEmpty()) ? "" : String.join(",", values);
    }

    private static boolean isEmpty(GameServiceClient.GameInfo info) {
        return info.name() == null
                && info.genres().isEmpty()
                && info.themes().isEmpty()
                && info.tags().isEmpty();
    }
}
