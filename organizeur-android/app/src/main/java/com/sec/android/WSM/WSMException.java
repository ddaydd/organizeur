package com.sec.android.WSM;

public class WSMException extends RuntimeException {
    private static final long serialVersionUID = -8812690429042316862L;

    public WSMException() {
    }

    public WSMException(String str) {
        super(str);
    }

    public WSMException(String str, Throwable th) {
        super(str, th);
    }

    public WSMException(Throwable th) {
        super(th);
    }
}
