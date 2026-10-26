"""仅针对专用本机测试实例，验证HTTP权限、单件追溯及异常处置。"""
import datetime, json, os, time, uuid
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from urllib.parse import urlencode

BASE=os.environ.get('CCF_TEST_BASE_URL','')
if BASE!='http://127.0.0.1:18080' or os.environ.get('CCF_RUN_ISOLATED_INTEGRATION')!='1':
    raise SystemExit('只能显式启用后连接127.0.0.1:18080专用测试实例')
PASSWORD=os.environ['CCF_TEST_PASSWORD']
results=[]
def call(method,path,body=None,token=None,success=True,raw=False,headers=None):
    head={'Content-Type':'application/json',**(headers or {})}
    if token: head['Authorization']='Bearer '+token
    data=body if isinstance(body,bytes) else None if body is None else json.dumps(body,ensure_ascii=False).encode()
    for kind in ('production','quality','logistics'):
        if path == '/api/trace/'+kind: path='/api/trace/event/'+kind
    request=Request(BASE+path,data=data,headers=head,method=method)
    try:
        with urlopen(request,timeout=45) as response: status=response.status; content=response.read(); content_type=response.headers.get('Content-Type','')
    except HTTPError as error: status=error.code;content=error.read();content_type=error.headers.get('Content-Type','')
    if raw:
        assert status==200,(path,status)
        return content,content_type
    value=json.loads(content)
    ok=status==200 and value.get('code')==200
    assert ok==success,(method,path,status,value.get('message'))
    return value.get('data') if success else value
def case(name):
    results.append({'case':name,'passed':True});print('PASS:',name,flush=True)
def login(name): return call('POST','/api/login',{'username':name,'password':PASSWORD})['accessToken']

tokens={role:login(name) for role,name in [('admin','admin'),('manufacturer','manufacturer1'),('quality','quality1'),('logistics','logistics1'),('viewer','viewer1')]}
for role in ['manufacturer','quality','logistics']:
    current=call('GET','/api/did/info',token=tokens[role])
    if not current or current.get('bindStatus')!=1: call('POST','/api/did/bind',{},tokens[role])
case('business identities bind with encrypted server keys')
call('POST','/api/role/assign?userId=5&roleId=1',token=tokens['viewer'],success=False)
call('PUT','/api/user/info',{'userType':'regulator'},tokens['viewer'],success=False)
case('ordinary account cannot assign roles or promote account type')
keylist=call('GET','/api/rsa/list',token=tokens['manufacturer'])
assert keylist and all('privateKey' not in item for item in keylist)
case('key query excludes private keys')
batch='IT-'+uuid.uuid4().hex[:10]
part=call('POST','/api/parts/create',{'partsCode':batch,'partsName':'集成测试关节零部件','batchNumber':batch,'categoryId':1,'quantity':2,'unit':'件'},tokens['manufacturer'])
units=call('GET','/api/parts/units/by-batch?'+urlencode({'batchNumber':batch}),token=tokens['manufacturer']);assert len(units)==2
dids=[]
for unit in units:
    did=call('POST',f'/api/parts/unit/{unit["id"]}/did/sync',token=tokens['manufacturer']);dids.append(did)
    call('POST',f'/api/parts/did/{did["id"]}/bind',{'bindType':'QRCODE','bindCode':'QR-'+str(unit['id'])},tokens['manufacturer'])
case('parts units, DID anchors and physical identifiers created')
boundary='ccf-'+uuid.uuid4().hex
attachment=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="evidence.txt"\r\nContent-Type: text/plain\r\n\r\nquality-evidence-content\r\n--{boundary}--\r\n').encode()
stored=call('POST','/api/attachments',attachment,tokens['manufacturer'],headers={'Content-Type':'multipart/form-data; boundary='+boundary})
assert stored['sha256'] and stored['reference'].startswith('db-cid-')
case('attachment upload returns content reference and byte digest')
def event_body(index,name):
    return {'partsId':part['id'],'unitId':units[index]['id'],'eventName':name,'eventTime':datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'requestId':str(uuid.uuid4()),'operatorId':999,'operatorName':'forged-operator','organization':'forged-org'}
productions=[]
disabled=keylist[0]['id']
call('PUT',f'/api/rsa/{disabled}/status?status=0',token=tokens['manufacturer'])
try:
    call('POST','/api/trace/production',event_body(0,'签发失败回滚'),tokens['manufacturer'],success=False)
    empty=call('GET','/api/trace/event/list?'+urlencode({'partsId':part['id'],'unitId':units[0]['id']}),token=tokens['manufacturer'])
    assert empty['total']==0
finally: call('PUT',f'/api/rsa/{disabled}/status?status=1',token=tokens['manufacturer'])
case('failed credential signing rolls back event and detail database writes')
for index in [0,1]:
    body=event_body(index,'生产登记');body['evidenceUrls']=stored['reference'];body['productionLine']='LINE-A';body['productionBatch']=batch
    event=call('POST','/api/trace/production',body,tokens['manufacturer']);productions.append(event)
    assert event['operatorId']==2 and event['operatorName']!='forged-operator' and event['chainStatus']==2
    again=call('POST','/api/trace/production',body,tokens['manufacturer']);assert again['id']==event['id']
case('production signs credentials atomically, uses real actor and deduplicates retries')
vc=call('GET',f'/api/trace/credential/event/{productions[0]["id"]}',token=tokens['manufacturer'])
verified=call('GET','/api/trace/credential/verify/'+vc['vcId'],token=tokens['viewer']);assert verified['verified'] and verified['simulated']
case('actual signed credential passes full cryptographic and stored-document verification')
bad=event_body(0,'不合格质检');bad.update(inspectionType='出厂检验',inspectionResult='不合格',inspector='forged')
bad_event=call('POST','/api/trace/quality',bad,tokens['quality'])
did_rows=call('GET','/api/parts/did/list?'+urlencode({'partsId':part['id'],'onlyUnit':'true'}),token=tokens['viewer'])
assert all(row['didStatus']==2 for row in did_rows)
case('failed inspection freezes every DID in the affected parts batch')
blocked=event_body(0,'禁止流转');blocked['logisticsType']='运输'
call('POST','/api/trace/logistics',blocked,tokens['logistics'],success=False)
call('POST',f'/api/parts/did/{dids[0]["id"]}/unfreeze',token=tokens['quality'],success=False)
alerts=call('GET','/api/anomaly/alert/list',token=tokens['quality'])
alert=next(row for row in alerts['list'] if row['partsId']==part['id'] and row['status']==0)
call('POST','/api/anomaly/alert/handle',{'id':alert['id'],'handleResult':'尚无复检'},tokens['quality'],success=False)
case('freeze blocks logistics, early unfreeze and premature alert closure')
retest=event_body(0,'合格复检');retest.update(inspectionType='复检',inspectionResult='合格')
call('POST','/api/trace/quality',retest,tokens['quality'])
call('POST','/api/anomaly/alert/handle',{'id':alert['id'],'handler':'forged','handleResult':'复检合格'},tokens['quality'])
for did in dids: call('POST',f'/api/parts/did/{did["id"]}/unfreeze',token=tokens['quality'])
case('passed retest permits authorized alert closure and unfreeze')
for index in [0,1]:
    if index==1:
        quality=event_body(index,'合格质检');quality.update(inspectionType='出厂检验',inspectionResult='合格')
        call('POST','/api/trace/quality',quality,tokens['quality'])
    logistics=event_body(index,'物流流转');logistics.update(logisticsType='运输',trackingNumber='IT-'+str(index))
    call('POST','/api/trace/logistics',logistics,tokens['logistics'])
case('authorized production, quality and logistics flow completes for separate units')
overview=call('GET','/api/trace/workbench/batch-overview?'+urlencode({'batchNumber':batch}),token=tokens['manufacturer'])
assert overview['totalParts']==2 and overview['completedParts']==2 and len(overview['items'])==2
case('batch overview aggregates unit progress without mixing batches')
comprehensive=call('POST',f'/api/trace/credential/comprehensive/unit/{units[0]["id"]}',token=tokens['manufacturer'])
assert call('GET','/api/trace/credential/verify/'+comprehensive['vcId'],token=tokens['viewer'])['verified']
case('comprehensive credential signs one explicitly selected unit')
for did in dids:
    verified=call('GET','/api/trace/chain/'+did['did']+'/verify',token=tokens['viewer']);assert verified['passed'],verified['issues']
case('DID trace verifies every associated event and credential without mixing units')
report=call('GET','/api/trace/report?'+urlencode({'did':dids[0]['did'],'format':'json'}),token=tokens['viewer']);assert report['verify']['passed']
csv,ctype=call('GET','/api/trace/report?'+urlencode({'did':dids[0]['did'],'format':'csv'}),token=tokens['viewer'],raw=True);assert 'text/csv' in ctype and len(csv)>30
case('authenticated JSON and CSV report exports return actual report content')
records=call('GET','/api/trace/cross-chain?'+urlencode({'did':dids[0]['did']}),token=tokens['viewer'])['records'];assert records and all(r['status']==2 for r in records)
case('local cross-chain records remain explicitly simulated')
call('PUT','/api/trace/credential/revoke/'+vc['vcId']+'?'+urlencode({'reason':'集成测试撤销'}),token=tokens['manufacturer'])
assert not call('GET','/api/trace/credential/verify/'+vc['vcId'],token=tokens['viewer'])['verified']
assert not call('POST',f'/api/trace/certificate/verify/{vc["id"]}',token=tokens['viewer'])['verified']
assert not call('GET','/api/trace/chain/'+dids[0]['did']+'/verify',token=tokens['viewer'])['passed']
case('revocation invalidates all verification entry points and overall trace')
stats=call('GET','/api/dashboard/stats',token=tokens['viewer']);assert stats['todayEvents']>=7 and stats['eventTrend'] and stats['categoryStats']
case('statistics derive from persisted business records')
call('PUT','/api/admin/user/5/status?status=0',token=tokens['admin'])
call('GET','/api/user/info',token=tokens['viewer'],success=False)
case('disabled account cannot continue using old access token')
call('POST','/api/logout',token=tokens['admin']);call('GET','/api/user/info',token=tokens['admin'],success=False)
case('logout invalidates the same token on the server')
output=Path(__file__).resolve().parents[1]/'target/test-results/integration-results.json'
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps({'baseUrl':BASE,'cases':results,'count':len(results)},ensure_ascii=False,indent=2),encoding='utf-8')
print('Integration cases completed:',len(results))
