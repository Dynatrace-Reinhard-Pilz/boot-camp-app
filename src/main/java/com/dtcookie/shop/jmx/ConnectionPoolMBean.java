package com.dtcookie.shop.jmx;

public interface ConnectionPoolMBean {

	int getActiveConnections();
	void setActiveConnections(int value);
	
}
