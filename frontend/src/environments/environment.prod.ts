/** Keep production API traffic same-origin unless deployment supplies runtime configuration. */
export const environment = { production: true, apiBase: (window as Window & {NOVA_CONFIG?: {apiBase?: string}}).NOVA_CONFIG?.apiBase ?? '/api/v1' };

