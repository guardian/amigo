import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createScenario, groups, tokenResponse, startProvider } from './provider.mjs';

test('successful identity matches the pinned ID-token parser contract', () => {
  const scenario = createScenario({}, 'test-id');
  const token = tokenResponse(scenario, 1000);
  const claims = JSON.parse(Buffer.from(token.id_token.split('.')[1], 'base64url').toString());
  assert.equal(claims.email, 'e2e-test-id@guardian.co.uk');
  assert.equal(claims.exp, 4600);
  for (const field of ['iss', 'sub', 'azp', 'aud', 'email', 'email_verified', 'at_hash', 'iat', 'exp']) {
    assert.ok(Object.hasOwn(claims, field), field);
  }
});

test('expired identity is already expired at issuance', () => {
  const scenario = createScenario({ outcome: 'expired' }, 'test-id');
  const claims = JSON.parse(Buffer.from(tokenResponse(scenario, 1000).id_token.split('.')[1], 'base64url').toString());
  assert.equal(claims.exp, 940);
});

test('disallowed domain is expressed in the identity email', () => {
  assert.equal(createScenario({ outcome: 'wrong-domain' }, 'test-id').email,
    'e2e-test-id@example.invalid');
});

test('each configured group can independently authorise a fixture', () => {
  for (const group of groups) assert.equal(createScenario({ group }).group, group);
});

test('unknown outcomes and group identifiers fail explicitly', () => {
  assert.throws(() => createScenario({ outcome: 'typo' }), /Invalid authentication fixture/);
  assert.throws(() => createScenario({ group: 'real-group' }), /Invalid authentication fixture/);
});

test('the proxy rejects unrecognised CONNECT destinations without forwarding them', async () => {
  const errors = [];
  const provider = await startProvider({ port: 0, logError: (...args) => errors.push(args) });
  try {
    const responseCode = await new Promise((resolve, reject) => {
      const request = http.request(provider.url, {
        method: 'CONNECT', path: 'unexpected.example.invalid:443',
      });
      request.once('error', reject);
      request.once('connect', (response, socket) => {
        socket.destroy();
        resolve(response.statusCode);
      });
      request.end();
    });
    assert.equal(responseCode, 502);
    const status = await (await fetch(`${provider.url}/status`)).json();
    assert.equal(status.discovery, 0);
    assert.equal(status.serviceTokens, 0);
    assert.deepEqual(status.unexpected, [{
      message: 'Rejected CONNECT destination',
      destination: 'unexpected.example.invalid:443',
    }]);
    assert.deepEqual(errors, [[
      'Authentication mock rejected CONNECT:', 'unexpected.example.invalid:443',
    ]]);
  } finally {
    await provider.close();
  }
});
