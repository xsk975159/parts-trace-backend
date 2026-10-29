package com.kewei.parts;

import com.kewei.parts.infrastructure.chain.ChainNetworkProperties;
import com.kewei.parts.infrastructure.chainmaker.ChainMakerSdkInvoker;
import com.kewei.parts.infrastructure.chainmaker.ChainMakerKeyMaterial;
import com.kewei.parts.infrastructure.storage.ChainRouter;
import com.google.protobuf.ByteString;
import org.chainmaker.pb.common.ResultOuterClass.*;
import org.chainmaker.sdk.ChainClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.io.StringWriter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.chainmaker.sdk.crypto.ChainmakerX509CryptoSuite;
import org.chainmaker.sdk.utils.CryptoUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChainMakerDriverTest {
    @TempDir Path certificateDirectory;
    @Test void tlsTrustRootsAcceptFilesAndDirectoriesAndRejectEmptyDirectories() throws Exception {
        byte[] certificate;
        try (java.io.InputStream stream = getClass().getResourceAsStream("/chainmaker/public-node.crt")) {
            certificate = org.springframework.util.StreamUtils.copyToByteArray(stream);
        }
        Path root = certificateDirectory.resolve("ca.crt");
        Files.write(root, certificate);
        org.chainmaker.sdk.config.NodeConfig node = new org.chainmaker.sdk.config.NodeConfig();
        node.setEnableTls(true);
        org.chainmaker.sdk.config.ChainClientConfig config = new org.chainmaker.sdk.config.ChainClientConfig();
        config.setNodes(new org.chainmaker.sdk.config.NodeConfig[]{node});
        node.setTrustRootPaths(new String[]{root.toString()});
        ChainMakerKeyMaterial.prepare(config);
        assertArrayEquals(certificate, node.getTrustRootBytes()[0]);
        node.setTrustRootBytes(null);
        node.setTrustRootPaths(new String[]{certificateDirectory.toString()});
        ChainMakerKeyMaterial.prepare(config);
        assertEquals(1, node.getTrustRootBytes().length);
        Files.delete(root);
        node.setTrustRootBytes(null);
        assertThrows(IllegalArgumentException.class, () -> ChainMakerKeyMaterial.prepare(config));
    }
    @Test void grpcMetadataUsesCompatibleGuavaAtRuntime() {
        io.grpc.Metadata headers = new io.grpc.Metadata();
        io.grpc.Metadata.Key<String> key = io.grpc.Metadata.Key.of("trace-chain", io.grpc.Metadata.ASCII_STRING_MARSHALLER);
        headers.put(key, "chain1");
        assertEquals("chain1", headers.get(key));
    }
    @Test void nativeTlsRequiredByChainMakerIsPackagedAndLoadable() throws Exception {
        io.netty.handler.ssl.OpenSsl.ensureAvailability();
        io.netty.handler.ssl.SslContext context = io.grpc.netty.GrpcSslContexts.forClient()
                .sslProvider(io.netty.handler.ssl.SslProvider.OPENSSL).build();
        try { assertTrue(context.isClient()); }
        finally { io.netty.util.ReferenceCountUtil.release(context); }
    }
    @Test void independentReaderChecksSourceTransactionPayloadAndBlock() throws Exception {
        String hash=String.join("",Collections.nCopies(64,"a"));
        com.kewei.parts.infrastructure.chainmaker.CrossChainAnchorPayload source=com.kewei.parts.infrastructure.chainmaker.CrossChainAnchorPayload.builder()
            .sourceChainId(ChainRouter.SUB_PRODUCTION).sourceTxHash("source-tx").sourceBlockHeight(9L).businessType("trace_event").businessId(1L).dataHash(hash).partsDid("did:test:unit").objectCid("cid").build();
        org.chainmaker.pb.common.Request.Payload.Builder payload=org.chainmaker.pb.common.Request.Payload.newBuilder().setChainId("chain1").setTxId("source-tx").setContractName("trace_anchor").setMethod("save_anchor");
        Map<String,String> args=new LinkedHashMap<>();args.put("business_type","trace_event");args.put("business_id","1");args.put("data_hash",hash);args.put("parts_did","did:test:unit");args.put("object_cid","cid");
        for(Map.Entry<String,String> entry:args.entrySet())payload.addParameters(org.chainmaker.pb.common.Request.KeyValuePair.newBuilder().setKey(entry.getKey()).setValue(ByteString.copyFromUtf8(entry.getValue())));
        org.chainmaker.pb.common.ChainmakerTransaction.Transaction.Builder tx=org.chainmaker.pb.common.ChainmakerTransaction.Transaction.newBuilder().setPayload(payload)
            .setResult(org.chainmaker.pb.common.ResultOuterClass.Result.newBuilder().setCode(TxStatusCode.SUCCESS).setContractResult(ContractResult.newBuilder().setCode(0)));
        when(client.getChainId()).thenReturn("chain1");
        when(client.getTxByTxId(anyString(),anyLong())).thenReturn(org.chainmaker.pb.common.ChainmakerTransaction.TransactionInfo.newBuilder().setBlockHeight(9).setTransaction(tx).build());
        driver.verifyAnchorTransaction(ChainRouter.SUB_PRODUCTION,source);
        tx.setPayload(payload.setMethod("append_vc"));when(client.getTxByTxId(anyString(),anyLong())).thenReturn(org.chainmaker.pb.common.ChainmakerTransaction.TransactionInfo.newBuilder().setBlockHeight(9).setTransaction(tx).build());
        assertThrows(IllegalStateException.class,()->driver.verifyAnchorTransaction(ChainRouter.SUB_PRODUCTION,source));
        tx.setPayload(payload.setMethod("save_anchor"));when(client.getTxByTxId(anyString(),anyLong())).thenReturn(org.chainmaker.pb.common.ChainmakerTransaction.TransactionInfo.newBuilder().setBlockHeight(8).setTransaction(tx).build());
        assertThrows(IllegalStateException.class,()->driver.verifyAnchorTransaction(ChainRouter.SUB_PRODUCTION,source));
    }
    private ChainMakerSdkInvoker driver;
    private ChainNetworkProperties properties;
    private ChainClient client;
    private TxResponse response;
    @BeforeEach void prepare() throws Exception {
        client=mock(ChainClient.class);properties=new ChainNetworkProperties();driver=new ChainMakerSdkInvoker(new DefaultResourceLoader(),properties);
        Map<String,ChainClient> connections=new HashMap<>();connections.put(ChainRouter.SUB_PRODUCTION,client);
        ReflectionTestUtils.setField(driver,"clients",connections);
        response=TxResponse.newBuilder().setCode(TxStatusCode.SUCCESS).setTxId("confirmed-cm-tx").setTxBlockHeight(9)
                .setContractResult(ContractResult.newBuilder().setCode(0).setResult(ByteString.copyFromUtf8("actual-result"))).build();
    }
    @Test void confirmedSdkReceiptAndContractResultAreUsed() throws Exception {
        when(client.invokeContract(anyString(),anyString(),anyString(),anyMap(),anyLong(),anyLong())).thenReturn(response);
        assertEquals("confirmed-cm-tx",driver.invoke(ChainRouter.SUB_PRODUCTION,"save_anchor",Collections.emptyMap()).getTxId());
        when(client.queryContract(anyString(),anyString(),anyString(),anyMap(),anyLong())).thenReturn(response);
        assertEquals("actual-result",driver.query(ChainRouter.SUB_PRODUCTION,"get_anchor",Collections.emptyMap()));
    }
    @Test void failedOrMissingContractReceiptIsRejected() {
        assertThrows(Exception.class,()->ChainMakerSdkInvoker.check(null));
        assertThrows(Exception.class,()->ChainMakerSdkInvoker.check(response.toBuilder().clearContractResult().build()));
        assertThrows(Exception.class,()->ChainMakerSdkInvoker.check(response.toBuilder().setContractResult(ContractResult.newBuilder().setCode(1)).build()));
        assertThrows(Exception.class,()->ChainMakerSdkInvoker.check(response.toBuilder().setCodeValue(1).build()));
    }
    @Test void unconfirmedReceiptCannotBecomeSuccessfulAnchor() throws Exception {
        properties.setTimeoutMs(1);
        when(client.invokeContract(anyString(),anyString(),anyString(),anyMap(),anyLong(),anyLong())).thenReturn(response.toBuilder().setTxBlockHeight(0).build());
        assertThrows(Exception.class,()->driver.invoke(ChainRouter.SUB_PRODUCTION,"save_anchor",Collections.emptyMap()));
    }
    @Test void sdkResponseWithoutHeightUsesTheActualConfirmedTransaction() throws Exception {
        when(client.getChainId()).thenReturn("chain1");
        org.chainmaker.pb.common.Request.Payload payload=org.chainmaker.pb.common.Request.Payload.newBuilder()
                .setChainId("chain1").setTxId("confirmed-cm-tx").setContractName("trace_anchor").setMethod("save_anchor").build();
        org.chainmaker.pb.common.ChainmakerTransaction.Transaction tx=org.chainmaker.pb.common.ChainmakerTransaction.Transaction.newBuilder()
                .setPayload(payload).setResult(Result.newBuilder().setCode(TxStatusCode.SUCCESS).setContractResult(ContractResult.newBuilder().setCode(0))).build();
        when(client.getTxByTxId(eq("confirmed-cm-tx"),anyLong())).thenReturn(
                org.chainmaker.pb.common.ChainmakerTransaction.TransactionInfo.newBuilder().setBlockHeight(12).setTransaction(tx).build());
        when(client.invokeContract(anyString(),anyString(),anyString(),anyMap(),anyLong(),anyLong())).thenReturn(response.toBuilder().setTxBlockHeight(0).build());
        assertEquals(Long.valueOf(12),driver.invoke(ChainRouter.SUB_PRODUCTION,"save_anchor",Collections.emptyMap()).getBlockHeight());
        when(client.getTxByTxId(eq("confirmed-cm-tx"),anyLong())).thenReturn(
                org.chainmaker.pb.common.ChainmakerTransaction.TransactionInfo.newBuilder().setBlockHeight(12)
                        .setTransaction(tx.toBuilder().setPayload(payload.toBuilder().setMethod("append_vc"))).build());
        assertThrows(org.chainmaker.sdk.ChainClientException.class,()->driver.invoke(ChainRouter.SUB_PRODUCTION,"save_anchor",Collections.emptyMap()));
    }
    @Test void otherLogicalChainCannotReuseExistingClient() {
        assertThrows(IllegalStateException.class,()->driver.query(ChainRouter.SUB_LOGISTICS,"get_anchor",Collections.emptyMap()));
        driver.close();verify(client).stop();assertFalse(driver.configured(ChainRouter.SUB_PRODUCTION));
    }
    @Test void chainMakerSdkStillSignsAndReadsKeysWithSharedCryptoDependencies() throws Exception {
        ChainmakerX509CryptoSuite suite=ChainmakerX509CryptoSuite.newInstance(false);
        byte[] message="mixed-platform-sdk".getBytes(StandardCharsets.UTF_8);
        for(String algorithm:new String[]{"SHA256withECDSA","SM3withSM2"}) {
            KeyPair keys;
            if(algorithm.equals("SM3withSM2")) {
                KeyPairGenerator generator=KeyPairGenerator.getInstance("EC","BC");
                generator.initialize(new ECGenParameterSpec("sm2p256v1"));keys=generator.generateKeyPair();
            } else keys=suite.keyGen();
            suite.setAlgorithm(algorithm);
            StringWriter text=new StringWriter();try(JcaPEMWriter pem=new JcaPEMWriter(text)){pem.writeObject(keys.getPrivate());}
            byte[] original=text.toString().getBytes(StandardCharsets.UTF_8);
            org.chainmaker.sdk.config.ChainClientConfig config=new org.chainmaker.sdk.config.ChainClientConfig();
            config.setUserKeyBytes(original);config.setUserSignKeyBytes(original);ChainMakerKeyMaterial.prepare(config);
            PrivateKey restored=CryptoUtils.getPrivateKeyFromBytes(config.getUserSignKeyBytes());
            assertArrayEquals(restored.getEncoded(),CryptoUtils.getPrivateKeyFromBytes(config.getUserKeyBytes()).getEncoded());
            byte[] signed=suite.sign(restored,message);
            Signature verifier=Signature.getInstance(algorithm,"BC");verifier.initVerify(keys.getPublic());verifier.update(message);
            assertTrue(verifier.verify(signed));
        }
    }
    @Test void keyLoaderRejectsPublicKeysAndMultiplePrivateKeys() throws Exception {
        KeyPair pair=KeyPairGenerator.getInstance("RSA").generateKeyPair();
        StringWriter publicText=new StringWriter();try(JcaPEMWriter pem=new JcaPEMWriter(publicText)){pem.writeObject(pair.getPublic());}
        assertThrows(IllegalArgumentException.class,()->ChainMakerKeyMaterial.normalize(publicText.toString().getBytes(StandardCharsets.UTF_8)));
        StringWriter privateText=new StringWriter();try(JcaPEMWriter pem=new JcaPEMWriter(privateText)){pem.writeObject(pair.getPrivate());pem.writeObject(pair.getPrivate());}
        assertThrows(IllegalArgumentException.class,()->ChainMakerKeyMaterial.normalize(privateText.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
