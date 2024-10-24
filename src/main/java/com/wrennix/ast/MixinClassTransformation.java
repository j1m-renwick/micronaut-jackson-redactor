package com.wrennix.ast;

import static com.wrennix.annotations.Constants.ANNOTATION_JSON_SERIALIZE_USING;
import static com.wrennix.annotations.Constants.ANNOTATION_REDACTABLE_METADATA_FROM;
import static com.wrennix.annotations.Constants.ANNOTATION_REDACTABLE_METADATA_TO;
import static com.wrennix.annotations.Constants.ANNOTATION_REDACTABLE_MIXIN;
import static com.wrennix.annotations.Constants.ANNOTATION_REDACTABLE_PROXY_FOR;
import static com.wrennix.annotations.Constants.ANNOTATION_REDACT_REDACTOR;
import static com.wrennix.annotations.Constants.ANNOTATION_REDACT_REDACTOR_DEFAULT;
import static com.wrennix.annotations.Constants.MIXIN_FILE_SUFFIX;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.wrennix.RedactorType;
import com.wrennix.annotations.Redact;
import com.wrennix.annotations.RedactableMetaData;
import groovyjarjarasm.asm.Opcodes;
import java.util.List;
import java.util.logging.Logger;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

@GroovyASTTransformation
public class MixinClassTransformation implements ASTTransformation {

  private static final Logger LOG = Logger.getLogger(MixinClassTransformation.class.getName());

  public void visit(ASTNode[] nodes, SourceUnit source) {
    ClassNode existingClass = (ClassNode) nodes[1];
      LOG.info("Redaction processing started for Groovy class: " + existingClass.getName());

    String newClassName = existingClass.getName() + MIXIN_FILE_SUFFIX;

    ClassNode newClass = new ClassNode(newClassName, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, ClassNode.SUPER);

    AnnotationNode annotationNode = (AnnotationNode) nodes[0];
    AnnotationNode metaclassAnnotation = new AnnotationNode(ClassHelper.make(RedactableMetaData.class));

    // MIXIN LOGIC

    Expression mixin = annotationNode.getMember(ANNOTATION_REDACTABLE_MIXIN);

    if (mixin == null) {
      List<FieldNode> fields = existingClass.getFields();
      for (FieldNode field : fields) {
        List<AnnotationNode> fieldAnnotations = field.getAnnotations(new ClassNode(Redact.class));
        if (!fieldAnnotations.isEmpty()) {
          System.out.println("@Redact found on field "
              + field.getType().getTypeClass().getName() + " : " + field.getName());

          if (!(ClassHelper.isPrimitiveType(field.getType()) || ClassHelper.isStringType(field.getType()))) {
            throw new RuntimeException("Cannot apply @Redact to a non-primitive or non-String type for field: " + field.getName());
          }

          // find serializer based on the @Redact redactor member
          Expression redactorExpression = fieldAnnotations.getFirst().getMember(ANNOTATION_REDACT_REDACTOR);
          if (redactorExpression == null) {
            // set default value if not supplied in annotation
            redactorExpression = new PropertyExpression(new ClassExpression(ClassHelper.make(RedactorType.class)),
                new ConstantExpression(ANNOTATION_REDACT_REDACTOR_DEFAULT));
          }

          PropertyExpression redactorProperty = (PropertyExpression) redactorExpression;
          String redactorValue = (String) ((ConstantExpression) redactorProperty.getProperty()).getValue();
          Class<? extends StdSerializer<?>> serializerToUse = RedactorType.valueOf(redactorValue).getSerializer();

          // Create field and add annotation
          FieldNode newField = new FieldNode(field.getName(), Opcodes.ACC_PUBLIC,
              field.getType(), newClass, new ConstantExpression(null));

          AnnotationNode newAnnotation = new AnnotationNode(ClassHelper.make(JsonSerialize.class));
          newAnnotation.addMember(ANNOTATION_JSON_SERIALIZE_USING, new ClassExpression(ClassHelper.make(serializerToUse)));

          newField.addAnnotation(newAnnotation);
          newClass.addField(newField);
        }
      }
      metaclassAnnotation.addMember(ANNOTATION_REDACTABLE_METADATA_TO, new ConstantExpression(newClassName));

    } else {
      List<String> annotatedFields = existingClass.getFields()
          .stream()
          .filter(f -> !f.getAnnotations(new ClassNode(Redact.class)).isEmpty())
          .map(FieldNode::getName)
          .toList();

      if (!annotatedFields.isEmpty()) {
        throw new RuntimeException("The fields: " + annotatedFields + " on class: " + existingClass.getName()
            + " should not have @Redact annotations, as these will be ignored!");
      }
      metaclassAnnotation.addMember(ANNOTATION_REDACTABLE_METADATA_TO, mixin);
    }

    // PROXY LOGIC

    Expression proxy = annotationNode.getMember(ANNOTATION_REDACTABLE_PROXY_FOR);

    ListExpression sourceList = new ListExpression();
    sourceList.addExpression(new ConstantExpression(existingClass.getName()));

    if (proxy != null) {
      sourceList.addExpression(new ConstantExpression(proxy.getText()));
    }

    metaclassAnnotation.addMember(ANNOTATION_REDACTABLE_METADATA_FROM, sourceList);
    newClass.addAnnotation(metaclassAnnotation);

    source.getAST().addClass(newClass);

  }
}
