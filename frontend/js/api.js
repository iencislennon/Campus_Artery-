import { config } from "./config.js";
import { mockRequest } from "./mock.js";
import { clearUser, getUser } from "./session.js?v=2";

const PUBLIC_PATHS = ["/auth/login", "/auth/request-code", "/auth/register"];

async function httpRequest(method, path, { params, body }) {
  const query = new URLSearchParams(Object.entries(params ?? {}).filter(([, v]) => v)).toString();
  const token = getUser()?.token;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), config.timeoutMs);
  try {
    const response = await fetch(`${config.gatewayUrl}${path}${query ? `?${query}` : ""}`, {
      method,
      headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
    const payload = await response.json().catch(() => ({}));
    if (response.status === 401 && !PUBLIC_PATHS.includes(path)) {
      clearUser();
      location.reload();
    }
    if (!response.ok) throw Object.assign(new Error(payload?.message ?? "Request failed"), { status: response.status });
    return payload;
  } catch (error) {
    if (error.name === "AbortError") throw new Error("The server took too long to respond.");
    if (error instanceof TypeError) throw new Error("Cannot reach the server. Is the backend running?");
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

function request(method, path, options = {}) {
  console.info(`[api] ${method} ${path}`);
  return config.useMock
    ? mockRequest(method, path, { ...options, user: getUser() })
    : httpRequest(method, path, options);
}

export const api = {
  get: (path, params) => request("GET", path, { params }),
  post: (path, body) => request("POST", path, { body }),
  put: (path, body) => request("PUT", path, { body }),
};
