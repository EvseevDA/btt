import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'

const password = 'test-account-Pass-123'
const named = (page, name) => page.getByRole('button', { name, exact: true })
const status = (page, text) => page.locator('.badge').filter({ hasText: new RegExp(`^${text}$`) })

function account(prefix) {
  const nonce = randomUUID().slice(0, 8)
  return { username: `${prefix}-${nonce}`, fullName: `${prefix} ${nonce}`, email: `${prefix}-${nonce}@example.test` }
}

async function signUp(page, user, kind) {
  await page.goto('/#/signup')
  await named(page, kind).click()
  await page.getByLabel('Имя и фамилия').fill(user.fullName)
  await page.getByLabel('Электронная почта').fill(user.email)
  await page.getByLabel('Логин', { exact: true }).fill(user.username)
  await page.getByLabel('Пароль', { exact: true }).fill(password)
}

async function login(page, username, secret = password) {
  await page.goto('/#/login')
  await page.getByLabel('Логин', { exact: true }).fill(username)
  await page.getByLabel('Пароль', { exact: true }).fill(secret)
  await named(page, 'Войти').click()
  await expect(page.getByRole('heading', { name: 'Найти рейс', exact: true })).toBeVisible()
}

async function open(page, hash) {
  await page.goto('/' + hash)
}

async function requestHash(page, prefix) {
  await expect(page).toHaveURL(new RegExp(`#/${prefix}/[a-f0-9-]{36}$`))
  return new URL(page.url()).hash
}

async function reviewOrganization(page, hash) {
  await open(page, hash)
  await named(page, 'Отправить решение').click()
  await expect(status(page, 'Одобрено')).toBeVisible()
}

async function submitDraft(page) {
  await named(page, 'Сохранить черновик').click()
  const hash = await requestHash(page, 'registration-requests')
  await named(page, 'Подать заявление').click()
  await expect(status(page, 'Подано')).toBeVisible()
  return hash
}

async function approveAsAdmin(page, hash, changeDescription) {
  await open(page, hash)
  await named(page, 'Взять в работу').click()
  if (changeDescription) await page.getByLabel('Описание изменений').fill(changeDescription)
  await named(page, 'Передать на согласование').click()
  await named(page, 'Согласовать решение').click()
  await expect(status(page, 'Одобрено')).toBeVisible()
}

function timeAfter(days, hour = 0) {
  return new Date(Date.now() + (days * 24 + hour) * 3_600_000).toISOString().slice(0, 16)
}

test('registration, delegated carrier operations and passenger booking through the real portal', async ({ browser }) => {
  const adminName = process.env.BTT_E2E_ADMIN_USERNAME
  const adminPassword = process.env.BTT_E2E_ADMIN_PASSWORD
  test.skip(!adminName || !adminPassword, 'Requires the administrator seeded into the test stack')
  const contexts = await Promise.all(Array.from({ length: 5 }, () => browser.newContext({ baseURL: process.env.BTT_E2E_URL || 'http://localhost:8080', timezoneId: 'UTC' })))
  const [owner, employee, admin, executor, passenger] = await Promise.all(contexts.map(context => context.newPage()))
  contexts.forEach(context => context.setDefaultTimeout(20_000))
  const errors = []
  for (const page of [owner, employee, admin, executor, passenger]) page.on('pageerror', error => errors.push(error.message))
  try {
    const ownerAccount = account('owner')
    const employeeAccount = account('employee')
    const executorAccount = account('executor')
    const passengerAccount = account('passenger')
    const digits = String(Date.now()).slice(-8)
    const inn = `52${digits}`
    const company = `Транспорт ${digits}`
    const origin = `Отправление ${digits}`
    const destination = `Назначение ${digits}`

    await signUp(owner, ownerAccount, 'Представитель организации')
    await owner.getByLabel('Наименование организации').fill(company)
    await owner.getByLabel('ИНН', { exact: true }).fill(inn)
    await owner.getByLabel('ОГРН', { exact: true }).fill(`10252${digits}`)
    await owner.getByLabel('Юридический адрес').fill('Нижний Новгород, Вокзальная 10')
    await owner.getByLabel('Подтверждение полномочий').fill('Доверенность № 7')
    await named(owner, 'Продолжить').click()
    await expect(owner).toHaveURL(/#\/organizations$/)
    const ownerRequest = owner.getByRole('article').filter({ hasText: company }).getByRole('link', { name: 'Открыть заявку' })
    await ownerRequest.click()
    const onboarding = await requestHash(owner, 'organization-requests')
    await expect(status(owner, 'На проверке')).toBeVisible()

    await login(admin, adminName, adminPassword)
    await reviewOrganization(admin, onboarding)
    await expect(status(owner, 'Одобрено')).toBeVisible()
    await owner.getByRole('link', { name: 'Перейти к моим организациям' }).click()

    await signUp(employee, employeeAccount, 'Сотрудник организации')
    await named(employee, 'Продолжить').click()
    await employee.getByLabel('Найти организацию по названию или ИНН').fill(inn)
    await employee.getByLabel('Организация', { exact: true }).selectOption({ label: `${company} · ИНН ${inn}` })
    await named(employee, 'Подать заявку').click()
    await expect(employee).toHaveURL(/#\/organizations$/)
    await employee.getByRole('link', { name: 'Открыть заявку' }).click()
    const membership = await requestHash(employee, 'organization-requests')
    await reviewOrganization(owner, membership)
    await expect(status(employee, 'Одобрено')).toBeVisible()

    await open(owner, '#/organizations')
    await owner.getByRole('link', { name: 'Сотрудники и права' }).click()
    const employeeCard = owner.locator('form').filter({ hasText: employeeAccount.fullName })
    await employeeCard.getByRole('checkbox', { name: 'Представлять организацию' }).check()
    await employeeCard.getByRole('button', { name: 'Сохранить полномочия' }).click()
    await expect(employeeCard.getByRole('status')).toHaveText('Полномочия сохранены.')

    await open(admin, '#/admin')
    await named(admin, 'Добавить сотрудника').click()
    await admin.getByLabel('Имя и фамилия').fill(executorAccount.fullName)
    await admin.getByLabel('Электронная почта').fill(executorAccount.email)
    await admin.getByLabel('Логин сотрудника').fill(executorAccount.username)
    await admin.getByLabel('Пароль сотрудника').fill(password)
    await admin.getByRole('checkbox', { name: 'Исполнитель по заявлениям' }).check()
    await named(admin, 'Создать аккаунт сотрудника').click()
    await expect(admin.getByText('Аккаунт сотрудника создан.')).toBeVisible()

    await named(admin, 'Добавить маршрут').click()
    await admin.getByLabel('Пункт отправления').fill(origin)
    await admin.getByLabel('Пункт назначения').fill(destination)
    await admin.getByLabel('Длительность, мин').fill('60')
    await admin.getByLabel('Вид транспорта').selectOption({ label: 'Автобус' })
    await named(admin, 'Сохранить маршрут').click()
    await expect(admin.getByText('Маршрут добавлен.', { exact: false })).toBeVisible()

    await open(employee, '#/carrier')
    await employee.getByRole('link', { name: 'Первичная регистрация' }).click()
    await employee.getByLabel('Вид транспорта после регистрации').selectOption({ label: 'Автобус' })
    await named(employee, 'Добавить лицензию').click()
    await employee.getByLabel('Номер лицензии').fill(`Л-${digits}`)
    await employee.getByLabel('Дата выдачи').fill('2020-01-01')
    await employee.getByLabel('Действует до').fill('2099-01-01')
    await employee.getByLabel('Выдавший орган').fill('Транспортное управление')
    await named(employee, 'Сохранить черновик').click()
    const registration = await requestHash(employee, 'registration-requests')
    await named(employee, 'Подать заявление').click()
    await expect(status(employee, 'Подано')).toBeVisible()

    await open(admin, registration)
    await admin.getByLabel('Исполнитель').selectOption({ label: executorAccount.fullName })
    await named(admin, 'Назначить').click()
    await expect(status(admin, 'На рассмотрении')).toBeVisible()
    await login(executor, executorAccount.username)
    await open(executor, registration)
    await named(executor, 'Передать на согласование').click()
    await expect(status(executor, 'На согласовании')).toBeVisible()
    await named(admin, 'Вернуть на доработку').click()
    await admin.getByLabel('Причина возврата').fill('Уточнить основание решения')
    await named(admin, 'Отправить на доработку').click()
    await expect(status(executor, 'На доработке')).toBeVisible()
    await expect(executor.getByText('Причина возврата: Уточнить основание решения')).toBeVisible()
    await executor.getByLabel('Комментарий').fill('Основание уточнено')
    await named(executor, 'Передать на согласование').click()
    await expect(admin.getByRole('heading', { name: 'Решение · редакция 2' })).toBeVisible()
    await named(admin, 'Согласовать решение').click()
    await expect(status(employee, 'Одобрено')).toBeVisible()
    await employee.getByRole('link', { name: 'Удостоверение и история перевозчика' }).click()
    await expect(employee.getByRole('heading', { name: /Удостоверение №/ })).toBeVisible()
    await expect(employee.getByText(`Л-${digits}`, { exact: true })).toBeVisible()

    await open(employee, '#/carrier')
    await employee.getByRole('link', { name: 'Создать рейс' }).click()
    await employee.getByLabel('Маршрут', { exact: true }).selectOption({ label: `${origin} → ${destination} · 60 мин` })
    await employee.getByLabel('Отправление', { exact: true }).fill(timeAfter(2))
    await employee.getByLabel('Прибытие', { exact: true }).fill(timeAfter(2, 1))
    await employee.getByLabel('Цена билета, ₽').fill('920.50')
    await employee.getByLabel('Коды мест').fill('1A, 1B')
    await named(employee, 'Опубликовать рейс').click()
    const trip = await requestHash(employee, 'trips')

    await signUp(passenger, passengerAccount, 'Пассажир')
    await named(passenger, 'Продолжить').click()
    await expect(passenger).toHaveURL(/#\/trips$/)
    await passenger.getByLabel('Откуда').fill(origin)
    await passenger.getByLabel('Куда', { exact: true }).fill(destination)
    await named(passenger, 'Найти рейсы').click()
    await passenger.getByRole('article').filter({ hasText: origin }).getByRole('link', { name: 'Выбрать рейс' }).click()
    await passenger.getByRole('radio', { name: '1B' }).check()
    await open(owner, trip)
    await owner.getByRole('radio', { name: '1B' }).check()
    await named(owner, 'Забронировать билет').click()
    await expect(owner.getByText(/оформлен\. Место 1B/)).toBeVisible()
    await named(passenger, 'Забронировать билет').click()
    await expect(passenger.getByRole('alert')).toContainText(/место/i)
    await expect(passenger.getByRole('radio', { name: '1B' })).toBeDisabled()
    await named(passenger, 'Забронировать билет').click()
    await expect(passenger.getByText(/оформлен\. Место 1A/)).toBeVisible()
    await open(passenger, '#/tickets')
    await named(passenger, 'Отменить билет').click()
    await named(passenger, 'Подтвердить отмену').click()
    await expect(status(passenger, 'Отменён')).toBeVisible()

    await open(employee, '#/registration/new/2')
    await employee.getByLabel('Действующий перевозчик').selectOption({ label: 'Автобус' })
    await employee.getByLabel('Вид транспорта после регистрации').selectOption({ label: 'Автобус' })
    await named(employee, 'Добавить лицензию').click()
    await employee.getByLabel('Номер лицензии').fill(`Новая-${digits}`)
    await employee.getByLabel('Дата выдачи').fill('2021-01-01')
    await employee.getByLabel('Действует до').fill('2099-01-01')
    await employee.getByLabel('Выдавший орган').fill('Транспортное управление')
    const amendment = await submitDraft(employee)
    await approveAsAdmin(admin, amendment, 'Обновлена лицензия')
    await expect(status(employee, 'Одобрено')).toBeVisible()
    await employee.getByRole('link', { name: 'Удостоверение и история перевозчика' }).click()
    await expect(employee.getByText(`Новая-${digits}`, { exact: true })).toBeVisible()
    const history = employee.getByLabel('Версия удостоверения')
    await history.selectOption({ index: 1 })
    await expect(employee.getByText(`Л-${digits}`, { exact: true })).toBeVisible()

    await open(employee, '#/registration/new/3')
    await employee.getByLabel('Действующий перевозчик').selectOption({ label: 'Автобус' })
    const carrierCancellation = await submitDraft(employee)
    await approveAsAdmin(admin, carrierCancellation)
    await expect(status(employee, 'Одобрено')).toBeVisible()
    await employee.getByRole('link', { name: 'Удостоверение и история перевозчика' }).click()
    await expect(status(employee, 'Аннулировано')).toBeVisible()

    await open(employee, '#/registration/new/1')
    await employee.getByLabel('Вид транспорта после регистрации').selectOption({ label: 'Поезд' })
    const secondCarrier = await submitDraft(employee)
    await approveAsAdmin(admin, secondCarrier)
    await open(admin, '#/registration/new/4')
    await admin.getByLabel('Найти организацию по названию или ИНН').fill(inn)
    await admin.getByLabel('Организация', { exact: true }).selectOption({ label: `${company} · ИНН ${inn}` })
    await admin.getByLabel('Действующий перевозчик').selectOption({ label: 'Поезд' })
    const adminCancellation = await submitDraft(admin)
    await approveAsAdmin(admin, adminCancellation)
    await admin.getByRole('link', { name: 'Удостоверение и история перевозчика' }).click()
    await expect(status(admin, 'Аннулировано')).toBeVisible()

    await open(employee, '#/registration/new/1')
    await employee.getByLabel('Вид транспорта после регистрации').selectOption({ label: 'Водный транспорт' })
    await submitDraft(employee)
    employee.once('dialog', dialog => dialog.accept())
    await named(employee, 'Отозвать заявление').click()
    await expect(status(employee, 'Отозвано')).toBeVisible()
    expect(errors).toEqual([])
  } finally {
    await Promise.all(contexts.map(context => context.close()))
  }
})

test('mobile login errors are recoverable and the session survives page reload', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const passenger = account('mobile')
  await signUp(page, passenger, 'Пассажир')
  await named(page, 'Продолжить').click()
  await expect(page).toHaveURL(/#\/trips$/)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Найти рейс' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.evaluate(() => sessionStorage.setItem('btt-session', JSON.stringify({
    token: 'X'.repeat(43), expires: Date.now() + 60_000,
  })))
  await page.reload()
  await expect(page.getByText('Сессия истекла. Войдите снова.')).toBeVisible()
  await page.getByLabel('Логин', { exact: true }).fill(passenger.username)
  await page.getByLabel('Пароль', { exact: true }).fill('wrong-password')
  await named(page, 'Войти').click()
  await expect(page.getByRole('alert')).toContainText('Неверный логин или пароль')
  await login(page, passenger.username)
  await expect(page.getByRole('link', { name: 'Администрация' })).toHaveCount(0)
})
