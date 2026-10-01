package com.pulse.security;

import com.pulse.model.Admin;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;


import static org.assertj.core.api.Assertions.assertThat;

class PulseSecurityUnitTests {

    @Test
    void userPrincipalDoesNotExposePassword() {
        Admin user = new Admin();
        user.setUserId(7L);
        user.setName("Test Admin");
        user.setUsername("test_admin");
        user.setPassword("$2a$10$should-never-leave-the-user-entity");
        user.setStateId(1L);
        user.setDistrictId(2L);
        user.setHospitalId(3L);
        user.setEnabled(true);

        UserPrincipal principal = UserPrincipal.from(user);

        assertThat(principal.getPassword()).isNull();
        assertThat(principal.getUsername()).isEqualTo("test_admin");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void localNodeSecurityAcceptsPrivateAndLoopbackButRejectsPublic() {
        LocalNodeSecurityService service = new LocalNodeSecurityService();

        assertThat(service.isTrustedLocalAddress("127.0.0.1")).isTrue();
        assertThat(service.isTrustedLocalAddress("192.168.1.20")).isTrue();
        assertThat(service.isTrustedLocalAddress("8.8.8.8")).isFalse();
        assertThat(service.isTrustedLocalAddress(null)).isFalse();
    }

    @Test
    void accessDeniedHandlerKeepsForbiddenStatus() throws Exception {
        PulseAccessDeniedHandler handler = new PulseAccessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/staff");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute("jakarta.servlet.forward.request_uri", "/admin/staff");

        try {
            handler.handle(request, response, new AccessDeniedException("denied"));
        } catch (Exception ignored) {
            // The mock request has no servlet container dispatcher. The status
            // is assigned before the forward and is the security contract.
        }

        assertThat(response.getStatus()).isEqualTo(403);
    }
}
