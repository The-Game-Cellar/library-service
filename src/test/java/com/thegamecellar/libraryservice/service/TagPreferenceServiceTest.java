package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.UserTagPreferenceDTO;
import com.thegamecellar.libraryservice.model.entity.UserTagPreference;
import com.thegamecellar.libraryservice.repository.UserTagPreferenceRepository;
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
class TagPreferenceServiceTest {

    @Mock
    private UserTagPreferenceRepository repository;

    @InjectMocks
    private TagPreferenceService service;

    private static final String USER_ID = "user-123";

    @Test
    void shouldReturnEmptyListWhenUserHasNoPreferences() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of());

        assertThat(service.getPreferences(USER_ID)).isEmpty();
    }

    @Test
    void shouldMapStoredPreferencesToDtos() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(
                UserTagPreference.builder().id(1L).userId(USER_ID).tagName("cozy").build(),
                UserTagPreference.builder().id(2L).userId(USER_ID).tagName("atmospheric").build()
        ));

        List<UserTagPreferenceDTO> result = service.getPreferences(USER_ID);

        assertThat(result).extracting(UserTagPreferenceDTO::tagName)
                .containsExactly("cozy", "atmospheric");
    }

    @Test
    void replaceShouldDeleteExistingThenInsertNew() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<UserTagPreferenceDTO> result =
                service.replacePreferences(USER_ID, List.of("cozy", "exploration"));

        verify(repository).deleteByUserId(USER_ID);

        ArgumentCaptor<List<UserTagPreference>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        assertThat(captor.getValue()).extracting(UserTagPreference::getTagName)
                .containsExactly("cozy", "exploration");
        assertThat(captor.getValue()).allSatisfy(p -> assertThat(p.getUserId()).isEqualTo(USER_ID));

        assertThat(result).extracting(UserTagPreferenceDTO::tagName)
                .containsExactly("cozy", "exploration");
    }

    @Test
    void replaceWithEmptyListShouldDeleteAndSkipInsert() {
        List<UserTagPreferenceDTO> result = service.replacePreferences(USER_ID, List.of());

        verify(repository).deleteByUserId(USER_ID);
        verify(repository, never()).saveAll(anyList());

        assertThat(result).isEmpty();
    }

    @Test
    void replaceShouldDedupeAndTrimAndDropBlanks() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<String> input = Arrays.asList(" cozy ", "cozy", "exploration", "", null, "atmospheric", " atmospheric");

        service.replacePreferences(USER_ID, input);

        ArgumentCaptor<List<UserTagPreference>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        assertThat(captor.getValue()).extracting(UserTagPreference::getTagName)
                .containsExactly("cozy", "exploration", "atmospheric");
    }

    @Test
    void replaceWithNullListShouldDeleteAndSkipInsert() {
        List<UserTagPreferenceDTO> result = service.replacePreferences(USER_ID, null);

        verify(repository).deleteByUserId(USER_ID);
        verify(repository, never()).saveAll(anyList());

        assertThat(result).isEmpty();
    }
}
