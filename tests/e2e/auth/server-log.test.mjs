import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { readServerLogTail } from './server-log.mjs';

test('missing server logs are distinguishable from empty logs', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'amigo-log-test-'));
  try {
    const path = join(directory, 'server.log');
    assert.equal(readServerLogTail(path), undefined);
    await writeFile(path, '');
    assert.deepEqual(readServerLogTail(path), Buffer.alloc(0));
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('failure diagnostics retain the final 64 KiB without reading the entire log', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'amigo-log-test-'));
  try {
    const path = join(directory, 'server.log');
    const content = `${'x'.repeat(128 * 1024)}\nRelevant failure details`;
    await writeFile(path, content);
    const tail = readServerLogTail(path);
    assert.equal(tail.length, 64 * 1024);
    assert.ok(tail.toString().endsWith('\nRelevant failure details'));
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
