package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.AccountExportDTO;
import com.thegamecellar.libraryservice.model.dto.UserGenrePreferenceDTO;
import com.thegamecellar.libraryservice.model.dto.UserReleaseYearPreferenceDTO;
import com.thegamecellar.libraryservice.model.dto.UserTagPreferenceDTO;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import com.thegamecellar.libraryservice.repository.UserGenrePreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserOnboardingRepository;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
import com.thegamecellar.libraryservice.repository.UserReleaseYearPreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserTagPreferenceRepository;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String USER_ID = "user-123";
    private static final String ENTITY_PACKAGE = "com.thegamecellar.libraryservice.model.entity";
    private static final String REPOSITORY_PACKAGE = "com.thegamecellar.libraryservice.repository";

    @Mock private UserGameRepository userGameRepository;
    @Mock private UserPlatformRepository userPlatformRepository;
    @Mock private UserGenrePreferenceRepository userGenrePreferenceRepository;
    @Mock private UserTagPreferenceRepository userTagPreferenceRepository;
    @Mock private UserReleaseYearPreferenceRepository userReleaseYearPreferenceRepository;
    @Mock private UserOnboardingRepository userOnboardingRepository;
    @Mock private LibraryService libraryService;
    @Mock private PlatformService platformService;
    @Mock private GenrePreferenceService genrePreferenceService;
    @Mock private TagPreferenceService tagPreferenceService;
    @Mock private ReleaseYearPreferenceService releaseYearPreferenceService;

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
    void exportIncludesEveryPreferenceKind() {
        when(libraryService.getGames(USER_ID, null, null, null, null, null)).thenReturn(List.of());
        when(platformService.getPlatforms(USER_ID)).thenReturn(List.of());
        when(genrePreferenceService.getPreferences(USER_ID)).thenReturn(List.of(new UserGenrePreferenceDTO("RPG")));
        when(tagPreferenceService.getPreferences(USER_ID)).thenReturn(List.of(new UserTagPreferenceDTO("cozy")));
        when(releaseYearPreferenceService.getPreferences(USER_ID)).thenReturn(List.of(
                new UserReleaseYearPreferenceDTO("1990s"),
                new UserReleaseYearPreferenceDTO("2020s")));

        AccountExportDTO export = service.exportUser(USER_ID);

        assertThat(export.userId()).isEqualTo(USER_ID);
        assertThat(export.genrePreferenceCount()).isEqualTo(1);
        assertThat(export.tagPreferenceCount()).isEqualTo(1);
        assertThat(export.releaseYearPreferenceCount()).isEqualTo(2);
        assertThat(export.releaseYearPreferences())
                .extracting(UserReleaseYearPreferenceDTO::bucketLabel)
                .containsExactly("1990s", "2020s");
    }

    private static Set<Class<?>> userKeyedEntities() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<Class<?>> entities = new HashSet<>();
        for (var candidate : scanner.findCandidateComponents(ENTITY_PACKAGE)) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
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
