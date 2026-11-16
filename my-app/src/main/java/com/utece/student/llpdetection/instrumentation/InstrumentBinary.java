package com.utece.student.llpdetection.instrumentation;

import com.utece.student.llpdetection.instrumentation.structures.context_t;

public class InstrumentBinary {

    private static boolean nativeAvailable = false;
    private Trampoline<Object> instrumentThis;
    private context_t context;

    static {
        try {
            Class.forName("com.utece.student.llpdetection.instrumentation.inlineassembly.NativeAssemblyRegister64Wrapper");
            nativeAvailable = true;
        } catch (Throwable t) {
            System.out.println("[InstrumentBinary] Native assembly library not available: " + t.getMessage());
            System.out.println("[InstrumentBinary] Register-level instrumentation disabled. Using Java-level predicate detection instead.");
        }
    }

    public static boolean isNativeAvailable() {
        return nativeAvailable;
    }

    public InstrumentBinary() {
        if (!nativeAvailable) {
            System.out.println("[InstrumentBinary] Running in Java-only mode");
            return;
        }

        context = new context_t();
        this.instrumentThis = new Trampoline<>() {
            @Override
            public Object get() {
                return super.get();
            }

            @Override
            public Trampoline<Object> run() {
                try {
                    long rip = com.utece.student.llpdetection.instrumentation.inlineassembly.NativeAssemblyRegister64Wrapper.instance.print_eip();
                    com.utece.student.llpdetection.instrumentation.CLibrary.INSTANCE.getcontext(context);
                    long rax = com.utece.student.llpdetection.instrumentation.inlineassembly.NativeAssemblyRegister64Wrapper.instance.print_rax();
                    long rbx = com.utece.student.llpdetection.instrumentation.inlineassembly.NativeAssemblyRegister64Wrapper.instance.print_rbx();
                    long rcx = com.utece.student.llpdetection.instrumentation.inlineassembly.NativeAssemblyRegister64Wrapper.instance.print_rcx();
                    long rdx = com.utece.student.llpdetection.instrumentation.inlineassembly.NativeAssemblyRegister64Wrapper.instance.print_rdx();

                    System.out.println(String.format("RIP: 0x%016X  RAX: 0x%016X  RBX: 0x%016X  RCX: 0x%016X  RDX: 0x%016X",
                            rip, rax, rbx, rcx, rdx));
                } catch (Exception e) {
                    System.out.println("[InstrumentBinary] Native call failed: " + e.getMessage());
                }
                return this;
            }
        };
    }

    public void invoke() {
        if (instrumentThis != null) {
            instrumentThis.run();
        }
    }

    public long getLrAtSpecified(int specified) {
        if (!nativeAvailable) return 0L;
        try {
            return com.utece.student.llpdetection.instrumentation.inlineassembly.NativeAssemblyRegister64Wrapper.instance.print_lr_at_callee(specified);
        } catch (Exception e) {
            return 0L;
        }
    }

    public long getRip() {
        if (!nativeAvailable) return 0L;
        try {
            return com.utece.student.llpdetection.instrumentation.inlineassembly.NativeAssemblyRegister64Wrapper.instance.print_eip();
        } catch (Exception e) {
            return 0L;
        }
    }
}
