package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.AccountExportDTO;
import com.thegamecellar.libraryservice.model.dto.AccountExportDTO.ExportedGame;
import com.thegamecellar.libraryservice.model.dto.AccountExportDTO.ExportedGenrePreference;
import com.thegamecellar.libraryservice.model.dto.AccountExportDTO.ExportedPlatform;
import com.thegamecellar.libraryservice.model.dto.AccountExportDTO.ExportedReleaseYearPreference;
import com.thegamecellar.libraryservice.model.dto.AccountExportDTO.ExportedTagPreference;
import com.thegamecellar.libraryservice.model.dto.AccountDeletionDTO;
import com.thegamecellar.libraryservice.model.entity.AccountDeletion;
import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import com.thegamecellar.libraryservice.model.entity.UserOnboarding;
import com.thegamecellar.libraryservice.model.entity.UserPlatform;
import com.thegamecellar.libraryservice.model.entity.UserReleaseYearPreference;
import com.thegamecellar.libraryservice.model.entity.UserTagPreference;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.repository.AccountDeletionRepository;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import com.thegamecellar.libraryservice.repository.UserGenrePreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserOnboardingRepository;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
import com.thegamecellar.libraryservice.repository.UserReleaseYearPreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserTagPreferenceRepository;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String USER_ID = "user-123";
    private static final String ENTITY_PACKAGE = "com.thegamecellar.libraryservice.model.entity";
    private static final String REPOSITORY_PACKAGE = "com.thegamecellar.libraryservice.repository";

    @Mock private AccountDeletionRepository accountDeletionRepository;
    @Mock private UserGameRepository userGameRepository;
    @Mock private UserPlatformRepository userPlatformRepository;
    @Mock private UserGenrePreferenceRepository userGenrePreferenceRepository;
    @Mock private UserTagPreferenceRepository userTagPreferenceRepository;
    @Mock private UserReleaseYearPreferenceRepository userReleaseYearPreferenceRepository;
    @Mock private UserOnboardingRepository userOnboardingRepository;

    @InjectMocks
    private AccountService service;

    @Test
    void purgeReportsWhatEachTableRemoved() {
        when(userGameRepository.deleteByUserId(USER_ID)).thenReturn(3L);
        when(userPlatformRepository.deleteByUserId(USER_ID)).thenReturn(2L);
        when(userGenrePreferenceRepository.deleteByUserId(USER_ID)).thenReturn(4);
        when(userTagPreferenceRepository.deleteByUserId(USER_ID)).thenReturn(5);
        when(userReleaseYearPreferenceRepository.deleteByUserId(USER_ID)).thenReturn(2);
        when(userOnboardingRepository.deleteByUserId(USER_ID)).thenReturn(1);

        AccountService.PurgeResult result = service.purgeUser(USER_ID);

        assertThat(result).isEqualTo(new AccountService.PurgeResult(3, 2, 4, 5, 2, 1));
    }

    @Test
    void requestingDeletionWritesTheLedgerRowBeforePurging() {
        when(accountDeletionRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.requestDeletion(USER_ID);

        ArgumentCaptor<AccountDeletion> saved = ArgumentCaptor.forClass(AccountDeletion.class);
        verify(accountDeletionRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getIdentityDeletedAt()).isNull();
        verify(userGameRepository).deleteByUserId(USER_ID);
    }

    // A second request for the same user, after a first one that failed past the purge,
    // must not reset the clock the retry job reads.
    @Test
    void requestingDeletionAgainKeepsTheOriginalRequestedAt() {
        LocalDateTime firstAsk = LocalDateTime.now().minusHours(2);
        AccountDeletion existing = AccountDeletion.builder().userId(USER_ID).requestedAt(firstAsk).build();
        when(accountDeletionRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        service.requestDeletion(USER_ID);

        ArgumentCaptor<AccountDeletion> saved = ArgumentCaptor.forClass(AccountDeletion.class);
        verify(accountDeletionRepository).save(saved.capture());
        assertThat(saved.getValue().getRequestedAt()).isEqualTo(firstAsk);
    }

    @Test
    void completingADeletionStampsTheRowOnceAndIgnoresUnknownOrFinishedRows() {
        AccountDeletion open = AccountDeletion.builder().userId(USER_ID).requestedAt(LocalDateTime.now()).build();
        when(accountDeletionRepository.findById(USER_ID)).thenReturn(Optional.of(open));
        service.completeDeletion(USER_ID);
        assertThat(open.getIdentityDeletedAt()).isNotNull();

        LocalDateTime firstCompletion = open.getIdentityDeletedAt();
        service.completeDeletion(USER_ID);
        assertThat(open.getIdentityDeletedAt()).isEqualTo(firstCompletion);

        when(accountDeletionRepository.findById("nobody")).thenReturn(Optional.empty());
        service.completeDeletion("nobody");
        verify(accountDeletionRepository, never()).save(any());
    }

    @Test
    void pendingDeletionsMapTheLedgerRows() {
        LocalDateTime asked = LocalDateTime.now().minusMinutes(5);
        when(accountDeletionRepository.findByIdentityDeletedAtIsNullAndRequestedAtBefore(any()))
                .thenReturn(List.of(AccountDeletion.builder().userId(USER_ID).requestedAt(asked).build()));

        assertThat(service.pendingDeletions()).containsExactly(new AccountDeletionDTO(USER_ID, asked));
    }

    // A new user-keyed table is a new place personal data survives a deletion request. This
    // test finds every entity with a userId, works out its repository, and checks that the
    // purge called deleteByUserId on it. Adding the entity without wiring it here fails loudly.
    @Test
    void purgeCoversEveryEntityThatCarriesAUserId() throws Exception {
        service.purgeUser(USER_ID);

        Set<Class<?>> userKeyedEntities = userKeyedEntities();
        assertThat(userKeyedEntities).isNotEmpty();

        for (Class<?> entity : userKeyedEntities) {
            Class<?> repositoryType = Class.forName(REPOSITORY_PACKAGE + "." + entity.getSimpleName() + "Repository");
            Object mock = mockOfType(repositoryType);
            Method deleteByUserId = repositoryType.getMethod("deleteByUserId", String.class);
            deleteByUserId.invoke(verify(mock), USER_ID);
        }
    }

    @Test
    void exportIncludesEveryKindOfRowInIdOrderWithTheOnboardingDate() {
        LocalDateTime onboarded = LocalDateTime.of(2026, 5, 1, 12, 0);
        when(userGameRepository.findByUserId(USER_ID)).thenReturn(List.of(
                UserGame.builder().id(9L).igdbGameId(1002).gameName("Stardew Valley").status(GameStatus.PLAYING).build(),
                UserGame.builder().id(4L).igdbGameId(1001).gameName("Hollow Knight").status(GameStatus.BACKLOG).build()));
        when(userPlatformRepository.findByUserId(USER_ID)).thenReturn(List.of(
                UserPlatform.builder().id(1L).platformName("PC").isPrimary(true).build()));
        when(userGenrePreferenceRepository.findByUserId(USER_ID)).thenReturn(List.of(
                UserGenrePreference.builder().id(3L).genreName("RPG").createdAt(onboarded).build()));
        when(userTagPreferenceRepository.findByUserId(USER_ID)).thenReturn(List.of(
                UserTagPreference.builder().id(5L).tagName("cozy").createdAt(onboarded).build()));
        when(userReleaseYearPreferenceRepository.findByUserId(USER_ID)).thenReturn(List.of(
                UserReleaseYearPreference.builder().id(8L).bucketLabel("2020s").build(),
                UserReleaseYearPreference.builder().id(7L).bucketLabel("1990s").build()));
        when(userOnboardingRepository.findById(USER_ID)).thenReturn(Optional.of(
                UserOnboarding.builder().userId(USER_ID).completedAt(onboarded).build()));

        AccountExportDTO export = service.exportUser(USER_ID);

        assertThat(export.userId()).isEqualTo(USER_ID);
        assertThat(export.onboardingCompletedAt()).isEqualTo(onboarded);
        assertThat(export.games()).extracting(ExportedGame::id).containsExactly(4L, 9L);
        assertThat(export.platforms()).extracting(ExportedPlatform::platformName).containsExactly("PC");
        assertThat(export.genrePreferences()).extracting(ExportedGenrePreference::createdAt).containsExactly(onboarded);
        assertThat(export.tagPreferences()).extracting(ExportedTagPreference::tagName).containsExactly("cozy");
        assertThat(export.releaseYearPreferences())
                .extracting(ExportedReleaseYearPreference::bucketLabel)
                .containsExactly("1990s", "2020s");
        assertThat(export.gameCount()).isEqualTo(2);
        assertThat(export.releaseYearPreferenceCount()).isEqualTo(2);
    }

    @Test
    void exportOfAUserWhoNeverFinishedOnboardingHasNoDate() {
        when(userOnboardingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.exportUser(USER_ID).onboardingCompletedAt()).isNull();
    }

    // A column the export leaves out is personal data the user cannot see. Every field of every
    // user-keyed entity, bar the userId the file carries once at the top, must reach the file;
    // a new entity or a new column without its place in AccountExportDTO fails here.
    @Test
    void exportCarriesEveryFieldOfEveryEntityThatCarriesAUserId() throws Exception {
        Map<Class<?>, Set<String>> exported = Map.of(
                UserGame.class, components(ExportedGame.class),
                UserPlatform.class, components(ExportedPlatform.class),
                UserGenrePreference.class, components(ExportedGenrePreference.class),
                UserTagPreference.class, components(ExportedTagPreference.class),
                UserReleaseYearPreference.class, components(ExportedReleaseYearPreference.class),
                // One row per user, so its fields sit at the top level behind an "onboarding" prefix.
                UserOnboarding.class, components(AccountExportDTO.class).stream()
                        .filter(name -> name.startsWith("onboarding"))
                        .map(name -> Character.toLowerCase(name.charAt(10)) + name.substring(11))
                        .collect(Collectors.toSet()));

        for (Class<?> entity : userKeyedEntities()) {
            assertThat(exported)
                    .withFailMessage("%s carries a userId but has no place in AccountExportDTO", entity.getSimpleName())
                    .containsKey(entity);
            Set<String> fields = Arrays.stream(entity.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
                    .map(Field::getName)
                    .filter(name -> !name.equals("userId"))
                    .collect(Collectors.toSet());
            assertThat(exported.get(entity))
                    .withFailMessage("AccountExportDTO leaves out %s of %s", fields.stream()
                            .filter(name -> !exported.get(entity).contains(name)).toList(), entity.getSimpleName())
                    .containsAll(fields);
        }
    }

    private static Set<String> components(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }

    private static Set<Class<?>> userKeyedEntities() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<Class<?>> entities = new HashSet<>();
        for (var candidate : scanner.findCandidateComponents(ENTITY_PACKAGE)) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            // The ledger is keyed by the user being erased and is the one row that must
            // survive the purge: it is what records that the erasure was asked for.
            if (type == AccountDeletion.class) {
                continue;
            }
            boolean carriesUserId = Arrays.stream(type.getDeclaredFields())
                    .anyMatch(field -> field.getName().equals("userId"));
            if (carriesUserId) {
                entities.add(type);
            }
        }
        return entities;
    }

    private Object mockOfType(Class<?> repositoryType) throws IllegalAccessException {
        List<Field> matches = Arrays.stream(getClass().getDeclaredFields())
                .filter(field -> field.getType().equals(repositoryType))
                .collect(Collectors.toList());
        assertThat(matches)
                .withFailMessage("%s carries a userId but AccountService has no %s to purge it; inject it, call deleteByUserId in purgeUser, and add a @Mock here",
                        repositoryType.getSimpleName().replace("Repository", ""), repositoryType.getSimpleName())
                .hasSize(1);
        return matches.get(0).get(this);
    }
}
