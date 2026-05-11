package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.UserGenrePreferenceDTO;
import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import com.thegamecellar.libraryservice.repository.UserGenrePreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenrePreferenceServiceTest {

    @Mock
    private UserGenrePreferenceRepository repository;

    @InjectMocks
    private GenrePreferenceService service;

    private static final String USER_ID = "user-123";

    @Test
    void shouldReturnEmptyListWhenUserHasNoPreferences() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of());

        assertThat(service.getPreferences(USER_ID)).isEmpty();
    }

    @Test
    void shouldMapStoredPreferencesToDtos() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(
                UserGenrePreference.builder().id(1L).userId(USER_ID).genreName("RPG").build(),
                UserGenrePreference.builder().id(2L).userId(USER_ID).genreName("Strategy").build()
        ));

        List<UserGenrePreferenceDTO> result = service.getPreferences(USER_ID);

        assertThat(result).extracting(UserGenrePreferenceDTO::genreName)
                .containsExactly("RPG", "Strategy");
    }

    @Test
    void replaceShouldDeleteExistingThenInsertNew() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<UserGenrePreferenceDTO> result =
                service.replacePreferences(USER_ID, List.of("RPG", "Action"));

        verify(repository).deleteByUserId(USER_ID);

        ArgumentCaptor<List<UserGenrePreference>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        assertThat(captor.getValue()).extracting(UserGenrePreference::getGenreName)
                .containsExactly("RPG", "Action");
        assertThat(captor.getValue()).allSatisfy(p -> assertThat(p.getUserId()).isEqualTo(USER_ID));

        assertThat(result).extracting(UserGenrePreferenceDTO::genreName)
                .containsExactly("RPG", "Action");
    }

    @Test
    void replaceWithEmptyListShouldDeleteAndSkipInsert() {
        List<UserGenrePreferenceDTO> result = service.replacePreferences(USER_ID, List.of());

        verify(repository).deleteByUserId(USER_ID);
        verify(repository, never()).saveAll(anyList());

        assertThat(result).isEmpty();
    }

    @Test
    void replaceShouldDedupeAndTrimAndDropBlanks() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<String> input = Arrays.asList(" RPG ", "RPG", "Action", "", null, "Strategy", " Strategy");

        service.replacePreferences(USER_ID, input);

        ArgumentCaptor<List<UserGenrePreference>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        assertThat(captor.getValue()).extracting(UserGenrePreference::getGenreName)
                .containsExactly("RPG", "Action", "Strategy");
    }

    @Test
    void replaceWithNullListShouldDeleteAndSkipInsert() {
        List<UserGenrePreferenceDTO> result = service.replacePreferences(USER_ID, null);

        verify(repository).deleteByUserId(USER_ID);
        verify(repository, never()).saveAll(anyList());

        assertThat(result).isEmpty();
    }
}
