package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.exception.PlatformAlreadyAddedException;
import com.thegamecellar.libraryservice.exception.PlatformNotFoundException;
import com.thegamecellar.libraryservice.model.dto.AddPlatformRequest;
import com.thegamecellar.libraryservice.model.dto.UserPlatformDTO;
import com.thegamecellar.libraryservice.model.entity.UserPlatform;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformServiceTest {

    @Mock
    private UserPlatformRepository userPlatformRepository;

    @InjectMocks
    private PlatformService platformService;

    private static final String USER_ID = "user-123";
    private static final String OTHER_USER_ID = "other-user-456";

    @Test
    void shouldAddPlatform() {
        AddPlatformRequest request = new AddPlatformRequest();
        request.setPlatformName("PC");
        request.setIsPrimary(true);

        UserPlatform saved = UserPlatform.builder()
                .id(1L)
                .userId(USER_ID)
                .platformName("PC")
                .isPrimary(true)
                .build();

        when(userPlatformRepository.existsByUserIdAndPlatformName(USER_ID, "PC")).thenReturn(false);
        when(userPlatformRepository.save(any())).thenReturn(saved);

        UserPlatformDTO result = platformService.addPlatform(USER_ID, request);

        assertThat(result.getPlatformName()).isEqualTo("PC");
        assertThat(result.getIsPrimary()).isTrue();
        verify(userPlatformRepository).save(any());
    }

    @Test
    void shouldThrow409IfPlatformAlreadyAdded() {
        AddPlatformRequest request = new AddPlatformRequest();
        request.setPlatformName("PC");

        when(userPlatformRepository.existsByUserIdAndPlatformName(USER_ID, "PC")).thenReturn(true);

        assertThatThrownBy(() -> platformService.addPlatform(USER_ID, request))
                .isInstanceOf(PlatformAlreadyAddedException.class);

        verify(userPlatformRepository, never()).save(any());
    }

    @Test
    void shouldThrow404WhenRemovingOtherUsersPlatform() {
        UserPlatform otherUserPlatform = UserPlatform.builder()
                .id(1L)
                .userId(OTHER_USER_ID)
                .platformName("PC")
                .isPrimary(false)
                .build();

        assertThatThrownBy(() -> platformService.removePlatform(USER_ID, 1L))
                .isInstanceOf(PlatformNotFoundException.class);

        verify(userPlatformRepository, never()).delete(any());
    }
}
