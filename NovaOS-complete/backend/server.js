import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import OpenAI from 'openai';

const app = express();
app.disable('x-powered-by');
app.use(helmet());
app.use(express.json({ limit: '64kb' }));
app.use(rateLimit({ windowMs: 60_000, limit: 30, standardHeaders: 'draft-7', legacyHeaders: false }));

const apiKey = process.env.OPENAI_API_KEY;
if (!apiKey) {
  console.error('Missing OPENAI_API_KEY environment variable.');
  process.exit(1);
}
const openai = new OpenAI({ apiKey });
const model = process.env.OPENAI_MODEL || 'gpt-6.2-sol';

app.get('/health', (_req, res) => res.json({ ok: true, service: 'nova-intelligence' }));

app.post('/api/nova/chat', async (req, res) => {
  try {
    const instructions = typeof req.body?.instructions === 'string' ? req.body.instructions.slice(0, 12000) : '';
    const messages = Array.isArray(req.body?.messages) ? req.body.messages.slice(-12) : [];
    if (!instructions || messages.length === 0) {
      return res.status(400).json({ error: 'instructions and at least one message are required' });
    }
    const input = messages.map((m) => ({
      role: m?.role === 'assistant' ? 'assistant' : 'user',
      content: String(m?.content ?? '').slice(0, 8000)
    }));

    const response = await openai.responses.create({
      model,
      instructions,
      input,
      max_output_tokens: 1200,
      store: false
    });

    const text = response.output_text || '';
    if (!text) return res.status(502).json({ error: 'GPT returned no text output' });
    return res.json({ text, model });
  } catch (error) {
    console.error('NOVA GPT request failed:', error?.status || error?.message || 'unknown error');
    const status = Number.isInteger(error?.status) ? error.status : 502;
    return res.status(status >= 400 && status < 600 ? status : 502).json({
      error: status === 429 ? 'GPT rate limit reached. Try again shortly.' : 'The GPT request failed. Check backend logs.'
    });
  }
});

const port = Number(process.env.PORT || 8080);
app.listen(port, () => console.log(`NOVA Intelligence backend listening on ${port}`));
