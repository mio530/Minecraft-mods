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
  transport:
    process.env.NODE_ENV === 'production'
      ? undefined
      : { target: 'pino/file', options: { destination: 1 } },
});
