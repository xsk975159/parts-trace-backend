package com.kewei.parts.infrastructure.confirmation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.kewei.parts.infrastructure.chain.*;
import com.kewei.parts.infrastructure.chainmaker.ChainMakerSdkInvoker;
import com.kewei.parts.infrastructure.fisco.*;
import com.kewei.parts.infrastructure.storage.*;
import org.springframework.core.io.DefaultResourceLoader;
import lombok.Data;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 独立的委员会配置工具，不启动数据库或业务服务。默认只读，--apply才发送配置交易。 */
public final class RelayCommitteeConfigurator {
    private RelayCommitteeConfigurator() {}
    @Data public static class Manifest {
        private ChainNetworkProperties.Network network;
        private JSONObject committee;
    }
    public static void main(String[] args) throws Exception {
        if(args.length<1 || args.length>2 || (args.length==2 && !"--apply".equals(args[1])))throw new IllegalArgumentException("用法：RelayCommitteeConfigurator manifest.json [--apply]；默认只读");
        Manifest manifest=JSON.parseObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8),Manifest.class);
        if(manifest==null || manifest.network==null || manifest.committee==null || manifest.network.getExpectedChainId()==null || manifest.network.getExpectedChainId().trim().isEmpty())
            throw new IllegalArgumentException("须指定目标网络、预期链ID和委员会");
        Map<String,byte[]> arguments=new HashMap<>();arguments.put("committee",JSON.toJSONBytes(manifest.committee));
        FiscoBcosDriver.parameters("configure_relay_committee",arguments); // 两个平台采用相同的名单和密钥限制。
        RelayConfirmationProperties expected=new RelayConfirmationProperties();expected.setCommitteeVersion(manifest.committee.getString("version"));
        JSONObject members=manifest.committee.getJSONObject("members");
        for(String id:members.keySet())expected.getMembers().put(id,members.getObject(id,RelayConfirmationProperties.Member.class));
        expected.setFaultyNodes((members.size()-1)/3);
        ChainNetworkProperties networks=new ChainNetworkProperties();networks.setStorageMode("real");networks.setValidatorOnly(true);
        networks.getNetworks().clear();networks.getNetworks().put(ChainRouter.MAIN_REGULATOR,manifest.network);networks.validate();
        DefaultResourceLoader resources=new DefaultResourceLoader();ChainDriver driver;
        if(manifest.network.getPlatform()==ChainPlatform.FISCO_BCOS)driver=new FiscoBcosDriver(networks,new FiscoConnectionFactory(resources));
        else driver=new ChainMakerSdkInvoker(resources,networks);
        try {
            if(driver instanceof FiscoBcosDriver)((FiscoBcosDriver)driver).initialize();else((ChainMakerSdkInvoker)driver).init();
            ChainSdkRouter router=new ChainSdkRouter(networks,Collections.singletonList(driver));router.verifyBindings();
            JSONObject actual=JSON.parseObject(driver.query(ChainRouter.MAIN_REGULATOR,"relay_policy",Collections.emptyMap()));
            if(actual==null)throw new IllegalStateException("目标合约未提供委员会配置查询，请先升级合约");
            if(expected.getCommitteeVersion().equals(actual.getString("version"))) {
                router.verifyRelayPolicy(expected);System.out.println("委员会配置已经匹配，未发送交易。");return;
            }
            if(Long.parseLong(expected.getCommitteeVersion())!=Long.parseLong(actual.getString("version"))+1)throw new IllegalArgumentException("新委员会版本须严格递增1");
            System.out.println("目标="+driver.physicalIdentity(ChainRouter.MAIN_REGULATOR)+"；当前版本="+actual.getString("version")+"；拟配置版本="+expected.getCommitteeVersion()+"；门槛="+expected.threshold());
            if(args.length==1){System.out.println("检查完成，未发送交易；增加--apply后才由合约所有者提交配置。");return;}
            ChainStorageAdapter.StoreChainResult receipt=driver.invoke(ChainRouter.MAIN_REGULATOR,"configure_relay_committee",arguments);
            router.verifyRelayPolicy(expected);System.out.println("委员会已配置：交易="+receipt.getTxId()+"；高度="+receipt.getBlockHeight());
        } finally {
            if(driver instanceof FiscoBcosDriver)((FiscoBcosDriver)driver).close();else((ChainMakerSdkInvoker)driver).close();
        }
    }
}
