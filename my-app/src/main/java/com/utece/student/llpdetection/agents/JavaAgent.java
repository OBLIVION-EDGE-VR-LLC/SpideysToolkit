package com.utece.student.llpdetection.agents;

import com.utece.student.llpdetection.transformers.LLPTransformer;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class JavaAgent {

    static String jvmName = ManagementFactory.getRuntimeMXBean().getName();
    static String jvmPid = jvmName.substring(0, jvmName.indexOf('@'));

    public JavaAgent() {
        System.out.println("[JavaAgent] PID: " + jvmPid);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("[Agent] In agentmain method");
        Set<String> targets = parseTargetPackages(agentArgs);
        LLPTransformer transformer = new LLPTransformer(targets);

        inst.addTransformer(transformer, true);
        System.out.println("[Agent] LLPTransformer registered for: " +
                (targets.isEmpty() ? "all packages" : targets));

        retransformLoadedClasses(inst, targets);
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[Agent] In premain method");
        Set<String> targets = parseTargetPackages(agentArgs);
        LLPTransformer transformer = new LLPTransformer(targets);

        inst.addTransformer(transformer, true);
        System.out.println("[Agent] LLPTransformer registered for: " +
                (targets.isEmpty() ? "all packages" : targets));
    }

    private static Set<String> parseTargetPackages(String agentArgs) {
        Set<String> targets = new HashSet<>();
        if (agentArgs != null && !agentArgs.isEmpty()) {
            for (String pkg : agentArgs.split(",")) {
                targets.add(pkg.trim());
            }
        }
        return targets;
    }

    private static void retransformLoadedClasses(Instrumentation inst, Set<String> targets) {
        if (!inst.isRetransformClassesSupported()) {
            System.out.println("[Agent] Retransformation not supported");
            return;
        }

        ArrayList<Class<?>> toRetransform = new ArrayList<>();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            if (inst.isModifiableClass(clazz) && shouldTransform(clazz.getName(), targets)) {
                toRetransform.add(clazz);
            }
        }

        if (!toRetransform.isEmpty()) {
            try {
                inst.retransformClasses(toRetransform.toArray(new Class[0]));
                System.out.println("[Agent] Retransformed " + toRetransform.size() + " classes");
            } catch (UnmodifiableClassException e) {
                System.out.println("[Agent] Retransformation failed: " + e.getMessage());
            }
        }
    }

    private static boolean shouldTransform(String className, Set<String> targets) {
        if (targets.isEmpty()) return true;
        for (String target : targets) {
            if (className.startsWith(target)) return true;
        }
        return false;
    }
}
