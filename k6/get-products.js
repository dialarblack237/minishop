import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '60s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{name:GET /products,expected_response:true}': ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const url = 'http://localhost:8080/products?page=0&size=20&sort=priceCents,asc';
  const res = http.get(url, { tags: { name: 'GET /products' } });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'body is JSON':  (r) => (r.headers['Content-Type'] || '').includes('application/json'),
    'has content[]': (r) => JSON.parse(r.body).content !== undefined,
  });

  sleep(15);
}