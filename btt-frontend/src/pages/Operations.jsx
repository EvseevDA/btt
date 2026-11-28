import { useCallback, useState } from 'react'
import { isoLocal, listAll, post } from '../api'
import { Badge, Empty, Field, PageTitle, Resource, Status } from '../ui'
import { useAction, useResource } from '../hooks'
import { values } from '../api'
import { OrganizationQueue } from './Organizations'
import { RegistrationQueue } from './Registration'
import { canAdmin, requestTypes } from '../permissions'

export function Carrier({ api, kinds, session }) {
  const organizationIds = session.organizations.map(org => org.id).join(',')
  const load = useCallback(async signal => {
    if (!organizationIds) return []
    const results = await Promise.all(organizationIds.split(',').map(id => listAll(api, '/transporters', { organizationId: id }, signal)))
    return results.flat()
  }, [api, organizationIds])
  const carriers = useResource(load, { poll: 6000 })
  return <>
    <PageTitle title="Кабинет перевозчика">Регистрация, удостоверения и планирование рейсов.</PageTitle>
    {session.organizations.some(org => org.canRepresent) && <div className="actions">
      {[1, 2, 3].map(type => <a key={type} className="button secondary" href={`#/registration/new/${type}`}>{requestTypes[type]}</a>)}
    </div>}
    <section className="section"><h2>Перевозчики моих организаций</h2>
      <Resource value={carriers}>{data => <>
        {!data.length && <Empty>Перевозчиков пока нет. Для подачи заявления нужны полномочия представителя организации. <a href="#/organizations">Перейти к организациям</a></Empty>}
        <div className="results">{data.map(carrier => {
          const org = session.organizations.find(item => item.id === carrier.organizationId)
          return <article className="panel org-card" key={carrier.id}>
            <div><h3>{org?.name}</h3><p className="muted">{kinds.find(kind => kind.id === carrier.transportKindId)?.name}</p></div>
            <div className="actions"><Badge tone={carrier.status === 1 ? 'green' : ''}>{carrier.status === 1 ? 'Действует' : 'Не действует'}</Badge>
              <a className="button secondary" href={`#/certificates/${carrier.id}`}>Удостоверение</a>
              {carrier.status === 1 && org?.canRepresent && <a className="button" href={`#/schedule/${carrier.id}`}>Создать рейс</a>}
            </div>
          </article>
        })}</div>
      </>}</Resource>
    </section>
    <RegistrationQueue api={api} session={session} />
  </>
}

export function Schedule({ api, id, session, kinds }) {
  const load = useCallback(async signal => {
    const carrier = await api(`/transporters/${id}`, { signal })
    const routes = await listAll(api, '/routes', { transportKindId: carrier.transportKindId }, signal)
    return { carrier, routes }
  }, [api, id])
  const schedule = useResource(load)
  return <>
    <a className="back" href="#/carrier">← К кабинету перевозчика</a>
    <Resource value={schedule}>{({ carrier, routes }) => {
      const org = session.organizations.find(item => item.id === carrier.organizationId)
      const kind = kinds.find(item => item.id === carrier.transportKindId)?.name
      return <>
        <PageTitle title="Создать рейс">{org?.name} · {kind}</PageTitle>
        {!org?.canRepresent || carrier.status !== 1 ? <Empty>Создание рейсов доступно представителям действующего перевозчика.</Empty>
          : !routes.length ? <Empty>Для этого вида транспорта пока нет маршрутов. Маршруты добавляет администратор портала.</Empty>
            : <ScheduleForm api={api} routes={routes} carrier={id} />}
      </>
    }}</Resource>
  </>
}

function ScheduleForm({ api, routes, carrier }) {
  const action = useAction()
  async function create(event) {
    const form = values(event)
    const result = await action.run(async () => {
      const seatCodes = form.seatCodes.split(/[\s,;]+/).filter(Boolean)
      const departureAt = isoLocal(form.departureAt)
      const arrivalAt = isoLocal(form.arrivalAt)
      if (new Date(departureAt) <= new Date() || arrivalAt <= departureAt) {
        throw new Error('Время отправления должно быть в будущем, а прибытие позже отправления.')
      }
      if (!seatCodes.length || seatCodes.length > 1000 || new Set(seatCodes).size !== seatCodes.length || seatCodes.some(code => code.length > 20)) {
        throw new Error('Укажите от 1 до 1000 уникальных кодов мест длиной до 20 символов.')
      }
      return post(api, '/trips', { transporterId: carrier, routeId: form.routeId, departureAt, arrivalAt,
        basePrice: Number(form.basePrice), seatCodes })
    })
    if (result) location.hash = `#/trips/${result.id}`
  }
  return <form className="panel stack" onSubmit={create}>
    <Field label="Маршрут"><select name="routeId" required defaultValue="">
      <option value="" disabled>Выберите маршрут</option>
      {routes.map(route => <option value={route.id} key={route.id}>{route.source} → {route.dest} · {route.duration} мин</option>)}
    </select></Field>
    <div className="fields">
      <Field label="Отправление"><input type="datetime-local" required name="departureAt" /></Field>
      <Field label="Прибытие"><input type="datetime-local" required name="arrivalAt" /></Field>
    </div>
    <p className="muted">Дата и время указываются в часовом поясе устройства.</p>
    <Field label="Цена билета, ₽"><input type="number" min="0" max="9999999999.99" step="0.01" required name="basePrice" /></Field>
    <Field label="Коды мест"><textarea name="seatCodes" required maxLength={22000} placeholder="Например: 1A, 1B, 2A, 2B" /></Field>
    <Status error={action.error} />
    <button disabled={action.busy}>Опубликовать рейс</button>
  </form>
}

const roles = {
  PORTAL_ADMIN: 'Администратор портала',
  REGISTRATION_MANAGER: 'Начальник отдела рассмотрения заявлений',
  REGISTRATION_EXECUTOR: 'Исполнитель по заявлениям',
  REGISTRATION_APPROVER: 'Согласующий решения',
}

export function Admin({ api, session, kinds }) {
  const [form, setForm] = useState('')
  const { permissions } = session
  if (!canAdmin(permissions)) return <Empty>Этот раздел доступен сотрудникам администрации.</Empty>
  return <>
    <PageTitle title="Администрация">Сотрудники, справочники и рассмотрение заявлений.</PageTitle>
    <div className="actions">
      {permissions.includes('account.staff.create') && <button className="secondary" aria-expanded={form === 'staff'} onClick={() =>
        setForm(form === 'staff' ? '' : 'staff')}>Добавить сотрудника</button>}
      {permissions.includes('catalog.routes.create') && <button className="secondary" aria-expanded={form === 'route'} onClick={() =>
        setForm(form === 'route' ? '' : 'route')}>Добавить маршрут</button>}
      {permissions.includes('transporter.admin-cancellation.submit') && <a className="button secondary" href="#/registration/new/4">Подать заявление на отмену регистрации</a>}
    </div>
    {form === 'staff' && <StaffForm api={api} />}
    {form === 'route' && <RouteForm api={api} kinds={kinds} />}
    {permissions.includes('organization.registration.decide') && <OrganizationQueue api={api} session={session} admin />}
    {permissions.some(permission => permission.startsWith('transporter.')) && <RegistrationQueue api={api} session={session} />}
  </>
}

function StaffForm({ api }) {
  const action = useAction()
  return <section className="panel section">
    <h2>Новый сотрудник</h2>
    <form className="stack" onSubmit={event => {
      const form = event.currentTarget
      const data = new FormData(form)
      event.preventDefault()
      action.run(async () => {
        const roleNames = data.getAll('roleNames')
        if (!roleNames.length) throw new Error('Выберите хотя бы одну роль сотрудника.')
        const account = await post(api, '/users', { fullName: data.get('fullName'), username: data.get('username'),
          email: data.get('email'), password: data.get('password'), roleNames })
        form.reset()
        return account
      }, 'Аккаунт сотрудника создан.')
    }}>
      <div className="fields">
        <Field label="Имя и фамилия"><input name="fullName" required maxLength={255} /></Field>
        <Field label="Электронная почта"><input name="email" type="email" required maxLength={255} /></Field>
        <Field label="Логин сотрудника"><input name="username" required maxLength={100} autoComplete="off" /></Field>
        <Field label="Пароль сотрудника"><input name="password" type="password" required minLength={12} maxLength={64} autoComplete="new-password" /></Field>
      </div>
      <fieldset className="stack"><legend>Роли</legend>
        {Object.entries(roles).map(([key, label]) => <label key={key} className="check"><input type="checkbox" name="roleNames" value={key} /> {label}</label>)}
      </fieldset>
      <Status error={action.error} notice={action.notice} />
      <button disabled={action.busy}>Создать аккаунт сотрудника</button>
    </form>
  </section>
}

function RouteForm({ api, kinds }) {
  const action = useAction()
  return <section className="panel section">
    <h2>Новый маршрут</h2>
    <form className="stack" onSubmit={event => {
      const form = event.currentTarget
      const data = values(event)
      action.run(async () => {
        const route = await post(api, '/routes', { ...data, source: data.source.trim(), dest: data.dest.trim(), duration: Number(data.duration) })
        form.reset()
        return route
      }, 'Маршрут добавлен. Перевозчики могут выбрать его при создании рейса.')
    }}>
      <div className="fields">
        <Field label="Пункт отправления"><input name="source" required maxLength={255} /></Field>
        <Field label="Пункт назначения"><input name="dest" required maxLength={255} /></Field>
        <Field label="Длительность, мин"><input name="duration" type="number" required min="1" max="2147483647" /></Field>
        <Field label="Вид транспорта"><select name="transportKindId" required defaultValue="">
          <option value="" disabled>Выберите вид транспорта</option>
          {kinds.map(item => <option value={item.id} key={item.id}>{item.name}</option>)}
        </select></Field>
      </div>
      <Status error={action.error} notice={action.notice} />
      <button disabled={action.busy}>Сохранить маршрут</button>
    </form>
  </section>
}
