import http from 'node:http';
import https from 'node:https';
import { randomUUID } from 'node:crypto';

export const groups = [
  'e2e-department@guardian.co.uk',
  'e2e-data@guardian.co.uk',
  'e2e-multimedia@guardian.co.uk',
];

const hosts = new Set([
  'accounts.google.com',
  'oauth2.googleapis.com',
  'openidconnect.googleapis.com',
  'www.googleapis.com',
  'admin.googleapis.com',
]);
const outcomes = new Set([
  'success', 'no-groups', 'unrelated-group', 'wrong-domain', 'expired',
  'token-error', 'userinfo-error', 'groups-error',
  'token-malformed', 'userinfo-malformed', 'groups-malformed',
]);

export function createScenario(options = {}, id = randomUUID()) {
  const outcome = options.outcome ?? 'success';
  const group = options.group ?? groups[0];
  if (!outcomes.has(outcome) || !groups.includes(group)) {
    throw new Error('Invalid authentication fixture options');
  }
  return {
    id, outcome, group,
    email: `e2e-${id}@${outcome === 'wrong-domain' ? 'example.invalid' : 'guardian.co.uk'}`,
    events: [],
  };
}

export function tokenResponse(scenario, now = Math.floor(Date.now() / 1000)) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const claims = {
    iss: 'https://accounts.google.com', sub: scenario.id,
    azp: 'e2e-client', aud: 'e2e-client', email: scenario.email,
    email_verified: true, at_hash: 'e2e', hd: scenario.email.split('@')[1],
    iat: now, exp: now + (scenario.outcome === 'expired' ? -60 : 3600),
  };
  return {
    access_token: `e2e-access.${scenario.id}`, token_type: 'Bearer', expires_in: 3600,
    id_token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(claims)}.e2e-signature`,
  };
}

function sendJSON(response, status, body) {
  response.writeHead(status, { 'content-type': 'application/json' });
  response.end(JSON.stringify(body));
}

function providerError(stage) {
  return { error: { errors: [], code: 503, message: `Synthetic ${stage} failure` } };
}

async function bodyOf(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 65_536) throw new Error('Mock request body exceeds 64 KiB');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

/** Never opens upstream connections: all accepted CONNECT tunnels terminate here. */
export async function startProvider({ port, key, cert, logError = console.error }) {
  const scenarios = new Map();
  const unexpected = [];
  const counters = { discovery: 0, serviceTokens: 0 };
  const sockets = new Set();

  function record(scenario, stage) {
    scenario.events.push({ stage, scenarioId: scenario.id });
  }

  function lookup(id) {
    const scenario = scenarios.get(id);
    if (!scenario) throw new Error('Unknown authentication scenario');
    return scenario;
  }

  function requireRequest(condition, message) {
    if (!condition) throw new Error(message);
  }

  function replyForStage(response, scenario, stage, body) {
    record(scenario, stage);
    if (scenario.outcome === `${stage}-error`) {
      sendJSON(response, 503, providerError(stage));
    } else if (scenario.outcome === `${stage}-malformed`) {
      sendJSON(response, 200, { invalid: true });
    } else {
      sendJSON(response, 200, body);
    }
  }

  async function googleRequest(request, response) {
    const url = new URL(request.url, `https://${request.headers.host}`);
    requireRequest(hosts.has(url.hostname), 'Unexpected authentication host');
    if (request.method === 'GET' && url.hostname === 'accounts.google.com'
      && url.pathname === '/.well-known/openid-configuration') {
      counters.discovery++;
      sendJSON(response, 200, {
        authorization_endpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
        token_endpoint: 'https://oauth2.googleapis.com/token',
        userinfo_endpoint: 'https://openidconnect.googleapis.com/v1/userinfo',
      });
      return;
    }
    if (request.method === 'POST' && url.hostname === 'oauth2.googleapis.com'
      && url.pathname === '/token') {
      const form = new URLSearchParams(await bodyOf(request));
      if (form.get('grant_type') === 'urn:ietf:params:oauth:grant-type:jwt-bearer') {
        const assertion = form.get('assertion');
        requireRequest(typeof assertion === 'string', 'Missing service-account assertion');
        const claims = JSON.parse(Buffer.from(assertion.split('.')[1], 'base64url').toString());
        requireRequest(claims.sub === 'e2e-admin@guardian.co.uk'
          && claims.scope === 'https://www.googleapis.com/auth/admin.directory.group.readonly',
        'Incorrect delegated service-account claims');
        counters.serviceTokens++;
        sendJSON(response, 200, {
          access_token: 'e2e-service-token', token_type: 'Bearer', expires_in: 3600,
        });
        return;
      }
      const scenario = lookup(form.get('code'));
      requireRequest(form.get('grant_type') === 'authorization_code'
        && form.get('client_id') === 'e2e-client'
        && form.get('client_secret') === 'e2e-client-not-a-real-secret'
        && form.get('redirect_uri') === `http://127.0.0.1:${process.env.E2E_PORT ?? '9100'}/oauth2callback`,
      'Incorrect authorisation-code request');
      replyForStage(response, scenario, 'token', tokenResponse(scenario));
      return;
    }
    if (request.method === 'GET' && url.hostname === 'openidconnect.googleapis.com'
      && url.pathname === '/v1/userinfo') {
      const authorization = request.headers.authorization;
      requireRequest(typeof authorization === 'string'
        && authorization.startsWith('Bearer e2e-access.'), 'Missing synthetic user access token');
      const scenario = lookup(authorization.slice('Bearer e2e-access.'.length));
      replyForStage(response, scenario, 'userinfo', {
        sub: scenario.id, name: 'E2E User', given_name: 'E2E', family_name: 'User',
        email: scenario.email,
      });
      return;
    }
    if (request.method === 'GET'
      && ['www.googleapis.com', 'admin.googleapis.com'].includes(url.hostname)
      && url.pathname === '/admin/directory/v1/groups') {
      requireRequest(request.headers.authorization === 'Bearer e2e-service-token',
        'Missing synthetic service-account access token');
      const email = url.searchParams.get('userKey');
      const scenario = [...scenarios.values()].find(value => value.email === email);
      requireRequest(Boolean(scenario), 'Unknown groups userKey');
      const memberships = scenario.outcome === 'no-groups' ? []
        : [scenario.outcome === 'unrelated-group' ? 'unrelated@guardian.co.uk' : scenario.group];
      replyForStage(response, scenario, 'groups', {
        kind: 'admin#directory#groups',
        groups: memberships.map(email => ({ email })),
      });
      return;
    }
    throw new Error('Unexpected authentication request');
  }

  async function controlRequest(request, response) {
    const url = new URL(request.url, 'http://127.0.0.1');
    if (request.method === 'GET' && url.pathname === '/status') {
      sendJSON(response, 200, { ...counters, unexpected });
      return;
    }
    if (request.method === 'POST' && url.pathname === '/scenarios') {
      const options = JSON.parse(await bodyOf(request));
      requireRequest(options !== null && typeof options === 'object'
        && !Array.isArray(options), 'Invalid scenario options');
      const scenario = createScenario(options);
      scenarios.set(scenario.id, scenario);
      sendJSON(response, 201, { id: scenario.id, email: scenario.email });
      return;
    }
    const match = /^\/scenarios\/([0-9a-f-]+)$/.exec(url.pathname);
    if (match && request.method === 'GET') {
      const scenario = lookup(match[1]);
      sendJSON(response, 200, { events: scenario.events, unexpected });
      return;
    }
    if (match && request.method === 'DELETE') {
      lookup(match[1]);
      scenarios.delete(match[1]);
      sendJSON(response, 200, { removed: true });
      return;
    }
    throw new Error('Unexpected mock-control request');
  }

  function handle(handler) {
    return (request, response) => {
      handler(request, response).catch(error => {
        const event = { message: error.message, method: request.method,
          path: request.url?.split('?')[0] };
        unexpected.push(event);
        logError('Authentication mock rejected a request:', event);
        sendJSON(response, 500, { error: 'Authentication mock rejected the request' });
      });
    };
  }

  const secure = https.createServer({ key, cert }, handle(googleRequest));
  const proxy = http.createServer(handle(controlRequest));
  proxy.on('connection', socket => {
    sockets.add(socket);
    socket.once('close', () => sockets.delete(socket));
  });
  proxy.on('connect', (request, socket, head) => {
    if (!hosts.has(request.url?.replace(/:443$/, '')) || !request.url?.endsWith(':443')) {
      unexpected.push({ message: 'Rejected CONNECT destination', destination: request.url });
      logError('Authentication mock rejected CONNECT:', request.url);
      socket.end('HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n');
      return;
    }
    socket.write('HTTP/1.1 200 Connection Established\r\n\r\n');
    if (head.length) socket.unshift(head);
    secure.emit('connection', socket);
  });
  secure.on('tlsClientError', error => {
    unexpected.push({ message: `TLS client error: ${error.code ?? error.name}` });
    logError('Authentication mock TLS failure:', error.message);
  });
  await new Promise((resolve, reject) => {
    proxy.once('error', reject);
    proxy.listen(port, '127.0.0.1', resolve);
  });
  return {
    url: `http://127.0.0.1:${proxy.address().port}`,
    async close() {
      for (const socket of sockets) socket.destroy();
      await new Promise((resolve, reject) =>
        proxy.close(error => error ? reject(error) : resolve()));
    },
  };
}
