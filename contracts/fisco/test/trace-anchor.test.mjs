import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import { test, before, after, beforeEach, afterEach } from 'node:test';
import ganache from 'ganache';
import { BrowserProvider, ContractFactory, Contract, Interface, hexlify } from 'ethers';

const artifact = JSON.parse(fs.readFileSync(new URL('../artifacts/TraceAnchor.json', import.meta.url)));
const javaVector = JSON.parse(fs.readFileSync(new URL('../../trace_anchor/testdata/relay-vector.json', import.meta.url)));
const names = ['source_chain_id', 'source_tx_hash', 'source_block_height', 'business_type', 'business_id', 'parts_did', 'data_hash', 'object_cid', 'target_chain_id', 'issued_at'];
const fields = values => names.map(name => values[name]);
const message = values => Buffer.from(Object.keys(values).filter(key => key !== 'signature').sort().map(key => `${key}=${values[key]}\n`).join(''));
const keyParts = publicKey => {
  const key = typeof publicKey === 'string' ? crypto.createPublicKey({key: Buffer.from(publicKey, 'base64'), format: 'der', type: 'spki'}) : publicKey;
  const jwk = key.export({format:'jwk'});
  return [hexlify(Buffer.from(jwk.n, 'base64url')), hexlify(Buffer.from(jwk.e, 'base64url'))];
};
const [modulus, exponent] = keyParts(javaVector.publicKey);
const hashA = 'a'.repeat(64), hashB = 'b'.repeat(64), did = 'did:test:unit';
let rpc, provider, owner, writer, outsider, contract, snapshot;
before(async () => {
  rpc = ganache.provider({logging:{quiet:true}, chain:{hardfork:'istanbul', chainId:2026, time:new Date(1700000000000)}, miner:{timestampIncrement:1}, wallet:{totalAccounts:3}});
  provider = new BrowserProvider(rpc);provider.pollingInterval=10;
  owner=await provider.getSigner(0);writer=await provider.getSigner(1);outsider=await provider.getSigner(2);
  contract=await new ContractFactory(artifact.abi, artifact.evm.bytecode.object, owner).deploy('main-regulator',[await writer.getAddress()],['sub-production'],[modulus],[exponent]);
  await contract.waitForDeployment();
});
after(async () => { await rpc.disconnect(); });
beforeEach(async () => { snapshot=await provider.send('evm_snapshot',[]); });
afterEach(async () => { await provider.send('evm_revert',[snapshot]); });
const send = async promise => {const tx=await promise;return tx.wait();};
const rejected = async promise => assert.rejects(async () => {const tx=await promise;await tx.wait();});

test('合约报告平台无关的版本及逻辑链编码',async()=> {
  const result=await contract.contract_info();assert.equal(result[0],'parts-trace-anchor/v2');assert.equal(result[1],'main-regulator');
});
test('未授权账户不能写存证',async()=> {await rejected(contract.connect(outsider).save_anchor('vc','1',hashA,'cid',did));});
test('授权账户保存并读取存证',async()=> {
  await send(contract.connect(writer).save_anchor('vc','1',hashA,'cid',did));assert.deepEqual(Array.from(await contract.get_anchor('vc','1')),[hashA,'cid',did]);
});
test('同一存证相同内容可重试，不同内容不可覆盖',async()=> {
  await send(contract.save_anchor('vc','1',hashA,'cid',did));await send(contract.save_anchor('vc','1',hashA,'cid',did));await rejected(contract.save_anchor('vc','1',hashB,'cid',did));
});
test('摘要之外的CID和主体同样不能被覆盖',async()=> {
  await send(contract.save_anchor('vc','1',hashA,'cid',did));await rejected(contract.save_anchor('vc','1',hashA,'changed',did));await rejected(contract.save_anchor('vc','1',hashA,'cid','did:test:other'));
});
test('无效摘要与业务编号被拒绝',async()=> {
  await rejected(contract.save_anchor('vc','not-a-number',hashA,'cid',did));await rejected(contract.save_anchor('vc','1','not-a-hash','cid',did));
});
test('凭证登记、读取与相同内容重试',async()=> {
  await send(contract.connect(writer).append_vc(did,'VC-1',hashA));await send(contract.connect(writer).append_vc(did,'VC-1',hashA));assert.equal(await contract.get_vc(did,'VC-1'),hashA);
});
test('凭证内容不能被覆盖',async()=> {await send(contract.append_vc(did,'VC-1',hashA));await rejected(contract.append_vc(did,'VC-1',hashB));});
test('其他写入者不能撤销原签发者的凭证',async()=> {
  await send(contract.set_writer(await outsider.getAddress(),true));await send(contract.connect(writer).append_vc(did,'VC-1',hashA));await rejected(contract.connect(outsider).revoke_vc(did,'VC-1'));
});
test('撤销后迟到的追加不能恢复凭证',async()=> {
  await send(contract.connect(writer).append_vc(did,'VC-1',hashA));await send(contract.connect(writer).revoke_vc(did,'VC-1'));assert.equal(await contract.get_vc(did,'VC-1'),'0');await rejected(contract.append_vc(did,'VC-1',hashA));
});
test('先到达的撤销保留墓碑',async()=> {
  await send(contract.connect(writer).revoke_vc(did,'VC-1'));await rejected(contract.append_vc(did,'VC-1',hashA));assert.equal(await contract.get_vc(did,'VC-1'),'0');
});
test('只有合约所有者可修改写入授权和受信公钥',async()=> {
  await rejected(contract.connect(writer).set_writer(await outsider.getAddress(),true));await rejected(contract.connect(writer).set_relay_key('sub-production',modulus,exponent));
});
test('停用写入账户后立即拒绝写入',async()=> {
  await send(contract.set_writer(await writer.getAddress(),false));await rejected(contract.connect(writer).save_anchor('vc','1',hashA,'cid',did));
});
test('Java生成的RSA跨链签名在Solidity中通过验证',async()=> {
  await send(contract.connect(writer).receive_cross_anchor(fields(javaVector.arguments),hexlify(Buffer.from(javaVector.arguments.signature,'base64'))));
  assert.deepEqual(Array.from(await contract.get_cross_anchor('sub-production','source-tx-001')),[hashA,'QmTestContentIdentifier',javaVector.arguments.parts_did]);
});
test('完全相同的有效跨链消息可以幂等重试',async()=> {
  const signature=hexlify(Buffer.from(javaVector.arguments.signature,'base64'));
  await send(contract.receive_cross_anchor(fields(javaVector.arguments),signature));await send(contract.receive_cross_anchor(fields(javaVector.arguments),signature));
});
test('篡改摘要、主体或CID后原签名无效',async()=> {
  for(const [name,value] of [['data_hash',hashB],['parts_did','did:test:other'],['object_cid','replacement']]) {
    await rejected(contract.receive_cross_anchor(fields({...javaVector.arguments,[name]:value}),hexlify(Buffer.from(javaVector.arguments.signature,'base64'))));
  }
});
test('错误目标链和未授权源链被拒绝',async()=> {
  for(const [name,value] of [['target_chain_id','another-target'],['source_chain_id','untrusted-source']])
    await rejected(contract.receive_cross_anchor(fields({...javaVector.arguments,[name]:value}),hexlify(Buffer.from(javaVector.arguments.signature,'base64'))));
});
test('过期和过早的消息被拒绝',async()=> {
  for(const time of ['1699990000','1700100000']) await rejected(contract.receive_cross_anchor(fields({...javaVector.arguments,issued_at:time}),hexlify(Buffer.from(javaVector.arguments.signature,'base64'))));
});
test('带回车换行的字段和越界RSA签名被拒绝',async()=> {
  await rejected(contract.receive_cross_anchor(fields({...javaVector.arguments,object_cid:'bad\nfield'}),hexlify(Buffer.from(javaVector.arguments.signature,'base64'))));
  await rejected(contract.receive_cross_anchor(fields(javaVector.arguments),modulus));
});
test('短签名和被修改的签名字节被拒绝',async()=> {
  const signature=Buffer.from(javaVector.arguments.signature,'base64');signature[10]^=1;
  await rejected(contract.receive_cross_anchor(fields(javaVector.arguments),hexlify(signature)));
  await rejected(contract.receive_cross_anchor(fields(javaVector.arguments),'0x01'));
});
test('新的有效签名仍不能覆盖同一源交易的已有内容',async()=> {
  const pair=crypto.generateKeyPairSync('rsa',{modulusLength:2048});const [n,e]=keyParts(pair.publicKey);await send(contract.set_relay_key('sub-production',n,e));
  const values={...javaVector.arguments};delete values.signature;
  const sign=()=>hexlify(crypto.sign('RSA-SHA256',message(values),pair.privateKey));
  await send(contract.receive_cross_anchor(fields(values),sign()));values.data_hash=hashB;await rejected(contract.receive_cross_anchor(fields(values),sign()));
});
test('更新消息时间的相同业务内容可安全重试',async()=> {
  const pair=crypto.generateKeyPairSync('rsa',{modulusLength:2048});const [n,e]=keyParts(pair.publicKey);await send(contract.set_relay_key('sub-production',n,e));
  const values={...javaVector.arguments};delete values.signature;
  const sign=()=>hexlify(crypto.sign('RSA-SHA256',message(values),pair.privateKey));
  await send(contract.receive_cross_anchor(fields(values),sign()));values.issued_at='1700000001';await send(contract.receive_cross_anchor(fields(values),sign()));
});
test('错误PKCS1填充、算法标识及摘要均被拒绝',async()=> {
  const pair=crypto.generateKeyPairSync('rsa',{modulusLength:2048});const [n,e]=keyParts(pair.publicKey);await send(contract.set_relay_key('sub-production',n,e));
  const values={...javaVector.arguments};delete values.signature;
  const encoded=Buffer.alloc(256,0xff);encoded[0]=0;encoded[1]=1;encoded[204]=0;
  Buffer.from('3031300d060960864801650304020105000420','hex').copy(encoded,205);crypto.createHash('sha256').update(message(values)).digest().copy(encoded,224);
  for(const index of [1,20,204,210,250]) {
    const bad=Buffer.from(encoded);bad[index]^=1;
    const signature=crypto.privateEncrypt({key:pair.privateKey,padding:crypto.constants.RSA_NO_PADDING},bad);
    await rejected(contract.receive_cross_anchor(fields(values),hexlify(signature)));
  }
});
test('小于2048位的受信RSA公钥不能登记',async()=> {await rejected(contract.set_relay_key('sub-production','0x'+'ff'.repeat(128),'0x010001'));});

const sdkVectorFile=new URL('../../../target/fisco-codec-vectors.json',import.meta.url);
const quorumVector=JSON.parse(fs.readFileSync(new URL('../../trace_anchor/testdata/quorum-vector.json',import.meta.url)));
const quorumIds=Object.keys(quorumVector.policy.members).sort();
const committeeArguments=[quorumVector.policy.version,quorumIds,quorumIds.map(id=>quorumVector.policy.members[id].organization),
  quorumIds.map(id=>keyParts(quorumVector.policy.members[id].publicKey)[0]),quorumIds.map(id=>keyParts(quorumVector.policy.members[id].publicKey)[1])];
const configureCommittee=()=>send(contract.configure_relay_committee(...committeeArguments));
const quorumSignatures=ids=>ids.map(id=>hexlify(Buffer.from(quorumVector.signatures[id]||'', 'base64')));

test('Java生成的三机构联合签名可以通过Solidity验证并幂等重试',async()=>{
  await configureCommittee();const ids=quorumIds.slice(0,3);
  for(let i=0;i<2;i++)await send(contract.receive_cross_anchor_quorum(fields(quorumVector.arguments),'1',ids,quorumSignatures(ids)));
  assert.deepEqual(Array.from(await contract.get_cross_anchor('sub-production','quorum-source-001')),[hashA,'quorum-cid','did:test:quorum']);
});
test('签名不足、重复节点和未知节点均不能通过联合核验',async()=>{
  await configureCommittee();
  for(const ids of [quorumIds.slice(0,2),['node0','node0','node1'],['node0','node1','unknown']])
    await rejected(contract.receive_cross_anchor_quorum(fields(quorumVector.arguments),'1',ids,quorumSignatures(ids)));
  assert.equal((await contract.get_cross_anchor('sub-production','quorum-source-001'))[0],'');
});
test('开启委员会后旧单中继入口不能绕过门槛',async()=>{
  await configureCommittee();await rejected(contract.receive_cross_anchor(fields(javaVector.arguments),hexlify(Buffer.from(javaVector.arguments.signature,'base64'))));
});
test('联合确认拒绝内容篡改、错误版本和过期消息',async()=>{
  await configureCommittee();const ids=quorumIds.slice(0,3);
  await rejected(contract.receive_cross_anchor_quorum(fields({...quorumVector.arguments,object_cid:'changed'}),'1',ids,quorumSignatures(ids)));
  await rejected(contract.receive_cross_anchor_quorum(fields(quorumVector.arguments),'2',ids,quorumSignatures(ids)));
  await rejected(contract.receive_cross_anchor_quorum(fields({...quorumVector.arguments,issued_at:'1699990000'}),'1',ids,quorumSignatures(ids)));
});
test('委员会配置只允许所有者且不能重复版本或使用重复机构密钥',async()=>{
  await rejected(contract.connect(writer).configure_relay_committee(...committeeArguments));await configureCommittee();
  await rejected(contract.configure_relay_committee(...committeeArguments));
  const invalid=structuredClone(committeeArguments);invalid[0]='2';invalid[2][1]=invalid[2][0];await rejected(contract.configure_relay_committee(...invalid));
  const repeated=structuredClone(committeeArguments);repeated[0]='2';repeated[3][1]=repeated[3][0];await rejected(contract.configure_relay_committee(...repeated));
});
test('目标合约可以返回委员会版本、门槛、机构和公钥',async()=>{
  await configureCommittee();const actual=await contract.relay_policy();assert.equal(actual[0],'1');assert.equal(actual[1],3n);
  assert.deepEqual(Array.from(actual[2]),quorumIds);assert.deepEqual(Array.from(actual[3]),committeeArguments[2]);
  assert.deepEqual(Array.from(actual[4]),committeeArguments[3]);assert.deepEqual(Array.from(actual[5]),committeeArguments[4]);
});
const quorumSdkFile=new URL('../../../target/fisco-quorum-codec-vectors.json',import.meta.url);
test('FISCO原生SDK编码的委员会配置和联合签名调用可在EVM执行',{skip:!fs.existsSync(quorumSdkFile)},async()=>{
  const vectors=JSON.parse(fs.readFileSync(quorumSdkFile));
  for(const item of vectors.writes)await send(owner.sendTransaction({to:await contract.getAddress(),data:item.data}));
  assert.equal((await contract.get_cross_anchor('sub-production','quorum-source-001'))[0],hashA);
});
test('FISCO原生Java SDK生成的ABI调用可被合约执行',{skip:!fs.existsSync(sdkVectorFile)},async()=> {
  const vectors=JSON.parse(fs.readFileSync(sdkVectorFile));const from=await owner.getAddress();
  const deploymentHash=await provider.send('eth_sendTransaction',[{from,data:vectors.deploymentData,gas:'0x7a1200'}]);
  const deploymentReceipt=await provider.getTransactionReceipt(deploymentHash);assert.equal(deploymentReceipt.status,1);
  const address=deploymentReceipt.contractAddress;assert.ok(address);
  for(const item of vectors.writes) {
    const hash=await provider.send('eth_sendTransaction',[{from,to:address,data:item.data,gas:'0x7a1200'}]);const receipt=await provider.getTransactionReceipt(hash);assert.equal(receipt.status,1);
  }
  for(const item of vectors.reads) {
    const output=await provider.send('eth_call',[{from,to:address,data:item.data},'latest']);
    const decoded=new Interface(artifact.abi).decodeFunctionResult(item.method,output);
    assert.deepEqual(Array.from(decoded),item.expected);
  }
});
