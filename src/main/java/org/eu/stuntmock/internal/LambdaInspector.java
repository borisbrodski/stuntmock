package org.eu.stuntmock.internal;

import java.io.Serializable;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

import org.eu.stuntmock.StuntException;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

/**
 * Reads what a serializable lambda or method reference does without executing it: the last method call in a
 * {@code when}/{@code verify} closure (so the target class is known before the closure runs), and the parameter
 * types of a {@code thenDo} closure. Internal.
 */
public final class LambdaInspector {

    private LambdaInspector() {
    }

    /** The call a closure targets. */
    public record Target(String ownerName, String methodName, String descriptor, boolean isStatic,
                         Object boundReceiver, boolean methodReference) {

        public Class< ? > owner(ClassLoader loader) {
            try {
                return Class.forName(ownerName.replace('/', '.'), false, loader);
            }
            catch (ClassNotFoundException e) {
                throw new StuntException("Cannot load the class called in the closure: " + ownerName, e);
            }
        }
    }

    public static SerializedLambda serialize(Serializable lambda) {
        try {
            Method writeReplace = lambda.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object replaced = writeReplace.invoke(lambda);
            if (replaced instanceof SerializedLambda s) {
                return s;
            }
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            throw new StuntException("Cannot inspect the closure " + lambda.getClass().getName()
                + "; pass a lambda expression or a method reference", e);
        }
        throw new StuntException("Cannot inspect the closure " + lambda.getClass().getName()
            + "; pass a lambda expression or a method reference");
    }

    /** Finds the call a {@code when}/{@code verify} closure makes. */
    public static Target targetOf(Serializable closure) {
        SerializedLambda lambda = serialize(closure);
        ClassLoader loader = closure.getClass().getClassLoader();
        if (!isSyntheticLambda(lambda)) {
            boolean isStatic = lambda.getImplMethodKind() == MethodHandleInfo.REF_invokeStatic;
            Object bound = lambda.getCapturedArgCount() == 1 && !isStatic ? lambda.getCapturedArg(0) : null;
            return new Target(lambda.getImplClass(), lambda.getImplMethodName(), lambda.getImplMethodSignature(),
                isStatic, bound, true);
        }
        byte[] bytes;
        try {
            bytes = ClassFileLocator.ForClassLoader.of(loader).locate(lambda.getImplClass().replace('/', '.'))
                .resolve();
        }
        catch (Exception e) {
            throw new StuntException("Cannot read the bytecode of " + lambda.getImplClass(), e);
        }
        LastCallFinder finder = new LastCallFinder(lambda.getImplMethodName(), lambda.getImplMethodSignature());
        new ClassReader(bytes).accept(finder, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        if (finder.owner == null) {
            throw new StuntException("The closure does not call any method; when(...)/verify(...) needs exactly one"
                + " call such as when(() -> service.find(any()))");
        }
        return new Target(finder.owner, finder.name, finder.descriptor, finder.opcode == Opcodes.INVOKESTATIC, null,
            false);
    }

    private static boolean isSyntheticLambda(SerializedLambda lambda) {
        return lambda.getImplMethodName().startsWith("lambda$");
    }

    /** The declared parameter types of a {@code thenDo} closure, without captured variables. */
    public static Class< ? >[] parameterTypes(Serializable closure) {
        SerializedLambda lambda = serialize(closure);
        Type[] signature = Type.getArgumentTypes(lambda.getImplMethodSignature());
        java.util.List< Type > params = new java.util.ArrayList<>();
        if (isSyntheticLambda(lambda)) {
            // captured variables precede the declared parameters; a captured 'this' is the receiver of an
            // instance lambda body and is not part of the signature
            boolean instanceBody = lambda.getImplMethodKind() != MethodHandleInfo.REF_invokeStatic;
            int skip = lambda.getCapturedArgCount() - (instanceBody ? 1 : 0);
            for (int i = Math.max(skip, 0); i < signature.length; i++) {
                params.add(signature[i]);
            }
        }
        else {
            boolean isStatic = lambda.getImplMethodKind() == MethodHandleInfo.REF_invokeStatic;
            if (!isStatic && lambda.getCapturedArgCount() == 0) {
                params.add(Type.getObjectType(lambda.getImplClass())); // unbound reference: receiver first
            }
            params.addAll(java.util.Arrays.asList(signature));
        }
        ClassLoader loader = closure.getClass().getClassLoader();
        Class< ? >[] result = new Class< ? >[params.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = load(params.get(i), loader);
        }
        return result;
    }

    private static Class< ? > load(Type type, ClassLoader loader) {
        switch (type.getSort()) {
            case Type.VOID: return void.class;
            case Type.BOOLEAN: return boolean.class;
            case Type.CHAR: return char.class;
            case Type.BYTE: return byte.class;
            case Type.SHORT: return short.class;
            case Type.INT: return int.class;
            case Type.LONG: return long.class;
            case Type.FLOAT: return float.class;
            case Type.DOUBLE: return double.class;
            case Type.ARRAY:
                return java.lang.reflect.Array.newInstance(load(type.getElementType(), loader), new int[type.getDimensions()])
                    .getClass();
            default:
                try {
                    return Class.forName(type.getClassName(), false, loader);
                }
                catch (ClassNotFoundException e) {
                    throw new StuntException("Cannot load closure parameter type " + type.getClassName(), e);
                }
        }
    }

    private static final class LastCallFinder extends ClassVisitor {

        private final String methodName;
        private final String methodDescriptor;
        String owner;
        String name;
        String descriptor;
        int opcode;

        LastCallFinder(String methodName, String methodDescriptor) {
            super(Opcodes.ASM9);
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
        }

        @Override
        public MethodVisitor visitMethod(int access, String n, String d, String signature, String[] exceptions) {
            if (!n.equals(methodName) || !d.equals(methodDescriptor)) {
                return null;
            }
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int op, String o, String nm, String desc, boolean itf) {
                    if (nm.equals("<init>")) {
                        return;
                    }
                    owner = o;
                    name = nm;
                    descriptor = desc;
                    opcode = op;
                }
            };
        }
    }
}
