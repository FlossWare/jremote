package org.flossware.jremote;

public interface TestService {
    String echo(String message);
    int add(int a, int b);
    void voidMethod();
    String throwsException() throws Exception;
    Object getNullValue();
}
