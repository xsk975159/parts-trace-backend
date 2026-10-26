package com.kewei.parts.infrastructure.chainmaker;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.chainmaker.sdk.config.ChainClientConfig;
import org.chainmaker.sdk.utils.FileUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** 保留SEC1中的曲线参数，只在内存中转为SDK能正确读取的PKCS8，不改写部署文件。 */
public final class ChainMakerKeyMaterial {
    private ChainMakerKeyMaterial() {}

    public static void prepare(ChainClientConfig config) throws Exception {
        if (config == null) throw new IllegalArgumentException("长安链缺少chain_client配置");
        config.setUserKeyBytes(normalize(load(config.getUserKeyBytes(), config.getUserKeyFilePath())));
        // 硬件签名账户由SDK专用解析器处理，不能当作软件PEM私钥转换。
        if (config.getPkcs11() == null || !config.getPkcs11().isEnabled())
            config.setUserSignKeyBytes(normalize(load(config.getUserSignKeyBytes(), config.getUserSignKeyFilePath())));
    }

    private static byte[] load(byte[] bytes, String path) throws Exception {
        if (bytes != null) return bytes;
        return path == null || path.trim().isEmpty() ? null : FileUtils.getFileOrResourceFileBytes(path);
    }

    public static byte[] normalize(byte[] pem) throws Exception {
        if (pem == null) return null;
        try (PEMParser parser = new PEMParser(new StringReader(new String(pem, StandardCharsets.UTF_8)))) {
            Object value = parser.readObject();
            PrivateKeyInfo info;
            if (value instanceof PEMKeyPair) info = ((PEMKeyPair) value).getPrivateKeyInfo();
            else if (value instanceof PrivateKeyInfo) info = (PrivateKeyInfo) value;
            else throw new IllegalArgumentException("长安链软件私钥须为未加密PEM，支持PKCS8、SEC1或PKCS1格式");
            if (parser.readObject() != null) throw new IllegalArgumentException("长安链私钥文件只能包含一把私钥");
            StringWriter text = new StringWriter();
            try (PemWriter writer = new PemWriter(text)) { writer.writeObject(new PemObject("PRIVATE KEY", info.getEncoded())); }
            return text.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
