package com.kewei.parts.infrastructure.chainmaker;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.chainmaker.sdk.config.ChainClientConfig;
import org.chainmaker.sdk.config.NodeConfig;
import org.chainmaker.sdk.utils.FileUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** 保留SEC1中的曲线参数，只在内存中转为SDK能正确读取的PKCS8，不改写部署文件。 */
public final class ChainMakerKeyMaterial {
    private ChainMakerKeyMaterial() {}

    public static void prepare(ChainClientConfig config) throws Exception {
        if (config == null) throw new IllegalArgumentException("长安链缺少chain_client配置");
        config.setUserKeyBytes(normalize(load(config.getUserKeyBytes(), config.getUserKeyFilePath())));
        // 硬件签名账户由SDK专用解析器处理，不能当作软件PEM私钥转换。
        if (config.getPkcs11() == null || !config.getPkcs11().isEnabled())
            config.setUserSignKeyBytes(normalize(load(config.getUserSignKeyBytes(), config.getUserSignKeyFilePath())));
        if (config.getNodes() != null) {
            for (NodeConfig node : config.getNodes()) {
                if (node == null || !node.isEnableTls()) continue;
                byte[][] roots = node.getTrustRootBytes();
                if (roots == null) {
                    List<byte[]> certificates = new ArrayList<>();
                    if (node.getTrustRootPaths() != null) {
                        for (String configured : node.getTrustRootPaths()) {
                            if (configured == null || configured.trim().isEmpty()) throw new IllegalArgumentException("TLS根证书路径不能为空");
                            Path path = Paths.get(configured);
                            if (Files.isDirectory(path)) {
                                // 原SDK仅扫描目录；同时接受已有配置样例中的单个证书路径。
                                try (Stream<Path> files = Files.list(path)) {
                                    for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)
                                            .filter(item -> item.getFileName().toString().endsWith(".crt")).sorted()::iterator)
                                        certificates.add(Files.readAllBytes(file));
                                }
                            } else certificates.add(Files.isRegularFile(path) ? Files.readAllBytes(path)
                                    : FileUtils.getFileOrResourceFileBytes(configured));
                        }
                    }
                    roots = certificates.toArray(new byte[0][]);
                    node.setTrustRootBytes(roots);
                }
                if (roots.length == 0) throw new IllegalArgumentException("TLS未加载任何根证书，请检查trust_root_paths");
                for (byte[] root : roots) if (root == null || root.length == 0) throw new IllegalArgumentException("TLS根证书内容为空");
            }
        }
    }

    private static byte[] load(byte[] bytes, String path) throws Exception {
        if (bytes != null) return bytes;
        if (path == null || path.trim().isEmpty()) return null;
        Path file = Paths.get(path);
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : FileUtils.getFileOrResourceFileBytes(path);
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
