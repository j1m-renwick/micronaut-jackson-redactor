package com.wrennix;


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
import javax.lang.model.element.Modifier;

public class JavaRedactionVisitor implements TypeElementVisitor<Redactable, Object> {

    @Override
    public void start(VisitorContext visitorContext) {
        visitorContext.info("JavaRedactionVisitor starting");
    }
//
//    @Override
//    public int getOrder() {
//        return LOWEST_PRECEDENCE;
//    }

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

            context.info("Visiting class: " + element.getName());

            AnnotationDef.AnnotationDefBuilder metadataBuilder = AnnotationDef.builder(RedactableMetaData.class);
            String generatedMixinClassName = element.getName() + "__Mixin";

            Optional<String> mixinOptional = element.stringValue(Redactable.class, "mixin");

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
                context.info("using specified mixin value of " + mixinValue);
                metadataBuilder.addMember("to", mixinValue);
            } else {
                metadataBuilder.addMember("to", generatedMixinClassName);
            }


            Optional<String> proxyOptional = element.stringValue(Redactable.class, "proxyFor");

            List<String> sourceClasses = new ArrayList<>();
            sourceClasses.add(element.getName());

            if (proxyOptional.isPresent()) {
                String proxyValue = proxyOptional.get();
                context.info("using specified proxy value of " + proxyValue);
                sourceClasses.add(proxyValue);
            }

            metadataBuilder.addMember("from", sourceClasses);

            ClassDef.ClassDefBuilder builder = ClassDef.builder(generatedMixinClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

            // don't need to add any fields when a mixin is specified
            if (mixinOptional.isEmpty()) {
                for (FieldElement field : element.getFields()) {
                    if (field.hasAnnotation(Redact.class)) {

                        RedactorType redactorType = field.enumValue(Redact.class, "redactor", RedactorType.class)
                            .orElseGet(() -> field.getDefaultValue(Redact.class, "redactor", RedactorType.class).get());

                        context.info("using redactor type of " + redactorType.name());
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
                                    .addMember("using", redactionSerializer)
                                    .build())
                                .build()
                        );
                    }
                }
            }

            builder.addAnnotation(metadataBuilder.build());
            ClassDef classDef = builder.build();

            SourceGenerator sourceGenerator =
                SourceGenerators.findByLanguage(context.getLanguage()).orElse(null);
            if (sourceGenerator == null) {
                throw new ProcessingException(element,"couldn't get source for language: " + context.getLanguage().toString());
            }

            context.visitGeneratedSourceFile(classDef.getPackageName(), classDef.getSimpleName(), element)
                .ifPresent(generatedFile -> {
                    try {
                        context.info("Creating " + classDef.getSimpleName());
                        generatedFile.write(writer -> sourceGenerator.write(classDef, writer));
                    } catch (Exception e) {
                        throw new ProcessingException(element, e.getMessage(), e);
                    }
                });
        }
    }

}
