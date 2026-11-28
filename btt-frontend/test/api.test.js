import assert from 'node:assert/strict'
import test from 'node:test'
import { ApiError, client, isoLocal, parseUtc, query } from '../src/api.js'

test('empty filters are omitted and explicit zero and false are preserved', () => {
  const parameters = new URLSearchParams(query({ source: 'Нижний Новгород', destination: '', offset: 0, bookableOnly: false }))
  assert.equal(parameters.get('source'), 'Нижний Новгород')
  assert.equal(parameters.has('destination'), false)
  assert.equal(parameters.get('offset'), '0')
  assert.equal(parameters.get('bookableOnly'), 'false')
  assert.equal(query({ none: undefined }), '')
})

test('server timestamps without offsets are interpreted as UTC', () => {
  assert.equal(parseUtc('2026-11-27T15:30:00.123456').toISOString(), '2026-11-27T15:30:00.123Z')
  assert.equal(parseUtc('2026-11-27T15:30:00+03:00').toISOString(), '2026-11-27T12:30:00.000Z')
  assert.equal(isoLocal('2026-11-27T15:30:00+03:00'), '2026-11-27T12:30:00.000Z')
})

test('an authenticated 401 clears the session while keeping the API error', async context => {
  const expired = context.mock.fn()
  const fetch = context.mock.method(globalThis, 'fetch', async () =>
    Response.json({ message: 'Сессия истекла' }, { status: 401 }))
  await assert.rejects(client('token', expired)('/session'), error =>
    error instanceof ApiError && error.status === 401 && error.message === 'Сессия истекла')
  assert.equal(expired.mock.callCount(), 1)
  assert.equal(fetch.mock.calls[0].arguments[1].headers.Authorization, 'Bearer token')
})

test('a booking conflict preserves the backend message and does not end the session', async context => {
  const expired = context.mock.fn()
  const fetch = context.mock.method(globalThis, 'fetch', async () =>
    Response.json({ message: 'Место уже забронировано' }, { status: 409 }))
  const body = { tripId: 'test-trip', seatCode: '1A' }
  await assert.rejects(client('token', expired)('/tickets', { method: 'POST', body }), error =>
    error.status === 409 && error.message === 'Место уже забронировано')
  assert.equal(expired.mock.callCount(), 0)
  assert.equal(fetch.mock.calls[0].arguments[1].body, JSON.stringify(body))
})
