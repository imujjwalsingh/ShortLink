import { createBrowserRouter } from 'react-router'
import { RedirectIfAuthed, RequireAuth } from './auth/guards'
import { Layout } from './components/Layout'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { RegisterPage } from './pages/RegisterPage'
import { RouteError } from './pages/RouteError'

export const router = createBrowserRouter([
  {
    errorElement: <RouteError />,
    children: [
      {
        element: <RedirectIfAuthed />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/register', element: <RegisterPage /> },
        ],
      },
      {
        path: '/',
        element: <RequireAuth />,
        children: [
          {
            element: <Layout />,
            children: [
              { index: true, element: <DashboardPage /> },
              {
                path: 'links/:code',
                // Loaded on first visit: it carries the charting library,
                // which sign-in and the dashboard don't need.
                lazy: () => import('./pages/LinkPage').then((module) => ({ Component: module.LinkPage })),
              },
            ],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
