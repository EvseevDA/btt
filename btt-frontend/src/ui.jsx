import { cloneElement, useId } from 'react'

export function Status({ error, notice }) {
  return <>
    {error && <p className="message error" role="alert">{error.message || error}</p>}
    {notice && <p className="message notice" role="status">{notice}</p>}
  </>
}

export function Resource({ value, children }) {
  if (value.loading) return <p role="status" className="muted">Загрузка…</p>
  return <>
    {value.error && <div className="resource-error"><Status error={value.error} />
      <button type="button" className="secondary" onClick={value.reload}>Повторить</button></div>}
    {value.data != null && children(value.data)}
  </>
}

export function Field({ label, children }) {
  const id = useId()
  return <div className="field"><label htmlFor={id}>{label}</label>{cloneElement(children, { id })}</div>
}

export function Empty({ children }) {
  return <div className="empty">{children}</div>
}

export function PageTitle({ title, children }) {
  return <header className="page-title"><h1>{title}</h1>{children && <p>{children}</p>}</header>
}

export function Badge({ children, tone = '' }) {
  return <span className={`badge ${tone}`}>{children}</span>
}

export function Pager({ offset, size, count, onChange }) {
  if (!offset && count < size) return null
  return <nav className="pager" aria-label="Страницы">
    <button className="secondary" disabled={!offset} onClick={() => onChange(Math.max(0, offset - size))}>Назад</button>
    <span>Страница {Math.floor(offset / size) + 1}</span>
    <button className="secondary" disabled={count < size} onClick={() => onChange(offset + size)}>Далее</button>
  </nav>
}
