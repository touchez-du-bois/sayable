package org.jggug.dojo.sayable.transform;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@java.lang.annotation.Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PACKAGE, ElementType.TYPE})
@GroovyASTTransformationClass("org.jggug.dojo.sayable.transform.SayableASTTransformation")
public @interface Sayable {
//    String inputFile() default "";
//    boolean progress() default false;
    String voice() default "";
    int rate() default -1;
    String outputFile() default "";
    String networkSend() default "";
    String audioDevice() default "";
    String fileFormat() default "";
    String dataFormat() default "";
    int channels() default -1;
    int bitRate() default -1;
    int quality() default -1;
}
