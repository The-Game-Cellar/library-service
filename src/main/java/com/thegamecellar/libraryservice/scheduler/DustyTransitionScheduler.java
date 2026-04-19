package com.thegamecellar.libraryservice.scheduler;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DustyTransitionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DustyTransitionScheduler.class);
    private static final int DUSTY_THRESHOLD_DAYS = 90;

    private final UserGameRepository userGameRepository;

    @Scheduled(cron = "0 0 3 * * *")
    public void transitionDustyGames() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(DUSTY_THRESHOLD_DAYS);
        List<UserGame> eligible = userGameRepository.findAllEligibleForDusty(threshold);
        if (eligible.isEmpty()) {
            return;
        }
        eligible.forEach(g -> g.setStatus(GameStatus.DUSTY));
        userGameRepository.saveAll(eligible);
        log.info("Transitioned {} games to DUSTY status", eligible.size());
    }
}
