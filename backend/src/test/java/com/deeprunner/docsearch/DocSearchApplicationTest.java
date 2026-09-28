package com.deeprunner.docsearch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
class DocSearchApplicationTest {

    @Test
    @DisplayName("Application context loads without bean definition collisions or configuration errors")
    void contextLoads() {
        assertDoesNotThrow(() -> {});
    }
}
