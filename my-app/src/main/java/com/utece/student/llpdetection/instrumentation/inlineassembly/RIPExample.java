public class RIPExample {
    // Load the native library
    static {
        System.load("/home/bd007/projects/internet_bitbucket/predicatedetectionproject/my-app/src/main/java/com/utece/student/llpdetection/instrumentation/inlineassembly/libRIPExample.so");
    }

    // Native method declaration to get the RIP value
    private native long getRIP(int callee);

    public long functionStub(){
        long x = getRIP(0);
        long z_1 = getRIP(1);
        long z_2 = getRIP(0);
        return z_1;
    }

    public long AnotherFunctionDeep(){
        return functionStub();
    }

    public static void main(String[] args) {
        RIPExample example = new RIPExample();
        RIPExample example_2 = new RIPExample();
        long ripValue1 = example.functionStub();
        long ripValue2 = example.functionStub();
        long ripValue3 = example.functionStub();
        System.out.println("RIP value: 0x" + Long.toHexString(ripValue1));
        System.out.println("RIP value: 0x" + Long.toHexString(ripValue2));
        System.out.println("RIP value: 0x" + Long.toHexString(ripValue3));
        System.out.println("RIP value: 0x" + Long.toHexString(example.getRIP(0)));
        System.out.println("RIP value: 0x" + Long.toHexString(example.functionStub()));
        System.out.println("RIP value: 0x" + Long.toHexString(example_2.AnotherFunctionDeep()));
	System.out.println("RIP value example_2: 0x" + Long.toHexString(example_2.getRIP(2)));
	System.out.println("RIP value example_2: 0x" + Long.toHexString(example_2.getRIP(2)));
	System.out.println("RIP value example_2: 0x" + Long.toHexString(example_2.getRIP(2)));
	System.out.println("RIP value example_2: 0x" + Long.toHexString(example_2.getRIP(2)));
	System.out.println("RIP value example_2: 0x" + Long.toHexString(example_2.getRIP(2)));

    }
}
