package com.ruomox.skinslink.core.fetcher.impl;

import com.ruomox.skinslink.core.util.Constants;

/**
 * Mojang 专用 Fetcher
 * 本质上就是预设了 URL 的 CustomAPIFetcher
 */
public class MojangFetcher extends CustomAPIFetcher {

    public MojangFetcher() {
        super("Mojang", Constants.API_MOJANG_SESSION_PROFILE);
    }
}