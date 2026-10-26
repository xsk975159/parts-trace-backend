import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import solc from 'solc';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sources = Object.fromEntries(['TraceAnchor.sol', 'RsaSha256.sol'].map(name => [name, { content: fs.readFileSync(path.join(root, name), 'utf8') }]));
const input = { language: 'Solidity', sources, settings: { optimizer: { enabled: true, runs: 200 }, evmVersion: 'istanbul', outputSelection: { '*': { '*': ['abi', 'evm.bytecode.object', 'evm.deployedBytecode.object', 'evm.methodIdentifiers'] } } } };
const sm = process.argv.includes('--sm');
let version = solc.version(), compiled;
if (sm) {
  const executable = process.env.SOLC_GM;
  if (!executable) throw new Error('请将SOLC_GM设为FISCO官方0.8.11国密编译器的绝对路径');
  version = execFileSync(executable, ['--version'], { encoding: 'utf8' });
  if (!version.includes('0.8.11')) throw new Error('国密编译器版本须为0.8.11');
  compiled = execFileSync(executable, ['--standard-json'], { input: JSON.stringify(input), encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
} else {
  compiled = solc.compile(JSON.stringify(input));
}
const output = JSON.parse(compiled);
const errors = (output.errors || []).filter(item => item.severity === 'error');
if (errors.length) throw new Error(errors.map(item => item.formattedMessage).join('\n'));
const artifact = output.contracts['TraceAnchor.sol'].TraceAnchor;
if (sm && artifact.evm.methodIdentifiers['contract_info()'] !== crypto.createHash('sm3').update('contract_info()').digest('hex').slice(0,8))
  throw new Error('编译器未生成SM3函数选择器，拒绝把普通字节码标为国密字节码');
fs.mkdirSync(path.join(root, 'artifacts'), { recursive: true });
fs.writeFileSync(path.join(root, 'artifacts', sm ? 'TraceAnchor.sm.json' : 'TraceAnchor.json'), JSON.stringify(artifact, null, 2));
const resources = path.resolve(root, '../../src/main/resources/fisco');
fs.mkdirSync(resources, { recursive: true });
if (sm) {
  if (fs.readFileSync(path.join(resources, 'TraceAnchor.abi.json'), 'utf8') !== JSON.stringify(artifact.abi))
    throw new Error('国密与普通编译器的ABI不一致，请核对源文件与编译器版本');
} else fs.writeFileSync(path.join(resources, 'TraceAnchor.abi.json'), JSON.stringify(artifact.abi));
fs.writeFileSync(path.join(resources, sm ? 'TraceAnchor.sm.bin' : 'TraceAnchor.bin'), artifact.evm.bytecode.object + '\n');
console.log(`Solidity ${version.trim()}: TraceAnchor${sm ? '国密' : '普通'}字节码已生成`);
