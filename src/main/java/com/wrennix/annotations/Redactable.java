package com.wrennix.annotations;

import com.wrennix.ast.MixinClassTransformation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@GroovyASTTransformationClass(classes = {MixinClassTransformation.class})
public @interface Redactable {

    String mixin() default "";

    String proxyFor() default "";

}
