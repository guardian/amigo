import type { FullResult, Reporter, TestCase, TestResult } from '@playwright/test/reporter';
import { readServerLogTail, serverLogPath } from './server-log.mjs';

export default class ServerLogReporter implements Reporter {
  onTestEnd(_test: TestCase, result: TestResult): void {
    if (result.status === 'passed' || result.status === 'skipped') return;
    if (result.attachments.some(attachment => attachment.name === 'e2e-server-log')) return;
    const log = readServerLogTail();
    if (log !== undefined) {
      result.attachments.push({
        name: 'e2e-server-log', contentType: 'text/plain', body: log,
      });
    }
  }

  onEnd(result: FullResult): void {
    if (result.status === 'passed') return;
    const log = readServerLogTail();
    if (log === undefined) {
      console.error(`E2E server log unavailable at ${serverLogPath()}; restart any older reused server.`);
      return;
    }
    console.error(`\nE2E server output (last 64 KiB): ${serverLogPath()}\n${log.toString('utf8')}`);
  }
}
