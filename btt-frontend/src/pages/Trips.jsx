import { useCallback, useEffect, useState } from 'react'
import { dateTime, isoLocal, money, parseUtc, post, query } from '../api'
import { Badge, Empty, Field, PageTitle, Pager, Resource, Status } from '../ui'
import { useAction, useResource } from '../hooks'
import { values } from '../api'

const pageSize = 12

async function byIds(api, path, ids, signal) {
  const entries = await Promise.all([...new Set(ids)].map(async id => [id, await api(`${path}/${id}`, { signal })]))
  return Object.fromEntries(entries)
}

async function tripLabels(api, trips, signal) {
  const [routes, carriers] = await Promise.all([
    byIds(api, '/routes', trips.map(trip => trip.routeId), signal),
    byIds(api, '/transporters', trips.map(trip => trip.transporterId), signal),
  ])
  const [organizations, kinds] = await Promise.all([
    byIds(api, '/transporter-organizations', Object.values(carriers).map(carrier => carrier.organizationId), signal),
    byIds(api, '/transport-kinds', Object.values(routes).map(route => route.transportKindId), signal),
  ])
  return Object.fromEntries(trips.map(trip => {
    const route = routes[trip.routeId]
    return [trip.id, {
      route: `${route.source} → ${route.dest}`,
      kind: kinds[route.transportKindId].name,
      carrier: organizations[carriers[trip.transporterId].organizationId].name,
    }]
  }))
}

export function TripSearch({ api, kinds }) {
  const [filters, setFilters] = useState({ bookableOnly: true })
  const [offset, setOffset] = useState(0)
  const [validation, setValidation] = useState('')
  const load = useCallback(async signal => {
    const rows = await api('/trips' + query({ ...filters, offset, limit: pageSize }), { signal })
    return { rows, labels: await tripLabels(api, rows, signal) }
  }, [api, filters, offset])
  const trips = useResource(load)
  function search(event) {
    const form = values(event)
    const from = isoLocal(form.departureFrom)
    const until = isoLocal(form.departureUntil)
    if (from && until && from >= until) {
      setValidation('Конец интервала должен быть позже начала.')
      return
    }
    setValidation('')
    setOffset(0)
    setFilters({
      ...form, source: form.source.trim(), destination: form.destination.trim(), carrier: form.carrier.trim(),
      departureFrom: from, departureUntil: until, bookableOnly: form.bookableOnly === 'on',
    })
  }
  return <>
    <section className="trip-hero"><PageTitle title="Найти рейс">Выберите направление и время поездки.</PageTitle>
      <form onSubmit={search} className="search-form">
        <Field label="Откуда"><input name="source" placeholder="Пункт отправления" maxLength={255} /></Field>
        <Field label="Куда"><input name="destination" placeholder="Пункт назначения" maxLength={255} /></Field>
        <Field label="Отправление с"><input name="departureFrom" type="datetime-local" /></Field>
        <Field label="Отправление до"><input name="departureUntil" type="datetime-local" /></Field>
        <Field label="Вид транспорта"><select name="transportKindId"><option value="">Любой</option>
          {kinds.map(kind => <option key={kind.id} value={kind.id}>{kind.name}</option>)}</select></Field>
        <Field label="Перевозчик"><input name="carrier" placeholder="Название организации" maxLength={255} /></Field>
        <Field label="Максимальная цена, ₽"><input name="maxPrice" type="number" min="0" step="0.01" /></Field>
        <label className="check search-check"><input type="checkbox" name="bookableOnly" defaultChecked /> Только со свободными местами</label>
        <button>Найти рейсы</button>
        <Status error={validation} />
      </form>
    </section>
    <section className="section"><h2>Доступные рейсы</h2>
      <Resource value={trips}>{({ rows, labels }) => <>
        {!rows.length && <Empty>Рейсы не найдены. Попробуйте изменить условия поиска.</Empty>}
        <div className="results">{rows.map(trip => <article className="panel trip-card" key={trip.id}>
          <div><p className="muted metadata">{labels[trip.id].kind} · {labels[trip.id].carrier}</p>
            <h3>{labels[trip.id].route}</h3>
            <p className="trip-time">{dateTime(trip.departureAt)} → {dateTime(trip.arrivalAt)}</p></div>
          <div className="trip-card-action"><strong>{money(trip.basePrice)}</strong>
            <span className="muted">Свободных мест: {trip.availableSeats ?? 0}</span>
            <a className="button secondary" href={`#/trips/${trip.id}`}>Выбрать рейс</a></div>
        </article>)}</div>
        <Pager count={rows.length} offset={offset} size={pageSize} onChange={setOffset} />
      </>}</Resource>
    </section>
  </>
}

export function TripDetails({ api, id }) {
  const [seat, setSeat] = useState('')
  const [booked, setBooked] = useState(null)
  const action = useAction()
  const load = useCallback(async signal => {
    const [trip, seats] = await Promise.all([api(`/trips/${id}`, { signal }), api(`/trips/${id}/seats`, { signal })])
    return { trip, seats, label: (await tripLabels(api, [trip], signal))[id] }
  }, [api, id])
  const details = useResource(load)
  async function book() {
    await action.run(async () => {
      try {
        const ticket = await post(api, '/tickets', { tripId: id, ...(seat ? { seatCode: seat } : {}) })
        setBooked(ticket)
        details.reload()
      } catch (error) {
        if (error.status === 409) { setSeat(''); details.reload() }
        throw error
      }
    })
  }
  return <>
    <a className="back" href="#/trips">← К поиску рейсов</a>
    <Status error={action.error} />
    {booked && <div className="message notice" role="status">Билет {booked.ticketNumber} оформлен. Место {booked.seatCode}. <a href="#/tickets">Мои билеты</a></div>}
    <Resource value={details}>{({ trip, seats, label }) => <>
      <PageTitle title={label.route}>{label.kind} · {label.carrier}</PageTitle>
      <div className="panel stack">
        <dl className="facts"><div><dt>Отправление</dt><dd>{dateTime(trip.departureAt)}</dd></div>
          <div><dt>Прибытие</dt><dd>{dateTime(trip.arrivalAt)}</dd></div><div><dt>Стоимость</dt><dd>{money(trip.basePrice)}</dd></div></dl>
        {!booked && <h2>Места</h2>}
        {seats.some(item => item.available) && !booked ? <form className="stack" onSubmit={event => { event.preventDefault(); book() }}>
          <label className="check"><input type="radio" name="seat" checked={!seat} onChange={() => setSeat('')} /> Подобрать свободное место автоматически</label>
          <div className="seat-map" aria-label="Места рейса">
            {seats.map(item => <label key={item.seatCode} className={`seat ${item.available ? '' : 'taken'}`}>
              <input type="radio" name="seat" disabled={!item.available} value={item.seatCode} checked={seat === item.seatCode}
                onChange={() => setSeat(item.seatCode)} /><span>{item.seatCode}</span>
            </label>)}
          </div>
          <button disabled={action.busy}>Забронировать билет</button>
        </form> : !booked && <Empty>На этот рейс сейчас нет доступных мест.</Empty>}
      </div>
    </>}</Resource>
  </>
}

export function Tickets({ api }) {
  const [offset, setOffset] = useState(0)
  const load = useCallback(async signal => {
    const rows = await api('/tickets' + query({ offset, limit: pageSize }), { signal })
    const allTrips = Object.values(await byIds(api, '/trips', rows.map(ticket => ticket.tripId), signal))
    return { rows, trips: Object.fromEntries(allTrips.map(trip => [trip.id, trip])), labels: await tripLabels(api, allTrips, signal) }
  }, [api, offset])
  const tickets = useResource(load)
  return <>
    <PageTitle title="Мои билеты" />
    <Resource value={tickets}>{({ rows, trips, labels }) => <>
      {!rows.length && <Empty>Здесь появятся ваши билеты. <a href="#/trips">Найти рейс</a></Empty>}
      <div className="results">{rows.map(ticket => <TicketCard key={ticket.id} ticket={ticket} trip={trips[ticket.tripId]}
        label={labels[ticket.tripId]} api={api} reload={tickets.reload} />)}</div>
      <Pager count={rows.length} offset={offset} size={pageSize} onChange={setOffset} />
    </>}</Resource>
  </>
}

function TicketCard({ ticket, trip, label, api, reload }) {
  const action = useAction()
  const [confirm, setConfirm] = useState(false)
  const [departed, setDeparted] = useState(() => parseUtc(trip.departureAt) <= new Date())
  useEffect(() => {
    const remaining = parseUtc(trip.departureAt).getTime() - Date.now()
    if (remaining <= 2_147_483_647) {
      const timer = setTimeout(() => setDeparted(true), Math.max(0, remaining))
      return () => clearTimeout(timer)
    }
  }, [trip.departureAt])
  const canCancel = ticket.status === 1 && trip.status === 1 && !departed
  return <article className="panel trip-card">
    <div><p className="muted metadata">Билет {ticket.ticketNumber}</p><h3>{label.route}</h3>
      <p>{dateTime(trip.departureAt)} · Место {ticket.seatCode || '—'} · {money(ticket.price)}</p>
      <Status error={action.error} />
      {confirm && <div className="actions"><span>Отменить билет?</span>
        <button className="danger" disabled={action.busy} onClick={async () => {
          const result = await action.run(() => post(api, `/tickets/${ticket.id}/cancel`))
          if (result) reload()
        }}>Подтвердить отмену</button>
        <button className="secondary" disabled={action.busy} onClick={() => setConfirm(false)}>Оставить билет</button>
      </div>}
    </div>
    <div className="trip-card-action"><Badge tone={ticket.status === 1 ? 'green' : ''}>
      {ticket.status === 1 ? 'Забронирован' : 'Отменён'}</Badge>
      {canCancel && !confirm && <button className="secondary" onClick={() => setConfirm(true)}>Отменить билет</button>}
    </div>
  </article>
}
