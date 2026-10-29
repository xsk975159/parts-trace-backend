"""Local competition chain network. Never replaces identities in an existing ledger."""
import base64
import copy
import datetime as dt
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import uuid

import yaml
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa
from cryptography.x509.oid import NameOID, ObjectIdentifier

STATE = Path('/state')
TEMPLATES = Path('/opt/templates')
ORG = 'wx-org1.chainmaker.org'
CODES = {'chain1': 'main-regulator', 'chain2': 'sub-production', 'chain3': 'sub-quality'}


def write(path, value, private=False):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    data = value if isinstance(value, bytes) else value.encode('utf-8')
    temporary = path.with_name(path.name + '.tmp')
    with open(temporary, 'wb') as stream:
        stream.write(data)
    temporary.chmod(0o600 if private else 0o644)
    temporary.replace(path)


def dump(path, value, private=False):
    write(path, json.dumps(value, ensure_ascii=False, indent=2) + '\n', private)


def pem_key(key):
    return key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                             serialization.NoEncryption())


def peer_id(certificate):
    der = certificate.public_key().public_bytes(serialization.Encoding.DER,
                                               serialization.PublicFormat.SubjectPublicKeyInfo)
    if len(der) != 91:
        raise ValueError('This local profile requires an ECDSA P-256 key')
    data = b'\x12\x20' + hashlib.sha256(b'\x08\x03\x12' + bytes([len(der)]) + der).digest()
    alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'
    number, text = int.from_bytes(data, 'big'), ''
    while number:
        number, index = divmod(number, 58)
        text = alphabet[index] + text
    return text


def certificate(org, name, role, key, issuer=None, issuer_key=None, ca=False, node_uid=None):
    subject = x509.Name([x509.NameAttribute(NameOID.ORGANIZATION_NAME, org),
                         x509.NameAttribute(NameOID.ORGANIZATIONAL_UNIT_NAME, role),
                         x509.NameAttribute(NameOID.COMMON_NAME, name)])
    now = dt.datetime.now(dt.timezone.utc)
    ski = hashlib.sha256(key.public_key().public_bytes(serialization.Encoding.DER,
                         serialization.PublicFormat.SubjectPublicKeyInfo)).digest()
    aki = issuer.extensions.get_extension_for_class(x509.SubjectKeyIdentifier).value.digest if issuer else ski
    builder = (x509.CertificateBuilder().subject_name(subject)
               .issuer_name(issuer.subject if issuer else subject).public_key(key.public_key())
               .serial_number(x509.random_serial_number()).not_valid_before(now - dt.timedelta(minutes=10))
               .not_valid_after(now + dt.timedelta(days=3650))
               .add_extension(x509.BasicConstraints(ca=ca, path_length=None), critical=True)
               .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False,
                    key_encipherment=True, data_encipherment=False, key_agreement=False,
                    key_cert_sign=ca, crl_sign=ca, encipher_only=None, decipher_only=None), critical=True)
               .add_extension(x509.SubjectKeyIdentifier(ski), critical=False)
               .add_extension(x509.AuthorityKeyIdentifier(aki, None, None), critical=False))
    if not ca:
        builder = (builder.add_extension(x509.ExtendedKeyUsage([ObjectIdentifier('2.5.29.37.0')]), False)
                   .add_extension(x509.SubjectAlternativeName([x509.DNSName('chainmaker.org'),
                        x509.DNSName('localhost'), x509.DNSName(name),
                        x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]), False)
                   .add_extension(x509.UnrecognizedExtension(ObjectIdentifier('2.87.88.2020.11.30.2020.11.4'),
                        (node_uid or uuid.uuid4().hex).encode('ascii')), False))
    return builder.sign(issuer_key or key, hashes.SHA256())


def identity(directory, org, basename, role, ca, ca_key, node_uid=None):
    key = ec.generate_private_key(ec.SECP256R1())
    cert = certificate(org, basename + '.' + org, role, key, ca, ca_key, node_uid=node_uid)
    write(directory / (basename + '.key'), pem_key(key), True)
    write(directory / (basename + '.crt'), cert.public_bytes(serialization.Encoding.PEM))
    return cert


def sdk_config(chain_id, admin=False):
    prefix = STATE / 'cm/certs' / ORG
    user = prefix / 'user'
    signing = 'admin1.sign' if admin else 'client1.sign'
    return {'chain_client': {'chain_id': chain_id, 'org_id': ORG,
            'auth_type': 'permissionedWithCert',
            'user_key_file_path': str(user / 'client1.tls.key'),
            'user_crt_file_path': str(user / 'client1.tls.crt'),
            'user_sign_key_file_path': str(user / (signing + '.key')),
            'user_sign_crt_file_path': str(user / (signing + '.crt')),
            'nodes': [{'node_addr': 'cm-node1:12301', 'conn_cnt': 2, 'enable_tls': True,
                       'tls_host_name': 'chainmaker.org',
                       'trust_root_paths': [str(STATE / 'cm/certs' / ('wx-org%d.chainmaker.org' % i) / 'ca.crt')
                                            for i in range(1, 5)]}],
            'archive': {'type': '', 'dest': ''}, 'rpc_client': {'max_receive_message_size': 200}}}


def prepare_chainmaker():
    peers = []
    for index in range(1, 5):
        org = 'wx-org%d.chainmaker.org' % index
        directory = STATE / 'cm/certs' / org
        ca_key = ec.generate_private_key(ec.SECP256R1())
        ca = certificate(org, 'ca.' + org, 'root-cert', ca_key, ca=True)
        write(directory / 'ca.key', pem_key(ca_key), True)
        write(directory / 'ca.crt', ca.public_bytes(serialization.Encoding.PEM))
        node_uid = uuid.uuid4().hex
        identity(directory / 'node', org, 'consensus1.sign', 'consensus', ca, ca_key, node_uid)
        tls = identity(directory / 'node', org, 'consensus1.tls', 'consensus', ca, ca_key, node_uid)
        peers.append(peer_id(tls))
        if index == 1:
            identity(directory / 'user', org, 'admin1.sign', 'admin', ca, ca_key)
            identity(directory / 'user', org, 'client1.sign', 'client', ca, ca_key)
            identity(directory / 'user', org, 'client1.tls', 'client', ca, ca_key)

    template = yaml.safe_load((TEMPLATES / 'chainmaker.yml').read_text())
    genesis_template = yaml.safe_load((TEMPLATES / 'genesis.yml').read_text())
    for chain_id in CODES:
        genesis = copy.deepcopy(genesis_template)
        genesis['chain_id'] = chain_id
        genesis['block']['block_interval'] = 1000
        genesis['consensus']['nodes'] = [{'org_id': 'wx-org%d.chainmaker.org' % i, 'node_id': [peers[i-1]]}
                                         for i in range(1, 5)]
        genesis['trust_roots'] = [{'org_id': 'wx-org%d.chainmaker.org' % i,
                                 'root': [str(STATE / 'cm/certs' / ('wx-org%d.chainmaker.org' % i) / 'ca.crt')]}
                                 for i in range(1, 5)]
        write(STATE / 'cm/genesis' / (chain_id + '.yml'), yaml.safe_dump(genesis, sort_keys=False))

    log = yaml.safe_load((TEMPLATES / 'log.yml').read_text())
    def patch_log(value):
        if isinstance(value, dict):
            for key, child in value.items():
                if key == 'file_path':
                    value[key] = '/log/' + Path(child).name
                elif key == 'log_in_console':
                    value[key] = True
                else:
                    patch_log(child)
        elif isinstance(value, list):
            for child in value:
                patch_log(child)
    patch_log(log)

    for index in range(1, 5):
        org = 'wx-org%d.chainmaker.org' % index
        prefix = STATE / 'cm/certs' / org / 'node'
        config = copy.deepcopy(template)
        config['log']['config_file'] = str(STATE / 'cm' / ('node%d' % index) / 'log.yml')
        config['blockchain'] = [{'chainId': chain_id, 'genesis': str(STATE / 'cm/genesis' / (chain_id + '.yml'))}
                                for chain_id in CODES]
        config['node'].update(org_id=org, priv_key_file=str(prefix / 'consensus1.sign.key'),
                              cert_file=str(prefix / 'consensus1.sign.crt'))
        config['net']['seeds'] = ['/dns/cm-node%d/tcp/11301/p2p/%s' % (i, peers[i-1]) for i in range(1, 5)]
        for tls in (config['net']['tls'], config['rpc']['tls']):
            tls.update(priv_key_file=str(prefix / 'consensus1.tls.key'), cert_file=str(prefix / 'consensus1.tls.crt'))
        storage = config['storage']
        storage['store_path'] = '/data/ledger'
        for name in ('blockdb', 'statedb', 'historydb', 'resultdb'):
            leveldb = storage[name + '_config']['leveldb_config']
            leveldb['store_path'] = '/data/' + name
            if 'write_buffer_size' in leveldb:
                leveldb['write_buffer_size'] = 16
                leveldb['block_write_buffer_size'] = 16
        config['vm']['go'].update(enable=True, data_mount_path='/vmdata', log_mount_path='/log/vm',
                                  max_concurrency=4)
        config['vm']['go']['contract_engine']['host'] = 'cm-vm%d' % index
        config['vm']['common'] = {'contract_engine': {'cgroup': {'disable': True}}}
        directory = STATE / 'cm' / ('node%d' % index)
        write(directory / 'chainmaker.yml', yaml.safe_dump(config, sort_keys=False))
        write(directory / 'log.yml', yaml.safe_dump(log, sort_keys=False))
    for chain_id in CODES:
        for admin in (False, True):
            write(STATE / 'sdk/cm' / (chain_id + ('-admin' if admin else '') + '.yml'),
                  yaml.safe_dump(sdk_config(chain_id, admin), sort_keys=False))


def java(action):
    subprocess.run(['java', '-Dlogback.configurationFile=/opt/tools/bootstrap-logback.xml',
                    '-Dloader.main=com.kewei.parts.infrastructure.desktop.HeterogeneousBootstrap',
                    '-cp', '/opt/app/parts-trace-backend.jar', 'org.springframework.boot.loader.PropertiesLauncher',
                    action, str(STATE)], check=True)


def prepare_fisco():
    directory = STATE / 'fisco'
    directory.mkdir(parents=True)
    sdk = STATE / 'sdk/fisco'
    sdk.mkdir(parents=True, exist_ok=True)
    account = ec.generate_private_key(ec.SECP256K1())
    write(sdk / 'account.pem', pem_key(account), True)
    write(sdk / 'account.hex', '%064x' % account.private_numbers().private_value, True)
    java('account')
    account_address = json.loads((STATE / 'sdk/fisco-account.json').read_text())['address']
    subprocess.run(['bash', '/opt/tools/fisco-build-chain.sh', '-l', '127.0.0.1:4', '-p', '30300,20200',
                    '-o', str(directory / 'nodes'), '-D', '-v', '3.8.0', '-a', account_address], cwd=str(directory), check=True)
    peers = ['172.29.67.%d:30300' % i for i in range(31, 35)]
    for index in range(4):
        node = directory / 'nodes/127.0.0.1' / ('node%d' % index)
        import configparser
        config = configparser.ConfigParser(strict=False)
        config.read(node / 'config.ini')
        config['p2p']['listen_port'] = '30300'
        config['rpc']['listen_port'] = '20200'
        with open(node / 'config.ini', 'w') as stream:
            config.write(stream)
        dump(node / 'nodes.json', {'nodes': peers})
    for name in ('ca.crt', 'sdk.crt', 'sdk.key'):
        write(sdk / name, (directory / 'nodes/127.0.0.1/sdk' / name).read_bytes(), name.endswith('.key'))
    write(STATE / 'sdk/fisco.toml', '''[cryptoMaterial]
certPath = "/state/sdk/fisco"
useSMCrypto = "false"
enableSsl = "true"
[network]
messageTimeout = "60000"
defaultGroup = "group0"
peers = ["fisco-node1:20200"]
[account]
keyStoreDir = "/state/sdk/fisco/accounts"
accountFilePath = "/state/sdk/fisco/account.pem"
accountFileFormat = "pem"
[threadPool]
threadPoolSize = "4"
''', True)


def prepare():
    marker = STATE / 'prepared.json'
    if marker.exists():
        if json.loads(marker.read_text()).get('format') != 'parts-trace-local-chains/v1':
            raise RuntimeError('Unrecognized existing network; identities have been preserved')
        print('Existing chain identities retained.', flush=True)
        return
    if any(STATE.iterdir()):
        raise RuntimeError('Incomplete or foreign state exists. Inspect it before recovery; no keys were replaced.')
    dump(STATE / 'prepare.pending', {'startedAt': dt.datetime.now(dt.timezone.utc).isoformat()})
    prepare_chainmaker()
    relay = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    dump(STATE / 'sdk/relay.json', {
        'privateKey': base64.b64encode(relay.private_bytes(serialization.Encoding.DER,
                     serialization.PrivateFormat.PKCS8, serialization.NoEncryption())).decode(),
        'publicKey': base64.b64encode(relay.public_key().public_bytes(serialization.Encoding.DER,
                    serialization.PublicFormat.SubjectPublicKeyInfo)).decode()}, True)
    prepare_fisco()
    dump(marker, {'format': 'parts-trace-local-chains/v1', 'createdAt': dt.datetime.now(dt.timezone.utc).isoformat()})
    (STATE / 'prepare.pending').unlink()
    print('Generated fresh identities and four independent ledgers.', flush=True)


def wait_nodes():
    deadline = time.monotonic() + 240
    for host, port in [('cm-node1', 12301), ('fisco-node1', 20200)]:
        while True:
            try:
                with socket.create_connection((host, port), timeout=3):
                    break
            except OSError:
                if time.monotonic() > deadline:
                    raise RuntimeError('Node did not become ready: %s:%s' % (host, port))
                time.sleep(3)


def export():
    destination = Path('/export')
    # FISCO resolves this directory before loading the fixed account. On Windows,
    # a missing drive-qualified path can be mistaken for a classpath resource.
    (destination / 'fisco/accounts').mkdir(parents=True, exist_ok=True)
    # Export only the application identity. CA and node signing keys stay in the Docker volume.
    for path in (STATE / 'sdk/cm').glob('*.yml'):
        if '-admin' not in path.name:
            config = yaml.safe_load(path.read_text())
            client = config['chain_client']
            client['nodes'][0]['node_addr'] = '127.0.0.1:12301'
            for key in ('user_key_file_path', 'user_crt_file_path', 'user_sign_key_file_path', 'user_sign_crt_file_path'):
                source = Path(client[key])
                relative = Path('cm/certs') / source.name
                write(destination / relative, source.read_bytes(), source.suffix == '.key')
                client[key] = '@SDK@/' + relative.as_posix()
            roots = []
            for root in client['nodes'][0]['trust_root_paths']:
                source = Path(root)
                relative = Path('cm/certs') / source.parent.name / source.name
                write(destination / relative, source.read_bytes())
                roots.append('@SDK@/' + relative.as_posix())
            client['nodes'][0]['trust_root_paths'] = roots
            write(destination / 'cm' / path.name, yaml.safe_dump(config, sort_keys=False), True)
    for name in ('ca.crt', 'sdk.crt', 'sdk.key', 'account.pem'):
        write(destination / 'fisco' / name, (STATE / 'sdk/fisco' / name).read_bytes(), name.endswith(('.key', '.pem')))
    text = (STATE / 'sdk/fisco.toml').read_text().replace('/state/sdk', '@SDK@').replace('fisco-node1:20200', '127.0.0.1:20200')
    write(destination / 'fisco.toml', text, True)
    for name in ('relay.json', 'fisco-account.json'):
        write(destination / name, (STATE / 'sdk' / name).read_bytes(), True)
    for name in ('deployment.json', 'verification.json'):
        write(destination / name, (STATE / name).read_bytes())
    print('Application credentials and verified deployment exported.', flush=True)


if __name__ == '__main__':
    os.umask(0o077)
    action = sys.argv[1] if len(sys.argv) == 2 else ''
    if action == 'prepare':
        prepare()
    elif action in ('deploy', 'verify'):
        if not (STATE / 'prepared.json').exists():
            raise RuntimeError('Run prepare first')
        wait_nodes()
        if action == 'deploy':
            java('deploy')
        java('verify')
        export()
    elif action == 'recover-cm':
        wait_nodes()
        java('recover-cm')
    else:
        raise SystemExit('Usage: bootstrap.py prepare|deploy|verify')
