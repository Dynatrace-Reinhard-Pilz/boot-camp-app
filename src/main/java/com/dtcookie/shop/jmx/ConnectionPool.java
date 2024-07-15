package com.dtcookie.shop.jmx;

import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionPool implements ConnectionPoolMBean {
	
	private AtomicInteger activeConnections = new AtomicInteger(0);

	@Override
	public int getActiveConnections() {
		return activeConnections.get();
	}

	@Override
	public void setActiveConnections(int value) {
		activeConnections.set(value);
	}

}
