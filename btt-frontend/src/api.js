const prefix = '/api/v1'

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

export function query(values) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value !== '' && value != null) params.set(key, value)
  }
  const suffix = params.toString()
  return suffix ? `?${suffix}` : ''
}

export function client(token, onExpired) {
  return async (path, { method = 'GET', body, signal } = {}) => {
    let response
    const timeout = AbortSignal.timeout(method === 'GET' ? 10_000 : 30_000)
    const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout
    try {
      response = await fetch(prefix + path, {
        method, signal: requestSignal,
        headers: {
          ...(token && { Authorization: `Bearer ${token}` }),
          ...(body != null && { 'Content-Type': 'application/json' }),
        },
        ...(body != null && { body: JSON.stringify(body) }),
      })
    } catch (error) {
      if (error.name === 'AbortError') throw error
      if (error.name === 'TimeoutError') throw new ApiError(0, 'Сервер не ответил вовремя. Повторите запрос.')
      throw new ApiError(0, 'Не удалось подключиться к серверу. Попробуйте ещё раз.')
    }
    if (response.status === 204) return null
    const payload = response.headers.get('content-type')?.includes('json') ? await response.json() : null
    if (!response.ok) {
      if (token && response.status === 401) onExpired?.()
      const defaults = {
        400: 'Проверьте введённые данные.', 401: 'Неверный логин или пароль.',
        403: 'Недостаточно прав для этого действия.', 404: 'Запись не найдена.',
        409: 'Данные изменились. Обновите страницу и повторите действие.',
      }
      throw new ApiError(response.status, payload?.message || defaults[response.status] || 'Сервер не смог выполнить запрос.')
    }
    return payload
  }
}

export async function listAll(api, path, filters, signal) {
  const items = []
  for (let offset = 0; ; offset += 100) {
    const page = await api(path + query({ ...filters, offset, limit: 100 }), { signal })
    items.push(...page)
    if (page.length < 100) return items
  }
}

export const post = (api, path, body) => api(path, { method: 'POST', body })

export function parseUtc(value) {
  if (!value) return null
  const normalized = /(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : value + 'Z'
  return new Date(normalized)
}

export function isoLocal(value) {
  return value ? new Date(value).toISOString() : undefined
}

export function dateTime(value) {
  const parsed = parseUtc(value)
  return parsed && Number.isFinite(parsed.getTime()) ? parsed.toLocaleString('ru-RU', {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }) : '—'
}

export const money = amount => amount == null ? '—' : new Intl.NumberFormat('ru-RU', {
  style: 'currency', currency: 'RUB',
}).format(amount)

export function values(event) {
  event.preventDefault()
  return Object.fromEntries(new FormData(event.currentTarget))
}
