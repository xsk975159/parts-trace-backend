import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from cryptography import x509
from cryptography.hazmat.primitives.asymmetric import ec

DIRECTORY = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('bootstrap', DIRECTORY.parent / 'scripts/bootstrap.py')
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)


class BootstrapSecurityTest(unittest.TestCase):
    def test_peer_identity_matches_official_genesis(self):
        # Public certificate from chainmakerofficial/chainmaker:v2.3.10, wx-org1.
        cert = x509.load_pem_x509_certificate((DIRECTORY / 'fixtures/chainmaker-official-node.crt').read_bytes())
        self.assertEqual('QmcQHCuAXaFkbcsPUj7e37hXXfZ9DdN7bozseo5oX4qiC4', bootstrap.peer_id(cert))

    def test_fresh_certificates_verify_against_their_own_ca(self):
        ca_key = ec.generate_private_key(ec.SECP256R1())
        ca = bootstrap.certificate('local-org', 'ca.local-org', 'root-cert', ca_key, ca=True)
        node_key = ec.generate_private_key(ec.SECP256R1())
        node = bootstrap.certificate('local-org', 'node.tls.local-org', 'consensus', node_key, ca, ca_key)
        ca.public_key().verify(node.signature, node.tbs_certificate_bytes, ec.ECDSA(node.signature_hash_algorithm))
        self.assertFalse(node.extensions.get_extension_for_class(x509.BasicConstraints).value.ca)
        another_key = ec.generate_private_key(ec.SECP256R1())
        another = bootstrap.certificate('local-org', 'another.tls.local-org', 'consensus', another_key, ca, ca_key)
        self.assertNotEqual(bootstrap.peer_id(node), bootstrap.peer_id(another))

    def test_partial_state_is_never_reinitialized(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory)
            original = state / 'important-key'
            original.write_bytes(b'preserved')
            with patch.object(bootstrap, 'STATE', state):
                with self.assertRaisesRegex(RuntimeError, 'Incomplete or foreign'):
                    bootstrap.prepare()
            self.assertEqual(b'preserved', original.read_bytes())
            self.assertEqual([original], list(state.iterdir()))

    def test_node_signing_and_tls_certificates_share_node_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ca_key = ec.generate_private_key(ec.SECP256R1())
            ca = bootstrap.certificate('org', 'ca.org', 'root-cert', ca_key, ca=True)
            sign = bootstrap.identity(root, 'org', 'node.sign', 'consensus', ca, ca_key, 'a' * 32)
            tls = bootstrap.identity(root, 'org', 'node.tls', 'consensus', ca, ca_key, 'a' * 32)
            oid = x509.ObjectIdentifier('2.87.88.2020.11.30.2020.11.4')
            self.assertEqual(sign.extensions.get_extension_for_oid(oid).value.value,
                             tls.extensions.get_extension_for_oid(oid).value.value)


if __name__ == '__main__':
    unittest.main()
