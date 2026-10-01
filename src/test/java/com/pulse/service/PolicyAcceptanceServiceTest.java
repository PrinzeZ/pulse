package com.pulse.service;

import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.Admin;
import com.pulse.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PolicyAcceptanceServiceTest {

    @Test
    void recordsAcceptanceInCloudStore() {
        UserRepository users = mock(UserRepository.class);
        LocalOfflineStore local = mock(LocalOfflineStore.class);
        @SuppressWarnings("unchecked") ObjectProvider<LocalOfflineStore> provider = mock(ObjectProvider.class);
        Admin user = new Admin();
        user.setUsername("admin");
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        when(provider.getIfAvailable()).thenReturn(local);
        when(local.recordPolicyAcceptance(anyString(), anyString(), any())).thenReturn(0);

        PolicyAcceptanceService service = new PolicyAcceptanceService(users, provider);

        assertThat(service.recordForUsername("admin")).isTrue();
        assertThat(user.getPolicyVersion()).isEqualTo(PolicyAcceptanceService.POLICY_VERSION);
        assertThat(user.getPolicyAcceptedAt()).isNotNull();
        verify(users).saveAndFlush(user);
    }

    @Test
    void offlineMirrorCanBeTheDurableAcceptanceStore() {
        UserRepository users = mock(UserRepository.class);
        LocalOfflineStore local = mock(LocalOfflineStore.class);
        @SuppressWarnings("unchecked") ObjectProvider<LocalOfflineStore> provider = mock(ObjectProvider.class);
        when(users.findByUsername("staff")).thenThrow(new RuntimeException("cloud unavailable"));
        when(provider.getIfAvailable()).thenReturn(local);
        when(local.recordPolicyAcceptance(eq("staff"), eq(PolicyAcceptanceService.POLICY_VERSION), any())).thenReturn(1);

        PolicyAcceptanceService service = new PolicyAcceptanceService(users, provider);

        assertThat(service.recordForUsername("staff")).isTrue();
    }

    @Test
    void authenticationCannotProceedWhenAcceptanceCannotBeRecorded() {
        UserRepository users = mock(UserRepository.class);
        LocalOfflineStore local = mock(LocalOfflineStore.class);
        @SuppressWarnings("unchecked") ObjectProvider<LocalOfflineStore> provider = mock(ObjectProvider.class);
        when(users.findByUsername("nobody")).thenReturn(Optional.empty());
        when(provider.getIfAvailable()).thenReturn(local);
        when(local.recordPolicyAcceptance(anyString(), anyString(), any())).thenReturn(0);

        PolicyAcceptanceService service = new PolicyAcceptanceService(users, provider);

        assertThatThrownBy(() -> service.recordForUsername("nobody"))
                .isInstanceOf(IllegalStateException.class);
    }
}
