// Opaque bearer token lives only in memory; a page reload requires login.
localStorage.removeItem('Admin-Token');
localStorage.removeItem('password');
sessionStorage.removeItem('sessionObj');
let token: string | null = null;
export const getToken = () => token;
export const setToken = (value: string) => (token = value);
export const removeToken = () => (token = null);
