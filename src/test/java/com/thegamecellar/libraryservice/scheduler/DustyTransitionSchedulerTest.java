package com.thegamecellar.libraryservice.scheduler;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DustyTransitionSchedulerTest {

    @Mock
    private UserGameRepository userGameRepository;

    @InjectMocks
    private DustyTransitionScheduler scheduler;

    private UserGame buildGame(Long id, GameStatus status) {
        return UserGame.builder()
                .id(id)
                .userId("user-123")
                .igdbGameId(id.intValue())
                .gameName("Game " + id)
                .status(status)
                .platform("PC")
                .dateAdded(LocalDateTime.now().minusDays(100))
                .build();
    }

    @Test
    void shouldTransitionEligibleGamesToDusty() {
        UserGame backlogGame = buildGame(1L, GameStatus.BACKLOG);
        UserGame playingGame = buildGame(2L, GameStatus.PLAYING);
        when(userGameRepository.findAllEligibleForDusty(any(LocalDateTime.class)))
                .thenReturn(List.of(backlogGame, playingGame));

        scheduler.transitionDustyGames();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserGame>> captor = ArgumentCaptor.forClass(List.class);
        verify(userGameRepository).saveAll(captor.capture());
        List<UserGame> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(g -> g.getStatus() == GameStatus.DUSTY);
    }

    @Test
    void shouldDoNothingWhenNoEligibleGames() {
        when(userGameRepository.findAllEligibleForDusty(any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.transitionDustyGames();

        verify(userGameRepository, never()).saveAll(any());
    }
}
