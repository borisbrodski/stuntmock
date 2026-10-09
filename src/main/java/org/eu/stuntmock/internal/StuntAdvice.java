package org.eu.stuntmock.internal;

import org.eu.stuntmock.dispatch.StuntDispatcher;
import org.eu.stuntmock.dispatch.StuntResult;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * The around advice inlined into every instrumented method. The enter part asks the bootstrap dispatcher for a
 * result; a non-null result skips the original body. The exit part installs the stubbed return value or throws
 * the stubbed exception. Never called directly; its code is copied into the target classes. Internal.
 */
public final class StuntAdvice {

    private StuntAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static StuntResult enter(
            @Advice.This(optional = true) Object self,
            @Advice.Origin("#t") String declaringType,
            @Advice.Origin("#m") String methodName,
            @Advice.Origin("#d") String descriptor,
            @Advice.AllArguments Object[] args) {
        return StuntDispatcher.onCall(self, declaringType, methodName, descriptor, args);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, backupArguments = false)
    public static void exit(
            @Advice.Enter StuntResult result,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returned,
            @Advice.Thrown(readOnly = false) Throwable thrown) {
        if (result != null) {
            if (result.thrown != null) {
                thrown = result.thrown;
            }
            else {
                returned = result.value;
            }
        }
    }
}
