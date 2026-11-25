// k6 load test: sends incident events at a constant 50 per second for 2 minutes.
//
//   k6 run --summary-export loadtest/results/summary.json loadtest/incident-load-test.js
//   ./loadtest/verify-no-loss.sh
//
// Override with env vars, e.g.  k6 run -e RATE=100 -e DURATION=5m loadtest/incident-load-test.js
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = parseInt(__ENV.RATE || '50', 10);
const DURATION = __ENV.DURATION || '2m';
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

// Counted by verify-no-loss.sh: every accepted event must end up PROCESSED.
const accepted = new Counter('accepted_events');
const duplicates = new Counter('duplicate_events');

export const options = {
  scenarios: {
    steady_rate: {
      executor: 'constant-arrival-rate', // fixed requests/sec regardless of response time
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],      // fewer than 1% errors
    http_req_duration: ['p(95)<500'],    // 95% of requests answered within 500 ms
    checks: ['rate==1.0'],
  },
};

const TYPES = ['FIRE', 'MEDICAL', 'POLICE', 'TRAFFIC_COLLISION', 'HAZMAT', 'FLOOD', 'OTHER'];
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const DESCRIPTIONS = ['Smoke seen from window', 'Person unconscious on street', 'Two-car collision',
  'Burglary in progress', 'Gas smell reported', 'Road flooded', 'Child trapped in lift'];

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

export default function () {
  const eventId = `load-${RUN_ID}-${__VU}-${__ITER}`;
  const body = JSON.stringify({
    eventId,
    source: 'k6-load-test',
    type: pick(TYPES),
    severity: pick(SEVERITIES),
    latitude: 53.40 + Math.random() * 0.2,   // around Greater Manchester
    longitude: -2.35 + Math.random() * 0.25,
    description: pick(DESCRIPTIONS),
    occurredAt: new Date().toISOString(),
  });
  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(`${BASE_URL}/api/v1/incidents`, body, params);
  check(res, { 'new event accepted (202)': (r) => r.status === 202 });
  if (res.status === 202) accepted.add(1);

  // Every 10th event, a producer "retries" the same event: it must be recognised as a duplicate.
  if (__ITER % 10 === 0) {
    const dup = http.post(`${BASE_URL}/api/v1/incidents`, body, params);
    check(dup, { 'duplicate recognised (200)': (r) => r.status === 200 && r.json('duplicate') === true });
    if (dup.status === 200) duplicates.add(1);
  }
}
