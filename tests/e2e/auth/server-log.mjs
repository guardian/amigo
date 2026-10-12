import { closeSync, fstatSync, openSync, readSync } from 'node:fs';
import { resolve } from 'node:path';

export function serverLogPath() {
  const port = Number(process.env.E2E_PORT ?? 9100);
  if (!Number.isInteger(port) || port < 1024 || port > 65535) {
    throw new Error('E2E_PORT must be an integer between 1024 and 65535');
  }
  return resolve('test-results', `server-${port}.log`);
}

export function readServerLogTail(path = serverLogPath()) {
  let descriptor;
  try {
    descriptor = openSync(path, 'r');
  } catch (error) {
    if (error.code === 'ENOENT') return undefined;
    throw error;
  }
  try {
    const size = fstatSync(descriptor).size;
    const buffer = Buffer.alloc(Math.min(size, 64 * 1024));
    const read = readSync(descriptor, buffer, 0, buffer.length, size - buffer.length);
    return buffer.subarray(0, read);
  } finally {
    closeSync(descriptor);
  }
}
