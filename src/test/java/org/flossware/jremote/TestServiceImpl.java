package org.flossware.jremote;

public class TestServiceImpl implements TestService {
    @Override
    public String echo(String message) {
        return "Echo: " + message;
    }

    @Override
    public int add(int a, int b) {
        return a + b;
    }

    @Override
    public void voidMethod() {
    }

    @Override
    public String throwsException() throws Exception {
        throw new IllegalStateException("Test exception");
    }

    @Override
    public Object getNullValue() {
        return null;
    }

    public String unauthorizedMethod() {
        return "This should never be called remotely";
    }
}
