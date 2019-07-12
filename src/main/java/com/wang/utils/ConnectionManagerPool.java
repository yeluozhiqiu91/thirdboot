package com.wang.utils;

import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.util.IdleConnectionTimeoutThread;

/**
 * @author lijinhua
 * @since 2015年10月22日 下午6:12:10
 */

public final class ConnectionManagerPool {

    private static ConnectionManagerPool connectionManagerPool = new ConnectionManagerPool();
    /**
     * HTTP连接管理器，该连接管理器必须是线程安全的.
     */
    public HttpConnectionManager connectionManager;
    /**
     * 闲置连接超时时间, 由bean factory设置，缺省为60秒钟
     */
    private int defaultIdleConnTimeout = 60000;

    //        /** 默认等待HttpConnectionManager返回连接超时（只有在达到最大连接数时起作用）：1秒*/
//        private static final long          defaultHttpConnectionManagerTimeout = 3 * 1000;
    private int defaultMaxConnPerHost = 50;
    private int defaultMaxTotalConn = 300;

    private ConnectionManagerPool() {

        // 创建一个线程安全的HTTP连接池
        connectionManager = new MultiThreadedHttpConnectionManager();
        connectionManager.getParams().setDefaultMaxConnectionsPerHost(defaultMaxConnPerHost);
        connectionManager.getParams().setMaxTotalConnections(defaultMaxTotalConn);
        IdleConnectionTimeoutThread ict = new IdleConnectionTimeoutThread();
        ict.addConnectionManager(connectionManager);
        ict.setConnectionTimeout(defaultIdleConnTimeout);

        ict.start();
    }

    /**
     * 工厂方法
     *
     * @return
     */
    public static ConnectionManagerPool getInstance() {
        return connectionManagerPool;
    }
}
