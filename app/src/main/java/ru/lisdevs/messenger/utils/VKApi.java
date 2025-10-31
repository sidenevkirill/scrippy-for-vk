package ru.lisdevs.messenger.utils;

public class VKApi {
    public static final String BASE_URL = "https://api.vk.com/method/";
    public static final int PEER_OFFSET = 2_000_000_000;
    public static final double API_VERSION = 5.103;

    public static final String API_PROXY = "vk-api-proxy.xtrafrancyz.net";
    public static final String OAUTH_PROXY = "vk-oauth-proxy.xtrafrancyz.net";

    public static final String API_DOMAIN = "api.vk.com";
    public static final String OAUTH_DOMAIN = "oauth.vk.com";

    // inits in AppContext
    public static String apiDomain;
    public static String oauthDomain;

}
