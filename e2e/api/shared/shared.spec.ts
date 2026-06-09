import { expect, test } from '../fixtures/authenticated-api';

test.describe('Shared / actuator', () => {
  test('GET /actuator/health', async ({ authedRequest }) => {
    const response = await authedRequest.get('/actuator/health');
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('UP');
  });
});
