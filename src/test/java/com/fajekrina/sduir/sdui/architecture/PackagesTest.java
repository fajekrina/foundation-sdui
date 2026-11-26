package com.fajekrina.sduir.sdui.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// The package boundaries that used to be module boundaries: `core` uses no Spring and nothing of the
/// Spring-side packages, so it stays usable without a framework. (`contract` is its own library,
/// foundation-sdui-contract, built without Spring on its classpath at all.)
class PackagesTest {
    private static final String BASE = "com/fajekrina/sduir/sdui/";
    private static final List<String> SPRING_SIDE = List.of("web", "persistence", "authoring", "delivery", "action");

    @Test
    void coreUsesNoSpringAndNoSpringSidePackage() throws Exception {
        List<String> wrong = new ArrayList<>();
        int read = 0;
        for (String pkg : List.of("core")) {
            for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:" + BASE + pkg + "/**/*.class")) {
                if (r.getURL().toString().contains("/test/")) continue;
                read++;
                // the constant pool names every class a class file refers to (as a/b/C)
                String bytes = new String(r.getContentAsByteArray(), StandardCharsets.ISO_8859_1);
                if (bytes.contains("org/springframework/")) wrong.add(pkg + ": " + r.getFilename() + " refers to Spring");
                for (String side : SPRING_SIDE) {
                    if (bytes.contains(BASE + side + "/")) wrong.add(pkg + ": " + r.getFilename() + " refers to " + side);
                }
            }
        }
        assertThat(read).as("core classes read").isGreaterThan(4);
        assertThat(wrong).isEmpty();
    }
}
