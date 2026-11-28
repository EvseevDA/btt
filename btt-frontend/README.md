# Фронтенд BTT

React и Vite. Для разработки запустите бэкенд BTT, затем выполните в этом каталоге команды ниже. Нужен Node.js 22.12 или 24.

```sh
npm ci
npm run dev
```

Откройте [http://localhost:5173](http://localhost:5173). Vite проксирует запросы `/api` на `http://127.0.0.1:8080`. Если бэкенд запущен на другом адресе, задайте `BTT_API_TARGET` при запуске Vite.

```sh
npm run lint
npm test
npm run build
```

## Сквозные тесты

Поднимите полный стек на отдельной тестовой базе с работающим Zeebe и аккаунтом администратора, затем выполните команды. Тесты создают аккаунты, организации, заявления, маршруты и билеты. Указывайте адрес приложения или dev-сервера, подключённого к тестовому бэкенду.

```sh
npm ci
npx playwright install chromium
BTT_E2E_URL=http://localhost:8080 \
BTT_E2E_ADMIN_USERNAME=admin \
BTT_E2E_ADMIN_PASSWORD='пароль-тестового-администратора' npm run test:e2e
```
