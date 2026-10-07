import { spawn } from 'node:child_process';
import { closeSync, mkdirSync, openSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { serverLogPath } from './server-log.mjs';

mkdirSync('test-results', { recursive: true });
const descriptor = openSync(serverLogPath(), 'w', 0o600);
let child;
let stopRequested = false;
const stop = () => {
  stopRequested = true;
  child?.kill('SIGTERM');
};
process.once('SIGINT', stop);
process.once('SIGTERM', stop);

try {
  child = spawn(process.execPath, [fileURLToPath(new URL('./start.mjs', import.meta.url))], {
    stdio: ['ignore', descriptor, descriptor],
    env: process.env,
  });
  const code = await new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', (code, signal) => resolve(stopRequested ? 0 : (signal ? 1 : code)));
  });
  process.exitCode = code ?? 1;
} finally {
  closeSync(descriptor);
}
