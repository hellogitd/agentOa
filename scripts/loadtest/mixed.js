// k6 mixed load test — 200 VU, 30 min, p95 targets from docs/02 NFR
// Run: k6 run --vus 200 --duration 30m mixed.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const loginDuration = new Trend('login_duration');
const listDuration = new Trend('list_duration');
const submitDuration = new Trend('submit_duration');
const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '2m', target: 200 },
    { duration: '26m', target: 200 },
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    login_duration: ['p(95)<1000'],
    list_duration: ['p(95)<500'],
    submit_duration: ['p(95)<2000'],
    errors: ['rate<0.01'],
  },
};

function login(username, password) {
  const res = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({ username, password }), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'login ok': (r) => r.status === 200 }) || errorRate.add(1);
  loginDuration.add(res.timings.duration);
  try {
    return JSON.parse(res.body).data?.token || '';
  } catch {
    return '';
  }
}

export default function () {
  const token = login('loadtest_user', 'LoadTest@123');
  if (!token) { sleep(1); return; }
  const authHeaders = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

  // List APIs
  const todo = http.get(`${BASE}/api/v1/workflow/tasks/todo?pageNum=1&pageSize=20`, { headers: authHeaders });
  check(todo, { 'todo list ok': (r) => r.status === 200 }) || errorRate.add(1);
  listDuration.add(todo.timings.duration);

  const events = http.get(`${BASE}/api/v1/calendar/events?start=2026-01-01T00:00:00Z&end=2026-12-31T23:59:59Z`, { headers: authHeaders });
  check(events, { 'events ok': (r) => r.status === 200 }) || errorRate.add(1);
  listDuration.add(events.timings.duration);

  // Submit (lightweight — draft create)
  const draft = http.post(`${BASE}/api/v1/workflow/leaves/draft`, JSON.stringify({
    leaveType: 'annual',
    startTime: '2026-11-01T09:00:00',
    endTime: '2026-11-01T18:00:00',
    reason: 'loadtest',
  }), { headers: authHeaders });
  check(draft, { 'draft ok': (r) => r.status === 200 || r.status === 201 }) || errorRate.add(1);
  submitDuration.add(draft.timings.duration);

  sleep(1);
}
