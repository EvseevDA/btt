import { useCallback, useState } from 'react'
import { dateTime, listAll, post, query } from '../api'
import { Badge, Empty, Field, PageTitle, Pager, Resource, Status } from '../ui'
import { useAction, useResource } from '../hooks'
import { values } from '../api'
import { requestTypes } from '../permissions'
import { OrganizationPicker } from './Auth'

const statuses = { 0: 'Черновик', 1: 'Подано', 2: 'На рассмотрении', 3: 'Одобрено', 4: 'Отклонено',
  5: 'Отозвано', 6: 'На согласовании', 7: 'На доработке' }

const prefixes = { 1: 'registration', 2: 'amendment', 3: 'carrier-cancellation', 4: 'admin-cancellation' }
const permissionsFor = type => `transporter.${prefixes[type]}.`
const size = 15
const openRequest = item => [1, 2, 6, 7].includes(item.status)
const decisionVisible = (status, pending) => !pending && [2, 3, 4, 6, 7].includes(status)
const versionLabels = { 1: 'Текущая версия', 2: 'Архивная версия', 3: 'Аннулированная версия' }
const badgeTone = status => status === 3 ? 'green' : [1, 2, 6, 7].includes(status) ? 'amber' : ''

export function RegistrationQueue({ api, session }) {
  const [status, setStatus] = useState('')
  const [type, setType] = useState('')
  const [offset, setOffset] = useState(0)
  const load = useCallback(signal => api('/registration-requests' + query({ status, type, offset, limit: size }), { signal }), [api, status, type, offset])
  const requests = useResource(load, { poll: 4000 })
  return <section className="section">
    <h2>Заявления перевозчиков</h2>
    <div className="filter-row"><Field label="Статус заявления">
      <select value={status} onChange={event => { setStatus(event.target.value); setOffset(0) }}>
        <option value="">Все</option>{Object.entries(statuses).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
      </select>
    </Field>
      <Field label="Вид заявления"><select value={type} onChange={event => { setType(event.target.value); setOffset(0) }}>
        <option value="">Все</option>{Object.entries(requestTypes).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
      </select></Field>
    </div>
    <Resource value={requests}>{data => <>
      {!data.length && <Empty>Заявлений пока нет.</Empty>}
      <div className="results">{data.map(item => <article key={item.id} className="panel org-card">
        <div><h3>{requestTypes[item.type]}</h3><p className="muted">
          {session.organizations.find(org => org.id === item.organizationId)?.name || (item.applicantContactId === session.account.id ? 'Моё заявление' : '')}
          {' '}{dateTime(item.createdAt)}</p></div>
        <div className="actions"><Badge tone={badgeTone(item.status)}>{statuses[item.status]}</Badge>
          <a className="button secondary" href={`#/registration-requests/${item.id}`}>Открыть заявление</a></div>
      </article>)}</div>
      <Pager offset={offset} size={size} count={data.length} onChange={setOffset} />
    </>}</Resource>
  </section>
}

function CarrierOptions({ api, kinds, organization }) {
  const load = useCallback(signal => organization ? listAll(api, '/transporters', { organizationId: organization }, signal) : Promise.resolve([]), [api, organization])
  const carriers = useResource(load)
  return <Resource value={carriers}>{data => {
    const active = data.filter(item => item.status === 1)
    return <Field label="Действующий перевозчик"><select required name="transporterId" defaultValue="" key={organization}>
      <option value="" disabled>{active.length ? 'Выберите перевозчика' : 'Действующих перевозчиков нет'}</option>
      {active.map(item => <option key={item.id} value={item.id}>{kinds.find(kind => kind.id === item.transportKindId)?.name || item.transportKindId}</option>)}
    </select></Field>
  }}</Resource>
}

export function CreateRegistration({ api, session, kinds, type }) {
  const orgs = session.organizations.filter(org => org.canRepresent)
  const [organization, setOrganization] = useState(orgs.length === 1 && type !== 4 ? orgs[0].id : '')
  const [licenses, setLicenses] = useState([])
  const [nextKey, setNextKey] = useState(0)
  const action = useAction()
  const needsSnapshot = type === 1 || type === 2

  async function submit(event) {
    const form = values(event)
    const transporterData = needsSnapshot ? {
      transportKindId: form.transportKindId,
      licenses: licenses.map(key => ({
        licenseNumber: form[`number-${key}`].trim(),
        issuedAt: form[`issued-${key}`],
        expiresAt: form[`expires-${key}`],
        issuingAuthority: form[`authority-${key}`].trim(),
      })),
    } : undefined
    const result = await action.run(async () => {
      if (!organization || (type !== 1 && !form.transporterId)) throw new Error('Выберите организацию и перевозчика для заявления.')
      if (transporterData?.licenses.some(item => item.expiresAt < item.issuedAt)) {
        throw new Error('Срок окончания лицензии должен быть не раньше даты выдачи.')
      }
      return post(api, '/registration-requests', { type,
        ...(type === 1 ? { organizationId: organization } : { transporterId: form.transporterId }),
        ...(transporterData && { transporterData }),
      })
    })
    if (result) location.hash = `#/registration-requests/${result.id}`
  }

  if (!requestTypes[type] || (type === 4 && !session.permissions.includes('transporter.admin-cancellation.submit'))
      || (type !== 4 && !orgs.length)) {
    return <Empty>У вашего аккаунта нет полномочий для этого заявления.</Empty>
  }

  return <>
    <a className="back" href={type === 4 ? '#/admin' : '#/carrier'}>← К кабинету</a>
    <PageTitle title={requestTypes[type]}>Сначала сохраните черновик, затем подайте заявление из его карточки.</PageTitle>
    <form className="panel stack" onSubmit={submit}>
      {type === 4 ? <OrganizationPicker api={api} onSelect={setOrganization} /> :
        <Field label="Организация"><select value={organization} required onChange={event => setOrganization(event.target.value)}>
          <option value="" disabled>Выберите организацию</option>
          {orgs.map(org => <option key={org.id} value={org.id}>{org.name}</option>)}
        </select></Field>}
      {type !== 1 && <CarrierOptions api={api} kinds={kinds} organization={organization} />}
      {needsSnapshot && <>
        <Field label="Вид транспорта после регистрации"><select required name="transportKindId" defaultValue="">
          <option value="" disabled>Выберите вид транспорта</option>
          {kinds.map(kind => <option value={kind.id} key={kind.id}>{kind.name}</option>)}
        </select></Field>
        <div className="split"><h2>Лицензии</h2><button type="button" className="secondary" disabled={licenses.length >= 100}
          onClick={() => { setLicenses(items => [...items, nextKey]); setNextKey(key => key + 1) }}>Добавить лицензию</button></div>
        {type === 2 && <p className="muted">Укажите полный список лицензий после изменения, включая действующие лицензии без изменений.</p>}
        {licenses.map((key, index) => <fieldset key={key} className="stack">
          <legend>Лицензия {index + 1}</legend>
          <Field label="Номер лицензии"><input name={`number-${key}`} required maxLength={100} /></Field>
          <Field label="Дата выдачи"><input name={`issued-${key}`} type="date" required /></Field>
          <Field label="Действует до"><input name={`expires-${key}`} type="date" required /></Field>
          <Field label="Выдавший орган"><input name={`authority-${key}`} required maxLength={255} /></Field>
          <button type="button" className="secondary" onClick={() => setLicenses(items => items.filter(item => item !== key))}>Убрать лицензию {index + 1}</button>
        </fieldset>)}
      </>}
      <Status error={action.error} />
      <button disabled={action.busy}>Сохранить черновик</button>
    </form>
  </>
}

export function RegistrationDetails({ api, id, session, kinds }) {
  const path = `/registration-requests/${id}`
  const [queued, setQueued] = useState(false)
  const action = useAction()
  const load = useCallback(signal => api(path, { signal }), [api, path])
  const request = useResource(load, { poll: 2500, shouldPoll: openRequest })
  async function operate(suffix, body, asynchronous = true) {
    const result = await action.run(() => post(api, path + suffix, body))
    if (result) {
      setQueued(asynchronous)
      request.reload()
    }
  }
  return <>
    <a className="back" href={session.permissions.some(permission => permission.endsWith('.decide') || permission.endsWith('.approve')) ? '#/admin' : '#/carrier'}>← К заявлениям</a>
    <Status error={action.error} />
    <Resource value={request}>{item => <RegistrationContent {...{ api, item, session, kinds, queued, action, operate, path }} />}</Resource>
  </>
}

function RegistrationContent({ api, item, session, kinds, queued, action, operate, path }) {
  const organizationId = item.organizationId
  const loadOrganization = useCallback(signal => api(`/transporter-organizations/${organizationId}`, { signal }), [api, organizationId])
  const organization = useResource(loadOrganization)
  const pending = item.workflowActionPending ?? queued
  const canShowDecision = decisionVisible(item.status, pending)
  const loadDecision = useCallback(async signal => {
    if (!decisionVisible(item.status, pending)) return null
    try { return await api(path + '/decision', { signal }) }
    catch (error) { if (error.status !== 404) throw error; return null }
  }, [api, path, item.status, pending])
  const decisionResource = useResource(loadDecision)
  const decision = decisionResource.data
  const own = item.applicantContactId === session.account.id
  const rights = permissionsFor(item.type)
  const canDecide = session.permissions.includes(rights + 'decide')
  const canAssign = canDecide && session.permissions.includes(rights + 'assign')
  const canApprove = session.permissions.includes(rights + 'approve')

  return <>
    <PageTitle title={requestTypes[item.type]}>{organization.data?.name || 'Заявление перевозчика'} · {dateTime(item.createdAt)}</PageTitle>
    <section className="panel stack">
      <div className="actions"><Badge tone={badgeTone(item.status)}>{statuses[item.status]}</Badge>
        {item.executorId === session.account.id && <span className="muted">Вы исполнитель</span>}</div>
      {item.proposedData && <Snapshot data={item.proposedData} kinds={kinds} />}
      {item.transporterId && <a href={`#/certificates/${item.transporterId}`}>Удостоверение и история перевозчика</a>}
      {canShowDecision && <Resource value={decisionResource}>{current => <div><h2>Решение · редакция {current.revision}</h2>
        <p>{current.type === 1 ? 'Одобрить' : 'Отказать'}</p>
        {current.comment && <p>Комментарий: {current.comment}</p>}
        {current.changeDescription && <p>Изменения: {current.changeDescription}</p>}
        {current.returnReason && <p>Причина возврата: {current.returnReason}</p>}
      </div>}</Resource>}
      {pending && <Status notice="Действие принято. Статус обновится после обработки." />}
      {!pending && <div className="actions">
        {own && item.status === 0 && <button disabled={action.busy} onClick={() => operate('/submit', undefined, false)}>Подать заявление</button>}
        {own && [1, 2, 6, 7].includes(item.status) && <button className="secondary" disabled={action.busy} onClick={() => {
          if (window.confirm('Отозвать заявление?')) operate('/withdraw')
        }}>Отозвать заявление</button>}
        {canDecide && item.status === 1 && <button disabled={action.busy} onClick={() => operate('/claim')}>Взять в работу</button>}
      </div>}
    </section>
    {!pending && canAssign && item.status === 1 && <Assignment api={api} permission={rights + 'decide'} session={session}
      busy={action.busy} submit={executorId => operate('/assign-executor', { executorId })} />}
    {!pending && canDecide && item.executorId === session.account.id && [2, 7].includes(item.status)
      && !decisionResource.loading && !decisionResource.error &&
      <IssueDecision key={decision?.revision || 0} type={item.type} initial={decision} busy={action.busy} submit={form => operate('/decision', form)} />}
    {!pending && canApprove && item.status === 6 && decision && <ReviewDecision busy={action.busy} revision={decision.revision} submit={operate} />}
  </>
}

function Assignment({ api, permission, session, busy, submit }) {
  const load = useCallback(signal => api('/staff' + query({ permission }), { signal }), [api, permission])
  const staff = useResource(load)
  return <section className="panel section">
    <h2>Назначение исполнителя</h2>
    <Resource value={staff}>{data => {
      const others = data.filter(member => member.id !== session.account.id)
      return others.length ? <form className="stack" onSubmit={event => submit(values(event).executorId)}>
        <Field label="Исполнитель"><select name="executorId" required defaultValue="">
          <option value="" disabled>Выберите сотрудника</option>
          {others.map(member => <option key={member.id} value={member.id}>{member.fullName}</option>)}
        </select></Field><button disabled={busy}>Назначить</button>
      </form> : <p className="muted">Других сотрудников с правом рассмотрения пока нет.</p>
    }}</Resource>
  </section>
}

function IssueDecision({ type, initial, busy, submit }) {
  const [result, setResult] = useState(String(initial?.type || 1))
  return <section className="panel section">
    <h2>Проект решения</h2>
    <form className="stack" onSubmit={event => {
      const { comment, changeDescription } = values(event)
      submit({ type: Number(result), comment: comment.trim(), ...(changeDescription && { changeDescription: changeDescription.trim() }) })
    }}>
      <Field label="Предлагаемое решение"><select value={result} onChange={event => setResult(event.target.value)}>
        <option value="1">Одобрить</option><option value="2">Отказать</option>
      </select></Field>
      <Field label="Комментарий"><textarea name="comment" defaultValue={initial?.comment || ''} maxLength={2048} /></Field>
      {type === 2 && result === '1' && <Field label="Описание изменений">
        <textarea name="changeDescription" defaultValue={initial?.changeDescription || ''} maxLength={2048} />
      </Field>}
      <button disabled={busy}>Передать на согласование</button>
    </form>
  </section>
}

function ReviewDecision({ busy, revision, submit }) {
  const [returning, setReturning] = useState(false)
  return <section className="panel section stack">
    <h2>Согласование решения</h2>
    {!returning ? <div className="actions">
      <button disabled={busy} onClick={() => submit('/approve', { revision })}>Согласовать решение</button>
      <button className="secondary" disabled={busy} onClick={() => setReturning(true)}>Вернуть на доработку</button>
    </div> : <form className="stack" onSubmit={event => submit('/return', { revision, reason: values(event).reason.trim() })}>
      <Field label="Причина возврата"><textarea name="reason" required maxLength={2048} /></Field>
      <div className="actions">
        <button disabled={busy}>Отправить на доработку</button>
        <button type="button" className="secondary" onClick={() => setReturning(false)}>Отмена</button>
      </div>
    </form>}
  </section>
}

export function Snapshot({ data, kinds }) {
  return <div><p>Вид транспорта: <strong>{kinds.find(kind => kind.id === data.transportKindId)?.name || '—'}</strong></p>
    <h3>Лицензии</h3>
    {!data.licenses?.length ? <p className="muted">Лицензий нет.</p> : <ul className="license-list">{data.licenses.map(license =>
      <li key={license.id || license.licenseNumber}><strong>{license.licenseNumber}</strong> · {license.issuingAuthority}
        <br /><span className="muted">{license.issuedAt} — {license.expiresAt}</span></li>)}</ul>}
  </div>
}

export function Certificate({ api, id, kinds }) {
  const [selected, setSelected] = useState('')
  const load = useCallback(async signal => {
    const certificate = await api(`/transporters/${id}/registration-certificate`, { signal })
    const versions = await listAll(api, `/registration-certificates/${certificate.id}/versions`, {}, signal)
    return { certificate, versions }
  }, [api, id])
  const result = useResource(load)
  return <>
    <a className="back" href="#/carrier">← К кабинету перевозчика</a>
    <Resource value={result}>{({ certificate, versions }) => <>
      <PageTitle title={`Удостоверение № ${certificate.certificateNumber}`}>История версий перевозчика и лицензий.</PageTitle>
      <div className="panel stack"><div className="actions"><Badge tone={certificate.status === 1 ? 'green' : ''}>
        {certificate.status === 1 ? 'Действует' : 'Аннулировано'}</Badge><span>Оформлено {dateTime(certificate.createdAt)}</span></div>
        {!!versions.length && <Field label="Версия удостоверения"><select value={selected || certificate.currentVersionId || versions[0].id}
          onChange={event => setSelected(event.target.value)}>
          {versions.map(version => <option key={version.id} value={version.id}>Версия {version.versionNumber} · {dateTime(version.createdAt)}</option>)}
        </select></Field>}
        <CertificateVersion api={api} carrier={id} version={versions.find(version =>
          version.id === (selected || certificate.currentVersionId)) || versions[0]} kinds={kinds} />
      </div>
    </>}</Resource>
  </>
}

function CertificateVersion({ api, carrier, version, kinds }) {
  const id = version?.transporterVersionId
  const load = useCallback(signal => id ? api(`/transporters/${carrier}/versions/${id}`, { signal }) : Promise.resolve(null), [api, carrier, id])
  const snapshot = useResource(load)
  return version && <>
    <div><Badge tone={version.status === 1 ? 'green' : ''}>{versionLabels[version.status] || 'Версия'}</Badge>
      {version.changeDescription && <p>{version.changeDescription}</p>}</div>
    <Resource value={snapshot}>{data => <Snapshot data={data} kinds={kinds} />}</Resource>
  </>
}
