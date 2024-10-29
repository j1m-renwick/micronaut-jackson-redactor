package com.wrennix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrennix.annotations.RedactableMetaData;
import com.wrennix.interfaces.RedactorConfiguration;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Object that wraps a copy of the micronaut-created objectmapper bean, with additional mixins to perform redaction.
 */
@Singleton
public class Redactor {

    private static final Logger LOG = LoggerFactory.getLogger(Redactor.class.getName());

    @Inject
    ObjectMapper sourceMapper;

    @Inject
    RedactorConfiguration redactorConfiguration;

    ObjectMapper redactionMapper;

    @PostConstruct
    void init() {

        redactionMapper = sourceMapper.copy();

        if (redactorConfiguration.shouldRedact()) {
            LOG.info("Redaction is enabled.");

            for (Class<?> clazz: findClassesWithAnnotation(RedactableMetaData.class)) {
                processFile(clazz, redactionMapper);
            }
        } else {
            LOG.info("Redaction is *NOT* enabled.");
        }
    }

    public String asString(Object toRedact) throws JsonProcessingException {
        return redactionMapper.writeValueAsString(toRedact);
    }

    private void processFile(Class<?> clazz, ObjectMapper objectMapper) {
        RedactableMetaData annotation = clazz.getAnnotation(RedactableMetaData.class);
        String[] sources = annotation.from();
        String mixin = annotation.to();

        Class<?> mixinClass;

        try {
            mixinClass = Class.forName(mixin);
        } catch (ClassNotFoundException e) {
            throw new ApplicationStartupException("***ERROR LOADING REDACTIONS!!!*** " +
                "Could not load mixin for class: " + clazz.getName(), e);
        }

        Set<Class<?>> sourceClasses = new HashSet<>();

        for (String source : sources) {
            try {
                sourceClasses.add(Class.forName(source));
            } catch (ClassNotFoundException e) {
                throw new ApplicationStartupException("***ERROR LOADING REDACTIONS!!!*** " +
                    "Could not load proxyFor class: " + clazz.getName(), e);
            }
        }

        for (Class<?> sourceClass : sourceClasses) {
            objectMapper.addMixIn(sourceClass, mixinClass);
        }
    }

    private Set<Class<?>> findClassesWithAnnotation(Class<? extends Annotation> annotationClass) {
        Reflections reflections = new Reflections(
            new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forJavaClassPath())
                .setScanners(Scanners.TypesAnnotated)
                .setInputsFilter(new FilterBuilder()
                    .includePattern(stringifyPackagePath(redactorConfiguration.rootPackage())))
        );

        return reflections.getTypesAnnotatedWith(annotationClass);
    }

    private String stringifyPackagePath(String packagePath) {
        packagePath = packagePath.replace(".", "\\.");
        // wildcard at the end of the path to target subpackages
        return packagePath + "\\..*";
    }

}
