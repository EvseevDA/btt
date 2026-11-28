import { useCallback, useState } from 'react'
import { post, query } from '../api'
import { Field, PageTitle, Resource, Status } from '../ui'
import { useAction, useResource } from '../hooks'
import { values } from '../api'

export function OrganizationFields() {
  return <div className="fields">
    <Field label="Наименование организации"><input name="name" required maxLength={255} /></Field>
    <Field label="ИНН"><input name="inn" required pattern="[0-9]{10}" inputMode="numeric" maxLength={10} /></Field>
    <Field label="ОГРН"><input name="ogrn" required pattern="[0-9]{13}" inputMode="numeric" maxLength={13} /></Field>
    <Field label="Юридический адрес"><input name="legalAddress" required maxLength={2048} /></Field>
    <Field label="Подтверждение полномочий"><textarea name="authorityEvidence" required maxLength={4096} /></Field>
  </div>
}

export function OrganizationPicker({ api, excluded = [], onSelect }) {
  const [search, setSearch] = useState('')
  const [offset, setOffset] = useState(0)
  const load = useCallback(signal => api('/transporter-organizations' + query({ search: search.trim(), offset, limit: 30 }), { signal }), [api, search, offset])
  const matches = useResource(load)
  return <>
    <Field label="Найти организацию по названию или ИНН">
      <input value={search} onChange={event => { setSearch(event.target.value); setOffset(0); onSelect?.('') }} maxLength={255} />
    </Field>
    <Resource value={matches}>{data => <>
      <Field label="Организация">
        <select required name="organizationId" defaultValue="" onChange={event => onSelect?.(event.target.value)}>
          <option value="" disabled>Выберите организацию</option>
          {data.filter(item => !excluded.includes(item.id)).map(item => <option key={item.id} value={item.id}>{item.name} · ИНН {item.inn}</option>)}
        </select>
      </Field>
      {data.length === 0 && <p className="muted">По этому запросу ничего не найдено.</p>}
      {(offset > 0 || data.length === 30) && <div className="actions">
        <button type="button" className="secondary" disabled={!offset} onClick={() => { setOffset(offset - 30); onSelect?.('') }}>Назад</button>
        <button type="button" className="secondary" disabled={data.length < 30} onClick={() => { setOffset(offset + 30); onSelect?.('') }}>Далее</button>
      </div>}
    </>}</Resource>
  </>
}

export function Login({ login, message }) {
  const action = useAction()
  return <section className="auth panel">
    <PageTitle title="Вход" />
    <Status notice={message} error={action.error} />
    <form className="stack" onSubmit={event => {
      const { username, password } = values(event)
      action.run(async () => { await login(username, password); location.hash = '#/trips' })
    }}>
      <Field label="Логин"><input name="username" autoComplete="username" required maxLength={100} /></Field>
      <Field label="Пароль"><input name="password" type="password" autoComplete="current-password" required maxLength={64} /></Field>
      <button disabled={action.busy}>Войти</button>
    </form>
    <p>Впервые здесь? <a href="#/signup">Создать аккаунт</a></p>
  </section>
}

const types = {
  passenger: 'Пассажир',
  representative: 'Представитель организации',
  employee: 'Сотрудник организации',
}

export function Signup({ publicApi, login, api, signedIn, refreshSession }) {
  const [type, setType] = useState('passenger')
  const [created, setCreated] = useState(false)
  const action = useAction()
  async function submit(event) {
    const { username, password, email, fullName, organizationId, ...organization } = values(event)
    const selected = type
    await action.run(async () => {
      let requestApi = api
      if (!signedIn) {
        if (!created) {
          await post(publicApi, '/users', { username, password, email, fullName })
          setCreated(true)
        }
        requestApi = await login(username, password)
      }
      if (selected === 'employee') {
        if (!signedIn) return
        if (!organizationId) throw new Error('Выберите организацию.')
        await post(requestApi, '/organization-memberships', { organizationId })
      }
      if (selected === 'representative') await post(requestApi, '/organization-registrations', organization)
      refreshSession()
      location.hash = selected === 'passenger' ? '#/trips' : '#/organizations'
    })
  }
  return <section className="auth panel">
    <PageTitle title="Регистрация" />
    <div className="switches" aria-label="Тип регистрации">{Object.entries(types).filter(([key]) => !signedIn || key !== 'passenger')
      .map(([key, label]) => <button type="button" className={key === type ? 'selected' : 'secondary'}
        key={key} aria-pressed={key === type} disabled={action.busy} onClick={() => setType(key)}>{label}</button>)}</div>
    {type === 'representative' && <p>Администрация проверит данные новой организации и полномочия представителя.</p>}
    {type === 'employee' && <p>Представитель выбранной организации рассмотрит заявку на вступление.</p>}
    <form className="stack" onSubmit={submit}>
      {!signedIn && <>
        <Field label="Имя и фамилия"><input name="fullName" autoComplete="name" required maxLength={255} /></Field>
        <Field label="Электронная почта"><input name="email" type="email" autoComplete="email" required maxLength={255} /></Field>
        <Field label="Логин"><input name="username" autoComplete="username" required maxLength={100} /></Field>
        <Field label="Пароль"><input name="password" type="password" autoComplete="new-password" required minLength={12} maxLength={64} /></Field>
      </>}
      {type === 'representative' && <OrganizationFields />}
      {type === 'employee' && signedIn && <OrganizationPicker api={api} />}
      {type === 'employee' && !signedIn && <p className="muted">Выбор организации откроется после создания аккаунта.</p>}
      <Status error={action.error} />
      <button disabled={action.busy}>{signedIn && type !== 'passenger' ? 'Подать заявку' : 'Продолжить'}</button>
    </form>
    {!signedIn && <p>Уже есть аккаунт? <a href="#/login">Войти</a></p>}
  </section>
}
