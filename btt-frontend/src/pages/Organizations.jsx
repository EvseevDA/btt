import { useCallback, useState } from 'react'
import { dateTime, post, query } from '../api'
import { Badge, Empty, Field, PageTitle, Pager, Resource, Status } from '../ui'
import { useAction, useResource } from '../hooks'
import { values } from '../api'
import { OrganizationFields, OrganizationPicker } from './Auth'

const size = 15
const labels = { 1: 'На проверке', 2: 'Одобрено', 3: 'Отклонено' }
const pendingReview = ({ item }) => item.status === 1

export function Organizations({ api, session }) {
  const [form, setForm] = useState('')
  const action = useAction()
  return <>
    <PageTitle title="Организации">Подключение организации, заявки сотрудников и полномочия.</PageTitle>
    <div className="actions">
      <button className="secondary" aria-expanded={form === 'register'} onClick={() => setForm(form === 'register' ? '' : 'register')}>Подключить организацию</button>
      <button className="secondary" aria-expanded={form === 'join'} onClick={() => setForm(form === 'join' ? '' : 'join')}>Вступить в организацию</button>
    </div>
    <Status error={action.error} notice={action.notice} />
    {form && <section className="panel section">
      <h2>{form === 'register' ? 'Подключение организации' : 'Вступление в организацию'}</h2>
      <form className="stack" onSubmit={async event => {
        const data = values(event)
        if (form === 'join' && !data.organizationId) return
        const result = await action.run(() => post(api, form === 'register' ? '/organization-registrations' : '/organization-memberships', data), 'Заявка подана.')
        if (result) {
          setForm('')
          location.hash = `#/organization-requests/${result.id}`
        }
      }}>
        {form === 'register' ? <OrganizationFields /> : <OrganizationPicker api={api} excluded={session.organizations.map(org => org.id)} />}
        <button disabled={action.busy}>Подать заявку</button>
      </form>
    </section>}
    <section className="section"><h2>Мои организации</h2>
      {!session.organizations.length && <Empty>У вас пока нет подключённых организаций.</Empty>}
      {session.organizations.map(org => <article className="panel org-card" key={org.id}>
        <div><h3>{org.name}</h3><p className="muted">{org.canRepresent ? 'Представитель организации' : 'Сотрудник организации'}</p></div>
        {org.canConfirmMembers && <a className="button secondary" href={`#/organizations/${org.id}/members`}>Сотрудники и права</a>}
      </article>)}
    </section>
    <OrganizationQueue api={api} session={session} />
  </>
}

export function OrganizationQueue({ api, session, admin = false }) {
  const [status, setStatus] = useState(admin ? '1' : '')
  const [offset, setOffset] = useState(0)
  const load = useCallback(signal => api('/organization-requests' + query({ status, type: admin ? 1 : undefined, limit: size, offset }), { signal }),
    [api, status, admin, offset])
  const requests = useResource(load, { poll: 4000 })
  return <section className="section">
    <h2>{admin ? 'Подключение организаций' : 'Заявки организаций'}</h2>
    <div className="filter-row"><Field label="Статус заявки">
      <select value={status} onChange={event => { setStatus(event.target.value); setOffset(0) }}>
        <option value="">Все</option>{Object.entries(labels).map(([key, label]) => <option value={key} key={key}>{label}</option>)}
      </select>
    </Field></div>
    <Resource value={requests}>{data => <>
      {!data.length && <Empty>Заявок пока нет.</Empty>}
      <div className="results">{data.map(item => <article className="panel org-card" key={item.id}>
        <div><h3>{item.organizationData?.name || session.organizations.find(org => org.id === item.organizationId)?.name || 'Заявка на вступление'}</h3>
          <p className="muted">{item.type === 1 ? 'Подключение' : 'Вступление'} · {item.applicantId === session.account.id ? 'Моя заявка · ' : ''}{dateTime(item.createdAt)}</p>
        </div>
        <div className="actions"><Badge tone={item.status === 2 ? 'green' : item.status === 1 ? 'amber' : ''}>{labels[item.status]}</Badge>
          <a className="button secondary" href={`#/organization-requests/${item.id}`}>Открыть заявку</a></div>
      </article>)}</div>
      <Pager offset={offset} size={size} count={data.length} onChange={setOffset} />
    </>}</Resource>
  </section>
}

export function OrganizationRequest({ api, session, id, refreshSession }) {
  const action = useAction()
  const [submitted, setSubmitted] = useState(false)
  const path = `/organization-requests/${id}`
  const load = useCallback(async signal => {
    const item = await api(path, { signal })
    const org = item.organizationId ? await api(`/transporter-organizations/${item.organizationId}`, { signal }) : null
    return { item, org }
  }, [api, path])
  const request = useResource(load, { poll: 2500, shouldPoll: pendingReview })
  return <>
    <a className="back" href="#/organizations">← К организациям</a>
    <Resource value={request}>{({ item, org }) => {
      const review = item.applicantId !== session.account.id && (item.type === 1
        ? session.permissions.includes('organization.registration.decide')
        : session.organizations.some(member => member.id === item.organizationId && member.canConfirmMembers))
      return <>
        <PageTitle title={item.type === 1 ? 'Подключение организации' : 'Вступление в организацию'}>
          {item.organizationData?.name || org?.name}
        </PageTitle>
        <div className="panel stack">
          <div className="actions"><Badge tone={item.status === 2 ? 'green' : item.status === 1 ? 'amber' : ''}>{labels[item.status]}</Badge>
            <span className="muted">Подано {dateTime(item.createdAt)}</span></div>
          {item.organizationData && <dl className="facts">
            <div><dt>ИНН</dt><dd>{item.organizationData.inn}</dd></div>
            <div><dt>ОГРН</dt><dd>{item.organizationData.ogrn}</dd></div>
            <div><dt>Адрес</dt><dd>{item.organizationData.legalAddress}</dd></div>
            <div><dt>Подтверждение полномочий</dt><dd>{item.organizationData.authorityEvidence}</dd></div>
          </dl>}
          {item.decisionComment && <p>Комментарий: {item.decisionComment}</p>}
          {item.status === 1 && (item.decidedById || submitted) && <Status notice="Решение принято к обработке. Статус обновится автоматически." />}
          {item.status === 2 && item.applicantId === session.account.id &&
            <a href="#/organizations" onClick={refreshSession}>Перейти к моим организациям</a>}
        </div>
        {review && item.status === 1 && !item.decidedById && !submitted && <section className="panel section">
          <h2>Решение по заявке</h2>
          <ReviewOrganization action={action} submit={async data => {
            const result = await action.run(() => post(api, path + '/decision', data))
            if (result) { setSubmitted(true); request.reload() }
          }} />
        </section>}
      </>
    }}</Resource>
  </>
}

function ReviewOrganization({ action, submit }) {
  const [approved, setApproved] = useState(true)
  return <form className="stack" onSubmit={event => {
    const { comment } = values(event)
    submit({ approved, ...(comment.trim() ? { comment: comment.trim() } : {}) })
  }}>
    <Field label="Результат проверки"><select value={String(approved)} onChange={event => setApproved(event.target.value === 'true')}>
      <option value="true">Одобрить</option><option value="false">Отклонить</option>
    </select></Field>
    <Field label={approved ? 'Комментарий' : 'Причина отказа'}>
      <textarea name="comment" maxLength={2048} required={!approved} />
    </Field>
    <Status error={action.error} />
    <button disabled={action.busy}>Отправить решение</button>
  </form>
}

export function Members({ api, session, id }) {
  const organization = session.organizations.find(item => item.id === id)
  const load = useCallback(signal => api(`/transporter-organizations/${id}/members`, { signal }), [api, id])
  const members = useResource(load)
  return <>
    <a className="back" href="#/organizations">← К организациям</a>
    <PageTitle title="Сотрудники и права">{organization?.name}</PageTitle>
    <Resource value={members}>{data => <div className="results">{data.map(member => <Member key={member.account.id} member={member}
      own={member.account.id === session.account.id} api={api} organization={id} />)}</div>}</Resource>
  </>
}

function Member({ member, own, api, organization }) {
  const action = useAction()
  return <form className="panel stack" onSubmit={event => {
    const form = values(event)
    action.run(() => api(`/transporter-organizations/${organization}/members/${member.account.id}/permissions`, {
      method: 'PUT', body: { canRepresent: form.canRepresent === 'on', canConfirmMembers: form.canConfirmMembers === 'on' },
    }), 'Полномочия сохранены.')
  }}>
    <h3>{member.account.fullName}{own ? ' · Вы' : ''}</h3>
    <label className="check"><input type="checkbox" name="canRepresent" defaultChecked={member.canRepresent} disabled={own || action.busy} /> Представлять организацию</label>
    <label className="check"><input type="checkbox" name="canConfirmMembers" defaultChecked={member.canConfirmMembers} disabled={own || action.busy} /> Подтверждать участников</label>
    <Status error={action.error} notice={action.notice} />
    {!own && <button disabled={action.busy}>Сохранить полномочия</button>}
  </form>
}
