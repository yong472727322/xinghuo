package com.xinghuo.demo;

/**
 * 代理信息
 */
public class ProxyMessage {

    /**
     * IP地址
     */
    private String addr;
    /**
     * 端口
     */
    private int port;
    /**
     * 账号
     */
    private String account;
    /**
     * 密码
     */
    private String password;

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
