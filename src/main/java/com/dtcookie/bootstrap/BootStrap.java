package com.dtcookie.bootstrap;

import com.dtcookie.shop.backend.BackendServer;
import com.dtcookie.shop.frontend.FrontendServer;

public class BootStrap {

    public static void main(String[] args) throws Throwable {
    	System.setProperty("otel.java.global-autoconfigure.enabled", "true");
    	System.setProperty("otel.metrics.exporter", "none");
    	System.setProperty("otel.traces.exporter", "none");
    	System.setProperty("otel.logs.exporter", "none");
    	FrontendServer.submain(args);
    	BackendServer.submain(args);
    	LoadGenerator.submain(args);
        synchronized (BootStrap.class) {
            BootStrap.class.wait();
        }    	
    }    

}
