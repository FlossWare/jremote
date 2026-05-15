package org.flossware.jremote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public record RemoteResponse(
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    Object result,

    RemoteException error,

    String returnTypeName
) {
    public static RemoteResponse success(Object result, Class<?> returnType) {
        return new RemoteResponse(result, null, returnType.getName());
    }

    public static RemoteResponse failure(RemoteException error) {
        return new RemoteResponse(null, error, null);
    }

    @JsonIgnore
    public boolean isSuccess() {
        return error == null;
    }
}
