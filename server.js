// هذا السيرفر يستقبل كل الطلبات على iraq.qd.je
// ويحولها (يمررها) مباشرة لموقعك الحقيقي على Cloudflare Workers
// المستخدم يفضل شايف "iraq.qd.je" في المتصفح طول الوقت

const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();

// 👇 غيّر هذا السطر فقط لعنوان موقعك الحقيقي (بدون https://)
const TARGET = 'https://fifth-class-association.iraqminers.workers.dev';

const proxy = createProxyMiddleware({
  target: TARGET,
  changeOrigin: true,   // يخلي السيرفر الهدف يفكر إن الطلب أصلي
  ws: true,             // دعم WebSockets لو احتجته لاحقًا
  onProxyReq: (proxyReq, req) => {
    console.log(`[PROXY] ${req.method} ${req.originalUrl} -> ${TARGET}${req.originalUrl}`);
  },
  onError: (err, req, res) => {
    console.error('[PROXY ERROR]', err.message);
    res.status(502).send('Bad Gateway - الموقع الحقيقي غير متاح حاليًا');
  }
});

app.use('/', proxy);

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Proxy server running on port ${PORT}`);
  console.log(`Forwarding all traffic to: ${TARGET}`);
});
