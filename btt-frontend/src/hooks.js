import { useCallback, useEffect, useRef, useState } from 'react'

export function useResource(loader, { poll = 0, shouldPoll } = {}) {
  const [state, setState] = useState({ data: null, error: null, loading: true })
  const [revision, setRevision] = useState(0)
  const reload = useCallback(() => setRevision(value => value + 1), [])
  useEffect(() => {
    const controller = new AbortController()
    let timer
    let inFlight = false
    let keepPolling = true
    setState({ data: null, error: null, loading: true })
    async function load() {
      if (inFlight || controller.signal.aborted) return
      inFlight = true
      clearTimeout(timer)
      try {
        const data = await loader(controller.signal)
        keepPolling = !shouldPoll || shouldPoll(data)
        if (!controller.signal.aborted) setState({ data, error: null, loading: false })
      } catch (error) {
        if (!controller.signal.aborted) setState(current => ({ ...current, error, loading: false }))
      } finally {
        inFlight = false
        if (poll && keepPolling && !controller.signal.aborted) timer = setTimeout(load, poll)
      }
    }
    const onVisibilityChange = () => { if (!document.hidden) load() }
    if (poll) document.addEventListener('visibilitychange', onVisibilityChange)
    load()
    return () => {
      if (poll) document.removeEventListener('visibilitychange', onVisibilityChange)
      controller.abort()
      clearTimeout(timer)
    }
  }, [loader, revision, poll, shouldPoll])
  return { ...state, reload }
}

export function useAction() {
  const [state, setState] = useState({ busy: false, error: null, notice: '' })
  const running = useRef(false)
  const run = async (operation, notice = '') => {
    if (running.current) return null
    running.current = true
    setState({ busy: true, error: null, notice: '' })
    try {
      const result = await operation()
      setState({ busy: false, error: null, notice })
      return result
    } catch (error) {
      setState({ busy: false, error, notice: '' })
      return null
    } finally {
      running.current = false
    }
  }
  return { ...state, run }
}
