package com.kewei.parts.infrastructure.fisco;

import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.model.dto.*;
import java.util.List;

/** 实际FISCO 3.x SDK连接；测试可以替换该连接，不改变驱动的ABI参数与回执检查。 */
public class FiscoConnection implements AutoCloseable {
    private final BcosSDK sdk;
    private final Client client;
    private final AssembleTransactionProcessor transactions;
    private final String abi;
    private final String address;

    public FiscoConnection(BcosSDK sdk, Client client, AssembleTransactionProcessor transactions, String abi, String address) {
        this.sdk = sdk;
        this.client = client;
        this.transactions = transactions;
        this.abi = abi;
        this.address = address;
    }

    public TransactionResponse invoke(String method, List<Object> arguments) throws Exception {
        return transactions.sendTransactionAndGetResponse(address, abi, method, arguments);
    }

    public CallResponse query(String method, List<Object> arguments) throws Exception {
        return transactions.sendCall(account(), address, abi, method, arguments);
    }

    public TransactionResponse deploy(String binary, List<Object> arguments) throws Exception {
        return transactions.deployAndGetResponse(abi, binary, arguments);
    }

    public CallResponse queryAt(String deployedAddress, String method, List<Object> arguments) throws Exception {
        return transactions.sendCall(account(), deployedAddress, abi, method, arguments);
    }
    public TransactionResponse invokeAt(String deployedAddress, String method, List<Object> arguments) throws Exception {
        return transactions.sendTransactionAndGetResponse(deployedAddress, abi, method, arguments);
    }
    public org.fisco.bcos.sdk.v3.model.TransactionReceipt receipt(String hash) {
        org.fisco.bcos.sdk.v3.client.protocol.response.BcosTransactionReceipt response=client.getTransactionReceipt(hash,false);
        return response==null?null:response.getTransactionReceipt();
    }
    public String encodedInput(String method,List<Object> arguments) throws Exception {
        byte[] bytes=new org.fisco.bcos.sdk.v3.codec.ContractCodec(client.getCryptoSuite(),false).encodeMethod(abi,method,arguments);
        StringBuilder result=new StringBuilder("0x");for(byte value:bytes)result.append(String.format("%02x",value&255));return result.toString();
    }

    public int cryptoType() { return client.getCryptoSuite().getCryptoTypeConfig(); }

    public String physicalIdentity() { return "FISCO_BCOS:" + client.getChainId() + ":" + client.getGroup(); }
    public String account() { return client.getCryptoSuite().getCryptoKeyPair().getAddress(); }
    public String address() { return address; }

    @Override public void close() { sdk.stopAll(); }
}
