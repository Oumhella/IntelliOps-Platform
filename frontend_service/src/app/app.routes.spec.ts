import { routes } from './app.routes';

describe('application routes', () => {
  it('separates public, platform, and business URLs', () => {
    expect(routes.some((route) => route.path === 'login')).toBeTrue();
    expect(routes.some((route) => route.path === 'register')).toBeTrue();
    expect(routes.some((route) => route.path === 'super-admin')).toBeTrue();
    expect(routes.some((route) => route.path === 'app')).toBeTrue();
  });

  it('provides every business domain as a lazy child route', () => {
    const businessRoute = routes.find((route) => route.path === 'app');
    const childPaths = businessRoute?.children?.map((route) => route.path) ?? [];

    expect(childPaths).toEqual(jasmine.arrayContaining([
      '', 'leads', 'orders', 'stock', 'integrations', 'deliveries', 'billing',
      'subscriptions', 'team', 'notifications', 'assistant', 'profile',
    ]));
    expect(businessRoute?.children?.every((route) => route.loadComponent !== undefined)).toBeTrue();
  });

  it('restricts management routes to enterprise administrators', () => {
    const children = routes.find((route) => route.path === 'app')?.children ?? [];
    for (const path of ['billing', 'subscriptions', 'integrations', 'team', 'notifications', 'assistant', 'analytics']) {
      expect(children.find((route) => route.path === path)?.data?.['roles']).toEqual(['ROLE_ADMIN']);
    }
  });

  it('allows couriers into deliveries and profile only', () => {
    const children = routes.find((route) => route.path === 'app')?.children ?? [];
    expect(children.find((route) => route.path === 'deliveries')?.data?.['roles']).toContain('ROLE_LIVREUR');
    expect(children.find((route) => route.path === 'profile')?.data?.['roles']).toContain('ROLE_LIVREUR');
    expect(children.find((route) => route.path === 'orders')?.data?.['roles']).not.toContain('ROLE_LIVREUR');
    expect(children.find((route) => route.path === 'assistant')?.data?.['roles']).not.toContain('ROLE_LIVREUR');
  });
});
