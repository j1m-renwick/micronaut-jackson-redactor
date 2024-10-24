package com.wrennix;

import static com.wrennix.Constants.ANNOTATION_JSON_SERIALIZE_USING;
import static com.wrennix.Constants.MIXIN_FILE_SUFFIX;
import static com.wrennix.Constants.ANNOTATION_REDACTABLE_METADATA_FROM;
import static com.wrennix.Constants.ANNOTATION_REDACTABLE_METADATA_TO;
import static com.wrennix.Constants.ANNOTATION_REDACTABLE_MIXIN;
import static com.wrennix.Constants.ANNOTATION_REDACTABLE_PROXY_FOR;
import static com.wrennix.Constants.ANNOTATION_REDACT_REDACTOR;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.wrennix.annotations.Redact;
import com.wrennix.annotations.Redactable;
import com.wrennix.annotations.RedactableMetaData;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.TypeDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.lang.model.element.Modifier;

public class JavaRedactionVisitor implements TypeElementVisitor<Redactable, Object> {

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of("*");
    }

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {

        if (context.getLanguage().equals(VisitorContext.Language.JAVA)) {

            context.info("Redaction processing started for Java class: " + element.getName());

            String generatedMixinClassName = element.getName() + MIXIN_FILE_SUFFIX;

            Optional<String> mixinOptional = element.stringValue(Redactable.class, ANNOTATION_REDACTABLE_MIXIN);
            Optional<String> proxyOptional = element.stringValue(Redactable.class, ANNOTATION_REDACTABLE_PROXY_FOR);

            AnnotationDef metaclassAnnotation = createMetaAnnotation(mixinOptional, proxyOptional, element, generatedMixinClassName);

            ClassDef mixinClass = createMixinClass(mixinOptional, element, generatedMixinClassName, metaclassAnnotation);

            saveClass(mixinClass, context, element);
        }
    }

    AnnotationDef createMetaAnnotation(Optional<String> mixinOptional, Optional<String> proxyOptional,
                               ClassElement element, String generatedMixinClassName) {

        AnnotationDef.AnnotationDefBuilder metadataBuilder = AnnotationDef.builder(RedactableMetaData.class);

        List<String> sourceClasses = new ArrayList<>();
        sourceClasses.add(element.getName());

        if (proxyOptional.isPresent()) {
            String proxyValue = proxyOptional.get();
            sourceClasses.add(proxyValue);
        }

        metadataBuilder.addMember(ANNOTATION_REDACTABLE_METADATA_FROM, sourceClasses);


        if (mixinOptional.isPresent()) {
            List<String> fieldElements = element
                .getFields().stream()
                .filter(field -> field.hasAnnotation(Redact.class))
                .map(Element::getName)
                .toList();

            if (!fieldElements.isEmpty()) {
                throw new ProcessingException(element,
                    "The fields: " + fieldElements + " on class: " + element.getName()
                        + " should not have @Redact annotations - a custom mixin has been specified and these will be ignored!");
            }

            String mixinValue = mixinOptional.get();
            metadataBuilder.addMember(ANNOTATION_REDACTABLE_METADATA_TO, mixinValue);
        } else {
            metadataBuilder.addMember(ANNOTATION_REDACTABLE_METADATA_TO, generatedMixinClassName);
        }

        return metadataBuilder.build();

     }

    ClassDef createMixinClass(Optional<String> mixinOptional, ClassElement element,
                              String mixinClassName, AnnotationDef annotationToAdd) {

        ClassDef.ClassDefBuilder builder = ClassDef.builder(mixinClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

        // don't need to add any fields when a mixin is specified
        if (mixinOptional.isEmpty()) {
            for (FieldElement field : element.getFields()) {
                if (field.hasAnnotation(Redact.class)) {

                    RedactorType redactorType = field.enumValue(Redact.class, ANNOTATION_REDACT_REDACTOR, RedactorType.class)
                        .orElseGet(() -> field.getDefaultValue(Redact.class, ANNOTATION_REDACT_REDACTOR, RedactorType.class).get());

                    Class<? extends StdSerializer<?>> redactionSerializer = redactorType.getSerializer();

                    if (!(field.getType().isPrimitive() || field.getType().isAssignable(String.class))) {
                        throw new RuntimeException("Cannot apply @Redact to a non-primitive or " +
                            "non-String type for field: " + field.getName());
                    }

                    builder.addField(
                        FieldDef.builder(field.getName())
                            .ofType(TypeDef.of(field.getType()))
                            .addModifiers(
                                field.getModifiers()
                                    .stream()
                                    .map(mod -> Modifier.valueOf(mod.name())).toList().toArray(new Modifier[] {}))
                            .addAnnotation(AnnotationDef.builder(ClassTypeDef.of(JsonSerialize.class))
                                .addMember(ANNOTATION_JSON_SERIALIZE_USING, redactionSerializer)
                                .build())
                            .build()
                    );
                }
            }
        }

        builder.addAnnotation(annotationToAdd);
        return builder.build();

    }

    void saveClass(ClassDef classDef, VisitorContext context, ClassElement element) {
        SourceGenerator sourceGenerator =
            SourceGenerators.findByLanguage(context.getLanguage()).orElseThrow((Supplier<ProcessingException>) () -> {
                throw new ProcessingException(element, "couldn't get source for language: " + context.getLanguage().toString());
            });

        context.visitGeneratedSourceFile(classDef.getPackageName(), classDef.getSimpleName(), element)
            .ifPresent(generatedFile -> {
                try {
                    context.info("Adding redaction class : " + classDef.getSimpleName());
                    generatedFile.write(writer -> sourceGenerator.write(classDef, writer));
                } catch (Exception e) {
                    throw new ProcessingException(element, e.getMessage(), e);
                }
            });
    }

}
