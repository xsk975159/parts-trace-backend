package com.kewei.parts;

import com.alibaba.fastjson.*;
import com.kewei.parts.infrastructure.storage.*;
import com.kewei.parts.module.identity.entity.*;
import com.kewei.parts.module.identity.mapper.*;
import com.kewei.parts.module.parts.entity.*;
import com.kewei.parts.module.parts.mapper.*;
import com.kewei.parts.module.system.entity.SysUser;
import com.kewei.parts.module.system.mapper.SysUserMapper;
import com.kewei.parts.module.system.service.RoleService;
import com.kewei.parts.module.trace.entity.*;
import com.kewei.parts.module.trace.mapper.*;
import com.kewei.parts.module.trace.service.*;
import com.kewei.parts.module.trace.service.impl.*;
import com.kewei.parts.security.*;
import com.kewei.parts.utils.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CredentialSecurityTest {
    VerifiableCredentialServiceImpl service;
    VerifiableCredentialMapper vcs;
    ChainStorageAdapter chains;
    VerifiableCredential vc;
    TraceEvent event;
    Map<String,Object> eventData;
    static Map<String,String> pair;
    @BeforeAll static void keys() throws Exception { pair=RsaUtil.generateKeyPair(); }

    @BeforeEach void setup() {
        service=new VerifiableCredentialServiceImpl();vcs=mock(VerifiableCredentialMapper.class);
        TraceEventMapper events=mock(TraceEventMapper.class);PartsDidMapper subjects=mock(PartsDidMapper.class);
        SysUserDidMapper issuers=mock(SysUserDidMapper.class);SysUserRsaKeyMapper keys=mock(SysUserRsaKeyMapper.class);
        SysUserMapper users=mock(SysUserMapper.class);CurrentActor actor=mock(CurrentActor.class);RoleService roles=mock(RoleService.class);
        ObjectStorageAdapter objects=mock(ObjectStorageAdapter.class);chains=mock(ChainStorageAdapter.class);EventSnapshot snapshots=mock(EventSnapshot.class);
        KeyVault vault=new KeyVault();ReflectionTestUtils.setField(vault,"masterKey",Base64.getEncoder().encodeToString(new byte[32]));
        SysUser user=new SysUser();user.setId(7L);user.setUsername("issuer");user.setStatus(1);user.setUserType("manufacturer");
        when(actor.user()).thenReturn(user);when(actor.id()).thenReturn(7L);when(users.selectById(7L)).thenReturn(user);when(roles.hasRole(7L,"manufacturer")).thenReturn(true);
        SysUserDid issuer=new SysUserDid();issuer.setUserId(7L);issuer.setDid("did:test:issuer");issuer.setBindStatus(1);issuer.setPublicKey(pair.get("publicKey"));when(issuers.selectOne(any())).thenReturn(issuer);
        SysUserRsaKey key=new SysUserRsaKey();key.setPublicKey(pair.get("publicKey"));key.setPrivateKey(vault.encrypt(pair.get("privateKey")));when(keys.selectOne(any())).thenReturn(key);
        PartsDid subject=new PartsDid();subject.setPartsId(1L);subject.setUnitId(10L);subject.setDid("did:test:unit10");subject.setDidStatus(1);when(subjects.selectOne(any())).thenReturn(subject);
        event=new TraceEvent();event.setId(3L);event.setPartsId(1L);event.setUnitId(10L);event.setPartsDid(subject.getDid());event.setEventType("production");event.setOperatorId(7L);event.setStatus(1);event.setEventHash("event-hash");
        when(events.lockById(3L)).thenReturn(event);when(events.selectById(3L)).thenReturn(event);
        eventData=new TreeMap<>();eventData.put("operatorId",7L);eventData.put("result","original");when(snapshots.data(event)).thenReturn(eventData);when(snapshots.canonical(event)).thenAnswer(i->CanonicalJson.write(eventData));
        AtomicReference<String> raw=new AtomicReference<>(), hash=new AtomicReference<>();
        when(objects.store(eq("vc"),anyLong(),anyString())).thenAnswer(i->{raw.set(i.getArgument(2));return new ObjectStorageAdapter.StoreObjectResult("db-cid-test",new DidUtil().sha256Hex(raw.get()));});
        when(objects.read("db-cid-test")).thenAnswer(i->raw.get());
        when(chains.storeAnchor(any())).thenAnswer(i->{hash.set(((ChainAnchorRequest)i.getArgument(0)).getDataHash());return new ChainStorageAdapter.StoreChainResult("confirmed-tx",1L);});
        when(chains.getAnchorHash(anyString(),eq("vc"),eq(5L))).thenAnswer(i->hash.get());when(chains.getVcHash(anyString(),anyString())).thenAnswer(i->hash.get());
        doAnswer(i->{((VerifiableCredential)i.getArgument(0)).setId(5L);return 1;}).when(vcs).insert(any(VerifiableCredential.class));
        Object[][] dependencies={{"credentialMapper",vcs},{"traceEventMapper",events},{"partsDidMapper",subjects},{"userDidMapper",issuers},{"keyMapper",keys},{"userMapper",users},{"roleService",roles},{"vcVerifyRecordMapper",mock(VcVerifyRecordMapper.class)},
            {"objectStorageAdapter",objects},{"chainStorageAdapter",chains},{"chainRouter",new ChainRouter()},{"actor",actor},{"keyVault",vault},{"snapshots",snapshots},{"didUtil",new DidUtil()}};
        for(Object[] entry:dependencies) ReflectionTestUtils.setField(service,(String)entry[0],entry[1]);
        com.kewei.parts.module.parts.service.DidDocumentVerifier documentVerifier=mock(com.kewei.parts.module.parts.service.DidDocumentVerifier.class);
        when(documentVerifier.verify(any())).thenReturn(true);ReflectionTestUtils.setField(service,"documentVerifier",documentVerifier);
        vc=service.issueProductionCertificate(3L);when(vcs.selectOne(any())).thenReturn(vc);
    }
    private Map<?,?> check() {return (Map<?,?>)service.verifyCredentialStandard(vc.getVcId());}
    private void rejected() {assertEquals(false,check().get("verified"));}
    @Test void realSignatureAndIndependentStoredDocumentPass() {assertEquals(true,check().get("verified"));assertFalse(vc.getProof().startsWith("proof-"));}
    @Test void changedCredentialContentFails() {JSONObject data=JSON.parseObject(vc.getCredentialData());data.getJSONObject("event").put("result","forged");vc.setCredentialData(data.toJSONString());rejected();}
    @Test void forgedLegacyProofFails() {vc.setProof("proof-forged");rejected();}
    @Test void subjectReplacementFails() {vc.setSubjectDid("did:test:another-unit");rejected();}
    @Test void issuerReplacementFails() {vc.setIssuerDid("did:test:another-issuer");rejected();}
    @Test void expiredCredentialFails() {vc.setExpirationDate(new Date(0));rejected();}
    @Test void revokedCredentialFails() {vc.setStatus(0);rejected();}
    @Test void revokedOnChainFailsEvenIfDatabaseStatusActive() {when(chains.getVcHash(anyString(),anyString())).thenReturn("0");rejected();}
    @Test void missingAnchorFails() {when(chains.getAnchorHash(anyString(),anyString(),anyLong())).thenReturn(null);rejected();}
    @Test void unavailableChainFailsClosed() {when(chains.getAnchorHash(anyString(),anyString(),anyLong())).thenThrow(new IllegalStateException("offline"));rejected();}
    @Test void modifiedBusinessEventFailsOwnershipCheck() {eventData.put("result","modified-after-issue");assertEquals(false,check().get("ownershipCheck"));}
    @Test void issuanceIsIdempotentForExistingEvent() {assertSame(vc,service.issueProductionCertificate(3L));verify(vcs,times(1)).insert(any(VerifiableCredential.class));}
    @Test void missingCredentialNeverShowsSuccessfulChecks() {when(vcs.selectOne(any())).thenReturn(null);Map<?,?> result=check();for(String key:Arrays.asList("verified","hashCheck","ownershipCheck","signatureCheck")) assertEquals(false,result.get(key));}
    @Test void legacyVerificationEntryUsesSameVerifier() {TraceEventServiceImpl legacy=new TraceEventServiceImpl();ReflectionTestUtils.setField(legacy,"verifiableCredentialMapper",vcs);ReflectionTestUtils.setField(legacy,"credentials",service);when(vcs.selectById(5L)).thenReturn(vc);vc.setExpirationDate(new Date(0));assertEquals(false,((Map<?,?>)legacy.verifyCertificate(5L)).get("verified"));}
}
