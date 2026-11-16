package com.utece.student.llpdetection.agents;

import com.sun.tools.attach.VirtualMachine;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;

public class Launcher {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[Launcher] In premain method");
        JavaAgent.premain(agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("[Launcher] In agentmain method");
        JavaAgent.agentmain(agentArgs, inst);
    }

    public static void main(String[] args) throws Exception {
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        String pid = jvmName.substring(0, jvmName.indexOf('@'));

        String agentJar = getAgentJarPath();
        String agentArgs = args.length > 0 ? args[0] : "parallelalgorithms.group9.homework3";

        System.out.println("[Launcher] Attaching agent to PID " + pid);
        System.out.println("[Launcher] Agent JAR: " + agentJar);
        System.out.println("[Launcher] Target packages: " + agentArgs);

        VirtualMachine vm = VirtualMachine.attach(pid);
        vm.loadAgent(agentJar, agentArgs);
        vm.detach();

        System.out.println("[Launcher] Agent loaded successfully");
    }

    private static String getAgentJarPath() throws URISyntaxException {
        return Launcher.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
    }
}
