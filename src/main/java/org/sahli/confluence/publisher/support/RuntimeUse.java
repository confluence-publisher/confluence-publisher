package org.sahli.confluence.publisher.support;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * @author Alain Sahli
 * @since 1.0
 */
@Retention(SOURCE)
@Target({TYPE, METHOD})
public @interface RuntimeUse {
}
