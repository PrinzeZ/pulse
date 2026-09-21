package com.pulse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PulseApplication.class)
public class PulseApplicationTests {
    @Test
    void contextLoads() {
        assertThat(true).isTrue();
    }
}