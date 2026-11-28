export const requestTypes = {
  1: 'Первичная регистрация',
  2: 'Внесение изменений',
  3: 'Отмена по инициативе перевозчика',
  4: 'Отмена по инициативе администрации',
}

export function canAdmin(permissions) {
  return permissions.some(permission => permission.startsWith('transporter.') ||
    ['account.staff.create', 'organization.registration.decide', 'catalog.routes.create'].includes(permission))
}
