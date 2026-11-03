// כניסה חלופית לשרת דרך pages.dev (נפתח ברשתות שחוסמות workers.dev). מעביר כל בקשה, כולל WebSocket, ל-Worker הראשי.
interface Env {
  API: { fetch(request: Request): Promise<Response> };
}

export const onRequest = (context: { request: Request; env: Env }) => context.env.API.fetch(context.request);
