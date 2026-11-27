package com.fajekrina.sduir.foundation.sdui.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/// The folder layout of foundation-sdui, kept as it is written down in docs/ARSITEKTUR.md:
/// - only these top-level packages (the contract — rules, spec, envelope, errors — is its own
///   library, `…foundation.sdui.contract`);
/// - the plain packages (`annotation`, `model`, `utils`, `properties`) use none of the working
///   ones (`service`, `repository`, `security`, `exception`, `config`);
/// - an `impl` package sits only under `service/<feature>/`;
/// - only `repository` (and `config`, which wires it) uses JDBC.
class PackagesTest {
    private static final String BASE = "com/fajekrina/sduir/foundation/sdui/";
    private static final Set<String> TOP = Set.of("annotation", "config", "exception", "model", "properties", "repository",
        "security", "service", "utils");
    private static final List<String> PLAIN = List.of("annotation", "model", "utils", "properties");
    private static final List<String> WORKING = List.of("service", "repository", "security", "exception", "config");

    @Test
    void onlyTheAgreedTopLevelPackages() throws Exception {
        Set<String> found = new TreeSet<>();
        for (Resource r : own()) {
            String path = relative(r);
            found.add(path.substring(0, path.indexOf('/')));
        }
        assertThat(TOP).containsAll(found);
        assertThat(found).hasSizeGreaterThan(6);
    }

    @Test
    void plainPackagesUseNoWorkingPackage() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Resource r : own()) {
            String path = relative(r);
            String top = path.substring(0, path.indexOf('/'));
            if (!PLAIN.contains(top)) continue;
            // the constant pool names every class a class file refers to (as a/b/C)
            String bytes = new String(r.getContentAsByteArray(), StandardCharsets.ISO_8859_1);
            for (String w : WORKING) if (bytes.contains(BASE + w + "/")) wrong.add(path + " refers to " + w);
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    void implOnlyUnderAServiceFeature() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Resource r : own()) {
            String path = relative(r);
            if (path.contains("/impl/") && !path.matches("service/[a-z]+/impl/[^/]+\\.class")) wrong.add(path);
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    void onlyTheRepositoriesTouchTheDatabase() throws Exception {
        // the workflow (service/) decides, the repositories read and write, config wires them
        List<String> wrong = new ArrayList<>();
        for (Resource r : own()) {
            String path = relative(r);
            if (path.startsWith("repository/") || path.startsWith("config/")) continue;
            String bytes = new String(r.getContentAsByteArray(), StandardCharsets.ISO_8859_1);
            if (bytes.contains("org/springframework/jdbc/")) wrong.add(path + " uses JDBC");
        }
        assertThat(wrong).isEmpty();
    }

    /// the library's own classes (not the contract's, not these tests)
    private static List<Resource> own() throws Exception {
        List<Resource> out = new ArrayList<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:" + BASE + "**/*.class")) {
            String url = r.getURL().toString();
            if (url.contains("/test/") || url.contains("/test-classes/") || url.contains(BASE + "contract/")) continue;
            out.add(r);
        }
        assertThat(out).as("foundation-sdui classes read").hasSizeGreaterThan(30);
        return out;
    }

    private static String relative(Resource r) throws Exception {
        String url = r.getURL().toString();
        return url.substring(url.indexOf(BASE) + BASE.length());
    }
}
