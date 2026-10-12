import { spawn, execFileSync } from 'node:child_process';
import { generateKeyPairSync, randomBytes } from 'node:crypto';
import { mkdtemp, readFile, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { startProvider } from './provider.mjs';

const directory = await mkdtemp(join(tmpdir(), 'amigo-e2e-auth-'));
let provider;
let child;
let stopRequested = false;

const stop = () => {
  stopRequested = true;
  child?.kill('SIGTERM');
};
process.once('SIGINT', stop);
process.once('SIGTERM', stop);

function portFromEnvironment(name, fallback) {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isInteger(value) || value < 1024 || value > 65535) {
    throw new Error(`${name} must be an integer between 1024 and 65535`);
  }
  return value;
}

async function material() {
  const cert = join(directory, 'proxy.pem');
  const key = join(directory, 'proxy-key.pem');
  const trustStore = join(directory, 'trust.p12');
  const trustPassword = randomBytes(24).toString('hex');
  execFileSync('openssl', [
    'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-sha256', '-days', '1',
    '-keyout', key, '-out', cert, '-subj', '/CN=AMIgo E2E authentication mock',
    '-addext', 'subjectAltName=DNS:accounts.google.com,DNS:oauth2.googleapis.com,DNS:openidconnect.googleapis.com,DNS:www.googleapis.com,DNS:admin.googleapis.com',
  ], { stdio: 'pipe' });
  execFileSync('keytool', [
    '-importcert', '-noprompt', '-alias', 'e2e', '-file', cert,
    '-keystore', trustStore, '-storetype', 'PKCS12',
    '-storepass:env', 'E2E_TRUST_PASSWORD',
  ], { stdio: 'pipe', env: { ...process.env, E2E_TRUST_PASSWORD: trustPassword } });
  const { privateKey } = generateKeyPairSync('rsa', {
    modulusLength: 2048,
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
    publicKeyEncoding: { type: 'spki', format: 'pem' },
  });
  const accountPath = join(directory, 'service-account.json');
  await writeFile(accountPath, JSON.stringify({
    type: 'service_account', project_id: 'amigo-e2e',
    private_key_id: 'e2e', private_key: privateKey,
    client_email: 'e2e-service@amigo-e2e.iam.gserviceaccount.com',
    client_id: '000000000000000000000',
    token_uri: 'https://oauth2.googleapis.com/token',
  }), { mode: 0o600 });
  return { cert, key, trustStore, trustPassword, accountPath };
}

try {
  const port = portFromEnvironment('E2E_PORT', 9100);
  const proxyPort = portFromEnvironment('E2E_PROXY_PORT', 9101);
  if (port === proxyPort) throw new Error('Application and mock proxy ports must differ');
  const files = await material();
  if (stopRequested) throw new Error('E2E startup was cancelled');
  provider = await startProvider({
    port: proxyPort, cert: await readFile(files.cert), key: await readFile(files.key),
  });
  child = spawn('sbt', ['-batch', 'Test / runMain e2e.E2EServer'], {
    stdio: 'inherit',
    env: {
      ...process.env,
      E2E_PORT: String(port), E2E_PROXY_PORT: String(proxyPort),
      E2E_PROXY_CERT: files.cert, E2E_TRUST_STORE: files.trustStore,
      E2E_TRUST_PASSWORD: files.trustPassword,
      E2E_SERVICE_ACCOUNT: files.accountPath,
      E2E_SESSION_KEY: randomBytes(48).toString('hex'),
    },
  });
  const code = await new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', (code, signal) => resolve(stopRequested ? 0 : (signal ? 1 : code)));
  });
  process.exitCode = code ?? 1;
} finally {
  if (provider) await provider.close();
  await rm(directory, { recursive: true, force: true });
}
