package com.kewei.parts;

import com.alibaba.fastjson.*;
import com.kewei.parts.infrastructure.chain.*;
import com.kewei.parts.infrastructure.fisco.FiscoBcosDriver;
import com.kewei.parts.infrastructure.fisco.FiscoContractDeployer;
import com.kewei.parts.infrastructure.storage.*;
import org.fisco.bcos.sdk.v3.codec.ContractCodec;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真正的FISCO SDK编码器，输出供本地EVM执行的调用向量。 */
class FiscoNativeCodecTest {
    @Test void nativeSdkEncodesActualCompiledSolidityAbi() throws Exception {
        String abi=new String(Files.readAllBytes(Paths.get("src/main/resources/fisco/TraceAnchor.abi.json")),StandardCharsets.UTF_8);
        ContractCodec codec=new ContractCodec(new CryptoSuite(0),false);
        Map<String,Object> vectors=new LinkedHashMap<>();List<Object> writes=new ArrayList<>(),reads=new ArrayList<>();vectors.put("writes",writes);vectors.put("reads",reads);
        String hash=String.join("",Collections.nCopies(64,"a")),did="did:test:native-sdk";
        Map<String,byte[]> anchor=ContractArguments.anchor(ChainAnchorRequest.builder().businessType("trace_event").businessId(999L).dataHash(hash).objectCid("native-sdk-cid").partsDid(did).build());
        add(codec,abi,writes,"save_anchor",anchor,null);
        add(codec,abi,writes,"append_vc",ContractArguments.vc(did,"VC-NATIVE",hash),null);
        add(codec,abi,writes,"revoke_vc",ContractArguments.vc(did,"VC-NATIVE",null),null);
        JSONObject fixture=JSON.parseObject(new String(Files.readAllBytes(Paths.get("contracts/trace_anchor/testdata/relay-vector.json")),StandardCharsets.UTF_8));
        FiscoContractDeployer.Manifest manifest=new FiscoContractDeployer.Manifest();
        manifest.setSdkConfig("file:/external/sdk_config.toml");manifest.setExpectedChainId("chain0");
        manifest.setExpectedAccount("0x1111111111111111111111111111111111111111");manifest.setLocalChainCode("main-regulator");
        manifest.getRelayPublicKeys().put("sub-production",fixture.getString("publicKey"));
        String binary=new String(Files.readAllBytes(Paths.get("src/main/resources/fisco/TraceAnchor.bin")),StandardCharsets.UTF_8).trim();
        vectors.put("deploymentData",hex(codec.encodeConstructor(abi,binary,FiscoContractDeployer.constructorArguments(manifest))));
        // 同一依赖集合下也可加载国密SDK编码器；国密字节码另需官方国密编译器。
        ContractCodec smCodec=new ContractCodec(new CryptoSuite(1),false);
        assertFalse(Arrays.equals(codec.encodeMethod(abi,"contract_info",Collections.emptyList()),smCodec.encodeMethod(abi,"contract_info",Collections.emptyList())));
        Map<String,byte[]> proof=new HashMap<>();fixture.getJSONObject("arguments").forEach((key,value)->ContractArguments.put(proof,key,String.valueOf(value)));
        add(codec,abi,writes,"receive_cross_anchor",proof,null);
        add(codec,abi,reads,"get_anchor",ContractArguments.anchorKey("trace_event",999L),Arrays.asList(hash,"native-sdk-cid",did));
        add(codec,abi,reads,"get_vc",ContractArguments.vc(did,"VC-NATIVE",null),Collections.singletonList("0"));
        add(codec,abi,reads,"contract_info",Collections.emptyMap(),Arrays.asList("parts-trace-anchor/v2","main-regulator"));
        Files.write(Paths.get("target/fisco-codec-vectors.json"),JSON.toJSONString(vectors,true).getBytes(StandardCharsets.UTF_8));
        assertEquals(4,writes.size());assertEquals(3,reads.size());
    }
    private void add(ContractCodec codec,String abi,List<Object> result,String method,Map<String,byte[]> args,Object expected) throws Exception {
        byte[] bytes=codec.encodeMethod(abi,method,FiscoBcosDriver.parameters(method,args));assertTrue(bytes.length>=4);
        Map<String,Object> vector=new LinkedHashMap<>();vector.put("method",method);vector.put("data",hex(bytes));if(expected!=null)vector.put("expected",expected);result.add(vector);
    }
    private String hex(byte[] bytes) {
        StringBuilder hex=new StringBuilder("0x");for(byte value:bytes)hex.append(String.format("%02x",value&255));return hex.toString();
    }
}
