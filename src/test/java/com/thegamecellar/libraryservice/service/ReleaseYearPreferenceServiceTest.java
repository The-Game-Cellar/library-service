package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.UserReleaseYearPreferenceDTO;
import com.thegamecellar.libraryservice.model.entity.UserReleaseYearPreference;
import com.thegamecellar.libraryservice.repository.UserReleaseYearPreferenceRepository;
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
class ReleaseYearPreferenceServiceTest {

    @Mock
    private UserReleaseYearPreferenceRepository repository;

    @Mock
    private LibraryWritePublisher writePublisher;

    @InjectMocks
    private ReleaseYearPreferenceService service;

    private static final String USER_ID = "user-123";

    @Test
    void shouldReturnEmptyListWhenUserHasNoPreferences() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of());

        assertThat(service.getPreferences(USER_ID)).isEmpty();
    }

    @Test
    void shouldMapStoredPreferencesToDtos() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(
                UserReleaseYearPreference.builder().id(1L).userId(USER_ID).bucketLabel("1990s").build(),
                UserReleaseYearPreference.builder().id(2L).userId(USER_ID).bucketLabel("2020s").build()
        ));

        List<UserReleaseYearPreferenceDTO> result = service.getPreferences(USER_ID);

        assertThat(result).extracting(UserReleaseYearPreferenceDTO::bucketLabel)
                .containsExactly("1990s", "2020s");
    }

    @Test
    void replaceShouldDeleteExistingThenInsertNew() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<UserReleaseYearPreferenceDTO> result =
                service.replacePreferences(USER_ID, List.of("1990s", "2010s"));

        verify(repository).deleteByUserId(USER_ID);

        ArgumentCaptor<List<UserReleaseYearPreference>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        assertThat(captor.getValue()).extracting(UserReleaseYearPreference::getBucketLabel)
                .containsExactly("1990s", "2010s");
        assertThat(captor.getValue()).allSatisfy(p -> assertThat(p.getUserId()).isEqualTo(USER_ID));

        assertThat(result).extracting(UserReleaseYearPreferenceDTO::bucketLabel)
                .containsExactly("1990s", "2010s");
    }

    @Test
    void replaceWithEmptyListShouldDeleteAndSkipInsert() {
        List<UserReleaseYearPreferenceDTO> result = service.replacePreferences(USER_ID, List.of());

        verify(repository).deleteByUserId(USER_ID);
        verify(repository, never()).saveAll(anyList());

        assertThat(result).isEmpty();
    }

    @Test
    void replaceShouldDedupeTrimAndDropUnknownLabels() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<String> input = Arrays.asList(" 1990s ", "1990s", "2030s", "", null, "Pre-1990", " 2020s", "bogus");

        service.replacePreferences(USER_ID, input);

        ArgumentCaptor<List<UserReleaseYearPreference>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        assertThat(captor.getValue()).extracting(UserReleaseYearPreference::getBucketLabel)
                .containsExactly("1990s", "Pre-1990", "2020s");
    }

    @Test
    void replaceWithNullListShouldDeleteAndSkipInsert() {
        List<UserReleaseYearPreferenceDTO> result = service.replacePreferences(USER_ID, null);

        verify(repository).deleteByUserId(USER_ID);
        verify(repository, never()).saveAll(anyList());

        assertThat(result).isEmpty();
    }
}
