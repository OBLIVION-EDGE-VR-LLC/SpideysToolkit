package com.utece.student.llpdetection.transformers;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;

public class LLPTransformer implements ClassFileTransformer {

    private final Set<String> targetPackages;
    private final Set<String> excludePackages;

    private static final String COLLECTOR =
            "com.utece.student.llpdetection.predicate.event.EventCollector";

    public LLPTransformer(Set<String> targetPackages) {
        this.targetPackages = targetPackages;
        this.excludePackages = new HashSet<>();
        excludePackages.add("java.");
        excludePackages.add("javax.");
        excludePackages.add("sun.");
        excludePackages.add("jdk.");
        excludePackages.add("com.sun.");
        excludePackages.add("javassist.");
        excludePackages.add("com.utece.student.llpdetection.predicate.");
        excludePackages.add("com.utece.student.llpdetection.transformers.");
        excludePackages.add("com.utece.student.llpdetection.agents.");
    }

    public LLPTransformer() {
        this(new HashSet<>());
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (className == null) return null;

        String dotName = className.replace('/', '.');

        for (String excluded : excludePackages) {
            if (dotName.startsWith(excluded)) return null;
        }

        if (!targetPackages.isEmpty()) {
            boolean matches = false;
            for (String target : targetPackages) {
                if (dotName.startsWith(target)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) return null;
        }

        try {
            ClassPool cp = ClassPool.getDefault();
            cp.insertClassPath(new ByteArrayClassPath(dotName, classfileBuffer));
            CtClass cc = cp.get(dotName);

            if (cc.isFrozen()) cc.defrost();
            if (cc.isInterface() || cc.isAnnotation() || cc.isEnum()) {
                cc.detach();
                return null;
            }

            boolean modified = instrumentClass(cc);

            if (modified) {
                byte[] result = cc.toBytecode();
                cc.detach();
                System.out.println("[LLPTransformer] Instrumented: " + dotName);
                return result;
            }

            cc.detach();
            return null;

        } catch (Exception e) {
            // Transformation failure -- return original bytecode
            return null;
        }
    }

    private boolean instrumentClass(CtClass cc) throws CannotCompileException {
        boolean modified = false;
        String className = cc.getName();

        for (CtMethod method : cc.getDeclaredMethods()) {
            if (Modifier.isAbstract(method.getModifiers()) ||
                Modifier.isNative(method.getModifiers())) {
                continue;
            }

            try {
                FieldAccessEditor editor = new FieldAccessEditor(className);
                method.instrument(editor);
                if (editor.wasModified()) {
                    modified = true;
                }
            } catch (CannotCompileException e) {
                // Skip methods that can't be instrumented
            }
        }

        return modified;
    }

    private static class FieldAccessEditor extends ExprEditor {
        private final String className;
        private boolean modified = false;

        FieldAccessEditor(String className) {
            this.className = className;
        }

        boolean wasModified() { return modified; }

        @Override
        public void edit(FieldAccess fa) throws CannotCompileException {
            String fieldName = fa.getFieldName();
            String fieldClassName = fa.getClassName();

            // Skip accesses to EventCollector itself to avoid recursion
            if (fieldClassName.contains("EventCollector") ||
                fieldClassName.contains("predicate")) {
                return;
            }

            try {
                if (fa.isReader()) {
                    fa.replace(
                        "{ $_ = $proceed(); " +
                        COLLECTOR + ".onFieldRead(\"" + fieldClassName + "\", \"" +
                        fieldName + "\", ($w)$_); }"
                    );
                    modified = true;
                } else if (fa.isWriter()) {
                    fa.replace(
                        "{ " + COLLECTOR + ".onFieldWrite(\"" + fieldClassName + "\", \"" +
                        fieldName + "\", null, ($w)$1); " +
                        "$proceed($1); }"
                    );
                    modified = true;
                }
            } catch (CannotCompileException e) {
                // Some field accesses can't be instrumented (e.g., constants)
                // Skip silently
            }
        }
    }
}
