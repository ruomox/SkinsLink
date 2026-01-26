import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.util.HashUtil;
import com.ruomox.skinslink.core.util.HttpUtil;
import com.ruomox.skinslink.core.util.LogUtil;
import com.ruomox.skinslink.core.util.SkinCodec;

/**
 * 手动测试类
 * 运行方式：点击 main 方法左边的绿色三角形 -> Run 'ManualTest.main()'
 * 测试完后可以删除此文件
 */
public class ManualTest {

    public static void main(String[] args) {
        System.out.println("=== 开始 Core 模块单元测试 ===\n");

        // 1. 初始化环境 (模拟一个 Logger)
        Logger mockLogger = new Logger() {
            @Override public void info(String message) { System.out.println("[INFO] " + message); }
            @Override public void warn(String message) { System.out.println("[WARN] " + message); }
            @Override public void error(String message) { System.err.println("[ERROR] " + message); }
            @Override public void error(String message, Throwable t) {
                System.err.println("[ERROR] " + message);
                t.printStackTrace();
            }
        };

        // 初始化工具类 (开启 Debug 模式)
        LogUtil.setDebugMode(true);
        HttpUtil.init(mockLogger);
        SkinCodec.init(mockLogger);

        // ==========================================
        // 测试 1: HashUtil
        // ==========================================
        System.out.println("--- 测试 1: HashUtil ---");
        String url = "http://textures.minecraft.net/texture/292009a4925b58f02c77dadc3ecef07ea4c7472f64e0fdc32ce5522489362680";
        String hash = HashUtil.hashUrl(url);
        System.out.println("URL: " + url);
        System.out.println("Hash: " + hash);
        System.out.println("Hash 长度 (应为64): " + hash.length());
        System.out.println();

        // ==========================================
        // 测试 2: SkinCodec (解析 Mojang 格式)
        // ==========================================
        System.out.println("--- 测试 2: SkinCodec (Mojang) ---");
        // 这是你之前发的 Notch 的真实数据
        String mojangJson = """
            {
              "id" : "069a79f444e94726a5befca90e38aaf5",
              "name" : "Notch",
              "properties" : [ {
                "name" : "textures",
                "value" : "ewogICJ0aW1lc3RhbXAiIDogMTc2OTM4Njg4MDc5NiwKICAicHJvZmlsZUlkIiA6ICIwNjlhNzlmNDQ0ZTk0NzI2YTViZWZjYTkwZTM4YWFmNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJOb3RjaCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yOTIwMDlhNDkyNWI1OGYwMmM3N2RhZGMzZWNlZjA3ZWE0Yzc0NzJmNjRlMGZkYzMyY2U1NTIyNDg5MzYyNjgwIgogICAgfQogIH0KfQ==",
                "signature" : "PV6gC9+va7z2d220/c1/ku2lYSpOT6ZYy3IfNS4qIqk6ouO093kJPNtUApgWVKKsUZTBxOhxXetQtVs5Nwz9Il0qZbdn0RTa5/OspTkpbXShWnYILQAxKinSQ8SQ9fxWK67KLwBA0j6NIkByT8ZOzK3v/mgcqwUsNhREJk28CEqy2Sjt7mg75aZBjB5EAfYRhx6o5LGdN4Yg84EcBh5jaTufvqYJzW3lwN2du9ctd2cHQ8xJTuU8HuCixzHHjamCn2WGXow6FMYR8vXeHZu26xjZVMjZG8VWT18LGlmRch9FLw6LtVsi8aDWz4x5vEmcY5FNGGCdeaABjf3ed0P0/JYlCmxPh1wI86i6zKUe3LGDXGLzvoVzkvJt63v7fBk73oTDyxtTWpHE/1D4ERhUfLXzTBbk/neM5EmWfaySarbS8reil5o+HrrYmtRpe+Gq3aIg5azYl9VVWUQ4KzJMqa9V1DQXFgs98wKIfwZ/rIXRw476Wm/qe+X9Mv3TH6tCIJH38NNYyYpisnA/kPciETUx4cCvHURXHmskyRxUgt+/t93nTXuQmiEPzoDqH5zkPihSAr7hFQb+W130m7LMVlNd9fCBDuQtBaBj1O9ZRvzIc6TtMhQulkKOJ9aQk1iAobF0WiAsD9A0NqQieMUn7HD3loraJVeCjaIhcLwI7jI="
              } ]
            }
            """;

        SkinCodec.SkinResult mojangResult = SkinCodec.parseAPIData(mojangJson);
        printResult("Mojang", mojangResult);

        // ==========================================
        // 测试 3: SkinCodec (解析 Geyser 格式)
        // ==========================================
        System.out.println("--- 测试 3: SkinCodec (Geyser) ---");
        // 这是你之前发的 Geyser 真实数据
        String geyserJson = """
            {"hash":"119f047338f8f648aba284725b4ca79fd3c01d506144aeb31e27806dc18c90a7","is_steve":false,"last_update":1769220979925,"signature":"Rfs2ZidHtD99tIDU7wYRluOX/0rymjvIc5dvwac07CYbHVQNNYNrYH+WQLxUMy23QlqboxQMRSJocMjaons5d9oYQaBeiv2qm0rxSj9i1gwg4tZ/ojuY0ENYNjcglzZcKzKwtsZIaCm/Q5ZKtZ9w8DA3VRHjON5m6CIncy+SnYqRCfOutEByrxLjP+heQKQ2yx78JXZUUs1q/HewXWaQob6wCA/0zjBu73vuOLKcoj7Iz+ijid04wjNm3hvieCzn27/lHI4c6WiJUsIN++H75JkRJMaLyIUWyiXcx5INlk2xmZz4aQdm7Uc2LZoqBk+NfJjEntImTy/PPL/plFuNKPOoHl2vOuCPg6q1a6XLzDU8kxF+6dQ4VvU+7mKskUoFHUp+nPBEPk7M3VefVHjHQxbH8D190xGOTa2FRY5w6T2mj8CJSRbPPDWUQQG9o+SzvSco032pdMk9+a6ZB9jDBoPZg6Njvzt8v86uyDI9lKcYvVHVvDmsREQvhfQN3u8hl4I47dhTu3fZQExzgSV0yH1yXlP2Sx2peftaPnM72KJVV69eAY2jRPYzmGHnhgJY03DxGSgeHgziO1RM+fISKnI18yqvcIoaUF/vytcjmBhWVM2E55+YQFQJ7iQ4bGzGWEPVw+4qmO+FDtd2Dm9om6w6ICxeoKp1bpTUdBL5ZqI=","texture_id":"c180a81dbc85b40715f564c0f99d8788d1d57e1903749cb66524671f79455467","value":"ewogICJ0aW1lc3RhbXAiIDogMTc1MDU5OTQ4MTMwMywKICAicHJvZmlsZUlkIiA6ICI2MTU1NTMyOTY2OWM0ZDA5YmFiOGJlNDNkYWUwYTRjMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJmaTAxNSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9jMTgwYTgxZGJjODViNDA3MTVmNTY0YzBmOTlkODc4OGQxZDU3ZTE5MDM3NDljYjY2NTI0NjcxZjc5NDU1NDY3IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="}
            """;

        SkinCodec.SkinResult geyserResult = SkinCodec.parseAPIData(geyserJson);
        printResult("Geyser", geyserResult);

        // ==========================================
        // 测试 4: HttpUtil (真实网络请求)
        // ==========================================
        System.out.println("--- 测试 4: HttpUtil (联网) ---");
        // 尝试请求 Mojang 的公钥接口（这个接口响应快且稳定）
        String testUrl = "https://api.minecraftservices.com/publickeys";

        System.out.println("正在请求: " + testUrl);
        // 注意：因为是异步的，这里用 join() 阻塞等待结果，仅限测试使用
        HttpUtil.Result<String> result = HttpUtil.get(testUrl).join();

        System.out.println("状态码: " + result.statusCode());
    }

    private static void printResult(String source, SkinCodec.SkinResult result) {
        if (result == null) {
            System.err.println(source + " 解析失败！");
            return;
        }
        System.out.println(source + " ID: " + result.skinID());
        System.out.println(source + " URL: " + result.skinURL());
        System.out.println(source + " Signed: " + (result.skinKey() != null));
        System.out.println();
    }
}
