package com.fajekrina.sduir.sdui.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// foundation-sdui maps no route and hook nothing into a service's web layer beyond the
/// `@SduiEndpoint` advice: no controller, no `@RequestMapping`, no advice for every controller,
/// no servlet filter.
class NoRoutesTest {
    private static final String CONTROLLER = "org.springframework.stereotype.Controller";
    private static final String MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
    private static final String ADVICE = "org.springframework.web.bind.annotation.ControllerAdvice";
    private static final String BEAN = "org.springframework.context.annotation.Bean";

    @Test
    void noFoundationClassMapsARouteOrHooksTheWholeService() throws Exception {
        var factory = new SimpleMetadataReaderFactory();
        Resource[] classes = new PathMatchingResourcePatternResolver().getResources("classpath*:com/fajekrina/sduir/sdui/**/*.class");
        List<String> wrong = new ArrayList<>();
        int read = 0;
        for (Resource r : classes) {
            String url = r.getURL().toString();
            if (url.contains("/test-classes/") || url.contains("/classes/java/test/")) continue;   // these tests
            MetadataReader reader = factory.getMetadataReader(r);
            AnnotationMetadata m = reader.getAnnotationMetadata();
            String name = m.getClassName();
            read++;
            if (m.hasMetaAnnotation(CONTROLLER) || m.hasAnnotation(CONTROLLER)) wrong.add(name + " is a controller");
            if (m.hasAnnotation(MAPPING) || m.hasMetaAnnotation(MAPPING) || !m.getAnnotatedMethods(MAPPING).isEmpty()) wrong.add(name + " maps a route");
            if (m.hasAnnotation(ADVICE) || m.hasMetaAnnotation(ADVICE)) {
                Map<String, Object> attrs = m.getAnnotationAttributes("org.springframework.web.bind.annotation.RestControllerAdvice", true);
                if (attrs == null) attrs = m.getAnnotationAttributes(ADVICE, true);
                String[] only = attrs == null ? new String[0] : (String[]) attrs.get("annotations");
                if (only == null || only.length != 1 || !only[0].equals("com.fajekrina.sduir.sdui.web.SduiEndpoint")) wrong.add(name + " advises every controller");
            }
            String parent = reader.getClassMetadata().getSuperClassName();
            if (parent != null && (parent.endsWith("OncePerRequestFilter") || parent.endsWith("GenericFilterBean"))) wrong.add(name + " is a servlet filter");
            for (String i : reader.getClassMetadata().getInterfaceNames()) if (i.equals("jakarta.servlet.Filter")) wrong.add(name + " is a servlet filter");
            for (MethodMetadata bean : m.getAnnotatedMethods(BEAN)) {
                if (bean.getReturnTypeName().contains("FilterRegistrationBean")) wrong.add(name + "." + bean.getMethodName() + " registers a filter");
            }
        }
        assertThat(read).as("foundation classes read").isGreaterThan(40);
        assertThat(wrong).isEmpty();
    }
}
