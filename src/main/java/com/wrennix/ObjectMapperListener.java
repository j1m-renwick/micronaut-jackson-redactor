//package com.wrennix;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.wrennix.annotations.RedactableMetaData;
//import com.wrennix.interfaces.RedactorConfiguration;
//import io.micronaut.context.event.BeanCreatedEvent;
//import io.micronaut.context.event.BeanCreatedEventListener;
//import io.micronaut.core.annotation.NonNull;
//import io.micronaut.runtime.exceptions.ApplicationStartupException;
//import jakarta.inject.Inject;
//import jakarta.inject.Singleton;
//import java.lang.annotation.Annotation;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.logging.Logger;
//import org.reflections.Reflections;
//import org.reflections.scanners.Scanners;
//import org.reflections.util.ClasspathHelper;
//import org.reflections.util.ConfigurationBuilder;
//import org.reflections.util.FilterBuilder;
//
//@Singleton
//public class ObjectMapperListener implements BeanCreatedEventListener<ObjectMapper> {
//
//    private static final Logger LOG = Logger.getLogger(ObjectMapperListener.class.getName());
//
//    @Inject
//    RedactorConfiguration redactorConfiguration;
//
//    @Override
//    public ObjectMapper onCreated(@NonNull BeanCreatedEvent event) {
//
//        ObjectMapper objectMapper =  (ObjectMapper) event.getBean();
//
//        if (redactorConfiguration.shouldRedact()) {
//            LOG.info("Redaction is enabled.");
//
//            for (Class<?> clazz: findClassesWithAnnotation(RedactableMetaData.class)) {
//                processFile(clazz, objectMapper);
//            }
//        } else {
//            LOG.info("Redaction is *NOT* enabled.");
//        }
//        return objectMapper;
//    }
//
//    private void processFile(Class<?> clazz, ObjectMapper objectMapper) {
//        RedactableMetaData annotation = clazz.getAnnotation(RedactableMetaData.class);
//        String[] sources = annotation.from();
//        String mixin = annotation.to();
//
//        Class<?> mixinClass;
//
//        try {
//            mixinClass = Class.forName(mixin);
//        } catch (ClassNotFoundException e) {
//            throw new ApplicationStartupException("***ERROR LOADING REDACTIONS!!!*** " +
//                "Could not load mixin for class: " + clazz.getName(), e);
//        }
//
//        Set<Class<?>> sourceClasses = new HashSet<>();
//
//        for (String source : sources) {
//            try {
//                sourceClasses.add(Class.forName(source));
//            } catch (ClassNotFoundException e) {
//                throw new ApplicationStartupException("***ERROR LOADING REDACTIONS!!!*** " +
//                    "Could not load proxyFor class: " + clazz.getName(), e);
//            }
//        }
//
//        for (Class<?> sourceClass : sourceClasses) {
//            objectMapper.addMixIn(sourceClass, mixinClass);
//        }
//    }
//
//    private Set<Class<?>> findClassesWithAnnotation(Class<? extends Annotation> annotationClass) {
//        Reflections reflections = new Reflections(
//            new ConfigurationBuilder()
//                .setUrls(ClasspathHelper.forJavaClassPath())
//                .setScanners(Scanners.TypesAnnotated)
//                .setInputsFilter(new FilterBuilder()
//                    .includePattern(stringifyPackagePath(redactorConfiguration.rootPackage())))
//        );
//
//        return reflections.getTypesAnnotatedWith(annotationClass);
//    }
//
//    private String stringifyPackagePath(String packagePath) {
//        packagePath = packagePath.replace(".", "\\.");
//        // wildcard at the end of the path to target subpackages
//        return packagePath + "\\..*";
//    }
//
//}
