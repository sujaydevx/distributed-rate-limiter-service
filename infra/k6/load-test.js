import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const allowedCount = new Counter('allowed_count');
const deniedCount = new Counter('denied_count');

export const options = {
  vus: 10,           // 10 simulated clients
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<200'],
  },
};

export default function () {
  // X-Client-Id is the testing-only override described in RateLimitFilter —
  // it lets us simulate 10 distinct "users" without 10 real machines/IPs.
  const res = http.get('http://localhost:8080/api/hello', {
    headers: { 'X-Client-Id': `test-user-${__VU}` },
  });

  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
  });

  if (res.status === 200) allowedCount.add(1);
  else deniedCount.add(1);

  sleep(0.2);
}
