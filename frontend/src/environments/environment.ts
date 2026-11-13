/** Read optional deployment runtime configuration without embedding a backend host. */
export const environment = { production: false, apiBase: (window as Window & {NOVA_CONFIG?: {apiBase?: string}}).NOVA_CONFIG?.apiBase ?? '/api/v1' };

