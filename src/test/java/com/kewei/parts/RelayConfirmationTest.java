package com.kewei.parts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kewei.parts.infrastructure.confirmation.*;
import com.kewei.parts.infrastructure.chain.*;
import com.kewei.parts.infrastructure.chainmaker.*;
import com.kewei.parts.infrastructure.fisco.FiscoContractDeployer;
import com.kewei.parts.module.parts.entity.CrossChainRecord;
import com.kewei.parts.module.parts.mapper.CrossChainRecordMapper;
import com.kewei.parts.utils.RsaUtil;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RelayConfirmationTest {
    static final List<Map<String,String>> keys=new ArrayList<>();
    static Map<String,String> coordinator;
    final ObjectMapper json=new ObjectMapper();
    RelayConfirmationProperties properties;
    ChainSdkRouter chains;
    ChainNetworkProperties networks;
    RelayConfirmationMapper records;
    RelayVoteClient client;
    RelayConfirmationService service;
    AtomicReference<RelayConfirmation> stored;
    @BeforeAll static void keys() throws Exception {
        coordinator=RsaUtil.generateKeyPair();for(int i=0;i<4;i++)keys.add(RsaUtil.generateKeyPair());
    }
    @BeforeEach void setup() throws Exception {
        properties=policy();chains=mock(ChainSdkRouter.class);networks=new ChainNetworkProperties();networks.setRelayPrivateKey(coordinator.get("privateKey"));
        records=mock(RelayConfirmationMapper.class);client=mock(RelayVoteClient.class);stored=new AtomicReference<>();
        when(records.findRecord(anyLong())).thenAnswer(i->stored.get());
        doAnswer(i->{RelayConfirmation row=i.getArgument(0);row.setId(1L);stored.set(row);return 1;}).when(records).insert(any(RelayConfirmation.class));
        doAnswer(i->{stored.set(i.getArgument(0));return 1;}).when(records).updateById(any(RelayConfirmation.class));
        when(client.request(anyString(),any(),anyInt())).thenAnswer(i->{
            int member=Integer.parseInt(((String)i.getArgument(0)).split(":")[2].split("/")[0])-19000;
            return vote(member,i.getArgument(1));
        });
        service=new RelayConfirmationService(properties,networks,chains,records,client);
    }
    @AfterEach void close() {service.close();}
    static RelayConfirmationProperties policy() {
        RelayConfirmationProperties p=new RelayConfirmationProperties();p.setEnabled(true);p.setStorageMode("real");p.setTimeoutMs(500);p.setCoordinatorPublicKey(coordinator.get("publicKey"));
        for(int i=0;i<4;i++) {
            RelayConfirmationProperties.Member m=new RelayConfirmationProperties.Member();m.setOrganization("org"+i);m.setPublicKey(keys.get(i).get("publicKey"));m.setEndpoint("http://127.0.0.1:"+(19000+i)+"/internal/relay/endorse");p.getMembers().put("node"+i,m);
        }return p;
    }
    static CrossChainAnchorPayload payload() {
        return CrossChainAnchorPayload.builder().sourceChainId("sub-production").sourceTxHash("quorum-source-001").sourceBlockHeight(12L).businessType("trace_event")
                .businessId(51L).partsDid("did:test:quorum").objectCid("quorum-cid").dataHash(String.join("",Collections.nCopies(64,"a"))).build();
    }
    static RelayCertificate.Request request(long now) throws Exception {
        RelayCertificate.Request r=new RelayCertificate.Request();r.setPayload(payload());r.setCommitteeVersion("1");r.setIssuedAt(now);
        r.setCoordinatorSignature(RsaUtil.sign(ConfirmationProof.requestMessage(r),coordinator.get("privateKey")));return r;
    }
    static RelayCertificate.Vote vote(int member,RelayCertificate.Request request) throws Exception {
        RelayCertificate.Vote v=new RelayCertificate.Vote();v.setNodeId("node"+member);v.setSignature(RsaUtil.sign(ConfirmationProof.message(request.getPayload(),request.getCommitteeVersion(),request.getIssuedAt()),keys.get(member).get("privateKey")));return v;
    }
    @Test void disabledConfigurationDoesNotRequireExternalNodes() {assertDoesNotThrow(()->new RelayConfirmationProperties().validate());}
    @Test void policyRequiresIndependentOrganizationsKeysAndEndpoints() throws Exception {
        properties.validate();
        properties.getMembers().get("node1").setOrganization("org0");assertThrows(IllegalArgumentException.class,properties::validate);
        properties=policy();properties.getMembers().get("node1").setPublicKey(keys.get(0).get("publicKey"));assertThrows(IllegalArgumentException.class,properties::validate);
        properties=policy();properties.getMembers().get("node1").setEndpoint(properties.getMembers().get("node0").getEndpoint());assertThrows(IllegalArgumentException.class,properties::validate);
        properties=policy();properties.getMembers().get("node1").setEndpoint("http://remote.example/internal/relay/endorse");assertThrows(IllegalArgumentException.class,properties::validate);
    }
    @Test void validatorKeepsOnlyItsOwnPrivateKeyAndMayOmitPeerEndpoints() throws Exception {
        properties.setEnabled(false);properties.setValidatorEnabled(true);properties.setValidatorOnly(true);properties.setValidatorId("node0");properties.setValidatorPrivateKey(keys.get(0).get("privateKey"));
        properties.getMembers().values().forEach(m->m.setEndpoint(null));properties.validate();
        properties.setValidatorPrivateKey(keys.get(1).get("privateKey"));assertThrows(IllegalArgumentException.class,properties::validate);
    }
    @Test void successfulQuorumIsDurableAndReusedBeforeExpiry() throws Exception {
        service.initialize();RelayCertificate proof=service.confirm(1L,payload());
        assertEquals(3,proof.getSignatures().size());assertEquals("CONFIRMED",stored.get().getState());assertNotNull(stored.get().getProofJson());
        clearInvocations(client);RelayCertificate reused=service.confirm(1L,payload());assertEquals(proof.getSignatures(),reused.getSignatures());verifyNoInteractions(client);
        service.committed(1L);assertEquals("COMMITTED",stored.get().getState());
        assertFalse(json.writeValueAsString(stored.get()).contains("proofJson"));
    }
    @Test void oneUnavailableNodeDoesNotPreventThreeIndependentVotes() throws Exception {
        doThrow(new java.io.IOException("offline")).when(client).request(contains(":19003/"),any(),anyInt());
        service.initialize();assertEquals(3,service.confirm(1L,payload()).getSignatures().size());
    }
    @Test void twoHonestVotesAndOneImpersonationCannotReachQuorum() throws Exception {
        doAnswer(i->vote(0,i.getArgument(1))).when(client).request(contains(":19002/"),any(),anyInt());
        doThrow(new java.io.IOException("offline")).when(client).request(contains(":19003/"),any(),anyInt());
        service.initialize();assertThrows(IllegalStateException.class,()->service.confirm(1L,payload()));
        assertEquals("WAITING",stored.get().getState());assertEquals(2,stored.get().getValidVotes());assertNull(stored.get().getProofJson());
    }
    @Test void confirmedProofSurvivesMainChainFailureAndExpiredProofIsRenewed() throws Exception {
        service.initialize();service.confirm(1L,payload());service.failed(1L,new Exception("target unavailable"));
        assertEquals("CONFIRMED",stored.get().getState());assertNotNull(stored.get().getProofJson());
        RelayCertificate old=json.readValue(stored.get().getProofJson(),RelayCertificate.class);old.setIssuedAt(System.currentTimeMillis()/1000-400);stored.get().setProofJson(json.writeValueAsString(old));
        clearInvocations(client);service.confirm(1L,payload());verify(client,atLeast(3)).request(anyString(),any(),anyInt());
    }
    @Test void cachedProofCannotBeUsedForChangedBusinessContent() throws Exception {
        service.initialize();service.confirm(1L,payload());clearInvocations(client);
        CrossChainAnchorPayload changed=CrossChainAnchorPayload.builder().sourceChainId("sub-production").sourceTxHash("quorum-source-001").sourceBlockHeight(12L).businessType("trace_event")
                .businessId(51L).partsDid("did:test:other").objectCid("quorum-cid").dataHash(payload().getDataHash()).build();
        assertThrows(IllegalStateException.class,()->service.confirm(1L,changed));verifyNoInteractions(client);
    }
    @Test void badSourceOrContractPolicyBlocksRequestsBeforeVoting() throws Exception {
        service.initialize();doThrow(new IllegalStateException("source mismatch")).when(chains).verifyConfirmedSource(any());
        assertThrows(IllegalStateException.class,()->service.confirm(1L,payload()));verifyNoInteractions(client);
        reset(chains);doThrow(new IllegalStateException("policy mismatch")).when(chains).verifyRelayPolicy(any());assertThrows(IllegalStateException.class,service::initialize);
    }
    @Test void validatorAuthenticatesRequestBeforeReadingSourceAndBindsResponseToContent() throws Exception {
        properties.setValidatorId("node0");properties.setValidatorPrivateKey(keys.get(0).get("privateKey"));
        RelayValidatorController controller=new RelayValidatorController(properties,chains);
        RelayCertificate.Request request=request(System.currentTimeMillis()/1000);
        RelayCertificate.Vote vote=controller.endorse(request);assertTrue(ConfirmationProof.verifyVote(vote,"node0",request,properties));verify(chains).verifyConfirmedSource(any());
        clearInvocations(chains);request.setIssuedAt(request.getIssuedAt()+1);assertThrows(ResponseStatusException.class,()->controller.endorse(request));verifyNoInteractions(chains);
    }
    @Test void validationEndpointReturnsRealHttpFailureWithoutJwt() throws Exception {
        properties.setValidatorId("node0");properties.setValidatorPrivateKey(keys.get(0).get("privateKey"));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new RelayValidatorController(properties,chains)).build();
        RelayCertificate.Request request=request(System.currentTimeMillis()/1000);
        mvc.perform(post("/internal/relay/endorse").contentType("application/json").content(json.writeValueAsBytes(request))).andExpect(status().isOk()).andExpect(jsonPath("$.nodeId").value("node0"));
        request.setCoordinatorSignature("bad");mvc.perform(post("/internal/relay/endorse").contentType("application/json").content(json.writeValueAsBytes(request))).andExpect(status().isForbidden());
    }
    @Test void invalidTimesSignaturesAndCommitteeVersionsFailClosed() throws Exception {
        RelayCertificate.Request request=request(System.currentTimeMillis()/1000);
        RelayCertificate c=new RelayCertificate();c.setPayload(payload());c.setIssuedAt(request.getIssuedAt());c.setCommitteeVersion("1");
        for(int i=0;i<3;i++)c.getSignatures().put("node"+i,vote(i,request).getSignature());
        ConfirmationProof.verifyCertificate(c,properties,request.getIssuedAt());
        assertThrows(IllegalArgumentException.class,()->ConfirmationProof.verifyCertificate(c,properties,c.getIssuedAt()+301));
        c.setCommitteeVersion("2");assertThrows(IllegalArgumentException.class,()->ConfirmationProof.verifyCertificate(c,properties,request.getIssuedAt()));
        c.setCommitteeVersion("1");c.getSignatures().put("node1",c.getSignatures().get("node0"));assertThrows(IllegalArgumentException.class,()->ConfirmationProof.verifyCertificate(c,properties,request.getIssuedAt()));
    }
    @Test void failedConfirmationNeverCallsMainChainAndDoesNotInventReceipt() throws Exception {
        CrossChainRelayService relay=new CrossChainRelayService();CrossChainRecordMapper tasks=mock(CrossChainRecordMapper.class);
        RelayConfirmationService confirmation=mock(RelayConfirmationService.class);CrossChainRecord row=new CrossChainRecord();row.setId(1L);row.setAttempts(0);
        row.setPartsDid("did:test:unit");row.setSourceChainId("sub-production");row.setSourceTxHash("source");
        when(tasks.selectList(any())).thenReturn(Collections.singletonList(row));when(tasks.claim(1L)).thenReturn(1);
        when(confirmation.confirm(anyLong(),any())).thenThrow(new IllegalStateException("insufficient votes"));
        ReflectionTestUtils.setField(relay,"mapper",tasks);ReflectionTestUtils.setField(relay,"sdkRouter",chains);ReflectionTestUtils.setField(relay,"mode","real");ReflectionTestUtils.setField(relay,"confirmation",confirmation);
        relay.processPending();assertEquals(-1,row.getStatus());assertNull(row.getTargetTxHash());verify(chains,never()).relay(any());verify(chains,never()).relayConfirmed(any());
    }
    @Test void validatorOnlyDoesNotRunRelayWorkers() {
        CrossChainRelayService relay=new CrossChainRelayService();CrossChainRecordMapper tasks=mock(CrossChainRecordMapper.class);
        ReflectionTestUtils.setField(relay,"mapper",tasks);ReflectionTestUtils.setField(relay,"mode","real");ReflectionTestUtils.setField(relay,"validatorOnly",true);
        relay.processPending();verifyNoInteractions(tasks);
    }
    @Test void deployerProducesCommitteeArgumentsAndRejectsDuplicateKeys() throws Exception {
        FiscoContractDeployer.Manifest manifest=new FiscoContractDeployer.Manifest();manifest.setCommitteeVersion("1");manifest.setCommitteeMembers(properties.getMembers());
        List<Object> args=FiscoContractDeployer.committeeArguments(manifest);assertEquals(5,args.size());assertEquals(4,((List<?>)args.get(1)).size());
        properties.getMembers().get("node1").setPublicKey(keys.get(0).get("publicKey"));assertThrows(IllegalArgumentException.class,()->FiscoContractDeployer.committeeArguments(manifest));
    }
    @Test void certifiedRelayRoutesBothPlatformsAndChecksOnChainCommittee() throws Exception {
        for(ChainPlatform main:ChainPlatform.values()) {
            ChainDriver cm=mock(ChainDriver.class),fisco=mock(ChainDriver.class);when(cm.platform()).thenReturn(ChainPlatform.CHAINMAKER);when(fisco.platform()).thenReturn(ChainPlatform.FISCO_BCOS);
            when(cm.configured(anyString())).thenReturn(true);when(fisco.configured(anyString())).thenReturn(true);
            networks.network("main-regulator").setPlatform(main);ChainSdkRouter router=new ChainSdkRouter(networks,Arrays.asList(cm,fisco));ReflectionTestUtils.setField(router,"confirmation",properties);
            Map<String,Object> policy=new LinkedHashMap<>(),members=new LinkedHashMap<>();
            for(Map.Entry<String,RelayConfirmationProperties.Member> entry:properties.getMembers().entrySet()) {
                Map<String,String> member=new LinkedHashMap<>();member.put("organization",entry.getValue().getOrganization());member.put("publicKey",entry.getValue().getPublicKey());members.put(entry.getKey(),member);
            }
            policy.put("version","1");policy.put("threshold",3);policy.put("members",members);
            ChainDriver target=main==ChainPlatform.CHAINMAKER?cm:fisco;
            when(target.query(eq("main-regulator"),eq("relay_policy"),anyMap())).thenAnswer(i->json.writeValueAsString(policy));
            String source=main==ChainPlatform.CHAINMAKER?"sub-logistics":"sub-production";
            CrossChainAnchorPayload p=CrossChainAnchorPayload.builder().sourceChainId(source).sourceTxHash("source-tx").sourceBlockHeight(1L).businessType("trace_event").businessId(1L).partsDid("did:test:unit").dataHash(payload().getDataHash()).objectCid("cid").build();
            ChainDriver sourceDriver=source.equals("sub-logistics")?fisco:cm;
            when(sourceDriver.query(eq(source),eq("get_anchor"),anyMap())).thenReturn("{\"data_hash\":\""+p.getDataHash()+"\",\"parts_did\":\"did:test:unit\",\"object_cid\":\"cid\"}");
            when(target.invoke(anyString(),anyString(),anyMap())).thenReturn(new com.kewei.parts.infrastructure.storage.ChainStorageAdapter.StoreChainResult("target-tx",55L));
            RelayCertificate.Request request=request(System.currentTimeMillis()/1000);request.setPayload(p);RelayCertificate c=new RelayCertificate();c.setPayload(p);c.setIssuedAt(request.getIssuedAt());c.setCommitteeVersion("1");
            for(int i=0;i<3;i++)c.getSignatures().put("node"+i,vote(i,request).getSignature());
            assertEquals("target-tx",router.relayConfirmed(c).getTxId());verify(target).invoke(eq("main-regulator"),eq("receive_cross_anchor_quorum"),anyMap());
            assertThrows(IllegalStateException.class,()->router.relay(p));policy.put("threshold",2);assertThrows(IllegalStateException.class,()->router.relayConfirmed(c));
        }
    }
    @Test void actualHttpRoundTripToleratesOneFailedSourceReader() throws Exception {
        List<com.sun.net.httpserver.HttpServer> servers=new ArrayList<>();List<ChainSdkRouter> readers=new ArrayList<>();
        try {
            for(int i=0;i<4;i++) {
                RelayConfirmationProperties node=policy();node.setEnabled(false);node.setValidatorEnabled(true);node.setValidatorId("node"+i);node.setValidatorPrivateKey(keys.get(i).get("privateKey"));node.validate();
                ChainSdkRouter reader=mock(ChainSdkRouter.class);readers.add(reader);
                if(i==0)doThrow(new IllegalStateException("source unavailable")).when(reader).verifyConfirmedSource(any());
                RelayValidatorController controller=new RelayValidatorController(node,reader);
                com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
                server.createContext("/internal/relay/endorse",exchange->{
                    try {
                        RelayCertificate.Request body=json.readValue(exchange.getRequestBody(),RelayCertificate.Request.class);
                        byte[] reply=json.writeValueAsBytes(controller.endorse(body));exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,reply.length);exchange.getResponseBody().write(reply);
                    } catch(Exception error) {exchange.sendResponseHeaders(502,-1);} finally {exchange.close();}
                });server.start();servers.add(server);
                properties.getMembers().get("node"+i).setEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/internal/relay/endorse");
            }
            properties.setTimeoutMs(2000);properties.validate();service.close();
            service=new RelayConfirmationService(properties,networks,chains,records,new RelayVoteClient(json));service.initialize();
            RelayCertificate certificate=service.confirm(1L,payload());assertEquals(3,certificate.getSignatures().size());assertFalse(certificate.getSignatures().containsKey("node0"));
            for(int i=1;i<4;i++)verify(readers.get(i)).verifyConfirmedSource(any());
        } finally {for(com.sun.net.httpserver.HttpServer server:servers)server.stop(0);}
    }
    @Test void exportPublicInteropVectorWithoutPrivateKeys() throws Exception {
        RelayCertificate.Request request=request(1700000000L);Map<String,Object> vector=new LinkedHashMap<>(),policy=new LinkedHashMap<>(),members=new TreeMap<>();
        Map<String,String> signatures=new TreeMap<>();
        for(int i=0;i<4;i++) {
            Map<String,String> member=new LinkedHashMap<>();member.put("organization","org"+i);member.put("publicKey",keys.get(i).get("publicKey"));members.put("node"+i,member);
            signatures.put("node"+i,vote(i,request).getSignature());
        }
        policy.put("version","1");policy.put("threshold",3);policy.put("members",members);vector.put("policy",policy);
        vector.put("arguments",ConfirmationProof.values(payload(),"1",1700000000L));vector.put("signatures",signatures);
        byte[] bytes=json.writerWithDefaultPrettyPrinter().writeValueAsBytes(vector);
        assertFalse(new String(bytes,StandardCharsets.UTF_8).contains("privateKey"));Files.write(Paths.get("target/relay-quorum-vector.json"),bytes);
    }
}
