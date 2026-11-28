import { useCallback, useEffect, useMemo, useState } from 'react'
import { canAdmin } from './permissions'
import { client, listAll, post } from './api'
import { Empty, Resource, Status } from './ui'
import { useResource } from './hooks'
import { Login, Signup } from './pages/Auth'
import { Members, OrganizationRequest, Organizations } from './pages/Organizations'
import { Certificate, CreateRegistration, RegistrationDetails } from './pages/Registration'
import { Admin, Carrier, Schedule } from './pages/Operations'
import { Tickets, TripDetails, TripSearch } from './pages/Trips'

const sessionKey = 'btt-session'
const anonymous = client()
const uuid = /^[\da-f]{8}(?:-[\da-f]{4}){3}-[\da-f]{12}$/i

function readToken() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(sessionKey))
    return typeof saved?.token === 'string' && saved.expires > Date.now() ? saved : null
  } catch { return null }
}

function path() {
  return (location.hash.slice(1) || '/trips').split('/').filter(Boolean)
}

const navigation = [
  ['trips', 'Найти рейс'],
  ['tickets', 'Мои билеты'],
  ['organizations', 'Организации'],
  ['carrier', 'Перевозчик'],
  ['admin', 'Администрация'],
]

export default function App() {
  const [route, setRoute] = useState(path)
  const [token, setToken] = useState(readToken)
  const [message, setMessage] = useState('')
  const [page, id, subpage] = route
  useEffect(() => {
    function change() { setRoute(path()); window.scrollTo(0, 0) }
    window.addEventListener('hashchange', change)
    return () => window.removeEventListener('hashchange', change)
  }, [])
  const expired = useCallback(() => {
    sessionStorage.removeItem(sessionKey)
    setToken(null)
    setMessage('Сессия истекла. Войдите снова.')
    location.hash = '#/login'
  }, [])
  useEffect(() => {
    if (!token) return
    const timer = setTimeout(expired, Math.max(0, token.expires - Date.now()))
    return () => clearTimeout(timer)
  }, [token, expired])

  const api = useMemo(() => client(token?.token, expired), [token, expired])
  const loadSession = useCallback(signal => token ? api('/session', { signal }) : Promise.resolve(null), [api, token])
  const session = useResource(loadSession, { poll: token ? 8000 : 0 })
  const loadKinds = useCallback(signal => token ? listAll(api, '/transport-kinds', {}, signal) : Promise.resolve([]), [api, token])
  const kinds = useResource(loadKinds)

  async function login(username, password) {
    const response = await post(anonymous, '/auth/token', { username, password })
    const record = { token: response.token, expires: Date.now() + response.expiresIn * 1000 }
    const authenticated = client(record.token, expired)
    await authenticated('/session')
    sessionStorage.setItem(sessionKey, JSON.stringify(record))
    setToken(record)
    setMessage('')
    return authenticated
  }

  function logout() {
    sessionStorage.removeItem(sessionKey)
    setToken(null)
    setMessage('')
    location.hash = '#/login'
  }

  let content
  if (page === 'signup') {
    content = <Signup publicApi={anonymous} api={api} signedIn={Boolean(token)} login={login} refreshSession={session.reload} />
  } else if (!token) {
    content = <Login login={login} message={message} />
  } else {
    content = <Resource value={session}>{current => <Resource value={kinds}>{catalog => {
      if (page === 'login') return <p>Вы уже вошли. <a href="#/trips">Найти рейс</a></p>
      return <AuthenticatedPage key={route.join('/')} page={page} id={id} subpage={subpage}
        api={api} session={current} kinds={catalog} refreshSession={session.reload} />
    }}</Resource>}</Resource>
  }

  return <>
    <a className="skip" href="#main" onClick={event => { event.preventDefault(); document.getElementById('main').focus() }}>К содержанию</a>
    <header className="topbar"><div className="topbar-inner">
      <a className="brand" href="#/trips"><span className="brand-mark" aria-hidden="true">↗</span> Билеты BTT</a>
      {token && session.data && <nav aria-label="Основная навигация">
        {navigation.filter(([key]) => key !== 'admin' || canAdmin(session.data.permissions)).map(([key, label]) =>
          <a href={`#/${key}`} key={key} aria-current={page === key ? 'page' : undefined}>{label}</a>)}
      </nav>}
      {token && <div className="account">
        {session.data && <span>{session.data.account.fullName}</span>}<button className="logout" onClick={logout}>Выйти</button>
      </div>}
    </div></header>
    <main tabIndex={-1} id="main" className="container">
      {page === 'signup' && token && !session.data && !session.loading && <Status error={session.error} />}
      {content}
    </main>
  </>
}

function AuthenticatedPage({ page, id, subpage, api, session, kinds, refreshSession }) {
  if (page === 'trips' && !id) return <TripSearch api={api} kinds={kinds} />
  if (page === 'tickets' && !id) return <Tickets api={api} />
  if (page === 'organizations' && !id) return <Organizations api={api} session={session} />
  if (page === 'carrier' && !id) return <Carrier api={api} session={session} kinds={kinds} />
  if (page === 'admin' && !id) return <Admin api={api} session={session} kinds={kinds} />
  if (page === 'registration' && id === 'new') return <CreateRegistration api={api} session={session} kinds={kinds} type={Number(subpage)} />
  if (uuid.test(id || '')) {
    if (page === 'trips') return <TripDetails api={api} id={id} />
    if (page === 'organizations' && subpage === 'members') return <Members api={api} session={session} id={id} />
    if (page === 'organization-requests') return <OrganizationRequest api={api} session={session} id={id} refreshSession={refreshSession} />
    if (page === 'registration-requests') return <RegistrationDetails api={api} session={session} kinds={kinds} id={id} />
    if (page === 'certificates') return <Certificate api={api} kinds={kinds} id={id} />
    if (page === 'schedule') return <Schedule api={api} session={session} kinds={kinds} id={id} />
  }
  return <Empty>Страница не найдена. <a href="#/trips">К поиску рейсов</a></Empty>
}
