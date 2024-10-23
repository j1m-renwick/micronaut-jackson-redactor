package com.wrennix.ast;

import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.wrennix.RedactorType;
import com.wrennix.annotations.Redact;
import com.wrennix.annotations.RedactableMetaData;
import groovyjarjarasm.asm.Opcodes;
import java.util.List;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.InnerClassNode;
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
  public void visit(ASTNode[] nodes, SourceUnit source) {
    ClassNode outer = (ClassNode) nodes[1];
    System.out.println("@Redactable found on class: " + outer.getName());

    String newClassName = outer.getName() + "__Mixin";

    ClassNode newClass = new ClassNode(newClassName, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, ClassNode.SUPER);

    AnnotationNode annotationNode = (AnnotationNode) nodes[0];
    AnnotationNode metaclassAnnotation = new AnnotationNode(ClassHelper.make(RedactableMetaData.class));

    // MIXIN LOGIC

    Expression mixin = annotationNode.getMember("mixin");

    if (mixin == null) {
      List<FieldNode> fields = outer.getFields();
      for (FieldNode field : fields) {
        List<AnnotationNode> fieldAnnotations = field.getAnnotations(new ClassNode(Redact.class));
        if (!fieldAnnotations.isEmpty()) {
          System.out.println("@Redact found on field "
              + field.getType().getTypeClass().getName() + " : " + field.getName());

          if (!(ClassHelper.isPrimitiveType(field.getType()) || ClassHelper.isStringType(field.getType()))) {
            throw new RuntimeException("Cannot apply @Redact to a non-primitive or non-String type for field: " + field.getName());
          }

          // find serializer based on the @Redact redactor member
          Expression redactorExpression = fieldAnnotations.getFirst().getMember("redactor");
          if (redactorExpression == null) {
            // set default value if not supplied in annotation
            redactorExpression = new PropertyExpression(new ClassExpression(ClassHelper.make(RedactorType.class)),
                new ConstantExpression("EXES_REDACTION"));
          }

          PropertyExpression redactorProperty = (PropertyExpression) redactorExpression;
          String redactorValue = (String) ((ConstantExpression) redactorProperty.getProperty()).getValue();
          Class<? extends StdSerializer<?>> serializerToUse = RedactorType.valueOf(redactorValue).getSerializer();

          // Create field and add annotation
          FieldNode newField = new FieldNode(field.getName(), Opcodes.ACC_PUBLIC,
              field.getType(), newClass, new ConstantExpression(null));

          AnnotationNode newAnnotation = new AnnotationNode(ClassHelper.make(JsonSerialize.class));
          newAnnotation.addMember("using", new ClassExpression(ClassHelper.make(serializerToUse)));

          newField.addAnnotation(newAnnotation);
          newClass.addField(newField);
        }
      }
      metaclassAnnotation.addMember("to", new ConstantExpression(newClassName));

    } else {
      List<String> annotatedFields = outer.getFields()
          .stream()
          .filter(f -> !f.getAnnotations(new ClassNode(Redact.class)).isEmpty())
          .map(FieldNode::getName)
          .toList();

      if (!annotatedFields.isEmpty()) {
        throw new RuntimeException("The fields: " + annotatedFields + " on class: " + outer.getName()
            + " should not have @Redact annotations, as these will be ignored!");
      }
      metaclassAnnotation.addMember("to", mixin);
    }

    // PROXY LOGIC

    Expression proxy = annotationNode.getMember("proxyFor");

    ListExpression sourceList = new ListExpression();
    sourceList.addExpression(new ConstantExpression(outer.getName()));

    if (proxy != null) {
      sourceList.addExpression(new ConstantExpression(proxy.getText()));
    }

    metaclassAnnotation.addMember("from", sourceList);
    newClass.addAnnotation(metaclassAnnotation);

    source.getAST().addClass(newClass);

  }
}
