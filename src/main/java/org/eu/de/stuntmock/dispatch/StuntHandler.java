package org.eu.de.stuntmock.dispatch;

/**
 * The application-side handler the bootstrap {@link StuntDispatcher} forwards intercepted calls to. Implemented
 * once by the framework's dispatcher; the indirection exists because advice inlined into bootstrap classes
 * (e.g. {@code java.time.LocalDate}) can only reference bootstrap classes.
 */
public interface StuntHandler {

    /**
     * @param self          the receiver, or {@code null} for static methods
     * @param declaringType binary name of the class that declares the intercepted method
     * @param methodName    the method name
     * @param descriptor    the JVM method descriptor, e.g. {@code (ILjava/lang/String;)V}
     * @param args          the call arguments, boxed
     * @return {@code null} to run the original body, otherwise the result to return or throw
     */
    StuntResult onCall(Object self, String declaringType, String methodName, String descriptor, Object[] args);
}
