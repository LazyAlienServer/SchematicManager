package org.mod.schemcheck.data;

public class WsProtocol {
    public String id;
    public String action;
    public String data;

    public WsProtocol() {}
    public WsProtocol(String id, String action, String data) {
        this.id = id;
        this.action = action;
        this.data = data;
    }
}
