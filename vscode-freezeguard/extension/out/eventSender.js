"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EventSender = void 0;
const http = require("http");
class EventSender {
    static async ping() {
        return new Promise((resolve) => {
            const url = new URL(EventSender.metricsUrl);
            const options = {
                hostname: url.hostname,
                port: url.port,
                path: url.pathname,
                method: 'GET',
                timeout: 1000
            };
            const req = http.request(options, (res) => {
                resolve(res.statusCode || -1);
            });
            req.on('error', () => resolve(-1));
            req.on('timeout', () => {
                req.destroy();
                resolve(-1);
            });
            req.end();
        });
    }
    static async sendAsync(event) {
        const payload = JSON.stringify({
            action: event.action,
            duration_ms: parseFloat(event.duration_ms.toFixed(3)),
            thread: event.thread,
            heap_delta_bytes: event.heap_delta_bytes,
            edt_stalls: event.edt_stalls,
            edt_longest_stall_ms: parseFloat(event.edt_longest_stall_ms.toFixed(3)),
            ts: event.ts
        });
        return new Promise((resolve, reject) => {
            const url = new URL(EventSender.ingestUrl);
            const options = {
                hostname: url.hostname,
                port: url.port,
                path: url.pathname,
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json; charset=UTF-8',
                    'Content-Length': Buffer.byteLength(payload)
                },
                timeout: 2000
            };
            const req = http.request(options, (res) => {
                let data = '';
                res.on('data', (chunk) => data += chunk);
                res.on('end', () => {
                    if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
                        console.log(`FreezeGuard ingest OK ${res.statusCode}`);
                        resolve();
                    }
                    else {
                        console.warn(`FreezeGuard ingest HTTP ${res.statusCode} body='${data.slice(0, 200)}'`);
                        reject(new Error(`HTTP ${res.statusCode}`));
                    }
                });
            });
            req.on('error', (err) => {
                console.warn('FreezeGuard ingest error', err);
                reject(err);
            });
            req.on('timeout', () => {
                req.destroy();
                reject(new Error('Request timeout'));
            });
            req.write(payload);
            req.end();
        });
    }
}
exports.EventSender = EventSender;
EventSender.baseUrl = 'http://127.0.0.1:8000';
EventSender.ingestUrl = `${EventSender.baseUrl}/ingest`;
EventSender.metricsUrl = `${EventSender.baseUrl}/metrics`;
//# sourceMappingURL=eventSender.js.map