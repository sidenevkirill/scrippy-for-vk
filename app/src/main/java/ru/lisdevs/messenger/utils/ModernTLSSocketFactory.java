package ru.lisdevs.messenger.utils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class ModernTLSSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory delegate;

    public ModernTLSSocketFactory() throws KeyManagementException, NoSuchAlgorithmException {
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(null, new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        }, new SecureRandom());
        this.delegate = context.getSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
        return delegate.createSocket(s, host, port, autoClose);
    }

    @Override
    public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws java.io.IOException {
        return delegate.createSocket(host, port, localHost, localPort);
    }

    @Override
    public java.net.Socket createSocket(java.net.InetAddress host, int port) throws java.io.IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws java.io.IOException {
        return delegate.createSocket(address, port, localAddress, localPort);
    }
}