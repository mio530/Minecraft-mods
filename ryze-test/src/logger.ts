import pino from 'pino';
import { config } from './config.js';

/**
 * Structured logger. Never log secrets: tokens, passwords and Microsoft
 * credentials are explicitly redacted.
 */
export const logger = pino({
  level: config.LOG_LEVEL,
  redact: {
    paths: [
      'password',
      'token',
      'accessToken',
      'refreshToken',
      'mcToken',
      'authorization',
      'req.headers.authorization',
      'MCP_TOKEN',
      'JWT_SECRET',
      'DASHBOARD_PASSWORD',
    ],
    censor: '[redacted]',
  },
  // Always log to stderr (fd 2): stdout is reserved for the MCP stdio protocol,
  // and this keeps logs clean in every run mode.
}, pino.destination({ dest: 2, sync: false }));
