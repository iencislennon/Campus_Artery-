// All traffic goes through the Java gateway (see docs/architecture.md).
// Override with window.CAMPUSOS_CONFIG before app.js loads; set useMock: true to run without the backend.
const defaults = {
  useMock: false,
  skipVerification: false,
  skipTeacherInvite: true,
  timeoutMs: 8000,
  gatewayUrl: "http://localhost:8080/api/v1",
};

export const config = { ...defaults, ...(window.CAMPUSOS_CONFIG ?? {}) };
