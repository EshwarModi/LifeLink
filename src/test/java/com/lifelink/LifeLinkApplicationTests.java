package com.lifelink;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LifeLinkApplicationTests {

    @Test
    @DisplayName("Context loads successfully")
    void contextLoads() {
        // Verifies Spring ApplicationContext bootstrap
    }
}
